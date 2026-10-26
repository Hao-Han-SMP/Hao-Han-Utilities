package vn.haohan.utilities.core;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

public final class MessageService {
    private static final Set<String> DEBUG_ONLY_MESSAGES = Set.of(
            "pickup-success",
            "entity-pickup-success",
            "player-pickup-success",
            "place-success",
            "entity-place-success",
            "player-place-success",
            "invalid-destination",
            "invalid-player-destination"
    );

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
        try (InputStream bundled = plugin.getResource("messages.yml")) {
            if (bundled != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
            }
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Cannot load bundled message defaults: " + exception.getMessage());
        }
    }

    public void send(Player player, String key) {
        send(player, key, Map.of());
    }

    public void send(Player player, String key, Map<String, String> replacements) {
        if (DEBUG_ONLY_MESSAGES.contains(key) && !plugin.getConfig().getBoolean("debug", false)) {
            return;
        }
        String template = getRawMessage(key);
        if (template == null || template.isBlank()) {
            return;
        }
        String text = messages.getString("prefix", "") + template;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            text = text.replace("<" + replacement.getKey() + ">", replacement.getValue());
        }
        player.sendMessage(miniMessage.deserialize(text));
    }

    public void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        player.sendActionBar(miniMessage.deserialize(message));
    }

    public void sendActionBarKey(Player player, String key, String fallback, Map<String, String> replacements) {
        if (player == null || !player.isOnline()) return;
        String template = getRawMessage(key);
        if (template == null || template.isBlank()) {
            template = fallback;
        }
        if (template == null || template.isBlank()) return;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            template = template.replace("<" + replacement.getKey() + ">", replacement.getValue());
        }
        player.sendActionBar(miniMessage.deserialize(template));
    }

    private String getRawMessage(String key) {
        String msg = messages.getString(key);
        if (msg == null && messages.getDefaults() != null) {
            msg = messages.getDefaults().getString(key);
        }
        return msg;
    }

    public void sendIfOnline(Player player, String key) {
        if (player != null && player.isOnline()) {
            send(player, key);
        }
    }
}
