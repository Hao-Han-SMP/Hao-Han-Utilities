package vn.haohansmp.utilities.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import vn.haohansmp.utilities.carry.CarryService;
import vn.haohansmp.utilities.recovery.RecoveryService;

public final class PlayerLifecycleListener implements Listener {
    private final CarryService carryService;
    private final RecoveryService recoveryService;

    public PlayerLifecycleListener(CarryService carryService, RecoveryService recoveryService) {
        this.carryService = carryService;
        this.recoveryService = recoveryService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        recoveryService.recoverPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        carryService.detach(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        carryService.detach(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        carryService.detach(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        recoveryService.recoverPlayer(event.getPlayer());
    }
}
