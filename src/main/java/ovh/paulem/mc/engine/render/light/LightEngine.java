package ovh.paulem.mc.engine.render.light;

import ovh.paulem.mc.world.Chunk;

public class LightEngine {
    // Taille maximale de la lumière (0 = obscurité, 15 = lumière maximale)
    public static final int MAX_LIGHT = 15;

    public LightEngine() {
        // Initialisation du moteur de lumière
    }

    // Méthode pour propager la lumière dans un chunk
    public void propagateLight(Chunk chunk) {
        // À implémenter : propagation de la lumière
    }

    // Propagation simple de la lumière du ciel (lumière du soleil)
    public void propagateSkyLight(Chunk chunk) {
        for (int x = 0; x < Chunk.CHUNK_X; x++) {
            for (int z = 0; z < Chunk.CHUNK_Z; z++) {
                int light = MAX_LIGHT;
                for (int y = Chunk.CHUNK_Y - 1; y >= 0; y--) {
                    int blockId = chunk.getBlockId(x, y, z);
                    // Si le bloc est opaque, la lumière s'arrête
                    if (isOpaque(blockId)) {
                        light = 0;
                    }
                    chunk.setLightLevel(x, y, z, (byte) light);
                }
            }
        }
    }

    // Détermine si un bloc est opaque (à adapter selon vos types de blocs)
    private boolean isOpaque(int blockId) {
        // Par défaut, l'air (id 0) n'est pas opaque, les autres le sont
        return blockId != 0;
    }
}
