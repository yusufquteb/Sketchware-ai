package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;

/**
 * Groq API client — OpenAI-compatible endpoints.
 * Base URL: https://api.groq.com/openai/v1
 *
 * TOKEN LIMIT HANDLING:
 * Groq's free tier has per-minute and per-day token limits. This client:
 *  1. Uses llama-3.3-70b-versatile as the default model (128K context, good TPM limits).
 *  2. Removes compound-beta / compound-beta-mini from use — they exhaust TPM rapidly
 *     because each request fans out to multiple internal model calls.
 *  3. Caps max_tokens at 4096 to leave headroom for conversation history.
 *  4. Truncates conversation history to fit within model's context window.
 *
 * Budget per model (chars ≈ tokens × 4):
 *   8B / 9B models  : ~32 000 chars   (8K context)
 *   70B models       : ~200 000 chars  (128K context)
 *   default fallback : ~60 000 chars
 */
public class GroqApiClient extends AiApiClient {

    private static final String BASE       = "https://api.groq.com/openai/v1";
    private static final String MODELS_URL = BASE + "/models";
    private static final String CHAT_URL   = BASE + "/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_OUTPUT_TOKENS = 8192;

    public GroqApiClient(String apiKey) {
        super(apiKey, AiProvider.GROQ);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request request = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Groq HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) return fallbackModels();
            JsonElement root = JsonParser.parseString(body.string());
            List<ModelInfo> result = new ArrayList<>();
            JsonArray data = null;
            if (root.isJsonObject() && root.getAsJsonObject().has("data"))
                data = root.getAsJsonObject().getAsJsonArray("data");
            else if (root.isJsonArray()) data = root.getAsJsonArray();
            if (data == null) return fallbackModels();
            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id = str(obj, "id");
                if (id == null || id.isEmpty()) continue;
                String lo = id.toLowerCase(Locale.ROOT);
                if (lo.contains("whisper") || lo.contains("tts") || lo.contains("guard")
                        || lo.contains("audio") || lo.contains("speech") || lo.contains("embed")
                        || lo.contains("transcrib") || lo.contains("vision-only")) continue;
                long ctx = 0;
                if (obj.has("context_window") && !obj.get("context_window").isJsonNull()) {
                    try { ctx = obj.get("context_window").getAsLong(); } catch (Exception ignored) {}
                }
                result.add(new ModelInfo(id, toName(id), AiProvider.GROQ, ctx,
                        "Groq \u221e \u2014 " + toName(id)));
            }
            Collections.sort(result);
            return result.isEmpty() ? fallbackModels() : result;
        }
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, null, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, Object tag, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, tag, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, List<ToolDefinition> tools,
                                StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, tools, null, handler);
    }

    @Override
    public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                String systemPrompt, List<ToolDefinition> tools,
                                Object tag, StreamingResponseHandler handler) {
        try {
            // compound-beta models burn tokens fast via internal fan-out — never use them.
            // Decommissioned/invalid model IDs that may still be saved from a previous
            // version of this provider's model list — silently remap to a safe default
            // instead of letting Groq return a model_decommissioned / model_not_found error.
            String safeModel;
            if (modelId == null || modelId.isEmpty()
                    || modelId.startsWith("compound-beta")
                    || modelId.equals("gemma2-9b-it")
                    || modelId.equals("qwen-qwq-32b")
                    || modelId.equals("qwen3-32b")  // missing required "qwen/" prefix
                    || modelId.equals("deepseek-r1-distill-llama-70b")
                    || modelId.equals("mixtral-8x7b-32768")
                    || modelId.equals("llama-4-scout-17b-16e-preview")) {
                safeModel = "llama-3.3-70b-versatile";
            } else {
                safeModel = modelId;
            }
            String effectiveModel = safeModel;

            // Truncate messages to fit within this model's context window
            List<ChatMessage> truncated = truncateMessages(messages, effectiveModel, systemPrompt);

            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    truncated, effectiveModel, systemPrompt, tools,
                    userTemperature,
                    userMaxTokens > 0 ? userMaxTokens : MAX_OUTPUT_TOKENS);

            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            if (tag != null) builder.tag(tag);

            client.newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("Groq request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Groq: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Groq empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Groq build error: " + e.getMessage());
        }
    }

    /**
     * Trims conversation history so total chars (messages + system prompt) stay within budget.
     *
     * Algorithm:
     *  1. Reserve chars for system prompt + 500-char safety margin.
     *  2. Walk from newest to oldest, add messages that fit.
     *  3. If even the last message alone is too long, trim its content tail.
     *  4. Never return an empty list.
     */
    private static List<ChatMessage> truncateMessages(List<ChatMessage> messages,
                                                      String modelId, String systemPrompt) {
        if (messages == null || messages.isEmpty()) return messages;

        // Budget by model context window (chars ≈ tokens × 4)
        int totalBudget;
        String lo = modelId.toLowerCase(Locale.ROOT);
        if (lo.contains("70b") || lo.contains("70-b") || lo.contains("70b-versatile")
                || lo.contains("32b") || lo.contains("scout") || lo.contains("maverick")) {
            // 128K-131K context window models (llama-3.3-70b-versatile, qwen/qwen3-32b, ...)
            totalBudget = 200_000;
        } else if (lo.contains("8b") || lo.contains("9b") || lo.contains("7b")) {
            // 8K context window models — leave room for system prompt
            totalBudget = 28_000;
        } else {
            // Safe default for unknown models
            totalBudget = 60_000;
        }

        int systemLen = systemPrompt != null ? systemPrompt.length() : 0;
        int remaining = totalBudget - systemLen - 500;
        if (remaining <= 0)
            return messages.subList(messages.size() - 1, messages.size());

        ArrayDeque<ChatMessage> kept = new ArrayDeque<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            String content  = msg.getContent();
            int len = content != null ? content.length() : 0;
            if (remaining - len >= 0) {
                kept.addFirst(msg);
                remaining -= len;
            } else if (kept.isEmpty()) {
                // Last message alone is too large — trim from start, keep tail
                if (content != null && remaining > 0) {
                    msg.setContent(content.substring(content.length() - remaining));
                }
                kept.addFirst(msg);
                break;
            } else {
                break;
            }
        }
        return new ArrayList<>(kept);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
    private static String toName(String id) {
        String s = id.replace("-", " ").replace("_", " ");
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    private static List<ModelInfo> fallbackModels() {
        // Kept in sync with AiProviderModels.GROQ static list — see that file for
        // the authoritative reasoning behind each model choice / removal.
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", AiProvider.GROQ, 128000, "Groq \u221e \u2014 recommended, best for code & projects"));
        l.add(new ModelInfo("llama-3.1-8b-instant",    "Llama 3.1 8B Instant",    AiProvider.GROQ, 131072, "Groq \u221e \u2014 fastest, simple tasks"));
        l.add(new ModelInfo("qwen/qwen3-32b",          "Qwen3 32B",               AiProvider.GROQ, 131072, "Groq \u221e \u2014 best coding & reasoning"));
        l.add(new ModelInfo("compound-beta",           "Compound Beta",          AiProvider.GROQ, 128000, "Groq \u221e \u2014 agentic, built-in web search"));
        return l;
    }
}
