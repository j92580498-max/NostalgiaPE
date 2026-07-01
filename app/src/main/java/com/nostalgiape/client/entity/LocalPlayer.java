package com.nostalgiape.client.entity;

import com.nostalgiape.client.world.World;

/**
 * Local player state: position, orientation and simple AABB gravity/collision
 * against the voxel world. Values mirror classic PE (eye height ~1.62).
 * Clean-room implementation.
 */
public class LocalPlayer {
    public float x, y, z;
    public float yaw, pitch;
    public float vy;
    public boolean onGround;

    public static final float EYE_HEIGHT = 1.62f;
    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    private static final float GRAVITY = 28f;      // m/s^2 (tuned for feel)
    private static final float MOVE_SPEED = 4.3f;  // m/s
    private static final float JUMP_VELOCITY = 8.4f;

    private float pendingX, pendingZ;
    private boolean pendingJump;

    public float eyeY() { return y + EYE_HEIGHT; }

    /**
     * Stash movement input for the next physics step. forward is +1 (north-ish)
     * / -1, strafe is +1 (right) / -1, both relative to current yaw.
     */
    public void applyInput(float strafe, float forward, boolean jump, float dt, World world) {
        this.pendingX = strafe;
        this.pendingZ = forward;
        this.pendingJump = jump;
    }

    /** Integrates horizontal movement, gravity and collisions. */
    public void tickPhysics(float dt, World world) {
        double yawRad = Math.toRadians(yaw);
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);

        float forward = pendingZ;
        float strafe = pendingX;
        float dx = (forward * sin + strafe * cos) * MOVE_SPEED * dt;
        float dz = (-forward * cos + strafe * sin) * MOVE_SPEED * dt;

        vy -= GRAVITY * dt;
        if (vy < -50) vy = -50;
        float dy = vy * dt;

        moveAxis(world, dx, 0, 0);
        moveAxis(world, 0, 0, dz);
        onGround = false;
        moveAxis(world, 0, dy, 0);

        if (onGround && pendingJump) {
            vy = JUMP_VELOCITY;
        }
    }

    private void moveAxis(World world, float dx, float dy, float dz) {
        float nx = x + dx, ny = y + dy, nz = z + dz;
        if (collides(world, nx, ny, nz)) {
            if (dy < 0) { onGround = true; vy = 0; }
            if (dy > 0) { vy = 0; }
            return;
        }
        x = nx; y = ny; z = nz;
    }

    private boolean collides(World world, float px, float py, float pz) {
        float half = WIDTH / 2f;
        int minX = (int) Math.floor(px - half);
        int maxX = (int) Math.floor(px + half);
        int minY = (int) Math.floor(py);
        int maxY = (int) Math.floor(py + HEIGHT);
        int minZ = (int) Math.floor(pz - half);
        int maxZ = (int) Math.floor(pz + half);
        for (int bx = minX; bx <= maxX; bx++)
            for (int by = minY; by <= maxY; by++)
                for (int bz = minZ; bz <= maxZ; bz++)
                    if (world.isSolid(bx, by, bz)) return true;
        return false;
    }

    /**
     * Ray-marches from the eye along the view direction. Returns block coords:
     * if place==false, the first solid block hit [x,y,z, face]; if place==true,
     * the empty cell just before the hit surface [x,y,z, face]. Null if nothing
     * is within range.
     */
    public int[] raycast(World world, float maxDist, boolean place) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        float dirX = (float) (-Math.cos(pitchRad) * Math.sin(yawRad));
        float dirY = (float) (Math.sin(pitchRad));
        float dirZ = (float) (Math.cos(pitchRad) * Math.cos(yawRad));

        float px = x, py = eyeY(), pz = z;
        int lastX = (int) Math.floor(px), lastY = (int) Math.floor(py), lastZ = (int) Math.floor(pz);
        float step = 0.05f;
        for (float t = 0; t < maxDist; t += step) {
            float cx = px + dirX * t;
            float cy = py + dirY * t;
            float cz = pz + dirZ * t;
            int bx = (int) Math.floor(cx);
            int by = (int) Math.floor(cy);
            int bz = (int) Math.floor(cz);
            if (world.isSolid(bx, by, bz)) {
                if (place) {
                    int face = faceBetween(lastX, lastY, lastZ, bx, by, bz);
                    return new int[]{lastX, lastY, lastZ, face};
                }
                int face = faceBetween(lastX, lastY, lastZ, bx, by, bz);
                return new int[]{bx, by, bz, face};
            }
            lastX = bx; lastY = by; lastZ = bz;
        }
        return null;
    }

    private static int faceBetween(int ax, int ay, int az, int bx, int by, int bz) {
        if (ay > by) return 1;   // top
        if (ay < by) return 0;   // bottom
        if (az < bz) return 2;   // north
        if (az > bz) return 3;   // south
        if (ax < bx) return 4;   // west
        return 5;                // east
    }
}
