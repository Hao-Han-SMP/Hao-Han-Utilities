package vn.haohansmp.utilities.recovery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.carry.BlockPosition;
import vn.haohansmp.utilities.carry.CarryRecord;
import vn.haohansmp.utilities.carry.CarryService;
import vn.haohansmp.utilities.carry.CarryStatus;
import vn.haohansmp.utilities.carry.PayloadVerifier;
import vn.haohansmp.utilities.database.CarryRepository;
import vn.haohansmp.utilities.serializer.BlockSerializerRegistry;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public final class RecoveryService {
    private final JavaPlugin plugin;
    private final ExecutorService databaseExecutor;
    private final CarryRepository repository;
    private final CarryService carryService;
    private final BlockSerializerRegistry serializers;

    public RecoveryService(JavaPlugin plugin, ExecutorService databaseExecutor,
                           CarryRepository repository, CarryService carryService,
                           BlockSerializerRegistry serializers) {
        this.plugin = plugin;
        this.databaseExecutor = databaseExecutor;
        this.repository = repository;
        this.carryService = carryService;
        this.serializers = serializers;
    }

    public void recoverOnStartup() {
        if (!plugin.getConfig().getBoolean("recovery.restore-on-startup", true)) {
            return;
        }
        database(repository::findUnfinished).whenComplete((records, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Cannot load unfinished carry records", unwrap(error));
                return;
            }
            sync(() -> records.forEach(this::evaluate));
        });
    }

    public void recoverPlayer(Player player) {
        database(() -> repository.findActiveByPlayer(player.getUniqueId())).whenComplete((record, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Cannot recover carry for " + player.getUniqueId(), unwrap(error));
                return;
            }
            record.ifPresent(value -> sync(() -> evaluate(value)));
        });
    }

    private void evaluate(CarryRecord record) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Recovery world checks must run on the server thread");
        }
        switch (record.status()) {
            case PREPARED -> recoverPrepared(record);
            case CARRIED -> activateIfOnline(record);
            case PLACING -> recoverPlacing(record);
            default -> { }
        }
    }

    private void recoverPrepared(CarryRecord record) {
        Block original = loadedBlock(record.originalPosition());
        if (original == null) {
            return;
        }
        String current = original.getType().getKey().asString();
        if (current.equals(record.payload().material())
                && PayloadVerifier.matchesBlock(original, record.payload(), serializers)) {
            databaseVoid(() -> repository.markRestored(record.carryId()));
        } else if (original.getType() == Material.AIR) {
            databaseVoid(() -> repository.markCarried(record.carryId()));
            activateIfOnline(record);
        } else {
            databaseVoid(() -> repository.markFailed(record.carryId(), "original position occupied during PREPARED recovery"));
        }
    }

    private void recoverPlacing(CarryRecord record) {
        if (record.placementPosition() == null) {
            databaseVoid(() -> repository.markCarried(record.carryId()));
            activateIfOnline(record);
            return;
        }
        Block destination = loadedBlock(record.placementPosition());
        if (destination == null) {
            return;
        }
        String current = destination.getType().getKey().asString();
        if (current.equals(record.payload().material())
                && PayloadVerifier.matchesBlock(destination, record.payload(), serializers)) {
            databaseVoid(() -> repository.markPlaced(record.carryId()));
        } else if (destination.getType() == Material.AIR) {
            databaseVoid(() -> repository.markCarried(record.carryId()));
            activateIfOnline(record);
        } else {
            databaseVoid(() -> repository.markFailed(record.carryId(), "destination occupied during PLACING recovery"));
        }
    }

    private void activateIfOnline(CarryRecord record) {
        Player player = Bukkit.getPlayer(record.playerUuid());
        if (player != null && player.isOnline()) {
            carryService.activate(record, player);
        }
    }

    private static Block loadedBlock(BlockPosition position) {
        World world = Bukkit.getWorld(position.worldUuid());
        if (world == null || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            return null;
        }
        return position.block(world);
    }

    private <T> CompletableFuture<T> database(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    private void databaseVoid(SqlRunnable runnable) {
        CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor).exceptionally(error -> {
            plugin.getLogger().log(Level.SEVERE, "Recovery database update failed", unwrap(error));
            return null;
        });
    }

    private void sync(Runnable task) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    @FunctionalInterface private interface SqlSupplier<T> { T get() throws SQLException; }
    @FunctionalInterface private interface SqlRunnable { void run() throws SQLException; }
}
