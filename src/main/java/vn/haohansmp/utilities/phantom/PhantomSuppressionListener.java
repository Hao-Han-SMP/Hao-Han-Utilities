package vn.haohansmp.utilities.phantom;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PhantomSuppressionListener implements Listener {
    private final JavaPlugin plugin;

    public PhantomSuppressionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent event) {
        if (enabled() && event.getEntityType() == EntityType.PHANTOM) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (enabled() && removeExisting()) {
            removeFrom(event.getChunk());
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (enabled() && removeExisting()) {
            for (Chunk chunk : event.getWorld().getLoadedChunks()) removeFrom(chunk);
        }
    }

    public int cleanupLoadedWorlds() {
        if (!enabled() || !removeExisting()) return 0;
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                removed += removeFrom(chunk);
            }
        }
        return removed;
    }

    private int removeFrom(Chunk chunk) {
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.PHANTOM) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("phantom-suppression.enabled", true);
    }

    private boolean removeExisting() {
        return plugin.getConfig().getBoolean("phantom-suppression.remove-existing", true);
    }
}
