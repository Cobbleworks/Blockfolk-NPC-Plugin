package dev.blockfolk.util;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkinResolverTest {
    @Test
    void readsSignedTextureFromCompletedResponse() {
        String response = """
            {
              "job": {"id": "job-id", "status": "completed"},
              "skin": {
                "texture": {
                  "data": {"value": "encoded-value", "signature": "signed-value"},
                  "hash": {"skin": "texture-hash"},
                  "url": {"skin": "https://textures.minecraft.net/texture/texture-hash"}
                }
              }
            }
            """;

        ResolvedSkin skin = SkinResolver.parseCompletedSkin(JsonParser.parseString(response).getAsJsonObject());

        assertEquals("https://textures.minecraft.net/texture/texture-hash", skin.url());
        assertEquals("encoded-value", skin.textureValue());
        assertEquals("signed-value", skin.textureSignature());
    }

    @Test
    void waitingResponseHasNoCompletedSkin() {
        String response = "{\"job\":{\"id\":\"job-id\",\"status\":\"waiting\"}}";

        assertNull(SkinResolver.parseCompletedSkin(JsonParser.parseString(response).getAsJsonObject()));
    }
}
