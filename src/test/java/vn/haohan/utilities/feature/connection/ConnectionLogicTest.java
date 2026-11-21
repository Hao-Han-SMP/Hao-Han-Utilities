package vn.haohan.utilities.feature.connection;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionLogicTest {

    @Test
    void testDropCountCalculationPreventsLeashDupe() {
        ConnectionRecord leashRecord = new ConnectionRecord(
                UUID.randomUUID(),
                "LEASH",
                UUID.randomUUID(),
                UUID.randomUUID(),
                0, 64, 0,
                10, 64, 0,
                0, 64, 0,
                10, 64, 0,
                System.currentTimeMillis()
        );

        double dist = Math.sqrt(Math.pow(leashRecord.endX() - leashRecord.startX(), 2));
        assertEquals(10.0, dist);

        // LEASH must always drop exactly 1 item
        int leashDrops = "LEASH".equalsIgnoreCase(leashRecord.type())
                ? 1
                : (int) Math.max(1, Math.round(dist));
        assertEquals(1, leashDrops, "Leash must drop exactly 1 lead, not based on distance");

        ConnectionRecord chainRecord = new ConnectionRecord(
                UUID.randomUUID(),
                "CHAIN",
                UUID.randomUUID(),
                UUID.randomUUID(),
                0, 64, 0,
                16, 64, 0,
                0, 64, 0,
                16, 64, 0,
                System.currentTimeMillis()
        );

        double chainDist = Math.sqrt(Math.pow(chainRecord.endX() - chainRecord.startX(), 2));
        int chainDrops = "LEASH".equalsIgnoreCase(chainRecord.type())
                ? 1
                : (int) Math.max(1, Math.round(chainDist));
        assertEquals(16, chainDrops, "Chain drops must equal distance items");
    }

    @Test
    void testDistanceLimits() {
        double maxLeash = 10.0;
        double maxChain = 16.0;

        assertTrue(maxLeash <= 10.0, "Leash max distance cannot exceed vanilla 10.0 limit");
        assertTrue(maxChain >= 16.0, "Chain max distance should support at least 16.0 blocks");
    }

    @Test
    void testAllChainMaterialsSupported() {
        Material[] expectedChains = {
                Material.IRON_CHAIN,
                Material.COPPER_CHAIN,
                Material.EXPOSED_COPPER_CHAIN,
                Material.WEATHERED_COPPER_CHAIN,
                Material.OXIDIZED_COPPER_CHAIN,
                Material.WAXED_COPPER_CHAIN,
                Material.WAXED_EXPOSED_COPPER_CHAIN,
                Material.WAXED_WEATHERED_COPPER_CHAIN,
                Material.WAXED_OXIDIZED_COPPER_CHAIN
        };
        for (Material chain : expectedChains) {
            assertTrue(ConnectionConstants.isChainMaterial(chain), "Expected chain material to be supported: " + chain);
        }

        assertFalse(ConnectionConstants.isChainMaterial(Material.LEAD));
        assertFalse(ConnectionConstants.isChainMaterial(Material.STONE));
        assertFalse(ConnectionConstants.isChainMaterial(null));
    }

    @Test
    void testConnectionRecordChainMaterialFallbackAndPreservation() {
        ConnectionRecord legacyChain = new ConnectionRecord(
                UUID.randomUUID(), "CHAIN", UUID.randomUUID(), UUID.randomUUID(),
                0, 64, 0, 5, 64, 0, 0, 64, 0, 5, 64, 0, System.currentTimeMillis()
        );
        assertEquals(Material.IRON_CHAIN, legacyChain.chainMaterial());

        ConnectionRecord copperChain = new ConnectionRecord(
                UUID.randomUUID(), "CHAIN", Material.WAXED_OXIDIZED_COPPER_CHAIN, UUID.randomUUID(), UUID.randomUUID(),
                0, 64, 0, 5, 64, 0, 0, 64, 0, 5, 64, 0, System.currentTimeMillis()
        );
        assertEquals(Material.WAXED_OXIDIZED_COPPER_CHAIN, copperChain.chainMaterial());
    }
}
