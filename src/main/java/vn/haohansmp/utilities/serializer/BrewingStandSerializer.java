package vn.haohansmp.utilities.serializer;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import vn.haohansmp.utilities.carry.CarriedBlockPayload;

import java.util.HashMap;
import java.util.Map;

public final class BrewingStandSerializer implements BlockSerializer {
    @Override
    public boolean supports(BlockState state) {
        return state instanceof BrewingStand;
    }

    @Override
    public CarriedBlockPayload capture(Block block, BlockState snapshot) {
        BrewingStand stand = (BrewingStand) snapshot;
        Map<String, String> properties = new HashMap<>();
        properties.put("brewingTime", Integer.toString(stand.getBrewingTime()));
        properties.put("fuelLevel", Integer.toString(stand.getFuelLevel()));
        properties.put("recipeBrewTime", Integer.toString(stand.getRecipeBrewTime()));

        return new CarriedBlockPayload(
                snapshot.getType().getKey().asString(),
                snapshot.getBlockData().getAsString(),
                InventoryCodec.encode(stand.getSnapshotInventory()),
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
        if (!(state instanceof BrewingStand stand)) {
            throw new IllegalStateException("Restored block is not a brewing stand: " + state.getType());
        }
        InventoryCodec.decodeInto(payload.inventoryData(), stand.getSnapshotInventory());
        StateMetadataCodec.restore(state, payload);
        Map<String, String> properties = payload.properties();
        stand.setBrewingTime(Integer.parseInt(properties.getOrDefault("brewingTime", "0")));
        stand.setFuelLevel(Integer.parseInt(properties.getOrDefault("fuelLevel", "0")));
        stand.setRecipeBrewTime(Integer.parseInt(properties.getOrDefault("recipeBrewTime", "400")));
        if (!stand.update(true, false)) {
            throw new IllegalStateException("Brewing stand state update failed at " + block.getLocation());
        }
    }
}
