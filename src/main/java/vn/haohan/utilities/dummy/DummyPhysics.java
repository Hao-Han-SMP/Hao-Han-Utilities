package vn.haohan.utilities.dummy;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DummyPhysics {
    private DummyPhysics() {}

    /**
     * Perturbs a 2D normalized direction by a small random angle (in radians).
     */
    public static Vector3f perturbDirection(double dx, double dz, double angleOffsetRadians) {
        double currentAngle = Math.atan2(dz, dx);
        double newAngle = currentAngle + angleOffsetRadians;
        return new Vector3f((float) Math.cos(newAngle), 0.0F, (float) Math.sin(newAngle));
    }

    /**
     * Calculates the horizontal rotation axis perpendicular to the perturbed push direction.
     * When rotated by a positive angle around this axis, the top of the entity leans in the direction of (dx, dz).
     */
    public static Vector3f calculateTiltAxis(Vector3f direction) {
        // axis = (dz, 0, -dx)
        return new Vector3f(direction.z, 0.0F, -direction.x).normalize();
    }

    /**
     * Calculates the tilt angle in radians at a given tick using a damped harmonic oscillator.
     * theta(t) = theta0 * exp(-gamma * t) * cos(omega * t)
     */
    public static float calculateDampedAngle(float initialAngle, int tick, float damping, float frequency) {
        if (tick < 0) return 0.0F;
        float decay = (float) Math.exp(-damping * tick);
        float oscillation = (float) Math.cos(frequency * tick);
        return initialAngle * decay * oscillation;
    }

    /**
     * Computes the translation offset required to rotate around an arbitrary pivot point P.
     * T = P - R * P
     */
    public static Vector3f calculatePivotTranslation(Vector3f pivot, Quaternionf rotation) {
        Vector3f rotatedPivot = new Vector3f(pivot).rotate(rotation);
        return new Vector3f(pivot).sub(rotatedPivot);
    }
}
