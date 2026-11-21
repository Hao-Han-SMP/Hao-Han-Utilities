package vn.haohan.utilities.feature.connection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohan.utilities.core.ProtectionService;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ConnectionListener implements Listener {
    private final JavaPlugin plugin;
    private final ConnectionManager manager;
    private final ProtectionService protection;

    public ConnectionListener(JavaPlugin plugin, ConnectionManager manager, ProtectionService protection) {
        this.plugin = plugin;
        this.manager = manager;
        this.protection = protection;
    }

    private boolean isFeatureEnabled() {
        return plugin.getConfig().getBoolean("connection.enabled", true);
    }

    private boolean isWorldAllowed(World world) {
        if (world == null) return false;
        String mode = plugin.getConfig().getString("worlds.mode", "BLACKLIST").toUpperCase(Locale.ROOT);
        List<String> list = plugin.getConfig().getStringList("worlds.list");
        boolean listed = list.contains(world.getName());
        return "WHITELIST".equals(mode) ? listed : !listed;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isFeatureEnabled()) return;

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        Material mat = handItem.getType();

        boolean isChain = ConnectionConstants.isChainMaterial(mat);
        if (!isChain && mat != Material.LEAD) {
            return;
        }

        // Must sneak to connect
        if (!player.isSneaking()) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !isSupportedAnchor(clickedBlock.getType())) {
            return;
        }

        if (!isWorldAllowed(clickedBlock.getWorld())) {
            player.sendActionBar(Component.text("Tính năng kết nối không khả dụng tại thế giới này!", NamedTextColor.RED));
            return;
        }

        Location clickLoc = calculateConnectionPoint(clickedBlock, event.getBlockFace());
        ConnectionManager.PendingSelection pending = manager.getPending(player);

        // Allow canceling if clicking the exact same anchor block again
        if (pending != null && isSameBlock(pending.anchorBlock().getLocation(), clickedBlock.getLocation())) {
            manager.clearPending(player);
            event.setCancelled(true);
            player.playSound(clickLoc, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            player.sendActionBar(Component.text("Đã hủy lựa chọn điểm kết nối.", NamedTextColor.YELLOW));
            return;
        }

        // Protection check
        if (!protection.canBreakBlock(player, clickedBlock)) {
            player.sendActionBar(Component.text("Không có quyền kết nối tại đây!", NamedTextColor.RED));
            return;
        }

        if (pending == null || pending.toolMaterial() != mat) {
            // First point
            manager.setPending(player, clickLoc, clickedBlock, mat);
            event.setCancelled(true);
            player.playSound(clickLoc, Sound.BLOCK_CHAIN_PLACE, 1f, 1.2f);
            player.sendActionBar(Component.text("Đã chọn điểm đầu. Hãy Shift-Right Click điểm thứ hai để kết nối!", NamedTextColor.GREEN));
        } else {
            // Second point
            event.setCancelled(true);
            Location startLoc = pending.startLocation();
            Block anchorA = pending.anchorBlock();
            Block anchorB = clickedBlock;
            manager.clearPending(player);

            if (!startLoc.getWorld().equals(clickLoc.getWorld())) {
                player.sendActionBar(Component.text("Hai điểm không cùng thế giới!", NamedTextColor.RED));
                return;
            }

            double dist = startLoc.distance(clickLoc);
            if (dist < 0.5) {
                player.sendActionBar(Component.text("Khoảng cách quá ngắn!", NamedTextColor.YELLOW));
                return;
            }

            double maxDist = (mat == Material.LEAD)
                    ? plugin.getConfig().getDouble("connection.max-distance-leash", 10.0)
                    : plugin.getConfig().getDouble("connection.max-distance-chain", 16.0);

            if (dist > maxDist) {
                player.sendActionBar(Component.text("Khoảng cách quá xa (tối đa " + (int) maxDist + " blocks cho loại này)!", NamedTextColor.RED));
                return;
            }

            int requiredItems = (int) Math.max(1, Math.round(dist));
            if (player.getGameMode() != GameMode.CREATIVE) {
                if (isChain) {
                    if (handItem.getAmount() < requiredItems) {
                        player.sendActionBar(Component.text("Cần " + requiredItems + " sợi xích để nối khoảng cách " + String.format("%.1f", dist) + " blocks! (Hiện có: " + handItem.getAmount() + ")", NamedTextColor.RED));
                        return;
                    }
                    handItem.subtract(requiredItems);
                } else {
                    handItem.subtract(1);
                }
            }

            String type = (mat == Material.LEAD) ? "LEASH" : "CHAIN";
            manager.createConnection(player, type, mat, startLoc, clickLoc, anchorA, anchorB);

            Sound sound = (mat == Material.LEAD) ? Sound.ITEM_LEAD_TIED : Sound.BLOCK_CHAIN_PLACE;
            player.playSound(clickLoc, sound, 1f, 1f);

            String successMsg = (mat == Material.LEAD)
                    ? "Đã kết nối dây dắt thành công! (1 dây dắt tiêu hao)"
                    : "Đã kết nối xích thành công! (" + requiredItems + " xích tiêu hao)";
            player.sendActionBar(Component.text(successMsg, NamedTextColor.AQUA));
        }
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private boolean isSupportedAnchor(Material blockType) {
        return Tag.FENCES.isTagged(blockType) || Tag.WALLS.isTagged(blockType);
    }

    private Location calculateConnectionPoint(Block block, BlockFace face) {
        if (isSupportedAnchor(block.getType())) {
            return block.getLocation().add(0.5, 0.5, 0.5);
        }

        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        if (face != null) {
            loc.add(face.getDirection().multiply(0.5));
        }
        return loc;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (protection != null && protection.isProbing()) {
            return; // Ignore internal permission probes
        }
        manager.handleAnchorBroken(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            manager.handleAnchorBroken(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            manager.handleAnchorBroken(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        manager.handleAnchorBroken(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            manager.handleAnchorBroken(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            manager.handleAnchorBroken(b);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageInteraction(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Entity target = event.getEntity();

        UUID connId = manager.getConnectionIdFromEntity(target);
        if (connId == null) return;

        event.setCancelled(true);
        ConnectionRecord record = manager.getConnection(connId);
        if (record == null) return;

        Location loc = target.getLocation();
        if (loc.getBlock() != null && !protection.canBreakBlock(player, loc.getBlock())) {
            player.sendActionBar(Component.text("Không có quyền tháo dỡ tại đây!", NamedTextColor.RED));
            return;
        }

        // Animated collapse starting from the hitpoint, dropping all items back
        manager.collapseConnection(connId, loc, false);
        player.sendActionBar(Component.text("Đã tháo dỡ liên kết!", NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        UUID connId = manager.getConnectionIdFromEntity(target);
        if (connId == null) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item != null && item.getType() == Material.SHEARS) {
            event.setCancelled(true);
            Location loc = target.getLocation();
            if (loc.getBlock() != null && !protection.canBreakBlock(player, loc.getBlock())) {
                player.sendActionBar(Component.text("Không có quyền tháo dỡ tại đây!", NamedTextColor.RED));
                return;
            }
            player.playSound(loc, Sound.ENTITY_SHEEP_SHEAR, 1.0f, 1.0f);
            manager.collapseConnection(connId, loc, false);
            player.sendActionBar(Component.text("Đã cắt liên kết!", NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityUnleash(EntityUnleashEvent event) {
        UUID connId = manager.getConnectionIdFromEntity(event.getEntity());
        if (connId != null) {
            manager.collapseConnection(connId, event.getEntity().getLocation(), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerLeash(PlayerLeashEntityEvent event) {
        if (manager.getConnectionIdFromEntity(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.handleChunkLoad(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        manager.handleChunkUnload(event.getChunk());
    }
}
