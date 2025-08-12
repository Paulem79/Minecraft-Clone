package ovh.paulem.mc.world;

import ovh.paulem.mc.Dirs;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Gère la sauvegarde et le chargement des chunks sur le disque
 */
public class ChunkIO {
    private final Path worldDirectory;
    private final World world;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private final Map<Long, Object> chunkLocks = new ConcurrentHashMap<>();

    public ChunkIO(World world, String worldName) {
        this.world = world;
        // Créer un répertoire pour les sauvegardes de ce monde
        this.worldDirectory = Dirs.WORLD.resolve(worldName);
        try {
            Files.createDirectories(worldDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Folder created: " + worldDirectory);
    }

    /**
     * Sauvegarde un chunk sur le disque (privée, utiliser saveChunkAsync)
     */
    private void saveChunk(BaseChunk chunk) {
        int chunkX = chunk.getOriginX() / BaseChunk.CHUNK_X;
        int chunkZ = chunk.getOriginZ() / BaseChunk.CHUNK_Z;
        long chunkKey = (((long)chunkX) << 32) ^ (chunkZ & 0xffffffffL);
        Object lock = chunkLocks.computeIfAbsent(chunkKey, k -> new Object());
        synchronized (lock) {
            Path regionDir = getRegionDirectory(chunkX, chunkZ);
            try {
                Files.createDirectories(regionDir);
                Path chunkFile = getChunkFile(chunkX, chunkZ);
                Path tempFile = chunkFile.resolveSibling(chunkFile.getFileName() + ".tmp");

                try (FileOutputStream fos = new FileOutputStream(tempFile.toFile());
                     BufferedOutputStream bos = new BufferedOutputStream(fos);
                     DataOutputStream dos = new DataOutputStream(bos)) {

                    // Sauvegarde des métadonnées du chunk
                    dos.writeInt(chunk.getOriginX());
                    dos.writeInt(chunk.getOriginZ());
                    dos.writeInt(chunk.getVersion());

                    // Type de chunk : 0 = Chunk, 1 = GreedyChunk
                    if (chunk instanceof GreedyChunk) {
                        dos.writeByte(1);
                        GreedyChunk gChunk = (GreedyChunk) chunk;
                        byte[] rleBlocks = gChunk.getRleBlocks();
                        byte[] rleLight = gChunk.getRleLightLevels();
                        dos.writeInt(rleBlocks.length);
                        dos.write(rleBlocks);
                        dos.writeInt(rleLight.length);
                        dos.write(rleLight);
                    } else {
                        dos.writeByte(0);
                        // Sauvegarde des blocs compressés RLE (byte)
                        byte[] blocks = new byte[BaseChunk.CHUNK_X * BaseChunk.CHUNK_Y * BaseChunk.CHUNK_Z];
                        int idx = 0;
                        for (int x = 0; x < BaseChunk.CHUNK_X; x++)
                            for (int y = 0; y < BaseChunk.CHUNK_Y; y++)
                                for (int z = 0; z < BaseChunk.CHUNK_Z; z++)
                                    blocks[idx++] = chunk.getBlockId(x, y, z);
                        writeRLEByteArray(dos, blocks);
                        // Sauvegarde de la lumière compressée RLE
                        byte[] light = new byte[(BaseChunk.CHUNK_X * BaseChunk.CHUNK_Y * BaseChunk.CHUNK_Z + 1) / 2];
                        idx = 0;
                        for (int x = 0; x < BaseChunk.CHUNK_X; x++)
                            for (int y = 0; y < BaseChunk.CHUNK_Y; y++)
                                for (int z = 0; z < BaseChunk.CHUNK_Z; z++) {
                                    int index = chunk.getIndex(x, y, z);
                                    int byteIndex = index / 2;
                                    boolean highNibble = (index % 2) == 0;
                                    byte b = light[byteIndex];
                                    byte level = chunk.getLightLevel(x, y, z);
                                    if (highNibble) {
                                        b = (byte) ((b & 0x0F) | ((level & 0xF) << 4));
                                    } else {
                                        b = (byte) ((b & 0xF0) | (level & 0xF));
                                    }
                                    light[byteIndex] = b;
                                }
                        writeRLEByteArray(dos, light);
                    }
                    dos.flush();
                }
                try {
                    Files.move(tempFile, chunkFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException | AccessDeniedException e) {
                    try {
                        Files.move(tempFile, chunkFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ex) {
                        try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                        throw ex;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Charge un chunk depuis le disque
     * @return le chunk chargé ou null si le chunk n'existe pas ou ne peut pas être chargé
     */
    public BaseChunk loadChunk(int chunkX, int chunkZ) {
        long chunkKey = (((long)chunkX) << 32) ^ (chunkZ & 0xffffffffL);
        Object lock = chunkLocks.computeIfAbsent(chunkKey, k -> new Object());
        synchronized (lock) {
            Path chunkFile = getChunkFile(chunkX, chunkZ);
            if (!Files.exists(chunkFile)) {
                return null;
            }
            try (FileInputStream fis = new FileInputStream(chunkFile.toFile());
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 DataInputStream dis = new DataInputStream(bis)) {

                // Lecture des métadonnées
                int originX = dis.readInt();
                int originZ = dis.readInt();
                int version = dis.readInt();
                int type = dis.readByte();

                if (type == 1) { // GreedyChunk
                    int rleBlocksLen = dis.readInt();
                    byte[] rleBlocks = new byte[rleBlocksLen];
                    dis.readFully(rleBlocks);
                    int rleLightLen = dis.readInt();
                    byte[] rleLight = new byte[rleLightLen];
                    dis.readFully(rleLight);
                    GreedyChunk gChunk = new GreedyChunk(world, originX, originZ, rleBlocks, rleLight);
                    gChunk.setVersion(version);
                    return gChunk;
                } else { // Chunk classique
                    Chunk chunk = new Chunk(world, originX, originZ);
                    // Chargement des blocs compressés RLE (byte)
                    byte[] blocks = new byte[BaseChunk.CHUNK_X * BaseChunk.CHUNK_Y * BaseChunk.CHUNK_Z];
                    readRLEByteArray(dis, blocks);
                    int idx = 0;
                    for (int x = 0; x < BaseChunk.CHUNK_X; x++)
                        for (int y = 0; y < BaseChunk.CHUNK_Y; y++)
                            for (int z = 0; z < BaseChunk.CHUNK_Z; z++)
                                chunk.setBlockId(x, y, z, (byte)blocks[idx++]);
                    // Chargement de la lumière compressée RLE
                    byte[] light = new byte[(BaseChunk.CHUNK_X * BaseChunk.CHUNK_Y * BaseChunk.CHUNK_Z + 1) / 2];
                    readRLEByteArray(dis, light);
                    idx = 0;
                    for (int x = 0; x < BaseChunk.CHUNK_X; x++)
                        for (int y = 0; y < BaseChunk.CHUNK_Y; y++)
                            for (int z = 0; z < BaseChunk.CHUNK_Z; z++) {
                                int index = chunk.getIndex(x, y, z);
                                int byteIndex = index / 2;
                                boolean highNibble = (index % 2) == 0;
                                byte b = light[byteIndex];
                                byte level = (byte) (highNibble ? (b >> 4) & 0xF : b & 0xF);
                                chunk.setLightLevel(x, y, z, level);
                            }
                    chunk.setVersion(version);
                    return chunk;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * Vérifie si un chunk existe sur le disque
     */
    public boolean chunkExists(int chunkX, int chunkZ) {
        return Files.exists(getChunkFile(chunkX, chunkZ));
    }

    /**
     * Sauvegarde la seed du monde dans world.dat
     */
    public void saveWorldSeed(long seed) {
        Path seedFile = worldDirectory.resolve("world.dat");
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(seedFile))) {
            dos.writeLong(seed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Charge la seed du monde depuis world.dat, ou retourne null si absent
     */
    public Long loadWorldSeed() {
        Path seedFile = worldDirectory.resolve("world.dat");
        if (!Files.exists(seedFile)) return null;
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(seedFile))) {
            return dis.readLong();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sauvegarde un chunk de façon asynchrone
     */
    public void saveChunkAsync(BaseChunk chunk) {
        saveExecutor.submit(() -> saveChunk(chunk));
    }

    /**
     * Arrête proprement l'executor de sauvegarde
     */
    public void shutdown() {
        saveExecutor.shutdown();
    }

    private Path getRegionDirectory(int chunkX, int chunkZ) {
        // Organise les chunks en régions de 32x32 chunks
        int regionX = Math.floorDiv(chunkX, 32);
        int regionZ = Math.floorDiv(chunkZ, 32);
        return worldDirectory.resolve("region_" + regionX + "_" + regionZ);
    }

    private Path getChunkFile(int chunkX, int chunkZ) {
        Path regionDir = getRegionDirectory(chunkX, chunkZ);
        return regionDir.resolve("chunk_" + chunkX + "_" + chunkZ + ".dat");
    }

    // --- Compression RLE simple pour les blockIds ---
    private void writeRLEByteArray(DataOutputStream dos, byte[] data) throws IOException {
        final int n = data.length;
        int i = 0;
        while (i < n) {
            final byte value = data[i];
            int runLength = 1;
            for (; i + runLength < n && data[i + runLength] == value && runLength < 0x7FFF; runLength++);
            dos.writeByte(value);
            dos.writeShort(runLength); // 2 octets pour la taille du run
            i += runLength;
        }
        dos.writeByte(Byte.MIN_VALUE); // marqueur de fin
        dos.writeShort(0); // fin
    }

    private void readRLEByteArray(DataInputStream dis, byte[] data) throws IOException {
        int i = 0;
        while (i < data.length) {
            final byte value = dis.readByte();
            if (value == Byte.MIN_VALUE) {
                dis.readShort(); // consomme le short de fin
                break;
            }
            final int runLength = dis.readShort() & 0xFFFF;
            final int max = Math.min(runLength, data.length - i);
            for (int j = 0; j < max; j++) {
                data[i++] = value;
            }
        }
    }
}
