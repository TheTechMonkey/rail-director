package com.techmonkey.raildirector;

import net.minecraft.world.level.block.state.properties.RailShape;

public final class RailShapes {
    public static final RailShape[] FLAT = {
        RailShape.NORTH_SOUTH,
        RailShape.EAST_WEST,
        RailShape.SOUTH_EAST,
        RailShape.SOUTH_WEST,
        RailShape.NORTH_WEST,
        RailShape.NORTH_EAST
    };

    private RailShapes() {
    }

    public static RailShape fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < FLAT.length ? FLAT[ordinal] : RailShape.NORTH_SOUTH;
    }

    public static int ordinal(RailShape shape) {
        for (int i = 0; i < FLAT.length; i++) {
            if (FLAT[i] == shape) {
                return i;
            }
        }
        return 0;
    }

    public static boolean isFlat(RailShape shape) {
        for (RailShape flat : FLAT) {
            if (flat == shape) {
                return true;
            }
        }
        return false;
    }

    public static String translationKey(RailShape shape) {
        return "shape.raildirector." + shape.getSerializedName();
    }

    public static String shortName(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> "N ↔ S";
            case EAST_WEST -> "E ↔ W";
            case SOUTH_EAST -> "S ↔ E";
            case SOUTH_WEST -> "S ↔ W";
            case NORTH_WEST -> "N ↔ W";
            case NORTH_EAST -> "N ↔ E";
            default -> shape.getSerializedName();
        };
    }
}
