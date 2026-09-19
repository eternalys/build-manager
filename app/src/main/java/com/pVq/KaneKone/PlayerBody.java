package com.pVq.KaneKone.game;

public class PlayerBody {

    public float x, y, z;
    public float rotation = 0; 
    public boolean isAttacking = false;
    public boolean onGround = false;
    public float velocityY = 0;
    
    public int maxHP = 100;
    public int currentHP = 100;
    public int maxMana = 50;
    public int currentMana = 50;
    
    private static final float SPEED = 6.0f; 
    private static final float GRAVITY = -25.0f;
    private static final float JUMP_POWER = 9.0f; 
    private static final float BODY_RADIUS = 0.25f; 
    
    public PlayerBody() {
        this.x = 0; this.y = 50; this.z = 0;
    }

    public void teleport(float tx, float ty, float tz) {
        this.x = tx; this.y = ty; this.z = tz;
        this.velocityY = 0;
    }

    public void jump() {
        if (onGround) {
            velocityY = JUMP_POWER;
            onGround = false;
        }
    }
    
    public void heal(int amount) {
        currentHP += amount;
        if (currentHP > maxHP) currentHP = maxHP;
    }
    
    public void restoreMana(int amount) {
        currentMana += amount;
        if (currentMana > maxMana) currentMana = maxMana;
    }
    
    public void takeDamage(int amount) {
        currentHP -= amount;
        if (currentHP < 0) currentHP = 0;
    }
    
    public boolean useMana(int amount) {
        if (currentMana >= amount) {
            currentMana -= amount;
            return true;
        }
        return false;
    }

    public void tick(float joyX, float joyY, float camAngle, float dt, float currentGroundY, DungeonEngine dungeon) {
        
        float moveX = 0;
        float moveZ = 0;
        
        if (Math.abs(joyX) > 0.1f || Math.abs(joyY) > 0.1f) {
            float inputForward = -joyY; 
            float inputStrafe  = joyX;  
            double rad = Math.toRadians(camAngle);
            float sin = (float)Math.sin(rad);
            float cos = (float)Math.cos(rad);
            
            moveX = (inputForward * sin) - (inputStrafe * cos);
            moveZ = (inputForward * cos) + (inputStrafe * sin);
            
            if (moveX != 0 || moveZ != 0) {
                float targetRot = (float)Math.toDegrees(Math.atan2(moveX, moveZ));
                float lerpFactor = 10.0f * dt;
                if (lerpFactor > 1.0f) lerpFactor = 1.0f;
                this.rotation = Gods.rotLerp(this.rotation, targetRot, lerpFactor);
            }
        }

        float nextX = x + moveX * SPEED * dt;
        float nextZ = z + moveZ * SPEED * dt;

        // [SAFETY] Cek null agar tidak force close
        if (dungeon != null) {
            try {
                if (dungeon.checkCollision(nextX, z, BODY_RADIUS)) nextX = x; 
                if (dungeon.checkCollision(nextX, nextZ, BODY_RADIUS)) nextZ = z;
                
                if (dungeon.checkCollision(x, z, BODY_RADIUS)) {
                    float centerX = (float)Math.floor(x / 6.0f) * 6.0f + 3.0f;
                    float centerZ = (float)Math.floor(z / 6.0f) * 6.0f + 3.0f;
                    x += (centerX - x) * 5.0f * dt;
                    z += (centerZ - z) * 5.0f * dt;
                }
            } catch (Exception e) {}
        }

        this.x = nextX;
        this.z = nextZ;

        velocityY += GRAVITY * dt;
        y += velocityY * dt;
        
        if (y < currentGroundY) {
            y = currentGroundY; 
            velocityY = 0;
            onGround = true;
        } else {
            onGround = false;
        }
    }
    
    public float getRenderX(float t) { return x; }
    public float getRenderY(float t) { return y; }
    public float getRenderZ(float t) { return z; }
}