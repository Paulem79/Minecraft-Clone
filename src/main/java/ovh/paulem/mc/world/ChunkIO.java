package ovh.paulem.mc.world;

import ovh.paulem.mc.Dirs;

import java.io.*;
import java.nio.file.*;
import java.util.logging.Logger;

/**
 * Gère la sauvegarde et le chargement des chunks sur le disque
 */
public class ChunkIO {
    private static final Logger LOGGER = Logger.getLogger(ChunkIO.class.getName());
    private final Path worldDirectory;

    public ChunkIO(String worldName) {
        // Créer un répertoire pour les sauvegardes de ce monde
        this.worldDirectory = Dirs.WORLD.resolve(worldName);
        try {
            Files.createDirectories(worldDirectory);
        } catch (IOException e) {
            LOGGER.severe("Erreur lors de la création du répertoire de monde: " + e.getMessage());
        }
        System.out.println("Folder created: " + worldDirectory);
    }

    /**
     * Sauvegarde un chunk sur le disque
     */
    public void saveChunk(Chunk chunk) {
        int chunkX = chunk.getOriginX() / Chunk.CHUNK_X;
        int chunkZ = chunk.getOriginZ() / Chunk.CHUNK_Z;
        Path regionDir = getRegionDirectory(chunkX, chunkZ);

        try {
            Files.createDirectories(regionDir);
            Path chunkFile = getChunkFile(chunkX, chunkZ);

            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(chunkFile)))) {

                // Sauvegarde des métadonnées du chunk
                oos.writeInt(chunk.getOriginX());
                oos.writeInt(chunk.getOriginZ());
                oos.writeInt(chunk.getVersion());

                // Sauvegarde des blocs
                for (int x = 0; x < Chunk.CHUNK_X; x++) {
                    for (int y = 0; y < Chunk.CHUNK_Y; y++) {
                        for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                            oos.writeInt(chunk.getBlockId(x, y, z));
                        }
                    }
                }

                oos.flush();
            }
        } catch (IOException e) {
            LOGGER.warning("Erreur lors de la sauvegarde du chunk (" + chunkX + "," + chunkZ + "): " + e.getMessage());
        }
    }

    /**
     * Charge un chunk depuis le disque
     * @return le chunk chargé ou null si le chunk n'existe pas ou ne peut pas être chargé
     */
    public Chunk loadChunk(int chunkX, int chunkZ) {
        Path chunkFile = getChunkFile(chunkX, chunkZ);

        if (!Files.exists(chunkFile)) {
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(chunkFile)))) {

            // Lecture des métadonnées
            int originX = ois.readInt();
            int originZ = ois.readInt();
            int version = ois.readInt();

            // Création du chunk
            Chunk chunk = new Chunk(originX, originZ);

            // Chargement des blocs
            for (int x = 0; x < Chunk.CHUNK_X; x++) {
                for (int y = 0; y < Chunk.CHUNK_Y; y++) {
                    for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                        int blockId = ois.readInt();
                        chunk.setBlockId(x, y, z, blockId);
                    }
                }
            }

            // Restauration de la version sans déclencher de nouveau bump
            chunk.setVersion(version);

            return chunk;
        } catch (IOException e) {
            LOGGER.warning("Erreur lors du chargement du chunk (" + chunkX + "," + chunkZ + "): " + e.getMessage());
            return null;
        }
    }

    /**
     * Vérifie si un chunk existe sur le disque
     */
    public boolean chunkExists(int chunkX, int chunkZ) {
        return Files.exists(getChunkFile(chunkX, chunkZ));
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
}
