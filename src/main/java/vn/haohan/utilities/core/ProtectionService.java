package vn.haohan.utilities.core;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohan.utilities.feature.carry.CarryPayload;
import vn.haohan.utilities.feature.carry.event.CarryBlockPickupEvent;
import vn.haohan.utilities.feature.carry.event.CarryBlockPlaceEvent;

import java.util.UUID;

public final class ProtectionService {
    private final JavaPlugin plugin;
    private final ThreadLocal<Boolean> probing = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProtectionService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isProbing() {
        return probing.get();
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
        probing.set(Boolean.TRUE);
        try {
            Bukkit.getPluginManager().callEvent(breakProbe);
            return !breakProbe.isCancelled();
        } finally {
            probing.set(Boolean.FALSE);
        }
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
        if (!buildProbe.isBuildable()) {
            return false;
        }

        BlockState placedBlockState = destination.getState();
        placedBlockState.setBlockData(data);
        Block against = destination.getRelative(BlockFace.DOWN);
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        BlockPlaceEvent placeProbe = new BlockPlaceEvent(
                destination,
                placedBlockState,
                against,
                itemInHand,
                player,
                true,
                EquipmentSlot.HAND
        );
        probing.set(Boolean.TRUE);
        try {
            Bukkit.getPluginManager().callEvent(placeProbe);
            return !placeProbe.isCancelled() && placeProbe.canBuild();
        } finally {
            probing.set(Boolean.FALSE);
        }
    }

    public boolean canInteractBlock(Player player, Block block, BlockFace face) {
        if (!plugin.getConfig().getBoolean("protection.enabled", true)) {
            return true;
        }
        if (player.hasPermission("haohanutilities.bypass.protection")) {
            return true;
        }

        PlayerInteractEvent interactProbe = new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(),
                block,
                face,
                EquipmentSlot.HAND
        );
        probing.set(Boolean.TRUE);
        try {
            Bukkit.getPluginManager().callEvent(interactProbe);
            return interactProbe.useInteractedBlock() != Event.Result.DENY && !interactProbe.isCancelled();
        } finally {
            probing.set(Boolean.FALSE);
        }
    }
}
