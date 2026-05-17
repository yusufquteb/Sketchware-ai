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
 * GitHub Models API client — Azure OpenAI-compatible endpoint.
 * Base URL  : https://models.inference.ai.azure.com/v1
 * Auth      : GitHub Personal Access Token (PAT)
 * Free tier : Rate limits depend on Copilot subscription tier
 * API key   : https://github.com/settings/tokens
 * Supports  : GPT-4o, o3, Llama 4, Mistral, Phi, Cohere, Jamba and more
 */
public class GitHubModelsApiClient extends AiApiClient {

    private static final String BASE_URL   = "https://models.inference.ai.azure.com";
    private static final String MODELS_URL = BASE_URL + "/v1/models";
    private static final String CHAT_URL   = BASE_URL + "/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public GitHubModelsApiClient(String apiKey) {
        super(apiKey, AiProvider.GITHUB_MODELS);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request req = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IOException("GitHub Models HTTP " + r.code());
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
                if (lo.contains("embed") || lo.contains("image") || lo.contains("dall-e")
                        || lo.contains("whisper") || lo.contains("tts")) continue;
                String name = str(obj, "name");
                if (name == null || name.isEmpty()) name = str(obj, "friendly_name");
                if (name == null || name.isEmpty()) name = toName(id);
                long ctx = 0;
                if (obj.has("context_window") && !obj.get("context_window").isJsonNull()) {
                    try { ctx = obj.get("context_window").getAsLong(); } catch (Exception ignored) {}
                }
                result.add(new ModelInfo(id, name, AiProvider.GITHUB_MODELS, ctx,
                        "GitHub Models \u2014 " + name));
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
                    ? modelId : "Meta-Llama-4-Scout-17B-16E-Instruct";
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
                    handler.onError("GitHub Models request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("GitHub Models: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("GitHub Models empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("GitHub Models build error: " + e.getMessage());
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
        l.add(new ModelInfo("Meta-Llama-4-Scout-17B-16E-Instruct", "Llama 4 Scout 17B Instruct",  AiProvider.GITHUB_MODELS, 10000,  "GitHub Models \u2014 Llama 4 Scout"));
        l.add(new ModelInfo("Meta-Llama-3.3-70B-Instruct",         "Llama 3.3 70B Instruct",      AiProvider.GITHUB_MODELS, 128000, "GitHub Models \u2014 Llama 3.3 70B"));
        l.add(new ModelInfo("gpt-4o",                               "GPT-4o",                      AiProvider.GITHUB_MODELS, 128000, "GitHub Models \u2014 GPT-4o"));
        l.add(new ModelInfo("gpt-4o-mini",                          "GPT-4o Mini",                 AiProvider.GITHUB_MODELS, 128000, "GitHub Models \u2014 GPT-4o Mini"));
        l.add(new ModelInfo("Mistral-small",                        "Mistral Small",               AiProvider.GITHUB_MODELS, 128000, "GitHub Models \u2014 Mistral Small"));
        l.add(new ModelInfo("Phi-3.5-mini-instruct",                "Phi-3.5 Mini Instruct",       AiProvider.GITHUB_MODELS,  128000, "GitHub Models \u2014 Phi-3.5 Mini"));
        return l;
    }
}
