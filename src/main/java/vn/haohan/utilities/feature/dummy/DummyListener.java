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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !dummyManager.getDummyItem().isStrawDummy(item)) {
            return;
        }

        // Cancel event to prevent vanilla from placing an ordinary ArmorStand
        event.setCancelled(true);

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

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
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Minecart minecart)) return;

        Player player = event.getPlayer();
        ItemStack heldItem = event.getHand() == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        // 1. Placing dummy on minecart
        if (dummyManager.getDummyItem().isStrawDummy(heldItem)) {
            event.setCancelled(true);
            consumedClickTick.put(player.getUniqueId(), Bukkit.getCurrentTick());

            if (dummyManager.isDummyMinecart(minecart) || !minecart.isEmpty()) {
                return;
            }

            if (!protectionService.canBreakBlock(player, minecart.getLocation().getBlock())) {
                messageService.send(player, "dummy-no-permission");
                return;
            }

            if (player.getGameMode() != GameMode.CREATIVE) {
                heldItem.setAmount(heldItem.getAmount() - 1);
            }

            DummyInstance dummy = dummyManager.spawnDummyOnMinecart(minecart, player);
            if (dummy != null) {
                Location loc = minecart.getLocation();
                loc.getWorld().playSound(loc, Sound.ENTITY_ARMOR_STAND_PLACE, 1.0F, 0.9F);
                loc.getWorld().playSound(loc, Sound.BLOCK_WOOD_PLACE, 1.0F, 1.0F);
                loc.getWorld().spawnParticle(
                        Particle.BLOCK,
                        loc.clone().add(0, 0.5, 0),
                        15,
                        0.25, 0.25, 0.25,
                        Material.HAY_BLOCK.createBlockData()
                );
            }
            return;
        }

        // 2. Adjusting speed or shearing on dummy minecart
        DummyInstance dummy = dummyManager.getDummyByMinecart(minecart);
        if (dummy != null) {
            if (heldItem.getType() == Material.SHEARS) {
                event.setCancelled(true);
                consumedClickTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
                shearDummy(player, dummy);
                return;
            }
            if (player.isSneaking() && heldItem.getType() == Material.AIR) {
                event.setCancelled(true);
                consumedClickTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
                cycleMinecartSpeed(player, minecart);
                return;
            }
        }
    }

    private static final double[] SPEED_TIERS = {0.2, 0.4, 0.6, 0.8, 1.0};
    private static final String[] SPEED_LABELS = {"Chậm", "Bình thường", "Nhanh", "Rất nhanh", "Siêu tốc"};
    private static final float[] SPEED_PITCHES = {0.75F, 1.0F, 1.25F, 1.5F, 1.85F};

    private void cycleMinecartSpeed(Player player, Minecart minecart) {
        double current = minecart.getMaxSpeed();
        int nextIndex = 0;
        for (int i = 0; i < SPEED_TIERS.length; i++) {
            if (Math.abs(current - SPEED_TIERS[i]) < 0.05) {
                nextIndex = (i + 1) % SPEED_TIERS.length;
                break;
            }
        }
        double nextSpeed = SPEED_TIERS[nextIndex];
        String label = SPEED_LABELS[nextIndex];
        float pitch = SPEED_PITCHES[nextIndex];

        minecart.setMaxSpeed(nextSpeed);
        minecart.setSlowWhenEmpty(false);

        player.playSound(minecart.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0F, pitch);
        messageService.sendActionBarKey(player, "dummy-speed-changed",
                "<green>Tốc độ xe mỏ: <yellow><speed></yellow> (<label>)</green>",
                Map.of(
                        "speed", String.format("%.1f", nextSpeed),
                        "label", label
                ));
    }

    /**
     * Prevents projectiles from damaging or breaking the minecart when a dummy is on it,
     * and forwards the hit to the dummy so target practice functions seamlessly.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;
        DummyInstance dummy = dummyManager.getDummyByMinecart(minecart);
        if (dummy == null) return;

        Entity attacker = event.getAttacker();
        if (attacker instanceof Projectile projectile) {
            event.setCancelled(true);
            handleMinecartProjectileHit(dummy, projectile);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;
        DummyInstance dummy = dummyManager.getDummyByMinecart(minecart);
        if (dummy == null) return;

        Entity attacker = event.getAttacker();
        if (attacker instanceof Projectile) {
            event.setCancelled(true);
            return;
        }

        dummyManager.removeDummy(dummy, true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity hitEntity = event.getHitEntity();
        if (hitEntity != null) {
            DummyInstance dummy = dummyManager.getByEntity(hitEntity);
            if (dummy != null) {
                // If the projectile directly hit the stationary dummy's primary hitbox (Mannequin),
                // let vanilla's native LivingEntity damage pipeline process it normally.
                if (!dummy.isOnMinecart() && hitEntity.equals(dummy.getMannequin())) {
                    return;
                }

                // If it hit any part of a minecart dummy (ArmorStand, Minecart, Display) or other parts:
                event.setCancelled(true);
                handleMinecartProjectileHit(dummy, event.getEntity());
                return;
            }
        }

        // When a minecart dummy moves fast, arrows often hit the rail or floor block right next to it
        // due to discrete tick stepping. Check if a moving dummy minecart is right at the impact block:
        Block hitBlock = event.getHitBlock();
        if (hitBlock != null) {
            Location blockCenter = hitBlock.getLocation().add(0.5, 0.5, 0.5);
            for (DummyInstance dummy : dummyManager.getAllDummies()) {
                if (!dummy.isOnMinecart()) continue;
                Minecart cart = dummy.getMinecart();
                if (cart == null || !cart.isValid()) continue;

                Location cartLoc = cart.getLocation();
                if (!cartLoc.getWorld().equals(blockCenter.getWorld())) continue;

                double dx = blockCenter.getX() - cartLoc.getX();
                double dz = blockCenter.getZ() - cartLoc.getZ();
                double horizontalDistSq = dx * dx + dz * dz;
                double dy = blockCenter.getY() - cartLoc.getY();

                // Within 2.0 blocks horizontal radius and vertical range -0.5 to 2.5
                if (horizontalDistSq <= 2.0 * 2.0 && dy >= -0.5 && dy <= 2.5) {
                    event.setCancelled(true);
                    handleMinecartProjectileHit(dummy, event.getEntity());
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMinecartDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        DummyInstance dummy = dummyManager.getByEntity(entity);
        if (dummy == null) return;

        if (dummy.isOnMinecart()) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Projectile projectile) {
                handleMinecartProjectileHit(dummy, projectile);
            } else if (event.getDamager() instanceof Player player) {
                if (player.getInventory().getItemInMainHand().getType() == Material.SHEARS) {
                    shearDummy(player, dummy);
                } else {
                    handleMinecartMeleeHit(dummy, player);
                }
            }
            return;
        }

        if (event.getDamager() instanceof Projectile projectile) {
            if (!entity.equals(dummy.getMannequin())) {
                event.setCancelled(true);
                handleMinecartProjectileHit(dummy, projectile);
            }
        }
    }

    private void handleMinecartProjectileHit(DummyInstance dummy, Projectile projectile) {
        if (projectile == null || !projectile.isValid()) return;

        double damage = 4.0;
        boolean isCrit = false;
        if (projectile instanceof AbstractArrow arrow) {
            damage = Math.max(1.0, arrow.getDamage() * projectile.getVelocity().length());
            isCrit = arrow.isCritical();
        } else if (projectile instanceof org.bukkit.entity.Trident) {
            damage = 9.0;
        }

        Location loc = dummy.getBaseLocation();
        Vector impactDir = projectile.getVelocity();

        dummy.triggerHit(impactDir, damage);
        dummy.triggerRedstoneOutput(damage);
        dummyManager.getDamageIndicatorService().spawnIndicator(loc, damage, isCrit, true);

        loc.getWorld().playSound(loc, Sound.BLOCK_GRASS_HIT, 1.0F, 1.1F);
        loc.getWorld().spawnParticle(
                Particle.BLOCK,
                loc.clone().add(0, 1.2, 0),
                10,
                0.2, 0.3, 0.2,
                Material.HAY_BLOCK.createBlockData()
        );

        if (projectile.isValid()) {
            projectile.remove();
        }
    }

    private void handleMinecartMeleeHit(DummyInstance dummy, Player player) {
        Location loc = dummy.getBaseLocation();
        Vector impactDir = loc.toVector().subtract(player.getLocation().toVector());

        double damage = 1.0;
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (attr != null) {
            damage = Math.max(1.0, attr.getValue());
        }

        dummy.triggerHit(impactDir, damage);
        dummy.triggerRedstoneOutput(damage);
        dummyManager.getDamageIndicatorService().spawnIndicator(loc, damage, false, false);

        loc.getWorld().playSound(loc, Sound.BLOCK_GRASS_HIT, 1.0F, 1.1F);
        loc.getWorld().spawnParticle(
                Particle.BLOCK,
                loc.clone().add(0, 1.2, 0),
                10,
                0.2, 0.3, 0.2,
                Material.HAY_BLOCK.createBlockData()
        );
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        switch (event.getCause()) {
            case SUFFOCATION, DROWNING, FALL, CRAMMING, CONTACT, FLY_INTO_WALL -> {
                if (dummyManager.getByEntity(event.getEntity()) != null) {
                    event.setCancelled(true);
                }
            }
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof Minecart minecart && dummyManager.isDummyMinecart(minecart)) {
            DummyInstance dummy = dummyManager.getDummyByMinecart(minecart);
            if (dummy != null && !dummy.isPart(event.getEntered())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Minecart minecart && dummyManager.isDummyMinecart(minecart)) {
            DummyInstance dummy = dummyManager.getDummyByMinecart(minecart);
            if (dummy != null && dummy.isPart(event.getExited())) {
                event.setCancelled(true);
            }
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
        if (event.getEntity() instanceof Mannequin m) {
            DummyInstance dummy = dummyManager.getByEntity(m);
            if (dummy != null) {
                if (m.getLocation().getY() < m.getWorld().getMinHeight() - 64) {
                    dummyManager.removeDummy(dummy, false);
                    return;
                }
                event.setCancelled(true);
                m.setHealth(maxHealth(m));
            }
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

        // On a moving minecart dummy: ONLY Shears (to remove) or Sneak + Empty hand (to cycle speed)
        // are intercepted. All other items (especially Bow, Crossbow, Trident, Food) MUST NOT be cancelled
        // so the player can draw the bow or use items completely normally while aiming at the dummy!
        if (dummy.isOnMinecart()) {
            if (heldType == Material.SHEARS) {
                event.setCancelled(true);
                consumedClickTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
                shearDummy(player, dummy);
                return;
            }
            if (player.isSneaking() && heldType == Material.AIR) {
                event.setCancelled(true);
                consumedClickTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
                cycleMinecartSpeed(player, dummy.getMinecart());
                return;
            }
            return;
        }

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
            messageService.sendActionBarKey(player, "dummy-armor-equipped",
                    "<green>Bù nhìn đã mặc giáp <white><tier> <slot></white></green>",
                    Map.of(
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
            messageService.sendActionBarKey(player, "dummy-armor-removed",
                    "<yellow>Đã gỡ giáp <white><slot></white> khỏi bù nhìn</yellow>",
                    Map.of("slot", removedSlot.id()));
        }
    }

    /** Swallow the USE_ITEM that follows a consumed dummy click (e.g. so equipping armor doesn't also wear it). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFollowUpUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Integer tick = consumedClickTick.remove(event.getPlayer().getUniqueId());
        if (tick != null && tick == Bukkit.getCurrentTick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        consumedClickTick.remove(event.getPlayer().getUniqueId());
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
     * Handles Redstone input into the Dummy:
     * When any redstone signal (wire, lever, button, repeater, torch, observer, etc.) powers
     * the dummy's base block or support block, the dummy triggers a training wobble.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        DummyInstance dummy = dummyManager.getDummyByBlock(block);
        if (dummy == null) return;

        Block baseBlock = dummy.getBaseLocation().getBlock();
        Block supportBlock = baseBlock.getRelative(org.bukkit.block.BlockFace.DOWN);

        boolean powered = isBlockPowered(supportBlock) || isBlockPowered(baseBlock);
        dummy.handleRedstoneInput(powered);
    }

    private static boolean isBlockPowered(Block block) {
        if (block == null) return false;
        return block.isBlockPowered() || block.isBlockIndirectlyPowered() || block.getBlockPower() > 0;
    }

    /**
     * Handles Redstone Wire output from the Dummy:
     * When redstone wire recalculates near a dummy while the dummy has an active hit pulse,
     * maintain the wire's power level.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRedstoneWire(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.REDSTONE_WIRE) return;

        DummyInstance dummy = dummyManager.getDummyAdjacentTo(block);
        if (dummy != null && dummy.getLastOutputPower() > 0) {
            event.setNewCurrent(Math.max(event.getNewCurrent(), dummy.getLastOutputPower()));
        }
    }
}
