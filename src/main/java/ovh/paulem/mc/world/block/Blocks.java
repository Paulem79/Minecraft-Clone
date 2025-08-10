package ovh.paulem.mc.world.block;

import ovh.paulem.mc.engine.render.texture.TintType;
import ovh.paulem.mc.world.block.types.*;

import java.util.HashMap;
import java.util.Map;

public class Blocks {
    public static final Map<Integer, Block> blocks = new HashMap<>();

    public static final AirBlock AIR = register(new AirBlock("air", 0));
    public static final SingleFaceBlock STONE = register(new SingleFaceBlock("stone", 1));
    public static final SingleFaceBlock DIRT = register(new SingleFaceBlock("dirt", 2));
    public static final TintTopFaceBlock GRASS_BLOCK = register(new GrassBlock("grass_block", 3, TintType.GRASS));
    public static final SingleFaceBlock LOG = register(new SingleFaceBlock("log", 4));
    public static final FoliageBlock LEAVES = register(new FoliageBlock("leaves", 5, true, TintType.FOLIAGE));

    public static<T extends Block> T register(T block) {
        if(blocks.containsKey(block.getId())) {
            throw new IllegalArgumentException("Block with id " + block.getId() + " is already registered!");
        }

        blocks.put(block.getId(), block);
        return block;
    }
}