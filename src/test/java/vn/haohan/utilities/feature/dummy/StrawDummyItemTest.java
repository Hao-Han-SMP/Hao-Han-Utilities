package vn.haohan.utilities.feature.dummy;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import vn.haohan.utilities.feature.dummy.StrawDummyItem.DummyArmorSlot;
import vn.haohan.utilities.feature.dummy.StrawDummyItem.DummyArmorTier;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrawDummyItemTest {

    @Test
    void testDamageFormattingExactInteger() {
        double dmg = 5.0001;
        String formatted = (Math.abs(dmg - Math.round(dmg)) < 0.05)
                ? String.valueOf(Math.round(dmg))
                : String.format(Locale.US, "%.1f", dmg);
        assertEquals("5", formatted);
    }

    @Test
    void testDamageFormattingDecimal() {
        double dmg = 7.42;
        String formatted = (Math.abs(dmg - Math.round(dmg)) < 0.05)
                ? String.valueOf(Math.round(dmg))
                : String.format(Locale.US, "%.1f", dmg);
        assertEquals("7.4", formatted);
    }

    @Test
    void testRecoilAngleClamping() {
        double damage = 120.0;
        double intensity = 1.0;
        float baseTilt = (float) (0.28F * intensity);
        float damageTilt = (float) Math.min(0.32F * intensity, damage * 0.015F * intensity);
        float initialAngle = baseTilt + damageTilt;

        assertTrue(initialAngle <= 0.60F, "Angle should be capped within realistic bounds");
        assertTrue(initialAngle >= 0.28F, "Angle should have at least base tilt");
    }

    @Test
    void testArmorSlotAndTierDetection() {
        assertEquals(DummyArmorSlot.HELMET, DummyArmorSlot.fromMaterial(Material.IRON_HELMET));
        assertEquals(DummyArmorSlot.CHESTPLATE, DummyArmorSlot.fromMaterial(Material.NETHERITE_CHESTPLATE));
        assertEquals(DummyArmorSlot.LEGGINGS, DummyArmorSlot.fromMaterial(Material.DIAMOND_LEGGINGS));
        assertEquals(DummyArmorSlot.BOOTS, DummyArmorSlot.fromMaterial(Material.GOLDEN_BOOTS));

        assertEquals(DummyArmorTier.IRON, DummyArmorTier.fromMaterial(Material.IRON_HELMET));
        assertEquals(DummyArmorTier.NETHERITE, DummyArmorTier.fromMaterial(Material.NETHERITE_CHESTPLATE));
        assertEquals(DummyArmorTier.DIAMOND, DummyArmorTier.fromMaterial(Material.DIAMOND_LEGGINGS));

        // Click height mapping (Armor Stand style)
        assertEquals(DummyArmorSlot.HELMET, DummyArmorSlot.fromClickY(1.65));
        assertEquals(DummyArmorSlot.CHESTPLATE, DummyArmorSlot.fromClickY(1.10));
        assertEquals(DummyArmorSlot.LEGGINGS, DummyArmorSlot.fromClickY(0.55));
        assertEquals(DummyArmorSlot.BOOTS, DummyArmorSlot.fromClickY(0.15));
    }
}
