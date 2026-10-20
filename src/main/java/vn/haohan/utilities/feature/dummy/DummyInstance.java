package vn.haohan.utilities.feature.dummy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.haohan.utilities.feature.dummy.StrawDummyItem.DummyArmorSlot;
import vn.haohan.utilities.feature.dummy.StrawDummyItem.DummyArmorTier;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import vn.haohan.utilities.integration.ModelEngineIntegration;

public final class DummyInstance {
    private static final float WAIST_PIVOT_Y = 0.75F;
    private static final double ARMOR_STAND_NECK_ABOVE_WAIST = 0.65;
    private static final double RESTING_ARM_ROLL = 0.35; // ~20 deg outward to match dummy arms (22.5 deg)

    private final Plugin plugin;
    private final UUID dummyId;
    private final UUID ownerUuid;
    private final Location baseLocation;
    private final Interaction interaction;
    private final ItemDisplay baseDisplay;
    private final ItemDisplay modelDisplay;
    private final ModelEngineIntegration modelEngine;

    private final Map<DummyArmorSlot, DummyArmorTier> equippedArmor = new EnumMap<>(DummyArmorSlot.class);
    private ArmorStand armorStand;
    private Mannequin mannequin;
    static final double MANNEQUIN_HP = 1024.0;
    private static final NamespacedKey HIDDEN_ASSET = NamespacedKey.fromString("haohan:dummy_hidden");

    private BukkitTask wobbleTask;
    private BukkitTask redstoneResetTask;
    private Vector3f tiltAxis;
    private double worldPushX;
    private double worldPushZ;
    private float initialAngle;
    private int animTick;
    private int lastOutputPower = 0;

    public DummyInstance(
            Plugin plugin,
            UUID dummyId,
            UUID ownerUuid,
            Location baseLocation,
            Interaction interaction,
            ItemDisplay baseDisplay,
            ItemDisplay modelDisplay,
            ModelEngineIntegration modelEngine
    ) {
        this.plugin = plugin;
        this.dummyId = dummyId;
        this.ownerUuid = ownerUuid;
        this.baseLocation = baseLocation;
        this.interaction = interaction;
        this.baseDisplay = baseDisplay;
        this.modelDisplay = modelDisplay;
        this.modelEngine = modelEngine;
    }

    public UUID getDummyId() {
        return dummyId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Location getBaseLocation() {
        return baseLocation;
    }

    public Interaction getInteraction() {
        return interaction;
    }

    public ItemDisplay getBaseDisplay() {
        return baseDisplay;
    }

    public ItemDisplay getModelDisplay() {
        return modelDisplay;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public Mannequin getMannequin() {
        return mannequin;
    }

    public boolean hasAnyArmor() {
        return !equippedArmor.isEmpty();
    }

    public int getTotalArmorDefense() {
        int total = 0;
        for (Map.Entry<DummyArmorSlot, DummyArmorTier> entry : equippedArmor.entrySet()) {
            total += entry.getValue().defense(entry.getKey());
        }
        return total;
    }

    public void equipArmor(
            DummyArmorSlot slot,
            DummyArmorTier tier,
            ItemStack armorItem,
            NamespacedKey dummyIdKey,
            NamespacedKey roleKey,
            NamespacedKey armorDataKey
    ) {
        if (slot == null || tier == null || !modelDisplay.isValid()) return;

        equippedArmor.put(slot, tier);
        ItemStack visualCopy = (armorItem != null && armorItem.getType() != Material.AIR)
                ? armorItem.asOne()
                : new ItemStack(tier.defaultMaterial(slot), 1);
        ArmorStand stand = ensureArmorStand(dummyIdKey, roleKey);
        if (stand != null && stand.isValid()) {
            stand.getEquipment().setItem(slot.toEquipmentSlot(), visualCopy);
        }
        Mannequin m = ensureMannequin(dummyIdKey, roleKey);
        if (m != null) {
            m.getEquipment().setItem(slot.toEquipmentSlot(), hiddenCopy(visualCopy, slot.toEquipmentSlot()));
        }

        saveArmorToPdc(armorDataKey);
    }

    /** Same item (attributes, enchants) but pointing at a non-existent equipment asset, so it renders nothing. */
    private static ItemStack hiddenCopy(ItemStack item, EquipmentSlot slot) {
        ItemStack copy = item.clone();
        copy.editMeta(meta -> {
            var eq = meta.getEquippable();
            eq.setSlot(slot);
            eq.setModel(HIDDEN_ASSET);
            meta.setEquippable(eq);
        });
        return copy;
    }

    public Mannequin ensureMannequin(NamespacedKey dummyIdKey, NamespacedKey roleKey) {
        if (mannequin != null && mannequin.isValid()) return mannequin;
        World world = baseLocation.getWorld();
        if (world == null) return null;
        mannequin = world.spawn(baseLocation, Mannequin.class, m -> {
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(dummyIdKey, PersistentDataType.STRING, dummyId.toString());
            pdc.set(roleKey, PersistentDataType.STRING, "mannequin");
        });
        configureMannequin(mannequin);
        return mannequin;
    }

    public void attachRestoredMannequin(Mannequin restored) {
        this.mannequin = restored;
        configureMannequin(restored);
    }

    private void configureMannequin(Mannequin m) {
        m.setInvisible(true);
        m.setSilent(true);
        m.setGravity(false);
        m.setImmovable(true);
        m.setPersistent(true);
        m.setRemoveWhenFarAway(false);
        m.setCollidable(true); // Must be collidable for projectile ray-tracing and hits!
        m.setDescription(null);
        var maxHp = m.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(MANNEQUIN_HP);
        m.setHealth(MANNEQUIN_HP);
        m.teleport(baseLocation);
    }

    public ArmorStand ensureArmorStand(NamespacedKey dummyIdKey, NamespacedKey roleKey) {
        if (armorStand != null && armorStand.isValid()) {
            armorStand.setMarker(true);
            syncArmorStandRotationAndPose(armorStand);
            return armorStand;
        }
        World world = baseLocation.getWorld();
        if (world == null) return null;

        armorStand = world.spawn(baseLocation, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true); // visual only; the Mannequin is the hitbox
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setArms(true);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setPersistent(true);
            syncArmorStandRotationAndPose(stand);

            PersistentDataContainer pdc = stand.getPersistentDataContainer();
            pdc.set(dummyIdKey, PersistentDataType.STRING, dummyId.toString());
            pdc.set(roleKey, PersistentDataType.STRING, "armor_stand");
        });
        return armorStand;
    }

    private void syncArmorStandRotationAndPose(ArmorStand stand) {
        float yaw = modelDisplay.isValid() ? modelDisplay.getLocation().getYaw() : baseLocation.getYaw();
        baseLocation.setYaw(yaw);
        stand.setRotation(yaw, 0.0F);
        stand.setBodyYaw(yaw);
        stand.setHeadPose(EulerAngle.ZERO);
        stand.setBodyPose(EulerAngle.ZERO);
        stand.setLeftArmPose(new EulerAngle(0.0, 0.0, -RESTING_ARM_ROLL));
        stand.setRightArmPose(new EulerAngle(0.0, 0.0, RESTING_ARM_ROLL));
        stand.setLeftLegPose(EulerAngle.ZERO);
        stand.setRightLegPose(EulerAngle.ZERO);
    }

    public DummyArmorSlot unequipArmorAt(double clickOffsetY, NamespacedKey armorDataKey) {
        if (equippedArmor.isEmpty()) return null;

        DummyArmorSlot targetSlot = DummyArmorSlot.fromClickY(clickOffsetY);
        if (!equippedArmor.containsKey(targetSlot)) {
            for (DummyArmorSlot slot : DummyArmorSlot.values()) {
                if (equippedArmor.containsKey(slot)) {
                    targetSlot = slot;
                    break;
                }
            }
        }

        equippedArmor.remove(targetSlot);
        if (armorStand != null && armorStand.isValid()) {
            armorStand.getEquipment().setItem(targetSlot.toEquipmentSlot(), null);
        }
        if (mannequin != null && mannequin.isValid()) {
            mannequin.getEquipment().setItem(targetSlot.toEquipmentSlot(), null);
        }

        saveArmorToPdc(armorDataKey);
        return targetSlot;
    }

    /** Mirror the visible armor stand's armor onto the (hidden) mannequin, e.g. after restart/migration. */
    public void syncMannequinArmor() {
        if (mannequin == null || !mannequin.isValid() || armorStand == null || !armorStand.isValid()) return;
        for (DummyArmorSlot slot : DummyArmorSlot.values()) {
            ItemStack item = armorStand.getEquipment().getItem(slot.toEquipmentSlot());
            mannequin.getEquipment().setItem(slot.toEquipmentSlot(),
                    item == null || item.getType().isAir() ? null : hiddenCopy(item, slot.toEquipmentSlot()));
        }
    }

    public void attachRestoredArmorStand(ArmorStand restoredStand) {
        this.armorStand = restoredStand;
        if (restoredStand == null || !restoredStand.isValid()) return;
        restoredStand.setInvisible(true);
        restoredStand.setMarker(true);
        restoredStand.setGravity(false);
        restoredStand.setBasePlate(false);
        restoredStand.setArms(true);
        restoredStand.setSilent(true);
        restoredStand.setInvulnerable(true);
        restoredStand.teleport(baseLocation);
        syncArmorStandRotationAndPose(restoredStand);

        EntityEquipment equipment = restoredStand.getEquipment();
        if (equipment == null) return;

        for (DummyArmorSlot slot : DummyArmorSlot.values()) {
            ItemStack item = equipment.getItem(slot.toEquipmentSlot());
            if (item != null && item.getType() != Material.AIR) {
                DummyArmorTier tier = DummyArmorTier.fromMaterial(item.getType());
                if (tier != null) {
                    equippedArmor.put(slot, tier);
                }
            }
        }
    }

    private void saveArmorToPdc(NamespacedKey armorDataKey) {
        if (!interaction.isValid() || armorDataKey == null) return;
        if (equippedArmor.isEmpty()) {
            interaction.getPersistentDataContainer().remove(armorDataKey);
            return;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Map.Entry<DummyArmorSlot, DummyArmorTier> entry : equippedArmor.entrySet()) {
            joiner.add(entry.getKey().id() + ":" + entry.getValue().id());
        }
        interaction.getPersistentDataContainer().set(armorDataKey, PersistentDataType.STRING, joiner.toString());
    }

    public boolean isValid() {
        return interaction.isValid() && modelDisplay.isValid() && (baseDisplay == null || baseDisplay.isValid());
    }

    public void triggerHit(Vector impactDir, double damage) {
        if (!modelDisplay.isValid()) return;

        double dx = impactDir.getX();
        double dz = impactDir.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            double randAngle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
            dx = Math.cos(randAngle);
            dz = Math.sin(randAngle);
        } else {
            dx /= len;
            dz /= len;
        }

        float currentYaw = modelDisplay.getLocation().getYaw();
        baseLocation.setYaw(currentYaw);
        double yawRad = Math.toRadians(currentYaw);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double localDx = -dx * cosYaw - dz * sinYaw;
        double localDz = -dx * sinYaw + dz * cosYaw;

        // Slight natural variation (~ +/- 6 degrees) while preserving the true knockback direction
        double randomAngleOffset = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.20;
        Vector3f perturbedDir = DummyPhysics.perturbDirection(localDx, localDz, randomAngleOffset);
        this.tiltAxis = DummyPhysics.calculateTiltAxis(perturbedDir);

        // Map perturbed local direction back to world space for ArmorStand neck translation sync
        this.worldPushX = -perturbedDir.x * cosYaw - perturbedDir.z * sinYaw;
        this.worldPushZ = -perturbedDir.x * sinYaw + perturbedDir.z * cosYaw;

        double intensity = plugin.getConfig().getDouble("dummy.wobble-intensity", 1.0);
        float baseTilt = (float) (0.30F * intensity);
        float damageTilt = (float) Math.min(0.35F * intensity, damage * 0.018F * intensity);
        this.initialAngle = baseTilt + damageTilt;
        this.animTick = 0;

        if (wobbleTask == null) {
            Vector3f pivot = new Vector3f(0.0F, WAIST_PIVOT_Y, 0.0F);
            float scaleVal = (float) plugin.getConfig().getDouble("dummy.model-scale", 1.0);
            Vector3f scale = new Vector3f(scaleVal, scaleVal, scaleVal);

            wobbleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!modelDisplay.isValid()) {
                    stopWobble();
                    return;
                }

                if (animTick >= 18) {
                    resetTransformation(scale);
                    stopWobble();
                    return;
                }

                float angle = DummyPhysics.calculateDampedAngle(initialAngle, animTick, 0.16F, 0.90F);
                Quaternionf rotation = new Quaternionf().setAngleAxis(angle, tiltAxis.x, tiltAxis.y, tiltAxis.z);
                Vector3f translation = DummyPhysics.calculatePivotTranslation(pivot, rotation);

                Transformation transform = new Transformation(
                        translation,
                        rotation,
                        scale,
                        new Quaternionf()
                );

                applyTransformationToRig(transform, angle);
                animTick++;
            }, 0L, 1L);
        }
    }

    private void applyTransformationToRig(Transformation transform, float angle) {
        if (modelDisplay.isValid()) {
            modelDisplay.setInterpolationDuration(1);
            modelDisplay.setInterpolationDelay(0);
            modelDisplay.setTransformation(transform);
        }
        if (armorStand != null && armorStand.isValid() && !equippedArmor.isEmpty()) {
            // In LivingEntityRenderer (rotateY(180 - yaw) * scale(-1, -1, 1)), both head (y < 0)
            // and torso/arms (y > 0) share the same rigid-body rotation matrix around the neck pivot:
            double pitch = -angle * tiltAxis.x;
            double roll = -angle * tiltAxis.z;
            EulerAngle upperBodyPose = new EulerAngle(pitch, 0.0, roll);
            armorStand.setHeadPose(upperBodyPose);
            armorStand.setBodyPose(upperBodyPose);
            armorStand.setLeftArmPose(new EulerAngle(pitch, 0.0, roll - RESTING_ARM_ROLL));
            armorStand.setRightArmPose(new EulerAngle(pitch, 0.0, roll + RESTING_ARM_ROLL));
            armorStand.setLeftLegPose(EulerAngle.ZERO);
            armorStand.setRightLegPose(EulerAngle.ZERO);

            // Translate ArmorStand so its neck pivot (Y=1.40) follows the dummy's waist-pivoted neck (Y=0.75 + 0.65)
            double sinA = Math.sin(angle);
            double cosA = Math.cos(angle);
            double offsetX = worldPushX * ARMOR_STAND_NECK_ABOVE_WAIST * sinA;
            double offsetY = -ARMOR_STAND_NECK_ABOVE_WAIST * (1.0 - cosA);
            double offsetZ = worldPushZ * ARMOR_STAND_NECK_ABOVE_WAIST * sinA;
            Location neckSyncedLoc = baseLocation.clone().add(offsetX, offsetY, offsetZ);
            float yaw = baseLocation.getYaw();
            armorStand.teleport(neckSyncedLoc);
            armorStand.setRotation(yaw, 0.0F);
            armorStand.setBodyYaw(yaw);
        }
    }

    private void resetTransformation(Vector3f scale) {
        Transformation identity = new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Quaternionf(),
                scale,
                new Quaternionf()
        );
        if (modelDisplay.isValid()) {
            modelDisplay.setInterpolationDuration(2);
            modelDisplay.setInterpolationDelay(0);
            modelDisplay.setTransformation(identity);
        }
        if (armorStand != null && armorStand.isValid()) {
            armorStand.teleport(baseLocation);
            syncArmorStandRotationAndPose(armorStand);
        }
    }

    public int getLastOutputPower() {
        return lastOutputPower;
    }

    public void triggerRedstoneOutput(double damage) {
        // Power proportional to damage: 1 dmg = level 1, 20+ dmg = max level 15
        int power = (int) Math.max(1, Math.min(15, Math.round(damage)));
        this.lastOutputPower = power;

        org.bukkit.block.Block baseBlock = baseLocation.getBlock();
        org.bukkit.block.Block supportBlock = baseBlock.getRelative(0, -1, 0);

        // Update TargetBlock if player placed dummy on top of a TARGET block
        if (supportBlock.getType() == Material.TARGET && supportBlock.getBlockData() instanceof org.bukkit.block.data.AnaloguePowerable target) {
            target.setPower(power);
            supportBlock.setBlockData(target, true);
        }

        // Fire redstone events for adjacent comparators/wires
        supportBlock.getState().update(true, true);

        if (redstoneResetTask != null) {
            redstoneResetTask.cancel();
        }

        int pulseDuration = Math.max(4, plugin.getConfig().getInt("dummy.redstone-pulse-ticks", 10));
        redstoneResetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            this.lastOutputPower = 0;
            if (supportBlock.getType() == Material.TARGET && supportBlock.getBlockData() instanceof org.bukkit.block.data.AnaloguePowerable target) {
                target.setPower(0);
                supportBlock.setBlockData(target, true);
            }
            supportBlock.getState().update(true, true);
            redstoneResetTask = null;
        }, pulseDuration);
    }

    public void stopWobble() {
        if (wobbleTask != null) {
            wobbleTask.cancel();
            wobbleTask = null;
        }
    }

    public void remove() {
        stopWobble();
        if (redstoneResetTask != null) {
            redstoneResetTask.cancel();
            redstoneResetTask = null;
        }
        if (modelEngine != null && interaction.isValid()) {
            modelEngine.destroyModel(interaction);
        }
        if (armorStand != null && armorStand.isValid()) {
            armorStand.remove();
            armorStand = null;
        }
        equippedArmor.clear();
        if (mannequin != null && mannequin.isValid()) {
            mannequin.remove();
        }
        if (interaction.isValid()) {
            interaction.remove();
        }
        if (baseDisplay != null && baseDisplay.isValid()) {
            baseDisplay.remove();
        }
        if (modelDisplay.isValid()) {
            modelDisplay.remove();
        }
    }
}
