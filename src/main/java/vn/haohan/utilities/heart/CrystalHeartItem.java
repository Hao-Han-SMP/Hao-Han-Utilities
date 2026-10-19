package vn.haohan.utilities.heart;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import vn.haohan.itemcore.api.HaoHanItemCore;

public class CrystalHeartItem {
    public static final String ITEM_ID = "haohan:crystal_heart";
    private final Plugin plugin;
    private final NamespacedKey itemKey;

    public CrystalHeartItem(Plugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "crystal_heart");
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public ItemStack createItem(int amount) {
        try {
            return HaoHanItemCore.get().getItemService().create(ITEM_ID, Math.max(1, amount));
        } catch (Throwable t) {
            ItemStack item = new ItemStack(Material.POISONOUS_POTATO, Math.max(1, amount));
            var meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Crystal Heart");
                meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
                item.setItemMeta(meta);
            }
            return item;
        }
    }

    public boolean isCrystalHeart(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }

        try {
            if (HaoHanItemCore.get().getItemService().isItem(item, ITEM_ID)) {
                return true;
            }
        } catch (Throwable ignored) {}

        var meta = item.getItemMeta();
        if (meta == null) return false;

        // Legacy / PDC fallback checks
        Byte value = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        if (value != null && value == (byte) 1) return true;

        try {
            NamespacedKey customCraftingKey = new NamespacedKey("haohan", "crystal_heart");
            if (meta.getPersistentDataContainer().has(customCraftingKey, PersistentDataType.STRING)) return true;

            NamespacedKey tagKey = new NamespacedKey("haohan", "tag");
            if (meta.getPersistentDataContainer().has(tagKey, PersistentDataType.STRING)) {
                String tagVal = meta.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
                if ("haohan:crystal_heart".equalsIgnoreCase(tagVal)) return true;
            }
        } catch (Throwable ignored) {}

        if (meta.hasDisplayName()) {
            String name = ChatColor.stripColor(meta.getDisplayName());
            if (name != null && (name.contains("Crystal Heart") || name.contains("Trái Tim Tinh Thể"))) {
                return true;
            }
        }

        return false;
    }

    public void registerRecipe() {
        registerItemDefinition();
    }

    public void registerItemDefinition() {
        try {
            var core = HaoHanItemCore.get();
            if (core != null && core.getItemRegistry() != null && !core.getItemRegistry().exists(ITEM_ID)) {
                var definition = vn.haohan.itemcore.api.item.ItemDefinition.builder(ITEM_ID)
                        .material(Material.POISONOUS_POTATO)
                        .displayName("§c§lCrystal Heart")
                        .lore(java.util.List.of(
                                "§7Right-click to use.",
                                "§7Increases max health limit by §c+1 Heart (+2 HP)§7.",
                                "§7Maximum limit: §c30 Hearts (60 HP)"
                        ))
                        .customModelData(1)
                        .maxStackSize(64)
                        .type(vn.haohan.itemcore.api.item.ItemType.FOOD)
                        .model("haohan:crystal_heart")
                        .food(food -> food
                                .nutrition(0)
                                .saturation(0.0f)
                                .canAlwaysEat(true)
                                .consumeSeconds(2.5f)
                        )
                        .properties(java.util.Map.of(
                                "crystal_heart", "true",
                                "tag", "haohan:crystal_heart"
                        ))
                        .build();
                core.getItemRegistry().register(definition);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register " + ITEM_ID + " into ItemCore: " + t.getMessage());
        }
    }

    public void unregisterRecipe() {
        try {
            var core = HaoHanItemCore.get();
            if (core != null && core.getItemRegistry() != null && core.getItemRegistry().exists(ITEM_ID)) {
                core.getItemRegistry().unregister(ITEM_ID);
            }
        } catch (Throwable ignored) {}
    }
}
