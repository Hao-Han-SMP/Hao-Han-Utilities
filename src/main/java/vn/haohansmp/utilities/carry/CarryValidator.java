package vn.haohansmp.utilities.carry;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.listener.ViewerTracker;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CarryValidator {
    private final JavaPlugin plugin;
    private final CarrySessionManager sessions;
    private final CarryLockManager locks;
    private final ViewerTracker viewers;
    private Set<Material> allowed = EnumSet.noneOf(Material.class);
    private Set<String> configuredWorlds = Set.of();
    private String worldMode;

    public CarryValidator(JavaPlugin plugin, CarrySessionManager sessions, CarryLockManager locks, ViewerTracker viewers) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.locks = locks;
        this.viewers = viewers;
        reload();
    }

    public void reload() {
        EnumSet<Material> parsed = EnumSet.noneOf(Material.class);
        for (String name : plugin.getConfig().getStringList("blocks.allowed")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                parsed.add(material);
            } else {
                plugin.getLogger().warning("Unknown material in blocks.allowed: " + name);
            }
        }
        allowed = Set.copyOf(parsed);
        configuredWorlds = new HashSet<>(plugin.getConfig().getStringList("worlds.list"));
        worldMode = plugin.getConfig().getString("worlds.mode", "BLACKLIST").toUpperCase(Locale.ROOT);
    }

    public String validatePickup(Player player, Block block, boolean pending) {
        if (!player.hasPermission("haohanutilities.carry.use")) return "no-permission";
        if (sessions.isCarrying(player.getUniqueId()) || pending) return "already-carrying";
        if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) return "invalid-player-state";
        if (plugin.getConfig().getBoolean("pickup.require-sneaking", true) && !player.isSneaking()) return "must-sneak";
        if (plugin.getConfig().getBoolean("pickup.require-empty-main-hand", true)
                && !player.getInventory().getItem(EquipmentSlot.HAND).getType().isAir()) return "hands-must-be-empty";
        if (plugin.getConfig().getBoolean("pickup.require-empty-off-hand", true)
                && !player.getInventory().getItem(EquipmentSlot.OFF_HAND).getType().isAir()) return "hands-must-be-empty";
        if (!worldAllowed(block.getWorld())) return "world-disabled";
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return "chunk-not-loaded";
        if (player.getEyeLocation().distance(block.getLocation().add(0.5, 0.5, 0.5))
                > plugin.getConfig().getDouble("pickup.maximum-distance", 4.5)) return "too-far";
        if (!allowed.contains(block.getType())) return "unsupported-block";
        if (block.getBlockData() instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
            return "double-chest-not-supported";
        }
        BlockPosition position = BlockPosition.from(block);
        if (locks.isLocked(position)) return "block-is-busy";
        if (block.getState() instanceof Container && viewers.hasViewers(position)) return "container-in-use";
        return null;
    }

    public String validatePlacement(Player player, CarrySession session, Block destination) {
        if (!worldAllowed(destination.getWorld())) return "world-disabled";
        if (!destination.getWorld().isChunkLoaded(destination.getX() >> 4, destination.getZ() >> 4)) return "chunk-not-loaded";
        if (player.getEyeLocation().distance(destination.getLocation().add(0.5, 0.5, 0.5))
                > plugin.getConfig().getDouble("placement.maximum-distance", 5.0)) return "too-far";
        if (!destination.isEmpty() && !destination.isReplaceable()) return "invalid-destination";
        if (locks.isLocked(BlockPosition.from(destination))) return "block-is-busy";
        if (plugin.getConfig().getBoolean("placement.require-solid-support", true)
                && !destination.getRelative(0, -1, 0).getType().isSolid()) return "invalid-destination";
        BlockData data;
        try {
            data = org.bukkit.Bukkit.createBlockData(session.payload().blockData());
        } catch (IllegalArgumentException exception) {
            return "invalid-block-data";
        }
        if (!destination.canPlace(data)) return "invalid-destination";
        return null;
    }

    public boolean isAllowedMaterial(Material material) {
        return allowed.contains(material);
    }

    private boolean worldAllowed(World world) {
        boolean listed = configuredWorlds.contains(world.getName());
        return "WHITELIST".equals(worldMode) ? listed : !listed;
    }
}
