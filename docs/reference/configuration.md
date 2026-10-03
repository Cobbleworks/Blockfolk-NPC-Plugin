# config.yml

Global settings are created at `plugins/Blockfolk/config.yml`. Restart the server after changing provider settings.

## General settings

| Key | Default | Description |
| --- | ---: | --- |
| `chat-input-timeout-seconds` | `60` | Time allowed to answer a Blockfolk admin chat prompt. |
| `question-timeout-seconds` | `30` | Time allowed to answer an NPC question before its cancel/timeout branch runs. |
| `proximity-transition-cooldown-seconds` | `3` | Debounces rapid approach/leave transitions while an NPC starts or stops moving. |
| `mineskin-api-key` | empty | Optional key for higher MineSkin limits when resolving external skin image URLs. |

## OpenRouter settings

| Key | Default | Description |
| --- | --- | --- |
| `openrouter.endpoint` | `https://openrouter.ai/api/v1/chat/completions` | Chat-completions endpoint. HTTPS is required. |
| `openrouter.api-key` | empty | OpenRouter API key. Leave empty to keep AI unavailable. |
| `openrouter.model` | `deepseek/deepseek-v4-flash-0731` | OpenRouter model identifier. |
| `openrouter.timeout-seconds` | `17` | Network request timeout. Increase it for slower providers. |
| `openrouter.max-tokens` | `1600` | Maximum output allowance per request, including any reasoning tokens. |
| `openrouter.reasoning-effort` | `low` | How much the model may think before acting: `none`, `minimal`, `low`, `medium`, or `high`. Some reasoning makes NPCs follow through on requested actions more reliably; `none` gives the lowest latency. Models without reasoning support ignore it. |

Gameplay requests use native function calls with temperature `0.3`; memory review requests JSON output.

## AI control

| Key | Default | Description |
| --- | ---: | --- |
| `ai-control.invocation-cooldown-seconds` | `2` | Per-NPC protection in addition to event-specific trigger throttling. |
| `ai-control.conversation-history-limit` | `20` | Recent conversation lines retained per NPC conversation. `0` disables history. |

NPC-specific context, memory, inventory access, chat response, and allowed actions are configured through each preset's [AI Behaviour menu](/features/ai-behaviour), not in this file.

## Complete default file

```yaml
chat-input-timeout-seconds: 60
question-timeout-seconds: 30
proximity-transition-cooldown-seconds: 3
mineskin-api-key: ""

openrouter:
  endpoint: "https://openrouter.ai/api/v1/chat/completions"
  api-key: ""
  model: "deepseek/deepseek-v4-flash-0731"
  timeout-seconds: 17
  max-tokens: 1600
  reasoning-effort: "low"

ai-control:
  invocation-cooldown-seconds: 2
  conversation-history-limit: 20
```
