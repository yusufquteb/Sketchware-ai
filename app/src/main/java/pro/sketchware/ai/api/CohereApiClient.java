package pro.sketchware.ai.api;

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

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;

/**
 * Cohere API client — uses Cohere's native chat v2 endpoint.
 * Base URL  : https://api.cohere.com
 * Free tier : 20 req/min, 1,000 req/month (shared quota)
 * API key   : https://dashboard.cohere.com/api-keys
 */
public class CohereApiClient extends AiApiClient {

    private static final String MODELS_URL = "https://api.cohere.com/v1/models";
    private static final String CHAT_URL   = "https://api.cohere.com/v2/chat";
    private static final MediaType JSON    = MediaType.get("application/json; charset=utf-8");

    public CohereApiClient(String apiKey) {
        super(apiKey, AiProvider.COHERE);
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        // Paginated
        List<ModelInfo> result = new ArrayList<>();
        String nextPageToken = null;
        do {
            String url = MODELS_URL + "?endpoint=chat&page_size=50"
                    + (nextPageToken != null ? "&page_token=" + nextPageToken : "");
            Request req = new Request.Builder()
                    .url(url).get()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .build();
            try (Response r = client.newCall(req).execute()) {
                if (!r.isSuccessful()) throw new IOException("Cohere HTTP " + r.code());
                ResponseBody body = r.body();
                if (body == null) break;
                JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
                JsonArray models = root.has("models") ? root.getAsJsonArray("models") : new JsonArray();
                for (JsonElement el : models) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    String id = str(obj, "name");
                    if (id == null || id.isEmpty()) continue;
                    // Skip deprecated
                    if (obj.has("is_deprecated") && obj.get("is_deprecated").getAsBoolean()) continue;
                    // Only chat models
                    boolean hasChat = false;
                    if (obj.has("endpoints")) {
                        for (JsonElement ep : obj.getAsJsonArray("endpoints")) {
                            if ("chat".equals(ep.getAsString())) { hasChat = true; break; }
                        }
                    }
                    if (!hasChat && obj.has("default_endpoints")) {
                        for (JsonElement ep : obj.getAsJsonArray("default_endpoints")) {
                            if ("chat".equals(ep.getAsString())) { hasChat = true; break; }
                        }
                    }
                    if (!hasChat) continue;
                    long ctx = 0;
                    if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
                        try { ctx = obj.get("context_length").getAsLong(); } catch (Exception ignored) {}
                    }
                    String name = str(obj, "display_name");
                    if (name == null || name.isEmpty()) name = toName(id);
                    result.add(new ModelInfo(id, name, AiProvider.COHERE, ctx,
                            "Cohere \u2014 " + name));
                }
                nextPageToken = root.has("next_page_token") && !root.get("next_page_token").isJsonNull()
                        ? root.get("next_page_token").getAsString() : null;
            }
        } while (nextPageToken != null && !nextPageToken.isEmpty());

        Collections.sort(result);
        return result.isEmpty() ? fallbackModels() : result;
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
                    ? modelId : "command-r-plus";

            // Build Cohere v2 chat request body (OpenAI-like but with "message" field)
            JsonObject body = new JsonObject();
            body.addProperty("model", effective);
            body.addProperty("stream", true);

            // Build messages array
            JsonArray msgs = new JsonArray();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                JsonObject sys = new JsonObject();
                sys.addProperty("role", "system");
                sys.addProperty("content", systemPrompt);
                msgs.add(sys);
            }
            for (ChatMessage msg : messages) {
                JsonObject m = new JsonObject();
                String role = msg.getRole();
                // Map "tool" role to "user" for Cohere compatibility
                if ("tool".equals(role)) role = "user";
                m.addProperty("role", role);
                String text = msg.getContent();
                m.addProperty("content", text != null ? text : "");
                msgs.add(m);
            }
            body.add("messages", msgs);

            Request.Builder builder = new Request.Builder()
                    .url(CHAT_URL)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            if (tag != null) builder.tag(tag);

            client.newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("Cohere request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Cohere: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Cohere empty body"); return; }
                    // Cohere v2 SSE uses "data:" lines with JSON — delta in text-generation-chunk
                    parseCohereStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Cohere build error: " + e.getMessage());
        }
    }

    private static void parseCohereStream(ResponseBody body, StreamingResponseHandler handler) {
        try {
            StringBuilder full = new StringBuilder();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(body.byteStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.equals("[DONE]")) break;
                try {
                    JsonObject event = JsonParser.parseString(data).getAsJsonObject();
                    String type = event.has("type") ? event.get("type").getAsString() : "";
                    if ("content-delta".equals(type)) {
                        // v2 streaming: delta.message.content[].text
                        if (event.has("delta")) {
                            JsonObject delta = event.getAsJsonObject("delta");
                            if (delta.has("message")) {
                                JsonObject msg = delta.getAsJsonObject("message");
                                if (msg.has("content")) {
                                    JsonArray contentArr = msg.getAsJsonArray("content");
                                    for (JsonElement c : contentArr) {
                                        if (c.isJsonObject() && c.getAsJsonObject().has("text")) {
                                            String chunk = c.getAsJsonObject().get("text").getAsString();
                                            full.append(chunk);
                                            handler.onChunk(chunk);
                                        }
                                    }
                                }
                            }
                        }
                    } else if ("message-end".equals(type)) {
                        break;
                    }
                } catch (Exception ignored) {}
            }
            handler.onComplete(full.toString());
        } catch (Exception e) {
            handler.onError("Cohere stream error: " + e.getMessage());
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static String toName(String id) {
        String s = id.replace("-", " ").replace("_", " ");
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("command-r-plus",    "Command R+",    AiProvider.COHERE, 128000, "Cohere \u2014 most capable model for complex tasks"));
        l.add(new ModelInfo("command-r",         "Command R",     AiProvider.COHERE, 128000, "Cohere \u2014 balanced model for RAG and tools"));
        l.add(new ModelInfo("command",           "Command",       AiProvider.COHERE,   4096, "Cohere \u2014 general purpose model"));
        l.add(new ModelInfo("command-light",     "Command Light", AiProvider.COHERE,   4096, "Cohere \u2014 faster, lighter model"));
        return l;
    }
}
