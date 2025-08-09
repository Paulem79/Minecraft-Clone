package ovh.paulem.mc.world.block;

public class SingleFaceBlock extends Block {
    private final boolean transparent;

    public SingleFaceBlock(String name, int id) {
        this(name, id, false);
    }

    public SingleFaceBlock(String name, int id, boolean transparent) {
        super(name, id, transparent);
        this.transparent = transparent;
    }

    @Override
    public boolean isTransparent() {
        return transparent;
    }
}
