package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
 * DeepInfra client using the OpenAI-compatible chat completions endpoint.
 * Designed to work with DeepInfra's free tier by rotating identity headers
 * (User-Agent, X-Request-ID, etc.) to avoid 403 rate-limiting blocks.
 */
public class DeepInfraApiClient extends AiApiClient {

    private static final String CHAT_URL = "https://api.deepinfra.com/v1/openai/chat/completions";
    private static final String MODELS_URL = "https://api.deepinfra.com/v1/openai/models";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Rotating User-Agent pool to avoid fingerprinting
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Linux; Android 14) Sketchware-Pro/1.0",
        "Mozilla/5.0 (Linux; Android 13) Sketchware-Pro/1.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
    };

    private static final Random RANDOM = new Random();
    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger(0);

    public DeepInfraApiClient(String apiKey) {
        super(apiKey, AiProvider.DEEPINFRA);
    }

    /**
     * Picks a random User-Agent from the pool.
     */
    private String getRandomUserAgent() {
        return USER_AGENTS[RANDOM.nextInt(USER_AGENTS.length)];
    }

    /**
     * Generates a unique request ID to make each request look distinct.
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Applies randomized headers common to browser-like requests.
     * Called on every request to avoid fingerprinting.
     */
    private Request.Builder applyRandomizedHeaders(Request.Builder builder) {
        return builder
                .header("User-Agent", getRandomUserAgent())
                .header("Accept", "text/event-stream, */*")
                .header("X-Request-ID", generateRequestId())
                .header("Cache-Control", "no-cache");
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(MODELS_URL)
                .get();
        
        applyRandomizedHeaders(builder);

        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String body = readBodySafely(response);
                if (response.code() == 403) {
                    return fallbackModels();
                }
                throw new IOException("DeepInfra fetchModels failed: HTTP " + response.code() + " " + body);
            }

            ResponseBody body = response.body();
            if (body == null) {
                return fallbackModels();
            }

            String bodyString = body.string();
            JsonElement root = JsonParser.parseString(bodyString);
            List<ModelInfo> result = new ArrayList<>();
            JsonArray array;
            
            // /v1/openai/models returns a plain array of model objects
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("data") && obj.get("data").isJsonArray()) {
                    array = obj.getAsJsonArray("data");
                } else {
                    return fallbackModels();
                }
            } else {
                return fallbackModels();
            }

            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject model = element.getAsJsonObject();
                
                // Try both id and model_name fields
                String id = getString(model, "id");
                if (id == null || id.isEmpty()) {
                    id = getString(model, "model_name");
                }
                if (id == null || id.isEmpty()) continue;
                
                // Skip non-coding / non-general useful models
                String type = getString(model, "type");
                if (type != null) {
                    String lowerType = type.toLowerCase(Locale.US);
                    if (lowerType.contains("embedding") || lowerType.contains("image") || lowerType.contains("speech")) continue;
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

                }
                
                // Only keep powerful, coding or Gemma models
                String lowerId = id.toLowerCase(Locale.US);
                boolean isGemma = lowerId.contains("gemma");
                boolean isCoding = lowerId.contains("coder") || lowerId.contains("instruct") || lowerId.contains("deepseek-v3") || lowerId.contains("llama-3.3") || lowerId.contains("r1") || isGemma;
                if (!isCoding && !lowerId.contains("70b") && !lowerId.contains("405b")) continue;
                
                // Parse metadata sub-object (DeepInfra /v1/openai/models format)
                long contextLength = 0L;
                int maxTokens = 0;
                String description = null;
                if (model.has("metadata") && model.get("metadata").isJsonObject()) {
                    JsonObject meta = model.getAsJsonObject("metadata");
                    description = getString(meta, "description");
                    if (meta.has("context_length") && !meta.get("context_length").isJsonNull()) {
                        try { contextLength = meta.get("context_length").getAsLong(); } catch (Exception ignore) {}
                    }
                    if (meta.has("max_tokens") && !meta.get("max_tokens").isJsonNull()) {
                        try { maxTokens = meta.get("max_tokens").getAsInt(); } catch (Exception ignore) {}
                    }
                }
                
                // Fallback: top-level description
                if (description == null || description.isEmpty()) {
                    description = getString(model, "description");
                }
                
                String displayName = getString(model, "name");
                if (displayName == null || displayName.isEmpty()) {
                    displayName = toDisplayName(id);
                }
                
                ModelInfo baseInfo = new ModelInfo(id, displayName, AiProvider.DEEPINFRA, contextLength,
                        description != null ? description : "DeepInfra model");
                if (maxTokens > 0 || contextLength > 0) {
                    result.add(baseInfo.withMetadata(maxTokens, true, true, null));
                } else {
                    result.add(baseInfo);
                }
            }

            // Sort models alphabetically (A-Z)
            java.util.Collections.sort(result);

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
            JsonObject requestBody = NvidiaApiClient.buildOpenAiRequestBody(
                    messages,
                    (modelId == null || modelId.trim().isEmpty()) ? "deepseek-ai/DeepSeek-V3" : modelId,
                    systemPrompt,
                    tools
            );

            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(requestBody.toString(), JSON));
            
            if (tag != null) builder.tag(tag);
            applyRandomizedHeaders(builder);

            client.newCall(builder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("DeepInfra request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String errorBody = readBodySafely(response);
                        response.close();
                        handler.onError("DeepInfra: " + AiErrorHelper.getFriendlyMessage(code, errorBody));
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        handler.onError("DeepInfra returned empty response body");
                        return;
                    }

                    NvidiaApiClient.parseOpenAiSseStream(body, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Failed to build DeepInfra request: " + e.getMessage());
        }
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static String readBodySafely(Response response) {
        try {
            ResponseBody body = response.body();
            return body != null ? body.string() : "(no body)";
        } catch (Exception e) {
            return "(failed to read body: " + e.getMessage() + ")";
        }
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> fallback = new ArrayList<>();
        fallback.add(new ModelInfo("deepseek-ai/DeepSeek-V3", "DeepSeek V3",
                AiProvider.DEEPINFRA, 128000, "DeepInfra 🆓 — Best for code & general tasks"));
        fallback.add(new ModelInfo("meta-llama/Llama-3.3-70B-Instruct", "Llama 3.3 70B",
                AiProvider.DEEPINFRA, 128000, "DeepInfra 🆓 — Powerful instruct model"));
        fallback.add(new ModelInfo("Qwen/Qwen2.5-Coder-32B-Instruct", "Qwen 2.5 Coder 32B",
                AiProvider.DEEPINFRA, 128000, "DeepInfra 🆓 — Specialized coding model"));
        return fallback;
    }

    private static String toDisplayName(String id) {
        String value = id.replace('/', ' ').replace('-', ' ').replace('_', ' ').trim();
        if (value.isEmpty()) {
            return "DeepInfra";
        }
        return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
    }
}
