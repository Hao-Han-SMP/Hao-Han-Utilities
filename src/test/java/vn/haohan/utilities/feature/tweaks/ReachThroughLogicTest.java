package vn.haohan.utilities.feature.tweaks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.utilities.feature.tweaks.ReachThroughListener.ReachDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReachThroughLogicTest {

    @Test
    @DisplayName("Feature or player toggle disabled passes to vanilla")
    void testFeatureOrPlayerToggleDisabled() {
        assertEquals(
                ReachDecision.PASS_TO_VANILLA,
                ReachThroughListener.resolveDecision(false, true, false, true, false, false)
        );
        assertEquals(
                ReachDecision.PASS_TO_VANILLA,
                ReachThroughListener.resolveDecision(true, false, false, true, false, false)
        );
    }

    @Test
    @DisplayName("Sneaking player bypasses reach-through to interact with the frame directly")
    void testSneakingBypassesReachThrough() {
        assertEquals(
                ReachDecision.PASS_TO_VANILLA,
                ReachThroughListener.resolveDecision(true, true, true, true, false, false)
        );
    }

    @Test
    @DisplayName("Disabled target type passes to vanilla")
    void testTargetTypeDisabled() {
        assertEquals(
                ReachDecision.PASS_TO_VANILLA,
                ReachThroughListener.resolveDecision(true, true, false, false, false, false)
        );
    }

    @Test
    @DisplayName("Empty frame with reachThroughEmpty=false passes to vanilla so player can place an item")
    void testEmptyFrameWithoutReachThroughEmptyPassesToVanilla() {
        assertEquals(
                ReachDecision.PASS_TO_VANILLA,
                ReachThroughListener.resolveDecision(true, true, false, true, true, false)
        );
    }

    @Test
    @DisplayName("Empty frame with reachThroughEmpty=true triggers reach-through")
    void testEmptyFrameWithReachThroughEmptyTrue() {
        assertEquals(
                ReachDecision.REACH_THROUGH,
                ReachThroughListener.resolveDecision(true, true, false, true, true, true)
        );
    }

    @Test
    @DisplayName("Frame with item when standing triggers reach-through to open container")
    void testFrameWithItemTriggersReachThrough() {
        assertEquals(
                ReachDecision.REACH_THROUGH,
                ReachThroughListener.resolveDecision(true, true, false, true, false, false)
        );
    }
}
