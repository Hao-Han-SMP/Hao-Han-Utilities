package vn.haohan.utilities.feature.connection;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Chain;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LeashHitch;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ConnectionRenderer {
    private final JavaPlugin plugin;
    // Step length for BlockDisplay chain models
    private static final double MODEL_STEP = 1.0;

    public ConnectionRenderer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Entity> spawnChain(ConnectionRecord record) {
        List<Entity> spawned = new ArrayList<>();
        Location start = record.startLocation();
        Location end = record.endLocation();
        if (start == null || end == null || start.getWorld() == null) {
            return spawned;
        }

        World world = start.getWorld();
        Vector direction = end.toVector().subtract(start.toVector());
        double totalDistance = direction.length();
        if (totalDistance < 0.1) return spawned;

        int linkCount = Math.max(1, (int) Math.round(totalDistance / MODEL_STEP));
        double actualStepLength = totalDistance / linkCount;
        Vector unitDir = direction.clone().normalize();

        double horizontalDist = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        double flatness = (totalDistance > 0.001) ? (horizontalDist / totalDistance) : 0.0;
        double maxSag = Math.min(0.35, horizontalDist * 0.025) * flatness;

        Chain chainBlockData = (Chain) Material.IRON_CHAIN.createBlockData();
        chainBlockData.setAxis(Axis.Y);

        Vector[] positions = new Vector[linkCount + 1];
        for (int i = 0; i <= linkCount; i++) {
            double fraction = (double) i / linkCount;
            Vector pos = start.toVector().clone().add(unitDir.clone().multiply(i * actualStepLength));
            double sag = 4.0 * maxSag * fraction * (1.0 - fraction);
            pos.subtract(new Vector(0, sag, 0));
            positions[i] = pos;
        }

        for (int i = 0; i < linkCount; i++) {
            Vector cur = positions[i];
            Vector next = positions[i + 1];
            Vector segmentDir = next.clone().subtract(cur);
            double segLen = segmentDir.length();
            segmentDir.normalize();

            // Default Minecraft block model extends from [0, 0, 0] to [1, 1, 1] with center at (0.5, 0.5, 0.5).
            // Axis.Y model extends along +Y.
            Vector3f from = new Vector3f(0f, 1f, 0f);
            Vector3f to = new Vector3f((float) segmentDir.getX(), (float) segmentDir.getY(), (float) segmentDir.getZ());
            Quaternionf rotation = new Quaternionf().rotationTo(from, to);

            // Pivot at center of bottom face: (0.5, 0, 0.5)
            Vector3f baseOffset = new Vector3f(-0.5f, 0f, -0.5f);
            rotation.transform(baseOffset);

            float scaleY = (float) segLen;
            Transformation transform = new Transformation(
                    baseOffset,
                    rotation,
                    new Vector3f(1.0f, scaleY, 1.0f),
                    new Quaternionf()
            );

            Location blockLoc = cur.toLocation(world);
            BlockDisplay display = world.spawn(blockLoc, BlockDisplay.class, entity -> {
                entity.setBlock(chainBlockData);
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setTransformation(transform);
                entity.setPersistent(false);
                tagEntity(entity, record.id(), record.type(), "link");
            });
            spawned.add(display);
        }

        // Hitboxes: compact (0.45 width, 0.45 height) distributed along the path
        int hitboxCount = Math.max(1, (int) Math.ceil(totalDistance / 1.0));
        double hitboxStep = totalDistance / hitboxCount;
        for (int h = 0; h < hitboxCount; h++) {
            double frac = (h + 0.5) / hitboxCount;
            Vector hPos = start.toVector().clone().add(unitDir.clone().multiply((h + 0.5) * hitboxStep));
            double sag = 4.0 * maxSag * frac * (1.0 - fractionFallback(frac));
            hPos.subtract(new Vector(0, sag + 0.22, 0));

            Location hLoc = hPos.toLocation(world);
            Interaction interaction = world.spawn(hLoc, Interaction.class, entity -> {
                entity.setInteractionWidth(0.45f);
                entity.setInteractionHeight(0.45f);
                entity.setResponsive(true);
                entity.setPersistent(false);
                tagEntity(entity, record.id(), record.type(), "hitbox");
            });
            spawned.add(interaction);
        }

        return spawned;
    }

    private double fractionFallback(double frac) {
        return frac;
    }

    public List<Entity> spawnLeash(ConnectionRecord record) {
        List<Entity> spawned = new ArrayList<>();
        Location start = record.startLocation();
        Location end = record.endLocation();
        if (start == null || end == null || start.getWorld() == null) {
            return spawned;
        }

        World world = start.getWorld();
        Vector direction = end.toVector().subtract(start.toVector());
        double totalDistance = direction.length();

        Entity holderB = createLeashTarget(world, record.anchorBLocation(), end, record.id(), record.type());
        spawned.add(holderB);

        Chicken chicken = world.spawn(start.clone().subtract(0, 0.4, 0), Chicken.class, c -> {
            configureInvisibleChicken(c);
            tagEntity(c, record.id(), record.type(), "leash_source");
        });
        chicken.setLeashHolder(holderB);
        spawned.add(chicken);

        if (record.anchorALocation() != null && org.bukkit.Tag.FENCES.isTagged(record.anchorALocation().getBlock().getType())) {
            try {
                LeashHitch knotA = world.spawn(record.anchorALocation(), LeashHitch.class);
                knotA.setPersistent(false);
                tagEntity(knotA, record.id(), record.type(), "leash_knot_a");
                spawned.add(knotA);

                // In vanilla, LeashHitch without a leashed mob auto-despawns after 100 ticks.
                // Leashing a 0-distance chicken keeps knotA firmly in place.
                Chicken anchorChicken = world.spawn(record.anchorALocation(), Chicken.class, c -> {
                    configureInvisibleChicken(c);
                    tagEntity(c, record.id(), record.type(), "leash_knot_a_mob");
                });
                anchorChicken.setLeashHolder(knotA);
                spawned.add(anchorChicken);
            } catch (Exception ignored) {}
        }

        Vector unitDir = direction.clone().normalize();
        int hitboxCount = Math.max(1, (int) Math.ceil(totalDistance / 1.0));
        double hitboxStep = totalDistance / hitboxCount;
        for (int h = 0; h < hitboxCount; h++) {
            Vector hPos = start.toVector().clone().add(unitDir.clone().multiply((h + 0.5) * hitboxStep));
            Location hLoc = hPos.toLocation(world).subtract(0, 0.22, 0);

            Interaction interaction = world.spawn(hLoc, Interaction.class, entity -> {
                entity.setInteractionWidth(0.45f);
                entity.setInteractionHeight(0.45f);
                entity.setResponsive(true);
                entity.setPersistent(false);
                tagEntity(entity, record.id(), record.type(), "hitbox");
            });
            spawned.add(interaction);
        }

        return spawned;
    }

    private Entity createLeashTarget(World world, Location anchorLoc, Location ropeLoc, UUID id, String type) {
        if (anchorLoc != null && org.bukkit.Tag.FENCES.isTagged(anchorLoc.getBlock().getType())) {
            try {
                LeashHitch knot = world.spawn(anchorLoc, LeashHitch.class);
                knot.setPersistent(false);
                tagEntity(knot, id, type, "leash_target");
                return knot;
            } catch (Exception ignored) {}
        }

        return world.spawn(ropeLoc.clone().subtract(0, 0.4, 0), Chicken.class, c -> {
            configureInvisibleChicken(c);
            tagEntity(c, id, type, "leash_target");
        });
    }

    private void configureInvisibleChicken(Chicken c) {
        c.setInvisible(true);
        c.setSilent(true);
        c.setInvulnerable(true);
        c.setAI(false);
        c.setCollidable(false);
        c.setGravity(false);
        c.setPersistent(false);
        c.setAge(-Integer.MAX_VALUE);
        c.setAgeLock(true);
        c.setLootTable(null);
        c.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 1, false, false));
    }

    private void tagEntity(Entity entity, UUID connectionId, String type, String role) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(ConnectionConstants.KEY_CONNECTION_ID, PersistentDataType.STRING, connectionId.toString());
        pdc.set(ConnectionConstants.KEY_CONNECTION_TYPE, PersistentDataType.STRING, type);
        pdc.set(ConnectionConstants.KEY_CONNECTION_ROLE, PersistentDataType.STRING, role);
    }
}
