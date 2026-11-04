package vn.haohan.utilities.feature.connection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record ConnectionRecord(
        UUID id,
        String type, // "CHAIN" or "LEASH"
        UUID creatorUuid,
        UUID worldUuid,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        int anchorA_X,
        int anchorA_Y,
        int anchorA_Z,
        int anchorB_X,
        int anchorB_Y,
        int anchorB_Z,
        long createdAt
) {
    public Location startLocation() {
        World world = Bukkit.getWorld(worldUuid);
        if (world == null) return null;
        return new Location(world, startX, startY, startZ);
    }

    public Location endLocation() {
        World world = Bukkit.getWorld(worldUuid);
        if (world == null) return null;
        return new Location(world, endX, endY, endZ);
    }

    public Location anchorALocation() {
        World world = Bukkit.getWorld(worldUuid);
        if (world == null) return null;
        return new Location(world, anchorA_X, anchorA_Y, anchorA_Z);
    }

    public Location anchorBLocation() {
        World world = Bukkit.getWorld(worldUuid);
        if (world == null) return null;
        return new Location(world, anchorB_X, anchorB_Y, anchorB_Z);
    }
}
