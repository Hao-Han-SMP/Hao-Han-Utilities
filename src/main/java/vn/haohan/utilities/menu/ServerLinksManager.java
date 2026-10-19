package vn.haohan.utilities.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.ServerLinks;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.Objects;

/**
 * Manages ServerLinks configuration and distribution to clients for the pause/esc menu.
 */
public final class ServerLinksManager {

    public static final String DEFAULT_WEBSITE_URL = "https://web.haohansmp.io.vn";
    public static final String DEFAULT_DISCORD_URL = "https://discord.gg/NwTJ6Ezey4";

    private final Server server;
    private final URI websiteUri;
    private final URI discordUri;

    public ServerLinksManager(Server server) {
        this(server, URI.create(DEFAULT_WEBSITE_URL), URI.create(DEFAULT_DISCORD_URL));
    }

    public ServerLinksManager(Server server, URI websiteUri, URI discordUri) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
        this.websiteUri = Objects.requireNonNull(websiteUri, "websiteUri cannot be null");
        this.discordUri = Objects.requireNonNull(discordUri, "discordUri cannot be null");
    }

    /**
     * Creates a standalone ServerLinks snapshot containing HaoHanSMP links.
     */
    public ServerLinks createLinks() {
        ServerLinks base = server.getServerLinks();
        ServerLinks links = base != null ? base.copy() : null;
        if (links == null) {
            return null;
        }

        links.setLink(ServerLinks.Type.WEBSITE, websiteUri);
        links.setLink(ServerLinks.Type.COMMUNITY, discordUri);

        Component menuLabel = MiniMessage.miniMessage().deserialize("<gold><bold>HaoHanSMP</bold></gold> <yellow>Cài Đặt</yellow>");
        links.addLink(menuLabel, websiteUri);

        return links;
    }

    /**
     * Sends configured links to the specified player.
     */
    public void send(Player player) {
        if (player == null) return;
        try {
            ServerLinks links = createLinks();
            if (links != null) {
                player.sendLinks(links);
            }
        } catch (Throwable ignored) {
            // Safe fallback if client / server environment lacks full protocol support
        }
    }

    public URI getWebsiteUri() {
        return websiteUri;
    }

    public URI getDiscordUri() {
        return discordUri;
    }
}
