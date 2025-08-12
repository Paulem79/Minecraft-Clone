package ovh.paulem.mc.world;

import lombok.Getter;
import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.Blocks;

public class Chunk {
    public static final int CHUNK_X = 16;
    public static final int CHUNK_Y = 256;
    public static final int CHUNK_Z = 16;

    @Getter
    private final World world;
    @Getter
    private final int originX;
    @Getter
    private final int originZ;
    // Remplace blocks[x][y][z] par un tableau 1D pour de meilleures performances
    private final byte[] blocks = new byte[CHUNK_X * CHUNK_Y * CHUNK_Z];
    // Version increments when the chunk's block data is updated (e.g., generation complete)
    @Getter
    private volatile int version = 0;

    // Indique si le chunk a été modifié depuis son dernier chargement/sauvegarde
    @Getter
    private volatile boolean dirty = false;

    // Remplace l'ancien stockage 3D par un tableau 1D compacté (4 bits par bloc)
    private final byte[] lightLevels = new byte[(CHUNK_X * CHUNK_Y * CHUNK_Z + 1) / 2];

    public Chunk(World world, int originX, int originZ) {
        this.world = world;
        this.originX = originX;
        this.originZ = originZ;
    }

    private int getIndex(int x, int y, int z) {
        return x + CHUNK_X * (z + CHUNK_Z * y);
    }

    public byte getBlockId(int x, int y, int z) {
        return blocks[getIndex(x, y, z)];
    }
    public Block getBlock(int x, int y, int z) {
        return Blocks.blocks.get(getBlockId(x, y, z));
    }

    public void setBlockId(int x, int y, int z, byte id) {
        int idx = getIndex(x, y, z);
        if (blocks[idx] != id) {
            blocks[idx] = id;
            bumpVersion(); // Incrémenter la version pour déclencher la reconstruction du mesh
        }
    }
    public void setBlock(int x, int y, int z, Block block) { setBlockId(x, y, z, (byte)block.getId()); }

    public void bumpVersion() {
        version++;
        dirty = true;
    }

    // Utilisé uniquement lors du chargement pour restaurer la version sans marquer le chunk comme modifié
    public void setVersion(int version) {
        this.version = version;
        this.dirty = false;

        bakeLight();
    }

    public void bakeLight() {
        world.getLightEngine().propagateSkyLight(this);
    }

    public void markDirty() {
        dirty = true;
    }

    public void markClean() {
        dirty = false;
    }

    public byte getLightLevel(int x, int y, int z) {
        int index = getIndex(x, y, z);
        int byteIndex = index / 2;
        boolean highNibble = (index % 2) == 0;
        byte b = lightLevels[byteIndex];
        return (byte) (highNibble ? (b >> 4) & 0xF : b & 0xF);
    }

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
}