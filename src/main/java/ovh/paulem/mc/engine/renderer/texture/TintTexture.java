package ovh.paulem.mc.engine.renderer.texture;

/**
 * Texture qui peut être teintée par une couleur spécifique (biome)
 * Utilisée pour l'herbe et les feuilles
 */
public class TintTexture extends Texture {
    // Type de teinte
    public enum TintType {
        GRASS,
        FOLIAGE
    }

    private final TintType tintType;

    public TintTexture(String resourcePath, TintType tintType) {
        super(resourcePath);
        this.tintType = tintType;
    }

    public TintType getTintType() {
        return tintType;
    }
}