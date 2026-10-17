package vn.haohan.utilities.integration;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.block.CustomBlockDefinition;
import vn.haohan.itemcore.api.block.CustomBlockService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Adapter integrating ItemCore CustomBlockService with HaoHanUtilities Carry system.
 */
public final class ItemCoreCarryIntegration {

    private final JavaPlugin plugin;
    private CarryFilter filter;

    public ItemCoreCarryIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        boolean allowByDefault = plugin.getConfig().getBoolean("custom-blocks.allow-by-default", false);
        List<String> allowed = plugin.getConfig().getStringList("custom-blocks.allowed");
        List<String> denied = plugin.getConfig().getStringList("custom-blocks.denied");
        this.filter = new CarryFilter(allowByDefault, allowed, denied);
    }

    public boolean isCustomBlock(Block block) {
        if (block == null) return false;
        try {
            CustomBlockService service = getCustomBlockService();
            return service != null && service.isCustomBlock(block);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public Optional<CustomBlockDefinition> getCustomBlock(Block block) {
        if (block == null) return Optional.empty();
        try {
            CustomBlockService service = getCustomBlockService();
            if (service == null) return Optional.empty();
            return service.getCustomBlock(block);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public String getCustomBlockId(Block block) {
        if (block == null) return null;
        try {
            CustomBlockService service = getCustomBlockService();
            return service != null ? service.getCustomBlockId(block) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isCarryable(String customBlockId) {
        if (customBlockId == null || customBlockId.isBlank()) return false;
        return filter.isAllowed(customBlockId);
    }

    public boolean removeCustomBlock(Location location) {
        if (location == null) return false;
        try {
            CustomBlockService service = getCustomBlockService();
            return service != null && service.removeCustomBlock(location);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean placeCustomBlock(Location location, String customBlockId) {
        if (location == null || customBlockId == null) return false;
        try {
            CustomBlockService service = getCustomBlockService();
            return service != null && service.placeCustomBlock(location, customBlockId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public ItemStack resolveVisualItem(String customBlockId) {
        if (customBlockId == null) return null;
        try {
            var core = HaoHanItemCore.get();
            if (core == null) return null;

            // 1. Check definition drop item
            var blockService = core.getCustomBlockService();
            if (blockService != null) {
                var def = blockService.getRegistry().get(customBlockId);
                if (def.isPresent() && def.get().getDropItemId() != null) {
                    ItemStack fromDrop = core.getItemService().create(def.get().getDropItemId(), 1);
                    if (fromDrop != null) return fromDrop;
                }
            }

            // 2. Try creating item with same customBlockId
            ItemStack direct = core.getItemService().create(customBlockId, 1);
            if (direct != null) return direct;
        } catch (Throwable ignored) {}
        return null;
    }

    private CustomBlockService getCustomBlockService() {
        try {
            return HaoHanItemCore.get().getCustomBlockService();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Immutable filter rules for custom block carry permissions.
     */
    public static final class CarryFilter {
        private final boolean allowByDefault;
        private final List<Pattern> allowedPatterns;
        private final List<Pattern> deniedPatterns;

        public CarryFilter(boolean allowByDefault, List<String> allowed, List<String> denied) {
            this.allowByDefault = allowByDefault;
            this.allowedPatterns = compilePatterns(allowed);
            this.deniedPatterns = compilePatterns(denied);
        }

        public boolean isAllowed(String id) {
            if (id == null) return false;
            String lower = id.toLowerCase(Locale.ROOT);

            // Denied takes absolute precedence
            for (Pattern pattern : deniedPatterns) {
                if (pattern.matcher(lower).matches()) {
                    return false;
                }
            }

            // Allowed pattern matched
            for (Pattern pattern : allowedPatterns) {
                if (pattern.matcher(lower).matches()) {
                    return true;
                }
            }

            return allowByDefault;
        }

        private static List<Pattern> compilePatterns(List<String> list) {
            if (list == null || list.isEmpty()) return List.of();
            List<Pattern> patterns = new ArrayList<>();
            for (String raw : list) {
                if (raw == null || raw.isBlank()) continue;
                String regex = "\\Q" + raw.trim().toLowerCase(Locale.ROOT).replace("*", "\\E.*\\Q") + "\\E";
                patterns.add(Pattern.compile(regex));
            }
            return List.copyOf(patterns);
        }
    }
}
