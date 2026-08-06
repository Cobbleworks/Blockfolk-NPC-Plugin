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
    void invalidOrInsecureEndpointDisablesOnlyAiClient() {
        OpenRouterClient malformed = new OpenRouterClient("not a url", "key", "model", 5);
        OpenRouterClient insecure = new OpenRouterClient("http://example.test/api", "key", "model", 5);

        assertTrue(!malformed.configured());
        assertTrue(malformed.configurationIssue().contains("valid URL"));
        assertTrue(!insecure.configured());
        assertTrue(insecure.configurationIssue().contains("HTTPS"));
    }
}
