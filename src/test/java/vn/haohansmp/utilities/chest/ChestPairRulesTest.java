package vn.haohansmp.utilities.chest;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestPairRulesTest {
    @Test
    void matchesOppositeHalvesWithSameMaterialAndFacing() {
        assertTrue(ChestPairRules.matchesOtherHalf(
                Material.CHEST, Chest.Type.LEFT, BlockFace.NORTH,
                Material.CHEST, Chest.Type.RIGHT, BlockFace.NORTH));
        assertTrue(ChestPairRules.matchesOtherHalf(
                Material.TRAPPED_CHEST, Chest.Type.RIGHT, BlockFace.WEST,
                Material.TRAPPED_CHEST, Chest.Type.LEFT, BlockFace.WEST));
    }

    @Test
    void rejectsSingleMismatchedOrDifferentFacingChests() {
        assertFalse(ChestPairRules.matchesOtherHalf(
                Material.CHEST, Chest.Type.SINGLE, BlockFace.NORTH,
                Material.CHEST, Chest.Type.RIGHT, BlockFace.NORTH));
        assertFalse(ChestPairRules.matchesOtherHalf(
                Material.CHEST, Chest.Type.LEFT, BlockFace.NORTH,
                Material.TRAPPED_CHEST, Chest.Type.RIGHT, BlockFace.NORTH));
        assertFalse(ChestPairRules.matchesOtherHalf(
                Material.CHEST, Chest.Type.LEFT, BlockFace.NORTH,
                Material.CHEST, Chest.Type.RIGHT, BlockFace.SOUTH));
    }
}
