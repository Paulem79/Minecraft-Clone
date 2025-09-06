package ovh.paulem.mc.engine.render.texture;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL46.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * TextureAtlas combines multiple block textures into a single atlas texture
 * to reduce the number of texture binds per frame and enable batching.
 * Supports separate base and overlay atlases for overlay textures.
 */
public class TextureAtlas {
    
    public static class UVRegion {
        public final float u0, v0, u1, v1;
        
        public UVRegion(float u0, float v0, float u1, float v1) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }
    
    private int baseTextureId;
    private int overlayTextureId;
    private int width, height;
    private final Map<String, UVRegion> baseUvRegions = new HashMap<>();
    private final Map<String, UVRegion> overlayUvRegions = new HashMap<>();
    
    /**
     * Builds texture atlas from all PNG files in /textures/
     * Creates separate atlases for base textures and overlay textures.
     */
    public void buildAtlas() {
        int textureSize = 16; // Assume 16x16 textures, should match actual texture size
        
        // Define base textures and their corresponding overlays
        Map<String, String> baseToOverlayMap = new HashMap<>();
        baseToOverlayMap.put("grass_block_side", "grass_block_side_overlay");
        
        String[] baseTextures = {
            "stone", "dirt", "grass_block_side", "grass_block_top", "log", "leaves"
        };
        
        // Build base atlas
        buildSingleAtlas(baseTextures, textureSize, true);
        
        // Build overlay atlas - must match the order and positioning of base atlas
        List<String> overlayTextures = new ArrayList<>();
        for (String baseTexture : baseTextures) {
            String overlayTexture = baseToOverlayMap.get(baseTexture);
            if (overlayTexture != null) {
                overlayTextures.add(overlayTexture);
            } else {
                // Add transparent/empty texture to maintain atlas alignment
                overlayTextures.add(null);
            }
        }
        
        buildSingleAtlas(overlayTextures.toArray(new String[0]), textureSize, false);
        
        System.out.println("Texture atlases created: base " + width + "x" + height + 
                          ", overlay " + width + "x" + height);
    }
    
    private void buildSingleAtlas(String[] textureNames, int textureSize, boolean isBase) {
        List<String> texturePaths = new ArrayList<>();
        List<ByteBuffer> textureData = new ArrayList<>();
        
        // Load each texture
        for (String texName : textureNames) {
            if (texName == null) {
                // Add empty/transparent texture data for alignment
                ByteBuffer emptyTexture = MemoryUtil.memAlloc(textureSize * textureSize * 4);
                for (int i = 0; i < textureSize * textureSize * 4; i += 4) {
                    emptyTexture.put(i, (byte) 0);     // R
                    emptyTexture.put(i + 1, (byte) 0); // G
                    emptyTexture.put(i + 2, (byte) 0); // B
                    emptyTexture.put(i + 3, (byte) 0); // A (transparent)
                }
                emptyTexture.flip();
                texturePaths.add(null);
                textureData.add(emptyTexture);
                continue;
            }
            
            String resourcePath = "/textures/" + texName + ".png";
            try (InputStream inputStream = TextureAtlas.class.getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    System.err.println("Warning: Could not find texture " + resourcePath);
                    // Add empty texture for missing textures to maintain atlas alignment
                    ByteBuffer emptyTexture = MemoryUtil.memAlloc(textureSize * textureSize * 4);
                    for (int i = 0; i < textureSize * textureSize * 4; i += 4) {
                        emptyTexture.put(i, (byte) 0);     // R
                        emptyTexture.put(i + 1, (byte) 0); // G
                        emptyTexture.put(i + 2, (byte) 0); // B
                        emptyTexture.put(i + 3, (byte) 0); // A (transparent)
                    }
                    emptyTexture.flip();
                    texturePaths.add(null);
                    textureData.add(emptyTexture);
                    continue;
                }
                
                STBImage.stbi_set_flip_vertically_on_load(true);
                try (var stack = stackPush()) {
                    IntBuffer pw = stack.mallocInt(1);
                    IntBuffer ph = stack.mallocInt(1);
                    IntBuffer pc = stack.mallocInt(1);

                    byte[] textureBytes = inputStream.readAllBytes();
                    ByteBuffer buffer = ByteBuffer.allocateDirect(textureBytes.length).put(textureBytes);
                    buffer.flip();
                    
                    ByteBuffer image = STBImage.stbi_load_from_memory(buffer, pw, ph, pc, 4);
                    if (image == null) {
                        System.err.println("Failed to load texture: " + resourcePath + " - " + STBImage.stbi_failure_reason());
                        continue;
                    }
                    
                    int w = pw.get(0);
                    int h = ph.get(0);
                    if (w != textureSize || h != textureSize) {
                        System.err.println("Warning: Texture " + texName + " size is " + w + "x" + h + ", expected " + textureSize + "x" + textureSize);
                        textureSize = Math.max(w, h); // Adjust texture size
                    }
                    
                    texturePaths.add("/textures/" + texName + ".png");
                    textureData.add(image);
                }
            } catch (IOException e) {
                System.err.println("Error loading texture " + resourcePath + ": " + e.getMessage());
            }
        }
        
        if (textureData.isEmpty()) {
            if (isBase) {
                throw new RuntimeException("No base textures loaded for atlas");
            } else {
                // Create empty overlay atlas with same dimensions as base atlas
                // This ensures unit 1 has a valid texture bound
                overlayTextureId = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, overlayTextureId);
                
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                
                // Create a 1x1 transparent texture as placeholder
                ByteBuffer emptyBuffer = MemoryUtil.memAlloc(4);
                emptyBuffer.put((byte)0).put((byte)0).put((byte)0).put((byte)0);
                emptyBuffer.flip();
                
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, emptyBuffer);
                glBindTexture(GL_TEXTURE_2D, 0);
                
                MemoryUtil.memFree(emptyBuffer);
                return;
            }
        }
        
        // Calculate atlas dimensions - simple grid layout
        int atlasSize = (int) Math.ceil(Math.sqrt(textureData.size()));
        width = atlasSize * textureSize;
        height = atlasSize * textureSize;
        
        // Create atlas buffer
        ByteBuffer atlasBuffer = MemoryUtil.memAlloc(width * height * 4);
        atlasBuffer.clear();
        
        // Fill with transparent pixels initially
        for (int i = 0; i < width * height * 4; i++) {
            atlasBuffer.put((byte) 0);
        }
        atlasBuffer.flip();
        
        // Copy textures into atlas and calculate UV regions
        Map<String, UVRegion> targetRegions = isBase ? baseUvRegions : overlayUvRegions;
        
        for (int i = 0; i < textureData.size(); i++) {
            int atlasX = (i % atlasSize) * textureSize;
            int atlasY = (i / atlasSize) * textureSize;
            
            ByteBuffer texData = textureData.get(i);
            
            // Copy texture data into atlas
            for (int y = 0; y < textureSize; y++) {
                for (int x = 0; x < textureSize; x++) {
                    int srcOffset = (y * textureSize + x) * 4;
                    int dstOffset = ((atlasY + y) * width + (atlasX + x)) * 4;
                    
                    atlasBuffer.put(dstOffset, texData.get(srcOffset));         // R
                    atlasBuffer.put(dstOffset + 1, texData.get(srcOffset + 1)); // G
                    atlasBuffer.put(dstOffset + 2, texData.get(srcOffset + 2)); // B
                    atlasBuffer.put(dstOffset + 3, texData.get(srcOffset + 3)); // A
                }
            }
            
            // Calculate UV region (with small padding to avoid bleeding)
            float padding = 0.5f / width; // Half pixel padding
            float u0 = (float) atlasX / width + padding;
            float v0 = (float) atlasY / height + padding;
            float u1 = (float) (atlasX + textureSize) / width - padding;
            float v1 = (float) (atlasY + textureSize) / height - padding;
            
            String texturePath = texturePaths.get(i);
            if (texturePath != null) {
                targetRegions.put(texturePath, new UVRegion(u0, v0, u1, v1));
            }
            
            // Free the individual texture buffer (but not empty ones we created)
            if (texturePath != null) {
                STBImage.stbi_image_free(texData);
            } else {
                MemoryUtil.memFree(texData);
            }
        }
        
        // Create OpenGL texture
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, atlasBuffer);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        // Store texture ID
        if (isBase) {
            baseTextureId = textureId;
        } else {
            overlayTextureId = textureId;
        }
        
        // Free atlas buffer
        MemoryUtil.memFree(atlasBuffer);
    }
    
    public void bind(int unit) {
        // Bind base atlas to the specified unit
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, baseTextureId);
    }
    
    public void bindOverlay(int unit) {
        // Bind overlay atlas to the specified unit (if it exists)
        if (overlayTextureId != 0) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, overlayTextureId);
        }
    }
    
    public UVRegion getRegion(String texturePath) {
        return baseUvRegions.get(texturePath);
    }
    
    public UVRegion getOverlayRegion(String texturePath) {
        return overlayUvRegions.get(texturePath);
    }
    
    public boolean hasRegion(String texturePath) {
        return baseUvRegions.containsKey(texturePath);
    }
    
    public boolean hasOverlayRegion(String texturePath) {
        return overlayUvRegions.containsKey(texturePath);
    }
    
    public void cleanup() {
        if (baseTextureId != 0) {
            glDeleteTextures(baseTextureId);
            baseTextureId = 0;
        }
        if (overlayTextureId != 0) {
            glDeleteTextures(overlayTextureId);
            overlayTextureId = 0;
        }
    }
}