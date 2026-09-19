package com.pVq.KaneKone.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

public class TerrainEngine {

    public Map<String, Chunk> chunks = new HashMap<>();
    
    // UPDATE: Jarak Pandang JAUH (8 Chunk = 128 Meter)
    // Agar efek kabut terasa "Isekai" dan tidak sempit
    private int renderDistance = 8; 

    public TerrainEngine() {
    }
    
    public void setRenderDistance(int dist) {
        this.renderDistance = Math.max(1, dist / 16);
    }
    
    public float getHeight(float x, float z) {
        return Gods.getSmoothHeight(x, z);
    }

    public void update(float playerX, float playerZ) {
        int pCX = (int) Math.floor(playerX / 16.0f);
        int pCZ = (int) Math.floor(playerZ / 16.0f);

        // Load Chunk Baru
        for (int x = pCX - renderDistance; x <= pCX + renderDistance; x++) {
            for (int z = pCZ - renderDistance; z <= pCZ + renderDistance; z++) {
                String key = x + "," + z;
                if (!chunks.containsKey(key)) {
                    chunks.put(key, new Chunk(x, z));
                }
            }
        }
        
        // OPTIMASI: Hapus Chunk yang terlalu jauh (Biar RAM gak meledak)
        Iterator<Map.Entry<String, Chunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Chunk> entry = it.next();
            String[] parts = entry.getKey().split(",");
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);
            
            // Jika jarak > renderDistance + 2 (buffer), hapus
            if (Math.abs(cx - pCX) > renderDistance + 2 || Math.abs(cz - pCZ) > renderDistance + 2) {
                it.remove();
            }
        }
    }

    public void draw(int program) {
        for (Chunk chunk : chunks.values()) {
            chunk.draw(program);
        }
    }
}
