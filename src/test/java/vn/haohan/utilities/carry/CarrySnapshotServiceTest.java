package vn.haohan.utilities.carry;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrySnapshotServiceTest {
    @Test
    void keepsWaterloggedStateCarriedFromSourceBlock() {
        assertTrue(CarrySnapshotService.shouldWaterlog(true, Material.AIR));
    }

    @Test
    void waterlogsBlockWhenItReplacesWaterLikeVanilla() {
        assertTrue(CarrySnapshotService.shouldWaterlog(false, Material.WATER));
    }

    @Test
    void doesNotAddWaterAtDryDestination() {
        assertFalse(CarrySnapshotService.shouldWaterlog(false, Material.AIR));
    }
}
