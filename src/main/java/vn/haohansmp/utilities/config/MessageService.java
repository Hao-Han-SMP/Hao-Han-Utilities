package vn.haohansmp.utilities.config;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public void send(Player player, String key) {
        send(player, key, Map.of());
    }

    public void send(Player player, String key, Map<String, String> replacements) {
        String text = messages.getString("prefix", "") + messages.getString(key, key);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            text = text.replace("<" + replacement.getKey() + ">", replacement.getValue());
        }
        player.sendMessage(miniMessage.deserialize(text));
    }

    public void sendIfOnline(Player player, String key) {
        if (player != null && player.isOnline()) {
            send(player, key);
        }
    }
}
