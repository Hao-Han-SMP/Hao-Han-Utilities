package vn.haohansmp.utilities.carry;

import org.bukkit.entity.Entity;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CarrySession {
    private final UUID carryId;
    private final UUID playerUuid;
    private final BlockPosition originalPosition;
    private final CarryPayload payload;
    private final long startedAtMillis = System.currentTimeMillis();
    private final AtomicBoolean placing = new AtomicBoolean(false);
    private Entity visualEntity;

    public CarrySession(UUID carryId, UUID playerUuid, BlockPosition originalPosition, CarryPayload payload) {
        this.carryId = carryId;
        this.playerUuid = playerUuid;
        this.originalPosition = originalPosition;
        this.payload = payload;
    }

    public UUID carryId() { return carryId; }
    public UUID playerUuid() { return playerUuid; }
    public BlockPosition originalPosition() { return originalPosition; }
    public CarryPayload payload() { return payload; }
    public Entity visualEntity() { return visualEntity; }
    public long startedAtMillis() { return startedAtMillis; }
    public void visualEntity(Entity visualEntity) { this.visualEntity = visualEntity; }
    public boolean beginPlacement() { return placing.compareAndSet(false, true); }
    public void cancelPlacement() { placing.set(false); }
}
