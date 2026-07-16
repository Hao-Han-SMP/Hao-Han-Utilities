package vn.haohansmp.utilities.render;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.haohansmp.utilities.carry.CarryKind;
import vn.haohansmp.utilities.carry.CarrySession;
import vn.haohansmp.utilities.carry.CarrySessionManager;
import vn.haohansmp.utilities.carry.CarrySnapshotService;

public final class ItemDisplayRenderer implements CarryRenderer {
    private final JavaPlugin plugin;
    private final CarrySessionManager sessions;
    private final CarrySnapshotService snapshots;
    private final NamespacedKey movementSpeedKey;
    private BukkitTask followTask;

    public ItemDisplayRenderer(
            JavaPlugin plugin,
            CarrySessionManager sessions,
            CarrySnapshotService snapshots
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.snapshots = snapshots;
        this.movementSpeedKey = new NamespacedKey(plugin, "carry_movement_speed");
    }

    @Override
    public void start() {
        if (followTask == null) {
            followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 1L, 1L);
        }
    }

    @Override
    public void stop() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        sessions.sessions().forEach(this::remove);
    }

    @Override
    public void spawn(Player player, CarrySession session) {
        applyMovementSlowdown(player);
        try {
            Entity visual;
            if (session.payload().kind() == CarryKind.ENTITY) {
                visual = snapshots.spawnVisualEntity(entityLocation(player), session.payload());
                disableEntityPhysics(visual);
            } else {
                visual = spawnItemDisplay(player, session);
            }
            session.visualEntity(visual);
        } catch (RuntimeException exception) {
            removeMovementSlowdown(player);
            throw exception;
        }
    }

    @Override
    public void remove(CarrySession session) {
        Entity visual = session.visualEntity();
        if (visual != null) {
            visual.leaveVehicle();
            if (visual.isValid()) {
                visual.remove();
            }
        }
        Player player = Bukkit.getPlayer(session.playerUuid());
        if (player != null) {
            removeMovementSlowdown(player);
        }
        session.visualEntity(null);
    }

    private ItemDisplay spawnItemDisplay(Player player, CarrySession session) {
        float scale = (float) plugin.getConfig().getDouble("animation.object-scale", 1.05);
        float forward = (float) plugin.getConfig().getDouble("animation.forward-offset", 0.72);
        float vertical = (float) plugin.getConfig().getDouble("animation.vertical-offset", -0.55);
        ItemStack item = ItemStack.deserializeBytes(session.payload().visualItem());

        ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(
                    new Vector3f(0.0F, vertical, forward),
                    new Quaternionf(),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
            ));
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(0);
            entity.setTeleportDuration(0);
            entity.setDisplayWidth(1.5F);
            entity.setDisplayHeight(1.5F);
            entity.setRotation(player.getYaw(), 0.0F);
        });
        if (!player.addPassenger(display)) {
            display.remove();
            throw new IllegalStateException("Cannot attach carried block display to player");
        }
        return display;
    }

    private void updateAll() {
        for (CarrySession session : sessions.sessions()) {
            Player player = Bukkit.getPlayer(session.playerUuid());
            Entity visual = session.visualEntity();
            if (player == null || !player.isOnline() || visual == null || !visual.isValid()) {
                continue;
            }
            if (visual instanceof ItemDisplay display) {
                if (display.getVehicle() != player) {
                    display.leaveVehicle();
                    player.addPassenger(display);
                }
                display.setRotation(player.getYaw(), 0.0F);
            } else {
                disableEntityPhysics(visual);
                visual.teleport(entityLocation(player));
                visual.setVelocity(player.getVelocity());
            }
        }
    }

    private static void disableEntityPhysics(Entity visual) {
        visual.setNoPhysics(true);
        visual.setGravity(false);
        if (visual instanceof org.bukkit.entity.LivingEntity living) {
            living.setCollidable(false);
        }
    }

    private void applyMovementSlowdown(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }
        double configured = plugin.getConfig().getDouble("carrying.movement-speed-multiplier", 0.75);
        double multiplier = Math.max(0.1, Math.min(1.0, configured));
        movementSpeed.removeModifier(movementSpeedKey);
        movementSpeed.addTransientModifier(new AttributeModifier(
                movementSpeedKey,
                multiplier - 1.0,
                AttributeModifier.Operation.ADD_SCALAR
        ));
    }

    private void removeMovementSlowdown(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(movementSpeedKey);
        }
    }

    private Location entityLocation(Player player) {
        Location eye = player.getEyeLocation();
        double forwardDistance = plugin.getConfig().getDouble("animation.entity-forward-offset", 0.95);
        double verticalOffset = plugin.getConfig().getDouble("animation.entity-vertical-offset", -1.15);
        Vector forward = eye.getDirection().setY(0.0);
        if (forward.lengthSquared() < 0.0001) {
            forward = player.getLocation().getDirection().setY(0.0);
        }
        forward.normalize().multiply(forwardDistance);
        Location location = eye.clone().add(forward).add(0.0, verticalOffset, 0.0);
        location.setYaw(player.getYaw());
        location.setPitch(0.0F);
        return location;
    }
}
