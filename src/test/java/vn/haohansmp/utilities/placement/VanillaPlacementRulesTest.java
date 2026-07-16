package vn.haohansmp.utilities.placement;

import org.bukkit.block.BlockFace;
import org.bukkit.block.Orientation;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanillaPlacementRulesTest {
    @Test
    void selectsNearestLookingDirectionIncludingVerticalFaces() {
        assertEquals(BlockFace.DOWN, VanillaPlacementRules.nearestLookingFace(new Vector(0.1, -0.9, 0.2)));
        assertEquals(BlockFace.UP, VanillaPlacementRules.nearestLookingFace(new Vector(0.1, 0.9, 0.2)));
        assertEquals(BlockFace.WEST, VanillaPlacementRules.nearestLookingFace(new Vector(-0.8, 0.1, 0.2)));
        assertEquals(BlockFace.SOUTH, VanillaPlacementRules.nearestLookingFace(new Vector(0.1, 0.2, 0.8)));
    }

    @Test
    void createsCrafterFrontAndTopLikeVanilla() {
        assertEquals(
                Orientation.DOWN_NORTH,
                VanillaPlacementRules.crafterOrientation(BlockFace.DOWN, BlockFace.SOUTH)
        );
        assertEquals(
                Orientation.UP_SOUTH,
                VanillaPlacementRules.crafterOrientation(BlockFace.UP, BlockFace.SOUTH)
        );
        assertEquals(
                Orientation.WEST_UP,
                VanillaPlacementRules.crafterOrientation(BlockFace.WEST, BlockFace.SOUTH)
        );
    }
}
