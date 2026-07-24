package vn.haohansmp.utilities.protection;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohansmp.utilities.carry.CarryPayload;
import vn.haohansmp.utilities.event.CarryBlockPickupEvent;
import vn.haohansmp.utilities.event.CarryBlockPlaceEvent;

import java.util.UUID;

public final class ProtectionService {
    private final JavaPlugin plugin;

    public ProtectionService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canPickupBlock(Player player, Block block, UUID carryId) {
        if (!plugin.getConfig().getBoolean("protection.enabled", true)) {
            return true;
        }
        if (player.hasPermission("haohanutilities.bypass.protection")) {
            return true;
        }

        CarryBlockPickupEvent carryEvent = new CarryBlockPickupEvent(player, block, carryId);
        Bukkit.getPluginManager().callEvent(carryEvent);
        if (carryEvent.isCancelled()) {
            return false;
        }

        return canBreakBlock(player, block);
    }

    public boolean canBreakBlock(Player player, Block block) {
        if (!plugin.getConfig().getBoolean("protection.enabled", true)) {
            return true;
        }
        if (player.hasPermission("haohanutilities.bypass.protection")) {
            return true;
        }

        BlockBreakEvent breakProbe = new BlockBreakEvent(block, player);
        breakProbe.setDropItems(false);
        Bukkit.getPluginManager().callEvent(breakProbe);
        return !breakProbe.isCancelled();
    }

    public boolean canPlaceBlock(Player player, Block destination, BlockData data, CarryPayload payload) {
        if (!plugin.getConfig().getBoolean("protection.enabled", true)) {
            return true;
        }
        if (player.hasPermission("haohanutilities.bypass.protection")) {
            return true;
        }

        CarryBlockPlaceEvent carryEvent = new CarryBlockPlaceEvent(player, payload, destination);
        Bukkit.getPluginManager().callEvent(carryEvent);
        if (carryEvent.isCancelled()) {
            return false;
        }

        BlockCanBuildEvent buildProbe = new BlockCanBuildEvent(destination, player, data, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(buildProbe);
        return buildProbe.isBuildable();
    }
}
