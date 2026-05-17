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
 * Anthropic Claude API client.
 *
 * <p>Uses the Anthropic Messages API (not OpenAI-compatible).
 * Supports streaming text AND tool_use blocks via SSE.
 *
 * <p>Key differences from OpenAI format:
 * <ul>
 *   <li>Auth: {@code x-api-key} header (not Bearer token)</li>
 *   <li>Tools: wrapped as {@code tools} array with {@code input_schema} (not {@code parameters})</li>
 *   <li>SSE events: {@code content_block_start}, {@code content_block_delta}, {@code content_block_stop}</li>
 *   <li>Tool streaming: {@code input_json_delta} delta type builds JSON incrementally</li>
 * </ul>
 */
public class AnthropicApiClient extends AiApiClient {

    private static final String MODELS_URL  = "https://api.anthropic.com/v1/models";
    private static final String CHAT_URL    = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final MediaType JSON     = MediaType.get("application/json; charset=utf-8");

    public AnthropicApiClient(String apiKey) {
        super(apiKey, AiProvider.ANTHROPIC);
    }

    // ── Model listing ────────────────────────────────────────────────────────

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        Request request = new Request.Builder()
                .url(MODELS_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Anthropic HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) return fallback();
            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray data  = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();
            List<ModelInfo> result = new ArrayList<>();
            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                JsonObject obj  = el.getAsJsonObject();
                String id       = str(obj, "id");
                String name     = str(obj, "display_name");
                if (id == null) continue;
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
                long ctx = obj.has("context_window") ? obj.get("context_window").getAsLong() : 200000;
                result.add(new ModelInfo(id, name != null ? name : id, AiProvider.ANTHROPIC, ctx, "Anthropic " + id));
            }
            return result.isEmpty() ? fallback() : result;
        }
    }

    // ── Chat (no tools) ──────────────────────────────────────────────────────

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

    // ── Chat (with tools) ────────────────────────────────────────────────────

    /**
     * Sends a streaming request to the Anthropic Messages API.
     */
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
            // ── Build messages array ─────────────────────────────────────────
            JsonArray msgs = new JsonArray();
            for (ChatMessage m : messages) {
                if (m.getRole() == null) continue;

                switch (m.getRole()) {
                    case "user": {
                        // Plain user message
                        if (m.getContent() == null) continue;
                        JsonObject msg = new JsonObject();
                        msg.addProperty("role", "user");
                        msg.addProperty("content", m.getContent());
                        msgs.add(msg);
                        break;
                    }
                    case "assistant": {
                        // Assistant message — may contain tool_use blocks
                        JsonObject msg = new JsonObject();
                        msg.addProperty("role", "assistant");
                        if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                            // Mixed content: text + tool_use blocks
                            JsonArray contentArr = new JsonArray();
                            if (m.getContent() != null && !m.getContent().isEmpty()) {
                                JsonObject textBlock = new JsonObject();
                                textBlock.addProperty("type", "text");
                                textBlock.addProperty("text", m.getContent());
                                contentArr.add(textBlock);
                            }
                            for (pro.sketchware.ai.models.ToolCall tc : m.getToolCalls()) {
                                JsonObject toolUse = new JsonObject();
                                toolUse.addProperty("type", "tool_use");
                                toolUse.addProperty("id", tc.getId() != null ? tc.getId() : "");
                                toolUse.addProperty("name", tc.getName() != null ? tc.getName() : "");
                                // Parse arguments back to JSON object
                                JsonObject input = new JsonObject();
                                if (tc.getArguments() != null && !tc.getArguments().isEmpty()) {
                                    try {
                                        input = JsonParser.parseString(tc.getArguments()).getAsJsonObject();
                                    } catch (Exception ignored) {}
                                }
                                toolUse.add("input", input);
                                contentArr.add(toolUse);
                            }
                            msg.add("content", contentArr);
                        } else {
                            msg.addProperty("content", m.getContent() != null ? m.getContent() : "");
                        }
                        msgs.add(msg);
                        break;
                    }
                    case "tool": {
                        // Tool result — must be a user-role message with tool_result block
                        JsonObject toolResultBlock = new JsonObject();
                        toolResultBlock.addProperty("type", "tool_result");
                        toolResultBlock.addProperty("tool_use_id",
                                m.getToolCallId() != null ? m.getToolCallId() : "");
                        toolResultBlock.addProperty("content",
                                m.getContent() != null ? m.getContent() : "");
                        JsonArray contentArr = new JsonArray();
                        contentArr.add(toolResultBlock);
                        JsonObject msg = new JsonObject();
                        msg.addProperty("role", "user");
                        msg.add("content", contentArr);
                        msgs.add(msg);
                        break;
                    }
                    case "system": {
                        // ✅ FIX: Anthropic does not support 'system' role in messages array.
                        // Map system feedback (e.g. auto-fix build errors) to 'user' role
                        // with a [SYSTEM NOTE] prefix so they reach the model and are not dropped.
                        if (m.getContent() == null) break;
                        JsonObject sysMsg = new JsonObject();
                        sysMsg.addProperty("role", "user");
                        sysMsg.addProperty("content", "[SYSTEM NOTE]: " + m.getContent());
                        msgs.add(sysMsg);
                        break;
                    }
                    default:
                        break;
                }
            }

            // ── Build request body ───────────────────────────────────────────
            JsonObject body = new JsonObject();
            body.addProperty("model",      modelId != null ? modelId : "claude-3-5-sonnet-20241022");
            body.addProperty("max_tokens", 8192);
            body.addProperty("stream",     true);
            body.add("messages", msgs);

            // ── Anthropic Prompt Caching ─────────────────────────────────────────
            // Using the cache_control block on the system prompt saves ~90% of
            // system-prompt tokens on every subsequent request in the same session.
            // The cached prefix is stored server-side for 5 minutes (ephemeral).
            // Ref: https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                JsonArray systemArr = new JsonArray();
                JsonObject systemBlock = new JsonObject();
                systemBlock.addProperty("type", "text");
                systemBlock.addProperty("text", systemPrompt);
                // Mark as cacheable — Anthropic will store this prefix server-side
                JsonObject cacheControl = new JsonObject();
                cacheControl.addProperty("type", "ephemeral");
                systemBlock.add("cache_control", cacheControl);
                systemArr.add(systemBlock);
                body.add("system", systemArr);
            }

            // ✅ FIX: Anthropic tools use "input_schema" instead of "parameters"
            if (tools != null && !tools.isEmpty()) {
                JsonArray toolsArr = new JsonArray();
                for (ToolDefinition td : tools) {
                    JsonObject t = new JsonObject();
                    t.addProperty("name", td.getName());
                    t.addProperty("description", td.getDescription());
                    if (td.getParameters() != null) {
                        t.add("input_schema", td.getParameters());
                    } else {
                        JsonObject emptySchema = new JsonObject();
                        emptySchema.addProperty("type", "object");
                        emptySchema.add("properties", new JsonObject());
                        t.add("input_schema", emptySchema);
                    }
                    toolsArr.add(t);
                }
                body.add("tools", toolsArr);
            }

            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("x-api-key",          apiKey)
                    .header("anthropic-version",   API_VERSION)
                    .header("anthropic-beta",       "prompt-caching-2024-07-31")
                    .header("content-type",        "application/json");
            
            if (tag != null) builder.tag(tag);
            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("Anthropic error: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Anthropic: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Empty response"); return; }
                    parseAnthropicSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Anthropic: " + e.getMessage());
        }
    }

    // ── SSE stream parser ────────────────────────────────────────────────────

    /**
     * Parses the Anthropic SSE stream format.
     *
     * <p>Event sequence for a text response:
     * <pre>
     *   event: content_block_start  → {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}
     *   event: content_block_delta  → {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}
     *   event: content_block_stop
     *   event: message_stop
     * </pre>
     *
     * <p>Event sequence for a tool_use call:
     * <pre>
     *   event: content_block_start  → {"content_block":{"type":"tool_use","id":"toolu_xxx","name":"read_file","input":{}}}
     *   event: content_block_delta  → {"delta":{"type":"input_json_delta","partial_json":"{\"path\":"}}
     *   event: content_block_delta  → {"delta":{"type":"input_json_delta","partial_json":"\"foo\"}"}}
     *   event: content_block_stop   → emit ToolCall
     *   event: message_stop
     * </pre>
     */
    private static void parseAnthropicSseStream(ResponseBody body, StreamingResponseHandler handler) {
        // Per-block state
        String currentBlockType   = null;  // "text" or "tool_use"
        String currentToolId      = null;
        String currentToolName    = null;
        StringBuilder toolJsonAcc = new StringBuilder();
        StringBuilder fullText    = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data) || data.isEmpty()) continue;

                JsonObject event;
                try {
                    event = JsonParser.parseString(data).getAsJsonObject();
                } catch (Exception ignored) { continue; }

                String type = str(event, "type");
                if (type == null) continue;

                switch (type) {
                    case "content_block_start": {
                        JsonObject block = event.has("content_block")
                                ? event.getAsJsonObject("content_block") : null;
                        if (block == null) break;
                        currentBlockType = str(block, "type");
                        if ("tool_use".equals(currentBlockType)) {
                            currentToolId   = str(block, "id");
                            currentToolName = str(block, "name");
                            toolJsonAcc.setLength(0);
                        }
                        break;
                    }
                    case "content_block_delta": {
                        JsonObject delta = event.has("delta")
                                ? event.getAsJsonObject("delta") : null;
                        if (delta == null) break;
                        String deltaType = str(delta, "type");
                        if ("text_delta".equals(deltaType)) {
                            String text = str(delta, "text");
                            if (text != null) {
                                fullText.append(text);
                                handler.onChunk(text);
                            }
                        } else if ("input_json_delta".equals(deltaType)) {
                            // Accumulate partial JSON for tool input
                            String partial = str(delta, "partial_json");
                            if (partial != null) toolJsonAcc.append(partial);
                        }
                        break;
                    }
                    case "content_block_stop": {
                        // ✅ FIX: emit tool call when its block completes
                        if ("tool_use".equals(currentBlockType)
                                && currentToolId != null && currentToolName != null) {
                            String argsJson = toolJsonAcc.length() > 0
                                    ? toolJsonAcc.toString() : "{}";
                            handler.onToolCall(new ToolCall(currentToolId, currentToolName, argsJson));
                        }
                        currentBlockType = null;
                        currentToolId    = null;
                        currentToolName  = null;
                        toolJsonAcc.setLength(0);
                        break;
                    }
                    case "message_stop":
                        handler.onComplete(fullText.toString());
                        return;

                    case "error": {
                        JsonObject errObj = event.has("error")
                                ? event.getAsJsonObject("error") : null;
                        String errMsg = errObj != null ? str(errObj, "message") : null;
                        handler.onError("Anthropic stream error: "
                                + (errMsg != null ? errMsg : data));
                        return;
                    }
                    default:
                        break;
                }
            }
            handler.onComplete(fullText.toString());
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Software caused connection abort") || msg.contains("Socket closed"))) {
                if (fullText.length() > 0) {
                    handler.onComplete(fullText.toString());
                } else {
                    handler.onError("Connection lost. Please try again.");
                }
            } else {
                handler.onError("Anthropic stream error: " + (msg != null ? msg : "Unknown error"));
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static List<ModelInfo> fallback() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("claude-opus-4-5",             "Claude Opus 4.5",    AiProvider.ANTHROPIC, 200000, "Most powerful Claude model"));
        l.add(new ModelInfo("claude-sonnet-4-5",           "Claude Sonnet 4.5",  AiProvider.ANTHROPIC, 200000, "Balanced speed & intelligence"));
        l.add(new ModelInfo("claude-3-5-sonnet-20241022",  "Claude 3.5 Sonnet",  AiProvider.ANTHROPIC, 200000, "Best for coding tasks"));
        l.add(new ModelInfo("claude-3-5-haiku-20241022",   "Claude 3.5 Haiku",   AiProvider.ANTHROPIC, 200000, "Fastest Claude model"));
        l.add(new ModelInfo("claude-3-opus-20240229",      "Claude 3 Opus",      AiProvider.ANTHROPIC, 200000, "Most capable Claude 3"));
        return l;
    }
}
