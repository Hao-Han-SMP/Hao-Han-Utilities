package vn.haohan.utilities.feature.dummy;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.haohan.utilities.feature.dummy.StrawDummyItem.DummyArmorSlot;
import vn.haohan.utilities.feature.dummy.StrawDummyItem.DummyArmorTier;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DummyManager {
    private final Plugin plugin;
    private final StrawDummyItem dummyItem;
    private final DamageIndicatorService damageIndicatorService;
    private final vn.haohan.utilities.integration.ModelEngineIntegration modelEngine;

    private final NamespacedKey dummyIdKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey roleKey;
    private final NamespacedKey armorDataKey;
    private final NamespacedKey yawKey;

    private final Map<UUID, DummyInstance> activeDummies = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> interactionToDummyId = new ConcurrentHashMap<>();
    private final Map<Location, DummyInstance> dummiesByBlockLocation = new ConcurrentHashMap<>();

    public DummyManager(
            Plugin plugin,
            StrawDummyItem dummyItem,
            DamageIndicatorService damageIndicatorService,
            vn.haohan.utilities.integration.ModelEngineIntegration modelEngine
    ) {
        this.plugin = plugin;
        this.dummyItem = dummyItem;
        this.damageIndicatorService = damageIndicatorService;
        this.modelEngine = modelEngine;

        this.dummyIdKey = new NamespacedKey(plugin, "dummy_id");
        this.ownerKey = new NamespacedKey(plugin, "dummy_owner");
        this.roleKey = new NamespacedKey(plugin, "dummy_role");
        this.armorDataKey = new NamespacedKey(plugin, "dummy_armor");
        this.yawKey = new NamespacedKey(plugin, "dummy_yaw");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public NamespacedKey getDummyIdKey() {
        return dummyIdKey;
    }

    public NamespacedKey getRoleKey() {
        return roleKey;
    }

    public NamespacedKey getArmorDataKey() {
        return armorDataKey;
    }

    public StrawDummyItem getDummyItem() {
        return dummyItem;
    }

    public DamageIndicatorService getDamageIndicatorService() {
        return damageIndicatorService;
    }

    public DummyInstance spawnDummy(Location location, Player owner) {
        World world = location.getWorld();
        if (world == null) return null;

        UUID dummyId = UUID.randomUUID();
        UUID ownerUuid = owner != null ? owner.getUniqueId() : null;

        Location spawnLoc = location.clone();
        spawnLoc.setPitch(0.0F);
        final float initialYaw = spawnLoc.getYaw();

        // 1. Spawn Interaction entity: data/ModelEngine anchor only. Zero size so it never swallows clicks
        //    (client always consumes right-clicks on Interaction); the invisible Mannequin is the real hitbox.
        Interaction interaction = world.spawn(spawnLoc, Interaction.class, entity -> {
            entity.setInteractionWidth(0.0F);
            entity.setInteractionHeight(0.0F);
            entity.setResponsive(true);

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(dummyIdKey, PersistentDataType.STRING, dummyId.toString());
            if (ownerUuid != null) {
                pdc.set(ownerKey, PersistentDataType.STRING, ownerUuid.toString());
            }
            pdc.set(roleKey, PersistentDataType.STRING, "interaction");
            pdc.set(yawKey, PersistentDataType.FLOAT, initialYaw);
        });

        // 2. Spawn stationary Base ItemDisplay (pedestal + post + waist step)
        float verticalOffset = (float) plugin.getConfig().getDouble("dummy.model-vertical-offset", 0.0);
        Location displayLoc = spawnLoc.clone().add(0, verticalOffset, 0);

        ItemStack baseItem = dummyItem.createBaseItem();
        ItemStack bodyItem = dummyItem.createBodyItem();
        float scaleVal = (float) plugin.getConfig().getDouble("dummy.model-scale", 1.0);

        ItemDisplay baseDisplay = world.spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setRotation(initialYaw, 0.0F);
            entity.setItemStack(baseItem);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new Quaternionf(),
                    new Vector3f(scaleVal, scaleVal, scaleVal),
                    new Quaternionf()
            ));

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(dummyIdKey, PersistentDataType.STRING, dummyId.toString());
            if (ownerUuid != null) {
                pdc.set(ownerKey, PersistentDataType.STRING, ownerUuid.toString());
            }
            pdc.set(roleKey, PersistentDataType.STRING, "base");
        });

        // 3. Spawn animated Upper-Body ItemDisplay (torso + arms + head)
        ItemDisplay modelDisplay = world.spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setRotation(initialYaw, 0.0F);
            entity.setItemStack(bodyItem);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new Quaternionf(),
                    new Vector3f(scaleVal, scaleVal, scaleVal),
                    new Quaternionf()
            ));

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(dummyIdKey, PersistentDataType.STRING, dummyId.toString());
            if (ownerUuid != null) {
                pdc.set(ownerKey, PersistentDataType.STRING, ownerUuid.toString());
            }
            pdc.set(roleKey, PersistentDataType.STRING, "model");
        });

        DummyInstance instance = new DummyInstance(plugin, dummyId, ownerUuid, spawnLoc, interaction, baseDisplay, modelDisplay, modelEngine);
        org.bukkit.entity.ArmorStand stand = instance.ensureArmorStand(dummyIdKey, roleKey);
        org.bukkit.entity.Mannequin mannequin = instance.ensureMannequin(dummyIdKey, roleKey);
        activeDummies.put(dummyId, instance);
        interactionToDummyId.put(interaction.getUniqueId(), dummyId);
        if (stand != null) {
            interactionToDummyId.put(stand.getUniqueId(), dummyId);
        }
        if (mannequin != null) {
            interactionToDummyId.put(mannequin.getUniqueId(), dummyId);
        }
        registerDummyLocation(instance);

        return instance;
    }

    public void registerDummyLocation(DummyInstance dummy) {
        if (dummy == null) return;
        org.bukkit.block.Block baseBlock = dummy.getBaseLocation().getBlock();
        org.bukkit.block.Block supportBlock = baseBlock.getRelative(org.bukkit.block.BlockFace.DOWN);
        dummiesByBlockLocation.put(baseBlock.getLocation(), dummy);
        dummiesByBlockLocation.put(supportBlock.getLocation(), dummy);
    }

    public void unregisterDummyLocation(DummyInstance dummy) {
        if (dummy == null) return;
        org.bukkit.block.Block baseBlock = dummy.getBaseLocation().getBlock();
        org.bukkit.block.Block supportBlock = baseBlock.getRelative(org.bukkit.block.BlockFace.DOWN);
        dummiesByBlockLocation.remove(baseBlock.getLocation());
        dummiesByBlockLocation.remove(supportBlock.getLocation());
    }

    public DummyInstance getDummyByBlock(org.bukkit.block.Block block) {
        if (block == null) return null;
        DummyInstance dummy = dummiesByBlockLocation.get(block.getLocation());
        if (dummy != null && dummy.isValid()) {
            return dummy;
        }
        return null;
    }

    public DummyInstance getDummyAdjacentTo(org.bukkit.block.Block block) {
        if (block == null) return null;
        org.bukkit.block.BlockFace[] faces = {
                org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.UP,
                org.bukkit.block.BlockFace.DOWN
        };
        for (org.bukkit.block.BlockFace face : faces) {
            DummyInstance dummy = dummiesByBlockLocation.get(block.getRelative(face).getLocation());
            if (dummy != null && dummy.isValid()) {
                return dummy;
            }
        }
        return null;
    }

    public DummyInstance getDummyAtBlock(org.bukkit.block.Block block) {
        if (block == null) return null;
        DummyInstance dummy = dummiesByBlockLocation.get(block.getLocation());
        if (dummy != null && dummy.isValid()) {
            return dummy;
        }
        for (DummyInstance d : activeDummies.values()) {
            if (d.isValid() && d.getBaseLocation().getBlock().equals(block)) {
                return d;
            }
        }
        return null;
    }

    public DummyInstance getDummyAbove(org.bukkit.block.Block supportBlock) {
        if (supportBlock == null) return null;
        return getDummyAtBlock(supportBlock.getRelative(0, 1, 0));
    }

    public DummyInstance getByEntity(Entity entity) {
        if (entity == null) return null;

        UUID dummyId = interactionToDummyId.get(entity.getUniqueId());
        if (dummyId != null) {
            DummyInstance instance = activeDummies.get(dummyId);
            if (instance != null && instance.isValid()) {
                return instance;
            }
        }

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String idStr = pdc.get(dummyIdKey, PersistentDataType.STRING);
        if (idStr == null) return null;

        try {
            dummyId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        DummyInstance existing = activeDummies.get(dummyId);
        if (existing != null && existing.isValid()) {
            interactionToDummyId.put(entity.getUniqueId(), dummyId);
            return existing;
        }

        return reconnectDummy(entity.getWorld(), dummyId, entity);
    }

    private DummyInstance reconnectDummy(World world, UUID dummyId, Entity triggerEntity) {
        Interaction interaction = null;
        ItemDisplay baseDisplay = null;
        ItemDisplay modelDisplay = null;
        org.bukkit.entity.ArmorStand foundArmorStand = null;
        org.bukkit.entity.Mannequin foundMannequin = null;
        UUID ownerUuid = null;

        for (Entity e : world.getNearbyEntities(triggerEntity.getLocation(), 3.0, 3.0, 3.0)) {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            String idStr = pdc.get(dummyIdKey, PersistentDataType.STRING);
            if (idStr != null && idStr.equals(dummyId.toString())) {
                String role = pdc.get(roleKey, PersistentDataType.STRING);
                if ("interaction".equalsIgnoreCase(role) && e instanceof Interaction inter) {
                    interaction = inter;
                } else if ("base".equalsIgnoreCase(role) && e instanceof ItemDisplay disp) {
                    baseDisplay = disp;
                } else if ("model".equalsIgnoreCase(role) && e instanceof ItemDisplay disp) {
                    modelDisplay = disp;
                } else if ("armor_stand".equalsIgnoreCase(role) && e instanceof org.bukkit.entity.ArmorStand stand) {
                    foundArmorStand = stand;
                } else if ("mannequin".equalsIgnoreCase(role) && e instanceof org.bukkit.entity.Mannequin m) {
                    foundMannequin = m;
                } else if (role != null && role.startsWith("armor_") && e instanceof ItemDisplay legacyDisp) {
                    legacyDisp.remove();
                }
                String ownerStr = pdc.get(ownerKey, PersistentDataType.STRING);
                if (ownerStr != null && ownerUuid == null) {
                    try {
                        ownerUuid = UUID.fromString(ownerStr);
                    } catch (Exception ignored) {}
                }
            }
        }

        if (interaction != null && modelDisplay != null) {
            // Interaction entities in CraftBukkit do not persist rotation (yaw is always 0),
            // so restore yaw from modelDisplay or stored PDC dummy_yaw.
            Float savedYaw = interaction.getPersistentDataContainer().get(yawKey, PersistentDataType.FLOAT);
            float restoredYaw = (savedYaw != null) ? savedYaw : modelDisplay.getLocation().getYaw();
            interaction.getPersistentDataContainer().set(yawKey, PersistentDataType.FLOAT, restoredYaw);

            Location spawnLoc = interaction.getLocation().clone();
            spawnLoc.setYaw(restoredYaw);
            spawnLoc.setPitch(0.0F);
            float scaleVal = (float) plugin.getConfig().getDouble("dummy.model-scale", 1.0);

            // Migrate pre-split dummies seamlessly and ensure baseDisplay matches modelDisplay yaw
            if (baseDisplay == null) {
                float verticalOffset = (float) plugin.getConfig().getDouble("dummy.model-vertical-offset", 0.0);
                Location displayLoc = spawnLoc.clone().add(0, verticalOffset, 0);
                final UUID finalOwnerUuid = ownerUuid;
                baseDisplay = world.spawn(displayLoc, ItemDisplay.class, entity -> {
                    entity.setRotation(restoredYaw, 0.0F);
                    entity.setItemStack(dummyItem.createBaseItem());
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    entity.setTransformation(new Transformation(
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Quaternionf(),
                            new Vector3f(scaleVal, scaleVal, scaleVal),
                            new Quaternionf()
                    ));
                    PersistentDataContainer pdc = entity.getPersistentDataContainer();
                    pdc.set(dummyIdKey, PersistentDataType.STRING, dummyId.toString());
                    if (finalOwnerUuid != null) {
                        pdc.set(ownerKey, PersistentDataType.STRING, finalOwnerUuid.toString());
                    }
                    pdc.set(roleKey, PersistentDataType.STRING, "base");
                });
            } else {
                baseDisplay.setRotation(restoredYaw, 0.0F);
            }
            modelDisplay.setRotation(restoredYaw, 0.0F);
            modelDisplay.setItemStack(dummyItem.createBodyItem());

            DummyInstance instance = new DummyInstance(
                    plugin, dummyId, ownerUuid, spawnLoc, interaction, baseDisplay, modelDisplay, modelEngine);

            if (foundArmorStand != null) {
                instance.attachRestoredArmorStand(foundArmorStand);
            } else {
                foundArmorStand = instance.ensureArmorStand(dummyIdKey, roleKey);
            }

            String armorStr = interaction.getPersistentDataContainer().get(armorDataKey, PersistentDataType.STRING);
            if (armorStr != null && !armorStr.isBlank() && !instance.hasAnyArmor()) {
                for (String part : armorStr.split(",")) {
                    String[] kv = part.split(":");
                    if (kv.length == 2) {
                        DummyArmorSlot slot = DummyArmorSlot.fromId(kv[0]);
                        DummyArmorTier tier = DummyArmorTier.fromId(kv[1]);
                        if (slot != null && tier != null) {
                            instance.equipArmor(slot, tier, null, dummyIdKey, roleKey, armorDataKey);
                        }
                    }
                }
            }

            activeDummies.put(dummyId, instance);
            interactionToDummyId.put(interaction.getUniqueId(), dummyId);
            if (foundArmorStand != null) {
                interactionToDummyId.put(foundArmorStand.getUniqueId(), dummyId);
            }
            // Migrate: old dummies used the Interaction as hitbox
            interaction.setInteractionWidth(0.0F);
            interaction.setInteractionHeight(0.0F);
            if (foundMannequin != null) {
                instance.attachRestoredMannequin(foundMannequin);
            } else {
                foundMannequin = instance.ensureMannequin(dummyIdKey, roleKey);
            }
            instance.syncMannequinArmor();
            if (foundMannequin != null) {
                interactionToDummyId.put(foundMannequin.getUniqueId(), dummyId);
            }
            registerDummyLocation(instance);
            return instance;
        }

        return null;
    }

    public void removeDummy(DummyInstance dummy, boolean dropItem) {
        if (dummy == null) return;

        unregisterDummyLocation(dummy);
        activeDummies.remove(dummy.getDummyId());
        interactionToDummyId.remove(dummy.getInteraction().getUniqueId());
        if (dummy.getArmorStand() != null) {
            interactionToDummyId.remove(dummy.getArmorStand().getUniqueId());
        }
        if (dummy.getMannequin() != null) {
            interactionToDummyId.remove(dummy.getMannequin().getUniqueId());
        }

        Location dropLoc = dummy.getBaseLocation().clone().add(0, 0.5, 0);
        dummy.remove();

        if (dropItem) {
            dropLoc.getWorld().dropItemNaturally(dropLoc, dummyItem.createItem(1));
        }
    }

    public Collection<DummyInstance> getAllDummies() {
        return activeDummies.values();
    }

    public void shutdown() {
        for (DummyInstance dummy : activeDummies.values()) {
            dummy.stopWobble();
        }
        activeDummies.clear();
        interactionToDummyId.clear();
        dummiesByBlockLocation.clear();
    }
}
