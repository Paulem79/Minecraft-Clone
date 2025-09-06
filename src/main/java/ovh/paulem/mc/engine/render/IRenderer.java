package ovh.paulem.mc.engine.render;

import org.joml.Vector3f;
import ovh.paulem.mc.engine.Camera;
import ovh.paulem.mc.engine.Hotbar;
import ovh.paulem.mc.engine.Window;
import ovh.paulem.mc.world.World;
import ovh.paulem.mc.world.block.types.Block;

/**
 * Interface for different rendering approaches.
 * Allows switching between mesh-based rendering and voxel ray traversal.
 */
public interface IRenderer {
    
    /**
     * Initialize the renderer (shaders, buffers, etc.)
     */
    void init();
    
    /**
     * Render a frame
     * @param window The window context
     * @param frameDt Frame delta time in seconds
     */
    void render(Window window, float frameDt);
    
    /**
     * Get the camera used by this renderer
     * @return Camera instance
     */
    Camera getCamera();
    
    /**
     * Set the world to render
     * @param world World instance
     */
    void setWorld(World world);
    
    /**
     * Set the hotbar for UI rendering
     * @param hotbar Hotbar instance
     */
    void setHotbar(Hotbar hotbar);
    
    /**
     * Spawn block break particles
     * @param position Position where particles should spawn
     * @param block Block type for particle appearance
     */
    void spawnBlockParticles(Vector3f position, Block block);
    
    /**
     * Clean up resources
     */
    void shutdown();
    
    /**
     * Get the name of this renderer for debugging/logging
     * @return Renderer name
     */
    String getName();
}