package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
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
 * AI API client for Google AI Studio (OpenAI-compatible endpoint).
 *
 * <p>Google AI Studio provides free access to Gemma models and Gemini Flash.
 * Free API key available at aistudio.google.com.
 */
public class GoogleAiStudioApiClient extends AiApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public GoogleAiStudioApiClient(String apiKey) {
        super(apiKey, AiProvider.GOOGLE_AI_STUDIO);
    }

    // ── Model listing ────────────────────────────────────────────────────────

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        // Return a curated list of free models available on Google AI Studio
        return fallbackModels();
    }

    // ── Chat requests ────────────────────────────────────────────────────────

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
            String effectiveModel = (modelId != null && !modelId.isEmpty())
                    ? modelId : "gemma-3-27b-it";

            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effectiveModel, systemPrompt, tools);

            String chatUrl = AiProvider.GOOGLE_AI_STUDIO.getBaseUrl()
                    + AiProvider.GOOGLE_AI_STUDIO.getChatEndpoint();

            Request.Builder builder = new Request.Builder()
                    .url(chatUrl)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");

            if (tag != null) builder.tag(tag);
            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("Google AI Studio request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Google AI Studio: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) {
                        handler.onError("Google AI Studio returned empty body");
                        return;
                    }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Google AI Studio build error: " + e.getMessage());
        }
    }

    // ── Fallback models ──────────────────────────────────────────────────────

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> list = new ArrayList<>();
        list.add(new ModelInfo("gemma-3-27b-it",
                "Gemma 3 27B IT", AiProvider.GOOGLE_AI_STUDIO, 131072,
                "Google AI Studio — Gemma 3 27B (Free)"));
        list.add(new ModelInfo("gemma-3-12b-it",
                "Gemma 3 12B IT", AiProvider.GOOGLE_AI_STUDIO, 131072,
                "Google AI Studio — Gemma 3 12B (Free)"));
        list.add(new ModelInfo("gemma-3-4b-it",
                "Gemma 3 4B IT", AiProvider.GOOGLE_AI_STUDIO, 131072,
                "Google AI Studio — Gemma 3 4B (Free)"));
        list.add(new ModelInfo("gemma-3-1b-it",
                "Gemma 3 1B IT", AiProvider.GOOGLE_AI_STUDIO, 32768,
                "Google AI Studio — Gemma 3 1B (Free)"));
        list.add(new ModelInfo("gemma-3n-e4b-it",
                "Gemma 3n E4B IT", AiProvider.GOOGLE_AI_STUDIO, 32768,
                "Google AI Studio — Gemma 3n E4B (Free)"));
        return list;
    }
}
