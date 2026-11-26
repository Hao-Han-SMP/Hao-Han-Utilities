package vn.haohan.utilities.feature.tweaks;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Segmentable;
import org.bukkit.block.data.type.FlowerBed;
import org.bukkit.block.data.type.Snow;
import org.bukkit.enchantments.Enchantment;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BlockOverlayListener implements Listener {

    public enum PlacementAction {
        IGNORE,
        PLACE_NEW,
        STACK_OVERLAY
    }

    private record HitProgress(UUID playerId, int hits, long lastHitAtMs) {
    }

    private static final double FULL_CUBE_EPSILON = 0.999;
    private static final float FLAT_HITBOX_HEIGHT = 0.0625f;
    private static final float SNOW_LAYER_HEIGHT = 0.125f;
    private static final long HIT_RESET_MS = 2000L;

    private final Plugin plugin;
    private final ProtectionService protection;
    private final NamespacedKey overlayKey;
    private final NamespacedKey hitboxKey;
    private final Map<UUID, HitProgress> breakProgress = new HashMap<>();

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
        String name = material.name();
        return material == Material.SNOW
                || material == Material.PINK_PETALS
                || material == Material.WILDFLOWERS
                || material == Material.LEAF_LITTER
                || material == Material.SCULK_VEIN
                || material == Material.GLOW_LICHEN
                || name.endsWith("_CARPET")
                || name.endsWith("_CORAL")
                || name.endsWith("_CORAL_FAN");
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
        if (material.name().endsWith("_FENCE")) {
            return false;
        }
        return material.isInteractable();
    }

    public static float getHitboxHeight(BlockData data) {
        if (data instanceof Snow snow) {
            return Math.max(SNOW_LAYER_HEIGHT, SNOW_LAYER_HEIGHT * snow.getLayers());
        }
        String name = data.getMaterial().name();
        if (name.endsWith("_CORAL")) {
            return 0.25f;
        }
        if (name.endsWith("_CORAL_FAN")) {
            return 0.15f;
        }
        return FLAT_HITBOX_HEIGHT;
    }

    public static int getRequiredBreakHits(Material overlayMaterial, Material toolMaterial, boolean isCreative, int configuredHits) {
        if (isCreative || configuredHits <= 1) {
            return 1;
        }
        if (overlayMaterial == Material.PINK_PETALS
                || overlayMaterial == Material.WILDFLOWERS
                || overlayMaterial == Material.LEAF_LITTER
                || (overlayMaterial != null && (overlayMaterial.name().endsWith("_CORAL")
                || overlayMaterial.name().endsWith("_CORAL_FAN")))) {
            return 1;
        }
        if (overlayMaterial == Material.SNOW && toolMaterial != null && toolMaterial.name().endsWith("_SHOVEL")) {
            return 1;
        }
        if (overlayMaterial != null && overlayMaterial.name().endsWith("_CARPET") && toolMaterial == Material.SHEARS) {
            return 1;
        }
        if (overlayMaterial == Material.GLOW_LICHEN
                && (toolMaterial == Material.SHEARS || (toolMaterial != null && toolMaterial.name().endsWith("_AXE")))) {
            return 1;
        }
        if (overlayMaterial == Material.SCULK_VEIN && toolMaterial != null && toolMaterial.name().endsWith("_HOE")) {
            return 1;
        }
        return configuredHits;
    }

    public static Material getDropMaterial(Material overlayMaterial, boolean hasSilkTouch) {
        if (overlayMaterial == Material.SNOW) {
            return hasSilkTouch ? Material.SNOW : Material.SNOWBALL;
        }
        return overlayMaterial;
    }

    public static PlacementAction resolvePlacementAction(
            boolean featureEnabled,
            boolean isOverlayItem,
            boolean isSneaking,
            boolean requireSneak,
            boolean hostIsInteractable,
            BlockFace clickedFace,
            boolean hostIsNonFull,
            boolean vanillaSupported,
            Material existingOverlay,
            int existingCount,
            int maxCount,
            Material newOverlay,
            boolean stackEnabled
    ) {
        if (!featureEnabled || !isOverlayItem || !hostIsNonFull) {
            return PlacementAction.IGNORE;
        }
        if ((requireSneak || hostIsInteractable) && !isSneaking) {
            return PlacementAction.IGNORE;
        }
        if (hostIsInteractable && clickedFace == BlockFace.UP) {
            return PlacementAction.IGNORE;
        }
        if (existingOverlay == null) {
            return vanillaSupported ? PlacementAction.PLACE_NEW : PlacementAction.IGNORE;
        }
        if (existingOverlay == newOverlay && stackEnabled && maxCount > 1 && existingCount < maxCount) {
            return PlacementAction.STACK_OVERLAY;
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
        int existingCount = getStackCount(existing != null ? existing.getBlock() : null);
        int maxCount = getMaxStackCount(existing != null ? existing.getBlock() : null);

        boolean hostInteractable = directClickOnHost && requiresSneakToBypassInteract(targetHost.getType());
        boolean vanillaSupported = existing != null || createInitialBlockData(itemType, player).isSupported(targetHost);
        PlacementAction action = resolvePlacementAction(
                true,
                true,
                player.isSneaking(),
                plugin.getConfig().getBoolean("block-overlay.require-sneak", false),
                hostInteractable,
                event.getBlockFace(),
                true,
                vanillaSupported,
                existingMaterial,
                existingCount,
                maxCount,
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
        if (overlay == null) {
            breakProgress.remove(interaction.getUniqueId());
            interaction.remove();
            return;
        }

        BlockData overlayData = overlay.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        int configuredHits = plugin.getConfig().getInt("block-overlay.hits-to-break", 1);
        int requiredHits = getRequiredBreakHits(
                overlayData.getMaterial(),
                tool.getType(),
                player.getGameMode() == GameMode.CREATIVE,
                configuredHits
        );

        if (requiredHits > 1) {
            long now = System.currentTimeMillis();
            HitProgress prev = breakProgress.get(interaction.getUniqueId());
            int hits = (prev != null && prev.playerId().equals(player.getUniqueId()) && now - prev.lastHitAtMs() <= HIT_RESET_MS)
                    ? prev.hits() + 1
                    : 1;
            if (hits < requiredHits) {
                breakProgress.put(interaction.getUniqueId(), new HitProgress(player.getUniqueId(), hits, now));
                playBreakStepEffects(host, overlayData, false);
                return;
            }
        }

        breakProgress.remove(interaction.getUniqueId());
        boolean dropItems = player.getGameMode() != GameMode.CREATIVE;
        boolean silkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        playBreakStepEffects(host, overlayData, true);
        removeOverlayEntity(overlay, host, dropItems, silkTouch);
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
        Material itemType = heldItem.getType();
        if (!isOverlayMaterial(itemType)) return;

        if (plugin.getConfig().getBoolean("block-overlay.require-permission", false)
                && !player.hasPermission("haohanutilities.blockoverlay.use")) {
            return;
        }

        Block host = interaction.getLocation().getBlock();
        BlockDisplay existing = findOverlay(host);
        if (existing == null) return;
        BlockData existingData = existing.getBlock();
        if (existingData.getMaterial() != itemType) return;
        if (!plugin.getConfig().getBoolean("block-overlay.stack-snow-layers", true)) return;

        int current = getStackCount(existingData);
        int max = getMaxStackCount(existingData);
        if (max <= 1 || current >= max) return;
        if (protection != null && !protection.canBreakBlock(player, host)) return;

        event.setCancelled(true);
        applyPlacement(player, hand, heldItem, host, existing, itemType, PlacementAction.STACK_OVERLAY);
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
        boolean silkTouch = player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH);
        playBreakStepEffects(clicked, existing.getBlock(), true);
        removeOverlayAt(clicked, dropItems, silkTouch);
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
            placedData = createInitialBlockData(newMaterial, player);
            Location displayLoc = new Location(host.getWorld(), host.getX(), host.getY(), host.getZ(), 0f, 0f);
            host.getWorld().spawn(displayLoc, BlockDisplay.class, display -> {
                display.setBlock(placedData);
                display.setPersistent(true);
                display.getPersistentDataContainer().set(overlayKey, PersistentDataType.BYTE, (byte) 1);
            });
            spawnHitbox(host, placedData);
        } else if (action == PlacementAction.STACK_OVERLAY && existing != null) {
            placedData = incrementStack(existing.getBlock());
            if (placedData == null) return;
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

    private void playBreakStepEffects(Block host, BlockData data, boolean isFinalBreak) {
        Location effectLoc = host.getLocation().add(0.5, 0.08, 0.5);
        host.getWorld().spawnParticle(
                Particle.BLOCK,
                effectLoc,
                isFinalBreak ? 16 : 8,
                0.28, 0.04, 0.28,
                0.05,
                data
        );
        host.getWorld().playSound(
                effectLoc,
                isFinalBreak ? data.getSoundGroup().getBreakSound() : data.getSoundGroup().getHitSound(),
                SoundCategory.BLOCKS,
                isFinalBreak ? 1.0f : 0.7f,
                isFinalBreak ? 0.8f : 1.0f
        );
    }

    private BlockData createInitialBlockData(Material material, Player player) {
        BlockData data = Bukkit.createBlockData(material);
        if (data instanceof Directional directional) {
            directional.setFacing(player.getFacing().getOppositeFace());
        }
        if (data instanceof MultipleFacing multiface && multiface.getAllowedFaces().contains(BlockFace.DOWN)) {
            multiface.setFace(BlockFace.DOWN, true);
        }
        return data;
    }

    private int getStackCount(BlockData data) {
        if (data instanceof Snow snow) return snow.getLayers();
        if (data instanceof FlowerBed bed) return bed.getFlowerAmount();
        if (data instanceof Segmentable seg) return seg.getSegmentAmount();
        return data != null ? 1 : 0;
    }

    private int getMaxStackCount(BlockData data) {
        if (data instanceof Snow snow) return snow.getMaximumLayers();
        if (data instanceof FlowerBed bed) return bed.getMaximumFlowerAmount();
        if (data instanceof Segmentable seg) return seg.getMaximumSegmentAmount();
        return 1;
    }

    private BlockData incrementStack(BlockData data) {
        BlockData copy = data.clone();
        if (copy instanceof Snow snow) {
            snow.setLayers(snow.getLayers() + 1);
            return snow;
        }
        if (copy instanceof FlowerBed bed) {
            bed.setFlowerAmount(bed.getFlowerAmount() + 1);
            return bed;
        }
        if (copy instanceof Segmentable seg) {
            seg.setSegmentAmount(seg.getSegmentAmount() + 1);
            return seg;
        }
        return null;
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
        boolean silkTouch = event.getPlayer().getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH);
        removeOverlayAt(block, dropItems, silkTouch);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeOverlayAt(block, true, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeOverlayAt(block, true, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        if (!toBlock.isEmpty() && toBlock.isPassable()) {
            removeOverlayAt(toBlock, true, false);
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

    private void removeOverlayAt(Block block, boolean dropItems, boolean silkTouch) {
        BlockDisplay existing = findOverlay(block);
        if (existing != null) {
            removeOverlayEntity(existing, block, dropItems, silkTouch);
        }
        Interaction hitbox = findHitbox(block);
        if (hitbox != null) {
            breakProgress.remove(hitbox.getUniqueId());
            hitbox.remove();
        }
    }

    private void removeOverlayEntity(BlockDisplay display, Block block, boolean dropItems, boolean silkTouch) {
        if (dropItems) {
            dropOverlayItems(display, block, silkTouch);
        }
        display.remove();
    }

    private void dropOverlayItems(BlockDisplay display, Block block, boolean silkTouch) {
        BlockData data = display.getBlock();
        Material material = data.getMaterial();
        if (!isOverlayMaterial(material)) return;
        int amount = getStackCount(data);
        Material dropMaterial = getDropMaterial(material, silkTouch);
        block.getWorld().dropItemNaturally(
                block.getLocation().add(0.5, 0.5, 0.5),
                new ItemStack(dropMaterial, amount)
        );
    }
}
