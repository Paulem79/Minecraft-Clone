package ovh.paulem.mc.engine.render.light;

import lombok.Getter;
import ovh.paulem.mc.Values;
import ovh.paulem.mc.world.BaseChunk;
import ovh.paulem.mc.world.Chunk;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.Set;
import java.util.HashSet;

public class LightEngine {

    // File d'attente des chunks à éclairer
    private final Queue<BaseChunk> lightQueue = new ArrayDeque<>();
    // Chunks déjà en cours de traitement pour éviter les doublons
    private final Set<BaseChunk> processing = new HashSet<>();
    // Thread pool pour la lumière
    @Getter
    private final ExecutorService lightExecutor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    private static final int MAX_LIGHT_QUEUE_SIZE = 100_000;

    public LightEngine() {
        // Initialisation du moteur de lumière
    }

    // À appeler à chaque frame (depuis World ou Render)
    public void processLightQueue() {
        int processed = 0;
        while (processed < Values.LIGHT_PER_FRAME_BUDGET) {
            BaseChunk chunk;
            synchronized (lightQueue) {
                chunk = lightQueue.poll();
                if (chunk == null) break;
            }
            lightExecutor.submit(() -> {
                try {
                    propagateSkyLightSync(chunk);
                } finally {
                    synchronized (lightQueue) {
                        processing.remove(chunk);
                    }
                }
            });
            processed++;
        }
    }

    // Appel synchrone (interne, ne pas utiliser directement)
    private void propagateSkyLightSync(BaseChunk chunk) {
        // 1. Propagation verticale (remplir la colonne d'air)
        Queue<Short> queue = new ArrayDeque<>();
        for (byte x = 0; x < Chunk.CHUNK_X; x++) {
            for (byte z = 0; z < Chunk.CHUNK_Z; z++) {
                int light = Values.MAX_LIGHT;
                for (int y = Chunk.CHUNK_Y - 1; y >= 0; y--) {
                    byte blockId = chunk.getBlockId(x, y, z);
                    if (isOpaque(blockId)) {
                        light = 0;
                    }
                    chunk.setLightLevel(x, y, z, (byte) light);
                    if (light > 0) {
                        if (queue.size() < MAX_LIGHT_QUEUE_SIZE) {
                            queue.add(encodePos(x, y, z));
                        } else {
                            System.err.println("[LightEngine] Limite de queue atteinte lors de la propagation verticale, arrêt de la propagation.");
                            return;
                        }
                    }
                }
            }
        }

        // 2. Propagation horizontale (BFS)
        byte[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while (!queue.isEmpty()) {
            short pos = queue.poll();
            int x = decodeX(pos);
            int y = decodeY(pos);
            int z = decodeZ(pos);
            byte current = chunk.getLightLevel((byte)x, (byte)y, (byte)z);
            for (byte[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];
                if (nx < 0 || nx >= Chunk.CHUNK_X || ny < 0 || ny >= Chunk.CHUNK_Y || nz < 0 || nz >= Chunk.CHUNK_Z)
                    continue;
                byte neighborId = chunk.getBlockId((byte)nx, (byte)ny, (byte)nz);
                if (isOpaque(neighborId)) continue;
                byte neighborLight = chunk.getLightLevel((byte)nx, (byte)ny, (byte)nz);
                int newLight = (d[1] == -1) ? current : current - 1; // vers le bas : pas d'atténuation
                if (newLight > 0 && neighborLight < newLight) {
                    chunk.setLightLevel((byte)nx, (byte)ny, (byte)nz, (byte) newLight);
                    if (queue.size() < MAX_LIGHT_QUEUE_SIZE) {
                        queue.add(encodePos(nx, ny, nz));
                    } else {
                        System.err.println("[LightEngine] Limite de queue atteinte lors de la propagation horizontale, arrêt de la propagation.");
                        return;
                    }
                }
            }
        }
    }

    // Encode x, y, z dans un int (8 bits chacun, max 256)
    private short encodePos(int x, int y, int z) {
        return (short) ((x & 0xFF) | ((y & 0xFF) << 8) | ((z & 0xFF) << 16));
    }
    private int decodeX(short pos) { return pos & 0xFF; }
    private int decodeY(short pos) { return (pos >> 8) & 0xFF; }
    private int decodeZ(short pos) { return (pos >> 16) & 0xFF; }

    // Propagation améliorée de la lumière du ciel (lumière du soleil)
    public void propagateSkyLight(BaseChunk chunk) {
        synchronized (lightQueue) {
            if (!processing.contains(chunk)) {
                lightQueue.add(chunk);
                processing.add(chunk);
            }
        }
    }

    // Détermine si un bloc est opaque (à adapter selon vos types de blocs)
    private boolean isOpaque(byte blockId) {
        // Par défaut, l'air (id 0) n'est pas opaque, les autres le sont
        return blockId != 0;
    }
}
