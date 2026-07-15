package vn.haohansmp.utilities.serializer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Nameable;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lockable;
import org.bukkit.block.TileState;
import vn.haohansmp.utilities.carry.CarriedBlockPayload;

final class StateMetadataCodec {
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private StateMetadataCodec() {
    }

    static String customName(BlockState state) {
        if (!(state instanceof Nameable nameable)) {
            return null;
        }
        Component name = nameable.customName();
        return name == null ? null : GSON.serialize(name);
    }

    static String lock(BlockState state) {
        return state instanceof Lockable lockable ? lockable.getLock() : null;
    }

    static byte[] persistentData(BlockState state) {
        return state instanceof TileState tileState
                ? PersistentDataCodec.encode(tileState.getPersistentDataContainer())
                : null;
    }

    static void restore(BlockState state, CarriedBlockPayload payload) {
        if (state instanceof Nameable nameable && payload.customNameJson() != null) {
            nameable.customName(GSON.deserialize(payload.customNameJson()));
        }
        if (state instanceof Lockable lockable && payload.lock() != null) {
            lockable.setLock(payload.lock());
        }
        if (state instanceof TileState tileState) {
            PersistentDataCodec.restore(payload.persistentData(), tileState.getPersistentDataContainer());
        }
    }

}
