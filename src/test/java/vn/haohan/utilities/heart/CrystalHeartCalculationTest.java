package vn.haohan.utilities.heart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrystalHeartCalculationTest {

    @Test
    void testIncreaseMaxHealthWithinCap() {
        double currentMax = 20.0;
        double hpPerHeart = 2.0;
        double maxCap = 60.0;

        double newMax = Math.min(maxCap, currentMax + hpPerHeart);
        assertEquals(22.0, newMax);
    }

    @Test
    void testIncreaseMaxHealthExceedingCap() {
        double currentMax = 59.0;
        double hpPerHeart = 2.0;
        double maxCap = 60.0;

        double newMax = Math.min(maxCap, currentMax + hpPerHeart);
        assertEquals(60.0, newMax);
    }

    @Test
    void testDeathHpLossAboveMinCap() {
        double currentMax = 26.0;
        double hpLoss = 2.0;
        double minCap = 20.0;

        double newMax = Math.max(minCap, currentMax - hpLoss);
        assertEquals(24.0, newMax);
    }

    @Test
    void testDeathHpLossAtOrBelowMinCap() {
        double currentMax = 20.0;
        double hpLoss = 2.0;
        double minCap = 20.0;

        double newMax = Math.max(minCap, currentMax - hpLoss);
        assertEquals(20.0, newMax);
    }
}
