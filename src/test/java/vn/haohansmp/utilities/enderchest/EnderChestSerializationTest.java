package vn.haohansmp.utilities.enderchest;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EnderChestSerializationTest {

    @Test
    void deserializesNullOrEmptyBytesToExpectedSizeArray() {
        ItemStack[] items = EnderChestListener.deserializeItemStacks(null, 54);
        assertNotNull(items);
        assertEquals(54, items.length);

        ItemStack[] itemsEmpty = EnderChestListener.deserializeItemStacks(new byte[0], 54);
        assertNotNull(itemsEmpty);
        assertEquals(54, itemsEmpty.length);
    }
}
