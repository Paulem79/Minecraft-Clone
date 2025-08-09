package com.example.mc.world;

import com.example.mc.util.PerlinNoise;
import com.example.mc.world.block.Blocks;

import java.util.*;
import java.util.concurrent.*;

public class World {
    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private final PerlinNoise noise = new PerlinNoise(UUID.randomUUID().toString().hashCode());
    // Background executor for async chunk generation
    private final ExecutorService chunkExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    // Track which chunk keys are currently being generated to avoid duplicate tasks
    private final Set<Long> generating = ConcurrentHashMap.newKeySet();

    private static long key(int cx, int cz) { return (((long)cx) << 32) ^ (cz & 0xffffffffL); }

    public World() {
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
                                else chunk.setBlock(x, y, z, Blocks.DIRT);
                            } else {
                                chunk.setBlock(x, y, z, Blocks.AIR);
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
                Chunk chunk = chunks.computeIfAbsent(k, kk -> createChunk(cx, cz));
                // If this chunk has not been generated yet (version==0), schedule generation
                if (chunk.getVersion() == 0) {
                    scheduleGeneration(k, chunk, cx, cz);
                }
            }
        }
    }

    public Collection<Chunk> getChunks() { return chunks.values(); }

    // Gracefully shutdown background generation threads
    public void shutdown() {
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
}