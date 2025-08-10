package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.engine.renderer.texture.TintTexture;
import ovh.paulem.mc.world.block.Face;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoliageBlock extends SingleFaceBlock implements Tintable {
    protected final TintTexture.TintType tintType;

    public FoliageBlock(String name, int id, TintTexture.TintType tintType) {
        super(name, id);
        this.tintType = tintType;
    }

    public FoliageBlock(String name, int id, boolean transparent, TintTexture.TintType tintType) {
        super(name, id, transparent);
        this.tintType = tintType;
    }

    @Override
    public Map<Face, List<Texture>> getTextures() {
        Map<Face, List<Texture>> textures = new HashMap<>();

        for (Face face : Face.values()) {
            textures.put(face, new ArrayList<>(List.of(new TintTexture(this, getFaceTextureName(Face.POS_X), tintType))));
        }

        return textures;
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
        TintTexture texture = new TintTexture(this, getFaceTextureName(Face.POS_X), tintType);
        texture.serve(textureCache);
    }

    @Override
    public TintTexture.TintType getTintType() {
        return tintType;
    }
}
