package vn.haohansmp.utilities.serializer;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Container;
import org.bukkit.block.Furnace;
import vn.haohansmp.utilities.carry.CarriedBlockPayload;

import java.util.Map;

public final class ContainerBlockSerializer implements BlockSerializer {
    @Override
    public boolean supports(BlockState state) {
        return state instanceof Container && !(state instanceof Furnace) && !(state instanceof BrewingStand);
    }

    @Override
    public CarriedBlockPayload capture(Block block, BlockState snapshot) {
        Container container = (Container) snapshot;
        return new CarriedBlockPayload(
                snapshot.getType().getKey().asString(),
                snapshot.getBlockData().getAsString(),
                InventoryCodec.encode(container.getSnapshotInventory()),
                StateMetadataCodec.persistentData(snapshot),
                StateMetadataCodec.customName(snapshot),
                StateMetadataCodec.lock(snapshot),
                Map.of()
        );
    }

    @Override
    public void restore(Block block, CarriedBlockPayload payload) {
        block.setBlockData(Bukkit.createBlockData(payload.blockData()), false);
        BlockState state = block.getState();
        if (!(state instanceof Container container)) {
            throw new IllegalStateException("Restored block is not a container: " + state.getType());
        }
        InventoryCodec.decodeInto(payload.inventoryData(), container.getSnapshotInventory());
        StateMetadataCodec.restore(state, payload);
        if (!state.update(true, false)) {
            throw new IllegalStateException("Container state update failed at " + block.getLocation());
        }
    }
}
