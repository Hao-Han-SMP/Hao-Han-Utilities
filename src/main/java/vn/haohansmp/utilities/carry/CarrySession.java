package vn.haohansmp.utilities.carry;

import org.bukkit.entity.BlockDisplay;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CarrySession {
    private final UUID carryId;
    private final UUID playerUuid;
    private final BlockPosition originalPosition;
    private final CarriedBlockPayload payload;
    private final long startedAtMillis = System.currentTimeMillis();
    private final AtomicBoolean placing = new AtomicBoolean(false);
    private BlockDisplay display;

    public CarrySession(UUID carryId, UUID playerUuid, BlockPosition originalPosition, CarriedBlockPayload payload) {
        this.carryId = carryId;
        this.playerUuid = playerUuid;
        this.originalPosition = originalPosition;
        this.payload = payload;
    }

    public UUID carryId() { return carryId; }
    public UUID playerUuid() { return playerUuid; }
    public BlockPosition originalPosition() { return originalPosition; }
    public CarriedBlockPayload payload() { return payload; }
    public BlockDisplay display() { return display; }
    public long startedAtMillis() { return startedAtMillis; }
    public void display(BlockDisplay display) { this.display = display; }
    public boolean beginPlacement() { return placing.compareAndSet(false, true); }
    public void cancelPlacement() { placing.set(false); }
}
