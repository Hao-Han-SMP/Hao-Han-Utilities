package vn.haohan.utilities.feature.dummy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DummyMinecartTest {

    private static final double[] SPEED_TIERS = {0.2, 0.4, 0.6, 0.8, 1.0};

    private static double getNextSpeed(double currentSpeed) {
        int nextIndex = 0;
        for (int i = 0; i < SPEED_TIERS.length; i++) {
            if (Math.abs(currentSpeed - SPEED_TIERS[i]) < 0.05) {
                nextIndex = (i + 1) % SPEED_TIERS.length;
                break;
            }
        }
        return SPEED_TIERS[nextIndex];
    }

    @Test
    void testSpeedTiersCycleCorrectly() {
        assertEquals(0.6, getNextSpeed(0.4), 1e-4);
        assertEquals(0.8, getNextSpeed(0.6), 1e-4);
        assertEquals(1.0, getNextSpeed(0.8), 1e-4);
        assertEquals(0.2, getNextSpeed(1.0), 1e-4);
        assertEquals(0.4, getNextSpeed(0.2), 1e-4);
    }

    @Test
    void testSpeedTiersCycleFromUnknownSpeedDefaultsToFirst() {
        // If an unknown custom speed is set (e.g. 0.5), it resets to the first tier (0.2)
        assertEquals(0.2, getNextSpeed(0.5), 1e-4);
    }
}
