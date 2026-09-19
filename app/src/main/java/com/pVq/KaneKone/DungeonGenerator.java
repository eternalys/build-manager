package com.pVq.KaneKone.game;

import java.util.Random;

public class DungeonGenerator {
    
    private static final int MAP_SIZE = 125; 

    public static int[][] generate(int width, int height) {
        int size = MAP_SIZE;
        int[][] map = new int[size][size];
        Random rand = new Random();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) { map[y][x] = 1; }
        }

        int startX = size / 2;
        int startY = size / 2;
        map[startY][startX] = 9; 
        
        for (int i = 0; i < 5; i++) {
            digTunnel(map, startX, startY, 600, rand); 
        }
        
        placeBossRoom(map, startX, startY);

        cleanupAndDecorate(map, rand);

        return map;
    }

    private static void digTunnel(int[][] map, int x, int y, int life, Random rand) {
        int cx = x; int cy = y;
        int dx = rand.nextInt(3) - 1; 
        int dy = rand.nextInt(3) - 1;
        if (dx == 0 && dy == 0) dx = 1;

        for (int i = 0; i < life; i++) {
            int brushSize = (rand.nextInt(10) > 7) ? 2 : 1; 
            carveCircle(map, cx, cy, brushSize);
            cx += dx; cy += dy;
            if (cx < 2 || cx >= map.length - 2 || cy < 2 || cy >= map.length - 2) {
                dx = -dx; dy = -dy; cx += dx * 2; cy += dy * 2;
            }
            if (rand.nextInt(100) < 10) { 
                dx = rand.nextInt(3) - 1; dy = rand.nextInt(3) - 1;
                if (dx == 0 && dy == 0) dx = (rand.nextBoolean()) ? 1 : -1;
            }
        }
    }

    private static void carveCircle(int[][] map, int cx, int cy, int radius) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (y >= 1 && y < map.length - 1 && x >= 1 && x < map.length - 1) {
                    if ((x - cx)*(x - cx) + (y - cy)*(y - cy) <= radius*radius + 1) {
                        map[y][x] = 0; 
                    }
                }
            }
        }
    }

    private static void placeBossRoom(int[][] map, int startX, int startY) {
        double maxDist = 0;
        int bossX = startX;
        int bossY = startY;
        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map.length; x++) {
                if (map[y][x] == 0) {
                    double dist = Math.sqrt(Math.pow(x - startX, 2) + Math.pow(y - startY, 2));
                    if (dist > maxDist) { maxDist = dist; bossX = x; bossY = y; }
                }
            }
        }
        int roomRad = 6;
        for (int y = bossY - roomRad; y <= bossY + roomRad; y++) {
            for (int x = bossX - roomRad; x <= bossX + roomRad; x++) {
                if (y > 1 && y < map.length - 1 && x > 1 && x < map.length - 1) {
                    map[y][x] = 0;
                }
            }
        }
        map[bossY][bossX] = 6; 
    }
    
    private static void cleanupAndDecorate(int[][] map, Random rand) {
        for (int y = 1; y < map.length - 1; y++) {
            for (int x = 1; x < map.length - 1; x++) {
                if (map[y][x] == 0) { 
                    int walls = 0;
                    if (map[y+1][x]==1) walls++; if (map[y-1][x]==1) walls++;
                    if (map[y][x+1]==1) walls++; if (map[y][x-1]==1) walls++;
                    
                    if (walls >= 3 && rand.nextInt(100) < 10) map[y][x] = 4;
                    else if (walls == 0 && rand.nextInt(100) < 5) map[y][x] = 2;
                    
                    if (rand.nextInt(100) < 1) {
                        int roll = rand.nextInt(10);
                        if (roll < 6) map[y][x] = 80;
                        else if (roll < 9) map[y][x] = 81;
                        else map[y][x] = 82;
                    }
                }
            }
        }
    }
}
