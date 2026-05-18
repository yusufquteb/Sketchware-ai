package pro.sketchware.ai.api;

import android.content.Context;
import android.preference.PreferenceManager;

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
 * Cloudflare Workers AI API client.
 * Base URL  : https://api.cloudflare.com/client/v4/accounts/{account_id}/ai
 * Free tier : 10,000 neurons/day
 * API key   : https://dash.cloudflare.com/profile/api-tokens
 * Account ID stored in prefs: "cloudflare_account_id"
 * Supports  : Llama 3.3/3.2/3.1, Mistral, Gemma 3, DeepSeek and more
 *
 * NOTE: The API key should be "CF_API_KEY|CF_ACCOUNT_ID" separated by pipe,
 *       or just the API key if the account ID is stored in preferences.
 */
public class CloudflareApiClient extends AiApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String BASE    = "https://api.cloudflare.com/client/v4/accounts/";

    private final String accountId;
    private final String cfApiKey;

    public CloudflareApiClient(String apiKey) {
        super(apiKey, AiProvider.CLOUDFLARE);
        // Support "KEY|ACCOUNT_ID" format or just the key
        if (apiKey != null && apiKey.contains("|")) {
            String[] parts = apiKey.split("\\|", 2);
            this.cfApiKey   = parts[0].trim();
            this.accountId  = parts[1].trim();
        } else {
            this.cfApiKey  = apiKey != null ? apiKey : "";
            this.accountId = ""; // will fail gracefully
        }
    }

    @Override
    public List<ModelInfo> fetchModels() throws IOException {
        if (accountId.isEmpty()) {
            return fallbackModels();
        }
        String url = BASE + accountId + "/ai/models/search?search=Text+Generation&page=1&per_page=200";
        Request req = new Request.Builder()
                .url(url).get()
                .header("Authorization", "Bearer " + cfApiKey)
                .header("Content-Type", "application/json")
                .build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) return fallbackModels();
            ResponseBody body = r.body();
            if (body == null) return fallbackModels();
            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray result = root.has("result") ? root.getAsJsonArray("result") : new JsonArray();
            List<ModelInfo> models = new ArrayList<>();
            for (JsonElement el : result) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                String id = str(obj, "name");
                if (id == null || id.isEmpty()) continue;
                String lo = id.toLowerCase(Locale.ROOT);
                if (lo.contains("embed") || lo.contains("image") || lo.contains("flux")
                        || lo.contains("stable-diff") || lo.contains("sdxl")
                        || lo.contains("guard") || lo.contains("vision-only")) continue;
                String name = str(obj, "display_name");
                if (name == null || name.isEmpty()) name = toName(id);
                models.add(new ModelInfo(id, name, AiProvider.CLOUDFLARE, 0,
                        "Cloudflare \u2014 " + name + " (10k neurons/day free)"));
            }
            Collections.sort(models);
            return models.isEmpty() ? fallbackModels() : models;
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
        if (accountId.isEmpty()) {
            handler.onError("Cloudflare: Account ID missing. Use format API_KEY|ACCOUNT_ID");
            return;
        }
        try {
            String effective = (modelId != null && !modelId.isEmpty())
                    ? modelId : "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
            String chatUrl = BASE + accountId + "/ai/run/" + effective;

            // Cloudflare uses standard OpenAI-like body but streaming works slightly differently
            JsonObject body = new JsonObject();
            body.addProperty("stream", true);
            JsonArray msgs = new JsonArray();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                JsonObject sys = new JsonObject();
                sys.addProperty("role", "system");
                sys.addProperty("content", systemPrompt);
                msgs.add(sys);
            }
            for (ChatMessage msg : messages) {
                JsonObject m = new JsonObject();
                m.addProperty("role", msg.getRole());
                String text = msg.getContent();
                m.addProperty("content", text != null ? text : "");
                msgs.add(m);
            }
            body.add("messages", msgs);

            Request.Builder builder = new Request.Builder()
                    .url(chatUrl)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Authorization", "Bearer " + cfApiKey)
                    .header("Content-Type", "application/json");
            if (tag != null) builder.tag(tag);

            client.newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError("Cloudflare request failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        String err = AiErrorHelper.readBodySafely(response);
                        response.close();
                        handler.onError("Cloudflare: " + AiErrorHelper.getFriendlyMessage(code, err));
                        return;
                    }
                    ResponseBody rb = response.body();
                    if (rb == null) { handler.onError("Cloudflare empty body"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    response.close();
                }
            });
        } catch (Exception e) {
            handler.onError("Cloudflare build error: " + e.getMessage());
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static String toName(String id) {
        // e.g. "@cf/meta/llama-3.3-70b-instruct-fp8-fast" -> "Llama 3.3 70b instruct fp8 fast"
        String s = id.startsWith("@") && id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
        s = s.replace("-", " ").replace("_", " ");
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<ModelInfo> fallbackModels() {
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo("@cf/meta/llama-3.3-70b-instruct-fp8-fast", "Llama 3.3 70B Instruct (FP8)", AiProvider.CLOUDFLARE, 128000, "Cloudflare \u2014 Llama 3.3 70B fast"));
        l.add(new ModelInfo("@cf/meta/llama-3.2-3b-instruct",           "Llama 3.2 3B Instruct",         AiProvider.CLOUDFLARE,  16384, "Cloudflare \u2014 Llama 3.2 3B fast"));
        l.add(new ModelInfo("@cf/meta/llama-3.1-8b-instruct",           "Llama 3.1 8B Instruct",         AiProvider.CLOUDFLARE,  16384, "Cloudflare \u2014 Llama 3.1 8B"));
        l.add(new ModelInfo("@cf/qwen/qwq-32b",                         "Qwen QwQ 32B",                  AiProvider.CLOUDFLARE,  32768, "Cloudflare \u2014 Qwen QwQ 32B reasoning"));
        l.add(new ModelInfo("@cf/deepseek-ai/deepseek-r1-distill-qwen-32b", "DeepSeek R1 Distill Qwen 32B", AiProvider.CLOUDFLARE, 32768, "Cloudflare \u2014 DeepSeek R1 Distill"));
        l.add(new ModelInfo("@cf/mistralai/mistral-small-3.1-24b-instruct", "Mistral Small 3.1 24B", AiProvider.CLOUDFLARE, 131072, "Cloudflare \u2014 Mistral Small 3.1"));
        l.add(new ModelInfo("@cf/google/gemma-3-12b-it",                "Gemma 3 12B Instruct",           AiProvider.CLOUDFLARE, 131072, "Cloudflare \u2014 Gemma 3 12B"));
        return l;
    }
}
