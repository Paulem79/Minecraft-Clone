package ovh.paulem.mc.engine.renderer;

import org.joml.Vector2f;
import org.joml.Vector3f;
import ovh.paulem.mc.engine.Hotbar;
import ovh.paulem.mc.engine.Window;
import ovh.paulem.mc.engine.renderer.texture.OverlayTexture;
import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.engine.renderer.texture.TintTexture;
import ovh.paulem.mc.world.Biome;
import ovh.paulem.mc.world.block.Blocks;
import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.Face;
import ovh.paulem.mc.world.block.types.Tintable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;

/**
 * Classe responsable du rendu de la barre de sélection des blocs (Hotbar)
 */
public record HotbarRenderer(Hotbar hotbar, Shader shader) {
    private static final float HOTBAR_RATIO = 0.8f/0.25f;
    private static final float HOTBAR_HEIGHT = 0.2f;  // Hauteur de la barre en % de l'écran
    private static final float HOTBAR_WIDTH = HOTBAR_RATIO*HOTBAR_HEIGHT;
    private static final float SLOT_PADDING = 0.005f;  // Espace entre les slots en % de l'écran

    private static final Map<String, Texture> textureCache = new HashMap<>();

    /**
     * Dessine la barre de sélection à l'écran
     */
    public void render(Window window) {
        Blocks.blocks.forEach((integer, block) -> block.serveTextures(textureCache));

        // Sauvegarde complète de l'état d'OpenGL
        glPushAttrib(GL_ALL_ATTRIB_BITS);

        // Sauvegarde des matrices
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        // Configuration pour le rendu 2D de la hotbar
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Nombre de slots dans la hotbar
        int slotCount = Hotbar.BLOCKS.size();
        float slotWidth = HOTBAR_WIDTH / slotCount;

        // Position de la hotbar en bas de l'écran avec des coordonnées normalisées OpenGL (-1 à 1)
        float startX = -HOTBAR_WIDTH / 2.0f;
        float startY = -0.95f;

        // Dessiner le fond de la hotbar
        glBegin(GL_QUADS);
        glColor4f(0.0f, 0.0f, 0.0f, 0.5f); // Fond semi-transparent noir
        glVertex2f(startX - 0.01f, startY - 0.01f);
        glVertex2f(startX + HOTBAR_WIDTH + 0.01f, startY - 0.01f);
        glVertex2f(startX + HOTBAR_WIDTH + 0.01f, startY + HOTBAR_HEIGHT + 0.01f);
        glVertex2f(startX - 0.01f, startY + HOTBAR_HEIGHT + 0.01f);
        glEnd();

        // Dessiner chaque emplacement
        for (int i = 0; i < slotCount; i++) {
            float x = startX + i * slotWidth;

            // Dessiner la bordure du slot (plus claire pour le slot sélectionné)
            if (i == hotbar.getSelectedSlotIndex()) {
                glBegin(GL_QUADS);
                glColor4f(1.0f, 1.0f, 1.0f, 0.8f); // Blanc pour le slot sélectionné
                glVertex2f(x, startY);
                glVertex2f(x + slotWidth, startY);
                glVertex2f(x + slotWidth, startY + HOTBAR_HEIGHT);
                glVertex2f(x, startY + HOTBAR_HEIGHT);
                glEnd();

                // Intérieur du slot
                glBegin(GL_QUADS);
                glColor4f(0.3f, 0.3f, 0.3f, 0.7f);
            } else {
                // Bordure des slots non sélectionnés
                glBegin(GL_QUADS);
                glColor4f(0.5f, 0.5f, 0.5f, 0.6f); // Gris pour les slots non sélectionnés
                glVertex2f(x, startY);
                glVertex2f(x + slotWidth, startY);
                glVertex2f(x + slotWidth, startY + HOTBAR_HEIGHT);
                glVertex2f(x, startY + HOTBAR_HEIGHT);
                glEnd();

                // Intérieur du slot
                glBegin(GL_QUADS);
                glColor4f(0.2f, 0.2f, 0.2f, 0.6f);
            }
            glVertex2f(x + SLOT_PADDING, startY + SLOT_PADDING);
            glVertex2f(x + slotWidth - SLOT_PADDING, startY + SLOT_PADDING);
            glVertex2f(x + slotWidth - SLOT_PADDING, startY + HOTBAR_HEIGHT - SLOT_PADDING);
            glVertex2f(x + SLOT_PADDING, startY + HOTBAR_HEIGHT - SLOT_PADDING);
            glEnd();

            // Dessiner le bloc dans l'emplacement s'il y en a un
            Block block = hotbar.getBlockAt(i);
            if (block != null) { // Ne pas afficher l'air
                // Mesures pour le positionnement du rendu 3D
                float slotCenterX = x + slotWidth / 2.0f;
                float slotCenterY = startY + HOTBAR_HEIGHT / 2.0f;

                // Convertir les coordonnées normalisées en coordonnées écran
                float screenX = (slotCenterX + 1.0f) * window.getWidth() / 2.0f;
                float screenY = (slotCenterY + 1.0f) * window.getHeight() / 2.0f;

                // Taille du bloc en pixels (calculée par rapport à la taille du slot)
                float blockSizePixels = Math.min(slotWidth, HOTBAR_HEIGHT) * Math.min(window.getWidth(), window.getHeight()) * 0.7f;

                // Rendre le bloc en 3D au centre du slot
                render3DBlock(window, block, screenX, screenY, blockSizePixels);
            }
        }

        renderCrosshair(window);
    }

    private void renderCrosshair(Window window) {
        // Dessiner un réticule au centre de l'écran
        float reticuleSize = 0.04f;
        float ratio = (float) window.getWidth() / window.getHeight();

        glBegin(GL_LINES);
        glColor4f(1.0f, 1.0f, 1.0f, 0.8f);
        // Ligne horizontale (ajustée avec le ratio)
        glVertex2f(-reticuleSize / ratio, 0.0f);
        glVertex2f(reticuleSize / ratio, 0.0f);
        // Ligne verticale
        glVertex2f(0.0f, -reticuleSize);
        glVertex2f(0.0f, reticuleSize);
        glEnd();

        // Restaurer l'état d'OpenGL
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glPopAttrib();
    }

    /**
     * Rend un bloc en 3D à une position spécifique de l'écran
     */
    private void render3DBlock(Window window, Block block, float screenX, float screenY, float blockSizePixels) {
        // Sauvegarde des matrices et des états
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();

        // Définir une vue orthographique pour dessiner le bloc en 3D dans un espace 2D
        // Le repère est centré sur le bloc, avec des coordonnées en pixels
        int halfWidth = window.getWidth() / 2;
        int halfHeight = window.getHeight() / 2;
        glOrtho(
            -halfWidth, halfWidth,
            -halfHeight, halfHeight,
            -1000, 1000
        );


        // Positionner le bloc (conversion des coordonnées d'écran en coordonnées de la vue orthographique)
        glTranslatef(screenX - halfWidth, screenY - halfHeight, 0);

        // Taille et rotation du bloc
        float scale = blockSizePixels / 2.5f;
        glScalef(scale, scale, scale);

        // Rotation pour une vue isométrique typique de bloc
        glRotatef(30.0f, 1.0f, 0.0f, 0.0f);
        glRotatef(30f, 0.0f, 1.0f, 0.0f);

        // Dessiner le bloc 3D
        renderBlockCube(block);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

    }

    /**
     * Dessine un bloc 3D avec les textures appropriées pour chaque face
     */
    private void renderBlockCube(Block block) {
        // Définir les couleurs du biome normal
        Biome biome = Biome.NORMAL;
        float[] grassColor = {biome.grassR(), biome.grassG(), biome.grassB()};
        float[] foliageColor = {biome.foliageR(), biome.foliageG(), biome.foliageB()};


        for (Face face : Face.values()) {
            List<Texture> textures = block.getTextures().get(face);

            for (Texture texture : textures) {
                if (texture != null) {
                    doForTexture(face, texture, grassColor, foliageColor);
                }
            }
        }

    }

    private void doForTexture(Face face, Texture texture, float[] grassColor, float[] foliageColor) {
        // Configurer le rendu 3D
        glEnable(GL_DEPTH_TEST);
        glClear(GL_DEPTH_BUFFER_BIT); // Effacer seulement le buffer de profondeur

        glEnable(GL_TEXTURE_2D);
        texture.bind(0);
        glBegin(GL_QUADS);
        applyBiomeColor(texture, grassColor, foliageColor);
        for (Map.Entry<Vector2f, Vector3f> entry : face.getFaceNormals().entrySet()) {
            Vector2f uv = entry.getKey();
            Vector3f normal = entry.getValue();

            glTexCoord2f(uv.x, uv.y); glVertex3f(normal.x, normal.y, normal.z);
        }
        glEnd();
        glDisable(GL_TEXTURE_2D);

        // Restaurer l'état OpenGL
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
    }

    /**
     * Applique les couleurs du biome en fonction du type de bloc
     */
    private void applyBiomeColor(Texture texture, float[] grassColor, float[] foliageColor) {
        // Par défaut, couleur blanche (pas de modification)
        float r = 1.0f, g = 1.0f, b = 1.0f;

        // Détermine si la texture doit utiliser la couleur du biome
        if (texture.getBaseBlock() instanceof Tintable tintable) {
            if(tintable.getTintType() == TintTexture.TintType.GRASS) {
                System.out.println(texture);
            }
            if (tintable.getTintType() == TintTexture.TintType.GRASS && (texture.getResourcePath().contains("top") || texture.getResourcePath().contains("overlay"))) {
                // Appliquer la couleur de l'herbe
                r = grassColor[0];
                g = grassColor[1];
                b = grassColor[2];
            } else if (tintable.getTintType() == TintTexture.TintType.FOLIAGE && texture instanceof TintTexture) {
                // Appliquer la couleur du feuillage
                r = foliageColor[0];
                g = foliageColor[1];
                b = foliageColor[2];
            }
        }

        glColor3f(r, g, b);
    }
}
