package ovh.paulem.mc.world;

import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.Blocks;

public class Chunk {
    public static final int CHUNK_X = 16;
    public static final int CHUNK_Y = 256;
    public static final int CHUNK_Z = 16;

    private final int originX;
    private final int originZ;
    private final int[][][] blocks = new int[CHUNK_X][CHUNK_Y][CHUNK_Z];
    // Version increments when the chunk's block data is updated (e.g., generation complete)
    private volatile int version = 0;

    public Chunk(int originX, int originZ) {
        this.originX = originX;
        this.originZ = originZ;
    }

    public int getOriginX() { return originX; }
    public int getOriginZ() { return originZ; }

    public int getBlockId(int x, int y, int z) { return blocks[x][y][z]; }
    public Block getBlock(int x, int y, int z) { return Blocks.blocks.get(getBlockId(x, y, z)); }

    public void setBlockId(int x, int y, int z, int id) {
        if (blocks[x][y][z] != id) {
            blocks[x][y][z] = id;
            bumpVersion(); // Incrémenter la version pour déclencher la reconstruction du mesh
        }
    }
    public void setBlock(int x, int y, int z, Block block) { setBlockId(x, y, z, block.getId()); }

    public int getVersion() { return version; }
    public void bumpVersion() { version++; }
}