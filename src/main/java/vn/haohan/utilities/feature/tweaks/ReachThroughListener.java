package vn.haohan.utilities.feature.tweaks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import vn.haohan.utilities.core.ProtectionService;

import java.util.List;
import java.util.Locale;

public class ReachThroughListener implements Listener {

    public enum ReachDecision {
        PASS_TO_VANILLA,
        REACH_THROUGH
    }

    private final Plugin plugin;
    private final ProtectionService protection;
    private final NamespacedKey toggleKey;

    public ReachThroughListener(Plugin plugin, ProtectionService protection) {
        this.plugin = plugin;
        this.protection = protection;
        this.toggleKey = new NamespacedKey(plugin, "reach_through_toggle");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("reach-through.enabled", true);
    }

    public boolean isWorldAllowed(World world) {
        if (world == null) return false;
        String mode = plugin.getConfig().getString("worlds.mode", "BLACKLIST").toUpperCase(Locale.ROOT);
        List<String> list = plugin.getConfig().getStringList("worlds.list");
        boolean listed = list.contains(world.getName());
        return "WHITELIST".equals(mode) ? listed : !listed;
    }

    public boolean hasPermission(Player player) {
        if (!plugin.getConfig().getBoolean("reach-through.require-permission", false)) {
            return true;
        }
        return player.hasPermission("haohanutilities.reachthrough.use");
    }

    public boolean isPlayerToggleEnabled(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return pdc.getOrDefault(toggleKey, PersistentDataType.BOOLEAN, true);
    }

    public void setPlayerToggle(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(toggleKey, PersistentDataType.BOOLEAN, enabled);
    }

    public static ReachDecision resolveDecision(
            boolean featureEnabled,
            boolean playerToggleEnabled,
            boolean isSneaking,
            boolean targetTypeEnabled,
            boolean isFrameEmpty,
            boolean reachThroughEmpty
    ) {
        if (!featureEnabled || !playerToggleEnabled || isSneaking || !targetTypeEnabled) {
            return ReachDecision.PASS_TO_VANILLA;
        }
        if (isFrameEmpty && !reachThroughEmpty) {
            return ReachDecision.PASS_TO_VANILLA;
        }
        return ReachDecision.REACH_THROUGH;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!isEnabled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return;
        if (!isPlayerToggleEnabled(player)) return;
        if (!hasPermission(player)) return;

        Entity entity = event.getRightClicked();
        if (!isWorldAllowed(entity.getWorld())) return;

        Block attachedBlock;
        if (entity instanceof ItemFrame frame) {
            boolean framesEnabled = plugin.getConfig().getBoolean("reach-through.item-frames.enabled", true);
            boolean empty = frame.getItem().getType().isAir();
            boolean reachThroughEmpty = plugin.getConfig().getBoolean("reach-through.item-frames.reach-through-empty", false);

            ReachDecision decision = resolveDecision(
                    true,
                    true,
                    false,
                    framesEnabled,
                    empty,
                    reachThroughEmpty
            );
            if (decision != ReachDecision.REACH_THROUGH) return;

            attachedBlock = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
        } else if (entity instanceof Painting painting) {
            boolean paintingsEnabled = plugin.getConfig().getBoolean("reach-through.paintings.enabled", true);
            if (!paintingsEnabled) return;

            attachedBlock = painting.getLocation().getBlock().getRelative(painting.getAttachedFace());
        } else {
            return;
        }

        if (openTarget(player, attachedBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (protection != null && protection.isProbing()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return;
        if (!isPlayerToggleEnabled(player)) return;
        if (!hasPermission(player)) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        if (!isWorldAllowed(clickedBlock.getWorld())) return;

        if (!plugin.getConfig().getBoolean("reach-through.signs.enabled", true)) return;
        if (!(clickedBlock.getState() instanceof Sign sign)) return;

        boolean reachThroughUnwaxed = plugin.getConfig().getBoolean("reach-through.signs.reach-through-unwaxed", false);
        if (!reachThroughUnwaxed && !sign.isWaxed()) return;

        BlockFace attachedFace;
        if (clickedBlock.getBlockData() instanceof Directional directional) {
            attachedFace = directional.getFacing().getOppositeFace();
        } else {
            attachedFace = BlockFace.DOWN;
        }

        Block attachedBlock = clickedBlock.getRelative(attachedFace);
        if (openTarget(player, attachedBlock)) {
            event.setCancelled(true);
        }
    }

    public boolean openTarget(Player player, Block target) {
        if (target == null) return false;

        if (isChestBlocked(target)) {
            return false;
        }

        if (protection != null && !protection.canInteractBlock(player, target, BlockFace.UP)) {
            return false;
        }

        if (target.getType() == Material.ENDER_CHEST) {
            player.openInventory(player.getEnderChest());
            return true;
        }

        if (target.getType() == Material.CRAFTING_TABLE) {
            player.openWorkbench(target.getLocation(), true);
            return true;
        }

        if (target.getState() instanceof Container container) {
            player.openInventory(container.getInventory());
            return true;
        }

        return false;
    }

    private boolean isChestBlocked(Block block) {
        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST) {
            if (block.getRelative(BlockFace.UP).getType().isOccluding()) {
                return true;
            }
            if (hasSittingCatAbove(block)) {
                return true;
            }

            if (block.getState() instanceof Chest chest) {
                if (chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
                    if (doubleChest.getLeftSide() instanceof Chest left) {
                        Block leftBlock = left.getLocation().getBlock();
                        if (leftBlock.getRelative(BlockFace.UP).getType().isOccluding() || hasSittingCatAbove(leftBlock)) {
                            return true;
                        }
                    }
                    if (doubleChest.getRightSide() instanceof Chest right) {
                        Block rightBlock = right.getLocation().getBlock();
                        if (rightBlock.getRelative(BlockFace.UP).getType().isOccluding() || hasSittingCatAbove(rightBlock)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasSittingCatAbove(Block block) {
        Location loc = block.getLocation();
        BoundingBox box = new BoundingBox(
                loc.getX(), loc.getY() + 1.0, loc.getZ(),
                loc.getX() + 1.0, loc.getY() + 2.0, loc.getZ() + 1.0
        );
        return !block.getWorld().getNearbyEntities(box, e -> e instanceof Cat cat && cat.isSitting()).isEmpty();
    }
}
