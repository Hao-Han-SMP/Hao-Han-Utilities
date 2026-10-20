package vn.haohan.utilities.feature.dummy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import vn.haohan.utilities.core.MessageService;
import vn.haohan.utilities.feature.dummy.StrawDummyItem.DummyArmorSlot;
import vn.haohan.utilities.feature.dummy.StrawDummyItem.DummyArmorTier;
import vn.haohan.utilities.core.ProtectionService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dummy's hitbox is an invisible, immovable {@link Mannequin}: vanilla computes every PvP mechanic
 * (weapon/enchant/effects/crit/cooldown/armor/toughness/protection/i-frames/projectiles/DoT/potions).
 * This listener only mirrors the results onto the visual model and keeps the Mannequin alive.
 */
public final class DummyListener implements Listener {
    private final DummyManager dummyManager;
    private final ProtectionService protectionService;
    private final MessageService messageService;
    /** Player -> tick in which we consumed a right-click on a dummy (to swallow the follow-up USE_ITEM). */
    private final Map<UUID, Integer> consumedClickTick = new ConcurrentHashMap<>();

    public DummyListener(
            DummyManager dummyManager,
            ProtectionService protectionService,
            MessageService messageService
    ) {
        this.dummyManager = dummyManager;
        this.protectionService = protectionService;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceDummy(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !dummyManager.getDummyItem().isStrawDummy(item)) {
            return;
        }

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        event.setCancelled(true);

        Block targetBlock = clickedBlock.getRelative(event.getBlockFace());
        if (!targetBlock.getType().isAir() && !targetBlock.isLiquid()) {
            return;
        }

        if (!protectionService.canBreakBlock(player, targetBlock)) {
            messageService.send(player, "dummy-no-permission");
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        Location spawnLoc = targetBlock.getLocation().add(0.5, 0.0, 0.5);
        float yaw = (player.getLocation().getYaw() + 180.0F) % 360.0F;
        spawnLoc.setYaw(yaw);

        DummyInstance dummy = dummyManager.spawnDummy(spawnLoc, player);
        if (dummy != null) {
            spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_ARMOR_STAND_PLACE, 1.0F, 0.9F);
            spawnLoc.getWorld().playSound(spawnLoc, Sound.BLOCK_WOOD_PLACE, 1.0F, 1.0F);
            spawnLoc.getWorld().spawnParticle(
                    Particle.BLOCK,
                    spawnLoc.clone().add(0, 0.5, 0),
                    15,
                    0.25, 0.25, 0.25,
                    Material.HAY_BLOCK.createBlockData()
            );

            messageService.send(player, "dummy-placed");
        }
    }

    /** Shears left-click = take the dummy down. Nothing else removes it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onShearDummy(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.SHEARS) return;
        DummyInstance dummy = dummyManager.getByEntity(event.getEntity());
        if (dummy == null) return;
        event.setCancelled(true);
        shearDummy(player, dummy);
    }

    /** Vanilla already computed the final damage; just visualise it and keep the Mannequin topped up. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDummyDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mannequin mannequin)) return;
        DummyInstance dummy = dummyManager.getByEntity(mannequin);
        if (dummy == null) return;

        double finalDamage = event.getFinalDamage();
        Entity damager = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        boolean isCrit = event instanceof EntityDamageByEntityEvent byEntity && byEntity.isCritical();
        boolean isProjectile = damager instanceof Projectile;

        // Refill after vanilla applies the hit (i-frames / lastHurt stay vanilla)
        Bukkit.getScheduler().runTask(dummyManager.getPlugin(), () -> {
            if (mannequin.isValid()) {
                mannequin.setHealth(maxHealth(mannequin));
                mannequin.setArrowsInBody(0);
            }
        });
        if (finalDamage <= 0.0) return;

        Location loc = dummy.getBaseLocation();
        Vector impactDir;
        if (damager instanceof Projectile p && p.getVelocity().lengthSquared() > 0.001) {
            impactDir = p.getVelocity();
        } else if (damager != null) {
            impactDir = loc.toVector().subtract(damager.getLocation().toVector());
        } else {
            impactDir = new Vector(); // DoT / magic: random wobble direction
        }

        dummy.triggerHit(impactDir, finalDamage);
        dummy.triggerRedstoneOutput(finalDamage);
        dummyManager.getDamageIndicatorService().spawnIndicator(loc, finalDamage, isCrit, isProjectile);

        if (damager != null) {
            loc.getWorld().playSound(loc, Sound.BLOCK_GRASS_HIT, 1.0F, 1.1F);
            loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0, 1.2, 0), 10,
                    0.2, 0.3, 0.2, Material.HAY_BLOCK.createBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDummyHealed(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Mannequin m) || event.getAmount() <= 0.0) return;
        DummyInstance dummy = dummyManager.getByEntity(m);
        if (dummy != null) {
            dummyManager.getDamageIndicatorService().spawnHeal(dummy.getBaseLocation(), event.getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDummyEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Mannequin m)) return;
        PotionEffect effect = event.getNewEffect();
        if (effect == null) return; // removal/expiry
        DummyInstance dummy = dummyManager.getByEntity(m);
        if (dummy == null) return;
        Component text = Component.text("✦ ", NamedTextColor.AQUA)
                .append(Component.translatable(effect.getType().translationKey(), NamedTextColor.AQUA))
                .append(Component.text(" " + (effect.getAmplifier() + 1), NamedTextColor.AQUA));
        dummyManager.getDamageIndicatorService().spawnText(dummy.getBaseLocation(), text, false);
    }

    /** The Mannequin must never die; vanilla can still overkill through 1024 HP (e.g. /kill, void). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDummyDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Mannequin m && dummyManager.getByEntity(m) != null) {
            event.setCancelled(true);
            m.setHealth(maxHealth(m));
        }
    }

    private static double maxHealth(LivingEntity e) {
        var attr = e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : DummyInstance.MANNEQUIN_HP;
    }

    private void shearDummy(Player player, DummyInstance dummy) {
        if (!protectionService.canBreakBlock(player, dummy.getBaseLocation().getBlock())) {
            messageService.send(player, "dummy-no-permission");
            return;
        }
        Location loc = dummy.getBaseLocation();
        dummyManager.removeDummy(dummy, true);
        loc.getWorld().playSound(loc, Sound.ENTITY_SHEEP_SHEAR, 1.0F, 1.0F);
        loc.getWorld().playSound(loc, Sound.BLOCK_WOOD_BREAK, 1.0F, 1.0F);
        loc.getWorld().playSound(loc, Sound.BLOCK_GRASS_BREAK, 1.0F, 1.0F);
        loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0, 1.0, 0), 25,
                0.3, 0.5, 0.3, Material.HAY_BLOCK.createBlockData());
        messageService.send(player, "dummy-removed");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtDummy(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        DummyInstance dummy = dummyManager.getByEntity(event.getRightClicked());
        if (dummy == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        Material heldType = heldItem.getType();
        DummyArmorSlot armorSlot = DummyArmorSlot.fromMaterial(heldType);
        DummyArmorTier armorTier = DummyArmorTier.fromMaterial(heldType);

        boolean handled = heldType == Material.SHEARS
                || (armorSlot != null && armorTier != null)
                || (heldType == Material.AIR && dummy.hasAnyArmor());
        if (!handled) {
            return; // bow, trident, pearls, food, charge items... -> vanilla use proceeds untouched
        }

        event.setCancelled(true);
        // Mannequin interaction returns PASS client-side, so the client also sends USE_ITEM this tick: swallow it
        consumedClickTick.put(player.getUniqueId(), Bukkit.getCurrentTick());

        if (heldType == Material.SHEARS) {
            shearDummy(player, dummy);
            return;
        }

        if (!protectionService.canBreakBlock(player, dummy.getBaseLocation().getBlock())) {
            messageService.send(player, "dummy-no-permission");
            return;
        }

        Location loc = dummy.getBaseLocation();

        // Holding Armor: Equip on dummy WITHOUT consuming player's armor item
        if (armorSlot != null && armorTier != null) {
            dummy.equipArmor(
                    armorSlot,
                    armorTier,
                    heldItem,
                    dummyManager.getDummyIdKey(),
                    dummyManager.getRoleKey(),
                    dummyManager.getArmorDataKey()
            );
            loc.getWorld().playSound(loc, armorTier.equipSound(), 1.0F, 1.0F);
            messageService.send(player, "dummy-armor-equipped", Map.of(
                    "slot", armorSlot.id(),
                    "tier", armorTier.id()
            ));
            return;
        }

        // Empty Hand: Unequip armor at clicked height (like Armor Stand)
        double clickY = event.getClickedPosition().getY();
        DummyArmorSlot removedSlot = dummy.unequipArmorAt(clickY, dummyManager.getArmorDataKey());
        if (removedSlot != null) {
            loc.getWorld().playSound(loc, Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0F, 0.85F);
            messageService.send(player, "dummy-armor-removed", Map.of("slot", removedSlot.id()));
        }
    }

    /** Swallow the USE_ITEM that follows a consumed dummy click (e.g. so equipping armor doesn't also wear it). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFollowUpUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Integer tick = consumedClickTick.get(event.getPlayer().getUniqueId());
        if (tick != null && tick == Bukkit.getCurrentTick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        if (dummyManager.getByEntity(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        Block above = broken.getRelative(0, 1, 0);

        for (DummyInstance dummy : dummyManager.getAllDummies()) {
            if (dummy.getBaseLocation().getBlock().equals(above)) {
                dummyManager.removeDummy(dummy, true);
                break;
            }
        }
    }

    /**
     * Handles Redstone interaction with Dummy:
     * 1. Redstone Comparator output: When placed facing away from the block underneath the dummy,
     *    it reads out the power level of the hit (1-15) or defense level.
     * 2. Redstone Power input: When redstone power is supplied to the dummy's base block,
     *    the dummy triggers a wobble training reaction or clears active negative potion effects.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onRedstoneUpdate(org.bukkit.event.block.BlockRedstoneEvent event) {
        Block block = event.getBlock();

        // 1. Comparator reading from the dummy's base/support block
        if (block.getType() == Material.COMPARATOR && block.getBlockData() instanceof org.bukkit.block.data.type.Comparator comp) {
            org.bukkit.block.BlockFace facing = comp.getFacing();
            Block measuredBlock = block.getRelative(facing.getOppositeFace());
            DummyInstance dummy = dummyManager.getDummyAtBlock(measuredBlock);
            if (dummy == null) {
                dummy = dummyManager.getDummyAbove(measuredBlock);
            }

            if (dummy != null) {
                int outputPower = dummy.getLastOutputPower();
                if (outputPower <= 0 && dummy.hasAnyArmor()) {
                    // Static reading: number of armor pieces (1-4 -> scaled 3, 7, 11, 15)
                    int armorPieces = dummy.getTotalArmorDefense();
                    outputPower = Math.min(15, (int) Math.round((armorPieces / 20.0) * 15.0));
                }
                event.setNewCurrent(Math.max(event.getNewCurrent(), outputPower));
            }
            return;
        }

        // 2. Redstone power input into the dummy's base or support block
        if (event.getOldCurrent() == 0 && event.getNewCurrent() > 0) {
            DummyInstance dummy = dummyManager.getDummyAtBlock(block);
            if (dummy == null) {
                dummy = dummyManager.getDummyAbove(block);
            }

            if (dummy != null) {
                // Trigger a training wobble when pulsed with Redstone
                dummy.triggerHit(new Vector(0, 0, 0), 3.0);
                Location loc = dummy.getBaseLocation();
                loc.getWorld().playSound(loc, Sound.BLOCK_WOOD_STEP, 0.8F, 1.2F);
                loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.2, 0), 6,
                        0.2, 0.1, 0.2, 0.0, new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0F));
            }
        }
    }
}
