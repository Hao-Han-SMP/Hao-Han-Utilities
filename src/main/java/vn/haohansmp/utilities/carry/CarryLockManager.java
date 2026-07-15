package vn.haohansmp.utilities.carry;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CarryLockManager {
    private final JavaPlugin plugin;
    private final Set<BlockPosition> lockedBlocks = ConcurrentHashMap.newKeySet();
    private final Map<ChunkKey, AtomicInteger> chunkTickets = new ConcurrentHashMap<>();

    public CarryLockManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean lock(BlockPosition position) {
        if (!lockedBlocks.add(position)) {
            return false;
        }
        ChunkKey key = ChunkKey.from(position);
        AtomicInteger references = chunkTickets.computeIfAbsent(key, ignored -> new AtomicInteger());
        if (references.getAndIncrement() == 0) {
            World world = Bukkit.getWorld(position.worldUuid());
            if (world != null) world.addPluginChunkTicket(key.chunkX(), key.chunkZ(), plugin);
        }
        return true;
    }

    public void unlock(BlockPosition position) {
        if (!lockedBlocks.remove(position)) {
            return;
        }
        ChunkKey key = ChunkKey.from(position);
        chunkTickets.computeIfPresent(key, (ignored, references) -> {
            if (references.decrementAndGet() <= 0) {
                World world = Bukkit.getWorld(key.worldUuid());
                if (world != null) world.removePluginChunkTicket(key.chunkX(), key.chunkZ(), plugin);
                return null;
            }
            return references;
        });
    }

    public boolean isLocked(BlockPosition position) {
        return lockedBlocks.contains(position);
    }

    public boolean hasLockedBlockIn(UUID worldUuid, int chunkX, int chunkZ) {
        return lockedBlocks.stream().anyMatch(position -> position.worldUuid().equals(worldUuid)
                && (position.x() >> 4) == chunkX && (position.z() >> 4) == chunkZ);
    }

    private record ChunkKey(UUID worldUuid, int chunkX, int chunkZ) {
        private static ChunkKey from(BlockPosition position) {
            return new ChunkKey(position.worldUuid(), position.x() >> 4, position.z() >> 4);
        }
    }
}
