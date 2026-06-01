package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
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
 * LLM7.io API client — OpenAI-compatible endpoint.
 *
 * Base URL  : https://api.llm7.io/v1
 * Free tier : 30 req/min anonymous (no key), 120 RPM with free token from token.llm7.io
 * API key   : optional — get a free token at token.llm7.io for higher rate limits
 * Supports  : GPT-4o-mini, DeepSeek R1, Qwen2.5-Coder, Gemini Flash Lite, and more
 */
public class Llm7ApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.llm7.io/v1/models";
    private static final String CHAT_URL   = "https://api.llm7.io/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public Llm7ApiClient(String apiKey) {
        super(apiKey, AiProvider.LLM7);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try (Response r = client.newCall(builder.build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("LLM7 HTTP " + r.code());
            ResponseBody body = r.body();
            if (body == null) return fallbackModels();
            JsonElement root = JsonParser.parseString(body.string());
            List<ModelInfo> result = new ArrayList<>();
            JsonArray data = null;
            if (root.isJsonObject() && root.getAsJsonObject().has("data"))
                data = root.getAsJsonObject().getAsJsonArray("data");
            else if (root.isJsonArray())
                data = root.getAsJsonArray();
            if (data == null) return fallbackModels();
            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id = str(obj, "id");
                if (id == null || id.isEmpty()) continue;
                String lo = id.toLowerCase(Locale.ROOT);
                if (lo.contains("embed") || lo.contains("image") || lo.contains("tts")
                        || lo.contains("whisper") || lo.contains("speech")
                        || lo.contains("moderation")) continue;
                long ctx = 0;
                if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
                    try { ctx = obj.get("context_length").getAsLong(); } catch (Exception ignored) {}
                }
                String name = str(obj, "name");
                if (name == null || name.isEmpty()) name = toName(id);
                result.add(new ModelInfo(id, name, AiProvider.LLM7, ctx,
                        "LLM7.io — " + name + " (free, no key)"));
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
            String effective = (modelId != null && !modelId.isEmpty())
                    ? modelId : "qwen2.5-coder-32b-instruct";
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effective, systemPrompt, tools, 0.7f, 8192);
            Request.Builder reqBuilder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }
            if (tag != null) reqBuilder.tag(tag);
            client.newCall(reqBuilder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("LLM7 request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("LLM7: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("LLM7: empty response"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("LLM7 error: " + e.getMessage());
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static String toName(String id) {
        String s = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
        s = s.replace("-", " ").replace("_", " ");
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("qwen2.5-coder-32b-instruct", "Qwen2.5 Coder 32B",   AiProvider.LLM7, 32768,  "LLM7.io — Coding specialist (free)"));
        l.add(new ModelInfo("deepseek-r1-0528",            "DeepSeek R1 (0528)",  AiProvider.LLM7, 65536,  "LLM7.io — Latest DeepSeek R1 (free)"));
        l.add(new ModelInfo("gpt-4o-mini-2024-07-18",      "GPT-4o Mini",         AiProvider.LLM7, 128000, "LLM7.io — GPT-4o Mini via proxy (free)"));
        l.add(new ModelInfo("gemini-2.5-flash-lite",       "Gemini 2.5 Flash Lite", AiProvider.LLM7, 1000000, "LLM7.io — Gemini Flash Lite (free)"));
        l.add(new ModelInfo("gpt-o4-mini-2025-04-16",      "o4-mini",             AiProvider.LLM7, 128000, "LLM7.io — o4-mini reasoning (free)"));
        l.add(new ModelInfo("mistral-small-3.1-24b-instruct-2503", "Mistral Small 3.1", AiProvider.LLM7, 128000, "LLM7.io — Mistral Small (free)"));
        l.add(new ModelInfo("gpt-4.1-nano-2025-04-14",     "GPT-4.1 Nano",        AiProvider.LLM7, 128000, "LLM7.io — Nano model (free)"));
        return l;
    }
}
