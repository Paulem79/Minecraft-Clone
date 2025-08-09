package com.example.mc.world.block;

/**
 * Represents the six faces of a cube. The index mapping matches the existing
 * renderer and mesh generation order:
 * 0:+X, 1:-X, 2:+Y (top), 3:-Y (bottom), 4:+Z, 5:-Z
 */
public enum Face {
    POS_X(0),
    NEG_X(1),
    POS_Y(2),
    NEG_Y(3),
    POS_Z(4),
    NEG_Z(5);

    private final int index;

    Face(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public static Face fromIndex(int idx) {
        switch (idx) {
            case 0: return POS_X;
            case 1: return NEG_X;
            case 2: return POS_Y;
            case 3: return NEG_Y;
            case 4: return POS_Z;
            case 5: return NEG_Z;
            default:
                throw new IllegalArgumentException("Invalid face index: " + idx);
        }
    }
}
