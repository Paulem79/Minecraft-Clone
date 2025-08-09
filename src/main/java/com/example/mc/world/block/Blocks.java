package com.example.mc.world.block;

import java.util.HashMap;
import java.util.Map;

public class Blocks {
    public static final Map<Integer, Block> blocks = new HashMap<>();

    public static final SingleFaceBlock STONE = register(new SingleFaceBlock("stone", -1));
    public static final SingleFaceBlock AIR = register(new SingleFaceBlock("air", 0));
    public static final SingleFaceBlock DIRT = register(new SingleFaceBlock("dirt", 1));
    public static final TopFaceBlock GRASS_BLOCK = register(new TopFaceBlock("grass_block", 2));

    public static<T extends Block> T register(T block) {
        blocks.put(block.getId(), block);
        return block;
    }
}