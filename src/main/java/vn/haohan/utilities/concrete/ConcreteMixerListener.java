package vn.haohan.utilities.concrete;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import vn.haohan.utilities.protection.ProtectionService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ConcreteMixerListener implements Listener {
    private final JavaPlugin plugin;
    private final ProtectionService protection;
    private final Map<UUID, BukkitTask> transformations = new HashMap<>();

    public ConcreteMixerListener(JavaPlugin plugin, ProtectionService protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (!enabled() || !allowed(event.getPlayer())
                || !isConcretePowder(event.getItemDrop().getItemStack().getType())) return;
        transformConcretePowder(event.getItemDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        Item aggregate = event.getTarget();
        Item piece = event.getEntity();
        if (cancelExistingTransformation(aggregate) || cancelExistingTransformation(piece)) {
            if (aggregate.getThrower() == null) aggregate.setThrower(piece.getThrower());
            transformConcretePowder(aggregate);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !enabled() || !allowed(player)) return;
        if (event.getBlock().getType() != Material.CAULDRON
                || event.getNewState().getType() != Material.WATER_CAULDRON) return;

        event.getBlock().getWorld().getNearbyEntities(event.getBlock().getBoundingBox()).stream()
                .filter(Item.class::isInstance).map(Item.class::cast)
                .filter(item -> isConcretePowder(item.getItemStack().getType()))
                .filter(item -> !transformations.containsKey(item.getUniqueId())).limit(64)
                .forEach(this::transformConcretePowder);
    }

    private void transformConcretePowder(Item item) {
        if (transformations.containsKey(item.getUniqueId())) return;
        int[] outside = {0};
        int[] inside = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!item.isValid() || item.isDead()) { cancel(item); return; }
            Block cauldron = item.getLocation().getBlock();
            if (cauldron.getType() != Material.WATER_CAULDRON) {
                if (++outside[0] > 20) cancel(item);
                return;
            }
            inside[0]++;
            if (lowerWater() && item.getThrower() != null
                    && plugin.getServer().getEntity(item.getThrower()) instanceof Player player
                    && !protection.canBreakBlock(player, cauldron)) { cancel(item); return; }
            if (inside[0] == 1) { item.setPickupDelay(40); splashSound(item.getLocation()); }
            if (inside[0] < 15) { splashParticles(cauldron); return; }

            cancel(item);
            Material concrete = concreteFor(item.getItemStack().getType());
            if (concrete == null) return;
            ItemStack stack = item.getItemStack();
            stack.setType(concrete);
            item.setItemStack(stack);
            item.setVelocity(new Vector(0, 0.3, 0));
            item.setPickupDelay(10);
            transformEffects(cauldron);
            if (lowerWater()) drain(cauldron);
        }, 2L, 2L);
        transformations.put(item.getUniqueId(), task);
    }

    private boolean cancelExistingTransformation(Item item) { return transformations.remove(item.getUniqueId()) != null; }

    private void cancel(Item item) {
        BukkitTask task = transformations.remove(item.getUniqueId());
        if (task != null) task.cancel();
    }

    private boolean allowed(Player player) {
        return !plugin.getConfig().getBoolean("concrete-mixer.require-permission", false)
                || player.hasPermission("concretemixer.cauldrons")
                || player.hasPermission("haohanutilities.concrete-mixer");
    }

    private boolean enabled() { return plugin.getConfig().getBoolean("concrete-mixer.enabled", true); }

    private boolean lowerWater() { return plugin.getConfig().getBoolean("concrete-mixer.lower-water-level", true); }

    private boolean effectsEnabled(String path, boolean fallback) {
        return plugin.getConfig().getBoolean("concrete-mixer.effects.enabled", true)
                && plugin.getConfig().getBoolean("concrete-mixer.effects." + path, fallback);
    }

    private void splashSound(Location location) {
        if (effectsEnabled("splash.sound.enabled", true))
            playSound(location, "concrete-mixer.effects.splash.sound", Sound.ENTITY_GENERIC_SPLASH, 0.75f, 1.0f);
    }

    private void splashParticles(Block cauldron) {
        if (!effectsEnabled("splash.particles.enabled", true)) return;
        Levelled level = (Levelled) cauldron.getBlockData();
        double height = 0.9 - (0.1875 * (3 - level.getLevel()));
        cauldron.getWorld().spawnParticle(Particle.SPLASH, cauldron.getX() + 0.5,
                cauldron.getY() + height, cauldron.getZ() + 0.5, 8, 0.15, 0.05, 0.15);
    }

    private void transformEffects(Block cauldron) {
        if (effectsEnabled("transform.particles.enabled", true))
            cauldron.getWorld().spawnParticle(Particle.POOF, cauldron.getX() + 0.5,
                    cauldron.getY() + 1.0, cauldron.getZ() + 0.5, 3, 0.1, 0.0, 0.1, 0.03);
        if (effectsEnabled("transform.sound.enabled", true))
            playSound(cauldron.getLocation(), "concrete-mixer.effects.transform.sound",
                    Sound.BLOCK_FIRE_EXTINGUISH, 0.65f, 1.25f);
    }

    private void playSound(Location location, String path, Sound fallback, float defaultVolume, float defaultPitch) {
        Sound sound = sound(path + ".name", fallback);
        float volume = (float) plugin.getConfig().getDouble(path + ".volume", defaultVolume);
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", defaultPitch);
        location.getWorld().playSound(location, sound, volume,
                ThreadLocalRandom.current().nextFloat(pitch - 0.125f, pitch + 0.125f));
    }

    private Sound sound(String path, Sound fallback) {
        String value = plugin.getConfig().getString(path);
        if (value == null) return fallback;
        try { return Sound.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private void drain(Block cauldron) {
        Levelled level = (Levelled) cauldron.getBlockData();
        if (level.getLevel() <= 1) cauldron.setType(Material.CAULDRON);
        else { level.setLevel(level.getLevel() - 1); cauldron.setBlockData(level); }
    }

    private boolean isConcretePowder(Material material) { return concreteFor(material) != null; }

    private Material concreteFor(Material powder) {
        if (!powder.name().endsWith("_CONCRETE_POWDER")) return null;
        return Material.matchMaterial(powder.name().replace("_POWDER", ""));
    }
}
