package vn.haohan.utilities.fetch;

import java.util.UUID;

public final class ThrowChargeSession {
    private final UUID playerUuid;
    private int chargeTicks;
    private int lastInteractTick;
    private boolean fullSoundPlayed;

    public ThrowChargeSession(UUID playerUuid, int currentTick) {
        this.playerUuid = playerUuid;
        this.chargeTicks = 0;
        this.lastInteractTick = currentTick;
        this.fullSoundPlayed = false;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getChargeTicks() {
        return chargeTicks;
    }

    public void incrementChargeTicks() {
        this.chargeTicks++;
    }

    public int getLastInteractTick() {
        return lastInteractTick;
    }

    public void refreshInteract(int currentTick) {
        this.lastInteractTick = currentTick;
    }

    public boolean isFullSoundPlayed() {
        return fullSoundPlayed;
    }

    public void setFullSoundPlayed(boolean fullSoundPlayed) {
        this.fullSoundPlayed = fullSoundPlayed;
    }
}
