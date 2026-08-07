package vn.haohan.utilities.heart;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CrystalHeartListener implements Listener {
    private final Plugin plugin;
    private final CrystalHeartItem heartItem;

    public CrystalHeartListener(Plugin plugin, CrystalHeartItem heartItem) {
        this.plugin = plugin;
        this.heartItem = heartItem;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("crystal-heart.enabled", true);
    }

    public double getMaxHealthCap() {
        return plugin.getConfig().getDouble("crystal-heart.max-health-cap", 60.0);
    }

    public double getMinHealthCap() {
        return plugin.getConfig().getDouble("crystal-heart.min-health-cap", 20.0);
    }

    public double getHpPerHeart() {
        return plugin.getConfig().getDouble("crystal-heart.hp-per-heart", 2.0);
    }

    public double getDeathHpLoss() {
        return plugin.getConfig().getDouble("crystal-heart.death-hp-loss", 2.0);
    }

    public int getRegenDurationSeconds() {
        return plugin.getConfig().getInt("crystal-heart.regeneration-duration-seconds", 10);
    }

    @SuppressWarnings("deprecation")
    public AttributeInstance getMaxHealthAttribute(Player player) {
        try {
            Attribute attr = Attribute.valueOf("GENERIC_MAX_HEALTH");
            AttributeInstance instance = player.getAttribute(attr);
            if (instance != null) return instance;
        } catch (IllegalArgumentException ignored) {}

        try {
            Attribute attr = Attribute.valueOf("MAX_HEALTH");
            return player.getAttribute(attr);
        } catch (IllegalArgumentException ignored) {}

        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!heartItem.isCrystalHeart(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        AttributeInstance maxHealthAttr = getMaxHealthAttribute(player);
        if (maxHealthAttr == null) return;

        double currentMax = maxHealthAttr.getBaseValue();
        double maxCap = getMaxHealthCap();
        double hpPerHeart = getHpPerHeart();

        if (currentMax >= maxCap) {
            player.sendMessage(ChatColor.RED + "You have reached the maximum health limit (" + (int)(maxCap / 2) + " Hearts)!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double newMax = Math.min(maxCap, currentMax + hpPerHeart);
        maxHealthAttr.setBaseValue(newMax);

        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        int regenSeconds = getRegenDurationSeconds();
        if (regenSeconds > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenSeconds * 20, 2), true);
        }

        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 0.7f, 1.0f);
        player.getWorld().spawnParticle(Particle.HEART, loc.add(0, 1.2, 0), 15, 0.5, 0.5, 0.5, 0.1);

        player.sendMessage(ChatColor.GREEN + "You consumed a Crystal Heart! Max health increased to: " +
                ChatColor.RED + (int)(newMax / 2) + " Hearts " + ChatColor.GRAY + "(" + (int)newMax + " HP)");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isEnabled()) return;
        Player player = event.getEntity();

        AttributeInstance maxHealthAttr = getMaxHealthAttribute(player);
        if (maxHealthAttr == null) return;

        double currentMax = maxHealthAttr.getBaseValue();
        double minCap = getMinHealthCap();
        double hpLoss = getDeathHpLoss();

        if (currentMax > minCap) {
            double newMax = Math.max(minCap, currentMax - hpLoss);
            maxHealthAttr.setBaseValue(newMax);
            player.sendMessage(ChatColor.RED + "You died and lost " + (int)(hpLoss / 2) + " Heart(s)! Max health remaining: " +
                    ChatColor.YELLOW + (int)(newMax / 2) + " Hearts " + ChatColor.GRAY + "(" + (int)newMax + " HP)");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();

        AttributeInstance maxHealthAttr = getMaxHealthAttribute(player);
        if (maxHealthAttr == null) return;

        double currentMax = maxHealthAttr.getBaseValue();
        double minCap = getMinHealthCap();
        double maxCap = getMaxHealthCap();

        if (currentMax < minCap) {
            maxHealthAttr.setBaseValue(minCap);
        } else if (currentMax > maxCap) {
            maxHealthAttr.setBaseValue(maxCap);
        }
    }
}
