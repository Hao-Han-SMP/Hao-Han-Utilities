package vn.haohansmp.utilities.carry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import vn.haohansmp.utilities.config.MessageService;
import vn.haohansmp.utilities.database.CarryRepository;
import vn.haohansmp.utilities.listener.ViewerTracker;
import vn.haohansmp.utilities.protection.ProtectionService;
import vn.haohansmp.utilities.render.CarryRenderer;
import vn.haohansmp.utilities.serializer.BlockSerializer;
import vn.haohansmp.utilities.serializer.BlockSerializerRegistry;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class CarryService {
    private final JavaPlugin plugin;
    private final ExecutorService databaseExecutor;
    private final CarryRepository repository;
    private final CarrySessionManager sessions;
    private final CarryValidator validator;
    private final CarryLockManager locks;
    private final ViewerTracker viewers;
    private final BlockSerializerRegistry serializers;
    private final ProtectionService protection;
    private final CarryRenderer renderer;
    private final MessageService messages;
    private final FingerprintService fingerprints = new FingerprintService();
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private volatile boolean accepting = true;
    private BukkitTask timeoutTask;

    public CarryService(
            JavaPlugin plugin,
            ExecutorService databaseExecutor,
            CarryRepository repository,
            CarrySessionManager sessions,
            CarryValidator validator,
            CarryLockManager locks,
            ViewerTracker viewers,
            BlockSerializerRegistry serializers,
            ProtectionService protection,
            CarryRenderer renderer,
            MessageService messages
    ) {
        this.plugin = plugin;
        this.databaseExecutor = databaseExecutor;
        this.repository = repository;
        this.sessions = sessions;
        this.validator = validator;
        this.locks = locks;
        this.viewers = viewers;
        this.serializers = serializers;
        this.protection = protection;
        this.renderer = renderer;
        this.messages = messages;
    }

    public boolean isCarrying(UUID playerUuid) {
        return sessions.isCarrying(playerUuid);
    }

    public void start() {
        requireMainThread();
        if (timeoutTask == null) {
            timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::restoreExpiredSessions, 20L, 20L);
        }
    }

    public Optional<CarrySession> session(UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public void pickup(Player player, Block block) {
        requireMainThread();
        UUID playerUuid = player.getUniqueId();
        if (!accepting) {
            messages.send(player, "plugin-stopping");
            return;
        }
        if (cooldowns.getOrDefault(playerUuid, 0L) > System.currentTimeMillis()) {
            messages.send(player, "cooldown");
            return;
        }

        String denied = validator.validatePickup(player, block, pendingPlayers.contains(playerUuid));
        if (denied != null) {
            messages.send(player, denied);
            return;
        }
        if (!pendingPlayers.add(playerUuid)) {
            messages.send(player, "already-carrying");
            return;
        }

        UUID carryId = UUID.randomUUID();
        BlockPosition position = BlockPosition.from(block);
        try {
            if (!protection.canPickup(player, block, carryId)) {
                messages.send(player, "no-permission");
                pendingPlayers.remove(playerUuid);
                return;
            }
            if (!locks.lock(position)) {
                pendingPlayers.remove(playerUuid);
                messages.send(player, "block-is-busy");
                return;
            }

            BlockState snapshot = block.getState();
            BlockSerializer serializer = serializers.serializerFor(snapshot);
            CarriedBlockPayload payload = serializer.capture(block, snapshot);
            BlockFingerprint fingerprint = fingerprints.create(block);
            Instant now = Instant.now();
            CarryRecord record = new CarryRecord(carryId, playerUuid, position, null,
                    CarryStatus.PREPARED, payload, now, now);

            database(() -> repository.insertPrepared(record)).whenComplete((ignored, error) -> sync(() -> {
                if (error != null) {
                    logFailure("PREPARED write failed", carryId, error);
                    messages.sendIfOnline(Bukkit.getPlayer(playerUuid), "database-error");
                    finishPickupAttempt(playerUuid, position);
                    return;
                }
                completePickup(record, fingerprint);
            }));
        } catch (RuntimeException exception) {
            logFailure("Pickup snapshot failed", carryId, exception);
            messages.send(player, "snapshot-error");
            finishPickupAttempt(playerUuid, position);
        }
    }

    private void completePickup(CarryRecord record, BlockFingerprint expected) {
        UUID playerUuid = record.playerUuid();
        BlockPosition position = record.originalPosition();
        Player player = Bukkit.getPlayer(playerUuid);
        World world = Bukkit.getWorld(position.worldUuid());
        if (player == null || !player.isOnline() || world == null
                || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            database(() -> repository.markRestored(record.carryId()));
            finishPickupAttempt(playerUuid, position);
            return;
        }

        Block block = position.block(world);
        if (!expected.equals(fingerprints.create(block)) || viewers.hasViewers(position)
                || !protection.canPickupRecheck(player, block, record.carryId())) {
            database(() -> repository.markRestored(record.carryId()));
            messages.send(player, "block-changed");
            finishPickupAttempt(playerUuid, position);
            return;
        }

        BlockSerializer serializer = serializers.serializerFor(block.getState());
        try {
            block.setType(Material.AIR, false);
            CarrySession session = new CarrySession(record.carryId(), playerUuid, position, record.payload());
            if (!sessions.add(session)) {
                serializer.restore(block, record.payload());
                database(() -> repository.markRestored(record.carryId()));
                messages.send(player, "already-carrying");
                return;
            }
            try {
                renderer.spawn(player, session);
            } catch (RuntimeException renderFailure) {
                sessions.remove(playerUuid);
                serializer.restore(block, record.payload());
                database(() -> repository.markRestored(record.carryId()));
                throw renderFailure;
            }

            database(() -> repository.markCarried(record.carryId())).whenComplete((ignored, error) -> {
                if (error != null) logFailure("CARRIED write failed", record.carryId(), error);
            });
            cooldowns.put(playerUuid, System.currentTimeMillis()
                    + plugin.getConfig().getLong("pickup.cooldown-milliseconds", 500L));
            audit(record.carryId(), player, record.payload().material(), "PREPARED", "CARRIED", position, null);
            messages.send(player, "pickup-success", Map.of("block", displayMaterial(record.payload().material())));
        } catch (RuntimeException exception) {
            logFailure("Pickup remove/render failed", record.carryId(), exception);
            if (block.isEmpty()) {
                try {
                    serializer.restore(block, record.payload());
                    database(() -> repository.markRestored(record.carryId()));
                } catch (RuntimeException rollbackFailure) {
                    logFailure("Pickup rollback failed", record.carryId(), rollbackFailure);
                    database(() -> repository.markFailed(record.carryId(), "pickup rollback failed"));
                }
            }
            messages.send(player, "pickup-failed");
        } finally {
            finishPickupAttempt(playerUuid, position);
        }
    }

    public void handlePlacement(PlayerInteractEvent event) {
        requireMainThread();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        CarrySession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (!session.beginPlacement()) {
            return;
        }

        Block clicked = event.getClickedBlock();
        Block destination = clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
        String denied = validator.validatePlacement(player, session, destination);
        if (denied != null) {
            session.cancelPlacement();
            messages.send(player, denied);
            return;
        }
        BlockData data = Bukkit.createBlockData(session.payload().blockData());
        if (!protection.canPlace(player, destination, data, session.payload())) {
            session.cancelPlacement();
            messages.send(player, "no-permission");
            return;
        }

        BlockPosition position = BlockPosition.from(destination);
        if (!locks.lock(position)) {
            session.cancelPlacement();
            messages.send(player, "block-is-busy");
            return;
        }
        BlockFingerprint destinationFingerprint = fingerprints.create(destination);
        database(() -> repository.markPlacing(session.carryId(), position)).whenComplete((ignored, error) -> sync(() -> {
            if (error != null) {
                logFailure("PLACING write failed", session.carryId(), error);
                session.cancelPlacement();
                locks.unlock(position);
                messages.sendIfOnline(Bukkit.getPlayer(session.playerUuid()), "database-error");
                return;
            }
            completePlacement(session, position, destinationFingerprint);
        }));
    }

    private void completePlacement(CarrySession session, BlockPosition position, BlockFingerprint expected) {
        Player player = Bukkit.getPlayer(session.playerUuid());
        World world = Bukkit.getWorld(position.worldUuid());
        if (player == null || !player.isOnline() || world == null
                || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            rollbackPlacementState(session, position, player, "player or chunk unavailable");
            return;
        }
        Block destination = position.block(world);
        if (!expected.equals(fingerprints.create(destination))) {
            rollbackPlacementState(session, position, player, "destination changed");
            messages.send(player, "destination-changed");
            return;
        }

        try {
            destination.setBlockData(Bukkit.createBlockData(session.payload().blockData()), false);
            BlockSerializer serializer = serializers.serializerFor(destination.getState());
            serializer.restore(destination, session.payload());
            CarriedBlockPayload verified = serializer.capture(destination, destination.getState());
            if (!PayloadVerifier.matches(session.payload(), verified)) {
                throw new IllegalStateException("Restored payload verification failed");
            }

            renderer.remove(session);
            sessions.remove(session.playerUuid());
            locks.unlock(position);
            database(() -> repository.markPlaced(session.carryId())).whenComplete((ignored, error) -> {
                if (error != null) logFailure("PLACED write failed", session.carryId(), error);
            });
            audit(session.carryId(), player, session.payload().material(), "PLACING", "PLACED",
                    session.originalPosition(), position);
            messages.send(player, "place-success", Map.of("block", displayMaterial(session.payload().material())));
        } catch (RuntimeException exception) {
            destination.setType(Material.AIR, false);
            logFailure("Placement restore failed", session.carryId(), exception);
            rollbackPlacementState(session, position, player, "restore failed");
            messages.send(player, "place-failed");
        }
    }

    private void rollbackPlacementState(CarrySession session, BlockPosition position, Player player, String reason) {
        session.cancelPlacement();
        locks.unlock(position);
        database(() -> repository.markCarried(session.carryId())).whenComplete((ignored, error) -> {
            if (error != null) logFailure("Placement rollback status failed", session.carryId(), error);
        });
        if (player != null) {
            plugin.getLogger().warning("Carry " + session.carryId() + " kept as CARRIED: " + reason);
        }
    }

    public void restoreOriginal(UUID playerUuid) {
        requireMainThread();
        CarrySession session = sessions.get(playerUuid).orElse(null);
        if (session == null || !session.beginPlacement()) {
            return;
        }
        restoreSession(session, session.originalPosition(), false);
    }

    public void restoreHere(Player admin, CarryRecord record, Block destination) {
        requireMainThread();
        if (!destination.isEmpty() && !destination.isReplaceable()) {
            messages.send(admin, "invalid-destination");
            return;
        }
        BlockPosition position = BlockPosition.from(destination);
        database(() -> repository.markPlacing(record.carryId(), position)).whenComplete((ignored, error) -> sync(() -> {
            if (error != null) {
                messages.send(admin, "database-error");
                return;
            }
            try {
                destination.setBlockData(Bukkit.createBlockData(record.payload().blockData()), false);
                serializers.serializerFor(destination.getState()).restore(destination, record.payload());
                sessions.get(record.playerUuid()).ifPresent(renderer::remove);
                sessions.remove(record.playerUuid());
                database(() -> repository.markRestored(record.carryId()));
                messages.send(admin, "recover-success");
            } catch (RuntimeException exception) {
                destination.setType(Material.AIR, false);
                database(() -> repository.markCarried(record.carryId()));
                logFailure("Admin recovery failed", record.carryId(), exception);
                messages.send(admin, "place-failed");
            }
        }));
    }

    private void restoreSession(CarrySession session, BlockPosition target, boolean shutdown) {
        World world = Bukkit.getWorld(target.worldUuid());
        if (world == null || !world.isChunkLoaded(target.x() >> 4, target.z() >> 4)) {
            session.cancelPlacement();
            return;
        }
        Block block = target.block(world);
        if (!block.isEmpty() && !block.isReplaceable()) {
            session.cancelPlacement();
            return;
        }

        Runnable restore = () -> {
            try {
                block.setBlockData(Bukkit.createBlockData(session.payload().blockData()), false);
                serializers.serializerFor(block.getState()).restore(block, session.payload());
                renderer.remove(session);
                sessions.remove(session.playerUuid());
                database(() -> repository.markRestored(session.carryId()));
                audit(session.carryId(), Bukkit.getPlayer(session.playerUuid()), session.payload().material(),
                        "CARRIED", "RESTORED", session.originalPosition(), target);
            } catch (RuntimeException exception) {
                block.setType(Material.AIR, false);
                session.cancelPlacement();
                logFailure("Original restore failed", session.carryId(), exception);
            }
        };

        if (shutdown) {
            restore.run();
        } else {
            database(() -> repository.markPlacing(session.carryId(), target)).whenComplete((ignored, error) -> sync(() -> {
                if (error != null) {
                    session.cancelPlacement();
                    logFailure("Original restore prepare failed", session.carryId(), error);
                    return;
                }
                if (block.isEmpty() || block.isReplaceable()) {
                    restore.run();
                } else {
                    session.cancelPlacement();
                    database(() -> repository.markCarried(session.carryId()));
                }
            }));
        }
    }

    public void activate(CarryRecord record, Player player) {
        requireMainThread();
        if (sessions.isCarrying(record.playerUuid())) {
            return;
        }
        CarrySession session = new CarrySession(record.carryId(), record.playerUuid(),
                record.originalPosition(), record.payload());
        if (sessions.add(session)) {
            renderer.spawn(player, session);
            if (record.status() != CarryStatus.CARRIED) {
                database(() -> repository.markCarried(record.carryId()));
            }
            messages.send(player, "recovery-loaded", Map.of("block", displayMaterial(record.payload().material())));
        }
    }

    public CompletableFuture<Optional<CarryRecord>> findActive(UUID playerUuid) {
        return databaseValue(() -> repository.findActiveByPlayer(playerUuid));
    }

    public CompletableFuture<Optional<CarryRecord>> findById(UUID carryId) {
        return databaseValue(() -> repository.findById(carryId));
    }

    public void shutdown() {
        requireMainThread();
        accepting = false;
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        for (CarrySession session : sessions.sessions()) {
            if (session.beginPlacement()) {
                restoreSession(session, session.originalPosition(), true);
            } else {
                renderer.remove(session);
            }
        }
    }

    private void restoreExpiredSessions() {
        long maximumMillis = Math.max(1L,
                plugin.getConfig().getLong("carrying.maximum-duration-seconds", 300L)) * 1000L;
        long now = System.currentTimeMillis();
        for (CarrySession session : sessions.sessions()) {
            if (now - session.startedAtMillis() >= maximumMillis) {
                Player player = Bukkit.getPlayer(session.playerUuid());
                if (player != null) messages.send(player, "carry-timeout");
                restoreOriginal(session.playerUuid());
            }
        }
    }

    private void finishPickupAttempt(UUID playerUuid, BlockPosition position) {
        pendingPlayers.remove(playerUuid);
        locks.unlock(position);
    }

    private CompletableFuture<Void> database(SqlRunnable runnable) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    private <T> CompletableFuture<T> databaseValue(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    private void sync(Runnable runnable) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bukkit world access must run on the server thread");
        }
    }

    private void logFailure(String message, UUID carryId, Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        plugin.getLogger().log(Level.SEVERE, message + " [carryId=" + carryId + "]", cause);
    }

    private void audit(UUID carryId, Player player, String material, String from, String to,
                       BlockPosition original, BlockPosition destination) {
        plugin.getLogger().info("AUDIT carryId=" + carryId
                + " player=" + (player == null ? "offline" : player.getName() + "/" + player.getUniqueId())
                + " material=" + material + " status=" + from + "->" + to
                + " original=" + original + " destination=" + destination);
    }

    private static String displayMaterial(String key) {
        int separator = key.indexOf(':');
        return (separator >= 0 ? key.substring(separator + 1) : key).replace('_', ' ');
    }

    @FunctionalInterface
    private interface SqlRunnable { void run() throws SQLException; }

    @FunctionalInterface
    private interface SqlSupplier<T> { T get() throws SQLException; }
}
