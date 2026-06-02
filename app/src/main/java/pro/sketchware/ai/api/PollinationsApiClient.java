package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
 * Pollinations AI API client — OpenAI-compatible endpoint.
 *
 * Base URL   : https://text.pollinations.ai
 * Models URL : https://text.pollinations.ai/models
 * Chat URL   : https://text.pollinations.ai/openai  (OpenAI-compatible)
 * Free tier  : ~1 req/min anonymous, higher limits with free key from enter.pollinations.ai
 * API key    : optional
 */
public class PollinationsApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://text.pollinations.ai/models";
    private static final String CHAT_URL   = "https://text.pollinations.ai/openai";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public PollinationsApiClient(String apiKey) {
        super(apiKey, AiProvider.POLLINATIONS);
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
            if (!r.isSuccessful()) throw new IOException("Pollinations HTTP " + r.code());
            ResponseBody body = r.body();
            if (body == null) return fallbackModels();
            String raw = body.string();
            JsonElement root = JsonParser.parseString(raw);
            List<ModelInfo> result = new ArrayList<>();

            if (root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                for (JsonElement el : arr) {
                    if (el.isJsonPrimitive()) {
                        // Simple string array: ["openai", "mistral", ...]
                        String id = el.getAsString();
                        if (id == null || id.isEmpty()) continue;
                        result.add(new ModelInfo(id, toName(id), AiProvider.POLLINATIONS, 0,
                                "Pollinations AI — " + toName(id) + " (free, no key)"));
                    } else if (el.isJsonObject()) {
                        // Object array: [{name: "openai", type: "chat", ...}, ...]
                        JsonObject obj = el.getAsJsonObject();
                        String id = str(obj, "name");
                        if (id == null || id.isEmpty()) id = str(obj, "id");
                        if (id == null || id.isEmpty()) continue;
                        String type = str(obj, "type");
                        if (type != null && !type.equals("chat") && !type.equals("text")) continue;
                        result.add(new ModelInfo(id, toName(id), AiProvider.POLLINATIONS, 0,
                                "Pollinations AI — " + toName(id) + " (free, no key)"));
                    }
                }
            } else if (root.isJsonObject() && root.getAsJsonObject().has("data")) {
                // Standard OpenAI format
                JsonArray data = root.getAsJsonObject().getAsJsonArray("data");
                for (JsonElement el : data) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    String id = str(obj, "id");
                    if (id == null || id.isEmpty()) continue;
                    result.add(new ModelInfo(id, toName(id), AiProvider.POLLINATIONS, 0,
                            "Pollinations AI — " + toName(id) + " (free, no key)"));
                }
            }
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
                    ? modelId : "openai";
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
                    handler.onError("Pollinations AI request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Pollinations AI: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Pollinations AI: empty response"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Pollinations AI error: " + e.getMessage());
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static String toName(String id) {
        String s = id.replace("-", " ").replace("_", " ");
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("openai",      "OpenAI (GPT-4o)",    AiProvider.POLLINATIONS, 128000, "Pollinations AI — GPT-4o proxy (free)"));
        l.add(new ModelInfo("mistral",     "Mistral",            AiProvider.POLLINATIONS, 128000, "Pollinations AI — Mistral Large (free)"));
        l.add(new ModelInfo("qwen-coder",  "Qwen Coder",         AiProvider.POLLINATIONS, 65536,  "Pollinations AI — Qwen2.5-Coder (free)"));
        l.add(new ModelInfo("claude",      "Claude",             AiProvider.POLLINATIONS, 200000, "Pollinations AI — Claude proxy (free)"));
        l.add(new ModelInfo("searchgpt",   "SearchGPT",          AiProvider.POLLINATIONS, 128000, "Pollinations AI — GPT-4o + web search (free)"));
        return l;
    }
}
