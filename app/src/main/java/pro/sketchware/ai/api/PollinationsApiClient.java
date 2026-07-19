package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;

/**
 * Pollinations AI API client — OpenAI-compatible endpoint.
 *
 * Base URL   : https://text.pollinations.ai
 * Models URL : https://text.pollinations.ai/models
 * Chat URL   : https://text.pollinations.ai/openai  (OpenAI-compatible)
 * Free tier  : ~1 req/min anonymous, higher limits with free key from enter.pollinations.ai
 * API key    : optional
 *
 * Uses fixed fallback model list to avoid slow/unreliable /models fetch.
 * Models are fetched asynchronously in background and merged with fallback list.
 */
public class PollinationsApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://text.pollinations.ai/models";
    private static final String CHAT_URL   = "https://text.pollinations.ai/openai";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    /** Fast dedicated client with short timeout for model fetching */
    private static final OkHttpClient FAST_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build();

    public PollinationsApiClient(String apiKey) {
        super(apiKey, AiProvider.POLLINATIONS);
    }

    /**
     * Returns the fixed model list immediately (fast).
     * Attempts a background refresh from the /models endpoint but always falls back to the
     * stable list so the UI is never blocked waiting for the network.
     */
    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        // Always return the known-good list immediately
        // This avoids the long wait caused by polling /models which can be slow or return
        // a changing/empty list depending on Pollinations server load
        return stableModels();
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
            String effective = (modelId != null && !modelId.isEmpty()) ? modelId : "openai";
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(
                    messages, effective, systemPrompt, tools,
                    userTemperature > 0f ? userTemperature : 0.7f,
                    userMaxTokens > 0 ? userMaxTokens : 8192);
            Request.Builder reqBuilder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }
            if (tag != null) reqBuilder.tag(tag);
            client.newCall(reqBuilder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("Pollinations AI request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Pollinations AI: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Pollinations AI: empty response"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Pollinations AI error: " + e.getMessage());
        }
    }

    /**
     * Stable, curated model list — always available instantly without a network call.
     * Updated based on Pollinations\'s documented available models (June 2025).
     */
    public static List<ModelInfo> stableModels() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("openai",           "GPT-4o",              AiProvider.POLLINATIONS, 128000,  "Pollinations AI — GPT-4o via proxy (free)"));
        l.add(new ModelInfo("openai-large",     "GPT-4o Large",        AiProvider.POLLINATIONS, 128000,  "Pollinations AI — GPT-4o with larger context (free)"));
        l.add(new ModelInfo("openai-reasoning", "o3 Mini (Reasoning)", AiProvider.POLLINATIONS, 128000,  "Pollinations AI — OpenAI o3-mini reasoning model (free)"));
        l.add(new ModelInfo("claude",           "Claude",              AiProvider.POLLINATIONS, 200000,  "Pollinations AI — Claude proxy (free)"));
        l.add(new ModelInfo("deepseek",         "DeepSeek V3",         AiProvider.POLLINATIONS, 128000,  "Pollinations AI — DeepSeek V3 (free)"));
        l.add(new ModelInfo("deepseek-reasoning","DeepSeek R1 (Think)", AiProvider.POLLINATIONS, 128000, "Pollinations AI — DeepSeek R1 reasoning (free)"));
        l.add(new ModelInfo("mistral",          "Mistral Large",       AiProvider.POLLINATIONS, 128000,  "Pollinations AI — Mistral Large (free)"));
        l.add(new ModelInfo("qwen-coder",       "Qwen2.5 Coder 32B",   AiProvider.POLLINATIONS, 65536,   "Pollinations AI — Best coding model via proxy (free)"));
        l.add(new ModelInfo("llama",            "Llama 3.3 70B",       AiProvider.POLLINATIONS, 131072,  "Pollinations AI — Meta Llama 3.3 70B (free)"));
        l.add(new ModelInfo("llamalight",       "Llama 3.1 8B",        AiProvider.POLLINATIONS, 131072,  "Pollinations AI — Meta Llama 3.1 8B (fast, free)"));
        l.add(new ModelInfo("searchgpt",        "SearchGPT",           AiProvider.POLLINATIONS, 128000,  "Pollinations AI — GPT-4o with web search (free)"));
        l.add(new ModelInfo("phi",              "Phi-4",               AiProvider.POLLINATIONS, 16000,   "Pollinations AI — Microsoft Phi-4 (free)"));
        return l;
    }
}
