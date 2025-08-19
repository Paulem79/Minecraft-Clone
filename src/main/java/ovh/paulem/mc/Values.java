package ovh.paulem.mc;

public class Values {
    // Rayon de rendu des chunks autour du joueur
    public static final int RENDER_RADIUS = 8;
    // Rayon proche avec chunks détaillés (pleine fidélité); au-delà, stockage compressé/LOD (GreedyChunk)
    public static final int LOD_NEAR_RADIUS = 2;
    // Facteurs de LOD pour les chunks lointains (taille des macro-voxels)
    public static final int LOD_FACTOR_XZ = 2; // groupe 2x2 en X/Z
    public static final int LOD_FACTOR_Y = 2;  // groupe 2 en Y
    // Taille maximale de la lumière (0 = obscurité, 15 = lumière maximale)
    public static final byte MAX_LIGHT = 15;
    // Budget de chunks à traiter par frame
    public static final int LIGHT_PER_FRAME_BUDGET = RENDER_RADIUS*10;
    public static final int MESHES_PER_FRAME_BUDGET = RENDER_RADIUS/4;
    public static final float GREEDY_DIST = 80.0f;

    // Configuration des caves
    public static double BASE_CAVE_SCALE = 0.05; // Échelle de base du bruit pour les caves
    public static double SIZE_NOISE_SCALE = 0.15; // Échelle du bruit pour la variation de taille
    public static double FLOOR_NOISE_SCALE = 0.8; // Échelle du bruit pour la variation du plancher
    public static double CAVE_THRESHOLD = 0.5; // Seuil de base pour la création de caves
    public static int MIN_CAVE_HEIGHT = 2; // Hauteur minimale pour les caves
    public static int MAX_CAVE_HEIGHT = 60; // Hauteur maximale pour les caves
    public static double SIZE_VARIATION = 0.5; // Variations de seuil pour les caves
    public static int FLOOR_VARIATION_AMPLITUDE = 10; // Amplitude des variations de hauteur du plancher
    public static int TRANSITION_HEIGHT = 10; // Hauteur sur laquelle la probabilité de générer des caves diminue près du fond
}
