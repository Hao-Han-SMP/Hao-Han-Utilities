package vn.haohansmp.utilities.placement;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Orientation;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Crafter;
import org.bukkit.block.data.type.Grindstone;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Set;

public final class VanillaPlacementRules {
    private static final Set<Material> FULL_LOOK_DIRECTION = EnumSet.of(
            Material.BARREL,
            Material.DISPENSER,
            Material.DROPPER
    );

    private VanillaPlacementRules() {
    }

    public static BlockData orient(
            BlockData original,
            Player player,
            BlockFace clickedFace
    ) {
        BlockData data = original.clone();
        BlockFace horizontal = horizontalFacing(player.getEyeLocation().getDirection());
        BlockFace nearest = nearestLookingFace(player.getEyeLocation().getDirection());
        Material material = data.getMaterial();

        if (data instanceof Crafter crafter) {
            crafter.setOrientation(crafterOrientation(nearest.getOppositeFace(), horizontal));
            return data;
        }
        if (data instanceof Grindstone grindstone && clickedFace != null) {
            orientGrindstone(grindstone, horizontal, clickedFace);
            return data;
        }
        if (data instanceof Directional directional) {
            BlockFace facing = directionalFacing(material, nearest, horizontal, clickedFace);
            setFacingIfAllowed(directional, facing);
        }
        if (data instanceof Rotatable rotatable) {
            rotatable.setRotation(horizontal.getOppositeFace());
        }
        if (data instanceof Orientable orientable && clickedFace != null) {
            Axis axis = axis(clickedFace);
            if (orientable.getAxes().contains(axis)) {
                orientable.setAxis(axis);
            }
        }
        return data;
    }

    public static BlockFace nearestLookingFace(Vector direction) {
        double x = Math.abs(direction.getX());
        double y = Math.abs(direction.getY());
        double z = Math.abs(direction.getZ());
        if (y >= x && y >= z) {
            return direction.getY() >= 0.0 ? BlockFace.UP : BlockFace.DOWN;
        }
        if (x >= z) {
            return direction.getX() >= 0.0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() >= 0.0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    public static BlockFace horizontalFacing(Vector direction) {
        if (Math.abs(direction.getX()) >= Math.abs(direction.getZ())) {
            return direction.getX() >= 0.0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() >= 0.0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    public static Orientation crafterOrientation(BlockFace front, BlockFace horizontal) {
        BlockFace top = switch (front) {
            case DOWN -> horizontal.getOppositeFace();
            case UP -> horizontal;
            default -> BlockFace.UP;
        };
        return Orientation.valueOf(front.name() + "_" + top.name());
    }

    static BlockFace directionalFacing(
            Material material,
            BlockFace nearest,
            BlockFace horizontal,
            BlockFace clickedFace
    ) {
        if (isShulkerBox(material) && clickedFace != null) {
            return clickedFace;
        }
        if (material == Material.HOPPER && clickedFace != null) {
            BlockFace output = clickedFace.getOppositeFace();
            return output == BlockFace.UP ? BlockFace.DOWN : output;
        }
        if (FULL_LOOK_DIRECTION.contains(material)) {
            return nearest.getOppositeFace();
        }
        return horizontal.getOppositeFace();
    }

    static boolean isShulkerBox(Material material) {
        return material == Material.SHULKER_BOX || material.name().endsWith("_SHULKER_BOX");
    }

    private static void orientGrindstone(
            Grindstone grindstone,
            BlockFace horizontal,
            BlockFace clickedFace
    ) {
        switch (clickedFace) {
            case UP -> {
                grindstone.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
                setFacingIfAllowed(grindstone, horizontal);
            }
            case DOWN -> {
                grindstone.setAttachedFace(FaceAttachable.AttachedFace.CEILING);
                setFacingIfAllowed(grindstone, horizontal);
            }
            default -> {
                grindstone.setAttachedFace(FaceAttachable.AttachedFace.WALL);
                setFacingIfAllowed(grindstone, clickedFace);
            }
        }
    }

    private static void setFacingIfAllowed(Directional directional, BlockFace facing) {
        if (directional.getFaces().contains(facing)) {
            directional.setFacing(facing);
        }
    }

    private static Axis axis(BlockFace face) {
        if (face.getModY() != 0) {
            return Axis.Y;
        }
        return face.getModX() != 0 ? Axis.X : Axis.Z;
    }
}
