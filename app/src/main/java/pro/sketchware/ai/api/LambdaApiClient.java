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
 * Lambda Labs API client — OpenAI-compatible endpoint.
 * Base URL  : https://api.lambdalabs.com/v1
 * API key   : https://cloud.lambdalabs.com/api-keys
 * Supports  : Llama 3.3 70B, Hermes 3 405B, Qwen 2.5, Mistral and more
 */
public class LambdaApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.lambdalabs.com/v1/models";
    private static final String CHAT_URL   = "https://api.lambdalabs.com/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    private static final java.util.Set<String> IGNORED = new java.util.HashSet<>(java.util.Arrays.asList(
            "lfm-40b-vllm", "hermes3-405b-fp8-128k"
    ));

    public LambdaApiClient(String apiKey) {
        super(apiKey, AiProvider.LAMBDA);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request req = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("Lambda HTTP " + r.code());
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
                if (lo.contains("embed") || lo.contains("rerank")) continue;
                long ctx = 0;
                if (obj.has("context_window") && !obj.get("context_window").isJsonNull()) {
                    try { ctx = obj.get("context_window").getAsLong(); } catch (Exception ignored) {}
                }
                result.add(new ModelInfo(id, toName(id), AiProvider.LAMBDA, ctx,
                        "Lambda Labs \u2014 " + toName(id)));
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
                    ? modelId : "llama-3.3-70b-instruct";
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
                    handler.onError("Lambda request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Lambda: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Lambda empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Lambda build error: " + e.getMessage());
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
        l.add(new ModelInfo("llama-3.3-70b-instruct",      "Llama 3.3 70B Instruct",    AiProvider.LAMBDA, 131072, "Lambda \u2014 Llama 3.3 70B"));
        l.add(new ModelInfo("llama-3.1-405b-instruct-fp8", "Llama 3.1 405B Instruct",   AiProvider.LAMBDA, 131072, "Lambda \u2014 Llama 3.1 405B"));
        l.add(new ModelInfo("llama3.1-8b-instruct",        "Llama 3.1 8B Instruct",     AiProvider.LAMBDA,  32768, "Lambda \u2014 Llama 3.1 8B"));
        l.add(new ModelInfo("hermes3-70b",                 "Hermes 3 70B",              AiProvider.LAMBDA, 131072, "Lambda \u2014 Hermes 3 70B"));
        l.add(new ModelInfo("qwen2.5-coder-32b-instruct",  "Qwen 2.5 Coder 32B",        AiProvider.LAMBDA,  32768, "Lambda \u2014 Qwen 2.5 Coder 32B"));
        return l;
    }
}
