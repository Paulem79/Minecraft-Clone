package ovh.paulem.mc.engine.render;

import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import ovh.paulem.mc.config.Config;
import ovh.paulem.mc.engine.Camera;
import ovh.paulem.mc.engine.Hotbar;
import ovh.paulem.mc.engine.Raycaster;
import ovh.paulem.mc.engine.Window;
import ovh.paulem.mc.world.RaycastResult;
import ovh.paulem.mc.world.World;
import ovh.paulem.mc.world.block.types.Block;

import java.nio.FloatBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL46.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Voxel ray renderer using DDA 3D algorithm for direct voxel sampling.
 * Bypasses mesh generation and renders by casting rays from camera through each pixel.
 */
public class VoxelRayRenderer implements IRenderer {
    
    @Getter
    private Camera camera;
    private World world;
    private Hotbar hotbar;
    private HotbarRenderer hotbarRenderer;
    
    // Ray tracing components
    private final Raycaster raycaster = new Raycaster();
    private final Vector3f rayDirection = new Vector3f();
    private final Vector3f cameraPos = new Vector3f();
    
    // Fullscreen quad for rendering
    private int quadVAO, quadVBO;
    private Shader rayShader;
    
    // Color buffer for ray traced pixels
    private float[] colorBuffer;
    private int screenWidth, screenHeight;
    private int colorTexture;
    
    // Performance metrics
    private long lastFrameTime = 0;
    private int frameCount = 0;
    
    @Override
    public void init() {
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        
        // Set background color (sky blue)
        glClearColor(0.5f, 0.8f, 1.0f, 1.0f);
        
        // Initialize camera
        camera = new Camera();
        
        // Hotbar renderer will be initialized when hotbar is set
        
        // Create fullscreen quad
        createFullscreenQuad();
        
        // Create simple shader for displaying ray traced image
        createRayShader();
        
        System.out.println("VoxelRayRenderer initialized");
    }
    
    private void createFullscreenQuad() {
        float[] vertices = {
            // Position (NDC)  // UV
            -1.0f, -1.0f,      0.0f, 0.0f,
             1.0f, -1.0f,      1.0f, 0.0f,
             1.0f,  1.0f,      1.0f, 1.0f,
            -1.0f,  1.0f,      0.0f, 1.0f
        };
        
        int[] indices = {0, 1, 2, 2, 3, 0};
        
        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();
        int quadEBO = glGenBuffers();
        
        glBindVertexArray(quadVAO);
        
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        FloatBuffer vertexBuffer = memAllocFloat(vertices.length);
        vertexBuffer.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
        memFree(vertexBuffer);
        
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadEBO);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        
        // Position attribute
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // UV attribute  
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        glBindVertexArray(0);
    }
    
    private void createRayShader() {
        // Simple shader that just displays a texture
        String vertexShaderSource = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aTexCoord;
            
            out vec2 TexCoord;
            
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                TexCoord = aTexCoord;
            }
            """;
            
        String fragmentShaderSource = """
            #version 330 core
            in vec2 TexCoord;
            out vec4 FragColor;
            
            uniform sampler2D colorTexture;
            
            void main() {
                FragColor = texture(colorTexture, TexCoord);
            }
            """;
            
        rayShader = new Shader(vertexShaderSource, fragmentShaderSource, true);
    }
    
    @Override
    public void render(Window window, float frameDt) {
        long frameStart = System.nanoTime();
        
        // Update screen dimensions if needed
        updateScreenDimensions(window);
        
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        if (world != null && camera != null) {
            // Ray trace the scene
            raytraceScene(window);
            
            // Upload color buffer to texture and render fullscreen quad
            uploadAndRenderTexture();
        }
        
        // Render UI (hotbar, etc.)
        renderUI(window);
        
        // Performance logging
        if (Config.isPerformanceLogging()) {
            long frameEnd = System.nanoTime();
            long frameTime = frameEnd - frameStart;
            frameCount++;
            
            if (frameCount % 60 == 0) { // Log every 60 frames
                double avgFrameTimeMs = (frameTime / 1_000_000.0);
                System.out.printf("[Ray Renderer] Frame time: %.2f ms (%.1f FPS)%n", 
                    avgFrameTimeMs, 1000.0 / avgFrameTimeMs);
            }
        }
    }
    
    private void updateScreenDimensions(Window window) {
        int newWidth = window.getWidth();
        int newHeight = window.getHeight();
        
        if (newWidth != screenWidth || newHeight != screenHeight) {
            screenWidth = newWidth;
            screenHeight = newHeight;
            
            // Reallocate color buffer
            colorBuffer = new float[screenWidth * screenHeight * 3]; // RGB
            
            // Recreate texture
            if (colorTexture != 0) {
                glDeleteTextures(colorTexture);
            }
            
            colorTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, colorTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, screenWidth, screenHeight, 0, GL_RGB, GL_FLOAT, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }
    
    private void raytraceScene(Window window) {
        if (colorBuffer == null) return;
        
        // Clear color buffer
        Arrays.fill(colorBuffer, 0.0f);
        
        // Camera parameters
        cameraPos.set(camera.getPosition());
        float fov = window.getFov();
        float aspect = (float) screenWidth / screenHeight;
        
        // Ray trace each pixel
        for (int y = 0; y < screenHeight; y++) {
            for (int x = 0; x < screenWidth; x++) {
                // Convert screen coordinates to normalized device coordinates
                float ndcX = (2.0f * x / screenWidth) - 1.0f;
                float ndcY = 1.0f - (2.0f * y / screenHeight);
                
                // Calculate ray direction
                calculateRayDirection(ndcX, ndcY, fov, aspect);
                
                // Cast ray and get color
                Vector3f color = castRay();
                
                // Store color in buffer
                int index = (y * screenWidth + x) * 3;
                colorBuffer[index] = color.x;
                colorBuffer[index + 1] = color.y;
                colorBuffer[index + 2] = color.z;
            }
        }
    }
    
    private void calculateRayDirection(float ndcX, float ndcY, float fov, float aspect) {
        // Convert to camera space
        float halfFov = (float) Math.toRadians(fov * 0.5f);
        float tanHalfFov = (float) Math.tan(halfFov);
        
        float cameraX = ndcX * aspect * tanHalfFov;
        float cameraY = ndcY * tanHalfFov;
        float cameraZ = -1.0f;
        
        // Transform to world space using camera rotation
        Vector3f rotation = camera.getRotation();
        float yaw = (float) Math.toRadians(rotation.y);
        float pitch = (float) Math.toRadians(rotation.x);
        
        // Apply rotations: first pitch (around X), then yaw (around Y)
        float cosPitch = (float) Math.cos(pitch);
        float sinPitch = (float) Math.sin(pitch);
        float cosYaw = (float) Math.cos(yaw);
        float sinYaw = (float) Math.sin(yaw);
        
        // Rotate by pitch around X axis
        float tempY = cameraY * cosPitch - cameraZ * sinPitch;
        float tempZ = cameraY * sinPitch + cameraZ * cosPitch;
        
        // Then rotate by yaw around Y axis
        rayDirection.x = cameraX * cosYaw - tempZ * sinYaw;
        rayDirection.y = tempY;
        rayDirection.z = cameraX * sinYaw + tempZ * cosYaw;
        
        rayDirection.normalize();
    }
    
    private Vector3f castRay() {
        // Cast ray using existing raycaster
        RaycastResult result = raycaster.raycast(world, cameraPos, rayDirection, Config.getRayMaxDistance());
        
        if (result.hit) {
            // Get block at hit position
            Block block = world.getBlock(result.x, result.y, result.z);
            if (block != null && block.isBlock()) {
                // Simple Lambert lighting based on face normal
                Vector3f color = getBlockColor(block, result.face);
                
                // Apply fog if enabled
                if (Config.isRayFogEnabled()) {
                    float distance = cameraPos.distance(result.x + 0.5f, result.y + 0.5f, result.z + 0.5f);
                    color = applyFog(color, distance);
                }
                
                return color;
            }
        }
        
        // Return sky color
        return new Vector3f(0.5f, 0.8f, 1.0f);
    }
    
    private Vector3f getBlockColor(Block block, int face) {
        // Get base block color (simplified - could be enhanced with texture lookup)
        Vector3f baseColor = new Vector3f(0.6f, 0.4f, 0.2f); // Default brown for blocks
        
        // Apply simple Lambert lighting based on face normal
        float light = calculateLighting(face);
        baseColor.mul(light);
        
        return baseColor;
    }
    
    private float calculateLighting(int face) {
        // Simple directional lighting
        Vector3f lightDir = new Vector3f(0.3f, 0.8f, 0.5f).normalize();
        
        // Face normals (same as in Render.java)
        Vector3f[] normals = {
            new Vector3f( 1, 0, 0), new Vector3f(-1, 0, 0), // +X, -X
            new Vector3f( 0, 1, 0), new Vector3f( 0,-1, 0), // +Y, -Y  
            new Vector3f( 0, 0, 1), new Vector3f( 0, 0,-1)  // +Z, -Z
        };
        
        if (face >= 0 && face < normals.length) {
            Vector3f normal = normals[face];
            float lambertian = Math.max(0.1f, normal.dot(lightDir));
            return lambertian;
        }
        
        return 0.5f; // Default lighting
    }
    
    private Vector3f applyFog(Vector3f color, float distance) {
        Vector3f fogColor = new Vector3f(0.5f, 0.8f, 1.0f); // Sky color
        float fogStart = Config.getRayFogDistance();
        float fogEnd = Config.getRayMaxDistance();
        
        if (distance < fogStart) {
            return color;
        }
        
        float fogFactor = Math.min(1.0f, (distance - fogStart) / (fogEnd - fogStart));
        return color.lerp(fogColor, fogFactor);
    }
    
    private void uploadAndRenderTexture() {
        if (colorTexture == 0 || colorBuffer == null) return;
        
        // Upload color data to texture
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        FloatBuffer buffer = memAllocFloat(colorBuffer.length);
        buffer.put(colorBuffer).flip();
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, screenWidth, screenHeight, GL_RGB, GL_FLOAT, buffer);
        memFree(buffer);
        
        // Render fullscreen quad
        rayShader.bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        rayShader.setUniform("colorTexture", 0);
        
        glBindVertexArray(quadVAO);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        
        rayShader.detach();
    }
    
    private void renderUI(Window window) {
        // Render hotbar if available
        if (hotbarRenderer != null) {
            hotbarRenderer.render(window);
        }
    }
    
    @Override
    public void setWorld(World world) {
        this.world = world;
    }
    
    @Override
    public void setHotbar(Hotbar hotbar) {
        this.hotbar = hotbar;
        // Initialize hotbar renderer when hotbar is available
        if (hotbar != null) {
            try {
                // Create hotbar renderer - it might work with our simple shader
                hotbarRenderer = new HotbarRenderer(hotbar, rayShader);
            } catch (Exception e) {
                System.err.println("Warning: Could not initialize HotbarRenderer for VoxelRayRenderer: " + e.getMessage());
                // UI rendering will be skipped but core ray tracing will still work
                hotbarRenderer = null;
            }
        }
    }
    
    @Override
    public void spawnBlockParticles(Vector3f position, Block block) {
        // TODO: Implement particle system for ray renderer
        // For now, just log the event
        if (Config.isPerformanceLogging()) {
            System.out.println("Block particles spawned at: " + position + " (not implemented in ray renderer)");
        }
    }
    
    @Override
    public void shutdown() {
        if (rayShader != null) {
            rayShader.cleanup();
        }
        
        if (quadVAO != 0) {
            glDeleteVertexArrays(quadVAO);
        }
        if (quadVBO != 0) {
            glDeleteBuffers(quadVBO);
        }
        if (colorTexture != 0) {
            glDeleteTextures(colorTexture);
        }
        
        System.out.println("VoxelRayRenderer shutdown completed");
    }
    
    @Override
    public String getName() {
        return "Voxel Ray Renderer";
    }
}