package vn.haohansmp.utilities.carry;

import java.util.UUID;

public record CarryPayload(
        CarryKind kind,
        String typeKey,
        String blockData,
        byte[] data,
        byte[] visualItem,
        UUID sourceEntityUuid
) {
    public CarryPayload {
        data = data.clone();
        visualItem = visualItem.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public byte[] visualItem() {
        return visualItem.clone();
    }
}
