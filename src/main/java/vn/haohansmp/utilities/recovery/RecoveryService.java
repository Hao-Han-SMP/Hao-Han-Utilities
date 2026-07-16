package vn.haohansmp.utilities.recovery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.carry.BlockPosition;
import vn.haohansmp.utilities.carry.CarryKind;
import vn.haohansmp.utilities.carry.CarryRecord;
import vn.haohansmp.utilities.carry.CarryService;
import vn.haohansmp.utilities.carry.CarrySnapshotService;
import vn.haohansmp.utilities.carry.CarryStatus;
import vn.haohansmp.utilities.database.CarryRepository;
import vn.haohansmp.utilities.integration.SoulAnchorIntegration;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public final class RecoveryService {
    private final JavaPlugin plugin;
    private final CarryRepository repository;
    private final CarryService carryService;
    private final CarrySnapshotService snapshots;
    private final SoulAnchorIntegration soulAnchors;

    public RecoveryService(
            JavaPlugin plugin,
            CarryRepository repository,
            CarryService carryService,
            CarrySnapshotService snapshots,
            SoulAnchorIntegration soulAnchors
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.carryService = carryService;
        this.snapshots = snapshots;
        this.soulAnchors = soulAnchors;
    }

    public void recoverOnStartup() {
        if (!plugin.getConfig().getBoolean("recovery.restore-on-startup", true)) {
            return;
        }
        try {
            for (CarryRecord record : repository.findUnfinished()) {
                evaluate(record);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot load unfinished carry records", exception);
        }
        Bukkit.getOnlinePlayers().forEach(this::recoverPlayer);
    }

    public void recoverPlayer(Player player) {
        try {
            var active = repository.findActiveByPlayer(player.getUniqueId());
            if (active.isEmpty()) {
                return;
            }
            CarryRecord record = active.get();
            if (record.status() != CarryStatus.CARRIED) {
                evaluate(record);
                record = repository.findActiveByPlayer(player.getUniqueId()).orElse(null);
            }
            if (record != null && record.status() == CarryStatus.CARRIED) {
                carryService.activate(record, player);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot recover carry for " + player.getUniqueId(), exception);
        }
    }

    private void evaluate(CarryRecord record) throws SQLException {
        switch (record.status()) {
            case PREPARED -> recoverPrepared(record);
            case PLACING -> recoverPlacing(record);
            default -> {
            }
        }
    }

    private void recoverPrepared(CarryRecord record) throws SQLException {
        if (record.payload().kind() == CarryKind.ENTITY) {
            loadChunk(record.originalPosition());
            Entity source = record.payload().sourceEntityUuid() == null
                    ? null
                    : Bukkit.getEntity(record.payload().sourceEntityUuid());
            if (source != null && source.isValid()) {
                repository.markRestored(record.carryId());
            } else {
                repository.markCarried(record.carryId());
            }
            return;
        }

        Block original = loadedBlock(record.originalPosition());
        if (original == null) {
            return;
        }
        if (record.payload().kind() == CarryKind.SOUL_ANCHOR) {
            if (!soulAnchors.isAvailable()) {
                return;
            }
            if (soulAnchors.matches(original, record.payload())) {
                repository.markRestored(record.carryId());
            } else if (original.getType() == Material.AIR) {
                repository.markCarried(record.carryId());
            } else if (soulAnchors.matchesBackingBlock(original, record.payload())) {
                original.setType(Material.AIR, true);
                repository.markCarried(record.carryId());
            } else {
                repository.markFailed(record.carryId(), "original position occupied during Soul Anchor recovery");
            }
            return;
        }
        if (snapshots.matchesSourceBlock(original, record.payload())) {
            repository.markRestored(record.carryId());
        } else if (original.getType() == Material.AIR) {
            repository.markCarried(record.carryId());
        } else {
            repository.markFailed(record.carryId(), "original position occupied during PREPARED recovery");
        }
    }

    private void recoverPlacing(CarryRecord record) throws SQLException {
        if (record.placementPosition() == null) {
            repository.markCarried(record.carryId());
            return;
        }
        if (record.payload().kind() == CarryKind.ENTITY) {
            Entity placed = snapshots.findPlacedEntity(record.placementPosition(), record.carryId());
            if (placed != null) {
                repository.markPlaced(record.carryId());
                snapshots.clearPlacedEntityMarker(placed);
            } else {
                repository.markCarried(record.carryId());
            }
            return;
        }

        Block destination = loadedBlock(record.placementPosition());
        if (destination == null) {
            return;
        }
        if (record.payload().kind() == CarryKind.SOUL_ANCHOR) {
            if (!soulAnchors.isAvailable()) {
                return;
            }
            if (soulAnchors.matches(destination, record.payload())) {
                repository.markPlaced(record.carryId());
            } else if (soulAnchors.matchesBackingBlock(destination, record.payload())) {
                soulAnchors.restore(destination, record.payload());
                repository.markPlaced(record.carryId());
            } else if (destination.getType() == Material.AIR) {
                repository.markCarried(record.carryId());
            } else {
                repository.markFailed(record.carryId(), "destination occupied during Soul Anchor recovery");
            }
            return;
        }
        if (snapshots.matchesPlacedBlock(destination, record.payload())) {
            repository.markPlaced(record.carryId());
        } else if (destination.getType() == Material.AIR) {
            repository.markCarried(record.carryId());
        } else {
            repository.markFailed(record.carryId(), "destination occupied during PLACING recovery");
        }
    }

    private static void loadChunk(BlockPosition position) {
        World world = Bukkit.getWorld(position.worldUuid());
        if (world != null) {
            world.getChunkAt(position.x() >> 4, position.z() >> 4);
        }
    }

    private static Block loadedBlock(BlockPosition position) {
        World world = Bukkit.getWorld(position.worldUuid());
        if (world == null) {
            return null;
        }
        world.getChunkAt(position.x() >> 4, position.z() >> 4);
        return position.block(world);
    }
}
