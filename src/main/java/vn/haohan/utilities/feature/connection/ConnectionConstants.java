package vn.haohan.utilities.feature.connection;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConnectionConstants {
    private ConnectionConstants() {}

    public static final String METADATA_CONNECTION_ID = "connection_id";
    public static final String METADATA_CONNECTION_TYPE = "connection_type";
    public static final String METADATA_ROLE = "connection_role"; // "link" or "hitbox"

    public static final NamespacedKey KEY_CONNECTION_ID = new NamespacedKey("haohanutilities", "conn_id");
    public static final NamespacedKey KEY_CONNECTION_TYPE = new NamespacedKey("haohanutilities", "conn_type");
    public static final NamespacedKey KEY_CONNECTION_ROLE = new NamespacedKey("haohanutilities", "conn_role");

    public static NamespacedKey keyConnectionId(JavaPlugin plugin) {
        return KEY_CONNECTION_ID;
    }

    public static NamespacedKey keyConnectionType(JavaPlugin plugin) {
        return KEY_CONNECTION_TYPE;
    }

    public static NamespacedKey keyConnectionRole(JavaPlugin plugin) {
        return KEY_CONNECTION_ROLE;
    }
}
