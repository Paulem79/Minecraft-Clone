package com.example.mc.world;

public class Chunk {
    public static final int CHUNK_X = 16;
    public static final int CHUNK_Y = 256;
    public static final int CHUNK_Z = 16;

    private final int originX;
    private final int originZ;
    private final int[][][] blocks = new int[CHUNK_X][CHUNK_Y][CHUNK_Z];

    public Chunk(int originX, int originZ) {
        this.originX = originX;
        this.originZ = originZ;
    }

    public int getOriginX() { return originX; }
    public int getOriginZ() { return originZ; }

    public int getBlock(int x, int y, int z) { return blocks[x][y][z]; }
    public void setBlock(int x, int y, int z, int id) { blocks[x][y][z] = id; }
}