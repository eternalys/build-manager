package com.pVq.KaneKone;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

public class MetaManager {

    // Versi Aplikasi
    private static final String CURRENT_APP_VERSION = "0.0.1";

    public interface MetaCallback {
        void onSuccess();
        void onMaintenance();
        void onUpdateRequired(String serverVersion);
        void onError(String errorMsg);
    }

    // [ANTI CRASH] Tidak lagi memaksa minta Activity!
    // Kita ganti parameternya jadi object biasa, karena kita pakai Handler.
    public static void checkServer(Object context, final MetaCallback callback) {
        
        // Handler ini memaksa kode berjalan di Thread Utama (UI Thread)
        // Jadi aman untuk update UI, tanpa perlu Activity.runOnUiThread
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    // LOGIKA BYPASS (Offline Mode)
                    // Langsung panggil sukses
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } catch (Exception e) {
                    // Kalau ada error aneh, tangkap di sini biar gak force close
                    if (callback != null) {
                        callback.onError("Meta Error: " + e.getMessage());
                    }
                }
            }
        });
    }
}
