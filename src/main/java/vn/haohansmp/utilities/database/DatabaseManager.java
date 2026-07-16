package vn.haohansmp.utilities.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private String jdbcUrl;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public DatabaseManager(Path dbPath) {
        this.plugin = null;
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    public void initialize() throws SQLException {
        if (jdbcUrl == null) {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Cannot create plugin data folder");
            }
            Path dbPath = plugin.getDataFolder().toPath().resolve("carry-blocks.db");
            jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        }

        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS carried_objects (
                        carry_id TEXT PRIMARY KEY,
                        player_uuid TEXT NOT NULL,
                        original_world_uuid TEXT NOT NULL,
                        original_x INTEGER NOT NULL,
                        original_y INTEGER NOT NULL,
                        original_z INTEGER NOT NULL,
                        placement_world_uuid TEXT,
                        placement_x INTEGER,
                        placement_y INTEGER,
                        placement_z INTEGER,
                        status TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        type_key TEXT NOT NULL,
                        block_data TEXT,
                        payload_data BLOB NOT NULL,
                        visual_item BLOB NOT NULL,
                        source_entity_uuid TEXT,
                        failure_reason TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_carried_objects_player ON carried_objects(player_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_carried_objects_status ON carried_objects(status)");
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_carried_objects_one_active_player
                    ON carried_objects(player_uuid)
                    WHERE status IN ('PREPARED','CARRIED','PLACING')
                    """);
            failIfLegacyCarriesAreActive(connection);
        }
    }

    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private static void failIfLegacyCarriesAreActive(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery("""
                     SELECT 1 FROM sqlite_master
                     WHERE type = 'table' AND name = 'carried_blocks'
                     """)) {
            if (!tables.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet records = statement.executeQuery("""
                     SELECT COUNT(*) FROM carried_blocks
                     WHERE status IN ('PREPARED','CARRIED','PLACING')
                     """)) {
            if (records.next() && records.getInt(1) > 0) {
                throw new SQLException(
                        "Legacy carry records are still active. Start the previous plugin version, "
                                + "restore them, then upgrade again. The old table was left untouched."
                );
            }
        }
    }
}
