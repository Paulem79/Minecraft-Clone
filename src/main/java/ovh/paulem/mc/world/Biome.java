package ovh.paulem.mc.world;

/**
 * Simple biome definition. Defines colors for grass and foliage (leaves).
 */
public enum Biome {
    NORMAL(145, 189, 89, 108, 151, 47); // Minecraft plain biome

    private final float grassR;
    private final float grassG;
    private final float grassB;

    private final float foliageR;
    private final float foliageG;
    private final float foliageB;

    Biome(int grassR, int grassG, int grassB, int foliageR, int foliageG, int foliageB) {
        this.grassR = byteToFloat((short)grassR);
        this.grassG = byteToFloat((short)grassG);
        this.grassB = byteToFloat((short)grassB);
        this.foliageR = byteToFloat((short)foliageR);
        this.foliageG = byteToFloat((short)foliageG);
        this.foliageB = byteToFloat((short)foliageB);
    }

    public float grassR() { return grassR; }
    public float grassG() { return grassG; }
    public float grassB() { return grassB; }

    public float foliageR() { return foliageR; }
    public float foliageG() { return foliageG; }
    public float foliageB() { return foliageB; }

    public static float byteToFloat(short bytes) {
        return ((bytes & 0xFF) / 255.0f);
    }
}