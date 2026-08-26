package vn.haohan.utilities.fetch;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class FetchSession {
    private UUID wolfUuid;
    private final UUID playerUuid;
    private final Item stickItem;
    private ItemDisplay visualStick;
    private FetchState state;
    private int ticksElapsed;
    private int stuckTicks;
    private Location lastLocation;
    private boolean landed;

    public FetchSession(UUID wolfUuid, UUID playerUuid, Item stickItem) {
        this.wolfUuid = wolfUuid;
        this.playerUuid = playerUuid;
        this.stickItem = stickItem;
        this.state = FetchState.CHASING;
        this.ticksElapsed = 0;
        this.stuckTicks = 0;
        this.lastLocation = null;
        this.landed = false;
    }

    public UUID getWolfUuid() {
        return wolfUuid;
    }

    public void setWolfUuid(UUID wolfUuid) {
        this.wolfUuid = wolfUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public Item getStickItem() {
        return stickItem;
    }

    public ItemDisplay getVisualStick() {
        return visualStick;
    }

    public void setVisualStick(ItemDisplay visualStick) {
        this.visualStick = visualStick;
    }

    public FetchState getState() {
        return state;
    }

    public void setState(FetchState state) {
        this.state = state;
    }

    public int getTicksElapsed() {
        return ticksElapsed;
    }

    public void incrementTicks(int count) {
        this.ticksElapsed += count;
    }

    public int getStuckTicks() {
        return stuckTicks;
    }

    public void incrementStuckTicks(int count) {
        this.stuckTicks += count;
    }

    public void resetStuckTicks() {
        this.stuckTicks = 0;
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void setLastLocation(Location lastLocation) {
        this.lastLocation = lastLocation;
    }

    public boolean isLanded() {
        return landed;
    }

    public void setLanded(boolean landed) {
        this.landed = landed;
    }

    public void cleanupVisual() {
        if (visualStick != null) {
            if (visualStick.isValid()) {
                visualStick.remove();
            }
            visualStick = null;
        }
    }

    public void dropStickIfHolding(Wolf wolf) {
        cleanupVisual();
        if (state == FetchState.RETURNING && wolf != null && wolf.isValid()) {
            wolf.getWorld().dropItemNaturally(wolf.getLocation(), new ItemStack(Material.STICK, 1));
        }
    }
}
