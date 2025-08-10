package ovh.paulem.mc.engine.render.texture;

import ovh.paulem.mc.world.block.types.Block;

/**
 * Texture avec superposition (overlay)
 * Utilisée pour les côtés des blocs d'herbe qui combinent
 * une texture de base et une texture de superposition
 */
public class OverlayTexture extends Texture {
    private final TintTexture overlay;

    public OverlayTexture(Block baseBlock, String baseTexturePath, String overlayTexturePath) {
        super(baseBlock, baseTexturePath);
        this.overlay = new TintTexture(baseBlock, overlayTexturePath, TintType.GRASS);
    }

    public TintTexture getOverlay() {
        return overlay;
    }
    
    @Override
    public void bind(int unit) {
        // Lie la texture principale sur l'unité spécifiée
        super.bind(unit);
        // Lie la texture de superposition sur l'unité suivante
        overlay.bind(unit + 1);
    }
    
    @Override
    public void delete() {
        super.delete();
        overlay.delete();
    }

    @Override
    public String toString() {
        return "OverlayTexture{" +
                "overlay=" + overlay +
                "} " + super.toString();
    }
}