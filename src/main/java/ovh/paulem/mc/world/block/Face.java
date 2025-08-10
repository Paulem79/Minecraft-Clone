package ovh.paulem.mc.world.block;

import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Represents the six faces of a cube. The index mapping matches the existing
 * renderer and mesh generation order:
 * 0:+X, 1:-X, 2:+Y (top), 3:-Y (bottom), 4:+Z, 5:-Z
 */
public enum Face {
    POS_X(Map.of()),
    NEG_X(
            Map.of(
                    new Vector2f(1.0f, 0.0f), new Vector3f(-0.5f, -0.5f, -0.5f),
                    new Vector2f(1.0f, 1.0f), new Vector3f(-0.5f, 0.5f, -0.5f),
                    new Vector2f(0.0f, 1.0f), new Vector3f(-0.5f, 0.5f, 0.5f),
                    new Vector2f(0.0f, 0.0f), new Vector3f(-0.5f, -0.5f, 0.5f)
            )
    ),
    POS_Y(
            Map.of(
                    new Vector2f(0.0f, 1.0f), new Vector3f(-0.5f, 0.5f, -0.5f),
                    new Vector2f(0.0f, 0.0f), new Vector3f(-0.5f, 0.5f, 0.5f),
                    new Vector2f(1.0f, 0.0f), new Vector3f(0.5f, 0.5f, 0.5f),
                    new Vector2f(1.0f, 1.0f), new Vector3f(0.5f, 0.5f, -0.5f)
            )
    ),
    NEG_Y(Map.of()),
    POS_Z(
            Map.of(
                    new Vector2f(0.0f, 0.0f), new Vector3f(-0.5f, -0.5f, 0.5f),
                    new Vector2f(1.0f, 0.0f), new Vector3f(0.5f, -0.5f, 0.5f),
                    new Vector2f(1.0f, 1.0f), new Vector3f(0.5f, 0.5f, 0.5f),
                    new Vector2f(0.0f, 1.0f), new Vector3f(-0.5f, 0.5f, 0.5f)
            )
    ),
    NEG_Z(Map.of());

    private final Map<Vector2f, Vector3f> faceNormals;

    Face(Map<Vector2f, Vector3f> faceNormals) {
        this.faceNormals = faceNormals;
    }

    public Map<Vector2f, Vector3f> getFaceNormals() {
        return faceNormals;
    }

    public static Face fromIndex(int idx) {
        return switch (idx) {
            case 0 -> POS_X;
            case 1 -> NEG_X;
            case 2 -> POS_Y;
            case 3 -> NEG_Y;
            case 4 -> POS_Z;
            case 5 -> NEG_Z;
            default -> throw new IllegalArgumentException("Invalid face index: " + idx);
        };
    }
}
