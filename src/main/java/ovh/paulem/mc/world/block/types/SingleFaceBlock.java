package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.world.block.Face;

import java.util.*;

public class SingleFaceBlock extends Block {
    private final boolean transparent;

    public SingleFaceBlock(String name, int id) {
        this(name, id, false);
    }

    public SingleFaceBlock(String name, int id, boolean transparent) {
        super(name, id, transparent);
        this.transparent = transparent;
    }

    @Override
    public Map<Face, List<Texture>> getTextures() {
        Map<Face, List<Texture>> textures = new HashMap<>();

        for (Face face : Face.values()) {
            textures.put(face, new ArrayList<>(List.of(new Texture(this, getFaceTextureName(face)))));
        }

        return textures;
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
        new Texture(this, getFaceTextureName(Face.POS_X)).serve(textureCache);
    }

    @Override
    public boolean isTransparent() {
        return transparent;
    }
}
