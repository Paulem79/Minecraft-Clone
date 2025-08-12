package ovh.paulem.mc.engine.render;

import org.joml.Vector3f;
import ovh.paulem.mc.world.Chunk;
import ovh.paulem.mc.world.World;
import ovh.paulem.mc.world.BaseChunk;

public class Particle {
    public Vector3f position;
    public Vector3f velocity;
    public float life;
    public float size;
    public String texturePath;
    public float uOffset;
    public float vOffset;
    public float lightLevel = 1.0f;

    public Particle(Vector3f position, Vector3f velocity, float life, float size, String texturePath, float uOffset, float vOffset) {
        this.position = new Vector3f(position);
        this.velocity = new Vector3f(velocity);
        this.life = life;
        this.size = size * 100f;
        this.texturePath = texturePath;
        this.uOffset = uOffset;
        this.vOffset = vOffset;
    }

    public void update(float deltaTime, World world) {
        // Gravité
        float gravity = 18.0f; // Plus grand = chute plus rapide
        velocity.y -= gravity * deltaTime;
        // Calculer la nouvelle position
        Vector3f nextPos = new Vector3f(position).add(new Vector3f(velocity).mul(deltaTime));
        // Collision simple avec le sol et les blocs adjacents
        int nx = (int)Math.floor(nextPos.x);
        int ny = (int)Math.floor(nextPos.y);
        int nz = (int)Math.floor(nextPos.z);
        boolean collided = false;
        if (world != null && !world.isPassable(nx, ny, nz)) {
            // Collision détectée, stoppe la particule (ou rebond simple)
            velocity.x *= 0.5f;
            velocity.y *= -0.2f; // petit rebond
            velocity.z *= 0.5f;
            // Replace juste au-dessus du bloc
            nextPos.y = ny + 1.01f;
            collided = true;
        }
        position.set(nextPos);
        // MAJ du light level
        if (world != null) {
            int lx = (int)Math.floor(position.x);
            int ly = (int)Math.floor(position.y);
            int lz = (int)Math.floor(position.z);
            BaseChunk chunk = world.getChunkAt(lx, lz);
            if (ly >= BaseChunk.CHUNK_Y) {
                lightLevel = 1.0f; // plein ciel
            } else if (chunk != null) {
                int localX = Math.floorMod(lx, BaseChunk.CHUNK_X);
                int localZ = Math.floorMod(lz, BaseChunk.CHUNK_Z);
                // On prend la moyenne entre le bloc courant et celui au-dessus pour lisser
                float ll1 = Render.safeGetLightLevel(chunk, localX, ly, localZ);
                float ll2 = (ly+1 < Chunk.CHUNK_Y) ? Render.safeGetLightLevel(chunk, localX, ly+1, localZ) : 1.0f;
                lightLevel = (ll1 + ll2) * 0.5f;
            } else {
                lightLevel = 1.0f;
            }
        }
        life -= deltaTime;
        // Optionnel: si la vitesse est très faible après collision, tue la particule
        if (collided && Math.abs(velocity.y) < 0.2f) {
            life = 0;
        }
    }

    public boolean isAlive() {
        return life > 0;
    }
}
