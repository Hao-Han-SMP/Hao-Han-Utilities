package vn.haohan.utilities.fetch;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ExcitedDogSession {
    private final UUID wolfUuid;
    private final UUID playerUuid;
    private double angle;
    private final double radius;
    private final boolean clockwise;
    private int ticksActive;
    private int soundCooldown;

    public ExcitedDogSession(UUID wolfUuid, UUID playerUuid, double radius) {
        this.wolfUuid = wolfUuid;
        this.playerUuid = playerUuid;
        this.angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        this.radius = radius;
        this.clockwise = ThreadLocalRandom.current().nextBoolean();
        this.ticksActive = 0;
        this.soundCooldown = ThreadLocalRandom.current().nextInt(10, 30);
    }

    public UUID getWolfUuid() {
        return wolfUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void tick(Wolf wolf, Player player, double speedMultiplier) {
        ticksActive++;
        soundCooldown--;

        wolf.lookAt(player.getEyeLocation());

        double distanceSq = wolf.getLocation().distanceSquared(player.getLocation());

        if (distanceSq > (radius + 2.0) * (radius + 2.0)) {
            // Far away: sprint directly towards player
            wolf.getPathfinder().moveTo(player.getLocation(), speedMultiplier);
        } else {
            // Close: playfully circle around the player
            double step = clockwise ? 0.22 : -0.22;
            angle += step;
            if (angle > Math.PI * 2) angle -= Math.PI * 2;
            if (angle < 0) angle += Math.PI * 2;

            double targetX = player.getLocation().getX() + radius * Math.cos(angle);
            double targetZ = player.getLocation().getZ() + radius * Math.sin(angle);
            double targetY = player.getLocation().getY();

            Location targetLoc = new Location(player.getWorld(), targetX, targetY, targetZ);
            wolf.getPathfinder().moveTo(targetLoc, speedMultiplier);

            // Playful small hop occasionally
            if (ticksActive % 25 == 0 && wolf.isOnGround() && ThreadLocalRandom.current().nextDouble() < 0.35) {
                Vector vel = wolf.getVelocity();
                wolf.setVelocity(new Vector(vel.getX() * 1.2, 0.28, vel.getZ() * 1.2));
            }
        }

        // Excited particles and sounds
        if (soundCooldown <= 0) {
            soundCooldown = ThreadLocalRandom.current().nextInt(30, 60);

            if (ThreadLocalRandom.current().nextBoolean()) {
                wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_PANT, 0.85f, 1.3f);
            } else {
                wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 0.8f, 1.4f);
            }

            wolf.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    wolf.getLocation().add(0, 0.7, 0),
                    3,
                    0.2, 0.2, 0.2, 0.02
            );
        }
    }
}
