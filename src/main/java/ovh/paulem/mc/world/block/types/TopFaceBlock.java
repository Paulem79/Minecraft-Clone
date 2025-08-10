package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.world.block.Blocks;
import ovh.paulem.mc.world.block.Face;

import java.util.*;

public class TopFaceBlock extends Block {
    public TopFaceBlock(String name, int id) {
        super(name, id);
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
    public String getFaceTextureName(Face face) {
        // POS_Y is the top (+Y)
        if (face == Face.POS_Y) {
            return "/textures/" + name + "_top.png";
        } else if (face == Face.NEG_Y) {
            return "/textures/" + Blocks.DIRT.getName() + ".png";
        }
        return "/textures/" + name + "_side.png";
    }

    @Override
    public void serveTextures(Map<String, Texture> textureCache) {
        new Texture(this, getFaceTextureName(Face.POS_Y)).serve(textureCache);
        new Texture(this, getFaceTextureName(Face.NEG_Y)).serve(textureCache);
        new Texture(this, getFaceTextureName(Face.POS_X)).serve(textureCache);
    }
}
