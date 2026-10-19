package vn.haohan.utilities.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/**
 * Listens to player lifecycle events to synchronize server links on the client ESC menu.
 */
public final class ServerMenuListener implements Listener {

    private final ServerLinksManager linksManager;

    public ServerMenuListener(ServerLinksManager linksManager) {
        this.linksManager = Objects.requireNonNull(linksManager, "linksManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        linksManager.send(event.getPlayer());
    }
}
