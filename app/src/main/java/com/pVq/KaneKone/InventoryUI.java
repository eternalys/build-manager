package com.pVq.KaneKone.game;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class InventoryUI {

    private Context context;
    private InventorySystem inventory;
    private GameRenderer renderer;

    private LinearLayout panel;
    private List<Button> slotButtons = new ArrayList<>();
    private TextView txtItemDetail;
    private Button btnUseItem;
    
    private int selectedSlotIndex = -1;
    private boolean isOpen = false;

    public InventoryUI(Context context, InventorySystem inv, GameRenderer renderer) {
        this.context = context;
        this.inventory = inv;
        this.renderer = renderer;
        setupWindow();
    }

    private void setupWindow() {
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.parseColor("#EE111111")); 
        panel.setPadding(20, 20, 20, 20);
        panel.setVisibility(View.GONE);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(800, 750);
        params.gravity = Gravity.CENTER;
        panel.setLayoutParams(params);

        TextView title = new TextView(context);
        title.setText("🎒 TACTICAL BAG");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        
        LinearLayout gridContainer = new LinearLayout(context);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setGravity(Gravity.CENTER);
        
        int slotCounter = 0;
        for (int row = 0; row < 5; row++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            
            for (int col = 0; col < 4; col++) {
                final int idx = slotCounter;
                Button slotBtn = new Button(context);
                slotBtn.setBackgroundColor(Color.parseColor("#333333"));
                slotBtn.setTextColor(Color.WHITE);
                slotBtn.setTextSize(12);
                
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(160, 100);
                btnParams.setMargins(5, 5, 5, 5);
                slotBtn.setLayoutParams(btnParams);
                slotBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { selectSlot(idx); }
                });
                rowLayout.addView(slotBtn);
                slotButtons.add(slotBtn);
                slotCounter++;
            }
            gridContainer.addView(rowLayout);
        }
        panel.addView(gridContainer);
        
        txtItemDetail = new TextView(context);
        txtItemDetail.setText("Select an item...");
        txtItemDetail.setTextColor(Color.LTGRAY);
        txtItemDetail.setGravity(Gravity.CENTER);
        txtItemDetail.setPadding(0, 20, 0, 10);
        panel.addView(txtItemDetail);
        
        btnUseItem = new Button(context);
        btnUseItem.setText("USE ITEM");
        btnUseItem.setBackgroundColor(Color.DKGRAY);
        btnUseItem.setTextColor(Color.WHITE);
        btnUseItem.setVisibility(View.INVISIBLE);
        btnUseItem.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { useSelectedItem(); }
        });
        panel.addView(btnUseItem);
    }
    
    public View getView() { return panel; }
    public boolean isVisible() { return isOpen; }

    public void toggle() {
        if (isOpen) {
            panel.setVisibility(View.GONE);
            isOpen = false;
        } else {
            refreshGrid();
            panel.setVisibility(View.VISIBLE);
            isOpen = true;
        }
    }
    
    public void refreshGrid() {
        if (!isOpen) return;

        if (inventory == null) return;
        if (slotButtons == null) return;
        if (slotButtons.size() < inventory.capacity) return;

        for(int i=0; i<20; i++) {
            Button slotBtn = slotButtons.get(i);
            InventorySystem.Slot slot = inventory.slots[i];

            if (slot.isEmpty()) {
                slotBtn.setText("");
                slotBtn.setBackgroundColor(Color.parseColor("#333333"));
            } else {
                InventorySystem.ItemDef def = InventorySystem.ITEM_DB[slot.itemId];
                slotBtn.setText(def.icon + (slot.count > 1 ? String.valueOf(slot.count) : ""));
                
                if (i == selectedSlotIndex) slotBtn.setBackgroundColor(Color.parseColor("#0055AA"));
                else slotBtn.setBackgroundColor(Color.parseColor("#555555"));
            }
        }
    }
    
    private void selectSlot(int index) {
        selectedSlotIndex = index;
        refreshGrid();
        
        InventorySystem.Slot slot = inventory.slots[index];
        if (slot.isEmpty()) {
            txtItemDetail.setText("Empty Slot");
            btnUseItem.setVisibility(View.INVISIBLE);
        } else {
            InventorySystem.ItemDef def = InventorySystem.ITEM_DB[slot.itemId];
            txtItemDetail.setText(def.name + "\nWeight: " + def.weight + "kg | Qty: " + slot.count);
            btnUseItem.setVisibility(View.VISIBLE);

            if (def.id == 1 || def.id == 2) btnUseItem.setText("CONSUME");
            else btnUseItem.setText("DROP");
        }
    }
    
    private void useSelectedItem() {
        if (selectedSlotIndex == -1) return;
        InventorySystem.Slot slot = inventory.slots[selectedSlotIndex];
        if (slot.isEmpty()) return;
        
        int id = slot.itemId;

        if (renderer == null || renderer.player == null) return;

        if (id == 1) { 
            if (renderer.player.currentHP < renderer.player.maxHP) {
                inventory.useItem(selectedSlotIndex);
                renderer.player.heal(30);
                Toast.makeText(context, "HP Restored (+30)", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "HP Full!", Toast.LENGTH_SHORT).show();
                return;
            }
        } 
        else if (id == 2) { 
            inventory.useItem(selectedSlotIndex);
            renderer.player.restoreMana(20);
            Toast.makeText(context, "Mana Restored (+20)", Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(context, "Dropped Item.", Toast.LENGTH_SHORT).show();
            inventory.useItem(selectedSlotIndex); 
        }
        
        refreshGrid();
        selectSlot(selectedSlotIndex); 
    }
}
