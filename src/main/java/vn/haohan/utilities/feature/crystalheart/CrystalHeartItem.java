package vn.haohan.utilities.feature.crystalheart;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class CrystalHeartItem {
    private final Plugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;

    public CrystalHeartItem(Plugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "crystal_heart");
        this.recipeKey = new NamespacedKey(plugin, "crystal_heart_recipe");
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public NamespacedKey getRecipeKey() {
        return recipeKey;
    }

    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(Material.POISONOUS_POTATO, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Crystal Heart");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right-click to use.");
            lore.add(ChatColor.GRAY + "Increases max health limit by " + ChatColor.RED + "+1 Heart (+2 HP)" + ChatColor.GRAY + ".");
            lore.add(ChatColor.GRAY + "Maximum limit: " + ChatColor.RED + "30 Hearts (60 HP)");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            NamespacedKey ccKey = new NamespacedKey("haohan", "crystal_heart");
            meta.getPersistentDataContainer().set(ccKey, PersistentDataType.STRING, "true");
            NamespacedKey tagKey = new NamespacedKey("haohan", "tag");
            meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, "haohan:crystal_heart");

            meta.setCustomModelData(1);

            meta.setItemModel(new NamespacedKey("haohan", "crystal_heart"));

            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isCrystalHeart(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // Check haohanutilities PDC
        Byte value = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        if (value != null && value == (byte) 1) return true;

        // Check haohan:crystal_heart PDC tag from CustomCrafting
        if (meta.getPersistentDataContainer().has(new NamespacedKey("haohan", "crystal_heart"), PersistentDataType.STRING)) return true;
        String tagVal = meta.getPersistentDataContainer().get(new NamespacedKey("haohan", "tag"), PersistentDataType.STRING);
        return "haohan:crystal_heart".equalsIgnoreCase(tagVal);
    }

    /** Removes the legacy built-in recipe; the item is crafted via CustomCrafting now. */
    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }
}
