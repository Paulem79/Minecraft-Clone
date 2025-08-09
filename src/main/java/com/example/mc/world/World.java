package com.example.mc.world;

import com.example.mc.util.PerlinNoise;
import com.example.mc.world.block.Blocks;

import java.util.*;
import java.util.concurrent.*;

public class World {
    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private final PerlinNoise noise = new PerlinNoise(UUID.randomUUID().toString().hashCode());

    private static long key(int cx, int cz) { return (((long)cx) << 32) ^ (cz & 0xffffffffL); }

    public World() {
        // generate initial chunks around origin
        ensureChunksAround(0, 0, 2);
    }

    private Chunk createChunk(int cx, int cz) {
        int originX = cx * Chunk.CHUNK_X;
        int originZ = cz * Chunk.CHUNK_Z;
        Chunk chunk = new Chunk(originX, originZ);
        generateTerrainForChunk(chunk);
        return chunk;
    }

    private void generateTerrainForChunk(Chunk chunk) {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Runnable> tasks = new ArrayList<>();

        int baseX = chunk.getOriginX();
        int baseZ = chunk.getOriginZ();

        for (int x = 0; x < Chunk.CHUNK_X; x++) {
            final int fx = x;
            tasks.add(() -> {
                for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                    // Multi-octave Perlin height for nicer terrain (use world coordinates)
                    double wx = (baseX + fx) * 0.05;
                    double wz = (baseZ + z) * 0.05;
                    double amp = 1.0;
                    double freq = 1.0;
                    double sum = 0.0;
                    double ampSum = 0.0;
                    for (int o = 0; o < 4; o++) { // 4 octaves
                        sum += noise.noise(wx * freq, wz * freq) * amp;
                        ampSum += amp;
                        amp *= 0.5;
                        freq *= 2.0;
                    }
                    double n = sum / ampSum; // roughly [-1,1]
                    double height = (n + 1.0) * 0.5 * 48.0 + 40.0; // approx 40..88
                    int h = (int) height;
                    if (h >= Chunk.CHUNK_Y) h = Chunk.CHUNK_Y - 1;
                    for (int y = 0; y < Chunk.CHUNK_Y; y++) {
                        if (y <= h) {
                            if (y == h) chunk.setBlock(fx, y, z, Blocks.GRASS_BLOCK);
                            else chunk.setBlock(fx, y, z, Blocks.DIRT);
                        } else {
                            chunk.setBlock(fx, y, z, Blocks.AIR);
                        }
                    }
                }
            });
        }

        for (Runnable r : tasks) pool.submit(r);
        pool.shutdown();
        try {
            pool.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isSolid(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_Y) return false;
        int cx = Math.floorDiv(x, Chunk.CHUNK_X);
        int cz = Math.floorDiv(z, Chunk.CHUNK_Z);
        int lx = Math.floorMod(x, Chunk.CHUNK_X);
        int lz = Math.floorMod(z, Chunk.CHUNK_Z);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return false;
        return c.getBlockId(lx, y, lz) != Blocks.AIR.getId();
    }

    public void update(float playerX, float playerZ) {
        int pcx = Math.floorDiv((int)Math.floor(playerX), Chunk.CHUNK_X);
        int pcz = Math.floorDiv((int)Math.floor(playerZ), Chunk.CHUNK_Z);
        ensureChunksAround(pcx, pcz, 20);
        // Optional: could unload far chunks here to cap memory
    }

    private void ensureChunksAround(int centerCx, int centerCz, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;
                long k = key(cx, cz);
                chunks.computeIfAbsent(k, kk -> createChunk(cx, cz));
            }
        }
    }

    public Collection<Chunk> getChunks() { return chunks.values(); }
}