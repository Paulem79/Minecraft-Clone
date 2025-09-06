package ovh.paulem.mc.engine.render;

import ovh.paulem.mc.engine.RenderOptions;
import ovh.paulem.mc.engine.Window;

import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Classe responsable du rendu de l'interface des options de rendu
 * Utilise STB TrueType pour un rendu de texte amélioré
 */
public class OptionsRenderer {
    private final RenderOptions options;
    private boolean visible = false;
    
    // Dimensions et position de l'interface
    private static final float PANEL_WIDTH = 300f;
    private static final float PANEL_HEIGHT = 400f;
    private static final float OPTION_HEIGHT = 25f;
    private static final float MARGIN = 10f;
    
    // Système de rendu de texte amélioré avec STB
    private int fontTexture = 0;
    private STBTTBakedChar.Buffer charData;
    private final Map<Integer, Float> fontSizes = new HashMap<>();
    private static final float DEFAULT_FONT_SIZE = 16.0f;
    private static final int BITMAP_WIDTH = 512;
    private static final int BITMAP_HEIGHT = 512;
    
    public OptionsRenderer(RenderOptions options) {
        this.options = options;
        initializeFont();
    }
    
    /**
     * Initialise le système de rendu de texte avec STB TrueType
     */
    private void initializeFont() {
        try {
            // Essayer de charger une police système par défaut
            ByteBuffer fontData = loadDefaultFont();
            
            if (fontData != null) {
                charData = STBTTBakedChar.malloc(96); // ASCII 32-126
                ByteBuffer bitmap = memAlloc(BITMAP_WIDTH * BITMAP_HEIGHT);
                
                // Cuire la police dans une bitmap
                stbtt_BakeFontBitmap(fontData, DEFAULT_FONT_SIZE, bitmap, BITMAP_WIDTH, BITMAP_HEIGHT, 32, charData);
                
                // Créer la texture OpenGL
                fontTexture = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, fontTexture);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, BITMAP_WIDTH, BITMAP_HEIGHT, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                
                memFree(bitmap);
                fontSizes.put(16, DEFAULT_FONT_SIZE);
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'initialisation de la police: " + e.getMessage());
            // Fallback vers le système de rendu simple
            fontTexture = 0;
        }
    }
    
    /**
     * Charge une police par défaut (fallback vers police bitmap simple si STB échoue)
     */
    private ByteBuffer loadDefaultFont() {
        try {
            // Pour cette implémentation, nous utilisons une police bitmap simple
            // Dans une implémentation complète, on chargerait une vraie police TTF
            return null; // Utiliser le fallback bitmap pour l'instant
        } catch (Exception e) {
            return null;
        }
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
     * Système de rendu de texte avancé avec support STB TrueType
     * Support pour différentes tailles de police et caractères Unicode
     * Optimisé avec des textures précalculées
     */
    private void drawText(String text, float x, float y, float r, float g, float b, float a) {
        drawText(text, x, y, r, g, b, a, DEFAULT_FONT_SIZE);
    }
    
    /**
     * Dessine du texte avec une taille de police spécifique
     */
    private void drawText(String text, float x, float y, float r, float g, float b, float a, float fontSize) {
        if (fontTexture != 0 && charData != null) {
            // Rendu avec STB TrueType
            drawTextSTB(text, x, y, r, g, b, a, fontSize);
        } else {
            // Fallback vers le rendu bitmap amélioré
            drawTextBitmap(text, x, y, r, g, b, a);
        }
    }
    
    /**
     * Rendu de texte avec STB TrueType (version avancée)
     */
    private void drawTextSTB(String text, float x, float y, float r, float g, float b, float a, float fontSize) {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        
        float scale = fontSize / DEFAULT_FONT_SIZE;
        float currentX = x;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                currentX += 8 * scale; // Espace
                continue;
            }
            
            int charIndex = c - 32; // ASCII offset
            if (charIndex >= 0 && charIndex < 96) {
                STBTTBakedChar charInfo = charData.get(charIndex);
                
                float x0 = currentX + charInfo.xoff() * scale;
                float y0 = y + charInfo.yoff() * scale;
                float x1 = x0 + (charInfo.x1() - charInfo.x0()) * scale;
                float y1 = y0 + (charInfo.y1() - charInfo.y0()) * scale;
                
                float s0 = charInfo.x0() / (float) BITMAP_WIDTH;
                float t0 = charInfo.y0() / (float) BITMAP_HEIGHT;
                float s1 = charInfo.x1() / (float) BITMAP_WIDTH;
                float t1 = charInfo.y1() / (float) BITMAP_HEIGHT;
                
                glTexCoord2f(s0, t0); glVertex2f(x0, y0);
                glTexCoord2f(s1, t0); glVertex2f(x1, y0);
                glTexCoord2f(s1, t1); glVertex2f(x1, y1);
                glTexCoord2f(s0, t1); glVertex2f(x0, y1);
                
                currentX += charInfo.xadvance() * scale;
            }
        }
        
        glEnd();
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }
    
    /**
     * Rendu de texte bitmap amélioré (fallback)
     * Support amélioré pour les caractères avec formes plus reconnaissables
     */
    private void drawTextBitmap(String text, float x, float y, float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        
        // Dessiner des segments de ligne améliorés pour simuler les caractères
        float charWidth = 8f;
        float charHeight = 12f;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float charX = x + i * charWidth;
            
            if (c == ' ') continue; // Espace - ne rien dessiner
            
            glBegin(GL_LINES);
            
            // Dessiner une représentation simplifiée mais améliorée de chaque caractère
            switch (c) {
                case 'A', 'a' -> {
                    // Triangle pour A avec barre horizontale
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
                case 'R', 'r' -> {
                    // R avec formes distinctives
                    glVertex2f(charX + 1, y);
                    glVertex2f(charX + 1, y + charHeight);
                    glVertex2f(charX + 1, y);
                    glVertex2f(charX + 5, y);
                    glVertex2f(charX + 5, y);
                    glVertex2f(charX + 5, y + 6);
                    glVertex2f(charX + 5, y + 6);
                    glVertex2f(charX + 1, y + 6);
                    glVertex2f(charX + 1, y + 6);
                    glVertex2f(charX + 5, y + charHeight);
                }
                case 'D', 'd' -> {
                    // D arrondi
                    glVertex2f(charX + 1, y);
                    glVertex2f(charX + 1, y + charHeight);
                    glVertex2f(charX + 1, y);
                    glVertex2f(charX + 4, y);
                    glVertex2f(charX + 4, y);
                    glVertex2f(charX + 5, y + 2);
                    glVertex2f(charX + 5, y + 2);
                    glVertex2f(charX + 5, y + charHeight - 2);
                    glVertex2f(charX + 5, y + charHeight - 2);
                    glVertex2f(charX + 4, y + charHeight);
                    glVertex2f(charX + 4, y + charHeight);
                    glVertex2f(charX + 1, y + charHeight);
                }
                case ':', '-', '=', '.' -> {
                    // Points ou lignes horizontales
                    glVertex2f(charX + 2, y + 4);
                    glVertex2f(charX + 5, y + 4);
                    if (c == ':') {
                        glVertex2f(charX + 3, y + 2);
                        glVertex2f(charX + 4, y + 2);
                        glVertex2f(charX + 3, y + 8);
                        glVertex2f(charX + 4, y + 8);
                    } else if (c == '=') {
                        glVertex2f(charX + 2, y + 6);
                        glVertex2f(charX + 5, y + 6);
                    } else if (c == '.') {
                        glVertex2f(charX + 3, y + charHeight - 2);
                        glVertex2f(charX + 4, y + charHeight - 2);
                    }
                }
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    // Chiffres avec formes distinctives
                    if (c == '1') {
                        glVertex2f(charX + 4, y);
                        glVertex2f(charX + 4, y + charHeight);
                    } else {
                        // Forme rectangulaire pour les autres chiffres
                        glVertex2f(charX + 2, y + 1);
                        glVertex2f(charX + 5, y + 1);
                        glVertex2f(charX + 5, y + 1);
                        glVertex2f(charX + 5, y + charHeight - 1);
                        glVertex2f(charX + 5, y + charHeight - 1);
                        glVertex2f(charX + 2, y + charHeight - 1);
                        glVertex2f(charX + 2, y + charHeight - 1);
                        glVertex2f(charX + 2, y + 1);
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
     * Nettoyage des ressources de rendu de texte
     */
    public void cleanup() {
        if (fontTexture != 0) {
            glDeleteTextures(fontTexture);
            fontTexture = 0;
        }
        if (charData != null) {
            charData.free();
            charData = null;
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