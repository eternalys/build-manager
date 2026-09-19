package com.pVq.KaneKone.game;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class GLBLoader {

    private static final String TAG = "GLTFLoader";

    public static class GLTFAnimation {
        public static class Sampler {
            public float[] inputTimes;
            public float[] outputValues;
            public String interpolation = "LINEAR";
            public int components = 1;
        }

        public static class Channel {
            public int samplerIndex = -1;
            public int targetNode = -1;
            public String targetPath = "rotation";
        }

        public String name = "Animation";
        public float duration = 0f;
        public final ArrayList<Sampler> samplers = new ArrayList<>();
        public final ArrayList<Channel> channels = new ArrayList<>();
    }

    public static class GLTFNode {
        public String name = "";
        public int parent = -1;
        public final ArrayList<Integer> children = new ArrayList<>();
        public final float[] translation = new float[]{0f, 0f, 0f};
        public final float[] rotation = new float[]{0f, 0f, 0f, 1f};
        public final float[] scale = new float[]{1f, 1f, 1f};
        public int mesh = -1;
        public int skin = -1;
        public int jointIndex = -1;
    }

    public static class GLTFModel {
        public SkinnedMesh mesh;
        public final ArrayList<GLTFNode> nodes = new ArrayList<>();
        public int[] skinJoints;
        public int[] skinParents;
        public String[] jointNames;
        public float[] inverseBindMatrices;
        public float[] bindTranslations;
        public float[] bindRotations;
        public final ArrayList<GLTFAnimation> animations = new ArrayList<>();
        public float minX, maxX, minY, maxY, minZ, maxZ;
    }

    private static class GlbContainer {
        final JSONObject root;
        final ByteBuffer[] buffers;

        GlbContainer(JSONObject root, ByteBuffer[] buffers) {
            this.root = root;
            this.buffers = buffers;
        }
    }

    public static GLTFModel loadGLTF(Context context, String fileName) {
        try {
            JSONObject root;
            ByteBuffer[] buffers;

            if (fileName.toLowerCase().endsWith(".glb")) {
                GlbContainer glb = readGlb(context, fileName);
                root = glb.root;
                buffers = glb.buffers;
            } else {
                String jsonText = readAssetText(context, fileName);
                root = new JSONObject(jsonText);
                buffers = loadBuffers(root);
            }

            GLTFModel model = new GLTFModel();

            parseNodes(root, model);
            buildParents(model);

            if (root.has("skins")) {
                loadSkin(root, buffers, model);
            }

            float[] vertices = loadAllMeshVertices(root, buffers, model);
            if (vertices == null || vertices.length == 0) {
                throw new RuntimeException("Mesh tidak ditemukan / kosong.");
            }
            model.mesh = new SkinnedMesh(vertices);
            calcBounds(vertices, model);

            if (root.has("animations")) {
                loadAnimations(root, buffers, model);
            }

            Log.d(TAG, "GLTF load OK | verts=" + model.mesh.getVertexCount()
                    + " joints=" + (model.skinJoints == null ? 0 : model.skinJoints.length)
                    + " anims=" + model.animations.size());
            return model;
        } catch (Throwable e) {
            Log.e(TAG, "GLTF LOAD FAILED", e);
            return null;
        }
    }

    private static GlbContainer readGlb(Context context, String fileName) throws Exception {
        InputStream is = context.getAssets().open(fileName);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1) bos.write(buf, 0, read);
        is.close();

        byte[] data = bos.toByteArray();
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int magic = bb.getInt();
        if (magic != 0x46546C67) {
            throw new RuntimeException("Invalid GLB magic");
        }

        int version = bb.getInt();
        if (version != 2) {
            throw new RuntimeException("Unsupported GLB version: " + version);
        }

        int length = bb.getInt();
        if (length > data.length) {
            throw new RuntimeException("Corrupt GLB length");
        }

        JSONObject root = null;
        ArrayList<ByteBuffer> binBuffers = new ArrayList<>();

        while (bb.remaining() >= 8) {
            int chunkLength = bb.getInt();
            int chunkType = bb.getInt();

            if (chunkLength < 0 || chunkLength > bb.remaining()) {
                throw new RuntimeException("Corrupt GLB chunk");
            }

            byte[] chunk = new byte[chunkLength];
            bb.get(chunk);

            if (chunkType == 0x4E4F534A) { // JSON
                String jsonText = new String(chunk, "UTF-8");
                int nullPos = jsonText.indexOf('\0');
                if (nullPos >= 0) jsonText = jsonText.substring(0, nullPos);
                root = new JSONObject(jsonText.trim());
            } else if (chunkType == 0x004E4942) { // BIN
                binBuffers.add(ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN));
            }

            while ((bb.position() % 4) != 0 && bb.hasRemaining()) {
                bb.get();
            }
        }

        if (root == null) {
            throw new RuntimeException("JSON chunk not found in GLB");
        }

        if (binBuffers.isEmpty()) {
            binBuffers.add(ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN));
        }

        ByteBuffer[] buffers = binBuffers.toArray(new ByteBuffer[0]);
        return new GlbContainer(root, buffers);
    }

    private static String readAssetText(Context context, String fileName) throws Exception {
        InputStream is = context.getAssets().open(fileName);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1) bos.write(buf, 0, read);
        is.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static ByteBuffer[] loadBuffers(JSONObject root) throws Exception {
        JSONArray buffersArr = root.optJSONArray("buffers");
        if (buffersArr == null) return new ByteBuffer[0];

        ByteBuffer[] buffers = new ByteBuffer[buffersArr.length()];
        for (int i = 0; i < buffersArr.length(); i++) {
            String uri = buffersArr.getJSONObject(i).optString("uri", "");
            if (!uri.startsWith("data:")) {
                throw new RuntimeException("External .bin belum didukung. Pakai embedded/base64.");
            }
            String base64 = uri.substring(uri.indexOf(",") + 1);
            byte[] binData = Base64.decode(base64, Base64.DEFAULT);
            buffers[i] = ByteBuffer.wrap(binData).order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffers;
    }

    private static void parseNodes(JSONObject root, GLTFModel model) throws Exception {
        JSONArray nodes = root.optJSONArray("nodes");
        if (nodes == null) return;

        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.getJSONObject(i);
            GLTFNode node = new GLTFNode();
            node.name = n.optString("name", "node_" + i);
            node.mesh = n.optInt("mesh", -1);
            node.skin = n.optInt("skin", -1);

            JSONArray t = n.optJSONArray("translation");
            if (t != null && t.length() >= 3) {
                node.translation[0] = (float) t.getDouble(0);
                node.translation[1] = (float) t.getDouble(1);
                node.translation[2] = (float) t.getDouble(2);
            }

            JSONArray r = n.optJSONArray("rotation");
            if (r != null && r.length() >= 4) {
                node.rotation[0] = (float) r.getDouble(0);
                node.rotation[1] = (float) r.getDouble(1);
                node.rotation[2] = (float) r.getDouble(2);
                node.rotation[3] = (float) r.getDouble(3);
            }

            JSONArray s = n.optJSONArray("scale");
            if (s != null && s.length() >= 3) {
                node.scale[0] = (float) s.getDouble(0);
                node.scale[1] = (float) s.getDouble(1);
                node.scale[2] = (float) s.getDouble(2);
            }

            JSONArray children = n.optJSONArray("children");
            if (children != null) {
                for (int c = 0; c < children.length(); c++) {
                    node.children.add(children.getInt(c));
                }
            }

            model.nodes.add(node);
        }
    }

    private static void buildParents(GLTFModel model) {
        for (int i = 0; i < model.nodes.size(); i++) {
            GLTFNode node = model.nodes.get(i);
            for (int child : node.children) {
                if (child >= 0 && child < model.nodes.size()) {
                    model.nodes.get(child).parent = i;
                }
            }
        }
    }

    private static void loadSkin(JSONObject root, ByteBuffer[] buffers, GLTFModel model) throws Exception {
        JSONObject skin = root.getJSONArray("skins").getJSONObject(0);
        JSONArray joints = skin.getJSONArray("joints");
        JSONArray nodes = root.getJSONArray("nodes");

        int jointCount = joints.length();
        model.skinJoints = new int[jointCount];
        model.skinParents = new int[jointCount];
        model.jointNames = new String[jointCount];
        model.bindTranslations = new float[jointCount * 3];
        model.bindRotations = new float[jointCount * 4];

        for (int i = 0; i < jointCount; i++) {
            int nodeIndex = joints.getInt(i);
            model.skinJoints[i] = nodeIndex;
            JSONObject node = nodes.getJSONObject(nodeIndex);
            model.jointNames[i] = node.optString("name", "joint_" + i);

            JSONArray t = node.optJSONArray("translation");
            if (t != null && t.length() >= 3) {
                model.bindTranslations[i * 3] = (float) t.getDouble(0);
                model.bindTranslations[i * 3 + 1] = (float) t.getDouble(1);
                model.bindTranslations[i * 3 + 2] = (float) t.getDouble(2);
            }

            JSONArray r = node.optJSONArray("rotation");
            if (r != null && r.length() >= 4) {
                model.bindRotations[i * 4] = (float) r.getDouble(0);
                model.bindRotations[i * 4 + 1] = (float) r.getDouble(1);
                model.bindRotations[i * 4 + 2] = (float) r.getDouble(2);
                model.bindRotations[i * 4 + 3] = (float) r.getDouble(3);
            } else {
                model.bindRotations[i * 4] = 0f;
                model.bindRotations[i * 4 + 1] = 0f;
                model.bindRotations[i * 4 + 2] = 0f;
                model.bindRotations[i * 4 + 3] = 1f;
            }

            model.skinParents[i] = findJointParent(model, nodeIndex);
        }

        if (skin.has("inverseBindMatrices")) {
            int ibmAcc = skin.getInt("inverseBindMatrices");
            model.inverseBindMatrices = (float[]) readAccessor(root, buffers, ibmAcc, false, false);
        }
    }

    private static int findJointParent(GLTFModel model, int nodeIndex) {
        GLTFNode node = model.nodes.get(nodeIndex);
        int parentNode = node.parent;
        while (parentNode != -1) {
            for (int i = 0; i < model.skinJoints.length; i++) {
                if (model.skinJoints[i] == parentNode) return i;
            }
            parentNode = model.nodes.get(parentNode).parent;
        }
        return -1;
    }

    private static void loadAnimations(JSONObject root, ByteBuffer[] buffers, GLTFModel model) throws Exception {
        JSONArray anims = root.getJSONArray("animations");
        for (int i = 0; i < anims.length(); i++) {
            JSONObject anim = anims.getJSONObject(i);
            GLTFAnimation out = new GLTFAnimation();
            out.name = anim.optString("name", "Animation");

            JSONArray samplers = anim.optJSONArray("samplers");
            if (samplers != null) {
                for (int s = 0; s < samplers.length(); s++) {
                    JSONObject samp = samplers.getJSONObject(s);
                    GLTFAnimation.Sampler ss = new GLTFAnimation.Sampler();
                    ss.inputTimes = toFloatArray(readAccessor(root, buffers, samp.getInt("input"), false, false));
                    ss.interpolation = samp.optString("interpolation", "LINEAR");

                    Object outVals = readAccessor(root, buffers, samp.getInt("output"), true, false);
                    ss.outputValues = toFloatArray(outVals);
                    ss.components = inferOutputComponents(root, samp.getInt("output"));

                    out.samplers.add(ss);
                    if (ss.inputTimes != null && ss.inputTimes.length > 0) {
                        out.duration = Math.max(out.duration, ss.inputTimes[ss.inputTimes.length - 1]);
                    }
                }
            }

            JSONArray channels = anim.optJSONArray("channels");
            if (channels != null) {
                for (int c = 0; c < channels.length(); c++) {
                    JSONObject ch = channels.getJSONObject(c);
                    JSONObject target = ch.getJSONObject("target");
                    GLTFAnimation.Channel cc = new GLTFAnimation.Channel();
                    cc.samplerIndex = ch.getInt("sampler");
                    cc.targetNode = target.getInt("node");
                    cc.targetPath = target.getString("path");
                    out.channels.add(cc);
                }
            }

            model.animations.add(out);
        }
    }

    private static float[] toFloatArray(Object obj) {
        if (obj == null) return null;
        if (obj instanceof float[]) return (float[]) obj;
        if (obj instanceof int[]) {
            int[] src = (int[]) obj;
            float[] out = new float[src.length];
            for (int i = 0; i < src.length; i++) out[i] = src[i];
            return out;
        }
        return null;
    }

    private static int inferOutputComponents(JSONObject root, int accessorIndex) throws Exception {
        JSONObject accessor = root.getJSONArray("accessors").getJSONObject(accessorIndex);
        String type = accessor.optString("type", "SCALAR");
        if ("VEC2".equals(type)) return 2;
        if ("VEC3".equals(type)) return 3;
        if ("VEC4".equals(type)) return 4;
        if ("MAT4".equals(type)) return 16;
        return 1;
    }

    private static float[] loadAllMeshVertices(JSONObject root, ByteBuffer[] buffers, GLTFModel model) throws Exception {
        JSONArray meshes = root.optJSONArray("meshes");
        if (meshes == null || meshes.length() == 0) return null;

        ArrayList<float[]> parts = new ArrayList<>();
        int total = 0;

        for (int m = 0; m < meshes.length(); m++) {
            JSONObject mesh = meshes.getJSONObject(m);
            JSONArray prims = mesh.optJSONArray("primitives");
            if (prims == null) continue;

            for (int p = 0; p < prims.length(); p++) {
                JSONObject prim = prims.getJSONObject(p);
                if (prim.optInt("mode", 4) != 4) continue;

                JSONObject attrs = prim.getJSONObject("attributes");
                int posAcc = attrs.getInt("POSITION");
                int normAcc = attrs.optInt("NORMAL", -1);
                int colAcc = attrs.optInt("COLOR_0", -1);
                int uvAcc = attrs.optInt("TEXCOORD_0", -1);
                int weightAcc = attrs.optInt("WEIGHTS_0", -1);
                int jointAcc = attrs.optInt("JOINTS_0", -1);
                int indexAcc = prim.has("indices") ? prim.getInt("indices") : -1;

                float[] pos = (float[]) readAccessor(root, buffers, posAcc, true, false);
                if (pos == null || pos.length == 0) continue;

                float[] nor = normAcc >= 0 ? (float[]) readAccessor(root, buffers, normAcc, true, false) : null;
                float[] col = colAcc >= 0 ? (float[]) readAccessor(root, buffers, colAcc, true, false) : null;
                float[] uv = uvAcc >= 0 ? (float[]) readAccessor(root, buffers, uvAcc, true, false) : null;
                float[] w = weightAcc >= 0 ? (float[]) readAccessor(root, buffers, weightAcc, true, false) : null;
                int[] j = jointAcc >= 0 ? (int[]) readAccessor(root, buffers, jointAcc, false, true) : null;
                int[] idx = indexAcc >= 0 ? (int[]) readAccessor(root, buffers, indexAcc, false, false) : null;

                int vertexCount = pos.length / 3;
                float[] expanded = buildPrimitiveVertices(pos, nor, col, uv, w, j, idx, vertexCount);
                if (expanded != null && expanded.length > 0) {
                    parts.add(expanded);
                    total += expanded.length / SkinnedMesh.STRIDE_FLOATS;
                }
            }
        }

        if (parts.isEmpty()) return null;
        float[] out = new float[total * SkinnedMesh.STRIDE_FLOATS];
        int off = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, out, off, part.length);
            off += part.length;
        }
        return out;
    }

    private static float[] buildPrimitiveVertices(
            float[] positions, float[] normals, float[] colors, float[] uvs,
            float[] weights, int[] joints, int[] indices, int vertexCount
    ) {
        int outCount = (indices != null && indices.length > 0) ? indices.length : vertexCount;
        float[] out = new float[outCount * SkinnedMesh.STRIDE_FLOATS];

        for (int i = 0; i < outCount; i++) {
            int v = (indices != null && indices.length > 0) ? indices[i] : i;
            if (v < 0 || v >= vertexCount) continue;

            int base = i * SkinnedMesh.STRIDE_FLOATS;
            int p = v * 3;

            out[base] = positions[p];
            out[base + 1] = positions[p + 1];
            out[base + 2] = positions[p + 2];

            if (normals != null && normals.length >= p + 3) {
                out[base + 3] = normals[p];
                out[base + 4] = normals[p + 1];
                out[base + 5] = normals[p + 2];
            } else {
                out[base + 3] = 0f;
                out[base + 4] = 1f;
                out[base + 5] = 0f;
            }

            if (colors != null) {
                int cIdx = (colors.length / vertexCount == 4) ? v * 4 : v * 3;
                if (colors.length >= cIdx + 4) {
                    out[base + 6] = colors[cIdx];
                    out[base + 7] = colors[cIdx + 1];
                    out[base + 8] = colors[cIdx + 2];
                    out[base + 9] = colors[cIdx + 3];
                } else if (colors.length >= cIdx + 3) {
                    out[base + 6] = colors[cIdx];
                    out[base + 7] = colors[cIdx + 1];
                    out[base + 8] = colors[cIdx + 2];
                    out[base + 9] = 1f;
                } else {
                    out[base + 6] = 1f;
                    out[base + 7] = 1f;
                    out[base + 8] = 1f;
                    out[base + 9] = 1f;
                }
            } else {
                out[base + 6] = 1f;
                out[base + 7] = 1f;
                out[base + 8] = 1f;
                out[base + 9] = 1f;
            }

            if (uvs != null && uvs.length >= v * 2 + 2) {
                out[base + 10] = uvs[v * 2];
                out[base + 11] = uvs[v * 2 + 1];
            }

            if (weights != null && weights.length >= v * 4 + 4) {
                float w0 = weights[v * 4];
                float w1 = weights[v * 4 + 1];
                float w2 = weights[v * 4 + 2];
                float w3 = weights[v * 4 + 3];
                float sum = w0 + w1 + w2 + w3;
                if (sum > 0.00001f) {
                    w0 /= sum; w1 /= sum; w2 /= sum; w3 /= sum;
                } else {
                    w0 = 1f; w1 = 0f; w2 = 0f; w3 = 0f;
                }
                out[base + 12] = w0;
                out[base + 13] = w1;
                out[base + 14] = w2;
                out[base + 15] = w3;
            } else {
                out[base + 12] = 1f;
                out[base + 13] = 0f;
                out[base + 14] = 0f;
                out[base + 15] = 0f;
            }

            if (joints != null && joints.length >= v * 4 + 4) {
                out[base + 16] = joints[v * 4];
                out[base + 17] = joints[v * 4 + 1];
                out[base + 18] = joints[v * 4 + 2];
                out[base + 19] = joints[v * 4 + 3];
            } else {
                out[base + 16] = 0f;
                out[base + 17] = 0f;
                out[base + 18] = 0f;
                out[base + 19] = 0f;
            }
        }

        return out;
    }

    private static void calcBounds(float[] vertices, GLTFModel model) {
        model.minX = model.minY = model.minZ = Float.MAX_VALUE;
        model.maxX = model.maxY = model.maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < vertices.length; i += SkinnedMesh.STRIDE_FLOATS) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];
            if (x < model.minX) model.minX = x;
            if (y < model.minY) model.minY = y;
            if (z < model.minZ) model.minZ = z;
            if (x > model.maxX) model.maxX = x;
            if (y > model.maxY) model.maxY = y;
            if (z > model.maxZ) model.maxZ = z;
        }
    }

    private static Object readAccessor(JSONObject root, ByteBuffer[] allBuffers, int accessorIndex, boolean normalizeFloat, boolean forceJointInt) {
        try {
            JSONObject accessor = root.getJSONArray("accessors").getJSONObject(accessorIndex);
            int bufferViewIndex = accessor.getInt("bufferView");
            int count = accessor.getInt("count");
            int componentType = accessor.getInt("componentType");
            String type = accessor.optString("type", "SCALAR");
            boolean normalized = accessor.optBoolean("normalized", false);

            int components = getTypeComponents(type);

            JSONObject bufferView = root.getJSONArray("bufferViews").getJSONObject(bufferViewIndex);
            int bufferIndex = bufferView.getInt("buffer");
            int viewOffset = bufferView.optInt("byteOffset", 0);
            int stride = bufferView.optInt("byteStride", 0);
            int accessorOffset = accessor.optInt("byteOffset", 0);
            int start = viewOffset + accessorOffset;

            ByteBuffer src = allBuffers[bufferIndex].duplicate().order(ByteOrder.LITTLE_ENDIAN);

            if (stride <= 0) {
                stride = getComponentSize(componentType) * components;
            }

            if (componentType == 5126) {
                float[] out = new float[count * components];
                for (int i = 0; i < count; i++) {
                    int pos = start + i * stride;
                    src.position(pos);
                    for (int c = 0; c < components; c++) {
                        out[i * components + c] = src.getFloat();
                    }
                }
                return out;
            }

            if (forceJointInt) {
                int[] out = new int[count * components];
                for (int i = 0; i < count; i++) {
                    int pos = start + i * stride;
                    src.position(pos);
                    for (int c = 0; c < components; c++) {
                        int value;
                        switch (componentType) {
                            case 5121:
                                value = src.get() & 0xFF;
                                break;
                            case 5123:
                                value = src.getShort() & 0xFFFF;
                                break;
                            case 5125:
                                value = src.getInt();
                                break;
                            default:
                                value = 0;
                                break;
                        }
                        out[i * components + c] = value;
                    }
                }
                return out;
            }

            if (componentType == 5121 || componentType == 5123) {
                float[] out = new float[count * components];
                float div = (componentType == 5123) ? 65535f : 255f;
                for (int i = 0; i < count; i++) {
                    int pos = start + i * stride;
                    src.position(pos);
                    for (int c = 0; c < components; c++) {
                        float value = (componentType == 5123) ? (src.getShort() & 0xFFFF) : (src.get() & 0xFF);
                        if (normalized || normalizeFloat) value /= div;
                        out[i * components + c] = value;
                    }
                }
                return out;
            }

            if (componentType == 5125) {
                int[] out = new int[count * components];
                for (int i = 0; i < count; i++) {
                    int pos = start + i * stride;
                    src.position(pos);
                    for (int c = 0; c < components; c++) {
                        out[i * components + c] = src.getInt();
                    }
                }
                return out;
            }

            throw new RuntimeException("Component type unsupported: " + componentType);
        } catch (Throwable e) {
            Log.e(TAG, "ACCESSOR READ FAILED", e);
            return null;
        }
    }

    private static int getTypeComponents(String type) {
        if ("SCALAR".equals(type)) return 1;
        if ("VEC2".equals(type)) return 2;
        if ("VEC3".equals(type)) return 3;
        if ("VEC4".equals(type)) return 4;
        if ("MAT4".equals(type)) return 16;
        return 1;
    }

    private static int getComponentSize(int componentType) {
        if (componentType == 5126 || componentType == 5125) return 4;
        if (componentType == 5123) return 2;
        if (componentType == 5121) return 1;
        return 4;
    }
}
