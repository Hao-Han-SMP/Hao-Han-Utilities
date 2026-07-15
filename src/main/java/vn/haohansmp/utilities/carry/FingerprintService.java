package vn.haohansmp.utilities.carry;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import vn.haohansmp.utilities.serializer.InventoryCodec;
import vn.haohansmp.utilities.serializer.PersistentDataCodec;

import java.util.Arrays;

public final class FingerprintService {
    public BlockFingerprint create(Block block) {
        BlockState state = block.getState();
        int inventoryHash = state instanceof Container container
                ? Arrays.hashCode(InventoryCodec.encode(container.getSnapshotInventory()))
                : 0;
        int pdcHash = state instanceof TileState tileState
                ? Arrays.hashCode(PersistentDataCodec.encode(tileState.getPersistentDataContainer()))
                : 0;
        return new BlockFingerprint(block.getType(), block.getBlockData().getAsString(), inventoryHash, pdcHash);
    }
}
