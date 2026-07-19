package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
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
 * AI API client for Cerebras AI (OpenAI-compatible API).
 *
 * <p>Cerebras provides ultra-fast inference on their custom hardware.
 * Free tier available with API key from cloud.cerebras.ai.
 * Supports Llama 3.3 70B, Llama 3.1 70B, and Llama 3.1 8B.
 */
public class CerebrasApiClient extends AiApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public CerebrasApiClient(String apiKey) {
        super(apiKey, AiProvider.CEREBRAS);
    }

    // ── Model listing ────────────────────────────────────────────────────────

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        String url = AiProvider.CEREBRAS.getBaseUrl() + AiProvider.CEREBRAS.getModelsEndpoint();
        Request request = addBearerAuth(new Request.Builder())
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return fallbackModels();
            }
            ResponseBody body = response.body();
            if (body == null) return fallbackModels();

            String json = body.string();
            List<ModelInfo> result = new ArrayList<>();
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray data = root.has("data") ? root.getAsJsonArray("data") : null;
                if (data == null) return fallbackModels();

                for (int i = 0; i < data.size(); i++) {
                    JsonObject obj = data.get(i).getAsJsonObject();
                    String id = getStr(obj, "id", null);
                    if (id == null) continue;
                // Skip image/audio/embedding/non-chat models
                    {
                        String _lo = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
                        if (_lo.contains("whisper") || _lo.contains("tts") || _lo.contains("guard")
                        || _lo.contains("audio") || _lo.contains("speech") || _lo.contains("embed")
                        || _lo.contains("moderation") || _lo.contains("realtime")
                        || _lo.contains("dall-e") || _lo.contains("stable-diff")
                        || _lo.contains("sdxl") || _lo.contains("flux") || _lo.contains("imagen")
                        || _lo.contains("image-gen") || _lo.contains("text-to-image")
                        || _lo.contains("video") || _lo.contains("rerank")
                        || _lo.contains("transcrib") || _lo.contains("midjourney")) continue;
                    }
                    long ctx = 0;
                    if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
                        try { ctx = obj.get("context_length").getAsLong(); } catch (Exception ignored) {}
                    }
                    String name = toDisplayName(id);
                    result.add(new ModelInfo(id, name, AiProvider.CEREBRAS, ctx,
                            "Cerebras — Ultra-fast inference"));
                }
            } catch (Exception e) {
                return fallbackModels();
            }
            java.util.Collections.sort(result);
            return result.isEmpty() ? fallbackModels() : result;
        }
    }

    // ── Chat requests ────────────────────────────────────────────────────────

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
            String effectiveModel = (modelId != null && !modelId.isEmpty())
                    ? modelId : "llama-3.3-70b";

            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effectiveModel, systemPrompt, tools, userTemperature, userMaxTokens);

            String chatUrl = AiProvider.CEREBRAS.getBaseUrl() + AiProvider.CEREBRAS.getChatEndpoint();
            Request.Builder builder = new Request.Builder()
                    .url(chatUrl)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");

            if (tag != null) builder.tag(tag);
            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("Cerebras request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Cerebras: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) {
                        handler.onError("Cerebras returned empty body");
                        return;
                    }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Cerebras build error: " + e.getMessage());
        }
    }

    // ── Fallback models ──────────────────────────────────────────────────────

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> list = new ArrayList<>();
        list.add(new ModelInfo("llama-3.3-70b",
                "Llama 3.3 70B (Cerebras)", AiProvider.CEREBRAS, 131072,
                "Cerebras — Ultra-fast Llama 3.3 70B"));
        list.add(new ModelInfo("llama-3.1-70b",
                "Llama 3.1 70B (Cerebras)", AiProvider.CEREBRAS, 131072,
                "Cerebras — Ultra-fast Llama 3.1 70B"));
        list.add(new ModelInfo("llama3.1-8b",
                "Llama 3.1 8B (Cerebras)", AiProvider.CEREBRAS, 131072,
                "Cerebras — Ultra-fast Llama 3.1 8B"));
        return list;
    }

    private static String toDisplayName(String id) {
        if (id == null) return "Unknown";
        String s = id.replace("-", " ").replace("_", " ").replace(".", " ");
        if (s.isEmpty()) return id;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1) + " (Cerebras)";
    }

    private static String getStr(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }
}
