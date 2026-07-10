package dev.easynpc.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Converts arbitrary skin images into signed, Minecraft-hosted profile textures. */
public final class SkinResolver {
    private static final URI QUEUE_URI = URI.create("https://api.mineskin.org/v2/queue");
    private static final int MAX_POLL_ATTEMPTS = 30;

    private final HttpClient client;
    private final String userAgent;
    private final String apiKey;
    private final Map<String, CompletableFuture<ResolvedSkin>> requests = new ConcurrentHashMap<>();

    public SkinResolver(String userAgent, String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), userAgent, apiKey);
    }

    SkinResolver(HttpClient client, String userAgent, String apiKey) {
        this.client = client;
        this.userAgent = userAgent;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public CompletableFuture<ResolvedSkin> resolve(String imageUrl) {
        return requests.computeIfAbsent(imageUrl, ignored -> queue(imageUrl)
            .whenComplete((result, error) -> requests.remove(imageUrl)));
    }

    private CompletableFuture<ResolvedSkin> queue(String imageUrl) {
        JsonObject body = new JsonObject();
        body.addProperty("url", imageUrl);
        body.addProperty("visibility", "unlisted");
        body.addProperty("variant", "unknown");
        HttpRequest request = request(QUEUE_URI)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(response -> handleResponse(response, 0));
    }

    private CompletableFuture<ResolvedSkin> poll(String jobId, int attempt) {
        if (attempt >= MAX_POLL_ATTEMPTS) {
            return CompletableFuture.failedFuture(new SkinResolutionException("Skin processing timed out. Please try again."));
        }
        URI jobUri = QUEUE_URI.resolve("/v2/queue/" + jobId);
        return CompletableFuture.runAsync(() -> {
        }, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)).thenCompose(ignored ->
            client.sendAsync(request(jobUri).GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> handleResponse(response, attempt + 1))
        );
    }

    private CompletableFuture<ResolvedSkin> handleResponse(HttpResponse<String> response, int attempt) {
        if (response.statusCode() != 200 && response.statusCode() != 202) {
            return CompletableFuture.failedFuture(new SkinResolutionException(errorMessage(response)));
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(response.body()).getAsJsonObject();
            ResolvedSkin completed = parseCompletedSkin(root);
            if (completed != null) {
                return CompletableFuture.completedFuture(completed);
            }
            JsonObject job = root.getAsJsonObject("job");
            String jobId = requiredString(job, "id");
            String status = requiredString(job, "status");
            if ("failed".equalsIgnoreCase(status)) {
                return CompletableFuture.failedFuture(new SkinResolutionException("MineSkin could not process that image."));
            }
            return poll(jobId, attempt);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(new SkinResolutionException("MineSkin returned an invalid response.", exception));
        }
    }

    static ResolvedSkin parseCompletedSkin(JsonObject root) {
        JsonObject skin = object(root, "skin");
        JsonObject texture = object(skin, "texture");
        JsonObject data = object(texture, "data");
        JsonObject urls = object(texture, "url");
        if (skin == null || texture == null || data == null) {
            return null;
        }
        String url = optionalString(urls, "skin");
        if (url == null) {
            JsonObject hashes = object(texture, "hash");
            String hash = optionalString(hashes, "skin");
            if (hash != null) {
                url = "https://textures.minecraft.net/texture/" + hash;
            }
        }
        String value = requiredString(data, "value");
        String signature = requiredString(data, "signature");
        if (url == null) {
            throw new SkinResolutionException("MineSkin did not return a Minecraft texture URL.");
        }
        return new ResolvedSkin(url, value, signature);
    }

    private HttpRequest.Builder request(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("User-Agent", userAgent);
        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    private String errorMessage(HttpResponse<String> response) {
        if (response.statusCode() == 429) {
            return "The skin service is rate-limited. Please wait a moment and try again.";
        }
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonElement messages = root.get("messages");
            if (messages != null && messages.isJsonArray() && !messages.getAsJsonArray().isEmpty()) {
                String message = optionalString(messages.getAsJsonArray().get(0).getAsJsonObject(), "message");
                if (message != null) {
                    return "Could not process skin: " + message;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to the status-based error.
        }
        return "The skin service returned HTTP " + response.statusCode() + ".";
    }

    private static JsonObject object(JsonObject parent, String member) {
        if (parent == null) {
            return null;
        }
        JsonElement element = parent.get(member);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String optionalString(JsonObject object, String member) {
        if (object == null) {
            return null;
        }
        JsonElement element = object.get(member);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String requiredString(JsonObject object, String member) {
        String value = optionalString(object, member);
        if (value == null || value.isBlank()) {
            throw new SkinResolutionException("MineSkin response is missing '" + member + "'.");
        }
        return value;
    }

    public static final class SkinResolutionException extends RuntimeException {
        public SkinResolutionException(String message) {
            super(message);
        }

        public SkinResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
