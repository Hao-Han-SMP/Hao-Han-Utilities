package vn.haohansmp.utilities.database;

import vn.haohansmp.utilities.carry.BlockPosition;
import vn.haohansmp.utilities.carry.CarryRecord;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarryRepository {
    void insertPrepared(CarryRecord record) throws SQLException;
    void markCarried(UUID carryId) throws SQLException;
    void markPlacing(UUID carryId, BlockPosition destination) throws SQLException;
    void markPlaced(UUID carryId) throws SQLException;
    void markRestored(UUID carryId) throws SQLException;
    void markFailed(UUID carryId, String reason) throws SQLException;
    Optional<CarryRecord> findActiveByPlayer(UUID playerUuid) throws SQLException;
    Optional<CarryRecord> findById(UUID carryId) throws SQLException;
    List<CarryRecord> findUnfinished() throws SQLException;
}
