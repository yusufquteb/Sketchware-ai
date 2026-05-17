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

public class XaiGrokApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.x.ai/v1/models";
    private static final String CHAT_URL   = "https://api.x.ai/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public XaiGrokApiClient(String apiKey) {
        super(apiKey, AiProvider.XAI_GROK);
    }

    @Override public List<ModelInfo> fetchModels() throws IOException {
        Request req = new Request.Builder().url(MODELS_URL)
                .header("Authorization", "Bearer " + apiKey).build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) return fallback();
            ResponseBody body = r.body();
            if (body == null) return fallback();
            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray data = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();
            List<ModelInfo> res = new ArrayList<>();
            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                String id = el.getAsJsonObject().has("id")
                        ? el.getAsJsonObject().get("id").getAsString() : null;
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
                res.add(new ModelInfo(id, id, AiProvider.XAI_GROK, 131072, "xAI " + id));
            }
            return res.isEmpty() ? fallback() : res;
        }
    }

    @Override public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                          String systemPrompt, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, null, handler);
    }

    @Override public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                          String systemPrompt, Object tag, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, tag, handler);
    }

    @Override public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                          String systemPrompt, List<ToolDefinition> tools,
                                          StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, tools, null, handler);
    }

    @Override public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                          String systemPrompt, List<ToolDefinition> tools,
                                          Object tag, StreamingResponseHandler handler) {
        try {
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, modelId != null ? modelId : "grok-3-latest", systemPrompt, tools);
            Request.Builder builder = new Request.Builder().url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey);
            
            if (tag != null) builder.tag(tag);
            Request req = builder.build();

            client.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { handler.onError("xAI: " + e.getMessage()); }
                @Override public void onResponse(Call call, Response r) {
                    if (!r.isSuccessful()) { handler.onError("xAI HTTP " + r.code()); r.close(); return; }
                    ResponseBody rb = r.body();
                    if (rb == null) { handler.onError("Empty"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    r.close();
                }
            });
        } catch (Exception e) { handler.onError("xAI: " + e.getMessage()); }
    }

    private static List<ModelInfo> fallback() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("grok-3-latest",      "Grok-3",             AiProvider.XAI_GROK, 131072, "Most capable Grok"));
        l.add(new ModelInfo("grok-3-mini-latest", "Grok-3 Mini",        AiProvider.XAI_GROK, 131072, "Fast & efficient"));
        l.add(new ModelInfo("grok-2-1212",        "Grok-2",             AiProvider.XAI_GROK, 131072, "Previous generation"));
        return l;
    }
}
