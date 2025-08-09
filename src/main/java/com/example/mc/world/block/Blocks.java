package com.example.mc.world.block;

import java.util.HashMap;
import java.util.Map;

public class Blocks {
    public static final Map<Integer, Block> blocks = new HashMap<>();

    public static final SingleFaceBlock STONE = register(new SingleFaceBlock("stone", -1));
    public static final SingleFaceBlock AIR = register(new SingleFaceBlock("air", 0));
    public static final SingleFaceBlock DIRT = register(new SingleFaceBlock("dirt", 1));
    public static final TopFaceBlock GRASS_BLOCK = register(new TopFaceBlock("grass_block", 2));
    public static final SingleFaceBlock LOG = register(new SingleFaceBlock("log", 3));
    public static final SingleFaceBlock LEAVES = register(new SingleFaceBlock("leaves", 4, true));

    public static<T extends Block> T register(T block) {
        if(blocks.containsKey(block.getId())) {
            throw new IllegalArgumentException("Block with id " + block.getId() + " is already registered!");
        }

        blocks.put(block.getId(), block);
        return block;
    }
}