package ovh.paulem.mc.world;

import lombok.Getter;
import ovh.paulem.mc.world.block.types.Block;

@Getter
public abstract class BaseChunk {
    public static final int CHUNK_X = 16;
    public static final int CHUNK_Y = 256;
    public static final int CHUNK_Z = 16;

    protected final World world;
    protected final int originX;
    protected final int originZ;
    protected volatile int version = 0;
    protected volatile boolean dirty = false;

    public BaseChunk(World world, int originX, int originZ) {
        this.world = world;
        this.originX = originX;
        this.originZ = originZ;
    }

    public void bumpVersion() {
        version++;
        dirty = true;
    }
    public void setVersion(int version) {
        this.version = version;
        this.dirty = false;
        bakeLight();
    }
    public void markDirty() { dirty = true; }
    public void markClean() { dirty = false; }

    protected int getIndex(int x, int y, int z) {
        return x + CHUNK_X * (z + CHUNK_Z * y);
    }

    // Méthodes d'accès abstraites
    public abstract byte getBlockId(int x, int y, int z);
    public abstract Block getBlock(int x, int y, int z);
    public abstract void setBlockId(int x, int y, int z, byte id);
    public abstract void setBlock(int x, int y, int z, Block block);
    public abstract byte getLightLevel(int x, int y, int z);
    public abstract void setLightLevel(int x, int y, int z, byte level);

    public abstract void bakeLight();
}
