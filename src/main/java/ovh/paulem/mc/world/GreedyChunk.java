package ovh.paulem.mc.world;

import ovh.paulem.mc.Values;
import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.Blocks;
import java.util.Arrays;

/**
 * GreedyChunk sert désormais de Chunk LoD pour les chunks lointains.
 * Il stocke une grille de macro-voxels plus grossière afin de réduire l'utilisation mémoire,
 * et renvoie, pour toute requête (x,y,z), l'ID du bloc de la cellule LoD correspondante.
 */
public class GreedyChunk extends BaseChunk {
    // Facteurs de LOD (récupérés depuis Values pour cohérence globale)
    private static final int LOD_XZ = Math.max(1, Values.LOD_FACTOR_XZ);
    private static final int LOD_Y  = Math.max(1, Values.LOD_FACTOR_Y);

    // Dimensions de la grille LoD
    private static final int SX = CHUNK_X / LOD_XZ;
    private static final int SY = CHUNK_Y / LOD_Y;
    private static final int SZ = CHUNK_Z / LOD_XZ;

    // Données LoD: 1 octet par macro-voxel (block id déjà palettisé sur un byte)
    private byte[] cells; // taille SX*SY*SZ

    // RLE pour la sérialisation (paresseux)
    private byte[] rleBlocks;

    public GreedyChunk(World world, int originX, int originZ, byte[] rleBlocks, byte[] ignoredLight) {
        super(world, originX, originZ);
        // Décoder directement dans la grille LoD
        this.cells = decodeRLE(rleBlocks, SX * SY * SZ);
        this.rleBlocks = rleBlocks; // garder tel quel pour sauvegarde ultérieure
    }

    // Constructeur pour chunk vide (toutes cellules à air)
    public GreedyChunk(World world, int originX, int originZ) {
        super(world, originX, originZ);
        this.cells = new byte[SX * SY * SZ]; // 0 = air
        this.rleBlocks = null; // sera créé à la demande
    }

    private int idxLOD(int cx, int cy, int cz) {
        return cx + SX * (cz + SZ * cy);
    }

    private int clamp(int v, int max) { return (v < 0) ? 0 : (v >= max ? max - 1 : v); }

    private int toCx(int x) { return clamp(x / LOD_XZ, SX); }
    private int toCy(int y) { return clamp(y / LOD_Y,  SY); }
    private int toCz(int z) { return clamp(z / LOD_XZ, SZ); }

    @Override
    public byte getBlockId(int x, int y, int z) {
        int cx = toCx(x), cy = toCy(y), cz = toCz(z);
        return cells[idxLOD(cx, cy, cz)];
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return Blocks.blocks.get(getBlockId(x, y, z));
    }

    @Override
    public void setBlockId(int x, int y, int z, byte id) {
        int cx = toCx(x), cy = toCy(y), cz = toCz(z);
        int idx = idxLOD(cx, cy, cz);
        // Règle simple LoD: on privilégie les blocs non-air. Les écritures d'air ne suppriment pas la cellule.
        // Comme la génération remonte en Y, la dernière écriture non-air reflètera la « couche supérieure » du macro-voxel.
        if (id != 0) {
            cells[idx] = id;
            rleBlocks = null; // invalider cache RLE
        }
    }

    @Override
    public void setBlock(int x, int y, int z, Block block) {
        setBlockId(x, y, z, (byte) block.getId());
    }

    // Pour les chunks LoD lointains, on n'utilise pas la lumière par-voxel dans le rendu greedy.
    @Override
    public byte getLightLevel(int x, int y, int z) {
        return Values.MAX_LIGHT; // plein jour pour simplifier
    }

    @Override
    public void setLightLevel(int x, int y, int z, byte level) {
        // Ignoré pour LoD
    }

    @Override
    public void bakeLight() {
        // Ignoré pour LoD (le rendu greedy lointain utilise un niveau de lumière constant)
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
        while (pos < outLen && i < rle.length) {
            byte value = rle[i++];
            if (value == Byte.MIN_VALUE) break;
            if (i + 1 >= rle.length) break;
            int runLength = ((rle[i++] & 0xFF) << 8) | (rle[i++] & 0xFF);
            int end = Math.min(pos + runLength, outLen);
            Arrays.fill(out, pos, end, value);
            pos = end;
        }
        return out;
    }

    // Accès direct aux données RLE pour la sérialisation
    public byte[] getRleBlocks() {
        if (rleBlocks == null) rleBlocks = encodeRLE(cells);
        return rleBlocks;
    }
    public byte[] getRleLightLevels() { return new byte[0]; }
}
