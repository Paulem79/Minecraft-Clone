package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.render.texture.Texture;
import ovh.paulem.mc.engine.render.texture.TintTexture;
import ovh.paulem.mc.engine.render.texture.TintType;

import java.util.Map;

public class TintTopFaceBlock extends TopFaceBlock implements Tintable {
    protected final TintType tintType;

    public TintTopFaceBlock(String name, int id, TintType tintType) {
        super(name, id);
        this.tintType = tintType;
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
        super.serveTextures(textureCache);

        TintTexture texture = new TintTexture(this, "/textures/" + name + "_top.png", tintType);
        texture.serve(textureCache);
    }

    @Override
    public TintType getTintType() {
        return tintType;
    }
}
