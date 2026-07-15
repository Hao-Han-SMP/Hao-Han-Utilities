package vn.haohansmp.utilities.carry;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

public record BlockPosition(UUID worldUuid, int x, int y, int z) {
    public static BlockPosition from(Block block) {
        return new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public Block block(World world) {
        if (!world.getUID().equals(worldUuid)) {
            throw new IllegalArgumentException("World UUID does not match position");
        }
        return world.getBlockAt(x, y, z);
    }

    public Location location(World world) {
        return new Location(world, x, y, z);
    }

    @Override
    public String toString() {
        return worldUuid + ":" + x + "," + y + "," + z;
    }
}
