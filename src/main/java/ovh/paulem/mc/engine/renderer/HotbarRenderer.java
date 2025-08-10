package ovh.paulem.mc.engine.renderer;

import org.joml.Vector3f;
import ovh.paulem.mc.engine.Hotbar;
import ovh.paulem.mc.engine.Window;
import ovh.paulem.mc.engine.renderer.texture.OverlayTexture;
import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.engine.renderer.texture.TintTexture;
import ovh.paulem.mc.world.Biome;
import ovh.paulem.mc.world.block.Face;
import ovh.paulem.mc.world.block.types.Block;
import ovh.paulem.mc.world.block.types.Tintable;

import static org.lwjgl.opengl.GL11.*;

/**
 * Classe responsable du rendu de la barre de sélection des blocs (Hotbar)
 */
public record HotbarRenderer(Hotbar hotbar, Shader shader) {
    private static final float HOTBAR_RATIO = 0.83f/0.25f;
    private static final float HOTBAR_HEIGHT = 0.2f;  // Hauteur de la barre en % de l'écran
    private static final float HOTBAR_WIDTH = HOTBAR_RATIO*HOTBAR_HEIGHT;
    private static final float SLOT_PADDING = 0.005f;  // Espace entre les slots en % de l'écran

    /**
     * Dessine la barre de sélection à l'écran
     */
    public void render(Window window) {
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
            if (block != null) {
                // Rendu 2D de la face latérale du bloc dans le slot
                Texture texture = block.getFaceTexture(Face.POS_X);

                if(texture instanceof OverlayTexture overlayTexture) {
                    applyTexture(texture.asPlain(), x, startY, slotWidth, block);
                    applyTexture(overlayTexture.getOverlay(), x, startY, slotWidth, block);
                } else applyTexture(texture, x, startY, slotWidth, block);
            }
        }

        renderCrosshair(window);
    }

    private static void applyTexture(Texture texture, float x, float startY, float slotWidth, Block block) {
        if (texture != null) {
            texture.bind(0);
            float padding = 0.02f;
            float tx = x + SLOT_PADDING + padding;
            float ty = startY + SLOT_PADDING + padding;
            float tw = slotWidth - 2 * (SLOT_PADDING + padding);
            float th = HOTBAR_HEIGHT - 2 * (SLOT_PADDING + padding);

            glEnable(GL_TEXTURE_2D);
            if(block instanceof Tintable tintable && texture instanceof TintTexture) {
                Vector3f colors = Biome.NORMAL.getByTint(tintable.getTintType());
                glColor4f(colors.x, colors.y, colors.z, 1f);
            } else glColor4f(1f, 1f, 1f, 1f);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(tx, ty);
            glTexCoord2f(1, 0); glVertex2f(tx + tw, ty);
            glTexCoord2f(1, 1); glVertex2f(tx + tw, ty + th);
            glTexCoord2f(0, 1); glVertex2f(tx, ty + th);
            glEnd();
            glDisable(GL_TEXTURE_2D);
        }
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
}
