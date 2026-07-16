package vn.haohansmp.utilities.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import vn.haohansmp.utilities.carry.CarryPayload;

public final class CarryBlockPlaceEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CarryPayload payload;
    private final Block destination;
    private boolean cancelled;

    public CarryBlockPlaceEvent(Player player, CarryPayload payload, Block destination) {
        super(player);
        this.payload = payload;
        this.destination = destination;
    }

    public CarryPayload getPayload() { return payload; }
    public Block getDestination() { return destination; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
