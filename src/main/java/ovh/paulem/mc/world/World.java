package ovh.paulem.mc.world;

import ovh.paulem.mc.Values;
import ovh.paulem.mc.math.PerlinNoise;
import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.Blocks;

import java.util.*;
import java.util.concurrent.*;

public class World {

    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();

    public Collection<Chunk> getChunks() {
        return chunks.values();
    }

    private final PerlinNoise noise = new PerlinNoise(Values.SEED);
    // Bruit de Perlin pour les caves
    private final PerlinNoise caveNoise = new PerlinNoise(Values.SEED);
    // Bruits additionnels pour la diversité des cavernes
    private final PerlinNoise caveSizeNoise = new PerlinNoise(Values.SEED);
    private final PerlinNoise caveFloorNoise = new PerlinNoise(Values.SEED);
    // Background executor for async chunk generation
    private final ExecutorService chunkExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    // Track which chunk keys are currently being generated to avoid duplicate tasks
    private final Set<Long> generating = ConcurrentHashMap.newKeySet();

    // Système de sauvegarde et chargement de chunks
    private final ChunkIO chunkIO;
    // Rayon de rendu des chunks autour du joueur
    private final int renderRadius = 5;
    // Rayon supplémentaire pour ne pas décharger immédiatement les chunks
    private final int unloadBuffer = 2;

    private static long key(int cx, int cz) { return (((long)cx) << 32) ^ (cz & 0xffffffffL); }

    public World() {
        // Nom du monde - peut être paramétrable dans le futur
        chunkIO = new ChunkIO("default");

        // schedule initial chunks around origin asynchronously
        ensureChunksAround(0, 0, 2);
    }

    private Chunk createChunk(int cx, int cz) {
        int originX = cx * Chunk.CHUNK_X;
        int originZ = cz * Chunk.CHUNK_Z;
        return new Chunk(originX, originZ);
    }

    // Schedules async generation of the specified chunk if not already generating
    private void scheduleGeneration(long k, Chunk chunk, int cx, int cz) {
        if (!generating.add(k)) return; // already generating
        final int baseX = chunk.getOriginX();
        final int baseZ = chunk.getOriginZ();
        chunkExecutor.submit(() -> {
            try {
                // Génération du terrain de base
                for (int x = 0; x < Chunk.CHUNK_X; x++) {
                    for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                        double wx = (baseX + x) * 0.05;
                        double wz = (baseZ + z) * 0.05;
                        double amp = 1.0;
                        double freq = 1.0;
                        double sum = 0.0;
                        double ampSum = 0.0;
                        for (int o = 0; o < 4; o++) {
                            sum += noise.noise(wx * freq, wz * freq) * amp;
                            ampSum += amp;
                            amp *= 0.5;
                            freq *= 2.0;
                        }
                        double n = sum / ampSum;
                        double height = (n + 1.0) * 0.5 * 48.0 + 40.0;
                        int h = (int) height;
                        if (h >= Chunk.CHUNK_Y) h = Chunk.CHUNK_Y - 1;
                        for (int y = 0; y < Chunk.CHUNK_Y; y++) {
                            if (y <= h) {
                                if (y == h) chunk.setBlock(x, y, z, Blocks.GRASS_BLOCK);
                                else if (y > h - 4) chunk.setBlock(x, y, z, Blocks.DIRT);
                                else chunk.setBlock(x, y, z, Blocks.STONE);
                            } else {
                                chunk.setBlock(x, y, z, Blocks.AIR);
                            }
                        }
                    }
                }

                // Génération des caves avec bruit de Perlin 3D
                for (int x = 0; x < Chunk.CHUNK_X; x++) {
                    for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                        for (int y = Values.MIN_CAVE_HEIGHT; y < Values.MAX_CAVE_HEIGHT; y++) {
                            // Ne pas générer des caves dans l'air
                            if (!chunk.getBlock(x, y, z).isBlock()) {
                                continue;
                            }

                            // Coordonnées absolues pour les différents bruits
                            double wx = (baseX + x) * Values.BASE_CAVE_SCALE;
                            double wy = y * Values.BASE_CAVE_SCALE;
                            double wz = (baseZ + z) * Values.BASE_CAVE_SCALE;

                            // Coordonnées pour les variations de taille et de plancher
                            double wx2 = (baseX + x) * Values.SIZE_NOISE_SCALE;
                            double wy2 = y * Values.SIZE_NOISE_SCALE;
                            double wz2 = (baseZ + z) * Values.SIZE_NOISE_SCALE;

                            double wx3 = (baseX + x) * Values.FLOOR_NOISE_SCALE;
                            double wz3 = (baseZ + z) * Values.FLOOR_NOISE_SCALE;

                            // Bruit de base pour les caves
                            double caveValue = caveNoise.noise(wx, wy, wz);

                            // Variation de la taille des cavernes
                            double sizeVariation = caveSizeNoise.noise(wx2, wy2, wz2) * 0.5 + 0.5; // 0-1

                            // Variation du plancher (pour éviter les fonds plats)
                            double floorVariation = caveFloorNoise.noise(wx3, wz3) * Values.FLOOR_VARIATION_AMPLITUDE;

                            // Ajustement du seuil en fonction de la hauteur (transitions graduelles près du fond)
                            double threshold = Values.CAVE_THRESHOLD;

                            // Réduire progressivement la probabilité des caves près du fond
                            if (y < Values.MIN_CAVE_HEIGHT + Values.TRANSITION_HEIGHT) {
                                // Plus on est proche du fond, plus le seuil est élevé (moins de chances de générer une cave)
                                double factor = (double)(y - Values.MIN_CAVE_HEIGHT) / Values.TRANSITION_HEIGHT;
                                threshold = Values.CAVE_THRESHOLD + (1.0 - factor) * 0.4; // 0.4 est l'augmentation maximale du seuil
                            }

                            // Ajouter des variations de plancher basées sur le bruit
                            int effectiveMinHeight = Values.MIN_CAVE_HEIGHT;
                            if (y < Values.MIN_CAVE_HEIGHT + Values.FLOOR_VARIATION_AMPLITUDE) {
                                // Vérifier si ce bloc est au-dessus du plancher ondulé
                                int adjustedFloorHeight = Values.MIN_CAVE_HEIGHT + (int)floorVariation;
                                if (y < adjustedFloorHeight) {
                                    continue; // Ne pas creuser sous le plancher ondulé
                                }
                            }

                            // Ajuster le seuil en fonction de la taille pour créer des cavernes de tailles variées
                            double adjustedThreshold = threshold - sizeVariation * Values.SIZE_VARIATION;

                            // Si la valeur est supérieure au seuil, créer une cavité
                            if (caveValue > adjustedThreshold) {
                                chunk.setBlock(x, y, z, Blocks.AIR);

                                // Occasionnellement étendre verticalement pour créer des cavernes plus hautes
                                if (sizeVariation > 0.7 && y + 1 < Values.MAX_CAVE_HEIGHT && chunk.getBlock(x, y + 1, z).isBlock()) {
                                    chunk.setBlock(x, y + 1, z, Blocks.AIR);
                                }
                            }
                        }
                    }
                }

                // Mark chunk updated for renderers to rebuild meshes
                chunk.bumpVersion();
            } finally {
                generating.remove(k);
            }
        });
    }

    public boolean isOccluding(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_Y) return false;
        int cx = Math.floorDiv(x, Chunk.CHUNK_X);
        int cz = Math.floorDiv(z, Chunk.CHUNK_Z);
        int lx = Math.floorMod(x, Chunk.CHUNK_X);
        int lz = Math.floorMod(z, Chunk.CHUNK_Z);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return false;
        int id = c.getBlockId(lx, y, lz);
        Block block = Blocks.blocks.get(id);
        // Un bloc est solide s'il n'est pas de l'air ET n'est pas transparent
        return block != null && block.isBlock() && !block.isTransparent();
    }

    public boolean isPassable(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_Y) return false;
        int cx = Math.floorDiv(x, Chunk.CHUNK_X);
        int cz = Math.floorDiv(z, Chunk.CHUNK_Z);
        int lx = Math.floorMod(x, Chunk.CHUNK_X);
        int lz = Math.floorMod(z, Chunk.CHUNK_Z);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return false;
        int id = c.getBlockId(lx, y, lz);
        Block block = Blocks.blocks.get(id);
        // Un bloc est solide s'il n'est pas de l'air ET n'est pas transparent
        return block != null && !block.isBlock();
    }

    public Block getBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_Y) return null;
        int cx = Math.floorDiv(x, Chunk.CHUNK_X);
        int cz = Math.floorDiv(z, Chunk.CHUNK_Z);
        int lx = Math.floorMod(x, Chunk.CHUNK_X);
        int lz = Math.floorMod(z, Chunk.CHUNK_Z);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return null;
        int id = c.getBlockId(lx, y, lz);
        return Blocks.blocks.get(id);
    }

    public void update(float playerX, float playerZ) {
        int pcx = Math.floorDiv((int)Math.floor(playerX), Chunk.CHUNK_X);
        int pcz = Math.floorDiv((int)Math.floor(playerZ), Chunk.CHUNK_Z);

        // Assurez-vous que les chunks autour du joueur sont chargés
        ensureChunksAround(pcx, pcz, renderRadius);

        // Décharge les chunks éloignés et sauvegarde les chunks modifiés
        unloadDistantChunks(pcx, pcz);
    }

    private void ensureChunksAround(int centerCx, int centerCz, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;
                long k = key(cx, cz);

                // Si le chunk n'est pas chargé en mémoire
                if (!chunks.containsKey(k)) {
                    // Vérifier s'il existe sur le disque avant de le créer
                    Chunk loadedChunk = chunkIO.loadChunk(cx, cz);

                    if (loadedChunk != null) {
                        // Le chunk a été chargé depuis le disque
                        chunks.put(k, loadedChunk);
                    } else {
                        // Le chunk n'existe pas, on le crée et on programme sa génération
                        Chunk chunk = createChunk(cx, cz);
                        chunks.put(k, chunk);

                        // Programmer la génération si nécessaire
                        if (chunk.getVersion() == 0) {
                            scheduleGeneration(k, chunk, cx, cz);
                        }
                    }
                } else {
                    // Le chunk est déjà chargé, vérifier s'il faut générer
                    Chunk chunk = chunks.get(k);
                    if (chunk.getVersion() == 0) {
                        scheduleGeneration(k, chunk, cx, cz);
                    }
                }
            }
        }
    }

    /**
     * Décharge les chunks qui sont trop éloignés du joueur et sauvegarde les chunks modifiés
     */
    private void unloadDistantChunks(int playerCx, int playerCz) {
        // Rayon de déchargement = renderRadius + unloadBuffer
        int unloadRadius = renderRadius + unloadBuffer;
        int unloadRadiusSq = unloadRadius * unloadRadius;

        // Créer une liste des chunks à décharger pour éviter ConcurrentModificationException
        List<Long> chunksToUnload = new ArrayList<>();

        // Parcourir tous les chunks chargés
        for (Map.Entry<Long, Chunk> entry : chunks.entrySet()) {
            long key = entry.getKey();
            Chunk chunk = entry.getValue();

            // Extraire les coordonnées du chunk
            int cx = chunk.getOriginX() / Chunk.CHUNK_X;
            int cz = chunk.getOriginZ() / Chunk.CHUNK_Z;

            // Calculer la distance au carré entre le chunk et le joueur
            int dx = cx - playerCx;
            int dz = cz - playerCz;
            int distSq = dx*dx + dz*dz;

            // Si le chunk est trop loin du joueur
            if (distSq > unloadRadiusSq) {
                // S'il est modifié, le sauvegarder d'abord
                if (chunk.isDirty()) {
                    saveChunk(chunk);
                }

                // Ajouter à la liste des chunks à décharger
                chunksToUnload.add(key);
            }
        }

        // Décharger les chunks
        for (Long key : chunksToUnload) {
            chunks.remove(key);
        }
    }

    /**
     * Sauvegarde un chunk sur le disque
     */
    private void saveChunk(Chunk chunk) {
        chunkIO.saveChunk(chunk);
        chunk.markClean();
    }

    // Gracefully shutdown background generation threads
    public void shutdown() {
        // Sauvegarder tous les chunks modifiés avant de fermer
        for (Chunk chunk : chunks.values()) {
            if (chunk.isDirty()) {
                saveChunk(chunk);
            }
        }

        // Fermer le thread pool
        chunkExecutor.shutdown();
        try {
            if (!chunkExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                chunkExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            chunkExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void setBlock(int x, int y, int z, Block block) {
        if (y < 0 || y >= Chunk.CHUNK_Y) return; // Vérification des limites en Y

        // Obtenir le chunk contenant le bloc
        Chunk chunk = getChunkAt(x, z);
        if (chunk != null) {
            // Convertir les coordonnées globales en coordonnées locales du chunk
            int localX = Math.floorMod(x, Chunk.CHUNK_X);
            int localZ = Math.floorMod(z, Chunk.CHUNK_Z);

            // Modifier le bloc
            chunk.setBlock(localX, y, localZ, block);

            // Vérifier si le bloc est à la bordure d'un chunk et mettre à jour les chunks voisins
            if (localX == 0) {
                // Bloc à la bordure -X, mettre à jour le chunk à gauche
                Chunk neighbor = getChunk(Math.floorDiv(x, Chunk.CHUNK_X) - 1, Math.floorDiv(z, Chunk.CHUNK_Z));
                if (neighbor != null) neighbor.bumpVersion();
            }
            else if (localX == Chunk.CHUNK_X - 1) {
                // Bloc à la bordure +X, mettre à jour le chunk à droite
                Chunk neighbor = getChunk(Math.floorDiv(x, Chunk.CHUNK_X) + 1, Math.floorDiv(z, Chunk.CHUNK_Z));
                if (neighbor != null) neighbor.bumpVersion();
            }

            if (localZ == 0) {
                // Bloc à la bordure -Z, mettre à jour le chunk devant
                Chunk neighbor = getChunk(Math.floorDiv(x, Chunk.CHUNK_X), Math.floorDiv(z, Chunk.CHUNK_Z) - 1);
                if (neighbor != null) neighbor.bumpVersion();
            }
            else if (localZ == Chunk.CHUNK_Z - 1) {
                // Bloc à la bordure +Z, mettre à jour le chunk derrière
                Chunk neighbor = getChunk(Math.floorDiv(x, Chunk.CHUNK_X), Math.floorDiv(z, Chunk.CHUNK_Z) + 1);
                if (neighbor != null) neighbor.bumpVersion();
            }
        }
    }

    private Chunk getChunkAt(int x, int z) {
        int cx = Math.floorDiv(x, Chunk.CHUNK_X);
        int cz = Math.floorDiv(z, Chunk.CHUNK_Z);
        return getChunk(cx, cz);
    }

    private Chunk getChunk(int cx, int cz) {
        long k = key(cx, cz);
        return chunks.computeIfAbsent(k, k1 -> {throw new RuntimeException("Chunk not found: " + cx + ", " + cz);});
    }
}
