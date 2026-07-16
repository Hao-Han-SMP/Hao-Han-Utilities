package vn.haohansmp.utilities.listener;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import vn.haohansmp.utilities.carry.CarryService;

public final class PickupPlaceListener implements Listener {
    private final CarryService carryService;

    public PickupPlaceListener(CarryService carryService) {
        this.carryService = carryService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (carryService.isCarrying(event.getPlayer().getUniqueId())) {
            Block clicked = event.getClickedBlock();
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && clicked != null
                    && carryService.isSoulAnchor(clicked)) {
                return;
            }
            event.setCancelled(true);
            carryService.handlePlacement(event);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !event.getPlayer().getCurrentInput().isSprint()
                || !hasEmptyHands(event.getPlayer().getInventory().getItem(EquipmentSlot.HAND),
                        event.getPlayer().getInventory().getItem(EquipmentSlot.OFF_HAND))) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !carryService.isSupportedPickupBlock(clicked)) {
            return;
        }
        event.setCancelled(true);
        carryService.pickupBlock(event.getPlayer(), clicked);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (carryService.isCarrying(event.getPlayer().getUniqueId())) {
            if (carryService.isSoulAnchorEntity(event.getRightClicked())) {
                return;
            }
            event.setCancelled(true);
            carryService.handleObstructedPlacement(event.getPlayer());
            return;
        }
        if (!event.getPlayer().getCurrentInput().isSprint()
                || !hasEmptyHands(event.getPlayer().getInventory().getItem(EquipmentSlot.HAND),
                        event.getPlayer().getInventory().getItem(EquipmentSlot.OFF_HAND))
                || !carryService.isSupportedPickupEntity(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        carryService.pickupEntity(event.getPlayer(), event.getRightClicked());
    }

    private static boolean hasEmptyHands(ItemStack mainHand, ItemStack offHand) {
        return mainHand.getType().isAir() && offHand.getType().isAir();
    }
}
