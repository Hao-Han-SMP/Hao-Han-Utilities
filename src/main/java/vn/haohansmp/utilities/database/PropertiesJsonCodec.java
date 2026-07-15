package vn.haohansmp.utilities.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public final class PropertiesJsonCodec {
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<String, String>>() { }.getType();

    private PropertiesJsonCodec() {
    }

    public static String encode(Map<String, String> properties) {
        return GSON.toJson(properties == null ? Map.of() : properties, TYPE);
    }

    public static Map<String, String> decode(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, String> decoded = GSON.fromJson(json, TYPE);
        return decoded == null ? Map.of() : Map.copyOf(decoded);
    }
}
