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
 * Hyperbolic AI API client — OpenAI-compatible endpoint.
 * Base URL  : https://api.hyperbolic.xyz/v1
 * Free tier : $1 credit on sign-up
 * API key   : https://app.hyperbolic.ai/
 * Supports  : Llama 4, DeepSeek R1/V3, Qwen 2.5, Mistral and more
 */
public class HyperbolicApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.hyperbolic.xyz/v1/models";
    private static final String CHAT_URL   = "https://api.hyperbolic.xyz/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    // Models to skip (image gen, TTS, etc.)
    private static final java.util.Set<String> IGNORED = new java.util.HashSet<>(java.util.Arrays.asList(
            "Wifhat", "FLUX.1-dev", "StableDiffusion", "Monad", "TTS",
            "deepseek-ai/Janus-Pro-7B", "test", "SDXL1.0-base",
            "deepseek-ai/DeepSeek-R1", "deepseek-ai/DeepSeek-R1-Zero"
    ));

    public HyperbolicApiClient(String apiKey) {
        super(apiKey, AiProvider.HYPERBOLIC);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request req = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("Hyperbolic HTTP " + r.code());
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
                if (IGNORED.contains(id)) continue;
                String lo = id.toLowerCase(Locale.ROOT);
                if (lo.contains("embed") || lo.contains("image") || lo.contains("flux")
                        || lo.contains("stable-diff") || lo.contains("sdxl")) continue;
                long ctx = 0;
                if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
                    try { ctx = obj.get("context_length").getAsLong(); } catch (Exception ignored) {}
                }
                result.add(new ModelInfo(id, toName(id), AiProvider.HYPERBOLIC, ctx,
                        "Hyperbolic \u2014 " + toName(id)));
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
                    ? modelId : "meta-llama/Llama-4-Scout-17B-16E-Instruct";
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
                    handler.onError("Hyperbolic request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Hyperbolic: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Hyperbolic empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Hyperbolic build error: " + e.getMessage());
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static String toName(String id) {
        // e.g. "meta-llama/Llama-3.3-70B-Instruct" -> "Llama 3.3 70B Instruct"
        String s = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
        s = s.replace("-", " ").replace("_", " ");
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("meta-llama/Llama-4-Scout-17B-16E-Instruct", "Llama 4 Scout 17B Instruct",  AiProvider.HYPERBOLIC, 131072, "Hyperbolic \u2014 Llama 4 Scout"));
        l.add(new ModelInfo("meta-llama/Llama-3.3-70B-Instruct",         "Llama 3.3 70B Instruct",      AiProvider.HYPERBOLIC, 131072, "Hyperbolic \u2014 Llama 3.3 70B"));
        l.add(new ModelInfo("deepseek-ai/DeepSeek-V3",                   "DeepSeek V3",                 AiProvider.HYPERBOLIC,  32768, "Hyperbolic \u2014 DeepSeek V3"));
        l.add(new ModelInfo("Qwen/Qwen2.5-72B-Instruct",                 "Qwen 2.5 72B Instruct",       AiProvider.HYPERBOLIC, 131072, "Hyperbolic \u2014 Qwen 2.5 72B"));
        return l;
    }
}
