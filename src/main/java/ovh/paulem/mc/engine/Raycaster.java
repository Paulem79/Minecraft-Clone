package ovh.paulem.mc.engine;

import org.joml.Vector3f;
import ovh.paulem.mc.world.RaycastResult;
import ovh.paulem.mc.world.World;

/**
 * Allocation-free DDA raycaster using integer arithmetic.
 * Reuses result objects to eliminate per-frame Vector allocations.
 */
public class Raycaster {
    
    private final RaycastResult result = new RaycastResult();
    
    /**
     * Cast a ray through the world using DDA algorithm.
     * @param world The world to raycast through
     * @param start Starting position
     * @param dir Direction vector (should be normalized)
     * @param maxDistance Maximum ray distance
     * @return Reused RaycastResult object
     */
    public RaycastResult raycast(World world, Vector3f start, Vector3f dir, float maxDistance) {
        result.reset();
        
        // Current voxel coordinates
        int x = (int) Math.floor(start.x);
        int y = (int) Math.floor(start.y);
        int z = (int) Math.floor(start.z);
        
        // Direction to step in (1 or -1)
        float stepX = dir.x > 0 ? 1 : -1;
        float stepY = dir.y > 0 ? 1 : -1;
        float stepZ = dir.z > 0 ? 1 : -1;
        
        // Calculate initial tMax values (distance to next voxel boundary)
        float tMaxX = dir.x != 0 ? Math.abs((x + (stepX > 0 ? 1 : 0) - start.x) / dir.x) : Float.MAX_VALUE;
        float tMaxY = dir.y != 0 ? Math.abs((y + (stepY > 0 ? 1 : 0) - start.y) / dir.y) : Float.MAX_VALUE;
        float tMaxZ = dir.z != 0 ? Math.abs((z + (stepZ > 0 ? 1 : 0) - start.z) / dir.z) : Float.MAX_VALUE;
        
        // Calculate tDelta values (distance to travel along ray to cross one voxel)
        float tDeltaX = dir.x != 0 ? Math.abs(1 / dir.x) : Float.MAX_VALUE;
        float tDeltaY = dir.y != 0 ? Math.abs(1 / dir.y) : Float.MAX_VALUE;
        float tDeltaZ = dir.z != 0 ? Math.abs(1 / dir.z) : Float.MAX_VALUE;
        
        int face = -1;
        float distance = 0;
        
        // DDA loop
        while (distance < maxDistance) {
            // Check if current voxel is solid
            if (!world.isPassable(x, y, z)) {
                result.setHit(x, y, z, face);
                return result;
            }
            
            // Move to next voxel
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                distance = tMaxX;
                tMaxX += tDeltaX;
                x += (int) stepX;
                face = stepX > 0 ? 1 : 0; // +X : -X
            } else if (tMaxY < tMaxZ) {
                distance = tMaxY;
                tMaxY += tDeltaY;
                y += (int) stepY;
                face = stepY > 0 ? 3 : 2; // +Y : -Y
            } else {
                distance = tMaxZ;
                tMaxZ += tDeltaZ;
                z += (int) stepZ;
                face = stepZ > 0 ? 5 : 4; // +Z : -Z
            }
        }
        
        // No hit within max distance
        return result;
    }
}