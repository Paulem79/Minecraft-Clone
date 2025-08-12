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
    private void saveChunk(Chunk chunk) {
        int chunkX = chunk.getOriginX() / Chunk.CHUNK_X;
        int chunkZ = chunk.getOriginZ() / Chunk.CHUNK_Z;
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
                     ObjectOutputStream oos = new ObjectOutputStream(bos)) {

                    // Sauvegarde des métadonnées du chunk
                    oos.writeInt(chunk.getOriginX());
                    oos.writeInt(chunk.getOriginZ());
                    oos.writeInt(chunk.getVersion());

                    // Sauvegarde des blocs compressés RLE
                    int[] blocks = new int[Chunk.CHUNK_X * Chunk.CHUNK_Y * Chunk.CHUNK_Z];
                    int idx = 0;
                    for (int x = 0; x < Chunk.CHUNK_X; x++)
                        for (int y = 0; y < Chunk.CHUNK_Y; y++)
                            for (int z = 0; z < Chunk.CHUNK_Z; z++)
                                blocks[idx++] = chunk.getBlockId(x, y, z);
                    writeRLEIntArray(oos, blocks);
                    oos.flush();
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
    public Chunk loadChunk(int chunkX, int chunkZ) {
        long chunkKey = (((long)chunkX) << 32) ^ (chunkZ & 0xffffffffL);
        Object lock = chunkLocks.computeIfAbsent(chunkKey, k -> new Object());
        synchronized (lock) {
            Path chunkFile = getChunkFile(chunkX, chunkZ);
            if (!Files.exists(chunkFile)) {
                return null;
            }
            try (FileInputStream fis = new FileInputStream(chunkFile.toFile());
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 ObjectInputStream ois = new ObjectInputStream(bis)) {

                // Lecture des métadonnées
                int originX = ois.readInt();
                int originZ = ois.readInt();
                int version = ois.readInt();

                // Création du chunk
                Chunk chunk = new Chunk(world, originX, originZ);

                // Chargement des blocs compressés RLE
                int[] blocks = new int[Chunk.CHUNK_X * Chunk.CHUNK_Y * Chunk.CHUNK_Z];
                readRLEIntArray(ois, blocks);
                int idx = 0;
                for (int x = 0; x < Chunk.CHUNK_X; x++)
                    for (int y = 0; y < Chunk.CHUNK_Y; y++)
                        for (int z = 0; z < Chunk.CHUNK_Z; z++)
                            chunk.setBlockId(x, y, z, blocks[idx++]);

                // Restauration de la version sans déclencher de nouveau bump
                chunk.setVersion(version);

                return chunk;
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
    public void saveChunkAsync(Chunk chunk) {
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
    private void writeRLEIntArray(ObjectOutputStream oos, int[] data) throws IOException {
        int n = data.length;
        int i = 0;
        while (i < n) {
            int value = data[i];
            int runLength = 1;
            while (i + runLength < n && data[i + runLength] == value && runLength < 0x7FFF) {
                runLength++;
            }
            oos.writeInt(value);
            oos.writeShort(runLength); // 2 octets pour la taille du run
            i += runLength;
        }
        oos.writeInt(Integer.MIN_VALUE); // marqueur de fin
    }

    private void readRLEIntArray(ObjectInputStream ois, int[] data) throws IOException {
        int i = 0;
        while (i < data.length) {
            int value = ois.readInt();
            if (value == Integer.MIN_VALUE) break;
            int runLength = ois.readShort() & 0xFFFF;
            for (int j = 0; j < runLength && i < data.length; j++) {
                data[i++] = value;
            }
        }
    }
}
