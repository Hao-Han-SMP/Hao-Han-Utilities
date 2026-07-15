package vn.haohansmp.utilities.serializer;

import org.bukkit.persistence.PersistentDataContainer;

import java.io.IOException;

public final class PersistentDataCodec {
    private PersistentDataCodec() {
    }

    public static byte[] encode(PersistentDataContainer container) {
        try {
            return container.serializeToBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot serialize PersistentDataContainer", exception);
        }
    }

    public static void restore(byte[] data, PersistentDataContainer target) {
        if (data == null || data.length == 0) {
            return;
        }
        try {
            target.readFromBytes(data, true);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot restore PersistentDataContainer", exception);
        }
    }
}
