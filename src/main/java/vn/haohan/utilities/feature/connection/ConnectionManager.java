package vn.haohan.utilities.feature.connection;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectionManager {
    private final JavaPlugin plugin;
    private final ConnectionDatabase database;
    private final ConnectionRenderer renderer;

    private final Map<UUID, ConnectionRecord> connections = new ConcurrentHashMap<>();
    private final Map<UUID, List<Entity>> activeEntities = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSelection> pendingSelections = new ConcurrentHashMap<>();

    public record PendingSelection(Location startLocation, Block anchorBlock, Material toolMaterial, long timestamp) {}

    public ConnectionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.database = new ConnectionDatabase(plugin);
        this.renderer = new ConnectionRenderer(plugin);
    }

    public void initialize() {
        database.initialize();
        loadAll();
    }

    public void shutdown() {
        clearVisuals();
    }

    private void loadAll() {
        clearVisuals();
        List<ConnectionRecord> records = database.loadAll();
        for (ConnectionRecord record : records) {
            connections.put(record.id(), record);
            spawnVisuals(record);
        }
    }

    public void spawnVisuals(ConnectionRecord record) {
        if (activeEntities.containsKey(record.id())) {
            return;
        }

        Location start = record.startLocation();
        Location end = record.endLocation();
        if (start == null || end == null || start.getWorld() == null) {
            return;
        }

        World world = start.getWorld();
        int chunkX1 = start.getBlockX() >> 4;
        int chunkZ1 = start.getBlockZ() >> 4;
        int chunkX2 = end.getBlockX() >> 4;
        int chunkZ2 = end.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX1, chunkZ1) || !world.isChunkLoaded(chunkX2, chunkZ2)) {
            return; // Both ends must be loaded to avoid synchronous force-loading
        }

        if ("LEASH".equalsIgnoreCase(record.type())) {
            List<Entity> spawned = renderer.spawnLeash(record);
            if (!spawned.isEmpty()) {
                activeEntities.put(record.id(), spawned);
            }
        } else {
            List<Entity> spawned = renderer.spawnChain(record);
            if (!spawned.isEmpty()) {
                activeEntities.put(record.id(), spawned);
            }
        }
    }

    public void removeVisuals(UUID connectionId) {
        List<Entity> entities = activeEntities.remove(connectionId);
        if (entities != null) {
            for (Entity entity : entities) {
                if (entity != null) {
                    try {
                        entity.remove();
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void clearVisuals() {
        for (UUID id : activeEntities.keySet()) {
            removeVisuals(id);
        }
        activeEntities.clear();
    }

    public void handleChunkLoad(Chunk chunk) {
        int cx = chunk.getX();
        int cz = chunk.getZ();
        UUID worldId = chunk.getWorld().getUID();

        for (ConnectionRecord record : connections.values()) {
            if (!record.worldUuid().equals(worldId)) continue;
            if (activeEntities.containsKey(record.id())) continue;

            boolean nearA = (record.anchorA_X() >> 4) == cx && (record.anchorA_Z() >> 4) == cz;
            boolean nearB = (record.anchorB_X() >> 4) == cx && (record.anchorB_Z() >> 4) == cz;
            if (nearA || nearB) {
                spawnVisuals(record);
            }
        }
    }

    public void handleChunkUnload(Chunk chunk) {
        int cx = chunk.getX();
        int cz = chunk.getZ();
        UUID worldId = chunk.getWorld().getUID();

        for (ConnectionRecord record : connections.values()) {
            if (!record.worldUuid().equals(worldId)) continue;
            if (!activeEntities.containsKey(record.id())) continue;

            boolean nearA = (record.anchorA_X() >> 4) == cx && (record.anchorA_Z() >> 4) == cz;
            boolean nearB = (record.anchorB_X() >> 4) == cx && (record.anchorB_Z() >> 4) == cz;
            if (nearA || nearB) {
                removeVisuals(record.id());
            }
        }
    }

    public void setPending(Player player, Location location, Block anchorBlock, Material material) {
        pendingSelections.put(player.getUniqueId(), new PendingSelection(location, anchorBlock, material, System.currentTimeMillis()));
    }

    public PendingSelection getPending(Player player) {
        PendingSelection sel = pendingSelections.get(player.getUniqueId());
        if (sel != null && System.currentTimeMillis() - sel.timestamp() > 60000) {
            pendingSelections.remove(player.getUniqueId());
            return null;
        }
        return sel;
    }

    public void clearPending(Player player) {
        pendingSelections.remove(player.getUniqueId());
    }

    public ConnectionRecord createConnection(Player creator, String type, Material material, Location start, Location end, Block anchorA, Block anchorB) {
        UUID id = UUID.randomUUID();
        ConnectionRecord record = new ConnectionRecord(
                id,
                type,
                material,
                creator.getUniqueId(),
                start.getWorld().getUID(),
                start.getX(), start.getY(), start.getZ(),
                end.getX(), end.getY(), end.getZ(),
                anchorA.getX(), anchorA.getY(), anchorA.getZ(),
                anchorB.getX(), anchorB.getY(), anchorB.getZ(),
                System.currentTimeMillis()
        );
        connections.put(id, record);
        asyncSave(record);
        spawnVisuals(record);
        return record;
    }

    private void asyncSave(ConnectionRecord record) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.save(record));
        } else {
            database.save(record);
        }
    }

    private void asyncDelete(UUID id) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.delete(id));
        } else {
            database.delete(id);
        }
    }

    public void handleAnchorBroken(Block brokenBlock) {
        for (ConnectionRecord record : new ArrayList<>(connections.values())) {
            boolean brokeA = isSameBlock(record.anchorALocation(), brokenBlock.getLocation());
            boolean brokeB = isSameBlock(record.anchorBLocation(), brokenBlock.getLocation());

            if (brokeA || brokeB) {
                Location otherLoc = brokeA ? record.anchorBLocation() : record.anchorALocation();
                boolean otherGone = otherLoc == null || otherLoc.getBlock().isEmpty();
                collapseConnection(record.id(), brokeA ? record.startLocation() : record.endLocation(), otherGone);
            }
        }
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /**
     * Collapses connection starting from hitPoint outward, dropping all consumed items.
     */
    public void collapseConnection(UUID id, Location hitPoint, boolean fastCollapse) {
        ConnectionRecord record = connections.remove(id);
        if (record == null) return;

        asyncDelete(id);
        List<Entity> entities = activeEntities.remove(id);
        if (entities == null || entities.isEmpty()) return;

        List<Entity> links = new ArrayList<>();
        List<Entity> others = new ArrayList<>();
        for (Entity e : entities) {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            String role = pdc.get(ConnectionConstants.KEY_CONNECTION_ROLE, PersistentDataType.STRING);
            if ("link".equalsIgnoreCase(role)) {
                links.add(e);
            } else {
                others.add(e);
            }
        }

        // Clean up hitboxes and secondary entities immediately
        for (Entity o : others) {
            if (o != null) {
                try {
                    o.remove();
                } catch (Exception ignored) {}
            }
        }

        // Fix: Leash drops exactly 1 Lead; Chain drops based on distance.
        boolean isLeash = "LEASH".equalsIgnoreCase(record.type());
        int totalDrops = isLeash
                ? 1
                : (int) Math.max(1, Math.round(record.startLocation().distance(record.endLocation())));
        Material dropMat = isLeash ? Material.LEAD : record.chainMaterial();
        BlockData particleData = (isLeash ? Material.IRON_CHAIN : dropMat).createBlockData();

        if (links.isEmpty()) {
            Location dropLoc = (hitPoint != null) ? hitPoint : record.startLocation();
            if (dropLoc != null && dropLoc.getWorld() != null) {
                dropLoc.getWorld().dropItemNaturally(dropLoc, new ItemStack(dropMat, totalDrops));
                dropLoc.getWorld().playSound(dropLoc, Sound.ITEM_LEAD_BREAK, 1f, 1f);
            }
            return;
        }

        // Sort links from closest to hitPoint outward
        if (hitPoint != null) {
            links.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(hitPoint)));
        }

        long intervalTicks = fastCollapse ? 1L : 2L;

        new BukkitRunnable() {
            int index = 0;
            int droppedCount = 0;

            @Override
            public void run() {
                if (index >= links.size()) {
                    if (droppedCount < totalDrops) {
                        Location loc = record.endLocation();
                        if (loc != null && loc.getWorld() != null) {
                            loc.getWorld().dropItemNaturally(loc, new ItemStack(dropMat, totalDrops - droppedCount));
                        }
                    }
                    cancel();
                    return;
                }

                Entity link = links.get(index);
                if (link != null && link.isValid()) {
                    Location loc = link.getLocation();
                    loc.getWorld().spawnParticle(Particle.BLOCK, loc, 6, 0.1, 0.1, 0.1, particleData);
                    loc.getWorld().playSound(loc, Sound.BLOCK_CHAIN_BREAK, 0.5f, 1.2f);
                    link.remove();

                    if (droppedCount < totalDrops) {
                        loc.getWorld().dropItemNaturally(loc, new ItemStack(dropMat, 1));
                        droppedCount++;
                    }
                }
                index++;
            }
        }.runTaskTimer(plugin, 1L, intervalTicks);
    }

    public UUID getConnectionIdFromEntity(Entity entity) {
        if (entity == null) return null;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String idStr = pdc.get(ConnectionConstants.KEY_CONNECTION_ID, PersistentDataType.STRING);
        if (idStr != null) {
            try {
                return UUID.fromString(idStr);
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    public ConnectionRecord getConnection(UUID id) {
        return connections.get(id);
    }

    public Collection<ConnectionRecord> getAllConnections() {
        return connections.values();
    }
}
