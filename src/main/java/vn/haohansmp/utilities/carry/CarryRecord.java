package vn.haohansmp.utilities.carry;

import java.time.Instant;
import java.util.UUID;

public record CarryRecord(
        UUID carryId,
        UUID playerUuid,
        BlockPosition originalPosition,
        BlockPosition placementPosition,
        CarryStatus status,
        CarryPayload payload,
        Instant createdAt,
        Instant updatedAt
) {
}
