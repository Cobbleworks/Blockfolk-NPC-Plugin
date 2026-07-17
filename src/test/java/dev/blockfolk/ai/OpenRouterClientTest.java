package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenRouterClientTest {

    @Test
    void readsTextContent() {
        assertEquals("{\"actions\":[]}", OpenRouterClient.responseContent("""
                {"choices":[{"message":{"content":"{\\\"actions\\\":[]}"},"finish_reason":"stop"}]}
                """));
    }

    @Test
    void explainsNullContentInsteadOfThrowingJsonNull() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> OpenRouterClient.responseContent("""
                        {"choices":[{"message":{"content":null},"finish_reason":"length"}]}
                        """));

        assertTrue(error.getMessage().contains("finish reason: length"));
    }

    @Test
    void increasesLengthRetryAllowanceWithoutExceedingSafetyCap() {
        assertEquals(1600, OpenRouterClient.retryTokens(800));
        assertEquals(4096, OpenRouterClient.retryTokens(3000));
        assertEquals(4096, OpenRouterClient.retryTokens(4096));
    }
}
