package vn.haohansmp.utilities.carry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CarryWeightTest {
    @Test
    void interpolatesBetweenEmptyAndFullContainerSpeed() {
        assertEquals(0.75, CarryWeight.movementMultiplier(0.75, 0.35, 0.0), 0.0001);
        assertEquals(0.55, CarryWeight.movementMultiplier(0.75, 0.35, 0.5), 0.0001);
        assertEquals(0.35, CarryWeight.movementMultiplier(0.75, 0.35, 1.0), 0.0001);
    }

    @Test
    void clampsInvalidConfigurationAndFillRatio() {
        assertEquals(1.0, CarryWeight.movementMultiplier(2.0, 2.0, -1.0), 0.0001);
        assertEquals(0.1, CarryWeight.movementMultiplier(0.05, 0.01, 2.0), 0.0001);
    }
}
