package vn.haohansmp.utilities.chest;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;

public final class ChestPlacementRules {
    private ChestPlacementRules() {
    }

    public static Chest.Type typeForPartner(BlockFace facing, BlockFace directionToPartner) {
        if (clockwise(facing) == directionToPartner) {
            return Chest.Type.LEFT;
        }
        if (counterClockwise(facing) == directionToPartner) {
            return Chest.Type.RIGHT;
        }
        return Chest.Type.SINGLE;
    }

    public static Chest.Type opposite(Chest.Type type) {
        return switch (type) {
            case LEFT -> Chest.Type.RIGHT;
            case RIGHT -> Chest.Type.LEFT;
            case SINGLE -> Chest.Type.SINGLE;
        };
    }

    public static BlockFace clockwise(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("Chest facing must be horizontal");
        };
    }

    public static BlockFace counterClockwise(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("Chest facing must be horizontal");
        };
    }

    public static boolean isHorizontal(BlockFace face) {
        return face == BlockFace.NORTH
                || face == BlockFace.EAST
                || face == BlockFace.SOUTH
                || face == BlockFace.WEST;
    }
}
