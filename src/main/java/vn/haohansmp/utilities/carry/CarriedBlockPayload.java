package vn.haohansmp.utilities.carry;

import java.util.Map;

public record CarriedBlockPayload(
        String material,
        String blockData,
        byte[] inventoryData,
        byte[] persistentData,
        String customNameJson,
        String lock,
        Map<String, String> properties
) {
    public CarriedBlockPayload {
        inventoryData = inventoryData == null ? null : inventoryData.clone();
        persistentData = persistentData == null ? null : persistentData.clone();
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    @Override
    public byte[] inventoryData() {
        return inventoryData == null ? null : inventoryData.clone();
    }

    @Override
    public byte[] persistentData() {
        return persistentData == null ? null : persistentData.clone();
    }
}
