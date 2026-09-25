package dev.blockfolk.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    private final String endpointIssue;

    public OpenRouterClient(String endpoint, String apiKey, String model, int timeoutSeconds) {
        this(endpoint, apiKey, model, timeoutSeconds, 1600);
    }

    public OpenRouterClient(String endpoint, String apiKey, String model, int timeoutSeconds, int maxTokens) {
        URI parsedEndpoint = null;
        String parsedIssue = "";
        try {
            parsedEndpoint = URI.create(endpoint == null ? "" : endpoint.trim());
            if (!"https".equalsIgnoreCase(parsedEndpoint.getScheme()) || parsedEndpoint.getHost() == null) {
                parsedIssue = "endpoint must be a valid HTTPS URL";
                parsedEndpoint = null;
            }
        } catch (IllegalArgumentException exception) {
            parsedIssue = "endpoint is not a valid URL";
        }
        this.endpoint = parsedEndpoint;
        this.endpointIssue = parsedIssue;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.timeout = Duration.ofSeconds(Math.max(2, timeoutSeconds));
        this.maxTokens = Math.max(350, maxTokens);
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public boolean configured() {
        return endpoint != null && !apiKey.isBlank() && !model.isBlank();
    }

    public String configurationIssue() {
        if (!endpointIssue.isBlank())
            return endpointIssue;
        if (apiKey.isBlank() && model.isBlank())
            return "API key and model are missing";
        if (apiKey.isBlank())
            return "API key is missing";
        if (model.isBlank())
            return "Model is missing";
        return "";
    }

    public CompletableFuture<String> complete(String systemPrompt, String context) {
        return request(systemPrompt, context, null, false);
    }

    public CompletableFuture<String> completeActions(String systemPrompt, String context, JsonArray tools,
            boolean group) {
        if (tools == null || tools.isEmpty())
            return CompletableFuture.failedFuture(new IllegalArgumentException("AI action tools are missing"));
        return request(systemPrompt, context, tools, group);
    }

    ActionSession actionSession(String systemPrompt, String context, JsonArray tools, boolean group) {
        if (tools == null || tools.isEmpty())
            throw new IllegalArgumentException("AI action tools are missing");
        return new ActionSession(systemPrompt, context, tools, group);
    }

    final class ActionSession {
        private final JsonArray messages = new JsonArray();
        private final JsonArray tools;
        private final boolean group;

        private ActionSession(String systemPrompt, String context, JsonArray tools, boolean group) {
            messages.add(message("system", systemPrompt));
            messages.add(message("user", context));
            this.tools = tools;
            this.group = group;
        }

        CompletableFuture<ActionTurn> complete() {
            if (!configured())
                return CompletableFuture.failedFuture(new IllegalStateException("OpenRouter is not configured"));
            JsonObject body = requestBody(tools, messages.deepCopy());
            return send(body).thenApply(response -> {
                JsonObject choice = firstChoice(response);
                JsonObject assistant = choice.getAsJsonObject("message");
                JsonArray calls = assistant == null ? null : assistant.getAsJsonArray("tool_calls");
                boolean truncated = "length".equals(string(choice, "finish_reason"));
                if (calls != null) {
                    for (JsonElement element : calls) {
                        if (!element.isJsonObject() || string(element.getAsJsonObject(), "id") == null)
                            truncated = true;
                    }
                }
                return new ActionTurn(responseActions(response, group, tools),
                        assistant == null ? null : assistant.deepCopy(),
                        calls == null ? new JsonArray() : calls.deepCopy(), truncated);
            });
        }

        void retry(String issue) {
            messages.add(message("user", "The previous response was unusable (" + issue
                    + "). Call valid available functions using listed target aliases."));
        }

        void result(ActionTurn turn, List<String> results, String updatedContext) {
            messages.add(turn.assistant());
            int index = 0;
            for (JsonElement element : turn.calls()) {
                JsonObject call = element.getAsJsonObject();
                JsonObject toolMessage = message("tool",
                        index < results.size()
                                ? results.get(index)
                                : "Action was rejected or exceeded the turn limit.");
                toolMessage.addProperty("tool_call_id", string(call, "id"));
                messages.add(toolMessage);
                index++;
            }
            messages.add(message("user", "Updated NPC state after those actions:\n" + updatedContext
                    + "\nIf another action is needed, call a function. Otherwise finish with no tool calls."));
        }

        JsonArray transcript() {
            return messages.deepCopy();
        }
    }

    record ActionTurn(String normalized, JsonObject assistant, JsonArray calls, boolean truncated) {
    }

    private JsonObject requestBody(JsonArray tools, JsonArray messages) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.4);
        body.addProperty("max_tokens", maxTokens);
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", "none");
        body.add("reasoning", reasoning);
        if (tools == null) {
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            body.add("response_format", responseFormat);
        } else {
            body.add("tools", tools);
            body.addProperty("tool_choice", "auto");
        }
        body.add("messages", messages);
        return body;
    }

    private CompletableFuture<String> send(JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://github.com/blockfolk").header("X-Title", "Blockfolk AI Behaviour")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("OpenRouter returned HTTP " + response.statusCode());
            return response.body();
        }).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static JsonObject firstChoice(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty() || !choices.get(0).isJsonObject())
            throw new IllegalStateException("OpenRouter response did not contain a choice");
        return choices.get(0).getAsJsonObject();
    }

    private CompletableFuture<String> request(String systemPrompt, String context, JsonArray tools, boolean group) {
        if (!configured())
            return CompletableFuture
                    .failedFuture(new IllegalStateException("OpenRouter API key and model are not configured"));
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", context));
        return send(requestBody(tools, messages)).thenApply(
                response -> tools == null ? responseContent(response) : responseActions(response, group, tools));
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
            if (content != null && content.isJsonPrimitive())
                return content.getAsString();
            if (content != null && content.isJsonArray()) {
                StringBuilder combined = new StringBuilder();
                for (JsonElement part : content.getAsJsonArray()) {
                    if (!part.isJsonObject())
                        continue;
                    JsonElement text = part.getAsJsonObject().get("text");
                    if (text != null && text.isJsonPrimitive())
                        combined.append(text.getAsString());
                }
                if (!combined.isEmpty())
                    return combined.toString();
            }
            String finishReason = choice.has("finish_reason") && choice.get("finish_reason").isJsonPrimitive()
                    ? choice.get("finish_reason").getAsString()
                    : "unknown";
            throw new IllegalStateException(
                    "OpenRouter returned no message content (finish reason: " + finishReason + ")");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("OpenRouter returned a malformed response", exception);
        }
    }

    /**
     * Converts native function calls into the existing validated action envelope.
     */
    static String responseActions(String body, boolean group, JsonArray tools) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty() || !choices.get(0).isJsonObject())
                throw new IllegalStateException("OpenRouter response did not contain a choice");
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonElement messageElement = choice.get("message");
            JsonObject message = messageElement != null && messageElement.isJsonObject()
                    ? messageElement.getAsJsonObject()
                    : null;
            JsonElement callsElement = message == null ? null : message.get("tool_calls");
            JsonArray calls = callsElement != null && callsElement.isJsonArray() ? callsElement.getAsJsonArray() : null;
            // A text-only or truncated answer is unusable and triggers the existing retry.
            if (calls == null || calls.isEmpty() || "length".equals(string(choice, "finish_reason")))
                return "{}";
            JsonObject normalized = new JsonObject();
            JsonArray actions = new JsonArray();
            Map<String, JsonArray> grouped = new LinkedHashMap<>();
            Set<String> offeredNames = tools.asList().stream()
                    .map(tool -> tool.getAsJsonObject().getAsJsonObject("function").get("name").getAsString())
                    .collect(Collectors.toSet());
            for (JsonElement element : calls) {
                JsonObject call = element.isJsonObject() ? element.getAsJsonObject() : null;
                JsonElement functionElement = call == null ? null : call.get("function");
                JsonObject function = functionElement != null && functionElement.isJsonObject()
                        ? functionElement.getAsJsonObject()
                        : null;
                String name = function == null ? null : string(function, "name");
                String arguments = function == null ? null : string(function, "arguments");
                JsonObject argumentsObject = null;
                try {
                    if (arguments != null)
                        argumentsObject = JsonParser.parseString(arguments).getAsJsonObject();
                } catch (RuntimeException ignored) {
                    // Preserve the failed call so the validator can reject it.
                }
                JsonObject action = new JsonObject();
                action.addProperty("type",
                        name == null || argumentsObject == null || !offeredNames.contains(name)
                                ? "INVALID_TOOL_CALL"
                                : name);
                if (argumentsObject != null) {
                    for (String field : new String[]{"text", "target", "animation"}) {
                        if (argumentsObject.has(field))
                            action.add(field, argumentsObject.get(field));
                    }
                }
                if (group) {
                    String npc = argumentsObject == null ? null : string(argumentsObject, "npc");
                    grouped.computeIfAbsent(npc == null ? "" : npc, ignored -> new JsonArray()).add(action);
                } else {
                    actions.add(action);
                }
            }
            if (group) {
                JsonArray responses = new JsonArray();
                grouped.forEach((npc, npcActions) -> {
                    JsonObject response = new JsonObject();
                    response.addProperty("npc", npc);
                    response.add("actions", npcActions);
                    responses.add(response);
                });
                normalized.add("responses", responses);
            } else {
                normalized.add("actions", actions);
            }
            return normalized.toString();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("OpenRouter returned malformed tool calls", exception);
        }
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

}
