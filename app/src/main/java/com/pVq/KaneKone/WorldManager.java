package com.pVq.KaneKone.game;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class WorldManager {

    public static class WorldData {
        public String name;
        public long seed;
        public float x, y, z;
        public float rotY;
        public int charType = 0; 

        public WorldData(String name, long seed, int charType) {
            this.name = name;
            this.seed = seed;
            this.charType = charType;
            this.x = 0; this.y = 0; this.z = 0;
            this.rotY = 0;
        }
    }

    private static File getWorldsDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "Worlds");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // --- CREATE WORLD ---
    public static boolean createWorld(Context context, String worldName, String seedString, int charType) {
        try {
            long seed = seedString.hashCode();
            try { seed = Long.parseLong(seedString); } catch (Exception e) {}

            File root = getWorldsDir(context);
            File worldDir = new File(root, worldName);
            
            if (!worldDir.exists()) {
                if (!worldDir.mkdirs()) return false;
            }

            WorldData data = new WorldData(worldName, seed, charType);
            
            // Simpan Data Player (packet.json)
            return saveWorldData(context, data);

        } catch (Exception e) { return false; }
    }

    // --- LOAD DATA ---
    public static WorldData loadWorld(Context context, String worldName) {
        try {
            File root = getWorldsDir(context);
            File filePacket = new File(root, worldName + "/packet.json");
            if (!filePacket.exists()) return null;

            BufferedReader reader = new BufferedReader(new FileReader(filePacket));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject jsonPacket = new JSONObject(sb.toString());
            int cType = jsonPacket.optInt("charType", 0); 
            
            WorldData data = new WorldData(worldName, jsonPacket.getLong("seed"), cType);
            data.x = (float)jsonPacket.optDouble("x", 0);
            data.y = (float)jsonPacket.optDouble("y", 0);
            data.z = (float)jsonPacket.optDouble("z", 0);
            data.rotY = (float)jsonPacket.optDouble("rot", 0);
            
            return data;
        } catch (Exception e) { return null; }
    }

    // --- SAVE PLAYER DATA ---
    public static boolean saveWorldData(Context context, WorldData data) {
        try {
            JSONObject json = new JSONObject();
            json.put("seed", data.seed);
            json.put("x", (double)data.x);
            json.put("y", (double)data.y);
            json.put("z", (double)data.z);
            json.put("rot", (double)data.rotY);
            json.put("charType", data.charType);

            File root = getWorldsDir(context);
            File file = new File(root, data.name + "/packet.json");
            
            FileWriter writer = new FileWriter(file);
            writer.write(json.toString());
            writer.flush();
            writer.close();
            return true;
        } catch (Exception e) { return false; }
    }

    // --- DELETE & LIST ---
    public static void deleteWorld(Context context, String worldName) {
        File root = getWorldsDir(context);
        File worldDir = new File(root, worldName);
        deleteRecursive(worldDir);
    }
    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles()) deleteRecursive(child);
        fileOrDirectory.delete();
    }
    public static List<String> getWorldList(Context context) {
        List<String> worlds = new ArrayList<>();
        File root = getWorldsDir(context);
        File[] files = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    if (new File(f, "packet.json").exists()) worlds.add(f.getName());
                }
            }
        }
        return worlds;
    }
}
