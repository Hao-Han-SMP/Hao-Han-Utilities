package vn.haohan.utilities.carry;

import org.bukkit.Input;

import java.util.Locale;
import java.util.Optional;

public enum CarryActivationKey {
    SPRINT("sprint", "Sprint"),
    SNEAK("sneak", "Sneak");

    private final String configName;
    private final String displayName;

    CarryActivationKey(String configName, String displayName) {
        this.configName = configName;
        this.displayName = displayName;
    }

    public String configName() {
        return configName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isPressed(Input input) {
        return switch (this) {
            case SPRINT -> input.isSprint();
            case SNEAK -> input.isSneak();
        };
    }

    public static Optional<CarryActivationKey> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "sprint" -> Optional.of(SPRINT);
            case "sneak" -> Optional.of(SNEAK);
            default -> Optional.empty();
        };
    }
}
