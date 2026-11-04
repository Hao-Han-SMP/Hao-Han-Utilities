package vn.haohan.utilities.feature.tweaks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.utilities.feature.tweaks.InvisibleItemFrameListener.FrameAction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvisibleItemFrameLogicTest {

    @Test
    @DisplayName("Feature disabled or incorrect item returns IGNORE")
    void testDisabledOrWrongItem() {
        assertEquals(
                FrameAction.IGNORE,
                InvisibleItemFrameListener.resolveAction(false, true, true, true, false, true, true)
        );
        assertEquals(
                FrameAction.IGNORE,
                InvisibleItemFrameListener.resolveAction(true, false, true, true, false, true, true)
        );
    }

    @Test
    @DisplayName("Visible frame with item turns invisible on click (standing or sneaking)")
    void testVisibleWithItemTurnsInvisible() {
        // Standing click
        assertEquals(
                FrameAction.MAKE_INVISIBLE,
                InvisibleItemFrameListener.resolveAction(true, true, true, true, false, true, true)
        );
        // Sneaking click
        assertEquals(
                FrameAction.MAKE_INVISIBLE,
                InvisibleItemFrameListener.resolveAction(true, true, true, true, true, true, true)
        );
    }

    @Test
    @DisplayName("Visible empty frame without sneak passes to vanilla to place display item")
    void testVisibleEmptyFrameWithoutSneakPassesToVanilla() {
        assertEquals(
                FrameAction.PASS_TO_VANILLA,
                InvisibleItemFrameListener.resolveAction(true, true, true, false, false, true, true)
        );
    }

    @Test
    @DisplayName("Visible empty frame with sneak turns invisible")
    void testVisibleEmptyFrameWithSneakTurnsInvisible() {
        assertEquals(
                FrameAction.MAKE_INVISIBLE,
                InvisibleItemFrameListener.resolveAction(true, true, true, false, true, true, true)
        );
    }

    @Test
    @DisplayName("Visible empty frame without sneak turns invisible if emptyFrameRequiresSneak is false")
    void testVisibleEmptyFrameWithoutSneakWhenSneakNotRequired() {
        assertEquals(
                FrameAction.MAKE_INVISIBLE,
                InvisibleItemFrameListener.resolveAction(true, true, true, false, false, true, false)
        );
    }

    @Test
    @DisplayName("Invisible frame without sneak passes to vanilla so item can rotate or be placed")
    void testInvisibleFrameWithoutSneakPassesToVanilla() {
        // With item (vanilla rotates the item)
        assertEquals(
                FrameAction.PASS_TO_VANILLA,
                InvisibleItemFrameListener.resolveAction(true, true, false, true, false, true, true)
        );
        // Without item (vanilla places item into frame)
        assertEquals(
                FrameAction.PASS_TO_VANILLA,
                InvisibleItemFrameListener.resolveAction(true, true, false, false, false, true, true)
        );
    }

    @Test
    @DisplayName("Invisible frame with sneak toggles back to visible when allowToggle is true")
    void testInvisibleFrameWithSneakTogglesBack() {
        // Frame with item, sneaking -> visible
        assertEquals(
                FrameAction.MAKE_VISIBLE,
                InvisibleItemFrameListener.resolveAction(true, true, false, true, true, true, true)
        );
        // Frame empty, sneaking -> visible
        assertEquals(
                FrameAction.MAKE_VISIBLE,
                InvisibleItemFrameListener.resolveAction(true, true, false, false, true, true, true)
        );
    }

    @Test
    @DisplayName("Invisible frame with sneak passes to vanilla when allowToggle is false")
    void testInvisibleFrameWithSneakWhenToggleDisabled() {
        assertEquals(
                FrameAction.PASS_TO_VANILLA,
                InvisibleItemFrameListener.resolveAction(true, true, false, true, true, false, true)
        );
    }
}
