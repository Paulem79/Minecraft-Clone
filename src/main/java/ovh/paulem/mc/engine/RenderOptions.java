package ovh.paulem.mc.engine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
    
    // Système d'événements
    private final List<OptionsChangeListener> listeners = new ArrayList<>();
    
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
        int oldValue = this.renderDistance;
        this.renderDistance = Math.max(2, Math.min(32, distance));
        if (oldValue != this.renderDistance) {
            notifyRenderDistanceChanged(oldValue, this.renderDistance);
        }
    }
    
    public void setAntialiasing(boolean enabled) {
        boolean oldValue = this.antialiasing;
        this.antialiasing = enabled;
        if (oldValue != this.antialiasing) {
            notifyAntialiasingChanged(this.antialiasing, this.antialiasingLevel);
        }
    }
    
    public void setAntialiasingLevel(int level) {
        int oldLevel = this.antialiasingLevel;
        // Niveaux valides: 2, 4, 8, 16
        if (level == 2 || level == 4 || level == 8 || level == 16) {
            this.antialiasingLevel = level;
            if (oldLevel != this.antialiasingLevel) {
                notifyAntialiasingChanged(this.antialiasing, this.antialiasingLevel);
            }
        }
    }
    
    public void setLodDistance(float distance) {
        float oldValue = this.lodDistance;
        this.lodDistance = Math.max(20.0f, Math.min(500.0f, distance));
        if (Math.abs(oldValue - this.lodDistance) > 0.01f) {
            notifyLodDistanceChanged(oldValue, this.lodDistance);
        }
    }
    
    public void setLodEnabled(boolean enabled) {
        boolean oldValue = this.enableLod;
        this.enableLod = enabled;
        if (oldValue != this.enableLod) {
            notifyLodEnabledChanged(this.enableLod);
        }
    }
    
    public void setVsync(boolean enabled) {
        boolean oldValue = this.vsync;
        this.vsync = enabled;
        if (oldValue != this.vsync) {
            notifyVsyncChanged(this.vsync);
        }
    }
    
    public void setShowChunkBorders(boolean show) {
        this.showChunkBorders = show;
    }
    
    public void setMaxFps(int fps) {
        this.maxFps = Math.max(0, Math.min(300, fps));
    }
    
    public void setMeshesPerFrameBudget(int budget) {
        int oldValue = this.meshesPerFrameBudget;
        this.meshesPerFrameBudget = Math.max(1, Math.min(10, budget));
        if (oldValue != this.meshesPerFrameBudget) {
            notifyMeshBudgetChanged(oldValue, this.meshesPerFrameBudget);
        }
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
    
    // Méthodes de sauvegarde/chargement des options
    public void saveToFile(String filename) {
        try {
            Properties props = new Properties();
            props.setProperty("renderDistance", String.valueOf(renderDistance));
            props.setProperty("antialiasing", String.valueOf(antialiasing));
            props.setProperty("antialiasingLevel", String.valueOf(antialiasingLevel));
            props.setProperty("lodDistance", String.valueOf(lodDistance));
            props.setProperty("enableLod", String.valueOf(enableLod));
            props.setProperty("vsync", String.valueOf(vsync));
            props.setProperty("showChunkBorders", String.valueOf(showChunkBorders));
            props.setProperty("maxFps", String.valueOf(maxFps));
            props.setProperty("meshesPerFrameBudget", String.valueOf(meshesPerFrameBudget));
            
            Path configPath = Paths.get(filename);
            Files.createDirectories(configPath.getParent());
            
            try (FileOutputStream fos = new FileOutputStream(configPath.toFile())) {
                props.store(fos, "Minecraft Clone Render Options");
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la sauvegarde des options: " + e.getMessage());
        }
    }
    
    public void loadFromFile(String filename) {
        try {
            Path configPath = Paths.get(filename);
            if (!Files.exists(configPath)) {
                return; // Fichier n'existe pas, utiliser les valeurs par défaut
            }
            
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                props.load(fis);
            }
            
            // Charger avec validation
            if (props.containsKey("renderDistance")) {
                setRenderDistance(Integer.parseInt(props.getProperty("renderDistance")));
            }
            if (props.containsKey("antialiasing")) {
                setAntialiasing(Boolean.parseBoolean(props.getProperty("antialiasing")));
            }
            if (props.containsKey("antialiasingLevel")) {
                setAntialiasingLevel(Integer.parseInt(props.getProperty("antialiasingLevel")));
            }
            if (props.containsKey("lodDistance")) {
                setLodDistance(Float.parseFloat(props.getProperty("lodDistance")));
            }
            if (props.containsKey("enableLod")) {
                setLodEnabled(Boolean.parseBoolean(props.getProperty("enableLod")));
            }
            if (props.containsKey("vsync")) {
                setVsync(Boolean.parseBoolean(props.getProperty("vsync")));
            }
            if (props.containsKey("showChunkBorders")) {
                setShowChunkBorders(Boolean.parseBoolean(props.getProperty("showChunkBorders")));
            }
            if (props.containsKey("maxFps")) {
                setMaxFps(Integer.parseInt(props.getProperty("maxFps")));
            }
            if (props.containsKey("meshesPerFrameBudget")) {
                setMeshesPerFrameBudget(Integer.parseInt(props.getProperty("meshesPerFrameBudget")));
            }
            
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erreur lors du chargement des options: " + e.getMessage());
        }
    }
    
    // Validation plus stricte des paramètres
    public boolean isValidConfiguration() {
        return renderDistance >= 2 && renderDistance <= 32 &&
               (antialiasingLevel == 2 || antialiasingLevel == 4 || antialiasingLevel == 8 || antialiasingLevel == 16) &&
               lodDistance >= 20.0f && lodDistance <= 500.0f &&
               maxFps >= 0 && maxFps <= 300 &&
               meshesPerFrameBudget >= 1 && meshesPerFrameBudget <= 10;
    }
    
    // Système d'événements pour les changements d'options
    public interface OptionsChangeListener {
        default void onRenderDistanceChanged(int oldValue, int newValue) {}
        default void onAntialiasingChanged(boolean enabled, int level) {}
        default void onLodDistanceChanged(float oldValue, float newValue) {}
        default void onLodEnabledChanged(boolean enabled) {}
        default void onVsyncChanged(boolean enabled) {}
        default void onMeshBudgetChanged(int oldValue, int newValue) {}
    }
    
    public void addChangeListener(OptionsChangeListener listener) {
        listeners.add(listener);
    }
    
    public void removeChangeListener(OptionsChangeListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyRenderDistanceChanged(int oldValue, int newValue) {
        for (OptionsChangeListener listener : listeners) {
            listener.onRenderDistanceChanged(oldValue, newValue);
        }
    }
    
    private void notifyAntialiasingChanged(boolean enabled, int level) {
        for (OptionsChangeListener listener : listeners) {
            listener.onAntialiasingChanged(enabled, level);
        }
    }
    
    private void notifyLodDistanceChanged(float oldValue, float newValue) {
        for (OptionsChangeListener listener : listeners) {
            listener.onLodDistanceChanged(oldValue, newValue);
        }
    }
    
    private void notifyLodEnabledChanged(boolean enabled) {
        for (OptionsChangeListener listener : listeners) {
            listener.onLodEnabledChanged(enabled);
        }
    }
    
    private void notifyVsyncChanged(boolean enabled) {
        for (OptionsChangeListener listener : listeners) {
            listener.onVsyncChanged(enabled);
        }
    }
    
    private void notifyMeshBudgetChanged(int oldValue, int newValue) {
        for (OptionsChangeListener listener : listeners) {
            listener.onMeshBudgetChanged(oldValue, newValue);
        }
    }
    
    @Override
    public String toString() {
        return String.format("RenderOptions{distance=%d, AA=%s(%dx), LOD=%.1f, vsync=%s, borders=%s}", 
            renderDistance, antialiasing, antialiasingLevel, lodDistance, vsync, showChunkBorders);
    }
}