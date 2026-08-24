package vn.haohan.utilities.recipe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/** Adds the smoker recipe for turning rotten flesh into leather. */
public final class RottenFleshSmokingRecipe {
    private final NamespacedKey recipeKey;

    public RottenFleshSmokingRecipe(JavaPlugin plugin) {
        this.recipeKey = new NamespacedKey(plugin, "rotten_flesh_smoking");
    }

    public void register() {
        Bukkit.removeRecipe(recipeKey);
        Bukkit.addRecipe(new SmokingRecipe(
                recipeKey,
                new ItemStack(Material.LEATHER),
                new RecipeChoice.MaterialChoice(Material.ROTTEN_FLESH),
                0.1F,
                100
        ));
    }

    public void unregister() {
        Bukkit.removeRecipe(recipeKey);
    }
}
