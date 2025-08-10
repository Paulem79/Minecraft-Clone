package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.world.block.Face;

import java.util.*;

public class SingleFaceBlock extends Block {
    public SingleFaceBlock(String name, int id) {
        this(name, id, false);
    }

    public SingleFaceBlock(String name, int id, boolean transparent) {
        super(name, id, transparent);
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
        new Texture(this, getFaceTextureName(Face.POS_X)).serve(textureCache);
    }
}
