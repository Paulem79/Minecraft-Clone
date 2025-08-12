package ovh.paulem.mc.world;

import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.Blocks;
import java.util.Arrays;

public class GreedyChunk extends BaseChunk {
    // Données compressées (RLE)
    private byte[] rleBlocks;
    private byte[] rleLightLevels;
    // Tableaux décompressés (cache temporaire)
    private byte[] blocksCache;
    private byte[] lightLevelsCache;
    private boolean cacheValid = false;

    public GreedyChunk(World world, int originX, int originZ, byte[] rleBlocks, byte[] rleLightLevels) {
        super(world, originX, originZ);
        this.rleBlocks = rleBlocks;
        this.rleLightLevels = rleLightLevels;
        this.cacheValid = false;
    }

    // Constructeur pour chunk vide
    public GreedyChunk(World world, int originX, int originZ) {
        this(world, originX, originZ, encodeRLE(new byte[CHUNK_X * CHUNK_Y * CHUNK_Z]), encodeRLE(new byte[(CHUNK_X * CHUNK_Y * CHUNK_Z + 1) / 2]));
    }

    private void ensureCache() {
        if (!cacheValid) {
            blocksCache = decodeRLE(rleBlocks, CHUNK_X * CHUNK_Y * CHUNK_Z);
            lightLevelsCache = decodeRLE(rleLightLevels, (CHUNK_X * CHUNK_Y * CHUNK_Z + 1) / 2);
            cacheValid = true;
        }
    }

    private void flushCache() {
        if (cacheValid) {
            rleBlocks = encodeRLE(blocksCache);
            rleLightLevels = encodeRLE(lightLevelsCache);
            cacheValid = false;
        }
    }

    @Override
    public byte getBlockId(int x, int y, int z) {
        ensureCache();
        return blocksCache[getIndex(x, y, z)];
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return Blocks.blocks.get(getBlockId(x, y, z));
    }

    @Override
    public void setBlockId(int x, int y, int z, byte id) {
        ensureCache();
        int idx = getIndex(x, y, z);
        if (blocksCache[idx] != id) {
            blocksCache[idx] = id;
            bumpVersion();
            flushCache();
        }
    }

    @Override
    public void setBlock(int x, int y, int z, Block block) {
        setBlockId(x, y, z, (byte)block.getId());
    }

    @Override
    public byte getLightLevel(int x, int y, int z) {
        ensureCache();
        int index = getIndex(x, y, z);
        int byteIndex = index / 2;
        boolean highNibble = (index % 2) == 0;
        byte b = lightLevelsCache[byteIndex];
        return (byte) (highNibble ? (b >> 4) & 0xF : b & 0xF);
    }

    @Override
    public void setLightLevel(int x, int y, int z, byte level) {
        ensureCache();
        int index = getIndex(x, y, z);
        int byteIndex = index / 2;
        boolean highNibble = (index % 2) == 0;
        byte b = lightLevelsCache[byteIndex];
        if (highNibble) {
            b = (byte) ((b & 0x0F) | ((level & 0xF) << 4));
        } else {
            b = (byte) ((b & 0xF0) | (level & 0xF));
        }
        lightLevelsCache[byteIndex] = b;
        bumpVersion();
        flushCache();
    }

    @Override
    public void bakeLight() {
        //world.getLightEngine().propagateSkyLight(this);
    }

    // --- Méthodes RLE ---
    public static byte[] encodeRLE(byte[] data) {
        int n = data.length;
        byte[] tmp = new byte[n * 3 / 2 + 4]; // estimation large
        int pos = 0;
        int i = 0;
        while (i < n) {
            byte value = data[i];
            int runLength = 1;
            while (i + runLength < n && data[i + runLength] == value && runLength < 0x7FFF) {
                runLength++;
            }
            tmp[pos++] = value;
            tmp[pos++] = (byte)((runLength >> 8) & 0xFF);
            tmp[pos++] = (byte)(runLength & 0xFF);
            i += runLength;
        }
        tmp[pos++] = Byte.MIN_VALUE;
        tmp[pos++] = 0;
        tmp[pos++] = 0;
        return Arrays.copyOf(tmp, pos);
    }

    public static byte[] decodeRLE(byte[] rle, int outLen) {
        byte[] out = new byte[outLen];
        int i = 0, pos = 0;
        while (pos < outLen) {
            byte value = rle[i++];
            if (value == Byte.MIN_VALUE) break;
            int runLength = ((rle[i++] & 0xFF) << 8) | (rle[i++] & 0xFF);
            Arrays.fill(out, pos, pos + runLength, value);
            pos += runLength;
        }
        return out;
    }

    // Accès direct aux données RLE pour la sérialisation
    public byte[] getRleBlocks() { return rleBlocks; }
    public byte[] getRleLightLevels() { return rleLightLevels; }
}
