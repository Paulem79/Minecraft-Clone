package ovh.paulem.mc.engine.render;

import ovh.paulem.mc.config.Config;

/**
 * Factory for creating and managing different renderer implementations.
 */
public class RendererFactory {
    
    private static MeshRenderer meshRenderer;
    private static VoxelRayRenderer voxelRayRenderer;
    private static IRenderer currentRenderer;
    
    /**
     * Get the current active renderer based on configuration
     * @return Current renderer instance
     */
    public static IRenderer getCurrentRenderer() {
        if (currentRenderer == null || shouldSwitchRenderer()) {
            switchRenderer();
        }
        return currentRenderer;
    }
    
    /**
     * Force switch to the appropriate renderer based on config
     */
    public static void switchRenderer() {
        IRenderer newRenderer;
        
        if (Config.useVoxelRayRenderer()) {
            if (voxelRayRenderer == null) {
                voxelRayRenderer = new VoxelRayRenderer();
                voxelRayRenderer.init();
            }
            newRenderer = voxelRayRenderer;
        } else {
            if (meshRenderer == null) {
                meshRenderer = new MeshRenderer();
                meshRenderer.init();
            }
            newRenderer = meshRenderer;
        }
        
        // Transfer state from old renderer if switching
        if (currentRenderer != null && currentRenderer != newRenderer) {
            transferRendererState(currentRenderer, newRenderer);
        }
        
        currentRenderer = newRenderer;
        
        if (Config.isPerformanceLogging()) {
            System.out.println("Switched to renderer: " + currentRenderer.getName());
        }
    }
    
    /**
     * Check if we should switch renderer based on config changes
     * @return true if renderer should be switched
     */
    private static boolean shouldSwitchRenderer() {
        if (currentRenderer == null) return true;
        
        boolean isCurrentlyRay = currentRenderer instanceof VoxelRayRenderer;
        return isCurrentlyRay != Config.useVoxelRayRenderer();
    }
    
    /**
     * Transfer common state between renderers when switching
     * @param from Source renderer
     * @param to Target renderer
     */
    private static void transferRendererState(IRenderer from, IRenderer to) {
        // Transfer world and camera references
        if (from.getCamera() != null) {
            // Camera is typically shared, so both renderers will reference the same instance
        }
    }
    
    /**
     * Shutdown all renderers and clean up resources
     */
    public static void shutdown() {
        if (meshRenderer != null) {
            meshRenderer.shutdown();
            meshRenderer = null;
        }
        if (voxelRayRenderer != null) {
            voxelRayRenderer.shutdown();
            voxelRayRenderer = null;
        }
        currentRenderer = null;
    }
    
    /**
     * Get the mesh renderer instance (for state transfer)
     * @return MeshRenderer instance or null if not created
     */
    public static MeshRenderer getMeshRenderer() {
        return meshRenderer;
    }
    
    /**
     * Get the voxel ray renderer instance (for state transfer)
     * @return VoxelRayRenderer instance or null if not created
     */
    public static VoxelRayRenderer getVoxelRayRenderer() {
        return voxelRayRenderer;
    }
}