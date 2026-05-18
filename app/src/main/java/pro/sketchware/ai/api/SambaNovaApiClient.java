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
 * SambaNova Cloud API client — OpenAI-compatible endpoint.
 * Base URL : https://api.sambanova.ai/v1
 * Free key : cloud.sambanova.ai
 * Supports : Gemma 3 (1B/4B/12B/27B), Gemma 2 9B/27B, Llama 4, DeepSeek R1 0528, Qwen3 72B
 */
public class SambaNovaApiClient extends AiApiClient {

    private static final String CHAT_URL = "https://api.sambanova.ai/v1/chat/completions";
    private static final String MODELS_URL = "https://api.sambanova.ai/v1/models";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public SambaNovaApiClient(String apiKey) {
        super(apiKey, AiProvider.SAMBANOVA);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request req = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("SambaNova HTTP " + r.code());
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
                if (lo.contains("whisper") || lo.contains("tts") || lo.contains("embed")
                        || lo.contains("audio") || lo.contains("rerank")) continue;
                long ctx = 0;
                if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
                    try { ctx = obj.get("context_length").getAsLong(); } catch (Exception ignored) {}
                }
                result.add(new ModelInfo(id, toName(id), AiProvider.SAMBANOVA, ctx,
                        "SambaNova \u2014 " + toName(id)));
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
                    ? modelId : "Gemma-3-27B-IT";
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effective, systemPrompt, tools, 0f, 4096);
            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            if (tag != null) builder.tag(tag);
            client.newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("SambaNova request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("SambaNova: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("SambaNova empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("SambaNova build error: " + e.getMessage());
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
        l.add(new ModelInfo("Gemma-3-27B-IT",              "Gemma 3 27B Instruct",      AiProvider.SAMBANOVA, 8192,   "SambaNova \u2014 Gemma 3 27B \u2014 best Gemma for coding"));
        l.add(new ModelInfo("Gemma-3-12B-IT",              "Gemma 3 12B Instruct",      AiProvider.SAMBANOVA, 8192,   "SambaNova \u2014 Gemma 3 12B"));
        l.add(new ModelInfo("Gemma-3-4B-IT",               "Gemma 3 4B Instruct",       AiProvider.SAMBANOVA, 8192,   "SambaNova \u2014 Gemma 3 4B \u2014 fast"));
        l.add(new ModelInfo("Gemma2-27B-IT",               "Gemma 2 27B Instruct",      AiProvider.SAMBANOVA, 8192,   "SambaNova \u2014 Gemma 2 27B"));
        l.add(new ModelInfo("Meta-Llama-3.3-70B-Instruct", "Llama 3.3 70B Instruct",    AiProvider.SAMBANOVA, 131072, "SambaNova \u2014 Llama 3.3 70B"));
        l.add(new ModelInfo("DeepSeek-R1-0528",            "DeepSeek R1 0528",          AiProvider.SAMBANOVA, 32768,  "SambaNova \u2014 DeepSeek R1 reasoning"));
        l.add(new ModelInfo("Qwen3-72B",                   "Qwen 3 72B",               AiProvider.SAMBANOVA, 32768,  "SambaNova \u2014 Qwen 3 72B coding"));
        return l;
    }
}
