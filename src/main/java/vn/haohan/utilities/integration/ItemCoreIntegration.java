package vn.haohan.utilities.integration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ItemCoreIntegration {
    private final Plugin plugin;

    public ItemCoreIntegration(Plugin plugin) {
        this.plugin = plugin;
    }

    public void registerStrawDummy() {
        if (!Bukkit.getPluginManager().isPluginEnabled("HaoHanItemCore")) {
            return;
        }

        try {
            Class<?> itemCoreClass = Class.forName("vn.haohan.itemcore.api.HaoHanItemCore");
            Object itemCore = itemCoreClass.getMethod("get").invoke(null);
            if (itemCore == null) return;

            Object itemRegistry = itemCoreClass.getMethod("getItemRegistry").invoke(itemCore);
            Object recipeRegistry = itemCoreClass.getMethod("getRecipeRegistry").invoke(itemCore);

            Class<?> itemDefClass = Class.forName("vn.haohan.itemcore.api.item.ItemDefinition");
            Class<?> itemTypeClass = Class.forName("vn.haohan.itemcore.api.item.ItemType");

            Object builder = itemDefClass.getMethod("builder", String.class).invoke(null, "haohan:straw_dummy");
            Class<?> builderClass = builder.getClass();

            int cmd = plugin.getConfig().getInt("dummy.custom-model-data", 1001);
            Object specialType = Enum.valueOf((Class<Enum>) itemTypeClass, "SPECIAL");

            builderClass.getMethod("material", Material.class).invoke(builder, Material.ARMOR_STAND);
            builderClass.getMethod("displayName", String.class).invoke(builder, "§6§lBù Nhìn Rơm");
            builderClass.getMethod("lore", List.class).invoke(builder, List.of(
                    "§7Đặt xuống đất để làm bù nhìn tập đánh.",
                    "§7Cầm giáp chuột phải để thử giáp (không mất giáp).",
                    "§7Tay không chuột phải để gỡ giáp.",
                    "§eDùng Kéo đánh hoặc Shift + Chuột phải để cắt / thu hồi."
            ));
            builderClass.getMethod("customModelData", Integer.class).invoke(builder, cmd);
            builderClass.getMethod("model", String.class).invoke(builder, "haohan:straw_dummy");
            builderClass.getMethod("type", itemTypeClass).invoke(builder, specialType);
            builderClass.getMethod("maxStackSize", int.class).invoke(builder, 16);
            builderClass.getMethod("addFlag", ItemFlag.class).invoke(builder, ItemFlag.HIDE_ATTRIBUTES);

            Object itemDef = builderClass.getMethod("build").invoke(builder);

            Method registerItem = itemRegistry.getClass().getMethod("register", itemDefClass);
            Method existsItem = itemRegistry.getClass().getMethod("exists", String.class);
            Method unregisterItem = itemRegistry.getClass().getMethod("unregister", String.class);
            if ((boolean) existsItem.invoke(itemRegistry, "haohan:straw_dummy")) {
                unregisterItem.invoke(itemRegistry, "haohan:straw_dummy");
            }
            registerItem.invoke(itemRegistry, itemDef);

            if (plugin.getConfig().getBoolean("dummy.recipe.enabled", true) && recipeRegistry != null) {
                Class<?> shapedRecipeClass = Class.forName("vn.haohan.itemcore.api.recipe.ShapedRecipeDefinition");
                Class<?> matIngClass = Class.forName("vn.haohan.itemcore.api.recipe.Ingredient$MaterialIngredient");
                Class<?> itemResultClass = Class.forName("vn.haohan.itemcore.api.recipe.ItemResult");
                Class<?> recipeDefClass = Class.forName("vn.haohan.itemcore.api.recipe.RecipeDefinition");

                Constructor<?> matIngCtor = matIngClass.getConstructor(Material.class, int.class);
                Object hayIng = matIngCtor.newInstance(Material.HAY_BLOCK, 1);
                Object stickIng = matIngCtor.newInstance(Material.STICK, 1);
                Object slabIng = matIngCtor.newInstance(Material.SMOOTH_STONE_SLAB, 1);

                Object result = itemResultClass.getConstructor(String.class, int.class)
                        .newInstance("haohan:straw_dummy", 1);

                Object recipeDef = shapedRecipeClass
                        .getConstructor(String.class, List.class, Map.class, itemResultClass)
                        .newInstance(
                                "haohan:straw_dummy",
                                List.of(" H ", "SHS", " B "),
                                Map.of('H', hayIng, 'S', stickIng, 'B', slabIng),
                                result
                        );

                Method registerRecipe = recipeRegistry.getClass().getMethod("register", recipeDefClass);
                registerRecipe.invoke(recipeRegistry, recipeDef);
            }

            plugin.getLogger().info("Đã đăng ký Bù Nhìn Rơm (haohan:straw_dummy) với HaoHanItemCore.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Không thể đăng ký haohan:straw_dummy với HaoHanItemCore: " + t.getMessage());
        }
    }
}
