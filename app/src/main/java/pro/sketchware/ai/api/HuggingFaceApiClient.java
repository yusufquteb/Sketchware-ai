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
 * AI API client for HuggingFace Inference API (OpenAI-compatible endpoint).
 *
 * <p>HuggingFace provides free inference for many open-source models including
 * Gemma, Llama, Mistral, and Qwen. Free API key available at huggingface.co.
 */
public class HuggingFaceApiClient extends AiApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public HuggingFaceApiClient(String apiKey) {
        super(apiKey, AiProvider.HUGGINGFACE);
    }

    // ── Model listing ────────────────────────────────────────────────────────

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        // HuggingFace does not have a simple /models endpoint for inference-ready models,
        // so we return a curated list of well-known free models.
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
                    ? modelId : "google/gemma-3-27b-it";

            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effectiveModel, systemPrompt, tools, userTemperature, userMaxTokens);

            // HuggingFace OpenAI-compatible endpoint
            String chatUrl = AiProvider.HUGGINGFACE.getBaseUrl()
                    + AiProvider.HUGGINGFACE.getChatEndpoint();

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
                    handler.onError("HuggingFace request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("HuggingFace: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) {
                        handler.onError("HuggingFace returned empty body");
                        return;
                    }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("HuggingFace build error: " + e.getMessage());
        }
    }

    // ── Fallback models ──────────────────────────────────────────────────────

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> list = new ArrayList<>();
        list.add(new ModelInfo("google/gemma-3-27b-it",
                "Gemma 3 27B IT", AiProvider.HUGGINGFACE, 131072,
                "HuggingFace — Google Gemma 3 27B (Free)"));
        list.add(new ModelInfo("meta-llama/Llama-3.1-8B-Instruct",
                "Llama 3.1 8B Instruct", AiProvider.HUGGINGFACE, 131072,
                "HuggingFace — Meta Llama 3.1 8B (Free)"));
        list.add(new ModelInfo("mistralai/Mistral-7B-Instruct-v0.3",
                "Mistral 7B Instruct v0.3", AiProvider.HUGGINGFACE, 32768,
                "HuggingFace — Mistral 7B (Free)"));
        list.add(new ModelInfo("Qwen/Qwen2.5-72B-Instruct",
                "Qwen 2.5 72B Instruct", AiProvider.HUGGINGFACE, 131072,
                "HuggingFace — Qwen 2.5 72B (Free)"));
        list.add(new ModelInfo("microsoft/Phi-3.5-mini-instruct",
                "Phi 3.5 Mini Instruct", AiProvider.HUGGINGFACE, 131072,
                "HuggingFace — Microsoft Phi 3.5 Mini (Free)"));
        return list;
    }
}
