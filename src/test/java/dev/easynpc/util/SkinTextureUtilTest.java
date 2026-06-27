package dev.easynpc.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinTextureUtilTest {
    @Test
    void encodesSkinUrlAsMinecraftTexturePayload() {
        String encoded = SkinTextureUtil.toTextureProperty("https://textures.minecraft.net/texture/example");
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

        assertTrue(decoded.contains("\"SKIN\""));
        assertTrue(decoded.contains("https://textures.minecraft.net/texture/example"));
    }

    @Test
    void escapesJsonInSkinUrl() {
        String encoded = SkinTextureUtil.toTextureProperty("https://example.test/a\"b");
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

        assertEquals("{\"textures\":{\"SKIN\":{\"url\":\"https://example.test/a\\\"b\"}}}", decoded);
    }

    @Test
    void blankSkinUrlReturnsNull() {
        assertNull(SkinTextureUtil.toTextureProperty(" "));
    }
}
