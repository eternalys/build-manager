package com.pVq.KaneKone;

import android.content.Context;
import android.content.SharedPreferences;

public class ProfileManager {
    private SharedPreferences prefs;
    private static final String PREF_NAME = "KaneKoneData";
    private static final String KEY_USER = "username";
    private static final String KEY_LAST_RENAME = "last_rename_time";

    public ProfileManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isNewUser() {
        // Kalau tidak ada username tersimpan, berarti user baru
        return prefs.getString(KEY_USER, null) == null;
    }

    public void setUsername(String name) {
        prefs.edit().putString(KEY_USER, name).apply();
    }

    public String getUsername() {
        return prefs.getString(KEY_USER, "Unknown");
    }

    public void updateName(String newName) {
        long now = System.currentTimeMillis();
        prefs.edit()
            .putString(KEY_USER, newName)
            .putLong(KEY_LAST_RENAME, now)
            .apply();
    }

    public boolean canChangeName() {
        long lastRename = prefs.getLong(KEY_LAST_RENAME, 0);
        long now = System.currentTimeMillis();
        long diff = now - lastRename;
        long sevenDays = 7L * 24 * 60 * 60 * 1000;
        
        return diff > sevenDays;
    }

    public long getRemainingCooldown() {
        long lastRename = prefs.getLong(KEY_LAST_RENAME, 0);
        long now = System.currentTimeMillis();
        long sevenDays = 7L * 24 * 60 * 60 * 1000;
        return sevenDays - (now - lastRename);
    }
}
