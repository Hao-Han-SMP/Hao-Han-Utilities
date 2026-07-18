package vn.haohansmp.utilities.carry;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WaterMob;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.integration.SoulAnchorIntegration;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CarryValidator {
    private final JavaPlugin plugin;
    private final CarrySessionManager sessions;
    private final SoulAnchorIntegration soulAnchors;
    private Set<Material> allowedBlocks = EnumSet.noneOf(Material.class);
    private Set<String> configuredWorlds = Set.of();
    private String worldMode;

    public CarryValidator(
            JavaPlugin plugin,
            CarrySessionManager sessions,
            SoulAnchorIntegration soulAnchors
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.soulAnchors = soulAnchors;
        reload();
    }

    public void reload() {
        EnumSet<Material> parsed = EnumSet.noneOf(Material.class);
        for (String name : plugin.getConfig().getStringList("blocks.allowed")) {
            Material material = Material.matchMaterial(name);
            if (material != null && material.isBlock()) {
                parsed.add(material);
            } else {
                plugin.getLogger().warning("Unknown block in blocks.allowed: " + name);
            }
        }
        allowedBlocks = Set.copyOf(parsed);
        configuredWorlds = new HashSet<>(plugin.getConfig().getStringList("worlds.list"));
        worldMode = plugin.getConfig().getString("worlds.mode", "BLACKLIST").toUpperCase(Locale.ROOT);
    }

    public String validateBlockPickup(Player player, Block block) {
        String common = validateCommon(player, block.getWorld(), block.getLocation().add(0.5, 0.5, 0.5));
        if (common != null) {
            return common;
        }
        if (soulAnchors.isAnchor(block)) {
            return soulAnchors.validatePickup(player, block);
        }
        if (!isSupportedBlock(block)) {
            return "unsupported-block";
        }
        if (block.getState() instanceof Container container && !container.getInventory().getViewers().isEmpty()) {
            return "container-in-use";
        }
        return null;
    }

    public String validateEntityPickup(Player player, Entity entity) {
        String common = validateCommon(player, entity.getWorld(), entity.getLocation());
        if (common != null) {
            return common;
        }
        if (!isSupportedEntity(entity)) {
            return "unsupported-entity";
        }
        return null;
    }

    public String validatePlayerPickup(Player carrier, Player target) {
        String common = validateCommon(carrier, target.getWorld(), target.getLocation());
        if (common != null) {
            return common;
        }
        if (!plugin.getConfig().getBoolean("players.enabled", true)) {
            return "unsupported-player";
        }
        if (carrier == target
                || carrier.isInsideVehicle()
                || !carrier.getPassengers().isEmpty()
                || target.getGameMode() == GameMode.SPECTATOR
                || target.isDead()
                || target.isInsideVehicle()
                || !target.getPassengers().isEmpty()
                || sessions.isCarrying(target.getUniqueId())) {
            return "invalid-player-state";
        }
        return null;
    }

    public boolean isPlayerCarryEnabled() {
        return plugin.getConfig().getBoolean("players.enabled", true);
    }

    public String validatePlacement(
            Player player,
            CarrySession session,
            Block destination,
            BlockFace clickedFace
    ) {
        if (!worldAllowed(destination.getWorld())) {
            return "world-disabled";
        }
        if (player.getEyeLocation().distance(destination.getLocation().add(0.5, 0.5, 0.5))
                > plugin.getConfig().getDouble("placement.maximum-distance", 5.0)) {
            return "too-far";
        }
        if (!destination.isEmpty() && !destination.isReplaceable()) {
            return "invalid-destination";
        }
        if (session.payload().kind().isBlockLike()) {
            try {
                if (!destination.canPlace(CarrySnapshotService.placementBlockData(
                        player,
                        session.payload(),
                        clickedFace
                ))) {
                    return "invalid-destination";
                }
            } catch (IllegalArgumentException exception) {
                return "invalid-block-data";
            }
        }
        return null;
    }

    public boolean isAllowedMaterial(Material material) {
        return allowedBlocks.contains(material)
                || material == Material.CHEST
                || material == Material.TRAPPED_CHEST
                || (material.name().endsWith("_SHULKER_BOX") && allowedBlocks.contains(Material.SHULKER_BOX));
    }

    public boolean isSupportedBlock(Block block) {
        return soulAnchors.isAnchor(block) || isAllowedMaterial(block.getType());
    }

    public boolean isSupportedEntity(Entity entity) {
        return plugin.getConfig().getBoolean("entities.enabled", true)
                && !entity.getScoreboardTags().contains(CarrySnapshotService.VISUAL_ENTITY_TAG)
                && (entity instanceof Animals || entity instanceof WaterMob || entity instanceof Ambient)
                && !(entity instanceof Player)
                && !entity.isDead()
                && entity.isValid()
                && !entity.isInsideVehicle()
                && entity.getPassengers().isEmpty()
                && (!(entity instanceof LivingEntity living) || !living.isLeashed());
    }

    private String validateCommon(Player player, World world, org.bukkit.Location target) {
        if (!player.hasPermission("haohanutilities.carry.use")) {
            return "no-permission";
        }
        if (sessions.isCarrying(player.getUniqueId())) {
            return "already-carrying";
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
            return "invalid-player-state";
        }
        if (!worldAllowed(world)) {
            return "world-disabled";
        }
        if (player.getEyeLocation().distance(target)
                > plugin.getConfig().getDouble("pickup.maximum-distance", 4.5)) {
            return "too-far";
        }
        return null;
    }

    private boolean worldAllowed(World world) {
        boolean listed = configuredWorlds.contains(world.getName());
        return "WHITELIST".equals(worldMode) ? listed : !listed;
    }
}
