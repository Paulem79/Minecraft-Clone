package ovh.paulem.mc.world;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import ovh.paulem.mc.Values;
import ovh.paulem.mc.engine.render.light.LightEngine;
import ovh.paulem.mc.math.PerlinNoise;
import ovh.paulem.mc.math.Seeds;
import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.Blocks;

import java.util.*;
import java.util.concurrent.*;

public class World {

    // Remplace Map<Long, Chunk> par Map<Long, Future<Chunk>> pour éviter les chargements multiples
    private final Map<Long, Future<Chunk>> chunkFutures = new ConcurrentHashMap<>();

    public Collection<Chunk> getChunks() {
        List<Chunk> result = new ArrayList<>();
        for (Future<Chunk> f : chunkFutures.values()) {
            try {
                Chunk c = f.get();
                if (c != null) result.add(c);
            } catch (Exception ignored) {}
        }
        return result;
    }

    @Getter
    private final long seed;
    private final PerlinNoise noise;
    private final PerlinNoise caveNoise;
    private final PerlinNoise caveSizeNoise;
    private final PerlinNoise caveFloorNoise;
    private final PerlinNoise biomeNoise; // Nouveau bruit de Perlin pour la carte des biomes
    // Background executor for async chunk generation
    private final ExecutorService chunkExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));

    // Système de sauvegarde et chargement de chunks
    private final ChunkIO chunkIO;
    // Rayon supplémentaire pour ne pas décharger immédiatement les chunks
    private final int unloadBuffer = 2;

    @Getter
    private final LightEngine lightEngine = new LightEngine();

    private static long key(int cx, int cz) { return (((long)cx) << 32) ^ (cz & 0xffffffffL); }

    public World() {
        // Nom du monde - peut être paramétrable dans le futur
        chunkIO = new ChunkIO(this, "default");
        Long loadedSeed = chunkIO.loadWorldSeed();
        if (loadedSeed != null) {
            this.seed = loadedSeed;
        } else {
            this.seed = Seeds.stringToSeed(java.util.UUID.randomUUID().toString());
            chunkIO.saveWorldSeed(this.seed);
        }
        System.out.println("Seed: " + this.seed);
        noise = new PerlinNoise(this.seed);
        caveNoise = new PerlinNoise(this.seed);
        caveSizeNoise = new PerlinNoise(this.seed);
        caveFloorNoise = new PerlinNoise(this.seed);
        biomeNoise = new PerlinNoise(this.seed + 42L); // bruit indépendant pour la carte des biomes
        // schedule initial chunks around origin asynchronously
        ensureChunksAround(0, 0, 2);
    }

    private Chunk createChunk(int cx, int cz) {
        int originX = cx * Chunk.CHUNK_X;
        int originZ = cz * Chunk.CHUNK_Z;
        return new Chunk(this, originX, originZ);
    }

    public boolean isOccluding(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_Y) return false;
        int cx = Math.floorDiv(x, Chunk.CHUNK_X);
        int cz = Math.floorDiv(z, Chunk.CHUNK_Z);
        int lx = Math.floorMod(x, Chunk.CHUNK_X);
        int lz = Math.floorMod(z, Chunk.CHUNK_Z);
        Future<Chunk> f = chunkFutures.get(key(cx, cz));
        if (f == null) return false;
        try {
            Chunk c = f.get();
            if (c == null) return false;
            int id = c.getBlockId(lx, y, lz);
            Block block = Blocks.blocks.get(id);
            return block != null && block.isBlock() && !block.isTransparent();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPassable(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_Y) return false;
        int cx = Math.floorDiv(x, Chunk.CHUNK_X);
        int cz = Math.floorDiv(z, Chunk.CHUNK_Z);
        int lx = Math.floorMod(x, Chunk.CHUNK_X);
        int lz = Math.floorMod(z, Chunk.CHUNK_Z);
        Future<Chunk> f = chunkFutures.get(key(cx, cz));
        if (f == null) return false;
        try {
            Chunk c = f.get();
            if (c == null) return false;
            int id = c.getBlockId(lx, y, lz);
            Block block = Blocks.blocks.get(id);
            return block != null && !block.isBlock();
        } catch (Exception e) {
            return false;
        }
    }

    // Nouvelle méthode pour la génération synchrone (extrait de scheduleGeneration)
    private void generateChunk(Chunk chunk, int cx, int cz) {
        final int baseX = chunk.getOriginX();
        final int baseZ = chunk.getOriginZ();
        for (int x = 0; x < Chunk.CHUNK_X; x++) {
            for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                int wxInt = baseX + x;
                int wzInt = baseZ + z;
                // Lissage du bruit de biome (moyenne 3x3)
                double bSum = 0.0;
                int bCount = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        bSum += biomeNoise.noise((wxInt + dx) * 0.003, (wzInt + dz) * 0.003);
                        bCount++;
                    }
                }
                double b = bSum / bCount;
                // Interpolation des paramètres selon le bruit de biome lissé
                BiomeParams params;
                if (b <= -0.35) {
                    params = new BiomeParams(
                        Biome.PLAINS.relief, Biome.PLAINS.baseHeight, Biome.PLAINS.heightScale, Biome.PLAINS.octaves, Biome.PLAINS.terrainFrequency);
                } else if (b >= 0.35) {
                    params = new BiomeParams(
                        Biome.MOUNTAINS.relief, Biome.MOUNTAINS.baseHeight, Biome.MOUNTAINS.heightScale, Biome.MOUNTAINS.octaves, Biome.MOUNTAINS.terrainFrequency);
                } else if (b < 0) {
                    double t = (b + 0.35) / 0.35;
                    params = interpolateBiome(Biome.PLAINS, Biome.NORMAL, t);
                } else {
                    double t = b / 0.35;
                    params = interpolateBiome(Biome.NORMAL, Biome.MOUNTAINS, t);
                }
                double wx = wxInt * 0.05 * params.terrainFrequency;
                double wz = wzInt * 0.05 * params.terrainFrequency;
                double amp = 1.0;
                double freq = 1.0;
                double sum = 0.0;
                double ampSum = 0.0;
                for (int o = 0; o < (int)params.octaves; o++) {
                    sum += noise.noise(wx * freq, wz * freq) * amp;
                    ampSum += amp;
                    amp *= 0.5;
                    freq *= 2.0;
                }
                double n = sum / ampSum;
                double height = (n * params.relief + 1.0) * 0.5 * params.heightScale + params.baseHeight;
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

        // Génération des caves
        for (int x = 0; x < Chunk.CHUNK_X; x++) {
            for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                int wxInt = baseX + x;
                int wzInt = baseZ + z;
                // Récupère les paramètres du biome pour ce point
                double bSum = 0.0;
                int bCount = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        bSum += biomeNoise.noise((wxInt + dx) * 0.003, (wzInt + dz) * 0.003);
                        bCount++;
                    }
                }
                double b = bSum / bCount;
                BiomeParams params;
                if (b <= -0.35) {
                    params = new BiomeParams(
                        Biome.PLAINS.relief, Biome.PLAINS.baseHeight, Biome.PLAINS.heightScale, Biome.PLAINS.octaves, Biome.PLAINS.terrainFrequency);
                } else if (b >= 0.35) {
                    params = new BiomeParams(
                        Biome.MOUNTAINS.relief, Biome.MOUNTAINS.baseHeight, Biome.MOUNTAINS.heightScale, Biome.MOUNTAINS.octaves, Biome.MOUNTAINS.terrainFrequency);
                } else if (b < 0) {
                    double t = (b + 0.35) / 0.35;
                    params = interpolateBiome(Biome.PLAINS, Biome.NORMAL, t);
                } else {
                    double t = b / 0.35;
                    params = interpolateBiome(Biome.NORMAL, Biome.MOUNTAINS, t);
                }
                for (int y = Values.MIN_CAVE_HEIGHT; y < Values.MAX_CAVE_HEIGHT; y++) {
                    if (!chunk.getBlock(x, y, z).isBlock()) {
                        continue;
                    }
                    // On adapte la fréquence et la taille des caves selon le biome
                    double caveFreq = 1.0 / (0.7 + params.terrainFrequency * 0.6); // montagnes = freq plus basse = caves plus larges
                    double caveAmp = 1.0 + params.relief * 0.2; // plus de relief = caves plus hautes
                    double wx = (baseX + x) * Values.BASE_CAVE_SCALE * caveFreq;
                    double wy = y * Values.BASE_CAVE_SCALE * caveAmp;
                    double wz = (baseZ + z) * Values.BASE_CAVE_SCALE * caveFreq;
                    double wx2 = (baseX + x) * Values.SIZE_NOISE_SCALE * caveFreq;
                    double wy2 = y * Values.SIZE_NOISE_SCALE * caveAmp;
                    double wz2 = (baseZ + z) * Values.SIZE_NOISE_SCALE * caveFreq;
                    double wx3 = (baseX + x) * Values.FLOOR_NOISE_SCALE;
                    double wz3 = (baseZ + z) * Values.FLOOR_NOISE_SCALE;
                    double caveValue = caveNoise.noise(wx, wy, wz);
                    double sizeVariation = caveSizeNoise.noise(wx2, wy2, wz2) * 0.5 + 0.5;
                    double floorVariation = caveFloorNoise.noise(wx3, wz3) * Values.FLOOR_VARIATION_AMPLITUDE;
                    double threshold = Values.CAVE_THRESHOLD;
                    if (y < Values.MIN_CAVE_HEIGHT + Values.TRANSITION_HEIGHT) {
                        double factor = (double)(y - Values.MIN_CAVE_HEIGHT) / Values.TRANSITION_HEIGHT;
                        threshold = Values.CAVE_THRESHOLD + (1.0 - factor) * 0.4;
                    }
                    int effectiveMinHeight = Values.MIN_CAVE_HEIGHT;
                    if (y < Values.MIN_CAVE_HEIGHT + Values.FLOOR_VARIATION_AMPLITUDE) {
                        int adjustedFloorHeight = Values.MIN_CAVE_HEIGHT + (int)floorVariation;
                        if (y < adjustedFloorHeight) {
                            continue;
                        }
                    }
                    double adjustedThreshold = threshold - sizeVariation * Values.SIZE_VARIATION;
                    if (caveValue > adjustedThreshold) {
                        chunk.setBlock(x, y, z, Blocks.AIR);
                        if (sizeVariation > 0.7 && y + 1 < Values.MAX_CAVE_HEIGHT && chunk.getBlock(x, y + 1, z).isBlock()) {
                            chunk.setBlock(x, y + 1, z, Blocks.AIR);
                        }
                    }
                }
            }
        }
        chunk.bumpVersion();
        chunk.bakeLight();
    }

    public Block getBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_Y) return null;
        int cx = Math.floorDiv(x, Chunk.CHUNK_X);
        int cz = Math.floorDiv(z, Chunk.CHUNK_Z);
        long k = key(cx, cz);
        Future<Chunk> f = chunkFutures.get(k);
        if (f == null) return null;
        try {
            Chunk c = f.get();
            if (c == null) return null;
            int lx = Math.floorMod(x, Chunk.CHUNK_X);
            int lz = Math.floorMod(z, Chunk.CHUNK_Z);
            int id = c.getBlockId(lx, y, lz);
            return Blocks.blocks.get(id);
        } catch (Exception e) {
            return null;
        }
    }

    public void update(float playerX, float playerZ) {
        int pcx = Math.floorDiv((int)Math.floor(playerX), Chunk.CHUNK_X);
        int pcz = Math.floorDiv((int)Math.floor(playerZ), Chunk.CHUNK_Z);

        getLightEngine().processLightQueue();

        // Assurez-vous que les chunks autour du joueur sont chargés
        ensureChunksAround(pcx, pcz, Values.RENDER_RADIUS);

        // Décharge les chunks éloignés et sauvegarde les chunks modifiés
        unloadDistantChunks(pcx, pcz);

        getLightEngine().processLightQueue();
    }

    private void ensureChunksAround(int centerCx, int centerCz, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;
                long k = key(cx, cz);

                chunkFutures.computeIfAbsent(k, key -> chunkExecutor.submit(() -> {
                    // Tente de charger depuis le disque
                    Chunk loadedChunk = chunkIO.loadChunk(cx, cz);
                    if (loadedChunk != null) {
                        return loadedChunk;
                    } else {
                        // Génère le chunk si absent
                        Chunk chunk = createChunk(cx, cz);
                        if (chunk.getVersion() == 0) {
                            // Génération synchrone ici (sinon il faudrait chaîner les futures)
                            generateChunk(chunk, cx, cz);
                        }
                        return chunk;
                    }
                }));
            }
        }
    }

    /**
     * Décharge les chunks qui sont trop éloignés du joueur et sauvegarde les chunks modifiés
     */
    private void unloadDistantChunks(int playerCx, int playerCz) {
        int unloadRadius = Values.RENDER_RADIUS + unloadBuffer;
        int unloadRadiusSq = unloadRadius * unloadRadius;
        List<Long> chunksToUnload = new ArrayList<>();
        for (Map.Entry<Long, Future<Chunk>> entry : chunkFutures.entrySet()) {
            long key = entry.getKey();
            try {
                Chunk chunk = entry.getValue().get();
                if (chunk == null) continue;
                int cx = chunk.getOriginX() / Chunk.CHUNK_X;
                int cz = chunk.getOriginZ() / Chunk.CHUNK_Z;
                int dx = cx - playerCx;
                int dz = cz - playerCz;
                int distSq = dx*dx + dz*dz;
                if (distSq > unloadRadiusSq) {
                    if (chunk.isDirty()) {
                        saveChunk(chunk);
                    }
                    chunksToUnload.add(key);
                }
            } catch (Exception ignored) {}
        }
        for (Long key : chunksToUnload) {
            chunkFutures.remove(key);
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
        for (Future<Chunk> f : chunkFutures.values()) {
            try {
                Chunk chunk = f.get();
                if (chunk != null && chunk.isDirty()) {
                    saveChunk(chunk);
                }
            } catch (Exception ignored) {}
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

        getLightEngine().getLightExecutor().shutdown();
        try {
            if (!getLightEngine().getLightExecutor().awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                getLightEngine().getLightExecutor().shutdownNow();
            }
        } catch (InterruptedException e) {
            getLightEngine().getLightExecutor().shutdownNow();
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

            // --- Mise à jour dynamique de la lumière ---
            lightEngine.propagateSkyLight(chunk);
            chunk.markDirty();
            // --- Fin lumière dynamique ---

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

    @Nullable
    public Chunk getChunkAt(int x, int z) {
        int cx = Math.floorDiv(x, Chunk.CHUNK_X);
        int cz = Math.floorDiv(z, Chunk.CHUNK_Z);
        return getChunk(cx, cz);
    }

    @Nullable
    public Chunk getChunk(int cx, int cz) {
        long k = key(cx, cz);
        Future<Chunk> f = chunkFutures.get(k);
        if (f == null) return null;
        try {
            return f.get();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Retourne le biome à une position monde (x, z)
     */
    public Biome getBiomeAt(int x, int z) {
        double b = biomeNoise.noise(x * 0.003, z * 0.003);
        // Utilisation d'un blend lissé pour la transition
        if (b > 0.35) return Biome.MOUNTAINS;
        if (b < -0.35) return Biome.PLAINS;
        // Zone de transition lissée
        if (b > 0.15 && b <= 0.35) {
            double t = (b - 0.15) / (0.35 - 0.15); // t de 0 à 1
            // Mélange progressif, bruit secondaire pour la cohérence spatiale
            double blend = (biomeNoise.noise((x+1000)*0.01, (z-1000)*0.01) + 1) * 0.5;
            return blend < t ? Biome.MOUNTAINS : Biome.NORMAL;
        }
        if (b < -0.15 && b >= -0.35) {
            double t = (-0.15 - b) / (0.35 - 0.15); // t de 0 à 1
            double blend = (biomeNoise.noise((x-1000)*0.01, (z+1000)*0.01) + 1) * 0.5;
            return blend < t ? Biome.PLAINS : Biome.NORMAL;
        }
        return Biome.NORMAL;
    }

    // Interpolation linéaire entre deux biomes
    private static BiomeParams interpolateBiome(Biome a, Biome b, double t) {
        return new BiomeParams(
            lerp(a.relief, b.relief, t),
            lerp(a.baseHeight, b.baseHeight, t),
            lerp(a.heightScale, b.heightScale, t),
            lerp(a.octaves, b.octaves, t),
            lerp(a.terrainFrequency, b.terrainFrequency, t)
        );
    }
    private static double lerp(double a, double b, double t) {
        return a * (1 - t) + b * t;
    }
    private static class BiomeParams {
        final double relief, baseHeight, heightScale, octaves, terrainFrequency;
        BiomeParams(double relief, double baseHeight, double heightScale, double octaves, double terrainFrequency) {
            this.relief = relief;
            this.baseHeight = baseHeight;
            this.heightScale = heightScale;
            this.octaves = octaves;
            this.terrainFrequency = terrainFrequency;
        }
    }
}
