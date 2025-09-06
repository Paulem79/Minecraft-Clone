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
    
    private int textureId;
    private int width, height;
    private final Map<String, UVRegion> uvRegions = new HashMap<>();
    
    /**
     * Builds texture atlas from all PNG files in /textures/blocks/
     * Each texture should be the same size for proper atlas layout.
     */
    public void buildAtlas() {
        List<String> texturePaths = new ArrayList<>();
        List<ByteBuffer> textureData = new ArrayList<>();
        int textureSize = 16; // Assume 16x16 textures, should match actual texture size
        
        // List of block texture names
        String[] blockTextures = {
            "stone", "dirt", "grass_block_side", "grass_block_top", 
            "grass_block_side_overlay", "log", "leaves"
        };
        
        // Load each texture
        for (String texName : blockTextures) {
            String resourcePath = "/textures/" + texName + ".png";
            try (InputStream inputStream = TextureAtlas.class.getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    System.err.println("Warning: Could not find texture " + resourcePath);
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
            throw new RuntimeException("No textures loaded for atlas");
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
            
            uvRegions.put(texturePaths.get(i), new UVRegion(u0, v0, u1, v1));
            
            // Free the individual texture buffer
            STBImage.stbi_image_free(texData);
        }
        
        // Create OpenGL texture
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, atlasBuffer);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        // Free atlas buffer
        MemoryUtil.memFree(atlasBuffer);
        
        System.out.println("Texture atlas created: " + width + "x" + height + " with " + textureData.size() + " textures");
    }
    
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }
    
    public UVRegion getRegion(String texturePath) {
        return uvRegions.get(texturePath);
    }
    
    public boolean hasRegion(String texturePath) {
        return uvRegions.containsKey(texturePath);
    }
    
    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
    }
}