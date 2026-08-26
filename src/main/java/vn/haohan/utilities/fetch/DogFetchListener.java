package vn.haohan.utilities.fetch;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class DogFetchListener implements Listener {
    private final JavaPlugin plugin;
    private final DogFetchService dogFetchService;

    public DogFetchListener(JavaPlugin plugin, DogFetchService dogFetchService) {
        this.plugin = plugin;
        this.dogFetchService = dogFetchService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!dogFetchService.isEnabled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.STICK) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (plugin.getConfig().getBoolean("dog-fetch.require-permission", false)
                && !player.hasPermission("haohanutilities.dogfetch.use")) {
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);

        dogFetchService.refreshCharging(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            dogFetchService.handleSneakRelease(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        dogFetchService.cancelCharging(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!dogFetchService.isEnabled()) return;
        if (dogFetchService.isTrackedStick(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Wolf wolf && dogFetchService.isFetching(wolf)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWolfInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Wolf wolf) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (wolf.isSitting()) {
                    dogFetchService.cancelFetch(wolf);
                    dogFetchService.cancelExcited(wolf);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Wolf wolf) {
            dogFetchService.cancelFetch(wolf);
            dogFetchService.cancelExcited(wolf);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        dogFetchService.cancelCharging(event.getPlayer());
    }
}
