package vn.haohan.utilities.integration;

import dev.geco.gsit.api.GSitAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Optional bridge to GSit. The GSit jar is provided by the server, not bundled here. */
public final class GSitIntegration {
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("GSit");
    }

    /** Starts GSit's player-sit flow and returns whether the sit was created. */
    public boolean sitOnPlayer(Player player, Player target) {
        if (!isAvailable()) {
            return false;
        }
        return GSitAPI.sitOnPlayer(player, target);
    }
}
