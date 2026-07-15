package vn.haohansmp.utilities.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.util.UUID;

public final class CarryBlockPickupEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Block block;
    private final UUID carryId;
    private boolean cancelled;

    public CarryBlockPickupEvent(Player player, Block block, UUID carryId) {
        super(player);
        this.block = block;
        this.carryId = carryId;
    }

    public Block getBlock() { return block; }
    public UUID getCarryId() { return carryId; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
