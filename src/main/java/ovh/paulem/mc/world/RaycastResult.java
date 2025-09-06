package ovh.paulem.mc.world;

/**
 * Allocation-free container for raycast results.
 * Reused across multiple raycast operations to avoid per-frame allocations.
 */
public class RaycastResult {
    public boolean hit;
    public int x, y, z;
    public int face;
    
    public void reset() {
        hit = false;
        face = -1;
    }
    
    public void setHit(int x, int y, int z, int face) {
        this.hit = true;
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
    }
}