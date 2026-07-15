package vn.haohansmp.utilities.serializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import vn.haohansmp.utilities.carry.CarriedBlockPayload;

import java.util.Map;
import java.util.Set;

public final class BasicBlockSerializer implements BlockSerializer {
    private final Set<Material> supported;

    public BasicBlockSerializer(Set<Material> supported) {
        this.supported = Set.copyOf(supported);
    }

    @Override
    public boolean supports(BlockState state) {
        return supported.contains(state.getType());
    }

    @Override
    public CarriedBlockPayload capture(Block block, BlockState snapshot) {
        return new CarriedBlockPayload(
                snapshot.getType().getKey().asString(),
                snapshot.getBlockData().getAsString(),
                null,
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
        StateMetadataCodec.restore(state, payload);
        state.update(true, false);
    }
}
