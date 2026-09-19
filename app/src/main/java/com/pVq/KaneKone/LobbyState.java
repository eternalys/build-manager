package com.pVq.KaneKone.game; 

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.List;

import com.pVq.KaneKone.ProfileManager; 
import com.pVq.KaneKone.game.WorldManager;

public class LobbyState {
    
    private Context context;
    private LinearLayout layout;
    private ProfileManager profileManager;
    private int selectedCharType = 0;

    public interface OnWorldSelected {
        void onPlay(WorldManager.WorldData worldData);
    }
    private OnWorldSelected callback;

    public LobbyState(Context ctx, OnWorldSelected cb) {
        this.context = ctx;
        this.callback = cb;
        try {
            this.profileManager = new ProfileManager(ctx);
            if (profileManager.isNewUser()) initRegisterUI();
            else initLobbyUI();
        } catch (Exception e) {
            Toast.makeText(ctx, "Lobby Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initRegisterUI() {
        layout = createBaseLayout();
        layout.addView(createText("IDENTITY REGISTRATION", 22, Color.GREEN));
        layout.addView(createSpacer(50));
        
        final EditText inputCode = new EditText(context);
        inputCode.setHint("ENTER CODENAME");
        inputCode.setHintTextColor(Color.DKGRAY);
        inputCode.setTextColor(Color.WHITE);
        inputCode.setBackgroundColor(Color.parseColor("#111111"));
        inputCode.setPadding(40, 30, 40, 30);
        inputCode.setGravity(Gravity.CENTER);
        inputCode.setWidth(600);
        layout.addView(inputCode);
        
        layout.addView(createSpacer(30));
        
        Button btnSave = createButton("ESTABLISH LINK", Color.CYAN);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = inputCode.getText().toString().trim();
                if (name.length() < 3) return;
                profileManager.setUsername(name);
               
                layout.removeAllViews();
                initLobbyUI();
            }
        });
        layout.addView(btnSave);
    }

    private void initLobbyUI() {
        if(layout == null) layout = createBaseLayout();
        layout.removeAllViews();

        String pilotName = profileManager.getUsername().toUpperCase();
        TextView title = createText("PILOT: " + pilotName, 20, Color.GREEN);
        layout.addView(title);
        
        layout.addView(createSpacer(30));
        layout.addView(createText("MISSION SELECT:", 14, Color.LTGRAY));
        layout.addView(createSpacer(20));

        ScrollView scroll = new ScrollView(context);
        LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setGravity(Gravity.CENTER);
        scroll.addView(listContainer);

        List<String> worlds = WorldManager.getWorldList(context);
        if (worlds.isEmpty()) {
            listContainer.addView(createText("- NO DATA FOUND -", 12, Color.GRAY));
        } else {
            for (final String wName : worlds) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                
                Button btnWorld = createButton("✈ " + wName, Color.DKGRAY);
                btnWorld.setTextColor(Color.LTGRAY);
                LinearLayout.LayoutParams pPlay = new LinearLayout.LayoutParams(400, 140);
                pPlay.setMargins(0, 0, 10, 0);
                btnWorld.setLayoutParams(pPlay);
                
                btnWorld.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startGame(wName); 
                    }
                });

                Button btnDel = createButton("X", Color.parseColor("#880000"));
                LinearLayout.LayoutParams pDel = new LinearLayout.LayoutParams(90, 140);
                btnDel.setLayoutParams(pDel);
                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmDelete(wName);
                    }
                });

                row.addView(btnWorld);
                row.addView(btnDel);
                listContainer.addView(row);
                listContainer.addView(createSpacer(10));
            }
        }
        
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(600, 500);
        scroll.setLayoutParams(scrollParams);
        layout.addView(scroll);

        layout.addView(createSpacer(30));

        Button btnNew = createButton("+ NEW EXPEDITION", Color.BLUE);
        btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateWorldDialog();
            }
        });
        layout.addView(btnNew);
    }
    
    private void startGame(String wName) {
        try {
            WorldManager.WorldData data = WorldManager.loadWorld(context, wName);
            if (data != null) {
                if (callback != null) {
                    callback.onPlay(data);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Start Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void confirmDelete(final String wName) {
        new AlertDialog.Builder(context)
            .setTitle("DELETE WORLD?")
            .setMessage("Permanently delete '" + wName + "'?")
            .setPositiveButton("DELETE", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    WorldManager.deleteWorld(context, wName);
                    initLobbyUI();
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void showCreateWorldDialog() {
        LinearLayout dialogLayout = new LinearLayout(context);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(40, 40, 40, 40);

        final EditText inputName = new EditText(context);
        inputName.setHint("World Name");
        inputName.setTextColor(Color.BLACK); 
        
        final EditText inputSeed = new EditText(context);
        inputSeed.setHint("Seed (Optional)");
        inputSeed.setTextColor(Color.BLACK);

        TextView lblChar = new TextView(context);
        lblChar.setText("SELECT UNIT:");
        lblChar.setTextColor(Color.BLACK);
        lblChar.setTypeface(null, Typeface.BOLD);
        
        final TextView txtPreview = new TextView(context);
        txtPreview.setText("EVA-01\nType: Heavy Mecha\nBalance: Balanced");
        txtPreview.setBackgroundColor(Color.LTGRAY);
        txtPreview.setTextColor(Color.DKGRAY);
        txtPreview.setPadding(20, 20, 20, 20);
        txtPreview.setTypeface(Typeface.MONOSPACE);

        final Button btnToggle = new Button(context);
        btnToggle.setText("SWITCH TO AZURA");
        btnToggle.setBackgroundColor(Color.parseColor("#005588"));
        btnToggle.setTextColor(Color.WHITE);
        
        selectedCharType = 0; 
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedCharType == 0) {
                    selectedCharType = 1;
                    btnToggle.setText("SWITCH TO EVA");
                    btnToggle.setBackgroundColor(Color.parseColor("#448844"));
                    txtPreview.setText("AZURA\nType: Humanoid\nStyle: Agile & Elegant");
                } else {
                    selectedCharType = 0;
                    btnToggle.setText("SWITCH TO AZURA");
                    btnToggle.setBackgroundColor(Color.parseColor("#005588"));
                    txtPreview.setText("EVA-01\nType: Heavy Mecha\nStyle: Balanced");
                }
            }
        });
        
        dialogLayout.addView(inputName);
        dialogLayout.addView(createSpacer(20));
        dialogLayout.addView(inputSeed);
        dialogLayout.addView(createSpacer(20));
        dialogLayout.addView(lblChar);
        dialogLayout.addView(txtPreview);
        dialogLayout.addView(btnToggle);

        new AlertDialog.Builder(context)
            .setTitle("Deploy New Unit")
            .setView(dialogLayout)
            .setPositiveButton("LAUNCH", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    String name = inputName.getText().toString().trim();
                    String seed = inputSeed.getText().toString().trim();
                    if(name.isEmpty()) name = "World_" + System.currentTimeMillis();
                    if(seed.isEmpty()) seed = String.valueOf(System.currentTimeMillis());

                    if (WorldManager.createWorld(context, name, seed, selectedCharType)) {
                        initLobbyUI(); 
                    } else {
                        Toast.makeText(context, "Failed to create world!", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }
    
    private LinearLayout createBaseLayout() {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.BLACK);
        
        l.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));
        
        return l;
    }

    private TextView createText(String text, float size, int color) {
        TextView t = new TextView(context);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button createButton(String text, int color) {
        Button b = new Button(context);
        b.setText(text); b.setBackgroundColor(color);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setLayoutParams(new LinearLayout.LayoutParams(500, 140));
        return b;
    }

    private View createSpacer(int height) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, height));
        return v;
    }

    public LinearLayout getView() { return layout; }
}
