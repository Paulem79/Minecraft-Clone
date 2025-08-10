package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.render.texture.OverlayTexture;
import ovh.paulem.mc.engine.render.texture.Texture;
import ovh.paulem.mc.engine.render.texture.TintType;

import java.util.Map;

public class GrassBlock extends TintTopFaceBlock {
    public GrassBlock(String name, int id, TintType tintType) {
        super(name, id, tintType);
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
        super.serveTextures(textureCache);

        OverlayTexture grassSideWithOverlay = new OverlayTexture(this, "/textures/" + name + "_side.png",
                "/textures/" + name + "_side_overlay.png");
        grassSideWithOverlay.serve(textureCache);
    }
}
