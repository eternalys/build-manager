package com.pVq.KaneKone;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout; // PENTING: Import ini wajib
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class LoadingState {
    
    private LinearLayout layout;
    private TextView statusText;
    private Context context;
    private Runnable onCheckSuccess;

    public LoadingState(Context ctx, Runnable successCallback) {
        this.context = ctx;
        this.onCheckSuccess = successCallback;
        try {
            setupUI();
            startSystemCheck();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(ctx, "Loading Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupUI() {
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.BLACK);
        
        // [FIX FORCE CLOSE] 
        // Menggunakan FrameLayout.LayoutParams karena parent di MainActivity adalah FrameLayout
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT)); 

        TextView title = new TextView(context);
        title.setText("SYSTEM BOOT");
        title.setTextColor(Color.GREEN);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        ProgressBar pb = new ProgressBar(context);
        layout.addView(pb);

        statusText = new TextView(context);
        statusText.setText("Connecting to server...");
        statusText.setTextColor(Color.WHITE);
        statusText.setGravity(Gravity.CENTER);
        layout.addView(statusText);
    }

    private void startSystemCheck() {
        // [ANTI CRASH] Menggunakan MetaManager dengan aman
        MetaManager.checkServer(context, new MetaManager.MetaCallback() {
            @Override
            public void onSuccess() {
                if (statusText != null) statusText.setText("Server Verified. Loading Assets...");
                
                // Delay sedikit agar user sempat baca
                if (layout != null) {
                    layout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (onCheckSuccess != null) onCheckSuccess.run();
                        }
                    }, 1000);
                }
            }

            @Override
            public void onMaintenance() {
                showError("SERVER MAINTENANCE", "System is currently under maintenance.");
            }

            @Override
            public void onUpdateRequired(String serverVer) {
                showError("VERSION MISMATCH", "Please update your app.");
            }

            @Override
            public void onError(String errorMsg) {
                showError("CONNECTION ERROR", errorMsg);
            }
        });
    }

    private void showError(String title, String msg) {
        if (statusText != null) {
            statusText.setTextColor(Color.RED);
            statusText.setText("ERROR: " + title + "\n" + msg);
        }
    }

    public LinearLayout getView() {
        return layout;
    }
}
