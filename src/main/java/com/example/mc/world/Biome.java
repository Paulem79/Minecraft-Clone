package com.example.mc.world;

/**
 * Simple biome definition. For now we only support a single biome named "normal"
 * with a specific grass color used to tint grass top and side overlay.
 */
public enum Biome {
    NORMAL(0.569f, 0.741f, 0.345f); // approx Minecraft plain grass color (#91BD59)

    private final float grassR;
    private final float grassG;
    private final float grassB;

    Biome(float r, float g, float b) {
        this.grassR = r;
        this.grassG = g;
        this.grassB = b;
    }

    public float grassR() { return grassR; }
    public float grassG() { return grassG; }
    public float grassB() { return grassB; }
}