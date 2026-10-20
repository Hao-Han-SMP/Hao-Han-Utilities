package vn.haohan.utilities.dummy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

public final class StrawDummyItem {
    public static final String DISPLAY_NAME = "Bù Nhìn Rơm";
    public static final String MODEL_PATH = "straw_dummy";

    public enum DummyArmorSlot {
        HELMET("helmet", 0, 1.40, 3.00),
        CHESTPLATE("chestplate", 1, 0.80, 1.40),
        LEGGINGS("leggings", 2, 0.35, 0.80),
        BOOTS("boots", 3, -1.00, 0.35);

        private final String id;
        private final int offset;
        private final double minY;
        private final double maxY;

        DummyArmorSlot(String id, int offset, double minY, double maxY) {
            this.id = id;
            this.offset = offset;
            this.minY = minY;
            this.maxY = maxY;
        }

        public String id() { return id; }
        public int offset() { return offset; }

        public org.bukkit.inventory.EquipmentSlot toEquipmentSlot() {
            return switch (this) {
                case HELMET -> org.bukkit.inventory.EquipmentSlot.HEAD;
                case CHESTPLATE -> org.bukkit.inventory.EquipmentSlot.CHEST;
                case LEGGINGS -> org.bukkit.inventory.EquipmentSlot.LEGS;
                case BOOTS -> org.bukkit.inventory.EquipmentSlot.FEET;
            };
        }

        public static DummyArmorSlot fromClickY(double localY) {
            for (DummyArmorSlot slot : values()) {
                if (localY >= slot.minY && localY < slot.maxY) {
                    return slot;
                }
            }
            return CHESTPLATE;
        }

        public static DummyArmorSlot fromMaterial(Material mat) {
            if (mat == null) return null;
            String name = mat.name();
            if (name.endsWith("_HELMET") || mat == Material.CARVED_PUMPKIN) return HELMET;
            if (name.endsWith("_CHESTPLATE")) return CHESTPLATE;
            if (name.endsWith("_LEGGINGS")) return LEGGINGS;
            if (name.endsWith("_BOOTS")) return BOOTS;
            return null;
        }

        public static DummyArmorSlot fromId(String id) {
            if (id == null) return null;
            for (DummyArmorSlot s : values()) {
                if (s.id.equalsIgnoreCase(id)) return s;
            }
            return null;
        }
    }

    public enum DummyArmorTier {
        LEATHER("leather", 1100, new int[]{1, 3, 2, 1}),
        CHAINMAIL("chainmail", 1110, new int[]{2, 5, 4, 1}),
        IRON("iron", 1120, new int[]{2, 6, 5, 2}),
        GOLDEN("golden", 1130, new int[]{2, 5, 3, 1}),
        DIAMOND("diamond", 1140, new int[]{3, 8, 6, 3}),
        NETHERITE("netherite", 1150, new int[]{3, 8, 6, 3});

        private final String id;
        private final int baseCmd;
        private final int[] defenseBySlot;

        DummyArmorTier(String id, int baseCmd, int[] defenseBySlot) {
            this.id = id;
            this.baseCmd = baseCmd;
            this.defenseBySlot = defenseBySlot;
        }

        public String id() { return id; }
        public int baseCmd() { return baseCmd; }
        public int defense(DummyArmorSlot slot) { return defenseBySlot[slot.offset()]; }

        public Sound equipSound() {
            return switch (this) {
                case LEATHER -> Sound.ITEM_ARMOR_EQUIP_LEATHER;
                case CHAINMAIL -> Sound.ITEM_ARMOR_EQUIP_CHAIN;
                case IRON -> Sound.ITEM_ARMOR_EQUIP_IRON;
                case GOLDEN -> Sound.ITEM_ARMOR_EQUIP_GOLD;
                case DIAMOND -> Sound.ITEM_ARMOR_EQUIP_DIAMOND;
                case NETHERITE -> Sound.ITEM_ARMOR_EQUIP_NETHERITE;
            };
        }

        public Material defaultMaterial(DummyArmorSlot slot) {
            String prefix = this == GOLDEN ? "GOLDEN" : name();
            String suffix = switch (slot) {
                case HELMET -> "_HELMET";
                case CHESTPLATE -> "_CHESTPLATE";
                case LEGGINGS -> "_LEGGINGS";
                case BOOTS -> "_BOOTS";
            };
            try {
                return Material.valueOf(prefix + suffix);
            } catch (IllegalArgumentException e) {
                return Material.IRON_CHESTPLATE;
            }
        }


        public static DummyArmorTier fromMaterial(Material mat) {
            if (mat == null) return null;
            String name = mat.name();
            if (name.startsWith("LEATHER_")) return LEATHER;
            if (name.startsWith("CHAINMAIL_")) return CHAINMAIL;
            if (name.startsWith("IRON_")) return IRON;
            if (name.startsWith("GOLDEN_")) return GOLDEN;
            if (name.startsWith("DIAMOND_") || mat == Material.TURTLE_HELMET) return DIAMOND;
            if (name.startsWith("NETHERITE_")) return NETHERITE;
            return null;
        }

        public static DummyArmorTier fromId(String id) {
            if (id == null) return null;
            for (DummyArmorTier t : values()) {
                if (t.id.equalsIgnoreCase(id)) return t;
            }
            return null;
        }
    }

    private final Plugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;
    private final NamespacedKey customModelKey;

    public StrawDummyItem(Plugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "straw_dummy");
        this.recipeKey = new NamespacedKey(plugin, "straw_dummy_recipe");
        this.customModelKey = new NamespacedKey("haohan", "straw_dummy");
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public NamespacedKey getRecipeKey() {
        return recipeKey;
    }

    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(Material.ARMOR_STAND, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(DISPLAY_NAME, NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("Đặt xuống đất để làm bù nhìn tập đánh.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Cầm giáp chuột phải để thử giáp (không mất giáp).", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Tay không chuột phải để gỡ giáp.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Dùng Kéo đánh hoặc Shift + Chuột phải để cắt / thu hồi.", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(customModelKey, PersistentDataType.STRING, "true");
            meta.getPersistentDataContainer().set(new NamespacedKey("haohan", "tag"), PersistentDataType.STRING, "haohan:" + MODEL_PATH);

            int cmd = plugin.getConfig().getInt("dummy.custom-model-data", 1001);
            meta.setCustomModelData(cmd);

            try {
                meta.setItemModel(new NamespacedKey("haohan", MODEL_PATH));
            } catch (Throwable ignored) {
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBaseItem() {
        return createModelPartItem(1002, "straw_dummy_base");
    }

    public ItemStack createBodyItem() {
        return createModelPartItem(1003, "straw_dummy_body");
    }

    private ItemStack createModelPartItem(int cmd, String modelName) {
        ItemStack item = new ItemStack(Material.ARMOR_STAND, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(cmd);
            try {
                meta.setItemModel(new NamespacedKey("haohan", modelName));
            } catch (Throwable ignored) {
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isStrawDummy(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Byte pVal = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        if (pVal != null && pVal == (byte) 1) return true;

        if (meta.getPersistentDataContainer().has(customModelKey, PersistentDataType.STRING)) {
            return true;
        }

        NamespacedKey itemCoreKey = new NamespacedKey("haohanitemcore", "item_id");
        String itemCoreId = meta.getPersistentDataContainer().get(itemCoreKey, PersistentDataType.STRING);
        if ("haohan:straw_dummy".equalsIgnoreCase(itemCoreId)) {
            return true;
        }

        NamespacedKey tagKey = new NamespacedKey("haohan", "tag");
        if (meta.getPersistentDataContainer().has(tagKey, PersistentDataType.STRING)) {
            String tag = meta.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
            if (tag != null && tag.toLowerCase(Locale.ROOT).startsWith("haohan:straw_dummy")) {
                return true;
            }
        }

        return false;
    }

    public void registerRecipe() {
        if (!plugin.getConfig().getBoolean("dummy.recipe.enabled", true)) {
            return;
        }
        try {
            if (Bukkit.getRecipe(recipeKey) != null) {
                Bukkit.removeRecipe(recipeKey);
            }
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, createItem(1));
            recipe.shape(" H ", "SHS", " B ");
            recipe.setIngredient('H', Material.HAY_BLOCK);
            recipe.setIngredient('S', Material.STICK);
            recipe.setIngredient('B', Material.SMOOTH_STONE_SLAB);

            Bukkit.addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().warning("Không thể đăng ký recipe cho Straw Dummy: " + e.getMessage());
        }
    }

    public void unregisterRecipe() {
        try {
            if (Bukkit.getRecipe(recipeKey) != null) {
                Bukkit.removeRecipe(recipeKey);
            }
        } catch (Exception ignored) {
        }
    }
}
