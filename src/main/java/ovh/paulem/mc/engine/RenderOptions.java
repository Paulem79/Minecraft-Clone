package ovh.paulem.mc.engine;

/**
 * Gère les options de rendu configurables du jeu
 */
public class RenderOptions {
    // Distance de rendu (en chunks)
    private int renderDistance = 8;
    
    // Options d'antialiasing
    private boolean antialiasing = false;
    private int antialiasingLevel = 2; // 2x, 4x, 8x, etc.
    
    // Options LOD
    private float lodDistance = 80.0f;
    private boolean enableLod = true;
    
    // Autres options de rendu
    private boolean vsync = true;
    private boolean showChunkBorders = false;
    private int maxFps = 0; // 0 = illimité
    
    // Budget de performance par frame
    private int meshesPerFrameBudget = 2;
    
    public RenderOptions() {
        // Valeurs par défaut
    }
    
    // Getters
    public int getRenderDistance() { return renderDistance; }
    public boolean isAntialiasingEnabled() { return antialiasing; }
    public int getAntialiasingLevel() { return antialiasingLevel; }
    public float getLodDistance() { return lodDistance; }
    public boolean isLodEnabled() { return enableLod; }
    public boolean isVsyncEnabled() { return vsync; }
    public boolean isShowChunkBorders() { return showChunkBorders; }
    public int getMaxFps() { return maxFps; }
    public int getMeshesPerFrameBudget() { return meshesPerFrameBudget; }
    
    // Setters avec validation
    public void setRenderDistance(int distance) {
        this.renderDistance = Math.max(2, Math.min(32, distance));
    }
    
    public void setAntialiasing(boolean enabled) {
        this.antialiasing = enabled;
    }
    
    public void setAntialiasingLevel(int level) {
        // Niveaux valides: 2, 4, 8, 16
        if (level == 2 || level == 4 || level == 8 || level == 16) {
            this.antialiasingLevel = level;
        }
    }
    
    public void setLodDistance(float distance) {
        this.lodDistance = Math.max(20.0f, Math.min(500.0f, distance));
    }
    
    public void setLodEnabled(boolean enabled) {
        this.enableLod = enabled;
    }
    
    public void setVsync(boolean enabled) {
        this.vsync = enabled;
    }
    
    public void setShowChunkBorders(boolean show) {
        this.showChunkBorders = show;
    }
    
    public void setMaxFps(int fps) {
        this.maxFps = Math.max(0, Math.min(300, fps));
    }
    
    public void setMeshesPerFrameBudget(int budget) {
        this.meshesPerFrameBudget = Math.max(1, Math.min(10, budget));
    }
    
    // Méthodes pour changer les valeurs par incrément
    public void adjustRenderDistance(int delta) {
        setRenderDistance(renderDistance + delta);
    }
    
    public void adjustLodDistance(float delta) {
        setLodDistance(lodDistance + delta);
    }
    
    public void adjustMeshBudget(int delta) {
        setMeshesPerFrameBudget(meshesPerFrameBudget + delta);
    }
    
    // Cycle à travers les niveaux d'antialiasing
    public void cycleAntialiasingLevel() {
        switch (antialiasingLevel) {
            case 2 -> antialiasingLevel = 4;
            case 4 -> antialiasingLevel = 8;
            case 8 -> antialiasingLevel = 16;
            case 16 -> antialiasingLevel = 2;
            default -> antialiasingLevel = 2;
        }
    }
    
    public void toggleAntialiasing() {
        antialiasing = !antialiasing;
    }
    
    public void toggleLod() {
        enableLod = !enableLod;
    }
    
    public void toggleVsync() {
        vsync = !vsync;
    }
    
    public void toggleChunkBorders() {
        showChunkBorders = !showChunkBorders;
    }
    
    // TODO: Méthodes de sauvegarde/chargement des options
    // public void saveToFile(String filename) { }
    // public void loadFromFile(String filename) { }
    
    @Override
    public String toString() {
        return String.format("RenderOptions{distance=%d, AA=%s(%dx), LOD=%.1f, vsync=%s, borders=%s}", 
            renderDistance, antialiasing, antialiasingLevel, lodDistance, vsync, showChunkBorders);
    }
}