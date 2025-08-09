package com.example.mc.engine;

import com.example.mc.world.World;
import org.joml.Vector3f;

public class Player {
    // Player dimensions (used by both main and physics threads)
    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    public static final float DEPTH = 0.6f;

    private final Vector3f position = new Vector3f(8, 100, 8);
    private final Vector3f velocity = new Vector3f(0, 0, 0);
    private boolean onGround = false;

    public Vector3f getPosition() { return position; }
    public Vector3f getVelocity() { return velocity; }
    public boolean isOnGround() { return onGround; }

    public void setPosition(float x, float y, float z) { synchronized (this) { position.set(x, y, z); } }

    // Thread-safe snapshot of state for async simulation
    public static class State {
        public final Vector3f position;
        public final Vector3f velocity;
        public boolean onGround;
        public State(Vector3f position, Vector3f velocity, boolean onGround) {
            this.position = new Vector3f(position);
            this.velocity = new Vector3f(velocity);
            this.onGround = onGround;
        }
    }

    public synchronized State snapshot() {
        return new State(position, velocity, onGround);
    }

    public synchronized void apply(State s) {
        this.position.set(s.position);
        this.velocity.set(s.velocity);
        this.onGround = s.onGround;
    }

    // Nouvelle version fidèle à Minecraft
    // Ajout du paramètre sprintRequested
    public static State simulate(World world, float dt, Vector3f wishMove, boolean jumpRequested, boolean sprintRequested, State in) {
        Vector3f pos = new Vector3f(in.position);
        Vector3f vel = new Vector3f(in.velocity);
        boolean onGroundLocal = in.onGround;

        // Paramètres ajustés pour une sensation plus "Minecraft Java" rapide
        final float gravity = -0.25f / 0.01f; // gravité plus forte (≈ -5 m/s²)
        final float jumpVel = 8.0f; // saut plus puissant (≈ 10.4 m/s)
        final float walkAccel = 0.18f / 0.01f; // accélération plus forte (≈ 3.6 m/s²)
        final float sprintAccel = 0.23f / 0.01f; // accélération sprint (≈ 4.6 m/s²)
        final float walkFriction = 0.86f;
        final float airFriction = 0.865f;
        final float maxWalkSpeed = 6.0f; // vitesse de marche plus élevée
        final float maxSprintSpeed = 7.0f; // vitesse de sprint plus élevée

        // Appliquer la gravité
        vel.y += gravity * dt;

        // Accélération horizontale
        float accel = sprintRequested ? sprintAccel : walkAccel;
        float maxSpeed = sprintRequested ? maxSprintSpeed : maxWalkSpeed;
        Vector3f wishDir = new Vector3f(wishMove.x, 0, wishMove.z);
        if (wishDir.lengthSquared() > 0) wishDir.normalize();
        Vector3f horizVel = new Vector3f(vel.x, 0, vel.z);
        float currentSpeed = horizVel.dot(wishDir);
        float addSpeed = maxSpeed - currentSpeed;
        if (addSpeed > 0) {
            float accelAmount = accel * dt * maxSpeed;
            if (accelAmount > addSpeed) accelAmount = addSpeed;
            vel.x += wishDir.x * accelAmount;
            vel.z += wishDir.z * accelAmount;
        }

        // Friction
        if (onGroundLocal) {
            vel.x *= walkFriction;
            vel.z *= walkFriction;
        } else {
            vel.x *= airFriction;
            vel.z *= airFriction;
        }

        // Saut
        if (jumpRequested && onGroundLocal) {
            vel.y = jumpVel;
            onGroundLocal = false;
        }

        // Déplacement avec résolution de collision par axe
        pos.x = moveAxis(world, pos.x, pos.y, pos.z, vel.x * dt, Axis.X, vel);
        pos.y = moveAxis(world, pos.x, pos.y, pos.z, vel.y * dt, Axis.Y, vel);
        pos.z = moveAxis(world, pos.x, pos.y, pos.z, vel.z * dt, Axis.Z, vel);

        // Après le mouvement Y, si on touche le sol, on est au sol
        if (vel.y <= 0 && collidedBelow(world, pos)) {
            onGroundLocal = true;
            vel.y = 0;
        } else if (vel.y > 0 && collidedAbove(world, pos)) {
            vel.y = 0;
        } else {
            onGroundLocal = false;
        }

        return new State(pos, vel, onGroundLocal);
    }

    // Ajout d'une surcharge pour compatibilité ascendante
    public static State simulate(World world, float dt, Vector3f wishMove, boolean jumpRequested, State in) {
        // Par défaut, pas de sprint
        return simulate(world, dt, wishMove, jumpRequested, false, in);
    }

    public void update(World world, float dt, Vector3f wishMove, boolean jumpRequested) {
        // Keep the synchronous update behavior by delegating to simulate
        State out = simulate(world, dt, wishMove, jumpRequested, snapshot());
        apply(out);
    }

    public void update(World world, float dt, Vector3f wishMove, boolean jumpRequested, boolean sprintRequested) {
        State out = simulate(world, dt, wishMove, jumpRequested, sprintRequested, snapshot());
        apply(out);
    }

    private enum Axis { X, Y, Z }

    private static float moveAxis(World world, float x, float y, float z, float delta, Axis axis, Vector3f vel) {
        if (delta == 0) return axis == Axis.X ? x : axis == Axis.Y ? y : z;
        float nx = x, ny = y, nz = z;
        if (axis == Axis.X) nx += delta;
        if (axis == Axis.Y) ny += delta;
        if (axis == Axis.Z) nz += delta;

        // AABB bounds after movement
        float minX = nx - WIDTH / 2f;
        float maxX = nx + WIDTH / 2f;
        float minY = ny;
        float maxY = ny + HEIGHT;
        float minZ = nz - DEPTH / 2f;
        float maxZ = nz + DEPTH / 2f;

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
                nx = blockMin - WIDTH / 2f - 1e-3f;
            } else {
                float blockMax = (float)Math.floor(minX) + 1.0f;
                nx = blockMax + WIDTH / 2f + 1e-3f;
            }
            vel.x = 0;
            return nx;
        } else if (axis == Axis.Y) {
            if (delta > 0) {
                float blockMin = (float)Math.floor(maxY);
                ny = blockMin - HEIGHT - 1e-3f;
            } else {
                float blockMax = (float)Math.floor(minY) + 1.0f;
                ny = blockMax + 1e-3f;
            }
            vel.y = 0;
            return ny;
        } else {
            if (delta > 0) {
                float blockMin = (float)Math.floor(maxZ);
                nz = blockMin - DEPTH / 2f - 1e-3f;
            } else {
                float blockMax = (float)Math.floor(minZ) + 1.0f;
                nz = blockMax + DEPTH / 2f + 1e-3f;
            }
            vel.z = 0;
            return nz;
        }
    }

    private static boolean collidedBelow(World world, Vector3f position) {
        float x = position.x;
        float y = position.y - 0.05f;
        float z = position.z;
        return aabbIntersectsSolid(world, x, y, z);
    }

    private static boolean collidedAbove(World world, Vector3f position) {
        float x = position.x;
        float y = position.y + HEIGHT + 0.05f;
        float z = position.z;
        return aabbIntersectsSolid(world, x, y, z);
    }

    private static boolean aabbIntersectsSolid(World world, float x, float y, float z) {
        float minX = x - WIDTH / 2f;
        float maxX = x + WIDTH / 2f;
        float minY = y;
        float maxY = y + HEIGHT;
        float minZ = z - DEPTH / 2f;
        float maxZ = z + DEPTH / 2f;
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
