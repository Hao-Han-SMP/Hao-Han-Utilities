package vn.haohansmp.utilities.carry;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CarryPreferences {
    private final JavaPlugin plugin;
    private final NamespacedKey enabledKey;
    private final NamespacedKey activationKey;

    public CarryPreferences(JavaPlugin plugin) {
        this.plugin = plugin;
        enabledKey = new NamespacedKey(plugin, "carry_enabled");
        activationKey = new NamespacedKey(plugin, "carry_activation_key");
    }

    public boolean isEnabled(Player player) {
        Byte stored = player.getPersistentDataContainer().get(enabledKey, PersistentDataType.BYTE);
        return stored == null
                ? plugin.getConfig().getBoolean("carrying.enabled-by-default", true)
                : stored != 0;
    }

    public boolean toggle(Player player) {
        boolean enabled = !isEnabled(player);
        setEnabled(player, enabled);
        return enabled;
    }

    public void setEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(
                enabledKey,
                PersistentDataType.BYTE,
                enabled ? (byte) 1 : (byte) 0
        );
    }

    public CarryActivationKey activationKey(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        String stored = data.get(activationKey, PersistentDataType.STRING);
        if (stored != null) {
            CarryActivationKey parsed = CarryActivationKey.parse(stored).orElse(null);
            if (parsed != null) {
                return parsed;
            }
            data.remove(activationKey);
        }
        return CarryActivationKey.parse(
                plugin.getConfig().getString("carrying.default-activation-key", "sprint")
        ).orElse(CarryActivationKey.SPRINT);
    }

    public void setActivationKey(Player player, CarryActivationKey key) {
        player.getPersistentDataContainer().set(
                activationKey,
                PersistentDataType.STRING,
                key.configName()
        );
    }
}
