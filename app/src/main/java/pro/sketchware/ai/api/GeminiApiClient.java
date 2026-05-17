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
import java.util.UUID;

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
 * AI API client for the Google Gemini (Generative Language) API.
 *
 * <p>Uses the v1beta endpoint with SSE streaming for chat completions
 * and API-key query-parameter authentication.
 */
public class GeminiApiClient extends AiApiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public GeminiApiClient(String apiKey) {
        super(apiKey, AiProvider.GEMINI);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        String url = BASE_URL + "/v1beta/models?key=" + apiKey;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Gemini fetchModels failed: HTTP " + response.code()
                        + " " + readBodySafely(response));
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Gemini fetchModels returned empty body");
            }

            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray modelsArray = root.has("models") ? root.getAsJsonArray("models") : new JsonArray();

            List<ModelInfo> result = new ArrayList<>();
            for (JsonElement elem : modelsArray) {
                JsonObject model = elem.getAsJsonObject();
                if (!supportsGenerateContent(model)) {
                    continue;
                }

                String name = getStringOrDefault(model, "name", "");
                String displayName = getStringOrDefault(model, "displayName", name);
                String description = getStringOrDefault(model, "description", "");
                long inputTokenLimit = model.has("inputTokenLimit")
                        ? model.get("inputTokenLimit").getAsLong() : 0L;

                // Skip image/audio/embedding/non-chat Gemini models
                {
                    String _lo = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
                    if (_lo.contains("embed") || _lo.contains("tts") || _lo.contains("speech")
                        || _lo.contains("audio") || _lo.contains("vision-gen")
                        || _lo.contains("image") || _lo.contains("aqa")) continue;
                }
                result.add(new ModelInfo(name, displayName, AiProvider.GEMINI, inputTokenLimit, description));
            }

            // Sort models alphabetically (A-Z)
            java.util.Collections.sort(result);

            return result;
        }
    }

    private boolean supportsGenerateContent(JsonObject model) {
        if (!model.has("supportedGenerationMethods")) {
            return false;
        }
        JsonArray methods = model.getAsJsonArray("supportedGenerationMethods");
        for (JsonElement method : methods) {
            if ("generateContent".equals(method.getAsString())) {
                return true;
            }
        }
        return false;
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
            String url = BASE_URL + "/v1beta/" + modelId + ":streamGenerateContent?alt=sse&key=" + apiKey;
            JsonObject requestBody = buildRequestBody(messages, systemPrompt, tools);

            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), JSON));
            
            if (tag != null) builder.tag(tag);
            Request request = builder.build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError("Gemini request failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String errorBody = readBodySafely(response);
                        response.close();
                        handler.onError("Gemini: " + AiErrorHelper.getFriendlyMessage(code, errorBody));
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        handler.onError("Gemini returned empty response body");
                        return;
                    }

                    parseGeminiSseStream(body, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Failed to build Gemini request: " + e.getMessage());
        }
    }

    private JsonObject buildRequestBody(List<ChatMessage> messages, String systemPrompt,
                                        List<ToolDefinition> tools) {
        JsonObject body = new JsonObject();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", systemPrompt);
            parts.add(textPart);
            systemInstruction.add("parts", parts);
            body.add("systemInstruction", systemInstruction);
        }

        JsonArray contents = new JsonArray();
        for (ChatMessage message : messages) {
            String role = message.getRole();
            String msgContent = message.getContent();

            // ✅ FIX: Gemini does not support 'system' role in contents array.
            // Map system feedback messages (e.g. auto-fix build errors) to 'user' role
            // with a [SYSTEM NOTE] prefix so they reach the model and are not silently dropped.
            if ("system".equals(role)) {
                role = "user";
                msgContent = "[SYSTEM NOTE]: " + (msgContent != null ? msgContent : "");
            }

            JsonObject content = new JsonObject();
            content.addProperty("role", mapRoleToGemini(role));
            JsonArray parts = new JsonArray();

            if (msgContent != null && !msgContent.isEmpty()) {
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", msgContent);
                parts.add(textPart);
            }

            if ("tool".equals(message.getRole()) && message.getToolCallId() != null) {
                JsonObject functionResponse = new JsonObject();
                String functionName = message.getToolName();
                if (functionName == null || functionName.isEmpty()) {
                    functionName = message.getToolCallId();
                }
                functionResponse.addProperty("name", functionName);
                JsonObject responseContent = new JsonObject();
                responseContent.addProperty("result", message.getContent() != null ? message.getContent() : "");
                functionResponse.add("response", responseContent);

                JsonObject functionResponsePart = new JsonObject();
                functionResponsePart.add("functionResponse", functionResponse);
                parts.add(functionResponsePart);
            }

            if ("assistant".equals(message.getRole()) && message.getToolCalls() != null) {
                for (ToolCall tc : message.getToolCalls()) {
                    JsonObject functionCall = new JsonObject();
                    functionCall.addProperty("name", tc.getName());
                    try {
                        JsonObject args = JsonParser.parseString(
                                tc.getArguments() != null ? tc.getArguments() : "{}").getAsJsonObject();
                        functionCall.add("args", args);
                    } catch (Exception e) {
                        functionCall.add("args", new JsonObject());
                    }

                    JsonObject functionCallPart = new JsonObject();
                    functionCallPart.add("functionCall", functionCall);
                    if (tc.getThoughtSignature() != null && !tc.getThoughtSignature().isEmpty()) {
                        functionCallPart.addProperty("thoughtSignature", tc.getThoughtSignature());
                    }
                    parts.add(functionCallPart);
                }
            }

            if (parts.size() > 0) {
                content.add("parts", parts);
                contents.add(content);
            }
        }
        body.add("contents", contents);

        if (tools != null && !tools.isEmpty()) {
            body.add("tools", buildToolsPayload(tools));
            JsonObject toolConfig = new JsonObject();
            JsonObject functionCallingConfig = new JsonObject();
            functionCallingConfig.addProperty("mode", "AUTO");
            toolConfig.add("functionCallingConfig", functionCallingConfig);
            body.add("toolConfig", toolConfig);
        }

        return body;
    }

    private String mapRoleToGemini(String role) {
        switch (role) {
            case "assistant":
                return "model";
            case "tool":
                return "user";
            default:
                return "user";
        }
    }

    public JsonArray buildToolsPayload(List<ToolDefinition> tools) {
        JsonArray declarations = new JsonArray();
        for (ToolDefinition tool : tools) {
            declarations.add(tool.toGeminiJson());
        }

        JsonObject toolsObject = new JsonObject();
        toolsObject.add("functionDeclarations", declarations);

        JsonArray toolsArray = new JsonArray();
        toolsArray.add(toolsObject);
        return toolsArray;
    }

    private void parseGeminiSseStream(ResponseBody body, StreamingResponseHandler handler) {
        StringBuilder fullResponse = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }

                String data = line.substring(6).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }

                try {
                    JsonObject event = JsonParser.parseString(data).getAsJsonObject();
                    processGeminiEvent(event, fullResponse, handler);
                } catch (Exception ignored) {
                }
            }

            handler.onComplete(fullResponse.toString());
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Software caused connection abort") || msg.contains("Socket closed"))) {
                if (fullResponse.length() > 0) {
                    handler.onComplete(fullResponse.toString());
                } else {
                    handler.onError("Connection lost. Please try again.");
                }
            } else {
                handler.onError("Error reading Gemini stream: " + (msg != null ? msg : "Unknown error"));
            }
        }
    }

    private void processGeminiEvent(JsonObject event, StringBuilder fullResponse,
                                    StreamingResponseHandler handler) {
        if (!event.has("candidates")) {
            return;
        }

        JsonArray candidates = event.getAsJsonArray("candidates");
        if (candidates.size() == 0) {
            return;
        }

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        if (!candidate.has("content")) {
            return;
        }

        JsonObject content = candidate.getAsJsonObject("content");
        if (!content.has("parts")) {
            return;
        }

        JsonArray parts = content.getAsJsonArray("parts");
        for (JsonElement partElem : parts) {
            JsonObject part = partElem.getAsJsonObject();

            if (part.has("text")) {
                String text = part.get("text").getAsString();
                fullResponse.append(text);
                handler.onChunk(text);
            }

            if (part.has("functionCall")) {
                JsonObject functionCall = part.getAsJsonObject("functionCall");
                String name = getStringOrDefault(functionCall, "name", "unknown");
                JsonObject args = functionCall.has("args")
                        ? functionCall.getAsJsonObject("args") : new JsonObject();
                String thoughtSignature = getStringOrDefault(part, "thoughtSignature",
                        getStringOrDefault(part, "thought_signature", null));

                String callId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                ToolCall toolCall = new ToolCall(callId, name, args.toString(), thoughtSignature);
                handler.onToolCall(toolCall);
            }
        }
    }

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
