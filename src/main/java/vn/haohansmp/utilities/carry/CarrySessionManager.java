package vn.haohansmp.utilities.carry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CarrySessionManager {
    private final Map<UUID, CarrySession> sessions = new ConcurrentHashMap<>();

    public Optional<CarrySession> get(UUID playerUuid) {
        return Optional.ofNullable(sessions.get(playerUuid));
    }

    public boolean add(CarrySession session) {
        return sessions.putIfAbsent(session.playerUuid(), session) == null;
    }

    public CarrySession remove(UUID playerUuid) {
        return sessions.remove(playerUuid);
    }

    public boolean isCarrying(UUID playerUuid) {
        return sessions.containsKey(playerUuid);
    }

    public Collection<CarrySession> sessions() {
        return List.copyOf(sessions.values());
    }
}
