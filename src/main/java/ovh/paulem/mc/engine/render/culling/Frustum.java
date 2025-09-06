package ovh.paulem.mc.engine.render.culling;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Frustum culling utility that extracts 6 planes from projection*view matrix
 * and tests AABBs against the frustum to determine visibility.
 */
public class Frustum {
    
    // 6 frustum planes: left, right, bottom, top, near, far
    private final Vector4f[] planes = new Vector4f[6];
    
    public Frustum() {
        for (int i = 0; i < 6; i++) {
            planes[i] = new Vector4f();
        }
    }
    
    /**
     * Extract frustum planes from combined projection*view matrix.
     * Planes are stored in the form ax + by + cz + d = 0
     * @param projectionView The combined projection * view matrix
     */
    public void extractPlanes(Matrix4f projectionView) {
        // Extract the 6 frustum planes from the projection*view matrix
        // Using Gribb & Hartmann method
        
        float m00 = projectionView.m00(), m01 = projectionView.m01(), m02 = projectionView.m02(), m03 = projectionView.m03();
        float m10 = projectionView.m10(), m11 = projectionView.m11(), m12 = projectionView.m12(), m13 = projectionView.m13();
        float m20 = projectionView.m20(), m21 = projectionView.m21(), m22 = projectionView.m22(), m23 = projectionView.m23();
        float m30 = projectionView.m30(), m31 = projectionView.m31(), m32 = projectionView.m32(), m33 = projectionView.m33();
        
        // Left plane: column 4 + column 1
        planes[0].set(m30 + m00, m31 + m01, m32 + m02, m33 + m03);
        normalizePlane(planes[0]);
        
        // Right plane: column 4 - column 1
        planes[1].set(m30 - m00, m31 - m01, m32 - m02, m33 - m03);
        normalizePlane(planes[1]);
        
        // Bottom plane: column 4 + column 2
        planes[2].set(m30 + m10, m31 + m11, m32 + m12, m33 + m13);
        normalizePlane(planes[2]);
        
        // Top plane: column 4 - column 2
        planes[3].set(m30 - m10, m31 - m11, m32 - m12, m33 - m13);
        normalizePlane(planes[3]);
        
        // Near plane: column 4 + column 3
        planes[4].set(m30 + m20, m31 + m21, m32 + m22, m33 + m23);
        normalizePlane(planes[4]);
        
        // Far plane: column 4 - column 3
        planes[5].set(m30 - m20, m31 - m21, m32 - m22, m33 - m23);
        normalizePlane(planes[5]);
    }
    
    private void normalizePlane(Vector4f plane) {
        float length = (float) Math.sqrt(plane.x * plane.x + plane.y * plane.y + plane.z * plane.z);
        if (length > 0) {
            plane.div(length);
        }
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
        for (Vector4f plane : planes) {
            // Find the positive vertex (furthest from plane normal)
            float px = plane.x > 0 ? maxX : minX;
            float py = plane.y > 0 ? maxY : minY;
            float pz = plane.z > 0 ? maxZ : minZ;
            
            // If positive vertex is outside this plane, the entire AABB is outside
            if (plane.x * px + plane.y * py + plane.z * pz + plane.w < 0) {
                return true;
            }
        }
        return false; // AABB is at least partially inside frustum
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