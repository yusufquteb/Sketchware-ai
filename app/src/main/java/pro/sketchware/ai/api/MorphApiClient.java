package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
 * Morph LLM API client — OpenAI-compatible endpoint.
 * Base URL : https://api.morphllm.com/v1
 * API key  : https://www.morphllm.com/dashboard/api-keys
 * Best for : precise code editing and XML layout refinement
 */
public class MorphApiClient extends AiApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public MorphApiClient(String apiKey) {
        super(apiKey, AiProvider.MORPH);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request req = new Request.Builder()
                .url(getModelsUrl()).get()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) return fallbackModels();
            ResponseBody body = r.body();
            if (body == null) return fallbackModels();
            JsonElement root = JsonParser.parseString(body.string());
            List<ModelInfo> result = new ArrayList<>();
            JsonArray data = null;
            if (root.isJsonObject() && root.getAsJsonObject().has("data"))
                data = root.getAsJsonObject().getAsJsonArray("data");
            else if (root.isJsonArray()) data = root.getAsJsonArray();
            if (data != null) {
                for (JsonElement el : data) {
                    if (!el.isJsonObject()) continue;
                    String id = el.getAsJsonObject().has("id")
                            ? el.getAsJsonObject().get("id").getAsString() : null;
                    if (id != null && !id.isEmpty())
                        result.add(new ModelInfo(id, id, AiProvider.MORPH, 0, "Morph — " + id));
                }
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
            String effective = (modelId != null && !modelId.isEmpty()) ? modelId : "morph-v3-fast";
            // NOTE: Morph is used for precise code/XML edits, not general conversation — its low
            // 0.2f default is intentional. We still honor an explicit user override (Deep/Quick
            // profile or manual config) if one is set, but do NOT let it silently drift upward.
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effective, systemPrompt, tools,
                    userTemperature > 0f ? userTemperature : 0.2f,
                    userMaxTokens > 0 ? userMaxTokens : 4096);
            Request.Builder builder = new Request.Builder()
                    .url(getChatUrl())
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            if (tag != null) builder.tag(tag);
            client.newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("Morph request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Morph: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Morph empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Morph build error: " + e.getMessage());
        }
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("morph-v3-fast", "Morph V3 Fast", AiProvider.MORPH, 0, "Morph — precise code editing"));
        l.add(new ModelInfo("morph-v3",      "Morph V3",      AiProvider.MORPH, 0, "Morph — precise code editing"));
        return l;
    }
}
