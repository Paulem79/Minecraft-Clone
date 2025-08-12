package ovh.paulem.mc.world;

import org.joml.Vector3f;
import ovh.paulem.mc.engine.render.texture.TintType;

/**
 * Simple biome definition. Defines colors for grass and foliage (leaves).
 */
public enum Biome {
    NORMAL(145, 189, 89, 108, 151, 47, 1.0, 40.0, 48.0, 4, 1.0), // Minecraft plain biome
    PLAINS(255, 255, 255, 255, 255, 255, 0.5, 38.0, 24.0, 3, 1.0), // Un vert plus clair
    MOUNTAINS(255, 0, 0, 255, 255, 0, 2.2, 55.0, 80.0, 5, 0.4); // Un vert plus foncé, freq basse pour montagnes larges

    private final float grassR;
    private final float grassG;
    private final float grassB;

    private final float foliageR;
    private final float foliageG;
    private final float foliageB;

    // Paramètres de génération du terrain
    public final double relief;
    public final double baseHeight;
    public final double heightScale;
    public final double octaves;
    public final double terrainFrequency;

    Biome(int grassR, int grassG, int grassB, int foliageR, int foliageG, int foliageB,
          double relief, double baseHeight, double heightScale, double octaves, double terrainFrequency) {
        this.grassR = byteToFloat((short)grassR);
        this.grassG = byteToFloat((short)grassG);
        this.grassB = byteToFloat((short)grassB);
        this.foliageR = byteToFloat((short)foliageR);
        this.foliageG = byteToFloat((short)foliageG);
        this.foliageB = byteToFloat((short)foliageB);
        this.relief = relief;
        this.baseHeight = baseHeight;
        this.heightScale = heightScale;
        this.octaves = octaves;
        this.terrainFrequency = terrainFrequency;
    }

    public float grassR() { return grassR; }
    public float grassG() { return grassG; }
    public float grassB() { return grassB; }

    public float foliageR() { return foliageR; }
    public float foliageG() { return foliageG; }
    public float foliageB() { return foliageB; }

    public Vector3f getByTint(TintType tintType) {
        return switch (tintType) {
            case GRASS -> new Vector3f(grassR, grassG, grassB);
            case FOLIAGE -> new Vector3f(foliageR, foliageG, foliageB);
        };
    }

    public static float byteToFloat(short bytes) {
        return ((bytes & 0xFF) / 255.0f);
    }
}