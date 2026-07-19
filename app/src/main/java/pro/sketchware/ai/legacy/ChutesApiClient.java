package pro.sketchware.ai.legacy;

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

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiErrorHelper;
import pro.sketchware.ai.api.NvidiaApiClient;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.api.ToolDefinition;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;

/**
 * AirForce AI API client — OpenAI-compatible endpoint via api.airforce.
 * (Class name kept as ChutesApiClient for backward compatibility — internally serves AirForce AI.)
 *
 * Base URL  : https://api.airforce/v1
 * Free tier : Completely FREE — no API key required
 * API key   : optional (for future authenticated tiers)
 * Supports  : GPT-4o, Claude, Gemini Flash, Llama 4, DeepSeek V3/R1, Qwen3, Mistral and more
 *
 * <p><b>LEGACY — not wired into {@code AiClientFactory}.</b> The {@link AiProvider#CHUTES}
 * enum value is intentionally hidden from the AI Settings UI (see
 * {@code AiSettingsActivity}) and has no case in {@code AiClientFactory.createClient()},
 * so this client cannot currently be reached through normal app flow. It is kept only
 * so that {@code AiProvider.CHUTES} keeps resolving for any value a user may have saved
 * to SharedPreferences back when this provider was selectable (see
 * {@code AiProvider#fromName} / {@code AiPreferences}). Do not delete without first
 * confirming no shipped build ever exposed CHUTES in the UI.
 */
@SuppressWarnings("deprecation") // this entire class exists only to back the deprecated CHUTES provider
public class ChutesApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.airforce/v1/models";
    private static final String CHAT_URL   = "https://api.airforce/v1/chat/completions";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public ChutesApiClient(String apiKey) {
        super(apiKey, AiProvider.CHUTES);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request.Builder reqBuilder = new Request.Builder()
                .url(MODELS_URL).get()
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }
        try (Response r = client.newCall(reqBuilder.build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("AirForce HTTP " + r.code());
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
                        || lo.contains("stable-diff") || lo.contains("sdxl")
                        || lo.contains("tts") || lo.contains("speech")) continue;
                long ctx = 0;
                if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
                    try { ctx = obj.get("context_length").getAsLong(); } catch (Exception ignored) {}
                }
                String name = str(obj, "name");
                if (name == null || name.isEmpty()) name = toName(id);
                result.add(new ModelInfo(id, name, AiProvider.CHUTES, ctx,
                        "AirForce AI \u2014 " + name + " (free, no key)"));
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
                    ? modelId : "gpt-4o";
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effective, systemPrompt, tools, 0.7f, 8192);
            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            if (tag != null) builder.tag(tag);
            client.newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("AirForce AI request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("AirForce AI: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("AirForce AI: empty response"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("AirForce AI error: " + e.getMessage());
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
        l.add(new ModelInfo("gpt-4o",                       "GPT-4o",                  AiProvider.CHUTES, 128000, "AirForce AI \u2014 GPT-4o (free)"));
        l.add(new ModelInfo("gpt-4o-mini",                  "GPT-4o Mini",             AiProvider.CHUTES, 128000, "AirForce AI \u2014 GPT-4o Mini (free)"));
        l.add(new ModelInfo("claude-3-5-sonnet",            "Claude 3.5 Sonnet",        AiProvider.CHUTES, 200000, "AirForce AI \u2014 Claude 3.5 Sonnet (free)"));
        l.add(new ModelInfo("gemini-1-5-flash",             "Gemini 1.5 Flash",         AiProvider.CHUTES, 1000000,"AirForce AI \u2014 Gemini 1.5 Flash (free)"));
        l.add(new ModelInfo("meta-llama/llama-4-maverick",  "Llama 4 Maverick",         AiProvider.CHUTES, 524288, "AirForce AI \u2014 Llama 4 Maverick (free)"));
        l.add(new ModelInfo("meta-llama/llama-4-scout",     "Llama 4 Scout",            AiProvider.CHUTES, 524288, "AirForce AI \u2014 Llama 4 Scout (free)"));
        l.add(new ModelInfo("deepseek-v3",                  "DeepSeek V3",              AiProvider.CHUTES,  65536, "AirForce AI \u2014 DeepSeek V3 (free)"));
        l.add(new ModelInfo("deepseek-r1",                  "DeepSeek R1",              AiProvider.CHUTES,  65536, "AirForce AI \u2014 DeepSeek R1 (free)"));
        l.add(new ModelInfo("Qwen/Qwen3-235B-A22B",         "Qwen3 235B",               AiProvider.CHUTES, 131072, "AirForce AI \u2014 Qwen3 235B (free)"));
        l.add(new ModelInfo("mistral-7b-instruct",          "Mistral 7B Instruct",      AiProvider.CHUTES,  32768, "AirForce AI \u2014 Mistral 7B (free)"));
        return l;
    }
}
