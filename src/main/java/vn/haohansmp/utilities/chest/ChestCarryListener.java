package vn.haohansmp.utilities.chest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ChestCarryListener implements Listener {
    private final ChestCarryService chestCarry;

    public ChestCarryListener(ChestCarryService chestCarry) {
        this.chestCarry = chestCarry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestPickup(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !event.getPlayer().isSneaking()
                || event.getClickedBlock() == null
                || !chestCarry.isChest(event.getClickedBlock())) {
            return;
        }
        event.setCancelled(true);
        chestCarry.pickup(event.getPlayer(), event.getClickedBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCarriedChestPlace(BlockPlaceEvent event) {
        if (!chestCarry.isCarriedChestItem(event.getItemInHand())) {
            return;
        }
        try {
            chestCarry.restorePlacedState(event.getPlayer(), event.getBlockPlaced(), event.getItemInHand());
        } catch (RuntimeException exception) {
            event.setCancelled(true);
        }
    }
}
