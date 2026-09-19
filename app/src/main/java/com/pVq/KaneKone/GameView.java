package com.pVq.KaneKone.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class GameView {

    private Context context;
    private FrameLayout container;
    private GLSurfaceView glView;
    private GameRenderer renderer;
    private JoystickOverlay joystick;
    
    // SYSTEMS
    private InventorySystem inventory; 
    private InventoryUI inventoryUI;

    // HUD ELEMENTS
    private Button btnPickup; 
    private Button btnQuickSlot; 
    private Button btnAttack;
    private Button btnJump;
    private ProgressBar barHP;
    private ProgressBar barMana;

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable uiUpdater;

    public GameView(Context ctx, WorldManager.WorldData data) {
        this.context = ctx;
        try {
            inventory = new InventorySystem(); 
            setupEngine(data);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(ctx, "CRITICAL ERROR: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupEngine(WorldManager.WorldData data) {
        container = new FrameLayout(context);
        glView = new GLSurfaceView(context);
        glView.setEGLContextClientVersion(2);
        glView.setPreserveEGLContextOnPause(true);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        renderer = new GameRenderer(context, data); 
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        
        container.addView(glView);
        
        joystick = new JoystickOverlay(context);
        joystick.setRenderer(renderer);
        container.addView(joystick);

        inventoryUI = new InventoryUI(context, inventory, renderer);
        container.addView(inventoryUI.getView());

        setupHUD(); 
        startUIUpdateLoop();
    }
    
    private void startUIUpdateLoop() {
        uiUpdater = new Runnable() {
            @Override
            public void run() {
                try {
                    if (renderer != null && renderer.player != null) {
                        boolean near = renderer.isItemNearby();
                        if (near) {
                            if (btnPickup.getVisibility() != View.VISIBLE) btnPickup.setVisibility(View.VISIBLE);
                        } else {
                            if (btnPickup.getVisibility() != View.GONE) btnPickup.setVisibility(View.GONE);
                        }
                        
                        if (barHP != null) {
                            barHP.setMax(renderer.player.maxHP);
                            barHP.setProgress(renderer.player.currentHP);
                        }

                        if (barMana != null) {
                            barMana.setMax(renderer.player.maxMana);
                            barMana.setProgress(renderer.player.currentMana);
                        }

                        if (btnQuickSlot != null) {
                            int potionCount = inventory.countTotal(1);
                            btnQuickSlot.setText(String.valueOf(potionCount));
                        }
                    }
                } catch (Exception e) {}
                uiHandler.postDelayed(this, 100);
            }
        };
        uiHandler.post(uiUpdater);
    }
    
    private void tryPickup() {
        if (renderer == null) return;
        int id = renderer.tryPickupItem(); 
        if (id != -1) {
            int amount = (id == 0) ? 5 : 1; 
            int leftover = inventory.addItem(id, amount);
            
            if (leftover == 0) {
                InventorySystem.ItemDef def = InventorySystem.ITEM_DB[id];
                showToast("Got " + amount + "x " + def.name);
            } else {
                showToast("INVENTORY FULL!");
            }
            
            if (inventoryUI != null && inventoryUI.isVisible()) {
                inventoryUI.refreshGrid();
            }
        }
    }

    private void setupHUD() {
        LinearLayout statsContainer = new LinearLayout(context);
        statsContainer.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams pStats = new FrameLayout.LayoutParams(400, -2);
        pStats.setMargins(20, 20, 0, 0);
        statsContainer.setLayoutParams(pStats);

        TextView lblHP = new TextView(context); lblHP.setText("HP"); lblHP.setTextColor(Color.WHITE);
        lblHP.setTypeface(null, Typeface.BOLD);
        statsContainer.addView(lblHP);
        barHP = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        barHP.getProgressDrawable().setColorFilter(Color.RED, android.graphics.PorterDuff.Mode.SRC_IN);
        barHP.setScaleY(1.5f);
        statsContainer.addView(barHP);

        TextView lblMana = new TextView(context); lblMana.setText("MP"); lblMana.setTextColor(Color.CYAN);
        lblMana.setTypeface(null, Typeface.BOLD);
        statsContainer.addView(lblMana);
        barMana = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        barMana.getProgressDrawable().setColorFilter(Color.BLUE, android.graphics.PorterDuff.Mode.SRC_IN);
        barMana.setScaleY(1.5f);
        statsContainer.addView(barMana);
        
        container.addView(statsContainer);

        LinearLayout actionContainer = new LinearLayout(context);
        actionContainer.setOrientation(LinearLayout.HORIZONTAL);
        actionContainer.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2);
        params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        params.setMargins(0, 0, 30, 30); 
        actionContainer.setLayoutParams(params);

        btnQuickSlot = createImageButton("ic_potion"); 
        btnQuickSlot.setText("0");
        btnQuickSlot.setTextColor(Color.WHITE);
        btnQuickSlot.setTextSize(14);
        btnQuickSlot.setTypeface(null, Typeface.BOLD);
        btnQuickSlot.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        btnQuickSlot.setPadding(0,0,10,10);
        
        btnQuickSlot.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (renderer == null || renderer.player == null) return;
                for(int i=0; i<inventory.capacity; i++) {
                    if (inventory.slots[i].itemId == 1) { 
                        if (renderer.player.currentHP < renderer.player.maxHP) {
                            inventory.useItem(i);
                            renderer.player.heal(30);
                            showToast("HP Restored.");
                            if(inventoryUI != null && inventoryUI.isVisible()) inventoryUI.refreshGrid();
                        } else {
                            showToast("HP Full!");
                        }
                        return;
                    }
                }
                showToast("No Potion!");
            }
        });
        actionContainer.addView(btnQuickSlot);

        TextView s0 = new TextView(context); s0.setWidth(20); actionContainer.addView(s0);
        
        btnPickup = createImageButton("ic_hand");
        btnPickup.setVisibility(View.GONE);
        btnPickup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tryPickup(); }
        });
        actionContainer.addView(btnPickup);
        
        TextView s1 = new TextView(context); s1.setWidth(20); actionContainer.addView(s1);

        btnAttack = createImageButton("ic_attack");
        btnAttack.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(renderer != null && renderer.player != null) {
                    if(event.getAction()==MotionEvent.ACTION_DOWN) renderer.player.isAttacking=true;
                    if(event.getAction()==MotionEvent.ACTION_UP) renderer.player.isAttacking=false;
                }
                return true;
            }
        });
        actionContainer.addView(btnAttack);

        TextView s2 = new TextView(context); s2.setWidth(20); actionContainer.addView(s2);

        btnJump = createImageButton("ic_jump");
        btnJump.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction()==MotionEvent.ACTION_DOWN && renderer!=null) {
                    glView.queueEvent(new Runnable() { 
                        @Override public void run() { 
                           if (renderer != null && renderer.player != null) renderer.player.jump(); 
                        }
                    });
                }
                return true;
            }
        });
        actionContainer.addView(btnJump);
        container.addView(actionContainer);
        
        setupTopButtons();
    }
    
    private void setupTopButtons() {
        LinearLayout topContainer = new LinearLayout(context);
        topContainer.setOrientation(LinearLayout.HORIZONTAL);
        topContainer.setGravity(Gravity.RIGHT);
        FrameLayout.LayoutParams paramsTop = new FrameLayout.LayoutParams(-1, -2);
        paramsTop.setMargins(0, 20, 20, 0);
        topContainer.setLayoutParams(paramsTop);

        Button btnBag = createSmallImageButton("ic_bag");
        btnBag.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { 
                if(inventoryUI != null) inventoryUI.toggle(); 
            }
        });
        topContainer.addView(btnBag);
        
        TextView sp1 = new TextView(context); sp1.setWidth(20); topContainer.addView(sp1);
        
        Button btnDungeon = createSmallImageButton("ic_door");
        btnDungeon.setOnClickListener(new View.OnClickListener() { 
            @Override public void onClick(View v) { 
                glView.queueEvent(new Runnable() { 
                    @Override public void run() { 
                        if(renderer != null && renderer.inDungeon) renderer.exitDungeon(); 
                        else if (renderer != null) renderer.enterDungeon(); 
                    }
                }); 
            }
        });
        topContainer.addView(btnDungeon);

        TextView sp2 = new TextView(context); sp2.setWidth(20); topContainer.addView(sp2);

        Button btnSet = createSmallImageButton("ic_settings");
        btnSet.setOnClickListener(new View.OnClickListener() { 
            @Override public void onClick(View v) { showSettingsDialog(); }
        });
        topContainer.addView(btnSet);

        container.addView(topContainer);
    }
    
    private void showToast(String msg) { 
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
    
    private void showSettingsDialog() {
        if (!(context instanceof Activity)) return;
        final CharSequence[] items = {"Jarak: Dekat", "Jarak: Jauh", "Save & Exit"};
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int item) {
                if (renderer == null) return; 
                
                if (item == 0) {
                    glView.queueEvent(new Runnable() {
                        @Override public void run() { 
                            if(renderer.land != null) renderer.land.setRenderDistance(30); 
                        }
                    });
                }
                if (item == 1) {
                    glView.queueEvent(new Runnable() {
                        @Override public void run() { 
                            if(renderer.land != null) renderer.land.setRenderDistance(60); 
                        }
                    });
                }
                if (item == 2) {
                    saveGame();
                    if (uiHandler != null) uiHandler.removeCallbacks(uiUpdater);
                    ((Activity)context).finish();
                }
            }
        });
        builder.show();
    }

    private void saveGame() {
        if (renderer != null && renderer.currentData != null && renderer.player != null) {
            renderer.currentData.x = renderer.player.x;
            renderer.currentData.y = renderer.player.y;
            renderer.currentData.z = renderer.player.z;
            WorldManager.saveWorldData(context, renderer.currentData);
        }
    }

    private int getResId(String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    private Button createImageButton(String imgName) {
        Button b = new Button(context);
        int resId = getResId(imgName);
        if (resId != 0) {
            b.setBackgroundResource(resId);
        } else {
            b.setText("?");
            b.setBackgroundColor(Color.RED);
        }
        b.setLayoutParams(new LinearLayout.LayoutParams(150, 150)); 
        return b;
    }
    
    private Button createSmallImageButton(String imgName) {
        Button b = new Button(context);
        int resId = getResId(imgName);
        if (resId != 0) b.setBackgroundResource(resId);
        else b.setBackgroundColor(Color.GRAY);
        b.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
        return b;
    }

    public View getView() { return container; }
    public void onPause() { if (glView != null) glView.onPause(); }
    public void onResume() { if (glView != null) glView.onResume(); }
}
