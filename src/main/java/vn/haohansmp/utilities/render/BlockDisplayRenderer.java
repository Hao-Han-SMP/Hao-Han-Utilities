package vn.haohansmp.utilities.render;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import vn.haohansmp.utilities.carry.CarrySession;
import vn.haohansmp.utilities.carry.CarrySessionManager;

public final class BlockDisplayRenderer implements CarryRenderer {
    private final JavaPlugin plugin;
    private final CarrySessionManager sessions;
    private BukkitTask followTask;

    public BlockDisplayRenderer(JavaPlugin plugin, CarrySessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    @Override
    public void start() {
        if (followTask == null) {
            followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 1L, 2L);
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
        BlockData data = Bukkit.createBlockData(session.payload().blockData());
        BlockDisplay display = player.getWorld().spawn(calculateLocation(player), BlockDisplay.class, entity -> {
            entity.setBlock(data);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(2);
        });
        session.display(display);
    }

    @Override
    public void remove(CarrySession session) {
        BlockDisplay display = session.display();
        if (display != null && display.isValid()) {
            display.remove();
        }
        session.display(null);
    }

    private void updateAll() {
        for (CarrySession session : sessions.sessions()) {
            Player player = Bukkit.getPlayer(session.playerUuid());
            BlockDisplay display = session.display();
            if (player != null && player.isOnline() && display != null && display.isValid()) {
                display.teleport(calculateLocation(player));
            }
        }
    }

    private static Location calculateLocation(Player player) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize().multiply(0.75);
        return eye.clone().add(forward).subtract(0.0, 0.65, 0.0);
    }
}
