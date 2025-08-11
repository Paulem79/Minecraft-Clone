package ovh.paulem.mc.engine.render.light;

import lombok.Getter;
import ovh.paulem.mc.world.Chunk;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.Set;
import java.util.HashSet;

public class LightEngine {
    // Taille maximale de la lumière (0 = obscurité, 15 = lumière maximale)
    public static final int MAX_LIGHT = 15;

    // File d'attente des chunks à éclairer
    private final Queue<Chunk> lightQueue = new ArrayDeque<>();
    // Chunks déjà en cours de traitement pour éviter les doublons
    private final Set<Chunk> processing = new HashSet<>();
    // Thread pool pour la lumière
    @Getter
    private final ExecutorService lightExecutor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    // Budget de chunks à traiter par frame
    private final int lightPerFrameBudget = 5;

    public LightEngine() {
        // Initialisation du moteur de lumière
    }

    public void enqueueLightUpdate(Chunk chunk) {
        synchronized (lightQueue) {
            if (!processing.contains(chunk)) {
                lightQueue.add(chunk);
                processing.add(chunk);
            }
        }
    }

    // À appeler à chaque frame (depuis World ou Render)
    public void processLightQueue() {
        int processed = 0;
        while (processed < lightPerFrameBudget) {
            Chunk chunk;
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
    private void propagateSkyLightSync(Chunk chunk) {
        // 1. Propagation verticale (remplir la colonne d'air)
        byte[][][] skyLight = new byte[Chunk.CHUNK_X][Chunk.CHUNK_Y][Chunk.CHUNK_Z];
        Queue<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < Chunk.CHUNK_X; x++) {
            for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                int light = MAX_LIGHT;
                for (int y = Chunk.CHUNK_Y - 1; y >= 0; y--) {
                    int blockId = chunk.getBlockId(x, y, z);
                    if (isOpaque(blockId)) {
                        light = 0;
                    }
                    skyLight[x][y][z] = (byte) light;
                    chunk.setLightLevel(x, y, z, (byte) light);
                    if (light > 0 && !isOpaque(blockId)) {
                        // Ajouter à la file pour la propagation horizontale
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }
        // 2. Propagation horizontale (BFS)
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int x = pos[0], y = pos[1], z = pos[2];
            byte current = skyLight[x][y][z];
            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];
                if (nx < 0 || nx >= Chunk.CHUNK_X || ny < 0 || ny >= Chunk.CHUNK_Y || nz < 0 || nz >= Chunk.CHUNK_Z)
                    continue;
                int neighborId = chunk.getBlockId(nx, ny, nz);
                if (isOpaque(neighborId)) continue;
                byte neighborLight = skyLight[nx][ny][nz];
                int newLight = (d[1] == -1) ? current : current - 1; // vers le bas : pas d'atténuation
                if (newLight > 0 && neighborLight < newLight) {
                    skyLight[nx][ny][nz] = (byte) newLight;
                    chunk.setLightLevel(nx, ny, nz, (byte) newLight);
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
    }

    // Propagation améliorée de la lumière du ciel (lumière du soleil)
    public void propagateSkyLight(Chunk chunk) {
        enqueueLightUpdate(chunk);
    }

    // Détermine si un bloc est opaque (à adapter selon vos types de blocs)
    private boolean isOpaque(int blockId) {
        // Par défaut, l'air (id 0) n'est pas opaque, les autres le sont
        return blockId != 0;
    }
}
