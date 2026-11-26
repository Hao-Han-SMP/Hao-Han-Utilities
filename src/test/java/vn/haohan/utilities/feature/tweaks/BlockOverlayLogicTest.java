package vn.haohan.utilities.feature.tweaks;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.utilities.feature.tweaks.BlockOverlayListener.PlacementAction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockOverlayLogicTest {

    @Test
    @DisplayName("Overlay material detection accepts snow layers, carpets, petals, wildflowers, leaf litter, sculk vein, glow lichen, and corals")
    void testOverlayMaterialDetection() {
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.SNOW));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.WHITE_CARPET));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.RED_CARPET));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.MOSS_CARPET));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.PALE_MOSS_CARPET));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.PINK_PETALS));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.WILDFLOWERS));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.LEAF_LITTER));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.SCULK_VEIN));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.GLOW_LICHEN));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.TUBE_CORAL));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.DEAD_FIRE_CORAL));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.BRAIN_CORAL_FAN));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.DEAD_HORN_CORAL_FAN));

        assertFalse(BlockOverlayListener.isOverlayMaterial(Material.TUBE_CORAL_BLOCK));
        assertFalse(BlockOverlayListener.isOverlayMaterial(Material.DEAD_BRAIN_CORAL_BLOCK));
        assertFalse(BlockOverlayListener.isOverlayMaterial(Material.SNOW_BLOCK));
        assertFalse(BlockOverlayListener.isOverlayMaterial(Material.WHITE_WOOL));
        assertFalse(BlockOverlayListener.isOverlayMaterial(Material.STONE));
        assertFalse(BlockOverlayListener.isOverlayMaterial(Material.AIR));
        assertFalse(BlockOverlayListener.isOverlayMaterial(null));
    }

    @Test
    @DisplayName("Excluded host materials reject air, liquids, barriers, and overlays")
    void testExcludedHostMaterials() {
        assertTrue(BlockOverlayListener.isExcludedHostMaterial(Material.AIR));
        assertTrue(BlockOverlayListener.isExcludedHostMaterial(Material.WATER));
        assertTrue(BlockOverlayListener.isExcludedHostMaterial(Material.LAVA));
        assertTrue(BlockOverlayListener.isExcludedHostMaterial(Material.BARRIER));
        assertTrue(BlockOverlayListener.isExcludedHostMaterial(Material.SNOW));
        assertTrue(BlockOverlayListener.isExcludedHostMaterial(Material.BLUE_CARPET));
        assertTrue(BlockOverlayListener.isExcludedHostMaterial(Material.LEAF_LITTER));

        assertFalse(BlockOverlayListener.isExcludedHostMaterial(Material.CHEST));
        assertFalse(BlockOverlayListener.isExcludedHostMaterial(Material.OAK_FENCE));
        assertFalse(BlockOverlayListener.isExcludedHostMaterial(Material.COBBLESTONE_WALL));
        assertFalse(BlockOverlayListener.isExcludedHostMaterial(Material.OAK_SAPLING));
    }

    @Test
    @DisplayName("Full cube bounding box detection distinguishes full cubes from non-full blocks")
    void testFullCubeBoxDetection() {
        assertTrue(BlockOverlayListener.isFullCubeBox(new BoundingBox(0, 0, 0, 1, 1, 1)));
        assertFalse(BlockOverlayListener.isFullCubeBox(new BoundingBox(0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375)));
        assertFalse(BlockOverlayListener.isFullCubeBox(new BoundingBox(0.375, 0, 0.375, 0.625, 1.5, 0.625)));
        assertFalse(BlockOverlayListener.isFullCubeBox(new BoundingBox(0, 0, 0, 1, 0.5, 1)));
    }

    @Test
    @DisplayName("Disabled feature, wrong item, full host block, or unsupported vanilla surface returns IGNORE")
    void testBasicGuardsReturnIgnore() {
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                false, true, true, true, false, BlockFace.NORTH, true, true, null, 0, 8, Material.SNOW, true));
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, false, true, true, false, BlockFace.NORTH, true, true, null, 0, 8, Material.STONE, true));
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, false, true, null, 0, 8, Material.SNOW, true));
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, false, false, false, BlockFace.NORTH, true, false, null, 0, 4, Material.PINK_PETALS, true));
    }

    @Test
    @DisplayName("Sneak requirement and interactable top-face pass-through are respected")
    void testSneakAndInteractableGuards() {
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, false, true, false, BlockFace.NORTH, true, true, null, 0, 8, Material.SNOW, true));

        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, false, false, true, BlockFace.NORTH, true, true, null, 0, 8, Material.SNOW, true));

        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, true, BlockFace.UP, true, true, null, 0, 8, Material.WHITE_CARPET, true));

        assertEquals(PlacementAction.PLACE_NEW, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, true, BlockFace.NORTH, true, true, null, 0, 8, Material.WHITE_CARPET, true));

        assertEquals(PlacementAction.PLACE_NEW, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.UP, true, true, null, 0, 8, Material.SNOW, true));

        assertEquals(PlacementAction.PLACE_NEW, BlockOverlayListener.resolvePlacementAction(
                true, true, false, false, false, BlockFace.NORTH, true, true, null, 0, 8, Material.SNOW, true));
    }

    @Test
    @DisplayName("Fences never require sneak to bypass interaction")
    void testFencesDoNotRequireSneak() {
        assertFalse(BlockOverlayListener.requiresSneakToBypassInteract(Material.OAK_FENCE));
        assertFalse(BlockOverlayListener.requiresSneakToBypassInteract(Material.NETHER_BRICK_FENCE));
        assertFalse(BlockOverlayListener.requiresSneakToBypassInteract(null));
    }

    @Test
    @DisplayName("Snow and flowerbed/segmentable overlays stack while different overlay replacement is disallowed")
    void testStackingAndNoReplace() {
        assertEquals(PlacementAction.PLACE_NEW, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, true, BlockFace.EAST, true, true, null, 0, 8, Material.SNOW, true));

        assertEquals(PlacementAction.STACK_OVERLAY, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, true, true, Material.SNOW, 3, 8, Material.SNOW, true));

        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, true, true, Material.SNOW, 8, 8, Material.SNOW, true));

        assertEquals(PlacementAction.STACK_OVERLAY, BlockOverlayListener.resolvePlacementAction(
                true, true, false, false, false, BlockFace.NORTH, true, true, Material.PINK_PETALS, 2, 4, Material.PINK_PETALS, true));
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, false, false, false, BlockFace.NORTH, true, true, Material.PINK_PETALS, 4, 4, Material.PINK_PETALS, true));

        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, true, true, Material.WHITE_CARPET, 0, 8, Material.RED_CARPET, true));
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, true, true, Material.WHITE_CARPET, 0, 8, Material.SNOW, true));
    }

    @Test
    @DisplayName("Snow drops snowball unless silk touch is used, and break hits scale with tool")
    void testSnowDropsAndBreakHits() {
        assertEquals(Material.SNOWBALL, BlockOverlayListener.getDropMaterial(Material.SNOW, false));
        assertEquals(Material.SNOW, BlockOverlayListener.getDropMaterial(Material.SNOW, true));
        assertEquals(Material.RED_CARPET, BlockOverlayListener.getDropMaterial(Material.RED_CARPET, false));

        assertEquals(1, BlockOverlayListener.getRequiredBreakHits(Material.SNOW, Material.DIAMOND_SHOVEL, false, 2));
        assertEquals(2, BlockOverlayListener.getRequiredBreakHits(Material.SNOW, Material.AIR, false, 2));
        assertEquals(1, BlockOverlayListener.getRequiredBreakHits(Material.RED_CARPET, Material.SHEARS, false, 2));
        assertEquals(2, BlockOverlayListener.getRequiredBreakHits(Material.RED_CARPET, Material.AIR, false, 2));
        assertEquals(1, BlockOverlayListener.getRequiredBreakHits(Material.LEAF_LITTER, Material.AIR, false, 2));
        assertEquals(1, BlockOverlayListener.getRequiredBreakHits(Material.TUBE_CORAL, Material.AIR, false, 2));
        assertEquals(1, BlockOverlayListener.getRequiredBreakHits(Material.BRAIN_CORAL_FAN, Material.AIR, false, 2));
        assertEquals(1, BlockOverlayListener.getRequiredBreakHits(Material.GLOW_LICHEN, Material.SHEARS, false, 2));
        assertEquals(1, BlockOverlayListener.getRequiredBreakHits(Material.SCULK_VEIN, Material.DIAMOND_HOE, false, 2));
        assertEquals(1, BlockOverlayListener.getRequiredBreakHits(Material.RED_CARPET, Material.AIR, true, 2));
    }
}
