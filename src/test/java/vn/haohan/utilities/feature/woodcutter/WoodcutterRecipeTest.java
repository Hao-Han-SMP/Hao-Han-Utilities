package vn.haohan.utilities.feature.woodcutter;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.utilities.feature.woodcutter.WoodcutterService.RecipeDef;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WoodcutterRecipeTest {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");

    @Test
    @DisplayName("Verify that recipes.csv exists, parses correctly, and has unique keys")
    void testLoadAndParseDefinitions() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(WoodcutterService.RECIPE_RESOURCE);
        assertNotNull(stream, "File woodcutter/recipes.csv must be available on the classpath");

        List<RecipeDef> defs = WoodcutterService.parseDefinitions(stream);
        assertTrue(defs.size() >= 600, "Should load at least 600 recipe definitions, loaded: " + defs.size());

        Set<String> keys = new HashSet<>();
        for (RecipeDef def : defs) {
            assertTrue(KEY_PATTERN.matcher(def.key()).matches(), "Recipe key must match Bukkit key requirements: " + def.key());
            assertTrue(keys.add(def.key()), "Duplicate recipe key found: " + def.key());
            assertTrue(def.count() > 0, "Recipe result count must be positive: " + def.key());
        }
    }

    @Test
    @DisplayName("Verify that every ingredient and result material exists in Paper API")
    void testMaterialsExistInPaper() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(WoodcutterService.RECIPE_RESOURCE);
        assertNotNull(stream);

        List<RecipeDef> defs = WoodcutterService.parseDefinitions(stream);
        for (RecipeDef def : defs) {
            Material ingredient = Material.matchMaterial(def.ingredient().toUpperCase(Locale.ROOT));
            assertNotNull(ingredient, "Unknown ingredient material '" + def.ingredient() + "' in recipe: " + def.key());

            Material result = Material.matchMaterial(def.result().toUpperCase(Locale.ROOT));
            assertNotNull(result, "Unknown result material '" + def.result() + "' in recipe: " + def.key());
        }
    }

    @Test
    @DisplayName("Verify woodcutter location same block logic")
    void testSameBlockLogic() {
        assertFalse(WoodcutterService.isSameBlock(null, null));
    }
}
