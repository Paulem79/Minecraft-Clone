package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.renderer.texture.OverlayTexture;
import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.engine.renderer.texture.TintTexture;
import ovh.paulem.mc.world.block.Face;

import java.util.List;
import java.util.Map;

public class GrassBlock extends TintTopFaceBlock {
    public GrassBlock(String name, int id, TintTexture.TintType tintType) {
        super(name, id, tintType);
    }

    @Override
    public Map<Face, List<Texture>> getTextures() {
        Map<Face, List<Texture>> textures = super.getTextures();

        for (Face face : Face.values()) {
            if (face == Face.POS_Y || face == Face.NEG_Y) continue;

            List<Texture> sideTextures = textures.get(face);
            sideTextures.add(new OverlayTexture(this, "/textures/" + name + "_side.png",
                    "/textures/" + name + "_side_overlay.png"));
            textures.put(face, sideTextures);
        }

        return textures;
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
        super.serveTextures(textureCache);

        OverlayTexture grassSideWithOverlay = new OverlayTexture(this, "/textures/" + name + "_side.png",
                "/textures/" + name + "_side_overlay.png");
        grassSideWithOverlay.serve(textureCache);
    }
}
