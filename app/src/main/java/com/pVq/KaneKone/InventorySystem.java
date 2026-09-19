package com.pVq.KaneKone.game;

import java.util.ArrayList;
import java.util.List;

public class InventorySystem {

    // DEFINISI ITEM (Database kecil-kecilan)
    public static class ItemDef {
        int id;
        String name;
        String icon; // Emoji dulu
        int maxStack;
        float weight; // Dalam Kg
        
        public ItemDef(int id, String name, String icon, int maxStack, float weight) {
            this.id = id; this.name = name; this.icon = icon;
            this.maxStack = maxStack; this.weight = weight;
        }
    }

    // DATABASE ITEM
    public static final ItemDef[] ITEM_DB = {
        new ItemDef(0, "Gold Coin", "💰", 99, 0.005f), // 0: Gold (5 gram, tumpuk 99)
        new ItemDef(1, "HP Potion", "💊", 10, 0.2f),   // 1: Potion (200 gram, tumpuk 10)
        new ItemDef(2, "Mana Crystal", "💎", 5, 0.5f)  // 2: Mana (500 gram, tumpuk 5)
    };

    // DEFINISI SLOT TAS
    public static class Slot {
        public int itemId = -1; // -1 artinya Kosong
        public int count = 0;
        
        public boolean isEmpty() { return itemId == -1; }
    }

    // ISI TAS (20 Slot)
    public Slot[] slots;
    public int capacity = 20;

    public InventorySystem() {
        slots = new Slot[capacity];
        for (int i = 0; i < capacity; i++) {
            slots[i] = new Slot(); // Siapkan slot kosong
        }
    }

    // --- LOGIKA MENAMBAH ITEM (AAA LOGIC) ---
    // Return: Sisa item yang TIDAK muat (0 berarti masuk semua)
    public int addItem(int id, int amount) {
        ItemDef def = ITEM_DB[id];

        // 1. CEK STACKING: Cari slot yang isinya sama dan belum penuh
        for (int i = 0; i < capacity; i++) {
            if (slots[i].itemId == id && slots[i].count < def.maxStack) {
                int space = def.maxStack - slots[i].count;
                if (amount <= space) {
                    slots[i].count += amount;
                    return 0; // Masuk semua, selesai
                } else {
                    slots[i].count = def.maxStack; // Penuhin slot ini
                    amount -= space; // Sisa item lanjut cari slot lain
                }
            }
        }

        // 2. CEK SLOT KOSONG: Kalau gak bisa ditumpuk, cari slot baru
        for (int i = 0; i < capacity; i++) {
            if (slots[i].isEmpty()) {
                slots[i].itemId = id;
                if (amount <= def.maxStack) {
                    slots[i].count = amount;
                    return 0; // Masuk semua
                } else {
                    slots[i].count = def.maxStack;
                    amount -= def.maxStack; // Sisa item lanjut
                }
            }
        }

        return amount; // Balikin sisa item yang gak muat (Inventory Full)
    }
    
    // --- LOGIKA PAKAI ITEM ---
    public boolean useItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= capacity) return false;
        Slot s = slots[slotIndex];
        if (s.isEmpty()) return false;
        
        // Kurangi jumlah
        s.count--;
        if (s.count <= 0) {
            s.itemId = -1; // Kosongkan slot kalau habis
            s.count = 0;
        }
        return true; // Berhasil dipakai
    }
    
    // Helper: Hitung total item tertentu (untuk Quick Slot UI)
    public int countTotal(int id) {
        int total = 0;
        for(Slot s : slots) {
            if (s.itemId == id) total += s.count;
        }
        return total;
    }
}