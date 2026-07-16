package vn.haohansmp.utilities.database;

import vn.haohansmp.utilities.carry.BlockPosition;
import vn.haohansmp.utilities.carry.CarryKind;
import vn.haohansmp.utilities.carry.CarryPayload;
import vn.haohansmp.utilities.carry.CarryRecord;
import vn.haohansmp.utilities.carry.CarryStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SQLiteCarryRepository implements CarryRepository {
    private static final String SELECT_COLUMNS = """
            carry_id, player_uuid,
            original_world_uuid, original_x, original_y, original_z,
            placement_world_uuid, placement_x, placement_y, placement_z,
            status, kind, type_key, block_data, payload_data, visual_item,
            source_entity_uuid, created_at, updated_at
            """;

    private final DatabaseManager database;

    public SQLiteCarryRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public void insertPrepared(CarryRecord record) throws SQLException {
        String sql = """
                INSERT INTO carried_objects (
                    carry_id, player_uuid,
                    original_world_uuid, original_x, original_y, original_z,
                    status, kind, type_key, block_data, payload_data, visual_item,
                    source_entity_uuid, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            BlockPosition original = record.originalPosition();
            CarryPayload payload = record.payload();
            statement.setString(1, record.carryId().toString());
            statement.setString(2, record.playerUuid().toString());
            statement.setString(3, original.worldUuid().toString());
            statement.setInt(4, original.x());
            statement.setInt(5, original.y());
            statement.setInt(6, original.z());
            statement.setString(7, CarryStatus.PREPARED.name());
            statement.setString(8, payload.kind().name());
            statement.setString(9, payload.typeKey());
            statement.setString(10, payload.blockData());
            statement.setBytes(11, payload.data());
            statement.setBytes(12, payload.visualItem());
            statement.setString(13, payload.sourceEntityUuid() == null ? null : payload.sourceEntityUuid().toString());
            statement.setLong(14, record.createdAt().toEpochMilli());
            statement.setLong(15, record.updatedAt().toEpochMilli());
            statement.executeUpdate();
        }
    }

    @Override
    public void markCarried(UUID carryId) throws SQLException {
        updateStatus(carryId, CarryStatus.CARRIED, null);
    }

    @Override
    public void markPlacing(UUID carryId, BlockPosition destination) throws SQLException {
        String sql = """
                UPDATE carried_objects SET status = ?, placement_world_uuid = ?, placement_x = ?,
                placement_y = ?, placement_z = ?, updated_at = ? WHERE carry_id = ?
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, CarryStatus.PLACING.name());
            statement.setString(2, destination.worldUuid().toString());
            statement.setInt(3, destination.x());
            statement.setInt(4, destination.y());
            statement.setInt(5, destination.z());
            statement.setLong(6, Instant.now().toEpochMilli());
            statement.setString(7, carryId.toString());
            requireSingleUpdate(statement, carryId);
        }
    }

    @Override
    public void markPlaced(UUID carryId) throws SQLException {
        updateStatus(carryId, CarryStatus.PLACED, null);
    }

    @Override
    public void markRestored(UUID carryId) throws SQLException {
        updateStatus(carryId, CarryStatus.RESTORED, null);
    }

    @Override
    public void markFailed(UUID carryId, String reason) throws SQLException {
        updateStatus(carryId, CarryStatus.FAILED, reason);
    }

    @Override
    public Optional<CarryRecord> findActiveByPlayer(UUID playerUuid) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM carried_objects WHERE player_uuid = ? "
                + "AND status IN ('PREPARED','CARRIED','PLACING') ORDER BY updated_at DESC LIMIT 1";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(read(results)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<CarryRecord> findById(UUID carryId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM carried_objects WHERE carry_id = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, carryId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(read(results)) : Optional.empty();
            }
        }
    }

    @Override
    public List<CarryRecord> findUnfinished() throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM carried_objects "
                + "WHERE status IN ('PREPARED','CARRIED','PLACING') ORDER BY created_at";
        List<CarryRecord> records = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                records.add(read(results));
            }
        }
        return records;
    }

    private void updateStatus(UUID carryId, CarryStatus status, String failureReason) throws SQLException {
        String sql = "UPDATE carried_objects SET status = ?, failure_reason = ?, updated_at = ? WHERE carry_id = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setString(2, failureReason);
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.setString(4, carryId.toString());
            requireSingleUpdate(statement, carryId);
        }
    }

    private static void requireSingleUpdate(PreparedStatement statement, UUID carryId) throws SQLException {
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Carry record not found: " + carryId);
        }
    }

    private static CarryRecord read(ResultSet results) throws SQLException {
        String placementWorld = results.getString("placement_world_uuid");
        BlockPosition placement = placementWorld == null ? null : new BlockPosition(
                UUID.fromString(placementWorld),
                results.getInt("placement_x"),
                results.getInt("placement_y"),
                results.getInt("placement_z")
        );
        return new CarryRecord(
                UUID.fromString(results.getString("carry_id")),
                UUID.fromString(results.getString("player_uuid")),
                new BlockPosition(
                        UUID.fromString(results.getString("original_world_uuid")),
                        results.getInt("original_x"),
                        results.getInt("original_y"),
                        results.getInt("original_z")
                ),
                placement,
                CarryStatus.valueOf(results.getString("status")),
                new CarryPayload(
                        CarryKind.valueOf(results.getString("kind")),
                        results.getString("type_key"),
                        results.getString("block_data"),
                        results.getBytes("payload_data"),
                        results.getBytes("visual_item"),
                        results.getString("source_entity_uuid") == null
                                ? null
                                : UUID.fromString(results.getString("source_entity_uuid"))
                ),
                Instant.ofEpochMilli(results.getLong("created_at")),
                Instant.ofEpochMilli(results.getLong("updated_at"))
        );
    }
}
