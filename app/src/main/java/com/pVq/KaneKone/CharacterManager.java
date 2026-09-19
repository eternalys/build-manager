package com.pVq.KaneKone.game;

import java.util.ArrayList;
import java.util.List;

public class CharacterManager {
    private final List<CharacterEntity> characters = new ArrayList<>();
    private GLBLoader.GLTFModel sharedModel;

    public void setSharedModel(GLBLoader.GLTFModel model) {
        this.sharedModel = model;
    }

    public GLBLoader.GLTFModel getSharedModel() {
        return sharedModel;
    }

    public CharacterEntity spawnShared(float x, float y, float z, float rotationY, float scale, int type, boolean ai) {
        if (sharedModel == null) return null;
        CharacterEntity e = new CharacterEntity(sharedModel);
        e.setPosition(x, y, z);
        e.rotationY = rotationY;
        e.scale = scale;
        e.state = type;
        e.setAI(ai);
        characters.add(e);
        return e;
    }

    public void clear() {
        characters.clear();
    }

    public List<CharacterEntity> all() {
        return characters;
    }

    public void drawVisibleInRange(int program, float[] viewMatrix, float[] projectionMatrix, float camX, float camZ, float maxDistance) {
        float maxSq = maxDistance * maxDistance;
        for (CharacterEntity c : characters) {
            if (c == null || !c.visible) continue;
            float dx = camX - c.x;
            float dz = camZ - c.z;
            if (dx * dx + dz * dz > maxSq) continue;
            c.draw(program, viewMatrix, projectionMatrix);
        }
    }
}
