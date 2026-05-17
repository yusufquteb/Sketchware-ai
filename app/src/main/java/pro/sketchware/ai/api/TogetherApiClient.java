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
 * AI API client for Together AI (OpenAI-compatible API).
 *
 * <p>Together AI provides access to open-source models including Llama, Gemma,
 * DeepSeek, Qwen and more. Free tier available with API key from together.ai.
 */
public class TogetherApiClient extends AiApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public TogetherApiClient(String apiKey) {
        super(apiKey, AiProvider.TOGETHER);
    }

    // ── Model listing ────────────────────────────────────────────────────────

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        String url = AiProvider.TOGETHER.getBaseUrl() + AiProvider.TOGETHER.getModelsEndpoint();
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
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    String id = getStr(obj, "id", null);
                    if (id == null) continue;
                    // Filter: only chat/language models useful for coding
                    String type = getStr(obj, "type", "");
                    if (!type.isEmpty() && !type.contains("chat") && !type.contains("language")) continue;
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
                    String displayName = getStr(obj, "display_name", id);
                    long ctx = 0;
                    if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
                        try { ctx = obj.get("context_length").getAsLong(); } catch (Exception ignored) {}
                    }
                    result.add(new ModelInfo(id, displayName, AiProvider.TOGETHER, ctx, "Together AI"));
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
                    ? modelId : "meta-llama/Llama-3.3-70B-Instruct-Turbo";

            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effectiveModel, systemPrompt, tools);

            String chatUrl = AiProvider.TOGETHER.getBaseUrl() + AiProvider.TOGETHER.getChatEndpoint();
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
                    handler.onError("Together AI request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Together AI: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) {
                        handler.onError("Together AI returned empty body");
                        return;
                    }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Together AI build error: " + e.getMessage());
        }
    }

    // ── Fallback models ──────────────────────────────────────────────────────

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> list = new ArrayList<>();
        list.add(new ModelInfo("meta-llama/Llama-3.3-70B-Instruct-Turbo",
                "Llama 3.3 70B Instruct Turbo", AiProvider.TOGETHER, 131072,
                "Together AI — Fast Llama 3.3 70B"));
        list.add(new ModelInfo("google/gemma-3-27b-it",
                "Gemma 3 27B IT", AiProvider.TOGETHER, 131072,
                "Together AI — Google Gemma 3 27B"));
        list.add(new ModelInfo("google/gemma-3-12b-it",
                "Gemma 3 12B IT", AiProvider.TOGETHER, 131072,
                "Together AI — Google Gemma 3 12B"));
        list.add(new ModelInfo("deepseek-ai/DeepSeek-R1",
                "DeepSeek R1", AiProvider.TOGETHER, 131072,
                "Together AI — DeepSeek R1 Reasoning"));
        list.add(new ModelInfo("Qwen/Qwen2.5-72B-Instruct-Turbo",
                "Qwen 2.5 72B Instruct Turbo", AiProvider.TOGETHER, 32768,
                "Together AI — Qwen 2.5 72B"));
        return list;
    }

    private static String getStr(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }
}
