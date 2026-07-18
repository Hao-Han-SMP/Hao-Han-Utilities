package vn.haohansmp.utilities.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
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
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("carrying.disable-combat", false)) return;
        if (event.getDamager() instanceof Player player && carryService.isCarrying(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCarriedVisualDamage(EntityDamageByEntityEvent event) {
        Player carrier = carryService.carrierForVisual(event.getEntity()).orElse(null);
        if (carrier == null) {
            return;
        }

        // A carried animal visual still has an entity hitbox and can intercept a
        // melee attack before it reaches the carrier. Keep the snapshot visual
        // intact and forward that hit through Bukkit so armor, PvP protection and
        // other damage listeners are applied to the real player normally.
        event.setCancelled(true);
        if (event.getDamager().getUniqueId().equals(carrier.getUniqueId())) {
            return;
        }
        carrier.damage(event.getDamage(), event.getDamager());
    }

}
