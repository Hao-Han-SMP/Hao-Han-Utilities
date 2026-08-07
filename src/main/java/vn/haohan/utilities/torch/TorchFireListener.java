package vn.haohan.utilities.torch;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class TorchFireListener implements Listener {
    private final Plugin plugin;

    public TorchFireListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("torch-fire.enabled", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        if (attacker.getEquipment() == null) return;
        ItemStack mainHand = attacker.getEquipment().getItemInMainHand();
        if (mainHand.getType() == Material.AIR) return;

        Material type = mainHand.getType();
        int seconds = 0;
        Particle particle = Particle.FLAME;

        if (type == Material.TORCH) {
            seconds = plugin.getConfig().getInt("torch-fire.duration-seconds.torch", 3);
            particle = Particle.FLAME;
        } else if (type == Material.SOUL_TORCH) {
            seconds = plugin.getConfig().getInt("torch-fire.duration-seconds.soul-torch", 4);
            particle = Particle.SOUL_FIRE_FLAME;
        } else if (type == Material.REDSTONE_TORCH) {
            seconds = plugin.getConfig().getInt("torch-fire.duration-seconds.redstone-torch", 1);
            particle = Particle.FLAME;
        }

        if (seconds <= 0) return;

        int ticks = seconds * 20;
        target.setFireTicks(Math.max(target.getFireTicks(), ticks));

        Location loc = target.getLocation().add(0, target.getHeight() / 2.0, 0);
        target.getWorld().spawnParticle(particle, loc, 12, 0.2, 0.4, 0.2, 0.05);
        target.getWorld().playSound(loc, Sound.ITEM_FLINTANDSTEEL_USE, 0.8f, 1.2f);

        if (plugin.getConfig().getBoolean("torch-fire.consume-torch", false) && attacker instanceof Player player) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                mainHand.setAmount(mainHand.getAmount() - 1);
            }
        }
    }
}
