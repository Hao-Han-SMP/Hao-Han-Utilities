package vn.haohansmp.utilities.carry;

import org.bukkit.Input;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarryActivationKeyTest {
    @Test
    void parsesSupportedNamesAndPhysicalKeyAliases() {
        assertEquals(CarryActivationKey.SPRINT, CarryActivationKey.parse("sprint").orElseThrow());
        assertEquals(CarryActivationKey.SPRINT, CarryActivationKey.parse("CTRL").orElseThrow());
        assertEquals(CarryActivationKey.SNEAK, CarryActivationKey.parse("sneak").orElseThrow());
        assertEquals(CarryActivationKey.SNEAK, CarryActivationKey.parse("Shift").orElseThrow());
    }

    @Test
    void rejectsUnsupportedKeys() {
        assertTrue(CarryActivationKey.parse("space").isEmpty());
        assertTrue(CarryActivationKey.parse(null).isEmpty());
    }

    @Test
    void readsOnlyTheSelectedServerVisibleInput() {
        Input sprint = input(false, true);
        Input sneak = input(true, false);

        assertTrue(CarryActivationKey.SPRINT.isPressed(sprint));
        assertTrue(CarryActivationKey.SNEAK.isPressed(sneak));
    }

    private static Input input(boolean sneak, boolean sprint) {
        return new Input() {
            @Override public boolean isForward() { return false; }
            @Override public boolean isBackward() { return false; }
            @Override public boolean isLeft() { return false; }
            @Override public boolean isRight() { return false; }
            @Override public boolean isJump() { return false; }
            @Override public boolean isSneak() { return sneak; }
            @Override public boolean isSprint() { return sprint; }
        };
    }
}
