package pro.sketchware.ai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import pro.sketchware.ai.models.ToolCall;

/**
 * AI API client for the NVIDIA NIM API (OpenAI-compatible).
 *
 * <p>Also provides the shared static helpers {@link #buildOpenAiRequestBody} and
 * {@link #parseOpenAiSseStream} that are reused by all other OpenAI-compatible clients
 * (OpenAI, Groq, DeepSeek, xAI, OpenRouter, Paxsenix, LocalLlm, DeepInfra, AirForce).
 *
 * <p>Changes vs. original:
 * <ul>
 *   <li>✅ FIX: system-role {@link ChatMessage}s in the conversation history are no longer silently
 *       dropped. They are now forwarded as real {@code {"role":"system",...}} messages so the
 *       auto-fix feedback loop actually reaches the model.</li>
 *   <li>✅ FIX: temperature and max_tokens are accepted as optional parameters.</li>
 * </ul>
 */
public class NvidiaApiClient extends AiApiClient {

    private static final String BASE_URL = "https://integrate.api.nvidia.com";
    private static final MediaType JSON  = MediaType.get("application/json; charset=utf-8");

    public NvidiaApiClient(String apiKey) {
        super(apiKey, AiProvider.NVIDIA);
    }

    // ── Model listing ────────────────────────────────────────────────────────

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        String url = BASE_URL + "/v1/models";
        Request request = addBearerAuth(new Request.Builder())
                .url(url)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("NVIDIA HTTP " + response.code() + " "
                        + readBodySafely(response));
            }
            ResponseBody body = response.body();
            if (body == null) return fallback();
            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray data  = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();
            List<ModelInfo> result = new ArrayList<>();
            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id      = getStringOrDefault(obj, "id", null);
                if (id == null) continue;
                String name    = getStringOrDefault(obj, "name", id);
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
                result.add(new ModelInfo(id, name, AiProvider.NVIDIA, 0, "NVIDIA NIM " + id));
            }
            return result.isEmpty() ? fallback() : result;
        }
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

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
            JsonObject requestBody = buildOpenAiRequestBody(messages, modelId, systemPrompt, tools,
                    userTemperature, userMaxTokens);
            Request.Builder builder = addBearerAuth(new Request.Builder())
                    .url(BASE_URL + "/v1/chat/completions")
                    .post(RequestBody.create(requestBody.toString(), JSON));
            
            if (tag != null) builder.tag(tag);
            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("NVIDIA error: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("NVIDIA: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Empty response"); return; }
                    parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("NVIDIA: " + e.getMessage());
        }
    }

    // ── Shared helpers (used by all OpenAI-compatible clients) ───────────────

    /**
     * Builds an OpenAI-format chat completions request body.
     *
     * <p>✅ FIX: system-role ChatMessages (injected by the auto-fix feedback loop) are
     * no longer silently skipped. They are forwarded as real system messages so that
     * the model receives the build-error feedback prompt.
     *
     * @param messages     full conversation history including any system feedback messages
     * @param modelId      the model identifier
     * @param systemPrompt the top-level system instruction (injected first)
     * @param tools        optional tool definitions; null or empty = no tools
     */
    public static JsonObject buildOpenAiRequestBody(List<ChatMessage> messages, String modelId,
                                             String systemPrompt, List<ToolDefinition> tools) {
        return buildOpenAiRequestBody(messages, modelId, systemPrompt, tools, 0f, 0);
    }

    /**
     * Builds an OpenAI-format request body with optional temperature and max_tokens.
     *
     * @param temperature  0 = use provider default; values 0.01–1.0 are sent explicitly
     * @param maxTokens    0 = use provider default; positive values are sent explicitly
     */
    public static JsonObject buildOpenAiRequestBody(List<ChatMessage> messages, String modelId,
                                             String systemPrompt, List<ToolDefinition> tools,
                                             float temperature, int maxTokens) {
        JsonObject body = new JsonObject();
        body.addProperty("model", modelId);
        body.addProperty("stream", true);
        if (temperature > 0f) body.addProperty("temperature", temperature);
        if (maxTokens > 0)    body.addProperty("max_tokens", maxTokens);

        JsonArray messagesArray = new JsonArray();

        // Top-level system prompt goes first
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", systemPrompt);
            messagesArray.add(sysMsg);
        }

        // Conversation messages
        for (ChatMessage message : messages) {
            String role = message.getRole();
            if (role == null) continue;

            JsonObject msg = new JsonObject();
            msg.addProperty("role", role);

            if ("system".equals(role)) {
                // ✅ FIX: system messages from the auto-fix feedback loop must reach the model.
                // Previously these were silently dropped under the incorrect assumption that
                // the top-level systemPrompt parameter already covers all system instructions.
                // The feedback loop injects runtime content (build errors) that must follow
                // actual conversation turns, so they must be kept in sequence.
                msg.addProperty("content", message.getContent() != null ? message.getContent() : "");

            } else if ("tool".equals(role)) {
                msg.addProperty("content", message.getContent() != null ? message.getContent() : "");
                if (message.getToolCallId() != null) {
                    msg.addProperty("tool_call_id", message.getToolCallId());
                }
                // ✅ FIX for OpenRouter/OpenAI: Tool messages must include 'name' (the tool name)
                if (message.getToolName() != null && !message.getToolName().isEmpty()) {
                    msg.addProperty("name", message.getToolName());
                }

            } else if ("assistant".equals(role)
                    && message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                if (message.getContent() != null) {
                    msg.addProperty("content", message.getContent());
                }
                JsonArray toolCallsArray = new JsonArray();
                for (pro.sketchware.ai.models.ToolCall tc : message.getToolCalls()) {
                    JsonObject tcObj = new JsonObject();
                    tcObj.addProperty("id",   tc.getId()   != null ? tc.getId()   : "");
                    tcObj.addProperty("type", "function");
                    JsonObject function = new JsonObject();
                    function.addProperty("name",      tc.getName()      != null ? tc.getName()      : "");
                    function.addProperty("arguments", tc.getArguments() != null ? tc.getArguments() : "{}");
                    tcObj.add("function", function);
                    toolCallsArray.add(tcObj);
                }
                msg.add("tool_calls", toolCallsArray);

            } else {
                msg.addProperty("content", message.getContent() != null ? message.getContent() : "");
            }

            messagesArray.add(msg);
        }

        body.add("messages", messagesArray);

        // Tools
        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (ToolDefinition tool : tools) {
                toolsArray.add(tool.toOpenAiJson());
            }
            body.add("tools", toolsArray);
            body.addProperty("tool_choice", "auto");
        }

        return body;
    }

    // ── OpenAI SSE stream parser ─────────────────────────────────────────────

    /**
     * Parses an OpenAI-format SSE stream.
     * Handles text content deltas and tool call deltas (streamed incrementally by index).
     */
    public static void parseOpenAiSseStream(ResponseBody body, StreamingResponseHandler handler) {
        StringBuilder fullResponse = new StringBuilder();
        java.util.Map<Integer, ToolCallAccumulator> toolCallMap = new java.util.LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                try {
                    JsonObject event = JsonParser.parseString(data).getAsJsonObject();
                    processOpenAiEvent(event, fullResponse, toolCallMap, handler);
                } catch (Exception ignored) {}
            }
            // Emit any accumulated tool calls at end of stream
            for (ToolCallAccumulator acc : toolCallMap.values()) {
                handler.onToolCall(new ToolCall(acc.id, acc.name, acc.arguments.toString()));
            }
            handler.onComplete(fullResponse.toString());
        } catch (IOException e) {
            // Software caused connection abort usually happens when the app is backgrounded
            // or the socket is closed by the OS. We don't want to show this as a scary error.
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Software caused connection abort") || msg.contains("Socket closed"))) {
                // Silently complete or send a friendly message if we already have some response
                if (fullResponse.length() > 0) {
                    handler.onComplete(fullResponse.toString());
                } else {
                    handler.onError("Connection lost. Please try again.");
                }
            } else {
                handler.onError("Error reading stream: " + e.getMessage());
            }
        }
    }

    private static void processOpenAiEvent(JsonObject event, StringBuilder fullResponse,
                                           java.util.Map<Integer, ToolCallAccumulator> toolCallMap,
                                           StreamingResponseHandler handler) {
        if (!event.has("choices")) return;
        JsonArray choices = event.getAsJsonArray("choices");
        if (choices.size() == 0) return;
        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("delta")) return;
        JsonObject delta = choice.getAsJsonObject("delta");

        if (delta.has("content") && !delta.get("content").isJsonNull()) {
            String content = delta.get("content").getAsString();
            fullResponse.append(content);
            handler.onChunk(content);
        }

        if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
            JsonArray toolCalls = delta.getAsJsonArray("tool_calls");
            for (JsonElement tcElem : toolCalls) {
                JsonObject tc = tcElem.getAsJsonObject();
                int index = tc.has("index") ? tc.get("index").getAsInt() : 0;
                ToolCallAccumulator acc = toolCallMap.computeIfAbsent(index,
                        k -> new ToolCallAccumulator());
                if (tc.has("id") && !tc.get("id").isJsonNull()) {
                    acc.id = tc.get("id").getAsString();
                }
                if (tc.has("function")) {
                    JsonObject function = tc.getAsJsonObject("function");
                    if (function.has("name") && !function.get("name").isJsonNull()) {
                        acc.name = function.get("name").getAsString();
                    }
                    if (function.has("arguments") && !function.get("arguments").isJsonNull()) {
                        acc.arguments.append(function.get("arguments").getAsString());
                    }
                }
            }
        }
    }

    static class ToolCallAccumulator {
        String id   = "";
        String name = "";
        StringBuilder arguments = new StringBuilder();
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private static String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString() : defaultValue;
    }

    /**
     * @deprecated Use {@link AiErrorHelper#getFriendlyMessage(int, String)} instead.
     */
    @Deprecated
    public static String getFriendlyErrorMessage(int code, String rawError) {
        return AiErrorHelper.getFriendlyMessage(code, rawError);
    }

    /**
     * @deprecated Use {@link AiErrorHelper#readBodySafely(Response)} instead.
     */
    @Deprecated
    static String readBodySafely(Response response) {
        return AiErrorHelper.readBodySafely(response);
    }

    private static List<ModelInfo> fallback() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("meta/llama-3.3-70b-instruct",      "Llama 3.3 70B",        AiProvider.NVIDIA, 128000, "NVIDIA NIM"));
        l.add(new ModelInfo("deepseek-ai/deepseek-r1",           "DeepSeek R1",          AiProvider.NVIDIA, 128000, "Reasoning model"));
        l.add(new ModelInfo("google/gemma-3-27b-it",             "Gemma 3 27B",          AiProvider.NVIDIA, 128000, "Google Gemma 3"));
        l.add(new ModelInfo("mistralai/mistral-large-2-instruct","Mistral Large 2",      AiProvider.NVIDIA, 128000, "Mistral"));
        return l;
    }
}
