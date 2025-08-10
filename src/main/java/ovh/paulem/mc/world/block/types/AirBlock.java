package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.world.block.Face;

import java.util.Map;

public class AirBlock extends Block {
    public AirBlock(String name, int id) {
        super(name, id);
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
    }
}
