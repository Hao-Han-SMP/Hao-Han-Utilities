package vn.haohan.utilities.feature.tweaks;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import vn.haohan.utilities.core.ProtectionService;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class BlockOverlayListener implements Listener {

    public enum PlacementAction {
        IGNORE,
        PLACE_NEW,
        STACK_SNOW
    }

    private static final double FULL_CUBE_EPSILON = 0.999;
    private static final float CARPET_HITBOX_HEIGHT = 0.0625f;
    private static final float SNOW_LAYER_HEIGHT = 0.125f;

    private final Plugin plugin;
    private final ProtectionService protection;
    private final NamespacedKey overlayKey;
    private final NamespacedKey hitboxKey;

    public BlockOverlayListener(Plugin plugin, ProtectionService protection) {
        this.plugin = plugin;
        this.protection = protection;
        this.overlayKey = new NamespacedKey(plugin, "block_overlay");
        this.hitboxKey = new NamespacedKey(plugin, "block_overlay_hitbox");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("block-overlay.enabled", true);
    }

    public boolean isWorldAllowed(World world) {
        if (world == null) return false;
        String mode = plugin.getConfig().getString("worlds.mode", "BLACKLIST").toUpperCase(Locale.ROOT);
        List<String> list = plugin.getConfig().getStringList("worlds.list");
        boolean listed = list.contains(world.getName());
        return "WHITELIST".equals(mode) ? listed : !listed;
    }

    public static boolean isOverlayMaterial(Material material) {
        if (material == null) return false;
        return material == Material.SNOW || material.name().endsWith("_CARPET");
    }

    public static boolean isExcludedHostMaterial(Material material) {
        if (material == null
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || isOverlayMaterial(material)) {
            return true;
        }
        return material == Material.WATER
                || material == Material.LAVA
                || material == Material.BARRIER
                || material == Material.LIGHT
                || material == Material.STRUCTURE_VOID;
    }

    public static boolean isFullCubeBox(BoundingBox box) {
        return box.getWidthX() >= FULL_CUBE_EPSILON
                && box.getWidthZ() >= FULL_CUBE_EPSILON
                && box.getHeight() >= FULL_CUBE_EPSILON;
    }

    public static boolean isNonFullBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (isExcludedHostMaterial(type) || block.isLiquid() || type.isOccluding()) {
            return false;
        }
        if (!isFullCubeBox(block.getBoundingBox())) {
            return true;
        }
        Collection<BoundingBox> collisionBoxes = block.getCollisionShape().getBoundingBoxes();
        if (collisionBoxes.isEmpty() || collisionBoxes.size() > 1) {
            return true;
        }
        return !isFullCubeBox(collisionBoxes.iterator().next());
    }

    public static boolean requiresSneakToBypassInteract(Material material) {
        if (material == null) return false;
        // Fences report isInteractable() == true in Bukkit (for leads),
        // but right-clicking them with snow/carpet does nothing in vanilla.
        if (material.name().endsWith("_FENCE")) {
            return false;
        }
        return material.isInteractable();
    }

    public static float getHitboxHeight(BlockData data) {
        if (data instanceof Snow snow) {
            return Math.max(SNOW_LAYER_HEIGHT, SNOW_LAYER_HEIGHT * snow.getLayers());
        }
        return CARPET_HITBOX_HEIGHT;
    }

    public static PlacementAction resolvePlacementAction(
            boolean featureEnabled,
            boolean isOverlayItem,
            boolean isSneaking,
            boolean requireSneak,
            boolean hostIsInteractable,
            BlockFace clickedFace,
            boolean hostIsNonFull,
            Material existingOverlay,
            int existingSnowLayers,
            int maxSnowLayers,
            Material newOverlay,
            boolean stackSnowLayers
    ) {
        if (!featureEnabled || !isOverlayItem || !hostIsNonFull) {
            return PlacementAction.IGNORE;
        }
        if ((requireSneak || hostIsInteractable) && !isSneaking) {
            return PlacementAction.IGNORE;
        }
        // Shift + Right-click on the top face of an interactable block (e.g. chest)
        // must pass through to vanilla so players can still place blocks on top of it.
        if (hostIsInteractable && clickedFace == BlockFace.UP) {
            return PlacementAction.IGNORE;
        }
        if (existingOverlay == null) {
            return PlacementAction.PLACE_NEW;
        }
        if (existingOverlay == Material.SNOW && newOverlay == Material.SNOW
                && stackSnowLayers && existingSnowLayers < maxSnowLayers) {
            return PlacementAction.STACK_SNOW;
        }
        return PlacementAction.IGNORE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (protection != null && protection.isProbing()) return;

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        if (!isWorldAllowed(player.getWorld())) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking()) {
            handleSneakLeftClickRemove(event, player, clicked);
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack heldItem = event.getItem();
        Material itemType = heldItem != null ? heldItem.getType() : null;
        if (!isOverlayMaterial(itemType)) return;

        if (plugin.getConfig().getBoolean("block-overlay.require-permission", false)
                && !player.hasPermission("haohanutilities.blockoverlay.use")) {
            return;
        }

        Block targetHost = clicked;
        boolean directClickOnHost = true;
        if (!isNonFullBlock(clicked)) {
            Block adjacent = clicked.getRelative(event.getBlockFace());
            if (!isNonFullBlock(adjacent)) {
                return;
            }
            targetHost = adjacent;
            directClickOnHost = false;
        }

        BlockDisplay existing = findOverlay(targetHost);
        Material existingMaterial = existing != null ? existing.getBlock().getMaterial() : null;
        int existingLayers = (existing != null && existing.getBlock() instanceof Snow snow)
                ? snow.getLayers()
                : 0;
        int maxLayers = (existing != null && existing.getBlock() instanceof Snow snow)
                ? snow.getMaximumLayers()
                : 8;

        boolean hostInteractable = directClickOnHost && requiresSneakToBypassInteract(targetHost.getType());
        PlacementAction action = resolvePlacementAction(
                true,
                true,
                player.isSneaking(),
                plugin.getConfig().getBoolean("block-overlay.require-sneak", false),
                hostInteractable,
                event.getBlockFace(),
                true,
                existingMaterial,
                existingLayers,
                maxLayers,
                itemType,
                plugin.getConfig().getBoolean("block-overlay.stack-snow-layers", true)
        );

        if (action == PlacementAction.IGNORE) return;

        if (protection != null && !protection.canBreakBlock(player, targetHost)) {
            return;
        }

        event.setCancelled(true);
        applyPlacement(player, hand, heldItem, targetHost, existing, itemType, action);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction interaction) || !isHitboxEntity(interaction)) {
            return;
        }
        event.setCancelled(true);
        if (!isEnabled()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isWorldAllowed(player.getWorld())) return;
        if (plugin.getConfig().getBoolean("block-overlay.require-permission", false)
                && !player.hasPermission("haohanutilities.blockoverlay.use")) {
            return;
        }

        Block host = interaction.getLocation().getBlock();
        if (protection != null && !protection.canBreakBlock(player, host)) {
            return;
        }

        BlockDisplay overlay = findOverlay(host);
        boolean dropItems = player.getGameMode() != GameMode.CREATIVE;
        if (overlay != null) {
            host.getWorld().playSound(
                    host.getLocation().add(0.5, 0.2, 0.5),
                    overlay.getBlock().getSoundGroup().getBreakSound(),
                    SoundCategory.BLOCKS,
                    1.0f,
                    0.8f
            );
            removeOverlayEntity(overlay, host, dropItems);
        }
        interaction.remove();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getRightClicked() instanceof Interaction interaction) || !isHitboxEntity(interaction)) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        if (!isWorldAllowed(player.getWorld())) return;

        ItemStack heldItem = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (heldItem.getType() != Material.SNOW) return;

        if (plugin.getConfig().getBoolean("block-overlay.require-permission", false)
                && !player.hasPermission("haohanutilities.blockoverlay.use")) {
            return;
        }

        Block host = interaction.getLocation().getBlock();
        BlockDisplay existing = findOverlay(host);
        if (existing == null || !(existing.getBlock() instanceof Snow snow)) return;
        if (!plugin.getConfig().getBoolean("block-overlay.stack-snow-layers", true)) return;
        if (snow.getLayers() >= snow.getMaximumLayers()) return;
        if (protection != null && !protection.canBreakBlock(player, host)) return;

        event.setCancelled(true);
        applyPlacement(player, hand, heldItem, host, existing, Material.SNOW, PlacementAction.STACK_SNOW);
    }

    private void handleSneakLeftClickRemove(PlayerInteractEvent event, Player player, Block clicked) {
        BlockDisplay existing = findOverlay(clicked);
        if (existing == null) return;

        if (plugin.getConfig().getBoolean("block-overlay.require-permission", false)
                && !player.hasPermission("haohanutilities.blockoverlay.use")) {
            return;
        }
        if (protection != null && !protection.canBreakBlock(player, clicked)) {
            return;
        }

        event.setCancelled(true);
        boolean dropItems = player.getGameMode() != GameMode.CREATIVE;
        clicked.getWorld().playSound(
                clicked.getLocation().add(0.5, 0.2, 0.5),
                existing.getBlock().getSoundGroup().getBreakSound(),
                SoundCategory.BLOCKS,
                1.0f,
                0.8f
        );
        removeOverlayAt(clicked, dropItems);
    }

    private void applyPlacement(
            Player player,
            EquipmentSlot hand,
            ItemStack heldItem,
            Block host,
            BlockDisplay existing,
            Material newMaterial,
            PlacementAction action
    ) {
        BlockData placedData;
        if (action == PlacementAction.PLACE_NEW) {
            placedData = Bukkit.createBlockData(newMaterial);
            Location displayLoc = new Location(host.getWorld(), host.getX(), host.getY(), host.getZ(), 0f, 0f);
            host.getWorld().spawn(displayLoc, BlockDisplay.class, display -> {
                display.setBlock(placedData);
                display.setPersistent(true);
                display.getPersistentDataContainer().set(overlayKey, PersistentDataType.BYTE, (byte) 1);
            });
            spawnHitbox(host, placedData);
        } else if (action == PlacementAction.STACK_SNOW && existing != null) {
            Snow snow = (Snow) existing.getBlock().clone();
            snow.setLayers(snow.getLayers() + 1);
            placedData = snow;
            existing.setBlock(placedData);
            Interaction hitbox = findHitbox(host);
            if (hitbox != null) {
                hitbox.setInteractionHeight(getHitboxHeight(placedData));
            } else {
                spawnHitbox(host, placedData);
            }
        } else {
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            heldItem.setAmount(heldItem.getAmount() - 1);
        }

        host.getWorld().playSound(
                host.getLocation().add(0.5, 0.2, 0.5),
                placedData.getSoundGroup().getPlaceSound(),
                SoundCategory.BLOCKS,
                1.0f,
                0.8f
        );
        player.swingHand(hand);
    }

    private void spawnHitbox(Block host, BlockData data) {
        Location hitboxLoc = new Location(host.getWorld(), host.getX() + 0.5, host.getY(), host.getZ() + 0.5);
        host.getWorld().spawn(hitboxLoc, Interaction.class, interaction -> {
            interaction.setInteractionWidth(1.0f);
            interaction.setInteractionHeight(getHitboxHeight(data));
            interaction.setResponsive(true);
            interaction.setPersistent(true);
            interaction.getPersistentDataContainer().set(hitboxKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (protection != null && protection.isProbing()) return;
        Block block = event.getBlock();
        boolean dropItems = event.isDropItems() && event.getPlayer().getGameMode() != GameMode.CREATIVE;
        removeOverlayAt(block, dropItems);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeOverlayAt(block, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeOverlayAt(block, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        if (!toBlock.isEmpty() && toBlock.isPassable()) {
            removeOverlayAt(toBlock, true);
        }
    }

    private BlockDisplay findOverlay(Block block) {
        return findAtBlock(block, BlockDisplay.class, this::isOverlayEntity);
    }

    private Interaction findHitbox(Block block) {
        return findAtBlock(block, Interaction.class, this::isHitboxEntity);
    }

    private <T extends Entity> T findAtBlock(Block block, Class<T> type, java.util.function.Predicate<Entity> predicate) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.0, 1.0, 1.0, predicate)) {
            Location loc = entity.getLocation();
            if (loc.getBlockX() == block.getX()
                    && loc.getBlockY() == block.getY()
                    && loc.getBlockZ() == block.getZ()) {
                return type.cast(entity);
            }
        }
        return null;
    }

    private boolean isOverlayEntity(Entity entity) {
        return entity instanceof BlockDisplay display
                && display.getPersistentDataContainer().has(overlayKey, PersistentDataType.BYTE);
    }

    private boolean isHitboxEntity(Entity entity) {
        return entity instanceof Interaction interaction
                && interaction.getPersistentDataContainer().has(hitboxKey, PersistentDataType.BYTE);
    }

    private void removeOverlayAt(Block block, boolean dropItems) {
        BlockDisplay existing = findOverlay(block);
        if (existing != null) {
            removeOverlayEntity(existing, block, dropItems);
        }
        Interaction hitbox = findHitbox(block);
        if (hitbox != null) {
            hitbox.remove();
        }
    }

    private void removeOverlayEntity(BlockDisplay display, Block block, boolean dropItems) {
        if (dropItems) {
            dropOverlayItems(display, block);
        }
        display.remove();
    }

    private void dropOverlayItems(BlockDisplay display, Block block) {
        BlockData data = display.getBlock();
        Material material = data.getMaterial();
        if (!isOverlayMaterial(material)) return;
        int amount = (data instanceof Snow snow) ? snow.getLayers() : 1;
        block.getWorld().dropItemNaturally(
                block.getLocation().add(0.5, 0.5, 0.5),
                new ItemStack(material, amount)
        );
    }
}
