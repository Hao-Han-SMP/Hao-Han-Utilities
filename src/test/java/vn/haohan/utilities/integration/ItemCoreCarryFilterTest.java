package vn.haohan.utilities.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCoreCarryFilterTest {

    @Test
    @DisplayName("Should obey allow-by-default when lists are empty")
    void testDefaultAllowed() {
        ItemCoreCarryIntegration.CarryFilter filterDefaultFalse = new ItemCoreCarryIntegration.CarryFilter(
                false, List.of(), List.of()
        );
        assertFalse(filterDefaultFalse.isAllowed("decoration:bench"));

        ItemCoreCarryIntegration.CarryFilter filterDefaultTrue = new ItemCoreCarryIntegration.CarryFilter(
                true, List.of(), List.of()
        );
        assertTrue(filterDefaultTrue.isAllowed("decoration:bench"));
    }

    @Test
    @DisplayName("Denied list should always take precedence over allowed list")
    void testDeniedPrecedence() {
        ItemCoreCarryIntegration.CarryFilter filter = new ItemCoreCarryIntegration.CarryFilter(
                false,
                List.of("metallurgy:*", "decoration:bench"),
                List.of("metallurgy:blast_furnace")
        );

        assertTrue(filter.isAllowed("metallurgy:copper_block"));
        assertTrue(filter.isAllowed("decoration:bench"));
        assertFalse(filter.isAllowed("metallurgy:blast_furnace"));
        assertFalse(filter.isAllowed("magic:wand_table"));
    }

    @Test
    @DisplayName("Wildcard matching works properly")
    void testWildcardMatching() {
        ItemCoreCarryIntegration.CarryFilter filter = new ItemCoreCarryIntegration.CarryFilter(
                false,
                List.of("haohan:*", "*:crate"),
                List.of("haohan:forbidden_*")
        );

        assertTrue(filter.isAllowed("haohan:crystal_chest"));
        assertTrue(filter.isAllowed("storage:crate"));
        assertFalse(filter.isAllowed("haohan:forbidden_box"));
        assertFalse(filter.isAllowed("other:other_thing"));
    }
}
