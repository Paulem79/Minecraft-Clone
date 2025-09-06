package ovh.paulem.mc.engine.render;

import ovh.paulem.mc.engine.RenderOptions;
import ovh.paulem.mc.engine.Window;

import static org.lwjgl.opengl.GL11.*;

/**
 * Classe responsable du rendu de l'interface des options de rendu
 */
public class OptionsRenderer {
    private final RenderOptions options;
    private boolean visible = false;
    
    // Dimensions et position de l'interface
    private static final float PANEL_WIDTH = 300f;
    private static final float PANEL_HEIGHT = 400f;
    private static final float OPTION_HEIGHT = 25f;
    private static final float MARGIN = 10f;
    
    public OptionsRenderer(RenderOptions options) {
        this.options = options;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void toggleVisibility() {
        visible = !visible;
    }
    
    /**
     * Dessine l'interface des options à l'écran
     */
    public void render(Window window) {
        if (!visible) return;
        
        // Sauvegarder l'état OpenGL
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glPushMatrix();
        
        // Configuration pour le rendu 2D
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Matrice de projection 2D
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, window.getWidth(), window.getHeight(), 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        // Position centrée
        float panelX = (window.getWidth() - PANEL_WIDTH) / 2f;
        float panelY = (window.getHeight() - PANEL_HEIGHT) / 2f;
        
        // Arrière-plan du panneau
        drawPanel(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        
        // Titre
        float currentY = panelY + MARGIN;
        drawText("Options de Rendu", panelX + MARGIN, currentY, 1.0f, 1.0f, 1.0f, 1.0f);
        currentY += OPTION_HEIGHT + MARGIN;
        
        // Options
        currentY = drawOption("Distance de rendu: " + options.getRenderDistance(), 
                             panelX + MARGIN, currentY);
        
        currentY = drawOption("Antialiasing: " + (options.isAntialiasingEnabled() ? 
                             options.getAntialiasingLevel() + "x" : "OFF"), 
                             panelX + MARGIN, currentY);
        
        currentY = drawOption("Distance LOD: " + String.format("%.1f", options.getLodDistance()), 
                             panelX + MARGIN, currentY);
        
        currentY = drawOption("LOD activé: " + (options.isLodEnabled() ? "OUI" : "NON"), 
                             panelX + MARGIN, currentY);
        
        currentY = drawOption("V-Sync: " + (options.isVsyncEnabled() ? "OUI" : "NON"), 
                             panelX + MARGIN, currentY);
        
        currentY = drawOption("Bordures chunks: " + (options.isShowChunkBorders() ? "OUI" : "NON"), 
                             panelX + MARGIN, currentY);
        
        currentY = drawOption("Budget meshes/frame: " + options.getMeshesPerFrameBudget(), 
                             panelX + MARGIN, currentY);
        
        // Instructions de contrôle
        currentY += MARGIN;
        drawText("Contrôles:", panelX + MARGIN, currentY, 0.8f, 0.8f, 0.8f, 1.0f);
        currentY += OPTION_HEIGHT * 0.8f;
        
        drawText("1-7: Modifier options", panelX + MARGIN, currentY, 0.6f, 0.6f, 0.6f, 1.0f);
        currentY += OPTION_HEIGHT * 0.6f;
        
        drawText("F1: Fermer ce menu", panelX + MARGIN, currentY, 0.6f, 0.6f, 0.6f, 1.0f);
        
        // Restaurer l'état OpenGL
        glPopMatrix();
        glPopAttrib();
    }
    
    private void drawPanel(float x, float y, float width, float height) {
        // Arrière-plan semi-transparent
        glBegin(GL_QUADS);
        glColor4f(0.0f, 0.0f, 0.0f, 0.7f);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        
        // Bordure
        glBegin(GL_LINE_LOOP);
        glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }
    
    private float drawOption(String text, float x, float y) {
        drawText(text, x, y, 0.9f, 0.9f, 0.9f, 1.0f);
        return y + OPTION_HEIGHT;
    }
    
    /**
     * Dessine du texte simple avec des lignes représentant les caractères
     * TODO: Remplacer par un système de rendu de texte plus avancé avec STB_truetype
     * TODO: Ajouter support pour différentes tailles de police
     * TODO: Ajouter support pour les caractères Unicode/UTF-8
     * TODO: Optimiser le rendu de texte avec des textures précalculées
     */
    private void drawText(String text, float x, float y, float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        
        // Dessiner des segments de ligne pour simuler les caractères
        float charWidth = 8f;
        float charHeight = 12f;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float charX = x + i * charWidth;
            
            if (c == ' ') continue; // Espace - ne rien dessiner
            
            glBegin(GL_LINES);
            
            // Dessiner une représentation simplifiée de chaque caractère
            switch (c) {
                case 'A', 'a' -> {
                    // Triangle pour A
                    glVertex2f(charX + 2, y + charHeight);
                    glVertex2f(charX + 4, y);
                    glVertex2f(charX + 4, y);
                    glVertex2f(charX + 6, y + charHeight);
                    glVertex2f(charX + 3, y + 6);
                    glVertex2f(charX + 5, y + 6);
                }
                case 'E', 'e' -> {
                    // E avec lignes horizontales
                    glVertex2f(charX + 1, y);
                    glVertex2f(charX + 1, y + charHeight);
                    glVertex2f(charX + 1, y);
                    glVertex2f(charX + 6, y);
                    glVertex2f(charX + 1, y + 6);
                    glVertex2f(charX + 5, y + 6);
                    glVertex2f(charX + 1, y + charHeight);
                    glVertex2f(charX + 6, y + charHeight);
                }
                case 'O', 'o' -> {
                    // Carré pour O
                    glVertex2f(charX + 1, y + 1);
                    glVertex2f(charX + 6, y + 1);
                    glVertex2f(charX + 6, y + 1);
                    glVertex2f(charX + 6, y + charHeight - 1);
                    glVertex2f(charX + 6, y + charHeight - 1);
                    glVertex2f(charX + 1, y + charHeight - 1);
                    glVertex2f(charX + 1, y + charHeight - 1);
                    glVertex2f(charX + 1, y + 1);
                }
                case ':', '-', '=' -> {
                    // Points ou lignes horizontales
                    glVertex2f(charX + 2, y + 4);
                    glVertex2f(charX + 5, y + 4);
                    if (c == ':') {
                        glVertex2f(charX + 3, y + 2);
                        glVertex2f(charX + 4, y + 2);
                        glVertex2f(charX + 3, y + 8);
                        glVertex2f(charX + 4, y + 8);
                    }
                }
                default -> {
                    // Caractère générique - ligne verticale simple
                    glVertex2f(charX + 3, y);
                    glVertex2f(charX + 3, y + charHeight);
                }
            }
            
            glEnd();
        }
    }
    
    /**
     * Gère les entrées clavier pour modifier les options
     */
    public void handleKeyInput(int key, int action) {
        if (!visible || action != 1) return; // GLFW_PRESS = 1
        
        switch (key) {
            case 49 -> options.adjustRenderDistance(1); // Touche 1
            case 50 -> options.toggleAntialiasing(); // Touche 2
            case 51 -> options.cycleAntialiasingLevel(); // Touche 3
            case 52 -> options.adjustLodDistance(10.0f); // Touche 4
            case 53 -> options.toggleLod(); // Touche 5
            case 54 -> options.toggleVsync(); // Touche 6
            case 55 -> options.adjustMeshBudget(1); // Touche 7
        }
    }
}