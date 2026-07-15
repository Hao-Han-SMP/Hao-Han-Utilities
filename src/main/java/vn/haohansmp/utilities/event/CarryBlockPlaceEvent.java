package vn.haohansmp.utilities.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import vn.haohansmp.utilities.carry.CarriedBlockPayload;

public final class CarryBlockPlaceEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CarriedBlockPayload payload;
    private final Block destination;
    private boolean cancelled;

    public CarryBlockPlaceEvent(Player player, CarriedBlockPayload payload, Block destination) {
        super(player);
        this.payload = payload;
        this.destination = destination;
    }

    public CarriedBlockPayload getPayload() { return payload; }
    public Block getDestination() { return destination; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
