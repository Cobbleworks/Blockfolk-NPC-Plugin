package dev.blockfolk.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class OpenRouterClient {

    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxTokens;

    public OpenRouterClient(String endpoint, String apiKey, String model, int timeoutSeconds) {
        this(endpoint, apiKey, model, timeoutSeconds, 1600);
    }

    public OpenRouterClient(String endpoint, String apiKey, String model, int timeoutSeconds, int maxTokens) {
        this.endpoint = URI.create(endpoint);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.timeout = Duration.ofSeconds(Math.max(2, timeoutSeconds));
        this.maxTokens = Math.max(350, maxTokens);
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public boolean configured() {
        return !apiKey.isBlank() && !model.isBlank();
    }

    public String configurationIssue() {
        if (apiKey.isBlank() && model.isBlank()) return "API key and model are missing";
        if (apiKey.isBlank()) return "API key is missing";
        if (model.isBlank()) return "Model is missing";
        return "";
    }

    public CompletableFuture<String> complete(String systemPrompt, String context) {
        if (!configured()) return CompletableFuture.failedFuture(
                new IllegalStateException("OpenRouter API key and model are not configured"));
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.4);
        body.addProperty("max_tokens", maxTokens);
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", "none");
        body.add("reasoning", reasoning);
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        body.add("response_format", responseFormat);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", context));
        body.add("messages", messages);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://github.com/blockfolk")
                .header("X-Title", "Blockfolk AI Behaviour")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("OpenRouter returned HTTP " + response.statusCode());
                    }
                    return responseContent(response.body());
                });
    }

    static String responseContent(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty() || !choices.get(0).isJsonObject()) {
                throw new IllegalStateException("OpenRouter response did not contain a choice");
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            JsonElement content = message == null ? null : message.get("content");
            if (content != null && content.isJsonPrimitive()) return content.getAsString();
            if (content != null && content.isJsonArray()) {
                StringBuilder combined = new StringBuilder();
                for (JsonElement part : content.getAsJsonArray()) {
                    if (!part.isJsonObject()) continue;
                    JsonElement text = part.getAsJsonObject().get("text");
                    if (text != null && text.isJsonPrimitive()) combined.append(text.getAsString());
                }
                if (!combined.isEmpty()) return combined.toString();
            }
            String finishReason = choice.has("finish_reason") && choice.get("finish_reason").isJsonPrimitive()
                    ? choice.get("finish_reason").getAsString() : "unknown";
            throw new IllegalStateException("OpenRouter returned no message content (finish reason: "
                    + finishReason + ")");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("OpenRouter returned a malformed response", exception);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

}
