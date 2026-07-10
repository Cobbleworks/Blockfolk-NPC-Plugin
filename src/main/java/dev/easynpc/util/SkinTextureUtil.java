package dev.easynpc.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SkinTextureUtil {
    private static final String TEXTURE_PREFIX = "https://textures.minecraft.net/texture/";
    private static final Pattern TEXTURE_HASH = Pattern.compile("[0-9a-fA-F]{32,128}");

    private SkinTextureUtil() {
    }

    public static String toTextureProperty(String skinUrl) {
        if (skinUrl == null || skinUrl.isBlank()) {
            return null;
        }
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + escapeJson(skinUrl.trim()) + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static String normalizeTextureUrl(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("none") || input.equalsIgnoreCase("default")) {
            return null;
        }
        String value = input.trim();
        if (TEXTURE_HASH.matcher(value).matches()) {
            return TEXTURE_PREFIX + value.toLowerCase(Locale.ROOT);
        }
        try {
            URI uri = new URI(value);
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || path == null
                || path.isBlank()) {
                throw invalidTextureUrl();
            }
            if ("textures.minecraft.net".equalsIgnoreCase(uri.getHost())) {
                if (!path.matches("/texture/[0-9a-fA-F]{32,128}")) {
                    throw invalidTextureUrl();
                }
                return TEXTURE_PREFIX + path.substring("/texture/".length()).toLowerCase(Locale.ROOT);
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw invalidTextureUrl();
        }
    }

    public static boolean isMinecraftTextureUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                && "textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().matches("/texture/[0-9a-fA-F]{32,128}");
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static IllegalArgumentException invalidTextureUrl() {
        return new IllegalArgumentException("Use an HTTPS skin image URL, a Minecraft texture hash, or 'default'.");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
