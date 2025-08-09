package com.example.mc.world;

public class Block {
    public static final int AIR = 0;
    public static final int DIRT = 1;
    public static final int GRASS = 2;

    private final int id;

    public Block(int id) { this.id = id; }
    public int getId() { return id; }
    public boolean isOpaque() { return id != AIR; }
}