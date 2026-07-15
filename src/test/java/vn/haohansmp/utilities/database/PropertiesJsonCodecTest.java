package vn.haohansmp.utilities.database;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PropertiesJsonCodecTest {
    @Test
    void roundTripsFunctionalBlockProperties() {
        Map<String, String> properties = Map.of(
                "burnTime", "120",
                "cookTime", "42",
                "cookSpeedMultiplier", "1.25"
        );
        assertEquals(properties, PropertiesJsonCodec.decode(PropertiesJsonCodec.encode(properties)));
    }

    @Test
    void treatsMissingJsonAsEmptyProperties() {
        assertEquals(Map.of(), PropertiesJsonCodec.decode(null));
        assertEquals(Map.of(), PropertiesJsonCodec.decode(""));
    }
}
