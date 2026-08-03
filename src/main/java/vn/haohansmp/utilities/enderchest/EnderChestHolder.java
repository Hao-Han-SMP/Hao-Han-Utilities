package vn.haohansmp.utilities.enderchest;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class EnderChestHolder implements InventoryHolder {
    private final Player player;
    private final Location blockLocation;
    private Inventory inventory;

    public EnderChestHolder(Player player, Location blockLocation) {
        this.player = player;
        this.blockLocation = blockLocation;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getBlockLocation() {
        return blockLocation;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
