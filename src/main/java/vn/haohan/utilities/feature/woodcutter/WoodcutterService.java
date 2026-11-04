package vn.haohan.utilities.feature.woodcutter;

import io.papermc.paper.event.entity.EntityMoveEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Cung cấp tính năng cắt gỗ trong máy cắt đá (Stonecutter) theo WoodCutter.
 * Đồng thời hỗ trợ tùy chọn gây sát thương khi dẫm lên máy cắt đá.
 */
public final class WoodcutterService implements Listener {
    public static final String RECIPE_RESOURCE = "woodcutter/recipes.csv";

    private final Plugin plugin;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public WoodcutterService(Plugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void register() {
        unregister();
        if (!isEnabled()) {
            return;
        }

        List<RecipeDef> defs = loadDefinitions();
        boolean includeStoneVariants = isStoneVariantsEnabled();
        int added = 0;

        for (RecipeDef def : defs) {
            if (!includeStoneVariants && isStoneVariant(def.ingredient())) {
                continue;
            }

            Material ingredient = materialFromId(def.ingredient());
            Material result = materialFromId(def.result());
            if (ingredient == null || result == null) {
                plugin.getLogger().warning("Bỏ qua công thức woodcutter không hợp lệ: " + def.key()
                        + " (ingredient=" + def.ingredient() + ", result=" + def.result() + ")");
                continue;
            }

            NamespacedKey key = new NamespacedKey(plugin, def.key());
            StonecuttingRecipe recipe = new StonecuttingRecipe(
                    key,
                    new ItemStack(result, def.count()),
                    new RecipeChoice.MaterialChoice(ingredient)
            );

            Bukkit.removeRecipe(key);
            try {
                if (Bukkit.addRecipe(recipe)) {
                    registeredKeys.add(key);
                    added++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Không thể đăng ký công thức máy cắt đá: " + def.key(), e);
            }
        }

        plugin.getLogger().info("Đã đăng ký " + added + " công thức máy cắt đá (WoodCutter).");
    }

    public synchronized void unregister() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
    }

    public synchronized void reload() {
        unregister();
        register();
    }

    public synchronized int getRegisteredRecipeCount() {
        return registeredKeys.size();
    }

    public List<NamespacedKey> getRegisteredKeys() {
        return Collections.unmodifiableList(registeredKeys);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("woodcutter.enabled", true);
    }

    public boolean isStoneVariantsEnabled() {
        return plugin.getConfig().getBoolean("woodcutter.stone-variants", true);
    }

    public boolean isContactDamageEnabled() {
        return plugin.getConfig().getBoolean("woodcutter.contact-damage.enabled", false);
    }

    public double getContactDamageHearts() {
        return Math.max(0.0, plugin.getConfig().getDouble("woodcutter.contact-damage.hearts", 1.0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        if (isEnabled()) {
            register();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent event) {
        if (!isEnabled() || !isContactDamageEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        applyContactDamage(living, event.getFrom(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled() || !isContactDamageEnabled()) {
            return;
        }
        applyContactDamage(event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void applyContactDamage(LivingEntity living, @Nullable Location from, @Nullable Location to) {
        double hearts = getContactDamageHearts();
        if (hearts <= 0.0) {
            return;
        }
        if (isSameBlock(from, to)) {
            return;
        }
        if (!isStandingOnStonecutter(to) || isStandingOnStonecutter(from)) {
            return;
        }
        living.damage(hearts * 2.0);
    }

    public static boolean isSameBlock(@Nullable Location first, @Nullable Location second) {
        if (first == null || second == null) return false;
        if (first.getWorld() != second.getWorld()) return false;
        return first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    public static boolean isStandingOnStonecutter(@Nullable Location location) {
        if (location == null) return false;
        Material feetBlock = location.getBlock().getType();
        if (feetBlock == Material.STONECUTTER) return true;
        return location.getBlock().getRelative(BlockFace.DOWN).getType() == Material.STONECUTTER;
    }

    private boolean isStoneVariant(String ingredient) {
        return ingredient.equalsIgnoreCase("stone") || ingredient.equalsIgnoreCase("deepslate");
    }

    private Material materialFromId(String id) {
        return Material.matchMaterial(id.toUpperCase(Locale.ROOT));
    }

    private List<RecipeDef> loadDefinitions() {
        InputStream stream = plugin.getResource(RECIPE_RESOURCE);
        if (stream == null) {
            stream = getClass().getClassLoader().getResourceAsStream(RECIPE_RESOURCE);
        }
        if (stream == null) {
            plugin.getLogger().severe("Không tìm thấy file định nghĩa công thức: " + RECIPE_RESOURCE);
            return List.of();
        }
        try {
            return parseDefinitions(stream);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Lỗi khi đọc file định nghĩa công thức: " + RECIPE_RESOURCE, e);
            return List.of();
        }
    }

    public static List<RecipeDef> parseDefinitions(InputStream stream) throws IOException {
        List<RecipeDef> defs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split(",");
                if (parts.length != 4) {
                    throw new IllegalStateException("Định dạng CSV không hợp lệ ở dòng " + lineNumber + ": " + line);
                }
                defs.add(new RecipeDef(
                        parts[0].trim(),
                        parts[1].trim(),
                        parts[2].trim(),
                        Integer.parseInt(parts[3].trim())
                ));
            }
        }
        return List.copyOf(defs);
    }

    public record RecipeDef(String key, String ingredient, String result, int count) {}
}
