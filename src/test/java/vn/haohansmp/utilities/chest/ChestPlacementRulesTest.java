package vn.haohansmp.utilities.chest;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChestPlacementRulesTest {
    @Test
    void followsMinecraftConnectedDirectionMapping() {
        assertEquals(
                Chest.Type.LEFT,
                ChestPlacementRules.typeForPartner(BlockFace.NORTH, BlockFace.EAST)
        );
        assertEquals(
                Chest.Type.RIGHT,
                ChestPlacementRules.typeForPartner(BlockFace.NORTH, BlockFace.WEST)
        );
        assertEquals(
                Chest.Type.SINGLE,
                ChestPlacementRules.typeForPartner(BlockFace.NORTH, BlockFace.NORTH)
        );
    }

    @Test
    void assignsOppositeTypeToPartner() {
        assertEquals(Chest.Type.RIGHT, ChestPlacementRules.opposite(Chest.Type.LEFT));
        assertEquals(Chest.Type.LEFT, ChestPlacementRules.opposite(Chest.Type.RIGHT));
        assertEquals(Chest.Type.SINGLE, ChestPlacementRules.opposite(Chest.Type.SINGLE));
    }
}
