package vn.haohansmp.utilities.carry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import vn.haohansmp.utilities.config.MessageService;
import vn.haohansmp.utilities.database.CarryRepository;
import vn.haohansmp.utilities.integration.SoulAnchorIntegration;
import vn.haohansmp.utilities.protection.ProtectionService;
import vn.haohansmp.utilities.render.CarryRenderer;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public final class CarryService {
    private final JavaPlugin plugin;
    private final CarryRepository repository;
    private final CarrySessionManager sessions;
    private final CarryValidator validator;
    private final CarryPreferences preferences;
    private final CarrySnapshotService snapshots;
    private final SoulAnchorIntegration soulAnchors;
    private final ProtectionService protection;
    private final CarryRenderer renderer;
    private final MessageService messages;
    private boolean accepting = true;

    public CarryService(
            JavaPlugin plugin,
            CarryRepository repository,
            CarrySessionManager sessions,
            CarryValidator validator,
            CarryPreferences preferences,
            CarrySnapshotService snapshots,
            SoulAnchorIntegration soulAnchors,
            ProtectionService protection,
            CarryRenderer renderer,
            MessageService messages
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.sessions = sessions;
        this.validator = validator;
        this.preferences = preferences;
        this.snapshots = snapshots;
        this.soulAnchors = soulAnchors;
        this.protection = protection;
        this.renderer = renderer;
        this.messages = messages;
    }

    public boolean isCarrying(UUID playerUuid) {
        return sessions.isCarrying(playerUuid) || isCarryingPlayer(playerUuid);
    }

    public boolean isCarryingPlayer(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        return player != null && player.getPassengers().stream().anyMatch(Player.class::isInstance);
    }

    public Optional<CarrySession> session(UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public Optional<Player> carrierForVisual(Entity visual) {
        return sessions.sessions().stream()
                .filter(session -> session.visualEntity() != null
                        && session.visualEntity().getUniqueId().equals(visual.getUniqueId()))
                .map(CarrySession::playerUuid)
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    public boolean isSupportedPickupBlock(Block block) {
        return validator.isSupportedBlock(block);
    }

    public boolean isSupportedPickupEntity(Entity entity) {
        return soulAnchors.backingBlock(entity) != null || validator.isSupportedEntity(entity);
    }

    public boolean isSupportedPickupPlayer(Player player) {
        return validator.isPlayerCarryEnabled()
                && player.hasPermission("haohanutilities.carry.use")
                && preferences.isEnabled(player);
    }

    public boolean isSoulAnchor(Block block) {
        return soulAnchors.isAnchor(block);
    }

    public boolean isSoulAnchorEntity(Entity entity) {
        return soulAnchors.backingBlock(entity) != null;
    }

    public void pickupBlock(Player player, Block block) {
        requireMainThread();
        if (!accepting) {
            messages.send(player, "plugin-stopping");
            return;
        }
        String denied = validator.validateBlockPickup(player, block);
        if (denied != null) {
            if (!"unsupported-block".equals(denied)) {
                messages.send(player, denied);
            }
            return;
        }

        boolean soulAnchor = soulAnchors.isAnchor(block);
        UUID carryId = UUID.randomUUID();
        boolean canPickup;
        try {
            canPickup = soulAnchor
                    ? soulAnchors.runProtectionProbe(
                            block,
                            () -> protection.canPickupBlock(player, block, carryId)
                    )
                    : protection.canPickupBlock(player, block, carryId);
        } catch (RuntimeException exception) {
            logFailure("Cannot check block protection", carryId, exception);
            messages.send(player, "pickup-failed");
            return;
        }
        if (!canPickup) {
            messages.send(player, "no-permission");
            return;
        }

        CarryPayload payload;
        try {
            payload = soulAnchor ? soulAnchors.capture(block) : snapshots.captureBlock(block);
        } catch (RuntimeException exception) {
            logFailure("Cannot snapshot block", carryId, exception);
            messages.send(player, "snapshot-error");
            return;
        }

        CarryRecord record = preparedRecord(carryId, player, BlockPosition.from(block), payload);
        if (!insertPrepared(player, record)) {
            return;
        }

        try {
            if (payload.kind() == CarryKind.SOUL_ANCHOR) {
                soulAnchors.removeForCarry(block, payload);
            }
            block.setType(Material.AIR, true);
            activateNewSession(record, player);
            markCarriedBestEffort(record);
            messages.send(player, "pickup-success", Map.of("block", displayType(payload.typeKey())));
        } catch (RuntimeException exception) {
            logFailure("Block pickup failed", carryId, exception);
            rollbackPickup(record);
            messages.send(player, "pickup-failed");
        }
    }

    public void pickupEntity(Player player, Entity entity) {
        requireMainThread();
        Block anchorBlock = soulAnchors.backingBlock(entity);
        if (anchorBlock != null) {
            pickupBlock(player, anchorBlock);
            return;
        }
        if (!accepting) {
            messages.send(player, "plugin-stopping");
            return;
        }
        String denied = validator.validateEntityPickup(player, entity);
        if (denied != null) {
            if (!"unsupported-entity".equals(denied)) {
                messages.send(player, denied);
            }
            return;
        }

        UUID carryId = UUID.randomUUID();
        CarryPayload payload;
        try {
            payload = snapshots.captureEntity(entity);
        } catch (RuntimeException exception) {
            logFailure("Cannot snapshot entity", carryId, exception);
            messages.send(player, "snapshot-error");
            return;
        }

        CarryRecord record = preparedRecord(carryId, player, BlockPosition.from(entity.getLocation().getBlock()), payload);
        if (!insertPrepared(player, record)) {
            return;
        }

        try {
            entity.remove();
            activateNewSession(record, player);
            markCarriedBestEffort(record);
            messages.send(player, "entity-pickup-success", Map.of("entity", displayType(payload.typeKey())));
        } catch (RuntimeException exception) {
            logFailure("Entity pickup failed", carryId, exception);
            rollbackPickup(record);
            messages.send(player, "pickup-failed");
        }
    }

    public void pickupPlayer(Player carrier, Player target) {
        requireMainThread();
        if (!accepting) {
            messages.send(carrier, "plugin-stopping");
            return;
        }
        if (!preferences.isEnabled(carrier) || !isSupportedPickupPlayer(target)) {
            return;
        }
        if (isCarryingPlayer(carrier.getUniqueId())) {
            messages.send(carrier, "already-carrying");
            return;
        }
        String denied = validator.validatePlayerPickup(carrier, target);
        if (denied != null) {
            if (!"unsupported-player".equals(denied)) {
                messages.send(carrier, denied);
            }
            return;
        }
        if (!carrier.addPassenger(target)) {
            messages.send(carrier, "player-pickup-failed");
            return;
        }
        target.setFallDistance(0.0F);
        messages.send(carrier, "player-pickup-success", Map.of("player", target.getName()));
        messages.send(target, "player-carried", Map.of("player", carrier.getName()));
    }

    public void handlePlacement(PlayerInteractEvent event) {
        requireMainThread();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        place(event.getPlayer(), event.getClickedBlock(), event.getBlockFace());
    }

    public void handleObstructedPlacement(Player player) {
        requireMainThread();
        double maximumDistance = plugin.getConfig().getDouble("placement.maximum-distance", 5.0);
        RayTraceResult hit = player.rayTraceBlocks(maximumDistance, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            return;
        }
        place(player, hit.getHitBlock(), hit.getHitBlockFace());
    }

    private void place(Player player, Block clicked, BlockFace clickedFace) {
        if (placeCarriedPlayer(player, clicked, clickedFace)) {
            return;
        }

        CarrySession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        if (!session.beginPlacement()) {
            return;
        }

        Block destination = clicked.isReplaceable() ? clicked : clicked.getRelative(clickedFace);
        String denied = validator.validatePlacement(player, session, destination, clickedFace);
        if (denied != null) {
            session.cancelPlacement();
            debugPlacement(player, session, destination, clickedFace, denied);
            messages.send(player, denied);
            return;
        }

        if (session.payload().kind().isBlockLike()) {
            BlockData data = CarrySnapshotService.placementBlockData(
                    player,
                    session.payload(),
                    clickedFace
            );
            if (!protection.canPlaceBlock(player, destination, data, session.payload())) {
                session.cancelPlacement();
                messages.send(player, "no-permission");
                return;
            }
        }

        BlockPosition position = BlockPosition.from(destination);
        try {
            repository.markPlacing(session.carryId(), position);
        } catch (SQLException exception) {
            session.cancelPlacement();
            logFailure("Cannot write PLACING state", session.carryId(), exception);
            messages.send(player, "database-error");
            return;
        }

        Entity placedEntity = null;
        try {
            if (session.payload().kind() == CarryKind.BLOCK) {
                snapshots.restoreBlock(player, destination, session.payload(), clickedFace);
            } else if (session.payload().kind() == CarryKind.SOUL_ANCHOR) {
                soulAnchors.restore(destination, session.payload());
            } else {
                Location location = destination.getLocation().add(0.5, 0.0, 0.5);
                location.setYaw(player.getYaw());
                placedEntity = snapshots.restoreEntity(location, session.carryId(), session.payload());
            }
            repository.markPlaced(session.carryId());
            debugPlacement(player, session, destination, clickedFace, "placed");
            if (placedEntity != null) {
                snapshots.clearPlacedEntityMarker(placedEntity);
            }
            renderer.remove(session);
            sessions.remove(session.playerUuid());
            boolean blockLike = session.payload().kind().isBlockLike();
            messages.send(player, blockLike
                    ? "place-success"
                    : "entity-place-success", Map.of(
                    blockLike ? "block" : "entity",
                    displayType(session.payload().typeKey())
            ));
        } catch (SQLException | RuntimeException exception) {
            if (placedEntity != null && placedEntity.isValid()) {
                placedEntity.remove();
            }
            removeFailedPlacement(destination, session.payload());
            session.cancelPlacement();
            try {
                repository.markCarried(session.carryId());
            } catch (SQLException rollbackError) {
                exception.addSuppressed(rollbackError);
            }
            logFailure("Placement failed", session.carryId(), exception);
            messages.send(player, "place-failed");
        }
    }

    private boolean placeCarriedPlayer(Player carrier, Block clicked, BlockFace clickedFace) {
        Player passenger = carrier.getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst()
                .orElse(null);
        if (passenger == null) {
            return false;
        }

        Block destination = clicked.isReplaceable() ? clicked : clicked.getRelative(clickedFace);
        double maximumDistance = plugin.getConfig().getDouble("placement.maximum-distance", 5.0);
        boolean tooFar = carrier.getEyeLocation().distance(destination.getLocation().add(0.5, 0.5, 0.5))
                > maximumDistance;
        if (tooFar) {
            messages.send(carrier, "too-far");
            return true;
        }
        if (!isSafePlayerDestination(destination)) {
            messages.send(carrier, "invalid-player-destination");
            return true;
        }

        Location location = destination.getLocation().add(0.5, 0.0, 0.5);
        location.setYaw(passenger.getYaw());
        location.setPitch(passenger.getPitch());
        if (!carrier.removePassenger(passenger)) {
            messages.send(carrier, "player-place-failed");
            return true;
        }
        if (!passenger.teleport(location)) {
            carrier.addPassenger(passenger);
            messages.send(carrier, "player-place-failed");
            return true;
        }

        passenger.setFallDistance(0.0F);
        messages.send(carrier, "player-place-success", Map.of("player", passenger.getName()));
        messages.send(passenger, "player-placed", Map.of("player", carrier.getName()));
        return true;
    }

    private static boolean isSafePlayerDestination(Block destination) {
        return destination.isPassable()
                && destination.getRelative(BlockFace.UP).isPassable()
                && destination.getRelative(BlockFace.DOWN).getType().isSolid();
    }

    public void restoreHere(Player admin, CarryRecord record, Block destination) {
        requireMainThread();
        if (!destination.isEmpty() && !destination.isReplaceable()) {
            messages.send(admin, "invalid-destination");
            return;
        }
        BlockPosition position = BlockPosition.from(destination);
        Entity restoredEntity = null;
        try {
            repository.markPlacing(record.carryId(), position);
            if (record.payload().kind() == CarryKind.BLOCK) {
                snapshots.restoreBlock(admin, destination, record.payload());
            } else if (record.payload().kind() == CarryKind.SOUL_ANCHOR) {
                soulAnchors.restore(destination, record.payload());
            } else {
                restoredEntity = snapshots.restoreEntity(
                        destination.getLocation().add(0.5, 0.0, 0.5),
                        record.carryId(),
                        record.payload()
                );
            }
            repository.markRestored(record.carryId());
            if (restoredEntity != null) {
                snapshots.clearPlacedEntityMarker(restoredEntity);
            }
            sessions.get(record.playerUuid()).ifPresent(renderer::remove);
            sessions.remove(record.playerUuid());
            messages.send(admin, "recover-success");
        } catch (SQLException | RuntimeException exception) {
            if (restoredEntity != null && restoredEntity.isValid()) {
                restoredEntity.remove();
            }
            removeFailedPlacement(destination, record.payload());
            try {
                repository.markCarried(record.carryId());
            } catch (SQLException rollbackError) {
                exception.addSuppressed(rollbackError);
            }
            logFailure("Admin recovery failed", record.carryId(), exception);
            messages.send(admin, "place-failed");
        }
    }

    public void activate(CarryRecord record, Player player) {
        requireMainThread();
        if (sessions.isCarrying(record.playerUuid())) {
            return;
        }
        CarrySession session = new CarrySession(
                record.carryId(),
                record.playerUuid(),
                record.originalPosition(),
                record.payload()
        );
        if (sessions.add(session)) {
            try {
                renderer.spawn(player, session);
                if (record.status() != CarryStatus.CARRIED) {
                    markCarriedBestEffort(record);
                }
                messages.send(player, "recovery-loaded", Map.of(
                        "object",
                        displayType(record.payload().typeKey())
                ));
            } catch (RuntimeException exception) {
                sessions.remove(record.playerUuid());
                renderer.remove(session);
                logFailure("Cannot render recovered carry", record.carryId(), exception);
                messages.send(player, "snapshot-error");
            }
        }
    }

    public void detach(Player player) {
        requireMainThread();
        CarrySession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            renderer.remove(session);
        }
        releasePlayerPassengers(player);
        if (player.getVehicle() instanceof Player) {
            player.leaveVehicle();
        }
    }

    public void releaseIfCarried(Player player) {
        requireMainThread();
        if (player.getVehicle() instanceof Player) {
            player.leaveVehicle();
            player.setFallDistance(0.0F);
        }
    }

    public CompletableFuture<Optional<CarryRecord>> findActive(UUID playerUuid) {
        try {
            return CompletableFuture.completedFuture(repository.findActiveByPlayer(playerUuid));
        } catch (SQLException exception) {
            return CompletableFuture.failedFuture(new CompletionException(exception));
        }
    }

    public CompletableFuture<Optional<CarryRecord>> findById(UUID carryId) {
        try {
            return CompletableFuture.completedFuture(repository.findById(carryId));
        } catch (SQLException exception) {
            return CompletableFuture.failedFuture(new CompletionException(exception));
        }
    }

    public void shutdown() {
        requireMainThread();
        accepting = false;
        Bukkit.getOnlinePlayers().forEach(CarryService::releasePlayerPassengers);
        for (CarrySession session : sessions.sessions()) {
            renderer.remove(session);
            sessions.remove(session.playerUuid());
        }
    }

    private CarryRecord preparedRecord(UUID carryId, Player player, BlockPosition origin, CarryPayload payload) {
        Instant now = Instant.now();
        return new CarryRecord(
                carryId,
                player.getUniqueId(),
                origin,
                null,
                CarryStatus.PREPARED,
                payload,
                now,
                now
        );
    }

    private boolean insertPrepared(Player player, CarryRecord record) {
        try {
            repository.insertPrepared(record);
            return true;
        } catch (SQLException exception) {
            logFailure("Cannot write PREPARED state", record.carryId(), exception);
            messages.send(player, "database-error");
            return false;
        }
    }

    private void activateNewSession(CarryRecord record, Player player) {
        CarrySession session = new CarrySession(
                record.carryId(),
                record.playerUuid(),
                record.originalPosition(),
                record.payload()
        );
        if (!sessions.add(session)) {
            throw new IllegalStateException("Player already has an active carry session");
        }
        try {
            renderer.spawn(player, session);
        } catch (RuntimeException exception) {
            sessions.remove(player.getUniqueId());
            throw exception;
        }
    }

    private void rollbackPickup(CarryRecord record) {
        CarrySession session = sessions.remove(record.playerUuid());
        if (session != null) {
            renderer.remove(session);
        }
        World world = Bukkit.getWorld(record.originalPosition().worldUuid());
        if (world == null) {
            markFailedBestEffort(record.carryId(), "rollback world unavailable");
            return;
        }
        Block original = record.originalPosition().block(world);
        try {
            if (record.payload().kind() == CarryKind.BLOCK) {
                if (snapshots.matchesSourceBlock(original, record.payload())) {
                    repository.markRestored(record.carryId());
                    return;
                }
                if (!original.isEmpty() && !original.isReplaceable()) {
                    throw new IllegalStateException("Original block position is occupied");
                }
                snapshots.restoreBlock(original, record.payload());
            } else if (record.payload().kind() == CarryKind.SOUL_ANCHOR) {
                if (!original.isEmpty() && !original.isReplaceable()
                        && !soulAnchors.matchesBackingBlock(original, record.payload())) {
                    throw new IllegalStateException("Original Soul Anchor position is occupied");
                }
                soulAnchors.restore(original, record.payload());
            } else {
                Entity restored = snapshots.restoreEntity(
                        original.getLocation().add(0.5, 0.0, 0.5),
                        record.carryId(),
                        record.payload()
                );
                snapshots.clearPlacedEntityMarker(restored);
            }
            repository.markRestored(record.carryId());
        } catch (SQLException | RuntimeException rollbackError) {
            logFailure("Pickup rollback failed", record.carryId(), rollbackError);
            markFailedBestEffort(record.carryId(), "pickup rollback failed");
        }
    }

    private void markCarriedBestEffort(CarryRecord record) {
        try {
            repository.markCarried(record.carryId());
        } catch (SQLException exception) {
            logFailure("Cannot write CARRIED state", record.carryId(), exception);
        }
    }

    private void removeFailedPlacement(Block destination, CarryPayload payload) {
        if (payload.kind() == CarryKind.SOUL_ANCHOR) {
            if (soulAnchors.matches(destination, payload)) {
                soulAnchors.removeForCarry(destination, payload);
            }
            if (soulAnchors.matchesBackingBlock(destination, payload)) {
                destination.setType(Material.AIR, true);
            }
            return;
        }
        if (payload.kind() == CarryKind.BLOCK && snapshots.matchesPlacedBlock(destination, payload)) {
            destination.setType(Material.AIR, true);
        }
    }

    private void markFailedBestEffort(UUID carryId, String reason) {
        try {
            repository.markFailed(carryId, reason);
        } catch (SQLException exception) {
            logFailure("Cannot write FAILED state", carryId, exception);
        }
    }

    private static void releasePlayerPassengers(Player carrier) {
        carrier.getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .toList()
                .forEach(passenger -> {
                    carrier.removePassenger(passenger);
                    passenger.setFallDistance(0.0F);
                });
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bukkit world access must run on the server thread");
        }
    }

    private void logFailure(String message, UUID carryId, Throwable error) {
        plugin.getLogger().log(Level.SEVERE, message + " [carryId=" + carryId + "]", error);
    }

    private void debugPlacement(
            Player player,
            CarrySession session,
            Block destination,
            BlockFace clickedFace,
            String result
    ) {
        if (!plugin.getConfig().getBoolean("debug", false)) {
            return;
        }
        plugin.getLogger().info("[debug] placement player=" + player.getName()
                + " carryId=" + session.carryId()
                + " type=" + session.payload().typeKey()
                + " destination=" + destination.getX() + "," + destination.getY() + "," + destination.getZ()
                + " clickedFace=" + clickedFace
                + " result=" + result);
    }

    private static String displayType(String key) {
        int separator = key.indexOf(':');
        return (separator >= 0 ? key.substring(separator + 1) : key).replace('_', ' ');
    }
}
