package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

class OpenRouterClientTest {

    @Test
    void modelCanBeChangedWithoutRestartingClient() {
        OpenRouterClient client = new OpenRouterClient("https://example.test/api", "key", "", 5);
        assertTrue(!client.configured());
        client.setModel("test/model");
        assertTrue(client.configured());
    }

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

    @Test
    void readsNativeActionCallsInOrderAndRejectsUnadvertisedFunctions() {
        JsonArray tools = AiActionTools.definitions(EnumSet.of(AiActionType.SAY, AiActionType.DO_NOTHING), List.of());
        String response = """
                {"choices":[{"message":{"content":null,"tool_calls":[
                  {"function":{"name":"say","arguments":"{\\"text\\":\\"Hello\\"}"}},
                  {"function":{"name":"start_combat","arguments":"{\\"target\\":\\"nearest_player\\"}"}},
                  {"function":{"name":"do_nothing","arguments":"{}"}}
                ]},"finish_reason":"tool_calls"}]}
                """;

        String normalized = OpenRouterClient.responseActions(response, false, tools);
        AiParseResult<AiDecision> parsed = AiDecisionParser.parseDetailed(normalized, AiControlSettings.defaults());

        assertEquals(List.of(AiActionType.SAY, AiActionType.DO_NOTHING),
                parsed.value().actions().stream().map(AiDecision.Action::type).toList());
        assertEquals("Hello", parsed.value().actions().getFirst().text());
        assertTrue(parsed.issue().contains("rejected"));
    }

    @Test
    void groupsNativeCallsByResponseId() {
        JsonArray tools = AiActionTools.definitions(EnumSet.of(AiActionType.SAY, AiActionType.DO_NOTHING),
                List.of("npc_1", "npc_2"));
        String response = """
                {"choices":[{"message":{"tool_calls":[
                  {"function":{"name":"say","arguments":"{\\"npc\\":\\"npc_1\\",\\"text\\":\\"First\\"}"}},
                  {"function":{"name":"say","arguments":"{\\"npc\\":\\"npc_2\\",\\"text\\":\\"Second\\"}"}},
                  {"function":{"name":"say","arguments":"{\\"npc\\":\\"npc_1\\",\\"text\\":\\"Third\\"}"}}
                ]},"finish_reason":"tool_calls"}]}
                """;

        String normalized = OpenRouterClient.responseActions(response, true, tools);
        var parsed = AiGroupDecisionParser.parseDetailed(normalized,
                java.util.Map.of("npc_1", AiControlSettings.defaults(), "npc_2", AiControlSettings.defaults()));

        assertTrue(parsed.usable());
        assertEquals(List.of("First", "Third"),
                parsed.value().get("npc_1").actions().stream().map(AiDecision.Action::text).toList());
        assertEquals("Second", parsed.value().get("npc_2").actions().getFirst().text());
    }

    @Test
    void preservesRememberLocationNameFromNativeCall() {
        AiControlSettings settings = AiControlSettings.defaults().toggle(AiActionType.REMEMBER_LOCATION);
        JsonArray tools = AiActionTools.definitions(settings.allowedActions(), List.of());
        String normalized = OpenRouterClient.responseActions("""
                {"choices":[{"message":{"tool_calls":[
                  {"function":{"name":"remember_location","arguments":"{\\"name\\":\\"Town/Market\\"}"}}
                ]},"finish_reason":"tool_calls"}]}
                """, false, tools);

        AiDecision.Action action = AiDecisionParser.parse(normalized, settings).actions().getFirst();
        assertEquals(AiActionType.REMEMBER_LOCATION, action.type());
        assertEquals("Town/Market", action.locationName());
    }

    @Test
    void textOnlyGameplayResponseIsUnusableForRetry() {
        JsonArray tools = AiActionTools.definitions(EnumSet.of(AiActionType.DO_NOTHING), List.of());
        String normalized = OpenRouterClient.responseActions("""
                {"choices":[{"message":{"content":"I will wait"},"finish_reason":"stop"}]}
                """, false, tools);

        assertTrue(!AiDecisionParser.parseDetailed(normalized, AiControlSettings.defaults()).usable());
    }

    @Test
    void actionSessionCarriesToolResultsAndUpdatedContextIntoNextRound() {
        OpenRouterClient client = new OpenRouterClient("https://example.test/api", "key", "model", 5);
        JsonArray tools = AiActionTools.definitions(EnumSet.of(AiActionType.SAY), List.of());
        OpenRouterClient.ActionSession session = client.actionSession("rules", "initial state", tools, false);
        var assistant = JsonParser.parseString("""
                {"role":"assistant","content":null,"tool_calls":[
                  {"id":"call_1","type":"function","function":{"name":"say","arguments":"{\\"text\\":\\"Hello\\"}"}}
                ]}
                """).getAsJsonObject();
        session.feedback(
                new OpenRouterClient.ActionTurn("", assistant, assistant.getAsJsonArray("tool_calls"), false, ""),
                List.of("Speech was dispatched."), "NPC is speaking to Alex", true);

        JsonArray messages = session.transcript();
        assertEquals(5, messages.size());
        assertEquals("assistant", messages.get(2).getAsJsonObject().get("role").getAsString());
        assertEquals("call_1", messages.get(3).getAsJsonObject().get("tool_call_id").getAsString());
        assertEquals("Speech was dispatched.", messages.get(3).getAsJsonObject().get("content").getAsString());
        assertTrue(messages.get(4).getAsJsonObject().get("content").getAsString().contains("NPC is speaking to Alex"));
    }

    @Test
    void normalizedCallsCarryTheirIndexAndPreciseRejection() {
        JsonArray tools = AiActionTools.definitions(EnumSet.of(AiActionType.SAY, AiActionType.MOVE_TO), List.of());
        String normalized = OpenRouterClient.responseActions("""
                {"choices":[{"message":{"tool_calls":[
                  {"id":"a","function":{"name":"say","arguments":"{\\"text\\":\\"On my way\\"}"}},
                  {"id":"b","function":{"name":"teleport","arguments":"{}"}},
                  {"id":"c","function":{"name":"move_to","arguments":"not json"}}
                ]},"finish_reason":"tool_calls"}]}
                """, false, tools);
        AiControlSettings settings = AiControlSettings.defaults().toggle(AiActionType.MOVE_TO);

        AiParseResult<AiDecision> parsed = AiDecisionParser.parseDetailed(normalized, settings);

        assertEquals(List.of(0, 1, 2), parsed.outcomes().stream().map(AiCallOutcome::call).toList());
        assertTrue(parsed.outcomes().get(0).isAccepted());
        assertTrue(parsed.outcomes().get(1).rejection().contains("unknown function"));
        assertTrue(parsed.outcomes().get(2).rejection().contains("valid JSON"));
    }

    @Test
    void firstRequestRequiresAToolCallAndFollowUpsMayFinish() {
        OpenRouterClient client = new OpenRouterClient("https://example.test/api", "key", "model", 5);
        JsonArray tools = AiActionTools.definitions(EnumSet.of(AiActionType.SAY), List.of());
        OpenRouterClient.ActionSession session = client.actionSession("rules", "state", tools, false);
        assertEquals("required", session.toolChoice());

        var assistant = JsonParser.parseString("""
                {"role":"assistant","tool_calls":[{"id":"call_1","type":"function",
                  "function":{"name":"say","arguments":"{}"}}]}
                """).getAsJsonObject();
        var turn = new OpenRouterClient.ActionTurn("", assistant, assistant.getAsJsonArray("tool_calls"), false, "");
        session.feedback(turn, List.of("Rejected: missing text."), "fix it", false);
        assertEquals("required", session.toolChoice());
        session.feedback(turn, List.of("Done."), "state", true);
        assertEquals("auto", session.toolChoice());
    }

    @Test
    void unknownReasoningEffortFallsBackToLow() {
        assertEquals("low", OpenRouterClient.normalizeEffort("turbo"));
        assertEquals("none", OpenRouterClient.normalizeEffort(" NONE "));
    }
}
