package vn.haohansmp.utilities.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohansmp.utilities.carry.BlockPosition;
import vn.haohansmp.utilities.carry.CarriedBlockPayload;
import vn.haohansmp.utilities.carry.CarryRecord;
import vn.haohansmp.utilities.carry.CarryStatus;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        CarriedBlockPayload payload = new CarriedBlockPayload(
                "minecraft:furnace",
                "minecraft:furnace[facing=north,lit=true]",
                new byte[] {1, 2, 3},
                new byte[] {4, 5},
                "{\"text\":\"Forge\"}",
                "secret",
                Map.of("burnTime", "120", "cookTime", "30")
        );
        Instant now = Instant.now();
        repository.insertPrepared(new CarryRecord(carryId, playerId, origin, null,
                CarryStatus.PREPARED, payload, now, now));

        CarryRecord prepared = repository.findById(carryId).orElseThrow();
        assertEquals(CarryStatus.PREPARED, prepared.status());
        assertArrayEquals(payload.inventoryData(), prepared.payload().inventoryData());
        assertArrayEquals(payload.persistentData(), prepared.payload().persistentData());
        assertEquals(payload.properties(), prepared.payload().properties());

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
}
