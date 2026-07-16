package vn.haohansmp.utilities.carry;

public final class CarryWeight {
    private CarryWeight() {
    }

    public static double movementMultiplier(
            double emptyOrNonContainerMultiplier,
            double fullContainerMultiplier,
            double fillRatio
    ) {
        double empty = clamp(emptyOrNonContainerMultiplier, 0.1, 1.0);
        double full = clamp(fullContainerMultiplier, 0.1, empty);
        double fullness = clamp(fillRatio, 0.0, 1.0);
        return empty + (full - empty) * fullness;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
