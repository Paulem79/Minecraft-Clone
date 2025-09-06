package ovh.paulem.mc.config;

/**
 * Configuration for the Minecraft Clone.
 * Contains runtime settings that can be modified during gameplay.
 */
public class Config {
    
    // Renderer configuration
    private static boolean useVoxelRayRenderer = false;
    
    // Ray renderer settings
    private static float rayMaxDistance = 200.0f;
    private static float rayStepSize = 0.5f;
    private static boolean raySkipAir = true;
    private static boolean rayFogEnabled = true;
    private static float rayFogDistance = 150.0f;
    
    // Performance settings
    private static boolean performanceLogging = false;
    
    /**
     * Get whether to use voxel ray renderer instead of mesh renderer
     * @return true if voxel ray renderer should be used
     */
    public static boolean useVoxelRayRenderer() {
        return useVoxelRayRenderer;
    }
    
    /**
     * Set whether to use voxel ray renderer
     * @param use true to use voxel ray renderer, false for mesh renderer
     */
    public static void setUseVoxelRayRenderer(boolean use) {
        useVoxelRayRenderer = use;
    }
    
    /**
     * Toggle between mesh and ray renderer
     * @return new state after toggle
     */
    public static boolean toggleRenderer() {
        useVoxelRayRenderer = !useVoxelRayRenderer;
        return useVoxelRayRenderer;
    }
    
    /**
     * Get maximum ray distance for ray renderer
     * @return maximum distance in blocks
     */
    public static float getRayMaxDistance() {
        return rayMaxDistance;
    }
    
    /**
     * Set maximum ray distance
     * @param distance maximum distance in blocks
     */
    public static void setRayMaxDistance(float distance) {
        rayMaxDistance = distance;
    }
    
    /**
     * Get ray step size
     * @return step size for ray marching
     */
    public static float getRayStepSize() {
        return rayStepSize;
    }
    
    /**
     * Set ray step size
     * @param stepSize step size for ray marching
     */
    public static void setRayStepSize(float stepSize) {
        rayStepSize = stepSize;
    }
    
    /**
     * Get whether ray renderer should skip air blocks for performance
     * @return true if air blocks should be skipped
     */
    public static boolean isRaySkipAir() {
        return raySkipAir;
    }
    
    /**
     * Set whether to skip air blocks
     * @param skipAir true to skip air blocks
     */
    public static void setRaySkipAir(boolean skipAir) {
        raySkipAir = skipAir;
    }
    
    /**
     * Get whether fog is enabled for ray renderer
     * @return true if fog is enabled
     */
    public static boolean isRayFogEnabled() {
        return rayFogEnabled;
    }
    
    /**
     * Set fog enabled state
     * @param fogEnabled true to enable fog
     */
    public static void setRayFogEnabled(boolean fogEnabled) {
        rayFogEnabled = fogEnabled;
    }
    
    /**
     * Get fog distance
     * @return fog start distance in blocks
     */
    public static float getRayFogDistance() {
        return rayFogDistance;
    }
    
    /**
     * Set fog distance
     * @param fogDistance fog start distance in blocks
     */
    public static void setRayFogDistance(float fogDistance) {
        rayFogDistance = fogDistance;
    }
    
    /**
     * Get whether performance logging is enabled
     * @return true if performance logging is enabled
     */
    public static boolean isPerformanceLogging() {
        return performanceLogging;
    }
    
    /**
     * Set performance logging state
     * @param logging true to enable performance logging
     */
    public static void setPerformanceLogging(boolean logging) {
        performanceLogging = logging;
    }
}