package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.engine.renderer.texture.TintTexture;
import ovh.paulem.mc.world.block.Face;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TintTopFaceBlock extends TopFaceBlock implements Tintable {
    protected final TintTexture.TintType tintType;

    public TintTopFaceBlock(String name, int id, TintTexture.TintType tintType) {
        super(name, id);
        this.tintType = tintType;
    }

    @Override
    public Map<Face, List<Texture>> getTextures() {
        Map<Face, List<Texture>> textures = super.getTextures();

        List<Texture> topTextures = textures.get(Face.POS_Y);
        topTextures.add(new TintTexture(this, "/textures/" + name + "_top.png", tintType));
        textures.put(Face.POS_Y, topTextures);

        return textures;
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
        super.serveTextures(textureCache);

        TintTexture texture = new TintTexture(this, "/textures/" + name + "_top.png", tintType);
        texture.serve(textureCache);
    }

    @Override
    public TintTexture.TintType getTintType() {
        return tintType;
    }
}
