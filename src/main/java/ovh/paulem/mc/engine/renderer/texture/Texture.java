package ovh.paulem.mc.engine.renderer.texture;

import org.lwjgl.stb.STBImage;
import ovh.paulem.mc.world.block.types.Block;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;

import static org.lwjgl.opengl.GL46.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class Texture {
    protected final int id;
    protected final int width;
    protected final int height;
    protected final Block baseBlock;
    protected final String resourcePath;

    public Texture(Block baseBlock, String resourcePath) {
        this.baseBlock = baseBlock;
        this.resourcePath = resourcePath;

        URL url = getClass().getResource(resourcePath);
        if (url == null) {
            url = getClass().getResource("/textures/stone.png");
        }
        String filePath;
        try {
            filePath = java.nio.file.Paths.get(url.toURI()).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid texture URI: " + resourcePath, e);
        }

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);

        // Texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        int w, h, comp;
        ByteBuffer image;
        STBImage.stbi_set_flip_vertically_on_load(true);
        try (var stack = stackPush()) {
            IntBuffer pw = stack.mallocInt(1);
            IntBuffer ph = stack.mallocInt(1);
            IntBuffer pc = stack.mallocInt(1);
            image = STBImage.stbi_load(filePath, pw, ph, pc, 4);
            if (image == null) {
                throw new RuntimeException("Failed to load texture: " + resourcePath + " - " + STBImage.stbi_failure_reason());
            }
            w = pw.get(0);
            h = ph.get(0);
            comp = pc.get(0);
        }
        this.width = w;
        this.height = h;

        // Upload image data
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
        glGenerateMipmap(GL_TEXTURE_2D);

        STBImage.stbi_image_free(image);

        glBindTexture(GL_TEXTURE_2D, 0);

        this.id = texId;
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void delete() {
        glDeleteTextures(id);
    }

    public int getId() { return id; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getResourcePath() {
        return resourcePath;
    }

    public Block getBaseBlock() {
        return baseBlock;
    }

    public void serve(Map<String, Texture> textureCache) {
        textureCache.put(resourcePath, this);
    }

    @Override
    public String toString() {
        return "Texture{" +
                "id=" + id +
                ", width=" + width +
                ", height=" + height +
                ", resourcePath='" + resourcePath + '\'' +
                '}';
    }
}
