package ovh.paulem.mc.world;

import lombok.Getter;
import org.joml.Vector3f;
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
    private final int[][][] blocks = new int[CHUNK_X][CHUNK_Y][CHUNK_Z];
    // Version increments when the chunk's block data is updated (e.g., generation complete)
    @Getter
    private volatile int version = 0;

    // Indique si le chunk a été modifié depuis son dernier chargement/sauvegarde
    @Getter
    private volatile boolean dirty = false;

    private final byte[][][] lightLevels = new byte[CHUNK_X][CHUNK_Y][CHUNK_Z];

    public Chunk(World world, int originX, int originZ) {
        this.world = world;
        this.originX = originX;
        this.originZ = originZ;
    }

    public int getBlockId(int x, int y, int z) { return blocks[x][y][z]; }
    public Block getBlock(int x, int y, int z) { return Blocks.blocks.get(getBlockId(x, y, z)); }

    public void setBlockId(int x, int y, int z, int id) {
        if (blocks[x][y][z] != id) {
            blocks[x][y][z] = id;
            bumpVersion(); // Incrémenter la version pour déclencher la reconstruction du mesh
        }
    }
    public void setBlock(int x, int y, int z, Block block) { setBlockId(x, y, z, block.getId()); }

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

    public byte getLightLevel(Vector3f coords) {
        return getLightLevel((int) coords.x, (int) coords.y, (int) coords.z);
    }

    public byte getLightLevel(int x, int y, int z) {
        return lightLevels[org.joml.Math.clamp(0, Chunk.CHUNK_X-1, x)][y][org.joml.Math.clamp(0, Chunk.CHUNK_Z-1, z)];
    }

    public void setLightLevel(int x, int y, int z, byte level) {
        lightLevels[x][y][z] = level;
    }
}