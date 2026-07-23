package vn.haohansmp.utilities.food;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class GoldenAppleListener implements Listener {
    private static final int HUNGER_POINTS = 2;
    private static final int ABSORPTION_DURATION_TICKS = 30 * 20;

    private final JavaPlugin plugin;

    public GoldenAppleListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        ItemStack food = event.getItem();
        if (food == null || food.getType() != Material.GOLDEN_APPLE) return;

        int currentFoodLevel = ((Player) event.getEntity()).getFoodLevel();
        event.setFoodLevel(Math.min(20, currentFoodLevel + HUNGER_POINTS));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.GOLDEN_APPLE) return;

        Player player = event.getPlayer();
        PotionEffect previousRegeneration = player.getPotionEffect(PotionEffectType.REGENERATION);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            player.removePotionEffect(PotionEffectType.REGENERATION);
            if (previousRegeneration != null) {
                player.addPotionEffect(previousRegeneration, true);
            }

            PotionEffect absorption = player.getPotionEffect(PotionEffectType.ABSORPTION);
            if (absorption == null) return;

            player.removePotionEffect(PotionEffectType.ABSORPTION);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.ABSORPTION,
                    ABSORPTION_DURATION_TICKS,
                    absorption.getAmplifier(),
                    absorption.isAmbient(),
                    absorption.hasParticles(),
                    absorption.hasIcon()
            ), true);
        });
    }
}
