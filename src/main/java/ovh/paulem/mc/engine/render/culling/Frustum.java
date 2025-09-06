package ovh.paulem.mc.engine.render.culling;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/**
 * Frustum culling utility that extracts 6 planes from projection*view matrix
 * and tests AABBs against the frustum to determine visibility.
 */
public class Frustum {
    
    private final FrustumIntersection frustumIntersection = new FrustumIntersection();
    
    /**
     * Extract frustum planes from combined projection*view matrix.
     * @param projectionView The combined projection * view matrix
     */
    public void extractPlanes(Matrix4f projectionView) {
        // Use JOML's built-in frustum intersection which handles the plane extraction correctly
        frustumIntersection.set(projectionView);
    }
    
    /**
     * Test if an axis-aligned bounding box is completely outside the frustum.
     * @param minX Minimum X coordinate of the AABB
     * @param minY Minimum Y coordinate of the AABB
     * @param minZ Minimum Z coordinate of the AABB
     * @param maxX Maximum X coordinate of the AABB
     * @param maxY Maximum Y coordinate of the AABB
     * @param maxZ Maximum Z coordinate of the AABB
     * @return true if the AABB is completely outside the frustum (should be culled)
     */
    public boolean isOutside(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // JOML's testAab returns false if the AABB is outside the frustum
        return !frustumIntersection.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    /**
     * Test if a chunk AABB is outside the frustum.
     * @param chunkOriginX Chunk's origin X coordinate
     * @param chunkOriginZ Chunk's origin Z coordinate
     * @param chunkSizeX Chunk size in X direction
     * @param chunkSizeY Chunk size in Y direction
     * @param chunkSizeZ Chunk size in Z direction
     * @return true if the chunk should be culled
     */
    public boolean shouldCullChunk(float chunkOriginX, float chunkOriginZ, 
                                  int chunkSizeX, int chunkSizeY, int chunkSizeZ) {
        float minX = chunkOriginX;
        float minY = 0; // Chunks start at Y=0
        float minZ = chunkOriginZ;
        float maxX = chunkOriginX + chunkSizeX;
        float maxY = chunkSizeY;
        float maxZ = chunkOriginZ + chunkSizeZ;
        
        return isOutside(minX, minY, minZ, maxX, maxY, maxZ);
    }
}