package vn.haohansmp.utilities.serializer;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import vn.haohansmp.utilities.carry.CarriedBlockPayload;

public interface BlockSerializer {
    boolean supports(BlockState state);

    CarriedBlockPayload capture(Block block, BlockState snapshot);

    void restore(Block block, CarriedBlockPayload payload);
}
