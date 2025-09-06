package ovh.paulem.mc.world.block.types;

import lombok.Getter;
import ovh.paulem.mc.engine.render.texture.OverlayTexture;
import ovh.paulem.mc.engine.render.texture.Texture;
import ovh.paulem.mc.engine.render.texture.TextureAtlas;
import ovh.paulem.mc.engine.render.texture.Textures;
import ovh.paulem.mc.world.block.Face;

import java.util.Map;

import static ovh.paulem.mc.world.block.Blocks.AIR;

@Getter
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

    /**
     * Get the UV region for this block's face from the texture atlas.
     * @param face The face index (0-5)
     * @param atlas The texture atlas
     * @return UV region or null if not found
     */
    public TextureAtlas.UVRegion getRegion(int face, TextureAtlas atlas) {
        String textureName = getFaceTextureName(face);
        return atlas.getRegion(textureName);
    }
    
    /**
     * Get the overlay UV region for this block's face from the texture atlas.
     * Only relevant for blocks that have overlay textures (like grass sides).
     * @param face The face index (0-5)  
     * @param atlas The texture atlas
     * @return Overlay UV region or null if not found or not applicable
     */
    public TextureAtlas.UVRegion getOverlayRegion(int face, TextureAtlas atlas) {
        // Check if this block has overlay texture for this face
        Texture texture = getFaceTexture(face);
        if (texture instanceof OverlayTexture overlayTexture) {
            String overlayPath = overlayTexture.getOverlay().getResourcePath();
            return atlas.getOverlayRegion(overlayPath);
        }
        return null;
    }

    public String[] getSounds() {
        return new String[]{"Grass_dig1", "Grass_dig2", "Grass_dig3", "Grass_dig4"};
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
