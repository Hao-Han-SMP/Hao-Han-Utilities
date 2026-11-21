package vn.haohan.utilities.feature.connection;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class ConnectionDatabase {
    private final JavaPlugin plugin;
    private final String jdbcUrl;

    public ConnectionDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        Path dbPath = plugin.getDataFolder().toPath().resolve("connections.db");
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    public synchronized void initialize() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data folder for connections");
        }
        try (Connection conn = openConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS connections (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    creator_uuid TEXT NOT NULL,
                    world_uuid TEXT NOT NULL,
                    start_x REAL NOT NULL,
                    start_y REAL NOT NULL,
                    start_z REAL NOT NULL,
                    end_x REAL NOT NULL,
                    end_y REAL NOT NULL,
                    end_z REAL NOT NULL,
                    anchor_a_x INTEGER NOT NULL DEFAULT 0,
                    anchor_a_y INTEGER NOT NULL DEFAULT 0,
                    anchor_a_z INTEGER NOT NULL DEFAULT 0,
                    anchor_b_x INTEGER NOT NULL DEFAULT 0,
                    anchor_b_y INTEGER NOT NULL DEFAULT 0,
                    anchor_b_z INTEGER NOT NULL DEFAULT 0,
                    material TEXT NOT NULL DEFAULT 'IRON_CHAIN',
                    created_at INTEGER NOT NULL
                )
            """);

            String[] anchorCols = {
                "anchor_a_x", "anchor_a_y", "anchor_a_z",
                "anchor_b_x", "anchor_b_y", "anchor_b_z"
            };
            for (String col : anchorCols) {
                try {
                    stmt.executeUpdate("ALTER TABLE connections ADD COLUMN " + col + " INTEGER NOT NULL DEFAULT 0");
                } catch (SQLException ignored) {
                    // Column already exists
                }
            }
            try {
                stmt.executeUpdate("ALTER TABLE connections ADD COLUMN material TEXT NOT NULL DEFAULT 'IRON_CHAIN'");
            } catch (SQLException ignored) {
                // Column already exists
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize connections.db", e);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public synchronized void save(ConnectionRecord record) {
        String sql = """
            INSERT OR REPLACE INTO connections 
            (id, type, creator_uuid, world_uuid, start_x, start_y, start_z, end_x, end_y, end_z,
             anchor_a_x, anchor_a_y, anchor_a_z, anchor_b_x, anchor_b_y, anchor_b_z, material, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Material storedMat = "LEASH".equalsIgnoreCase(record.type()) ? Material.LEAD : record.chainMaterial();
            ps.setString(1, record.id().toString());
            ps.setString(2, record.type());
            ps.setString(3, record.creatorUuid().toString());
            ps.setString(4, record.worldUuid().toString());
            ps.setDouble(5, record.startX());
            ps.setDouble(6, record.startY());
            ps.setDouble(7, record.startZ());
            ps.setDouble(8, record.endX());
            ps.setDouble(9, record.endY());
            ps.setDouble(10, record.endZ());
            ps.setInt(11, record.anchorA_X());
            ps.setInt(12, record.anchorA_Y());
            ps.setInt(13, record.anchorA_Z());
            ps.setInt(14, record.anchorB_X());
            ps.setInt(15, record.anchorB_Y());
            ps.setInt(16, record.anchorB_Z());
            ps.setString(17, storedMat.name());
            ps.setLong(18, record.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save connection " + record.id(), e);
        }
    }

    public synchronized void delete(UUID id) {
        String sql = "DELETE FROM connections WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete connection " + id, e);
        }
    }

    public synchronized List<ConnectionRecord> loadAll() {
        List<ConnectionRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM connections";
        try (Connection conn = openConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String type = rs.getString("type");
                String matName = rs.getString("material");
                Material material = matName != null ? Material.matchMaterial(matName) : null;
                if (material == null) {
                    material = "LEASH".equalsIgnoreCase(type) ? Material.LEAD : Material.IRON_CHAIN;
                }
                list.add(new ConnectionRecord(
                        UUID.fromString(rs.getString("id")),
                        type,
                        material,
                        UUID.fromString(rs.getString("creator_uuid")),
                        UUID.fromString(rs.getString("world_uuid")),
                        rs.getDouble("start_x"),
                        rs.getDouble("start_y"),
                        rs.getDouble("start_z"),
                        rs.getDouble("end_x"),
                        rs.getDouble("end_y"),
                        rs.getDouble("end_z"),
                        rs.getInt("anchor_a_x"),
                        rs.getInt("anchor_a_y"),
                        rs.getInt("anchor_a_z"),
                        rs.getInt("anchor_b_x"),
                        rs.getInt("anchor_b_y"),
                        rs.getInt("anchor_b_z"),
                        rs.getLong("created_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load connections", e);
        }
        return list;
    }
}
