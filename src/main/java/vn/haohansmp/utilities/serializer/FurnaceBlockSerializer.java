package vn.haohansmp.utilities.serializer;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import vn.haohansmp.utilities.carry.CarriedBlockPayload;

import java.util.HashMap;
import java.util.Map;

public final class FurnaceBlockSerializer implements BlockSerializer {
    @Override
    public boolean supports(BlockState state) {
        return state instanceof Furnace;
    }

    @Override
    public CarriedBlockPayload capture(Block block, BlockState snapshot) {
        Furnace furnace = (Furnace) snapshot;
        Map<String, String> properties = new HashMap<>();
        properties.put("burnTime", Short.toString(furnace.getBurnTime()));
        properties.put("cookTime", Short.toString(furnace.getCookTime()));
        properties.put("cookTimeTotal", Integer.toString(furnace.getCookTimeTotal()));
        properties.put("cookSpeedMultiplier", Double.toString(furnace.getCookSpeedMultiplier()));

        return new CarriedBlockPayload(
                snapshot.getType().getKey().asString(),
                snapshot.getBlockData().getAsString(),
                InventoryCodec.encode(furnace.getSnapshotInventory()),
                StateMetadataCodec.persistentData(snapshot),
                StateMetadataCodec.customName(snapshot),
                StateMetadataCodec.lock(snapshot),
                properties
        );
    }

    @Override
    public void restore(Block block, CarriedBlockPayload payload) {
        block.setBlockData(Bukkit.createBlockData(payload.blockData()), false);
        BlockState state = block.getState();
        if (!(state instanceof Furnace furnace)) {
            throw new IllegalStateException("Restored block is not a furnace: " + state.getType());
        }
        InventoryCodec.decodeInto(payload.inventoryData(), furnace.getSnapshotInventory());
        StateMetadataCodec.restore(state, payload);
        Map<String, String> properties = payload.properties();
        furnace.setBurnTime(Short.parseShort(properties.getOrDefault("burnTime", "0")));
        furnace.setCookTime(Short.parseShort(properties.getOrDefault("cookTime", "0")));
        furnace.setCookTimeTotal(Integer.parseInt(properties.getOrDefault("cookTimeTotal", "200")));
        furnace.setCookSpeedMultiplier(Double.parseDouble(properties.getOrDefault("cookSpeedMultiplier", "1.0")));
        if (!furnace.update(true, false)) {
            throw new IllegalStateException("Furnace state update failed at " + block.getLocation());
        }
    }
}
