package vn.haohansmp.utilities.crop;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.protection.ProtectionService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public final class CropHarvestListener implements Listener {
    private static final Map<Material, Material> REPLANT_ITEMS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS
    );

    private final JavaPlugin plugin;
    private final ProtectionService protection;

    public CropHarvestListener(JavaPlugin plugin, ProtectionService protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled("break") || !canUse(event.getPlayer()) || !event.isDropItems()) return;

        Block block = event.getBlock();
        Ageable crop = cropData(block);
        if (crop == null) return;

        BlockData replanted = replantedData(crop);
        Collection<ItemStack> drops = harvestDrops(block, event.getPlayer());
        event.setDropItems(false);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!block.getType().isAir()) return;

            dropHarvest(block, drops);
            block.setBlockData(replanted, false);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || (event.getItem() != null && event.getItem().getType() == Material.BONE_MEAL)
                || !enabled("right-click")
                || !canUse(event.getPlayer())) {
            return;
        }

        Block block = event.getClickedBlock();
        Ageable crop = cropData(block);
        if (crop == null || crop.getAge() < crop.getMaximumAge()) return;
        if (!protection.canBreakBlock(event.getPlayer(), block)) return;

        event.setCancelled(true);
        Collection<ItemStack> drops = harvestDrops(block, event.getPlayer());
        dropHarvest(block, drops);
        block.setBlockData(replantedData(crop), false);
    }

    private Collection<ItemStack> harvestDrops(Block block, Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return new ArrayList<>();
        }

        Collection<ItemStack> drops = new ArrayList<>(
                block.getDrops(player.getInventory().getItemInMainHand(), player)
        );
        consumeReplantItem(drops, REPLANT_ITEMS.get(block.getType()));
        return drops;
    }

    private static void consumeReplantItem(Collection<ItemStack> drops, Material replantItem) {
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop.getType() != replantItem) continue;

            if (drop.getAmount() == 1) {
                iterator.remove();
            } else {
                drop.setAmount(drop.getAmount() - 1);
            }
            return;
        }
    }

    private static void dropHarvest(Block block, Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    drop
            );
        }
    }

    private static Ageable cropData(Block block) {
        if (block == null || !REPLANT_ITEMS.containsKey(block.getType())) return null;
        if (!(block.getBlockData() instanceof Ageable ageable)) return null;
        return ageable;
    }

    private static BlockData replantedData(Ageable crop) {
        BlockData replanted = crop.clone();
        ((Ageable) replanted).setAge(0);
        return replanted;
    }

    private boolean enabled(String action) {
        return plugin.getConfig().getBoolean("crop-replant.enabled", true)
                && plugin.getConfig().getBoolean("crop-replant." + action, true);
    }

    private static boolean canUse(Player player) {
        return player.hasPermission("haohanutilities.crops.use");
    }
}
