package vn.haohansmp.utilities.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.carry.CarryService;

public final class CarryRestrictionListener implements Listener {
    private final JavaPlugin plugin;
    private final CarryService carryService;

    public CarryRestrictionListener(JavaPlugin plugin, CarryService carryService) {
        this.plugin = plugin;
        this.carryService = carryService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && plugin.getConfig().getBoolean("carrying.disable-sprinting", true)
                && carryService.isCarrying(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player && event.isGliding()
                && plugin.getConfig().getBoolean("carrying.disable-gliding", true)
                && carryService.isCarrying(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying() && plugin.getConfig().getBoolean("carrying.disable-flight", false)
                && carryService.isCarrying(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.getConfig().getBoolean("carrying.disable-teleport", true)
                && carryService.isCarrying(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("carrying.disable-combat", true)) return;
        if (event.getDamager() instanceof Player player && carryService.isCarrying(player.getUniqueId())) {
            event.setCancelled(true);
        } else if (event.getEntity() instanceof Player player && carryService.isCarrying(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
