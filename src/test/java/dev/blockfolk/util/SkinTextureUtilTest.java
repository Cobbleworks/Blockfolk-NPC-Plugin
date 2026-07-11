package dev.blockfolk.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void normalizesTextureHashes() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        assertEquals("https://textures.minecraft.net/texture/" + hash, SkinTextureUtil.normalizeTextureUrl(hash));
    }

    @Test
    void defaultClearsTexture() {
        assertNull(SkinTextureUtil.normalizeTextureUrl("default"));
    }

    @Test
    void acceptsHttpsSkinImageUrls() {
        String url = "https://www.minecraftskins.com/uploads/skins/2026/07/09/rotten-hearts-24183095.png?v960";

        assertEquals(url, SkinTextureUtil.normalizeTextureUrl(url));
    }

    @Test
    void identifiesMinecraftTextureUrls() {
        assertTrue(SkinTextureUtil.isMinecraftTextureUrl(
            "https://textures.minecraft.net/texture/0123456789abcdef0123456789abcdef"));
    }

    @Test
    void doesNotTreatExternalImagesAsMinecraftTextures() {
        assertFalse(SkinTextureUtil.isMinecraftTextureUrl("https://s.namemc.com/i/34743edd854210eb.png"));
    }

    @Test
    void rejectsInsecureSkinImageUrls() {
        assertThrows(IllegalArgumentException.class, () ->
            SkinTextureUtil.normalizeTextureUrl("http://example.test/skin.png"));
    }
}
