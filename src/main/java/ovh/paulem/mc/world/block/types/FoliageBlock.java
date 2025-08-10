package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.render.texture.Texture;
import ovh.paulem.mc.engine.render.texture.TintTexture;
import ovh.paulem.mc.engine.render.texture.TintType;
import ovh.paulem.mc.world.block.Face;

import java.util.Map;

public class FoliageBlock extends SingleFaceBlock implements Tintable {
    protected final TintType tintType;

    public FoliageBlock(String name, int id, TintType tintType) {
        super(name, id);
        this.tintType = tintType;
    }

    public FoliageBlock(String name, int id, boolean transparent, TintType tintType) {
        super(name, id, transparent);
        this.tintType = tintType;
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
        TintTexture texture = new TintTexture(this, getFaceTextureName(Face.POS_X), tintType);
        texture.serve(textureCache);
    }

    @Override
    public TintType getTintType() {
        return tintType;
    }
}
