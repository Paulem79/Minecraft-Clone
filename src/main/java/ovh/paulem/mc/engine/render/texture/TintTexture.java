package ovh.paulem.mc.engine.render.texture;

import ovh.paulem.mc.world.block.types.Block;

/**
 * Texture qui peut être teintée par une couleur spécifique (biome)
 * Utilisée pour l'herbe et les feuilles
 */
public class TintTexture extends Texture {
    private final TintType tintType;

    public TintTexture(Block baseBlock, String resourcePath, TintType tintType) {
        super(baseBlock, resourcePath);
        this.tintType = tintType;
    }

    public TintType getTintType() {
        return tintType;
    }

    @Override
    public String toString() {
        return "TintTexture{" +
                "tintType=" + tintType +
                "} " + super.toString();
    }
}