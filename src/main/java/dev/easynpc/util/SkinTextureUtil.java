package dev.easynpc.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class SkinTextureUtil {
    private SkinTextureUtil() {
    }

    public static String toTextureProperty(String skinUrl) {
        if (skinUrl == null || skinUrl.isBlank()) {
            return null;
        }
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + escapeJson(skinUrl.trim()) + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
