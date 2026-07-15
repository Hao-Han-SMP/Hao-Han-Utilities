package vn.haohansmp.utilities.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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
                    CREATE TABLE IF NOT EXISTS carried_blocks (
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
                        material TEXT NOT NULL,
                        block_data TEXT NOT NULL,
                        inventory_data BLOB,
                        persistent_data BLOB,
                        custom_name_json TEXT,
                        lock_value TEXT,
                        properties_json TEXT NOT NULL,
                        failure_reason TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_carried_blocks_player ON carried_blocks(player_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_carried_blocks_status ON carried_blocks(status)");
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
}
