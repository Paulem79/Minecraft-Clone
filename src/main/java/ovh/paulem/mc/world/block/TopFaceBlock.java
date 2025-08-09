package ovh.paulem.mc.world.block;

public class TopFaceBlock extends Block {
    public TopFaceBlock(String name, int id) {
        super(name, id);
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
}
