package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiControlServiceTest {

    @Test
    void replacingClientUpdatesAiAvailabilityWithoutRestartingService() {
        String endpoint = "https://openrouter.ai/api/v1/chat/completions";
        AiControlService service = new AiControlService(null, null, null, null, null,
                new OpenRouterClient(endpoint, "key", "", 12), 2, 20);

        assertFalse(service.configured());
        service.setClient(new OpenRouterClient(endpoint, "key", "other/model", 12));
        assertTrue(service.configured());
        service.setClient(new OpenRouterClient(endpoint, "", "other/model", 12));
        assertFalse(service.configured());
        assertTrue(service.configurationIssue().contains("API key"));
    }
}
