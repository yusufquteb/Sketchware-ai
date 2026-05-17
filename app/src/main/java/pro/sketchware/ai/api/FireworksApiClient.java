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
 * Fireworks AI API client — OpenAI-compatible endpoint.
 * Base URL  : https://api.fireworks.ai/inference/v1
 * Free tier : $1 credit on sign-up
 * API key   : https://fireworks.ai/account/api-keys
 * Supports  : Llama 4, DeepSeek R1/V3, Qwen 2.5, Mixtral and more
 */
public class FireworksApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.fireworks.ai/inference/v1/models";
    private static final String CHAT_URL   = "https://api.fireworks.ai/inference/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public FireworksApiClient(String apiKey) {
        super(apiKey, AiProvider.FIREWORKS);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request req = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("Fireworks HTTP " + r.code());
            ResponseBody body = r.body();
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
                if (lo.contains("embed") || lo.contains("image") || lo.contains("whisper")
                        || lo.contains("tts") || lo.contains("speech-to-text")) continue;
                long ctx = 0;
                if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
                    try { ctx = obj.get("context_length").getAsLong(); } catch (Exception ignored) {}
                }
                String name = str(obj, "name");
                if (name == null || name.isEmpty()) name = toName(id);
                result.add(new ModelInfo(id, name, AiProvider.FIREWORKS, ctx,
                        "Fireworks \u2014 " + name));
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
                    ? modelId : "accounts/fireworks/models/llama4-scout-instruct-basic";
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effective, systemPrompt, tools, 0.7f, 4096);
            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            if (tag != null) builder.tag(tag);
            client.newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("Fireworks request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Fireworks: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Fireworks empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Fireworks build error: " + e.getMessage());
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
        l.add(new ModelInfo("accounts/fireworks/models/llama4-scout-instruct-basic",   "Llama 4 Scout Instruct",  AiProvider.FIREWORKS, 131072, "Fireworks \u2014 Llama 4 Scout"));
        l.add(new ModelInfo("accounts/fireworks/models/llama4-maverick-instruct-basic","Llama 4 Maverick Instruct",AiProvider.FIREWORKS, 131072, "Fireworks \u2014 Llama 4 Maverick"));
        l.add(new ModelInfo("accounts/fireworks/models/deepseek-r1",                   "DeepSeek R1",             AiProvider.FIREWORKS,  32768, "Fireworks \u2014 DeepSeek R1 reasoning"));
        l.add(new ModelInfo("accounts/fireworks/models/deepseek-v3",                   "DeepSeek V3",             AiProvider.FIREWORKS,  32768, "Fireworks \u2014 DeepSeek V3"));
        l.add(new ModelInfo("accounts/fireworks/models/qwen2p5-72b-instruct",          "Qwen 2.5 72B Instruct",   AiProvider.FIREWORKS, 131072, "Fireworks \u2014 Qwen 2.5 72B"));
        l.add(new ModelInfo("accounts/fireworks/models/mixtral-8x7b-instruct",         "Mixtral 8x7B Instruct",   AiProvider.FIREWORKS,  32768, "Fireworks \u2014 Mixtral 8x7B"));
        return l;
    }
}
