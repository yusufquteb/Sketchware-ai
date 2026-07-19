package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
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
 * AI API client for the OpenRouter API (OpenAI-compatible).
 *
 * <p>Uses standard OpenAI chat completion format with bearer token authentication
 * and additional OpenRouter-specific headers ({@code HTTP-Referer}, {@code X-Title}).
 */
public class OpenRouterApiClient extends AiApiClient {

    private static final String BASE_URL = "https://openrouter.ai";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String HTTP_REFERER = "https://sketchware.pro";
    private static final String X_TITLE = "Sketchware Pro Agent";

    public OpenRouterApiClient(String apiKey) {
        super(apiKey, AiProvider.OPENROUTER);
    }

    // -----------------------------------------------------------------------
    // Model listing
    // -----------------------------------------------------------------------

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        String url = BASE_URL + "/api/v1/models";
        Request request = addBearerAuth(new Request.Builder())
                .url(url)
                .get()
                .header("HTTP-Referer", HTTP_REFERER)
                .header("X-Title", X_TITLE)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OpenRouter fetchModels failed: HTTP " + response.code()
                        + " " + readBodySafely(response));
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("OpenRouter fetchModels returned empty body");
            }

            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray dataArray = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();

            List<ModelInfo> result = new ArrayList<>();
            for (JsonElement elem : dataArray) {
                JsonObject model = elem.getAsJsonObject();

                String id = getStringOrDefault(model, "id", "");
                String name = getStringOrDefault(model, "name", id);
                String description = getStringOrDefault(model, "description", "");
                long contextLength = model.has("context_length") && !model.get("context_length").isJsonNull()
                        ? model.get("context_length").getAsLong() : 0L;

                // Append pricing info to description if available
                String pricingInfo = extractPricingInfo(model);
                if (!pricingInfo.isEmpty()) {
                    description = description.isEmpty() ? pricingInfo : description + " | " + pricingInfo;
                }

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
                result.add(new ModelInfo(id, name, AiProvider.OPENROUTER, contextLength, description));
            }

            // Sort models alphabetically (A-Z)
            java.util.Collections.sort(result);

            return result;
        }
    }

    private String extractPricingInfo(JsonObject model) {
        if (!model.has("pricing") || model.get("pricing").isJsonNull()) {
            return "";
        }

        JsonObject pricing = model.getAsJsonObject("pricing");
        String prompt = getStringOrDefault(pricing, "prompt", "");
        String completion = getStringOrDefault(pricing, "completion", "");

        if (prompt.isEmpty() && completion.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("Pricing: ");
        if (!prompt.isEmpty()) {
            sb.append("$").append(prompt).append("/tok prompt");
        }
        if (!completion.isEmpty()) {
            if (!prompt.isEmpty()) {
                sb.append(", ");
            }
            sb.append("$").append(completion).append("/tok completion");
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Chat requests
    // -----------------------------------------------------------------------

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
            String url = BASE_URL + "/api/v1/chat/completions";

            JsonObject requestBody = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, modelId, systemPrompt, tools, userTemperature, userMaxTokens);

            Request.Builder builder = addBearerAuth(new Request.Builder())
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", HTTP_REFERER)
                    .header("X-Title", X_TITLE);
            
            if (tag != null) builder.tag(tag);
            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("OpenRouter request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String errorBody = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("OpenRouter: " + AiErrorHelper.getFriendlyMessage(code, errorBody));
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        handler.onError("OpenRouter returned empty response body");
                        return;
                    }

                    NvidiaApiClient.parseOpenAiSseStream(body, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Failed to build OpenRouter request: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }

    private static String readBodySafely(Response response) {
        try {
            ResponseBody body = response.body();
            return body != null ? body.string() : "(no body)";
        } catch (Exception e) {
            return "(failed to read body: " + e.getMessage() + ")";
        }
    }
}
