package com.example.mc.engine.renderer.texture;

/**
 * Texture avec superposition (overlay)
 * Utilisée pour les côtés des blocs d'herbe qui combinent
 * une texture de base et une texture de superposition
 */
public class OverlayTexture extends Texture {
    private final Texture overlay;
    private final boolean tinted;  // Si l'overlay doit être teinté (comme l'herbe)

    public OverlayTexture(String baseTexturePath, String overlayTexturePath, boolean tinted) {
        super(baseTexturePath);
        this.overlay = new Texture(overlayTexturePath);
        this.tinted = tinted;
    }

    public Texture getOverlay() {
        return overlay;
    }

    public boolean isTinted() {
        return tinted;
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
}