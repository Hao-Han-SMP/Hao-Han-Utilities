package vn.haohan.utilities.fetch;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class TrajectoryRenderer {
    private static final Particle.DustOptions TRAJECTORY_DUST = new Particle.DustOptions(Color.fromRGB(245, 245, 255), 0.35f);
    private static final Particle.DustOptions IMPACT_DUST = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.60f);
    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.98;

    private TrajectoryRenderer() {}

    public static Location getRightHandOrigin(Location eyeLocation) {
        Vector dir = eyeLocation.getDirection().normalize();
        double yawRad = Math.toRadians(eyeLocation.getYaw());
        Vector right = new Vector(-Math.cos(yawRad), 0, -Math.sin(yawRad)).normalize();
        return eyeLocation.clone()
                .add(dir.multiply(0.35))
                .add(right.multiply(0.26))
                .subtract(0, 0.10, 0);
    }

    public static List<Location> calculateTrajectory(Location origin, Vector initialVelocity, int maxSteps) {
        List<Location> points = new ArrayList<>(maxSteps);
        World world = origin.getWorld();
        if (world == null) return points;

        Location current = origin.clone();
        Vector velocity = initialVelocity.clone();

        for (int i = 0; i < maxSteps; i++) {
            points.add(current.clone());

            current.add(velocity);
            velocity.multiply(DRAG);
            velocity.subtract(new Vector(0, GRAVITY, 0));

            Block block = current.getBlock();
            if (block.getType().isSolid()) {
                points.add(current.clone());
                break;
            }
        }
        return points;
    }

    public static void drawPreview(Player player, List<Location> points) {
        if (points.isEmpty()) return;

        for (int i = 0; i < points.size(); i++) {
            Location loc = points.get(i);
            if (i == points.size() - 1) {
                player.spawnParticle(Particle.DUST, loc, 2, 0.03, 0.03, 0.03, 0, IMPACT_DUST);
            } else if (i % 2 == 0) {
                player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, TRAJECTORY_DUST);
            }
        }
    }

    public static void drawThrowFlash(World world, List<Location> points) {
        if (points.isEmpty() || world == null) return;

        for (int i = 0; i < points.size(); i++) {
            Location loc = points.get(i);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, TRAJECTORY_DUST);
            }
            if (i == points.size() - 1) {
                world.spawnParticle(Particle.WAX_OFF, loc, 3, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }
}
