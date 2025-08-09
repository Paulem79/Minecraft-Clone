package com.example.mc.world.block;

import com.example.mc.engine.renderer.Texture;

import static com.example.mc.world.block.Blocks.AIR;

public abstract class Block {
    protected final String name;
    private final int id;

    public Block(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public int getId() { return id; }

    public String getName() {
        return name;
    }

    // Default block texture (used for all faces if not overridden)
    public Texture getTexture() {
        return new Texture("/textures/" + name + ".png");
    }

    // New: enum-based per-face texture name
    public String getFaceTextureName(Face face) {
        return "/textures/" + name + ".png";
    }

    // Backward-compatible: Per-face texture name by index; indices: 0:+X,1:-X,2:+Y(top),3:-Y(bottom),4:+Z,5:-Z
    public String getFaceTextureName(int face) {
        return getFaceTextureName(Face.fromIndex(face));
    }

    // Convenience: create a texture instance for the specified face (enum)
    public Texture getFaceTexture(Face face) {
        return new Texture(getFaceTextureName(face));
    }

    // Backward-compatible: convenience by index
    public Texture getFaceTexture(int face) {
        return getFaceTexture(Face.fromIndex(face));
    }

    public boolean isOpaque() { return id != AIR.getId(); }
}
