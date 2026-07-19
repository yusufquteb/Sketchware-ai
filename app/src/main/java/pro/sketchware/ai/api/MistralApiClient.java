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
 * Mistral La Plateforme API client — OpenAI-compatible endpoint.
 * Base URL  : https://api.mistral.ai/v1
 * Free plan : Experiment plan (data training opt-in, phone verification required)
 * Limits    : 1 req/sec, 500,000 tokens/min, 1,000,000,000 tokens/month per model
 * API key   : https://console.mistral.ai/
 */
public class MistralApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.mistral.ai/v1/models";
    private static final String CHAT_URL   = "https://api.mistral.ai/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public MistralApiClient(String apiKey) {
        super(apiKey, AiProvider.MISTRAL);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request req = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("Mistral HTTP " + r.code());
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
                // skip embed, moderation, non-chat
                if (lo.contains("embed") || lo.contains("moderat")) continue;
                long ctx = 0;
                if (obj.has("max_context_length") && !obj.get("max_context_length").isJsonNull()) {
                    try { ctx = obj.get("max_context_length").getAsLong(); } catch (Exception ignored) {}
                }
                String name = str(obj, "name");
                if (name == null || name.isEmpty()) name = toName(id);
                result.add(new ModelInfo(id, name, AiProvider.MISTRAL, ctx,
                        "Mistral \u2014 " + name));
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
                    ? modelId : "mistral-small-latest";
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effective, systemPrompt, tools,
                    userTemperature > 0f ? userTemperature : 0.7f,
                    userMaxTokens > 0 ? userMaxTokens : 8192);
            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            if (tag != null) builder.tag(tag);
            client.newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("Mistral request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Mistral: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Mistral empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Mistral build error: " + e.getMessage());
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
        l.add(new ModelInfo("mistral-large-latest",       "Mistral Large",            AiProvider.MISTRAL, 131072, "Mistral \u2014 flagship model for complex tasks"));
        l.add(new ModelInfo("mistral-small-latest",       "Mistral Small",            AiProvider.MISTRAL, 131072, "Mistral \u2014 efficient model for everyday tasks"));
        l.add(new ModelInfo("mistral-nemo",               "Mistral Nemo",             AiProvider.MISTRAL, 131072, "Mistral \u2014 best-in-class 12B model"));
        l.add(new ModelInfo("codestral-latest",           "Codestral",                AiProvider.MISTRAL, 256000, "Mistral \u2014 state-of-the-art code model"));
        l.add(new ModelInfo("open-mistral-7b",            "Mistral 7B",               AiProvider.MISTRAL,  32768, "Mistral \u2014 fast open model"));
        l.add(new ModelInfo("open-mixtral-8x7b",          "Mixtral 8x7B",             AiProvider.MISTRAL,  32768, "Mistral \u2014 mixture-of-experts model"));
        l.add(new ModelInfo("open-mixtral-8x22b",         "Mixtral 8x22B",            AiProvider.MISTRAL,  65536, "Mistral \u2014 largest open MoE model"));
        return l;
    }
}
