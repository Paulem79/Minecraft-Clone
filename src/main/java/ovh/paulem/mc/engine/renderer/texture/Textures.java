package ovh.paulem.mc.engine.renderer.texture;

import java.util.HashMap;
import java.util.Map;

public class Textures {
    // Texture cache to avoid reloading the same texture many times
    public static final Map<String, Texture> textureCache = new HashMap<>();
}
