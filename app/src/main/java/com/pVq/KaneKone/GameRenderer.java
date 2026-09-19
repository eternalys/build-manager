package com.pVq.KaneKone.game;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.widget.Toast;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GameRenderer implements GLSurfaceView.Renderer {

    public float joyX, joyY;
    public CameraRig camera;
    public TerrainEngine land;
    public DungeonEngine dungeon;
    public ItemManager itemManager;
    public PlayerBody player;
    public WorldManager.WorldData currentData;

    public GLBLoader.GLTFModel gltfModel;
    public final CharacterManager characterManager = new CharacterManager();
    public CharacterEntity mainCharacter;

    public boolean inDungeon = false;
    private final Context context;

    private final float[] vPMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];

    public static int mProgram;
    private long lastTime;
    private float timeUniform = 0f;

    private static final float[] SKY_COLOR_WORLD = {0.53f, 0.81f, 0.92f, 1.0f};
    private static final float[] SKY_COLOR_DUNGEON = {0.05f, 0.02f, 0.15f, 1.0f};

    public GameRenderer(Context context, WorldManager.WorldData data) {
        this.context = context;
        this.currentData = data;
        this.player = new PlayerBody();
        if (data != null) {
            this.player.teleport(data.x, data.y, data.z);
            this.player.rotation = data.rotY;
        } else {
            this.player.teleport(0, 50, 0);
        }
        this.camera = new CameraRig();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        String vertexShaderCode =
                "uniform mat4 uMVPMatrix;" +
                "uniform mat4 uModelMatrix;" +
                "uniform float uType;" +
                "uniform float uTime;" +
                "uniform mat4 u_BoneMatrices[60];" +
                "attribute vec4 vPosition;" +
                "attribute vec3 vNormal;" +
                "attribute vec4 av_Color;" +
                "attribute vec2 a_UV;" +
                "attribute vec4 a_Weights;" +
                "attribute vec4 a_JointIds;" +
                "varying vec4 fColor;" +
                "varying vec3 fNormal;" +
                "varying vec3 fWorldPos;" +
                "varying float fDist;" +
                "void main() {" +
                "  vec4 pos = vPosition;" +
                "  vec3 nrm = vNormal;" +
                "  if (uType > 1.5) {" +
                "     ivec4 j = ivec4(a_JointIds);" +
                "     mat4 b1 = u_BoneMatrices[j.x];" +
                "     mat4 b2 = u_BoneMatrices[j.y];" +
                "     mat4 b3 = u_BoneMatrices[j.z];" +
                "     mat4 b4 = u_BoneMatrices[j.w];" +
                "     pos = (b1 * vPosition) * a_Weights.x +" +
                "           (b2 * vPosition) * a_Weights.y +" +
                "           (b3 * vPosition) * a_Weights.z +" +
                "           (b4 * vPosition) * a_Weights.w;" +
                "     nrm = normalize(" +
                "           (b1 * vec4(vNormal, 0.0)).xyz * a_Weights.x +" +
                "           (b2 * vec4(vNormal, 0.0)).xyz * a_Weights.y +" +
                "           (b3 * vec4(vNormal, 0.0)).xyz * a_Weights.z +" +
                "           (b4 * vec4(vNormal, 0.0)).xyz * a_Weights.w);" +
                "  } else if (uType < 0.5) {" +
                "     pos.y += sin(uTime + pos.x * 0.5 + pos.z * 0.5) * 0.05;" +
                "     nrm = vec3(0.0, 1.0, 0.0);" +
                "  } else {" +
                "     nrm = normalize((uModelMatrix * vec4(vNormal, 0.0)).xyz);" +
                "  }" +
                "  fColor = av_Color;" +
                "  fWorldPos = (uModelMatrix * pos).xyz;" +
                "  gl_Position = uMVPMatrix * pos;" +
                "  fDist = gl_Position.z;" +
                "  fNormal = nrm;" +
                "}";

        String fragmentShaderCode =
                "precision mediump float;" +
                "varying vec4 fColor;" +
                "varying vec3 fNormal;" +
                "varying vec3 fWorldPos;" +
                "varying float fDist;" +
                "uniform vec3 uCamPos;" +
                "uniform vec4 uSkyColor;" +
                "uniform float uFogStart;" +
                "uniform float uFogEnd;" +
                "uniform vec4 vColor;" +
                "void main() {" +
                "  vec3 lightDir = normalize(vec3(0.5, 1.0, 0.8));" +
                "  float diff = max(dot(normalize(fNormal), lightDir), 0.0);" +
                "  float intensity = smoothstep(0.2, 0.5, diff) * 0.4 + 0.6;" +
                "  vec3 shadowTint = vec3(0.1, 0.05, 0.2);" +
                "  vec3 baseCol = fColor.rgb * vColor.rgb;" +
                "  vec3 litCol = baseCol * intensity;" +
                "  vec3 finalCol = litCol + (shadowTint * (1.0 - intensity));" +
                "  float fogFactor = clamp((uFogEnd - fDist) / (uFogEnd - uFogStart), 0.0, 1.0);" +
                "  gl_FragColor = mix(uSkyColor, vec4(finalCol, fColor.a), fogFactor);" +
                "}";

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);

        land = new TerrainEngine();
        dungeon = new DungeonEngine();
        itemManager = new ItemManager();

        try {
            gltfModel = GLBLoader.loadGLTF(context, "Human.glb");
            if (gltfModel != null && gltfModel.mesh != null) {
                characterManager.setSharedModel(gltfModel);
                mainCharacter = characterManager.spawnShared(player.x, player.y, player.z, player.rotation, 1.15f, CharacterType.PLAYER, false);
            } else {
                Toast.makeText(context, "Human.gltf gagal dimuat", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable e) {
            final String msg = e.toString();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "GAGAL LOAD: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        }

        spawnExampleNPCs();
        lastTime = System.currentTimeMillis();
    }

    private void spawnExampleNPCs() {
        if (characterManager.getSharedModel() == null) return;
        for (int i = 0; i < 8; i++) {
            float px = (i * 5.5f) - 14f;
            float pz = (i % 2 == 0) ? 6f : -6f;
            CharacterEntity npc = characterManager.spawnShared(px, Gods.getSmoothHeight(px, pz), pz, 0f, 1.15f, CharacterType.NPC, true);
            if (npc != null) npc.moveSpeed = 0.9f;
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / (float) height;
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 1, 200);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastTime) / 1000.0f, 0.1f);
        lastTime = now;
        timeUniform = (timeUniform + dt) % 10000f;

        if (!inDungeon && land != null && player != null) land.update(player.x, player.z);

        float camYaw = camera.getYaw();
        float ground = inDungeon ? DungeonEngine.FLOOR_Y : Gods.getSmoothHeight(player.x, player.z);
        player.tick(joyX, joyY, camYaw, dt, ground, inDungeon ? dungeon : null);

        boolean moving = Math.abs(joyX) > 0.1f || Math.abs(joyY) > 0.1f;
        boolean running = Math.abs(joyX) > 0.7f || Math.abs(joyY) > 0.7f;
        boolean airborne = !player.onGround;
        boolean jumping = airborne && player.velocityY > 0.1f;
        boolean attacking = player.isAttacking;

        if (mainCharacter != null) {
            mainCharacter.setPosition(player.x, player.y, player.z);
            mainCharacter.rotationY = player.rotation;
            mainCharacter.scale = 1.15f;
            mainCharacter.update(moving, running, airborne, jumping, attacking, dt);
        }

        for (CharacterEntity c : characterManager.all()) {
            if (c == null || c == mainCharacter) continue;
            c.y = Gods.getSmoothHeight(c.x, c.z);
            c.update(false, false, false, false, false, dt);
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearColor(
                inDungeon ? SKY_COLOR_DUNGEON[0] : SKY_COLOR_WORLD[0],
                inDungeon ? SKY_COLOR_DUNGEON[1] : SKY_COLOR_WORLD[1],
                inDungeon ? SKY_COLOR_DUNGEON[2] : SKY_COLOR_WORLD[2],
                1f
        );

        GLES20.glUseProgram(mProgram);
        setupCamera();

        int mvpLoc = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, vPMatrix, 0);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgram, "uTime"), timeUniform);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgram, "uFogStart"), inDungeon ? 5f : 20f);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgram, "uFogEnd"), inDungeon ? 30f : 90f);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(mProgram, "vColor"), 1f, 1f, 1f, 1f);
        GLES20.glUniform4fv(GLES20.glGetUniformLocation(mProgram, "uSkyColor"), 1, inDungeon ? SKY_COLOR_DUNGEON : SKY_COLOR_WORLD, 0);

        float dist = camera.distance;
        float rYaw = (float) Math.toRadians(camera.getYaw());
        float rPit = (float) Math.toRadians(camera.getPitch());
        float cY = (float) Math.sin(rPit) * dist;
        float hD = (float) Math.cos(rPit) * dist;
        GLES20.glUniform3f(GLES20.glGetUniformLocation(mProgram, "uCamPos"),
                player.getRenderX(1f) - (float)Math.sin(rYaw) * hD,
                player.getRenderY(1f) + 1.8f + cY,
                player.getRenderZ(1f) - (float)Math.cos(rYaw) * hD);

        GLES20.glUniform1f(GLES20.glGetUniformLocation(mProgram, "uType"), 0.0f);

        if (inDungeon) {
            if (dungeon != null) dungeon.draw(mProgram);
            if (itemManager != null) itemManager.draw(viewMatrix, projectionMatrix, mProgram, timeUniform);
        } else {
            if (land != null) land.draw(mProgram);
        }

        characterManager.drawVisibleInRange(mProgram, viewMatrix, projectionMatrix, player.x, player.z, 90f);
    }

    private void setupCamera() {
        if (camera == null || player == null) return;
        float rYaw = (float) Math.toRadians(camera.getYaw());
        float rPit = (float) Math.toRadians(camera.getPitch());
        float hD = (float) Math.cos(rPit) * camera.distance;
        float targetY = player.getRenderY(1f) + 2f;
        Matrix.setLookAtM(viewMatrix, 0,
                player.getRenderX(1f) - (float)Math.sin(rYaw) * hD,
                targetY + (float)Math.sin(rPit) * camera.distance,
                player.getRenderZ(1f) - (float)Math.cos(rYaw) * hD,
                player.getRenderX(1f), targetY, player.getRenderZ(1f),
                0f, 1f, 0f);
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
    }

    private int loadShader(int type, String code) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, code);
        GLES20.glCompileShader(s);
        return s;
    }

    public void enterDungeon() {
        inDungeon = true;
        if (player != null && dungeon != null) player.teleport(dungeon.getStartX(), DungeonEngine.FLOOR_Y + 0.5f, dungeon.getStartZ());
        if (camera != null) camera.reset();
    }

    public void exitDungeon() {
        inDungeon = false;
        if (player != null) player.teleport(0, 50, 0);
        if (camera != null) camera.reset();
    }

    public boolean isItemNearby() {
        return inDungeon && itemManager != null && player != null && itemManager.tryPickup(player.x, player.y, player.z) != -1;
    }

    public int tryPickupItem() {
        return (inDungeon && itemManager != null && player != null) ? itemManager.tryPickup(player.x, player.y, player.z) : -1;
    }
}
