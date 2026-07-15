package vn.haohansmp.utilities.serializer;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryCodec {
    private InventoryCodec() {
    }

    public static byte[] encode(Inventory inventory) {
        return ItemStack.serializeItemsAsBytes(inventory.getContents());
    }

    public static void decodeInto(byte[] data, Inventory inventory) {
        inventory.clear();
        if (data == null || data.length == 0) {
            return;
        }
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(data);
        if (items.length != inventory.getSize()) {
            throw new IllegalStateException("Inventory size mismatch: stored=" + items.length
                    + ", current=" + inventory.getSize());
        }
        inventory.setContents(items);
    }
}
