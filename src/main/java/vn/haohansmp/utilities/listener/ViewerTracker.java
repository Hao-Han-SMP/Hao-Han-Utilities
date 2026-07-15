package vn.haohansmp.utilities.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vn.haohansmp.utilities.carry.BlockPosition;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViewerTracker implements Listener {
    private final Map<BlockPosition, Set<UUID>> viewers = new ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        position(event.getInventory().getLocation()).ifPresent(position ->
                viewers.computeIfAbsent(position, ignored -> ConcurrentHashMap.newKeySet())
                        .add(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        position(event.getInventory().getLocation()).ifPresent(position -> remove(position, event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        removePlayer(event.getPlayer().getUniqueId());
    }

    public boolean hasViewers(BlockPosition position) {
        Set<UUID> current = viewers.get(position);
        return current != null && !current.isEmpty();
    }

    private void remove(BlockPosition position, UUID playerUuid) {
        viewers.computeIfPresent(position, (ignored, current) -> {
            current.remove(playerUuid);
            return current.isEmpty() ? null : current;
        });
    }

    private void removePlayer(UUID playerUuid) {
        viewers.forEach((position, ignored) -> remove(position, playerUuid));
    }

    private static java.util.Optional<BlockPosition> position(Location location) {
        if (location == null || location.getWorld() == null) {
            return java.util.Optional.empty();
        }
        Block block = location.getBlock();
        return java.util.Optional.of(BlockPosition.from(block));
    }
}
