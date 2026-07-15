package vn.haohansmp.utilities.carry;

import java.time.Instant;
import java.util.UUID;

public record CarryRecord(
        UUID carryId,
        UUID playerUuid,
        BlockPosition originalPosition,
        BlockPosition placementPosition,
        CarryStatus status,
        CarriedBlockPayload payload,
        Instant createdAt,
        Instant updatedAt
) {
}
