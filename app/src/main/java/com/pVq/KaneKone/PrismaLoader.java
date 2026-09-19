package com.pVq.KaneKone.game;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Loader file .prisma tanpa dependency MsgPack eksternal.
 *
 * File .prisma yang kamu kirim ternyata adalah ZIP yang berisi:
 * - project.proj
 * - content/*.pobject
 *
 * Isi .pobject dibaca sebagai MessagePack stream, jadi parser ini punya decoder
 * MessagePack internal sendiri. Tidak perlu org.msgpack di Gradle.
 */
public class PrismaLoader {

    private static final String TAG = "PrismaLoader";

    public static class Vec3 {
        public float x, y, z;

        public Vec3() {}

        public Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vec3 copy() {
            return new Vec3(x, y, z);
        }
    }

    public static class Vec2 {
        public float u, v;

        public Vec2() {}

        public Vec2(float u, float v) {
            this.u = u;
            this.v = v;
        }
    }

    public static class Bone {
        public String id;
        public String name;
        public String parentId;
        public int parentIndex = -1;

        public Vec3 bindLocalPosition = new Vec3();
        public Vec3 bindLocalEulerHint = new Vec3();

        public Vec3 localPosition = new Vec3();
        public Vec3 localEulerHint = new Vec3();

        public float[] localMatrix = identity4();
        public float[] worldMatrix = identity4();
        public float[] bindMatrix = identity4();
        public float[] bindInverse = identity4();

        @Override
        public String toString() {
            return "Bone{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", parentId='" + parentId + '\'' +
                    ", pos=(" + localPosition.x + "," + localPosition.y + "," + localPosition.z + ")" +
                    '}';
        }
    }

    public static class SkinnedVertex {
        public Vec3 pos = new Vec3();
        public Vec3 normal = new Vec3(0, 1, 0);
        public Vec2 uv = new Vec2();
        public int[] joints = new int[]{0, 0, 0, 0};
        public float[] weights = new float[]{1, 0, 0, 0};
    }

    public static class PrismaModel {
        public final ArrayList<Bone> bones = new ArrayList<>();
        public final HashMap<String, Integer> boneIndexById = new HashMap<>();
        public final HashMap<String, Integer> boneIndexByName = new HashMap<>();
        public final ArrayList<String> skinBoneIds = new ArrayList<>();
        public final ArrayList<SkinnedVertex> vertices = new ArrayList<>();
        public final ArrayList<Integer> triangleIndices = new ArrayList<>();
        public String rawProjectText;

        public Bone findBone(String name) {
            Integer idx = boneIndexByName.get(name);
            return idx == null ? null : bones.get(idx);
        }
    }

    public PrismaModel load(InputStream inputStream) {
        PrismaModel model = new PrismaModel();

        try {
            byte[] pobjectBytes = extractPObjectBytes(inputStream);
            if (pobjectBytes == null || pobjectBytes.length == 0) {
                Log.e(TAG, "Tidak menemukan .pobject di dalam .prisma");
                return model;
            }

            ArrayList<Object> top = new MessagePackReader(pobjectBytes).readAllTopLevel();
            if (top.isEmpty()) {
                Log.e(TAG, "MessagePack stream kosong");
                return model;
            }

            List<Object> tree = null;
            List<Object> meshWrap = null;

            for (Object o : top) {
                if (!(o instanceof List)) continue;
                List<?> list = (List<?>) o;

                if (tree == null && looksLikeTree(list)) {
                    tree = (List<Object>) list;
                    continue;
                }

                if (meshWrap == null && looksLikeMeshWrapper(list)) {
                    meshWrap = (List<Object>) list;
                }
            }

            if (tree == null || meshWrap == null) {
                Log.e(TAG, "Gagal menemukan tree atau mesh di .pobject");
                return model;
            }

            parseTree(model, tree);
            parseMesh(model, meshWrap);
            buildParentIndices(model);
            computeCurrentWorldMatrices(model);
            assignBindMatrices(model);
            computeCurrentWorldMatrices(model);

            Log.d(TAG, "Loaded Prisma: bones=" + model.bones.size()
                    + ", vertices=" + model.vertices.size()
                    + ", triangles=" + (model.triangleIndices.size() / 3));

            return model;
        } catch (Exception e) {
            Log.e(TAG, "Gagal load Prisma", e);
            return model;
        }
    }

    /**
     * Buffer vertex 20-float:
     * pos3 + normal3 + color4 + uv2 + weight4 + joint4
     */
    public float[] buildSkinnedMesh20(PrismaModel model) {
        float[] out = new float[model.vertices.size() * 20];

        for (int i = 0; i < model.vertices.size(); i++) {
            SkinnedVertex v = model.vertices.get(i);
            int b = i * 20;

            out[b + 0] = v.pos.x;
            out[b + 1] = v.pos.y;
            out[b + 2] = v.pos.z;

            out[b + 3] = v.normal.x;
            out[b + 4] = v.normal.y;
            out[b + 5] = v.normal.z;

            out[b + 6] = 1f;
            out[b + 7] = 1f;
            out[b + 8] = 1f;
            out[b + 9] = 1f;

            out[b + 10] = v.uv.u;
            out[b + 11] = v.uv.v;

            out[b + 12] = v.weights[0];
            out[b + 13] = v.weights[1];
            out[b + 14] = v.weights[2];
            out[b + 15] = v.weights[3];

            out[b + 16] = v.joints[0];
            out[b + 17] = v.joints[1];
            out[b + 18] = v.joints[2];
            out[b + 19] = v.joints[3];
        }

        return out;
    }

    /**
     * Pose root untuk render world-space.
     * Root yang dipakai adalah bone pertama yang cocok, atau bone 0.
     */
    public void setRootPose(PrismaModel model, float x, float y, float z, float rotX, float rotY, float rotZ) {
        Bone root = model.findBone("hip");
        if (root == null && !model.bones.isEmpty()) {
            root = model.bones.get(0);
        }
        if (root == null) return;

        root.localPosition = new Vec3(x, y, z);
        root.localEulerHint = new Vec3(rotX, rotY, rotZ);
        computeCurrentWorldMatrices(model);
    }

    public void setBoneEuler(PrismaModel model, String boneName, float rotX, float rotY, float rotZ) {
        Bone b = model.findBone(boneName);
        if (b == null) return;

        b.localEulerHint = new Vec3(rotX, rotY, rotZ);
        computeCurrentWorldMatrices(model);
    }

    public void resetPose(PrismaModel model) {
        for (Bone b : model.bones) {
            b.localPosition = b.bindLocalPosition.copy();
            b.localEulerHint = b.bindLocalEulerHint.copy();
        }
        computeCurrentWorldMatrices(model);
    }

    // ============================================================
    // PARSING
    // ============================================================

    private byte[] extractPObjectBytes(InputStream inputStream) throws IOException {
        ZipInputStream zis = new ZipInputStream(inputStream);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (!entry.isDirectory() && entry.getName().endsWith(".pobject")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                copy(zis, out);
                zis.close();
                return out.toByteArray();
            }
        }
        zis.close();
        return null;
    }

    private void copy(InputStream in, ByteArrayOutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
    }

    private boolean looksLikeTree(List<?> list) {
        if (list.size() < 10) return false;
        Object first = list.get(0);
        if (!(first instanceof List)) return false;

        List<?> node = (List<?>) first;
        return node.size() >= 8
                && node.get(0) instanceof String
                && node.get(1) instanceof String
                && node.get(2) instanceof List;
    }

    private boolean looksLikeMeshWrapper(List<?> list) {
        if (list.size() != 1) return false;
        Object inner = list.get(0);
        if (!(inner instanceof List)) return false;

        List<?> mesh = (List<?>) inner;
        return mesh.size() >= 10 && mesh.get(0) instanceof String;
    }

    @SuppressWarnings("unchecked")
    private void parseTree(PrismaModel model, List<Object> tree) {
        for (Object nodeObj : tree) {
            if (!(nodeObj instanceof List)) continue;
            List<Object> node = (List<Object>) nodeObj;
            if (node.size() < 8) continue;

            Bone bone = new Bone();
            bone.id = asString(node.get(0));
            bone.name = asString(node.get(1));
            bone.parentId = asString(node.get(7));

            Object compsObj = node.get(2);
            if (compsObj instanceof List) {
                List<Object> comps = (List<Object>) compsObj;
                for (Object compObj : comps) {
                    if (!(compObj instanceof List)) continue;
                    List<Object> comp = (List<Object>) compObj;
                    if (comp.size() < 2) continue;

                    String compName = asString(comp.get(0));
                    Object propsObj = comp.get(1);
                    if (!(propsObj instanceof List)) continue;
                    List<Object> props = (List<Object>) propsObj;

                    if ("PTransform".equals(compName)) {
                        for (Object propObj : props) {
                            if (!(propObj instanceof List)) continue;
                            List<Object> prop = (List<Object>) propObj;
                            if (prop.size() < 2) continue;

                            String key = asString(prop.get(0));
                            String val = asString(prop.get(1));

                            if ("localPosition".equals(key)) {
                                bone.bindLocalPosition = parseVec3Json(val);
                            } else if ("localEulerAnglesHint".equals(key)) {
                                bone.bindLocalEulerHint = parseVec3Json(val);
                            }
                        }
                    }

                    if ("PMeshRenderer".equals(compName)) {
                        for (Object propObj : props) {
                            if (!(propObj instanceof List)) continue;
                            List<Object> prop = (List<Object>) propObj;
                            if (prop.size() < 2) continue;

                            String key = asString(prop.get(0));
                            String val = asString(prop.get(1));

                            if ("skinBones".equals(key)) {
                                ArrayList<String> ids = parseIdArrayJson(val);
                                model.skinBoneIds.clear();
                                model.skinBoneIds.addAll(ids);
                            }
                        }
                    }
                }
            }

            bone.localPosition = bone.bindLocalPosition.copy();
            bone.localEulerHint = bone.bindLocalEulerHint.copy();

            int idx = model.bones.size();
            model.bones.add(bone);
            model.boneIndexById.put(bone.id, idx);
            model.boneIndexByName.put(bone.name, idx);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseMesh(PrismaModel model, List<Object> meshWrap) {
        if (meshWrap.isEmpty()) return;

        Object meshObj = meshWrap.get(0);
        if (!(meshObj instanceof List)) return;

        List<Object> mesh = (List<Object>) meshObj;
        if (mesh.size() < 11) return;

        List<Object> normalsObj = asList(mesh.get(3));
        List<Object> quadIndexObj = asList(mesh.get(4));
        List<Object> uvObj = asList(mesh.get(5));
        List<Object> posObj = asList(mesh.get(8));
        List<Object> skinObj = asList(mesh.get(9));
        List<Object> bindMatricesObj = asList(mesh.get(10));

        ArrayList<Vec3> positions = new ArrayList<>();
        for (Object p : posObj) {
            positions.add(parseVec3List(asList(p)));
        }

        ArrayList<Vec3> normals = new ArrayList<>();
        for (Object n : normalsObj) {
            normals.add(parseVec3List(asList(n)));
        }

        ArrayList<Vec2> uvs = new ArrayList<>();
        for (Object uv : uvObj) {
            uvs.add(parseVec2List(asList(uv)));
        }

        ArrayList<int[]> skin = new ArrayList<>();
        for (Object s : skinObj) {
            List<Object> row = asList(s);
            if (row.size() < 8) continue;
            int[] joints = new int[]{
                    asInt(row.get(0)), asInt(row.get(1)), asInt(row.get(2)), asInt(row.get(3))
            };
            float[] weights = new float[]{
                    asFloat(row.get(4)), asFloat(row.get(5)), asFloat(row.get(6)), asFloat(row.get(7))
            };
            skin.add(new int[]{
                    joints[0], joints[1], joints[2], joints[3],
                    Float.floatToIntBits(weights[0]),
                    Float.floatToIntBits(weights[1]),
                    Float.floatToIntBits(weights[2]),
                    Float.floatToIntBits(weights[3])
            });
        }

        if (!bindMatricesObj.isEmpty() && !model.skinBoneIds.isEmpty()) {
            int count = Math.min(bindMatricesObj.size(), model.skinBoneIds.size());
            for (int i = 0; i < count; i++) {
                String boneId = model.skinBoneIds.get(i);
                Integer boneIndex = model.boneIndexById.get(boneId);
                if (boneIndex == null) continue;

                List<Object> matrixRow = asList(bindMatricesObj.get(i));
                float[] bind = parseMatrix16(matrixRow);
                Bone bone = model.bones.get(boneIndex);
                bone.bindMatrix = bind;
                bone.bindInverse = invert4(bind);
            }
        }

        int faceCount = quadIndexObj.size() / 4;
        for (int f = 0; f < faceCount; f++) {
            int i0 = asInt(quadIndexObj.get(f * 4));
            int i1 = asInt(quadIndexObj.get(f * 4 + 1));
            int i2 = asInt(quadIndexObj.get(f * 4 + 2));
            int i3 = asInt(quadIndexObj.get(f * 4 + 3));

            Vec2 uv0 = getSafe(uvs, f * 4);
            Vec2 uv1 = getSafe(uvs, f * 4 + 1);
            Vec2 uv2 = getSafe(uvs, f * 4 + 2);
            Vec2 uv3 = getSafe(uvs, f * 4 + 3);

            emitTriangleCorner(model, positions, normals, skin, i0, uv0);
            emitTriangleCorner(model, positions, normals, skin, i1, uv1);
            emitTriangleCorner(model, positions, normals, skin, i2, uv2);

            emitTriangleCorner(model, positions, normals, skin, i0, uv0);
            emitTriangleCorner(model, positions, normals, skin, i2, uv2);
            emitTriangleCorner(model, positions, normals, skin, i3, uv3);
        }
    }

    private void emitTriangleCorner(PrismaModel model,
                                    ArrayList<Vec3> positions,
                                    ArrayList<Vec3> normals,
                                    ArrayList<int[]> skin,
                                    int vertexIndex,
                                    Vec2 uv) {
        if (vertexIndex < 0 || vertexIndex >= positions.size()) return;

        SkinnedVertex v = new SkinnedVertex();
        v.pos = positions.get(vertexIndex).copy();
        v.uv = uv == null ? new Vec2() : new Vec2(uv.u, uv.v);

        if (vertexIndex < normals.size()) {
            v.normal = normals.get(vertexIndex).copy();
        }

        if (vertexIndex < skin.size()) {
            int[] row = skin.get(vertexIndex);
            v.joints[0] = row[0];
            v.joints[1] = row[1];
            v.joints[2] = row[2];
            v.joints[3] = row[3];
            v.weights[0] = Float.intBitsToFloat(row[4]);
            v.weights[1] = Float.intBitsToFloat(row[5]);
            v.weights[2] = Float.intBitsToFloat(row[6]);
            v.weights[3] = Float.intBitsToFloat(row[7]);
        }

        model.vertices.add(v);
        model.triangleIndices.add(model.vertices.size() - 1);
    }

    // ============================================================
    // BONE TRANSFORM
    // ============================================================

    private void buildParentIndices(PrismaModel model) {
        for (Bone b : model.bones) {
            Integer parentIdx = model.boneIndexById.get(b.parentId);
            b.parentIndex = parentIdx == null ? -1 : parentIdx;
        }
    }

    private void assignBindMatrices(PrismaModel model) {
        for (Bone b : model.bones) {
            if (isIdentity4(b.bindInverse)) {
                b.bindInverse = invert4(b.worldMatrix);
            }
        }
    }

    private void computeCurrentWorldMatrices(PrismaModel model) {
        for (int i = 0; i < model.bones.size(); i++) {
            Bone b = model.bones.get(i);
            b.localMatrix = composeTRS(b.localPosition, eulerDegToQuat(b.localEulerHint), new Vec3(1, 1, 1));

            if (b.parentIndex >= 0 && b.parentIndex < model.bones.size()) {
                b.worldMatrix = mul4(model.bones.get(b.parentIndex).worldMatrix, b.localMatrix);
            } else {
                b.worldMatrix = b.localMatrix;
            }
        }
    }

    // ============================================================
    // UTIL
    // ============================================================

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object o) {
        if (o instanceof List) return (List<Object>) o;
        return new ArrayList<>();
    }

    private int asInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(String.valueOf(o));
    }

    private float asFloat(Object o) {
        if (o instanceof Number) return ((Number) o).floatValue();
        return Float.parseFloat(String.valueOf(o));
    }

    private Vec2 getSafe(ArrayList<Vec2> list, int idx) {
        if (idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    private Vec3 parseVec3Json(String json) {
        if (json == null) return new Vec3();
        try {
            float x = extractJsonFloat(json, "x");
            float y = extractJsonFloat(json, "y");
            float z = extractJsonFloat(json, "z");
            return new Vec3(x, y, z);
        } catch (Exception e) {
            return new Vec3();
        }
    }

    private ArrayList<String> parseIdArrayJson(String json) {
        ArrayList<String> out = new ArrayList<>();
        if (json == null) return out;

        int start = json.indexOf("\"");
        if (start < 0) return out;

        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (!inString) {
                if (c == '"') {
                    inString = true;
                    current.setLength(0);
                }
            } else {
                if (escape) {
                    current.append(c);
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                    out.add(current.toString());
                } else {
                    current.append(c);
                }
            }
        }
        return out;
    }

    private Vec3 parseVec3List(List<Object> list) {
        if (list.size() < 3) return new Vec3();
        return new Vec3(asFloat(list.get(0)), asFloat(list.get(1)), asFloat(list.get(2)));
    }

    private Vec2 parseVec2List(List<Object> list) {
        if (list.size() < 2) return new Vec2();
        return new Vec2(asFloat(list.get(0)), asFloat(list.get(1)));
    }

    private float[] parseMatrix16(List<Object> row) {
        float[] m = identity4();
        int n = Math.min(16, row.size());
        for (int i = 0; i < n; i++) {
            m[i] = asFloat(row.get(i));
        }
        return m;
    }

    private float extractJsonFloat(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return 0f;
        int colon = json.indexOf(':', k);
        if (colon < 0) return 0f;
        int end = colon + 1;
        while (end < json.length() && json.charAt(end) == ' ') end++;
        int stop = end;
        while (stop < json.length()) {
            char c = json.charAt(stop);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') {
                stop++;
            } else {
                break;
            }
        }
        return Float.parseFloat(json.substring(end, stop));
    }

    private static float[] identity4() {
        return new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        };
    }

    private boolean isIdentity4(float[] m) {
        if (m == null || m.length != 16) return true;
        return m[0] == 1f && m[5] == 1f && m[10] == 1f && m[15] == 1f
                && m[1] == 0f && m[2] == 0f && m[3] == 0f
                && m[4] == 0f && m[6] == 0f && m[7] == 0f
                && m[8] == 0f && m[9] == 0f && m[11] == 0f
                && m[12] == 0f && m[13] == 0f && m[14] == 0f;
    }

    private float[] composeTRS(Vec3 pos, float[] q, Vec3 s) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float wx = w * x, wy = w * y, wz = w * z;

        float[] m = new float[16];
        m[0] = (1 - 2 * (yy + zz)) * s.x;
        m[1] = (2 * (xy + wz)) * s.x;
        m[2] = (2 * (xz - wy)) * s.x;
        m[3] = 0;

        m[4] = (2 * (xy - wz)) * s.y;
        m[5] = (1 - 2 * (xx + zz)) * s.y;
        m[6] = (2 * (yz + wx)) * s.y;
        m[7] = 0;

        m[8] = (2 * (xz + wy)) * s.z;
        m[9] = (2 * (yz - wx)) * s.z;
        m[10] = (1 - 2 * (xx + yy)) * s.z;
        m[11] = 0;

        m[12] = pos.x;
        m[13] = pos.y;
        m[14] = pos.z;
        m[15] = 1;
        return m;
    }

    private float[] eulerDegToQuat(Vec3 e) {
        float rx = (float) Math.toRadians(e.x);
        float ry = (float) Math.toRadians(e.y);
        float rz = (float) Math.toRadians(e.z);
        float cx = (float) Math.cos(rx * 0.5f), sx = (float) Math.sin(rx * 0.5f);
        float cy = (float) Math.cos(ry * 0.5f), sy = (float) Math.sin(ry * 0.5f);
        float cz = (float) Math.cos(rz * 0.5f), sz = (float) Math.sin(rz * 0.5f);

        // ZYX order
        float w = cx * cy * cz + sx * sy * sz;
        float x = sx * cy * cz - cx * sy * sz;
        float y = cx * sy * cz + sx * cy * sz;
        float z = cx * cy * sz - sx * sy * cz;
        return new float[]{x, y, z, w};
    }

    private float[] mul4(float[] a, float[] b) {
        float[] o = new float[16];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                o[c * 4 + r] =
                        a[0 * 4 + r] * b[c * 4 + 0] +
                        a[1 * 4 + r] * b[c * 4 + 1] +
                        a[2 * 4 + r] * b[c * 4 + 2] +
                        a[3 * 4 + r] * b[c * 4 + 3];
            }
        }
        return o;
    }

    private float[] invert4(float[] m) {
        float[] inv = new float[16];
        float[] a = m;

        inv[0] = a[5]  * a[10] * a[15] - a[5]  * a[11] * a[14] - a[9]  * a[6]  * a[15] + a[9]  * a[7]  * a[14] + a[13] * a[6]  * a[11] - a[13] * a[7]  * a[10];
        inv[4] = -a[4]  * a[10] * a[15] + a[4]  * a[11] * a[14] + a[8]  * a[6]  * a[15] - a[8]  * a[7]  * a[14] - a[12] * a[6]  * a[11] + a[12] * a[7]  * a[10];
        inv[8] = a[4]  * a[9] * a[15] - a[4]  * a[11] * a[13] - a[8]  * a[5] * a[15] + a[8]  * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9];
        inv[12] = -a[4]  * a[9] * a[14] + a[4]  * a[10] * a[13] + a[8]  * a[5] * a[14] - a[8]  * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9];
        inv[1] = -a[1]  * a[10] * a[15] + a[1]  * a[11] * a[14] + a[9]  * a[2] * a[15] - a[9]  * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10];
        inv[5] = a[0]  * a[10] * a[15] - a[0]  * a[11] * a[14] - a[8]  * a[2] * a[15] + a[8]  * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10];
        inv[9] = -a[0]  * a[9] * a[15] + a[0]  * a[11] * a[13] + a[8]  * a[1] * a[15] - a[8]  * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9];
        inv[13] = a[0]  * a[9] * a[14] - a[0]  * a[10] * a[13] - a[8]  * a[1] * a[14] + a[8]  * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9];
        inv[2] = a[1]  * a[6] * a[15] - a[1]  * a[7] * a[14] - a[5]  * a[2] * a[15] + a[5]  * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6];
        inv[6] = -a[0]  * a[6] * a[15] + a[0]  * a[7] * a[14] + a[4]  * a[2] * a[15] - a[4]  * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6];
        inv[10] = a[0]  * a[5] * a[15] - a[0]  * a[7] * a[13] - a[4]  * a[1] * a[15] + a[4]  * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5];
        inv[14] = -a[0]  * a[5] * a[14] + a[0]  * a[6] * a[13] + a[4]  * a[1] * a[14] - a[4]  * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5];
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] - a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6];
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] + a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6];
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] - a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5];
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] + a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5];

        float det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        if (det == 0) return identity4();

        det = 1.0f / det;
        for (int i = 0; i < 16; i++) inv[i] *= det;
        return inv;
    }

    // ============================================================
    // INTERNAL MESSAGEPACK READER
    // ============================================================

    private static class MessagePackReader {
        private final byte[] data;
        private int pos = 0;

        MessagePackReader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
        }

        ArrayList<Object> readAllTopLevel() throws IOException {
            ArrayList<Object> out = new ArrayList<>();
            while (pos < data.length) {
                out.add(readValue());
            }
            return out;
        }

        private Object readValue() throws IOException {
            if (pos >= data.length) throw new IOException("EOF");

            int b = readUnsignedByte();

            if ((b & 0x80) == 0x00) {
                return b;
            }

            if ((b & 0xF0) == 0x80) {
                int size = b & 0x0F;
                HashMap<Object, Object> map = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    Object k = readValue();
                    Object v = readValue();
                    map.put(k, v);
                }
                return map;
            }

            if ((b & 0xF0) == 0x90) {
                int size = b & 0x0F;
                ArrayList<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(readValue());
                }
                return list;
            }

            if ((b & 0xE0) == 0xA0) {
                int len = b & 0x1F;
                return readString(len);
            }

            if ((b & 0xE0) == 0xE0) {
                return (byte) b;
            }

            switch (b) {
                case 0xC0:
                    return null;
                case 0xC2:
                    return Boolean.FALSE;
                case 0xC3:
                    return Boolean.TRUE;
                case 0xC4:
                    return readBinary(readUnsignedByte());
                case 0xC5:
                    return readBinary(readUnsignedShort());
                case 0xC6:
                    return readBinary(readInt());
                case 0xCA:
                    return Float.intBitsToFloat(readInt());
                case 0xCB:
                    return Double.longBitsToDouble(readLong());
                case 0xCC:
                    return readUnsignedByte();
                case 0xCD:
                    return readUnsignedShort();
                case 0xCE:
                    return (int) readUnsignedInt();
                case 0xCF:
                    return readLong();
                case 0xD0:
                    return (byte) readUnsignedByte();
                case 0xD1:
                    return (short) readShort();
                case 0xD2:
                    return readInt();
                case 0xD3:
                    return readLong();
                case 0xD4:
                case 0xD5:
                case 0xD6:
                case 0xD7:
                case 0xD8: {
                    int len = extPayloadLength(b);
                    skipExtType();
                    return readBinary(len);
                }
                case 0xD9:
                    return readString(readUnsignedByte());
                case 0xDA:
                    return readString(readUnsignedShort());
                case 0xDB:
                    return readString(readInt());
                case 0xDC: {
                    int size = readUnsignedShort();
                    ArrayList<Object> list = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) list.add(readValue());
                    return list;
                }
                case 0xDD: {
                    int size = readInt();
                    ArrayList<Object> list = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) list.add(readValue());
                    return list;
                }
                case 0xDE: {
                    int size = readUnsignedShort();
                    HashMap<Object, Object> map = new HashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        Object k = readValue();
                        Object v = readValue();
                        map.put(k, v);
                    }
                    return map;
                }
                case 0xDF: {
                    int size = readInt();
                    HashMap<Object, Object> map = new HashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        Object k = readValue();
                        Object v = readValue();
                        map.put(k, v);
                    }
                    return map;
                }
                default:
                    throw new IOException(String.format("Unsupported msgpack type 0x%02X at pos %d", b, pos - 1));
            }
        }

        private int extPayloadLength(int typeByte) throws IOException {
            switch (typeByte) {
                case 0xD4: return 1;
                case 0xD5: return 2;
                case 0xD6: return 4;
                case 0xD7: return 8;
                case 0xD8: return 16;
                default: throw new IOException("Unsupported ext type: " + typeByte);
            }
        }

        private void skipExtType() throws IOException {
            readUnsignedByte();
        }

        private String readString(int len) throws IOException {
            if (len < 0 || pos + len > data.length) throw new IOException("EOF string");
            String s = new String(data, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }

        private byte[] readBinary(int len) throws IOException {
            if (len < 0 || pos + len > data.length) throw new IOException("EOF binary");
            byte[] out = Arrays.copyOfRange(data, pos, pos + len);
            pos += len;
            return out;
        }

        private int readUnsignedByte() throws IOException {
            if (pos >= data.length) throw new IOException("EOF byte");
            return data[pos++] & 0xFF;
        }

        private int readUnsignedShort() throws IOException {
            return ((readUnsignedByte() << 8) | readUnsignedByte());
        }

        private short readShort() throws IOException {
            return (short) readUnsignedShort();
        }

        private int readInt() throws IOException {
            return (int) readUnsignedInt();
        }

        private long readUnsignedInt() throws IOException {
            long a = readUnsignedByte();
            long b = readUnsignedByte();
            long c = readUnsignedByte();
            long d = readUnsignedByte();
            return (a << 24) | (b << 16) | (c << 8) | d;
        }

        private long readLong() throws IOException {
            long a = readUnsignedInt();
            long b = readUnsignedInt();
            return (a << 32) | (b & 0xffffffffL);
        }
    }
}