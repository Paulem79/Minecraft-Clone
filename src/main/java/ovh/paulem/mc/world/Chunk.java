package ovh.paulem.mc.world;

import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.Blocks;

public class Chunk extends BaseChunk {
    private final byte[] blocks = new byte[CHUNK_X * CHUNK_Y * CHUNK_Z];
    private final byte[] lightLevels = new byte[(CHUNK_X * CHUNK_Y * CHUNK_Z + 1) / 2];

    public Chunk(World world, int originX, int originZ) {
        super(world, originX, originZ);
    }

    @Override
    public byte getBlockId(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_X || y < MIN_CHUNK_Y || y >= CHUNK_Y || z < 0 || z >= CHUNK_Z) {
            return 0; // Air par défaut hors limites
        }
        return blocks[getIndex(x, y, z)];
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return Blocks.blocks.get(getBlockId(x, y, z));
    }

    @Override
    public void setBlockId(int x, int y, int z, byte id) {
        int idx = getIndex(x, y, z);
        if (blocks[idx] != id) {
            blocks[idx] = id;
            bumpVersion();
        }
    }

    @Override
    public void setBlock(int x, int y, int z, Block block) {
        setBlockId(x, y, z, (byte)block.getId());
    }

    @Override
    public byte getLightLevel(int x, int y, int z) {
        int index = getIndex(x, y, z);
        int byteIndex = index / 2;
        boolean highNibble = (index % 2) == 0;
        byte b = lightLevels[byteIndex];
        return (byte) (highNibble ? (b >> 4) & 0xF : b & 0xF);
    }

    @Override
    public void setLightLevel(int x, int y, int z, byte level) {
        int index = getIndex(x, y, z);
        int byteIndex = index / 2;
        boolean highNibble = (index % 2) == 0;
        byte b = lightLevels[byteIndex];
        if (highNibble) {
            b = (byte) ((b & 0x0F) | ((level & 0xF) << 4));
        } else {
            b = (byte) ((b & 0xF0) | (level & 0xF));
        }
        lightLevels[byteIndex] = b;
    }

    @Override
    public void bakeLight() {
        world.getLightEngine().propagateSkyLight(this);
    }
}