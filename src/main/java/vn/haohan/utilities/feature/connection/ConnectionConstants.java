package vn.haohan.utilities.feature.connection;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Set;

public final class ConnectionConstants {
    private ConnectionConstants() {}

    public static final Set<Material> CHAIN_MATERIALS = EnumSet.of(
            Material.IRON_CHAIN,
            Material.COPPER_CHAIN,
            Material.EXPOSED_COPPER_CHAIN,
            Material.WEATHERED_COPPER_CHAIN,
            Material.OXIDIZED_COPPER_CHAIN,
            Material.WAXED_COPPER_CHAIN,
            Material.WAXED_EXPOSED_COPPER_CHAIN,
            Material.WAXED_WEATHERED_COPPER_CHAIN,
            Material.WAXED_OXIDIZED_COPPER_CHAIN
    );

    public static boolean isChainMaterial(Material material) {
        return material != null && CHAIN_MATERIALS.contains(material);
    }

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
