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
 * Novita AI API client — OpenAI-compatible endpoint.
 * Base URL  : https://api.novita.ai/v3/openai
 * Free tier : $0.50 free credit valid for 1 year on sign-up
 * API key   : https://novita.ai/settings/key-management
 * Supports  : Llama 4, DeepSeek R1/V3, Gemma 3, Qwen 2.5 and more
 */
public class NovitaApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.novita.ai/v3/openai/models";
    private static final String CHAT_URL   = "https://api.novita.ai/v3/openai/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public NovitaApiClient(String apiKey) {
        super(apiKey, AiProvider.NOVITA);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request req = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("Novita HTTP " + r.code());
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
                if (lo.contains("embed") || lo.contains("image") || lo.contains("flux")
                        || lo.contains("stable-diff") || lo.contains("sdxl")) continue;
                long ctx = 0;
                if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
                    try { ctx = obj.get("context_length").getAsLong(); } catch (Exception ignored) {}
                }
                String name = str(obj, "name");
                if (name == null || name.isEmpty()) name = toName(id);
                result.add(new ModelInfo(id, name, AiProvider.NOVITA, ctx,
                        "Novita \u2014 " + name));
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
                    ? modelId : "meta-llama/llama-4-scout-17b-16e-instruct";
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effective, systemPrompt, tools,
                    userTemperature > 0f ? userTemperature : 0.7f,
                    userMaxTokens > 0 ? userMaxTokens : 4096);
            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            if (tag != null) builder.tag(tag);
            client.newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("Novita request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Novita: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Novita empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Novita build error: " + e.getMessage());
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
        l.add(new ModelInfo("meta-llama/llama-4-scout-17b-16e-instruct", "Llama 4 Scout 17B Instruct",  AiProvider.NOVITA, 131072, "Novita \u2014 Llama 4 Scout"));
        l.add(new ModelInfo("meta-llama/llama-4-maverick-17b-128e-instruct","Llama 4 Maverick Instruct", AiProvider.NOVITA, 131072, "Novita \u2014 Llama 4 Maverick"));
        l.add(new ModelInfo("deepseek/deepseek-r1",                       "DeepSeek R1",                AiProvider.NOVITA,  32768, "Novita \u2014 DeepSeek R1 reasoning"));
        l.add(new ModelInfo("deepseek/deepseek-v3-0324",                  "DeepSeek V3 0324",           AiProvider.NOVITA,  32768, "Novita \u2014 DeepSeek V3 0324"));
        l.add(new ModelInfo("google/gemma-3-27b-it",                      "Gemma 3 27B Instruct",       AiProvider.NOVITA, 131072, "Novita \u2014 Gemma 3 27B"));
        l.add(new ModelInfo("qwen/qwen2.5-72b-instruct",                  "Qwen 2.5 72B Instruct",      AiProvider.NOVITA, 131072, "Novita \u2014 Qwen 2.5 72B"));
        return l;
    }
}
