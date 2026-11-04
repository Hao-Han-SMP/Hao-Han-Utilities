package vn.haohan.utilities.feature.tweaks;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import vn.haohan.utilities.core.ProtectionService;

import java.util.List;
import java.util.Locale;

public class InvisibleItemFrameListener implements Listener {

    public enum FrameAction {
        IGNORE,
        PASS_TO_VANILLA,
        MAKE_INVISIBLE,
        MAKE_VISIBLE
    }

    private final Plugin plugin;
    private final ProtectionService protection;

    public InvisibleItemFrameListener(Plugin plugin, ProtectionService protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("invisible-item-frame.enabled", true);
    }

    public Material getRequiredMaterial() {
        String matName = plugin.getConfig().getString("invisible-item-frame.item", "AMETHYST_SHARD");
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : Material.AMETHYST_SHARD;
    }

    public boolean isWorldAllowed(World world) {
        if (world == null) return false;
        String mode = plugin.getConfig().getString("worlds.mode", "BLACKLIST").toUpperCase(Locale.ROOT);
        List<String> list = plugin.getConfig().getStringList("worlds.list");
        boolean listed = list.contains(world.getName());
        return "WHITELIST".equals(mode) ? listed : !listed;
    }

    public static FrameAction resolveAction(
            boolean featureEnabled,
            boolean isCorrectItem,
            boolean isVisible,
            boolean hasItem,
            boolean isSneaking,
            boolean allowToggle,
            boolean emptyFrameRequiresSneak
    ) {
        if (!featureEnabled || !isCorrectItem) {
            return FrameAction.IGNORE;
        }

        if (isVisible) {
            if (!hasItem && emptyFrameRequiresSneak && !isSneaking) {
                return FrameAction.PASS_TO_VANILLA;
            }
            return FrameAction.MAKE_INVISIBLE;
        } else {
            if (allowToggle && isSneaking) {
                return FrameAction.MAKE_VISIBLE;
            }
            return FrameAction.PASS_TO_VANILLA;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack heldItem = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (heldItem.getType().isAir()) return;

        boolean isCorrectItem = heldItem.getType() == getRequiredMaterial();
        ItemStack displayed = frame.getItem();
        boolean hasItem = displayed != null && !displayed.getType().isAir();
        boolean isSneaking = player.isSneaking();
        boolean allowToggle = plugin.getConfig().getBoolean("invisible-item-frame.allow-toggle", true);
        boolean emptyFrameRequiresSneak = plugin.getConfig().getBoolean("invisible-item-frame.empty-frame-requires-sneak", true);

        FrameAction action = resolveAction(
                true,
                isCorrectItem,
                frame.isVisible(),
                hasItem,
                isSneaking,
                allowToggle,
                emptyFrameRequiresSneak
        );

        if (action == FrameAction.IGNORE || action == FrameAction.PASS_TO_VANILLA) {
            return;
        }

        if (plugin.getConfig().getBoolean("invisible-item-frame.require-permission", false)
                && !player.hasPermission("haohanutilities.invisible-frame.use")) {
            return;
        }

        if (!isWorldAllowed(frame.getWorld())) {
            return;
        }

        if (protection != null && !protection.canBreakBlock(player, frame.getLocation().getBlock())) {
            return;
        }

        event.setCancelled(true);

        if (action == FrameAction.MAKE_INVISIBLE) {
            frame.setVisible(false);

            boolean consumeItem = plugin.getConfig().getBoolean("invisible-item-frame.consume-item", true);
            if (consumeItem && player.getGameMode() != GameMode.CREATIVE) {
                heldItem.setAmount(heldItem.getAmount() - 1);
            }

            playEffects(frame, true);
        } else if (action == FrameAction.MAKE_VISIBLE) {
            frame.setVisible(true);
            playEffects(frame, false);
        }
    }

    private void playEffects(ItemFrame frame, boolean invisible) {
        Location loc = frame.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        if (invisible) {
            world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
            world.spawnParticle(
                    Particle.BLOCK,
                    loc,
                    12,
                    0.15, 0.15, 0.15,
                    0.05,
                    Material.AMETHYST_BLOCK.createBlockData()
            );
        } else {
            world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.0F, 1.2F);
            world.spawnParticle(
                    Particle.BLOCK,
                    loc,
                    8,
                    0.15, 0.15, 0.15,
                    0.05,
                    Material.AMETHYST_BLOCK.createBlockData()
            );
        }
    }
}
