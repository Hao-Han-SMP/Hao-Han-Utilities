package vn.haohansmp.utilities.chest;

import net.kyori.adventure.text.Component;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import vn.haohansmp.utilities.serializer.PersistentDataCodec;

import java.util.Arrays;

record ChestSnapshot(
        ItemStack[] contents,
        byte[] persistentData,
        Component customName,
        String lock,
        LootTable lootTable,
        long lootSeed
) {
    ChestSnapshot {
        contents = cloneContents(contents);
        persistentData = persistentData.clone();
    }

    static ChestSnapshot capture(Chest chest) {
        return new ChestSnapshot(
                chest.getBlockInventory().getContents(),
                PersistentDataCodec.encode(chest.getPersistentDataContainer()),
                chest.customName(),
                chest.getLock(),
                chest.getLootTable(),
                chest.getSeed()
        );
    }

    @Override
    public ItemStack[] contents() {
        return cloneContents(contents);
    }

    @Override
    public byte[] persistentData() {
        return persistentData.clone();
    }

    void applyTo(Chest chest) {
        chest.getBlockInventory().clear();
        chest.getBlockInventory().setContents(contents());
        PersistentDataCodec.restore(persistentData, chest.getPersistentDataContainer());
        chest.customName(customName);
        chest.setLock(lock == null ? "" : lock);
        chest.setLootTable(lootTable);
        if (lootTable != null) {
            chest.setSeed(lootSeed);
        }
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        return Arrays.stream(source)
                .map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }
}
