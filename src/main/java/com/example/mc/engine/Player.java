package com.example.mc.engine;

import com.example.mc.world.World;
import org.joml.Vector3f;

public class Player {
    private final Vector3f position = new Vector3f(8, 100, 8);
    private final Vector3f velocity = new Vector3f(0, 0, 0);

    private final float width = 0.6f;
    private final float height = 1.8f;
    private final float depth = 0.6f;

    private boolean onGround = false;

    public Vector3f getPosition() { return position; }
    public Vector3f getVelocity() { return velocity; }
    public boolean isOnGround() { return onGround; }

    public void setPosition(float x, float y, float z) { position.set(x, y, z); }

    public void update(World world, float dt, Vector3f wishMove, boolean jumpRequested) {
        // Apply gravity
        float gravity = -25.0f; // m/s^2
        velocity.y += gravity * dt;

        // Apply horizontal movement (wishMove expected as world-space desired velocity)
        float accel = 50.0f;
        velocity.x += wishMove.x * accel * dt;
        velocity.z += wishMove.z * accel * dt;

        // Dampen horizontal speed slightly
        float friction = onGround ? 8.0f : 2.0f;
        velocity.x -= velocity.x * Math.min(1.0f, friction * dt);
        velocity.z -= velocity.z * Math.min(1.0f, friction * dt);

        // Jump
        if (jumpRequested && onGround) {
            velocity.y = 10.5f;
            onGround = false;
        }

        // Move with collision resolution per-axis
        position.x = moveAxis(world, position.x, position.y, position.z, velocity.x * dt, Axis.X);
        position.y = moveAxis(world, position.x, position.y, position.z, velocity.y * dt, Axis.Y);
        position.z = moveAxis(world, position.x, position.y, position.z, velocity.z * dt, Axis.Z);

        // After Y movement, if we collided with ground, set onGround
        if (velocity.y < 0 && collidedBelow(world)) {
            onGround = true;
            velocity.y = 0;
        } else if (velocity.y > 0 && collidedAbove(world)) {
            velocity.y = 0;
        } else {
            onGround = false;
        }
    }

    private enum Axis { X, Y, Z }

    private float moveAxis(World world, float x, float y, float z, float delta, Axis axis) {
        if (delta == 0) return axis == Axis.X ? x : axis == Axis.Y ? y : z;
        float nx = x, ny = y, nz = z;
        if (axis == Axis.X) nx += delta;
        if (axis == Axis.Y) ny += delta;
        if (axis == Axis.Z) nz += delta;

        // AABB bounds after movement
        float minX = nx - width / 2f;
        float maxX = nx + width / 2f;
        float minY = ny;
        float maxY = ny + height;
        float minZ = nz - depth / 2f;
        float maxZ = nz + depth / 2f;

        int startX = (int)Math.floor(minX);
        int endX   = (int)Math.floor(maxX);
        int startY = (int)Math.floor(minY);
        int endY   = (int)Math.floor(maxY);
        int startZ = (int)Math.floor(minZ);
        int endZ   = (int)Math.floor(maxZ);

        boolean collided = false;
        for (int bx = startX; bx <= endX; bx++) {
            for (int by = startY; by <= endY; by++) {
                for (int bz = startZ; bz <= endZ; bz++) {
                    if (world.isSolid(bx, by, bz)) {
                        collided = true;
                    }
                }
            }
        }

        if (!collided) {
            return axis == Axis.X ? nx : axis == Axis.Y ? ny : nz;
        }

        // Resolve by moving to the boundary of the collided blocks
        if (axis == Axis.X) {
            if (delta > 0) {
                // moving +X, clamp to left side of the first block we hit
                float blockMin = (float)Math.floor(maxX);
                nx = blockMin - width / 2f - 1e-3f;
            } else {
                float blockMax = (float)Math.floor(minX) + 1.0f;
                nx = blockMax + width / 2f + 1e-3f;
            }
            velocity.x = 0;
            return nx;
        } else if (axis == Axis.Y) {
            if (delta > 0) {
                float blockMin = (float)Math.floor(maxY);
                ny = blockMin - height - 1e-3f;
            } else {
                float blockMax = (float)Math.floor(minY) + 1.0f;
                ny = blockMax + 1e-3f;
            }
            velocity.y = 0;
            return ny;
        } else {
            if (delta > 0) {
                float blockMin = (float)Math.floor(maxZ);
                nz = blockMin - depth / 2f - 1e-3f;
            } else {
                float blockMax = (float)Math.floor(minZ) + 1.0f;
                nz = blockMax + depth / 2f + 1e-3f;
            }
            velocity.z = 0;
            return nz;
        }
    }

    private boolean collidedBelow(World world) {
        float x = position.x;
        float y = position.y - 0.05f;
        float z = position.z;
        return aabbIntersectsSolid(world, x, y, z);
    }

    private boolean collidedAbove(World world) {
        float x = position.x;
        float y = position.y + height + 0.05f;
        float z = position.z;
        return aabbIntersectsSolid(world, x, y, z);
    }

    private boolean aabbIntersectsSolid(World world, float x, float y, float z) {
        float minX = x - width / 2f;
        float maxX = x + width / 2f;
        float minY = y;
        float maxY = y + height;
        float minZ = z - depth / 2f;
        float maxZ = z + depth / 2f;
        int startX = (int)Math.floor(minX);
        int endX   = (int)Math.floor(maxX);
        int startY = (int)Math.floor(minY);
        int endY   = (int)Math.floor(maxY);
        int startZ = (int)Math.floor(minZ);
        int endZ   = (int)Math.floor(maxZ);
        for (int bx = startX; bx <= endX; bx++)
            for (int by = startY; by <= endY; by++)
                for (int bz = startZ; bz <= endZ; bz++)
                    if (world.isSolid(bx, by, bz)) return true;
        return false;
    }
}
