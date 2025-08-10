package ovh.paulem.mc.world.block.types;

import ovh.paulem.mc.engine.renderer.texture.Texture;
import ovh.paulem.mc.engine.renderer.texture.Textures;
import ovh.paulem.mc.world.block.Face;

import java.util.Map;

import static ovh.paulem.mc.world.block.Blocks.AIR;

public abstract class Block {
    protected final String name;
    private final int id;
    private final boolean transparent;

    public Block(String name, int id) {
        this(name, id, false);
    }

    public Block(String name, int id, boolean transparent) {
        this.name = name;
        this.id = id;
        this.transparent = transparent;
    }

    public int getId() { return id; }

    public String getName() {
        return name;
    }

    public String getFaceTextureName(Face face) {
        return "/textures/" + name + ".png";
    }

    public String getFaceTextureName(int face) {
        return getFaceTextureName(Face.fromIndex(face));
    }

    public Texture getFaceTexture(int face) {
        return getFaceTexture(Face.fromIndex(face));
    }

    public Texture getFaceTexture(Face face) {
        String textureName = getFaceTextureName(face);
        Texture texture = Textures.textureCache.get(textureName);

        if(texture == null) throw new RuntimeException("Texture not found: " + textureName);

        return texture;
    }

    public abstract void serveTextures(Map<String, Texture> textureCache);

    public boolean isTransparent() {
        return transparent;
    }

    public boolean isBlock() { return this != AIR; }

    @Override
    public String toString() {
        return "Block{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", transparent=" + transparent +
                '}';
    }
}
