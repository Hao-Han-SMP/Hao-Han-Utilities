package vn.haohansmp.utilities.carry;

import org.bukkit.block.Block;
import vn.haohansmp.utilities.serializer.BlockSerializer;
import vn.haohansmp.utilities.serializer.BlockSerializerRegistry;

import java.util.Arrays;
import java.util.Objects;

public final class PayloadVerifier {
    private PayloadVerifier() {
    }

    public static boolean matches(CarriedBlockPayload expected, CarriedBlockPayload actual) {
        return expected.material().equals(actual.material())
                && expected.blockData().equals(actual.blockData())
                && Arrays.equals(expected.inventoryData(), actual.inventoryData())
                && Arrays.equals(expected.persistentData(), actual.persistentData())
                && Objects.equals(expected.customNameJson(), actual.customNameJson())
                && Objects.equals(expected.lock(), actual.lock())
                && expected.properties().equals(actual.properties());
    }

    public static boolean matchesBlock(Block block, CarriedBlockPayload expected, BlockSerializerRegistry registry) {
        try {
            BlockSerializer serializer = registry.serializerFor(block.getState());
            return matches(expected, serializer.capture(block, block.getState()));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
