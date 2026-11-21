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
    @DisplayName("Overlay material detection accepts snow layers and carpets only")
    void testOverlayMaterialDetection() {
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.SNOW));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.WHITE_CARPET));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.RED_CARPET));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.MOSS_CARPET));
        assertTrue(BlockOverlayListener.isOverlayMaterial(Material.PALE_MOSS_CARPET));

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
    @DisplayName("Disabled feature, wrong item, or full host block returns IGNORE")
    void testBasicGuardsReturnIgnore() {
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                false, true, true, true, false, BlockFace.NORTH, true, null, 0, 8, Material.SNOW, true));
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, false, true, true, false, BlockFace.NORTH, true, null, 0, 8, Material.STONE, true));
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, false, null, 0, 8, Material.SNOW, true));
    }

    @Test
    @DisplayName("Sneak requirement and interactable top-face pass-through are respected")
    void testSneakAndInteractableGuards() {
        // requireSneak = true without sneaking -> IGNORE
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, false, true, false, BlockFace.NORTH, true, null, 0, 8, Material.SNOW, true));

        // requireSneak = false on interactable host (chest) without sneaking -> IGNORE so chest can open
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, false, false, true, BlockFace.NORTH, true, null, 0, 8, Material.SNOW, true));

        // Shift + Right-click on TOP face of interactable host (chest) -> IGNORE so player can place on top of chest
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, true, BlockFace.UP, true, null, 0, 8, Material.WHITE_CARPET, true));

        // Shift + Right-click on SIDE face of interactable host (chest) -> PLACE_NEW inside chest block
        assertEquals(PlacementAction.PLACE_NEW, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, true, BlockFace.NORTH, true, null, 0, 8, Material.WHITE_CARPET, true));

        // Shift + Right-click on TOP face of non-interactable host (fence/sapling) -> PLACE_NEW
        assertEquals(PlacementAction.PLACE_NEW, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.UP, true, null, 0, 8, Material.SNOW, true));

        // requireSneak = false on non-interactable host (fence/sapling) without sneaking -> PLACE_NEW
        assertEquals(PlacementAction.PLACE_NEW, BlockOverlayListener.resolvePlacementAction(
                true, true, false, false, false, BlockFace.NORTH, true, null, 0, 8, Material.SNOW, true));
    }

    @Test
    @DisplayName("Fences never require sneak to bypass interaction")
    void testFencesDoNotRequireSneak() {
        assertFalse(BlockOverlayListener.requiresSneakToBypassInteract(Material.OAK_FENCE));
        assertFalse(BlockOverlayListener.requiresSneakToBypassInteract(Material.NETHER_BRICK_FENCE));
        assertFalse(BlockOverlayListener.requiresSneakToBypassInteract(null));
    }

    @Test
    @DisplayName("Snow stacking works while different overlay replacement is disallowed")
    void testStackingAndNoReplace() {
        // Empty host -> PLACE_NEW
        assertEquals(PlacementAction.PLACE_NEW, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, true, BlockFace.EAST, true, null, 0, 8, Material.SNOW, true));

        // Snow on snow (< 8) -> STACK_SNOW
        assertEquals(PlacementAction.STACK_SNOW, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, true, Material.SNOW, 3, 8, Material.SNOW, true));

        // Snow on snow (== 8) -> IGNORE
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, true, Material.SNOW, 8, 8, Material.SNOW, true));

        // Different carpet on existing carpet -> IGNORE (no replace)
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, true, Material.WHITE_CARPET, 0, 8, Material.RED_CARPET, true));

        // Snow on existing carpet -> IGNORE (no replace)
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, true, Material.WHITE_CARPET, 0, 8, Material.SNOW, true));

        // Same carpet on existing carpet -> IGNORE
        assertEquals(PlacementAction.IGNORE, BlockOverlayListener.resolvePlacementAction(
                true, true, true, true, false, BlockFace.NORTH, true, Material.WHITE_CARPET, 0, 8, Material.WHITE_CARPET, true));
    }
}
