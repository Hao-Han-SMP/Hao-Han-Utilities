package vn.haohansmp.utilities.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohansmp.utilities.carry.BlockPosition;
import vn.haohansmp.utilities.carry.CarryKind;
import vn.haohansmp.utilities.carry.CarryPayload;
import vn.haohansmp.utilities.carry.CarryRecord;
import vn.haohansmp.utilities.carry.CarryStatus;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteCarryRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsPayloadAndTransactionLifecycle() throws Exception {
        DatabaseManager database = new DatabaseManager(temporaryDirectory.resolve("carry.db"));
        database.initialize();
        SQLiteCarryRepository repository = new SQLiteCarryRepository(database);

        UUID carryId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        BlockPosition origin = new BlockPosition(UUID.randomUUID(), 10, 64, -2);
        CarryPayload payload = new CarryPayload(
                CarryKind.BLOCK,
                "minecraft:furnace",
                "minecraft:furnace[facing=north,lit=true]",
                new byte[] {1, 2, 3},
                new byte[] {4, 5},
                null
        );
        Instant now = Instant.now();
        repository.insertPrepared(new CarryRecord(carryId, playerId, origin, null,
                CarryStatus.PREPARED, payload, now, now));

        CarryRecord prepared = repository.findById(carryId).orElseThrow();
        assertEquals(CarryStatus.PREPARED, prepared.status());
        assertEquals(payload.kind(), prepared.payload().kind());
        assertEquals(payload.typeKey(), prepared.payload().typeKey());
        assertEquals(payload.blockData(), prepared.payload().blockData());
        assertArrayEquals(payload.data(), prepared.payload().data());
        assertArrayEquals(payload.visualItem(), prepared.payload().visualItem());

        repository.markCarried(carryId);
        BlockPosition destination = new BlockPosition(origin.worldUuid(), 20, 70, 4);
        repository.markPlacing(carryId, destination);
        CarryRecord placing = repository.findActiveByPlayer(playerId).orElseThrow();
        assertEquals(CarryStatus.PLACING, placing.status());
        assertEquals(destination, placing.placementPosition());

        repository.markPlaced(carryId);
        assertEquals(CarryStatus.PLACED, repository.findById(carryId).orElseThrow().status());
        assertTrue(repository.findActiveByPlayer(playerId).isEmpty());
    }

    @Test
    void enforcesOneActiveCarryPerPlayerAndPersistsEntityIdentity() throws Exception {
        DatabaseManager database = new DatabaseManager(temporaryDirectory.resolve("entities.db"));
        database.initialize();
        SQLiteCarryRepository repository = new SQLiteCarryRepository(database);

        UUID playerId = UUID.randomUUID();
        UUID sourceEntityId = UUID.randomUUID();
        BlockPosition origin = new BlockPosition(UUID.randomUUID(), 1, 64, 1);
        CarryPayload entity = new CarryPayload(
                CarryKind.ENTITY,
                "minecraft:cow",
                null,
                new byte[] {9, 8, 7},
                new byte[] {6, 5, 4},
                sourceEntityId
        );
        Instant now = Instant.now();
        CarryRecord first = new CarryRecord(
                UUID.randomUUID(), playerId, origin, null, CarryStatus.PREPARED, entity, now, now);
        CarryRecord second = new CarryRecord(
                UUID.randomUUID(), playerId, origin, null, CarryStatus.PREPARED, entity, now, now);

        repository.insertPrepared(first);
        assertThrows(java.sql.SQLException.class, () -> repository.insertPrepared(second));

        CarryRecord stored = repository.findActiveByPlayer(playerId).orElseThrow();
        assertEquals(CarryKind.ENTITY, stored.payload().kind());
        assertEquals(sourceEntityId, stored.payload().sourceEntityUuid());
        assertArrayEquals(entity.data(), stored.payload().data());
    }

    @Test
    void refusesUpgradeWhileLegacyCarryIsStillActive() throws Exception {
        Path path = temporaryDirectory.resolve("legacy.db");
        DatabaseManager database = new DatabaseManager(path);
        database.initialize();

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE carried_blocks (status TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO carried_blocks(status) VALUES ('CARRIED')");
        }

        assertThrows(java.sql.SQLException.class, database::initialize);
    }
}
