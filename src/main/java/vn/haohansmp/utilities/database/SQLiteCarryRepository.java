package vn.haohansmp.utilities.database;

import vn.haohansmp.utilities.carry.BlockPosition;
import vn.haohansmp.utilities.carry.CarriedBlockPayload;
import vn.haohansmp.utilities.carry.CarryRecord;
import vn.haohansmp.utilities.carry.CarryStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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
            status, material, block_data, inventory_data, persistent_data,
            custom_name_json, lock_value, properties_json, created_at, updated_at
            """;

    private final DatabaseManager database;

    public SQLiteCarryRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public void insertPrepared(CarryRecord record) throws SQLException {
        String sql = """
                INSERT INTO carried_blocks (
                    carry_id, player_uuid,
                    original_world_uuid, original_x, original_y, original_z,
                    status, material, block_data, inventory_data, persistent_data,
                    custom_name_json, lock_value, properties_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            BlockPosition original = record.originalPosition();
            CarriedBlockPayload payload = record.payload();
            statement.setString(1, record.carryId().toString());
            statement.setString(2, record.playerUuid().toString());
            statement.setString(3, original.worldUuid().toString());
            statement.setInt(4, original.x());
            statement.setInt(5, original.y());
            statement.setInt(6, original.z());
            statement.setString(7, CarryStatus.PREPARED.name());
            statement.setString(8, payload.material());
            statement.setString(9, payload.blockData());
            setBytes(statement, 10, payload.inventoryData());
            setBytes(statement, 11, payload.persistentData());
            statement.setString(12, payload.customNameJson());
            statement.setString(13, payload.lock());
            statement.setString(14, PropertiesJsonCodec.encode(payload.properties()));
            statement.setLong(15, record.createdAt().toEpochMilli());
            statement.setLong(16, record.updatedAt().toEpochMilli());
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
                UPDATE carried_blocks SET status = ?, placement_world_uuid = ?, placement_x = ?,
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
        String sql = "SELECT " + SELECT_COLUMNS + " FROM carried_blocks WHERE player_uuid = ? "
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
        String sql = "SELECT " + SELECT_COLUMNS + " FROM carried_blocks WHERE carry_id = ?";
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
        String sql = "SELECT " + SELECT_COLUMNS + " FROM carried_blocks "
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
        String sql = "UPDATE carried_blocks SET status = ?, failure_reason = ?, updated_at = ? WHERE carry_id = ?";
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

    private static void setBytes(PreparedStatement statement, int index, byte[] value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BLOB);
        } else {
            statement.setBytes(index, value);
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
                new CarriedBlockPayload(
                        results.getString("material"),
                        results.getString("block_data"),
                        results.getBytes("inventory_data"),
                        results.getBytes("persistent_data"),
                        results.getString("custom_name_json"),
                        results.getString("lock_value"),
                        PropertiesJsonCodec.decode(results.getString("properties_json"))
                ),
                Instant.ofEpochMilli(results.getLong("created_at")),
                Instant.ofEpochMilli(results.getLong("updated_at"))
        );
    }
}
