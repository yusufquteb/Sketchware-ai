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

public class OpenAiApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.openai.com/v1/models";
    private static final String CHAT_URL   = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public OpenAiApiClient(String apiKey) {
        super(apiKey, AiProvider.OPENAI);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request request = new Request.Builder()
                .url(MODELS_URL)
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("OpenAI HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) return fallback();
            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray data = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();
            List<ModelInfo> result = new ArrayList<>();
            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : null;
                if (id == null) continue;
                // Skip deprecated/old models
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
                result.add(new ModelInfo(id, id, AiProvider.OPENAI, 128000, "OpenAI " + id));
            }
            return result.isEmpty() ? fallback() : result;
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
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, modelId != null ? modelId : "gpt-4o", systemPrompt, tools,
                    userTemperature, userMaxTokens);
            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey);
            
            if (tag != null) builder.tag(tag);
            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("OpenAI error: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = NvidiaApiClient.readBodySafely(response);
                        response.close();
                        handler.onError("OpenAI: " + NvidiaApiClient.getFriendlyErrorMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Empty response"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) { handler.onError("OpenAI: " + e.getMessage()); }
    }

    private static List<ModelInfo> fallback() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("gpt-4o",             "GPT-4o",             AiProvider.OPENAI, 128000, "Most capable multimodal"));
        l.add(new ModelInfo("gpt-4o-mini",        "GPT-4o Mini",        AiProvider.OPENAI, 128000, "Fast and affordable"));
        l.add(new ModelInfo("o1",                 "o1",                 AiProvider.OPENAI, 200000, "Advanced reasoning"));
        l.add(new ModelInfo("o1-mini",            "o1 Mini",            AiProvider.OPENAI, 128000, "Reasoning, fast"));
        l.add(new ModelInfo("o3-mini",            "o3 Mini",            AiProvider.OPENAI, 200000, "Latest reasoning model"));
        l.add(new ModelInfo("gpt-4-turbo",        "GPT-4 Turbo",        AiProvider.OPENAI, 128000, "Powerful + vision"));
        return l;
    }
}
