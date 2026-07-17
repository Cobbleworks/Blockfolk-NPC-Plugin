package dev.blockfolk.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class OpenRouterClient {

    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public OpenRouterClient(String endpoint, String apiKey, String model, int timeoutSeconds) {
        this.endpoint = URI.create(endpoint);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.timeout = Duration.ofSeconds(Math.max(2, timeoutSeconds));
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public boolean configured() {
        return !apiKey.isBlank() && !model.isBlank();
    }

    public CompletableFuture<String> complete(String systemPrompt, String context) {
        if (!configured()) return CompletableFuture.failedFuture(
                new IllegalStateException("OpenRouter API key and model are not configured"));
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.4);
        body.addProperty("max_tokens", 350);
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
                .header("X-Title", "Blockfolk AI Control")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("OpenRouter returned HTTP " + response.statusCode());
                    }
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("message").get("content").getAsString();
                });
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }
}
