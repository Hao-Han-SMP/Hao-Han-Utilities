package vn.haohan.utilities.dummy;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DummyPhysicsTest {

    @Test
    void testPerturbDirectionPreservesLength() {
        Vector3f dir = DummyPhysics.perturbDirection(1.0, 0.0, 0.2);
        assertEquals(1.0F, dir.length(), 1e-4F);
    }

    @Test
    void testTiltAxisIsOrthogonalToDirection() {
        Vector3f dir = DummyPhysics.perturbDirection(0.0, 1.0, 0.15);
        Vector3f axis = DummyPhysics.calculateTiltAxis(dir);

        // Dot product of (dx, 0, dz) and (dz, 0, -dx) must be 0
        float dot = dir.dot(axis);
        assertEquals(0.0F, dot, 1e-4F);
        assertEquals(1.0F, axis.length(), 1e-4F);
    }

    @Test
    void testDampedOscillationDecaysOverTime() {
        float initial = 0.5F;
        float damping = 0.18F;
        float freq = 0.85F;

        float angle0 = DummyPhysics.calculateDampedAngle(initial, 0, damping, freq);
        assertEquals(initial, angle0, 1e-4F);

        float angleEnd = DummyPhysics.calculateDampedAngle(initial, 20, damping, freq);
        assertTrue(Math.abs(angleEnd) < Math.abs(angle0) * 0.1F, "Angle should decay to under 10% after 20 ticks");
    }

    @Test
    void testPivotTranslationKeepsPivotFixed() {
        Vector3f pivot = new Vector3f(0.0F, 0.75F, 0.0F);
        Quaternionf rotation = new Quaternionf().setAngleAxis((float) Math.toRadians(30), 1.0F, 0.0F, 0.0F);

        Vector3f translation = DummyPhysics.calculatePivotTranslation(pivot, rotation);

        // Position of pivot after transformation: translation + rotation * pivot
        Vector3f transformedPivot = new Vector3f(pivot).rotate(rotation).add(translation);

        assertEquals(pivot.x, transformedPivot.x, 1e-4F);
        assertEquals(pivot.y, transformedPivot.y, 1e-4F);
        assertEquals(pivot.z, transformedPivot.z, 1e-4F);
    }
}
