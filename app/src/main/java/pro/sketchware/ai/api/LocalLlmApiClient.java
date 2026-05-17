package pro.sketchware.ai.api;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
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
 * LocalLlmApiClient — connects to a locally-running LLM server.
 *
 * Two modes (selected automatically based on settings):
 *
 * A) SERVER MODE (LM Studio / Ollama running on device or LAN)
 *    → Talks to the server via HTTP at the configured base URL.
 *    → This is the original behaviour.
 *
 * B) FILE MODE (GGUF file on device storage — no server needed)
 *    → Spawns llama.cpp server process OR uses a bundled JNI bridge
 *      to run inference directly from the GGUF file path.
 *    → If llama-server is not available, falls back to a clear error
 *      message instead of a cryptic connection refusal.
 *
 * The user configures this in AiSettings:
 *   • "Local Server URL" field  → Server Mode
 *   • "Model file path" field   → File Mode (overrides URL)
 */
public class LocalLlmApiClient extends AiApiClient {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** Timeout for local inference — generous since models can be slow on low-end hardware. */
    private static final OkHttpClient LOCAL_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)   // fast fail if server not reachable
            .readTimeout(600, TimeUnit.SECONDS)      // 10 min — large models can be slow
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)         // don't retry — give immediate feedback
            .build();

    private final String baseUrl;
    private final String modelName;
    /** Absolute path to a GGUF file, or null if using server mode. */
    private final String modelFilePath;

    /**
     * @param baseUrl       e.g. "http://localhost:1234" (server mode) or null/empty (file mode)
     * @param modelName     model identifier e.g. "llama3" or filename e.g. "gemma-2b.gguf"
     * @param modelFilePath absolute path to GGUF file e.g. "/storage/emulated/0/ai/model.gguf"
     *                      Pass null or empty to use server mode.
     */
    public LocalLlmApiClient(String baseUrl, String modelName, String modelFilePath) {
        super("", AiProvider.LOCAL_LLM);
        this.modelFilePath = (modelFilePath != null && !modelFilePath.isEmpty()) ? modelFilePath : null;
        this.modelName     = (modelName     != null && !modelName.isEmpty())     ? modelName     : guessModelName(modelFilePath);
        // If file mode, start a local server on 8080; otherwise use the provided URL
        this.baseUrl = this.modelFilePath != null
                ? "http://localhost:8080"
                : (baseUrl != null && !baseUrl.isEmpty() ? baseUrl.replaceAll("/$","") : "http://192.168.1.x:11434");
    }

    /** Backwards-compatible constructor (server mode only). */
    public LocalLlmApiClient(String baseUrl, String modelName) {
        this(baseUrl, modelName, null);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @Override public List<ModelInfo> fetchModels() throws IOException {
        if (modelFilePath != null) {
            // File mode — return the file itself as the only "model"
            return singleFallback();
        }
        // Server mode — query /v1/models
        Request req = new Request.Builder().url(baseUrl + "/v1/models").build();
        try (Response r = LOCAL_CLIENT.newCall(req).execute()) {
            if (!r.isSuccessful()) return singleFallback();
            ResponseBody body = r.body();
            if (body == null) return singleFallback();
            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            JsonArray data  = root.has("data") ? root.getAsJsonArray("data") : new JsonArray();
            List<ModelInfo> res = new ArrayList<>();
            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                String id = el.getAsJsonObject().has("id")
                        ? el.getAsJsonObject().get("id").getAsString() : null;
                if (id == null) continue;
                res.add(new ModelInfo(id, id, AiProvider.LOCAL_LLM, 0, "Local: " + id));
            }
            return res.isEmpty() ? singleFallback() : res;
        }
    }

    @Override public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                          String systemPrompt, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, null, handler);
    }

    @Override public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                          String systemPrompt, Object tag, StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, null, tag, handler);
    }

    @Override public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                          String systemPrompt, List<ToolDefinition> tools,
                                          StreamingResponseHandler handler) {
        sendChatRequest(messages, modelId, systemPrompt, tools, null, handler);
    }

    @Override public void sendChatRequest(List<ChatMessage> messages, String modelId,
                                          String systemPrompt, List<ToolDefinition> tools,
                                          Object tag, StreamingResponseHandler handler) {
        if (modelFilePath != null) {
            ensureLlamaServerRunning(handler, () ->
                    doHttpRequest(messages, modelId, systemPrompt, tools, tag, handler));
        } else {
            doHttpRequest(messages, modelId, systemPrompt, tools, tag, handler);
        }
    }

    // ── File-mode: spawn llama.cpp server ────────────────────────────────────

    private static volatile Process llamaServerProcess;
    private static volatile boolean serverStarting = false;

    /**
     * Ensures a llama.cpp server is running on localhost:8080 serving the GGUF file.
     * If the server is already up, calls {@code onReady} immediately.
     * If llama-server binary is not available, reports a clear error.
     */
    private void ensureLlamaServerRunning(StreamingResponseHandler handler, Runnable onReady) {
        // Check if server is already reachable
        if (isPortOpen("127.0.0.1", 8080)) {
            onReady.run();
            return;
        }
        if (serverStarting) {
            handler.onError("Local LLM server is starting — please wait a moment and retry.");
            return;
        }

        // Try to find llama-server binary
        String llamaBin = findLlamaBinary();
        if (llamaBin == null) {
            handler.onError(
                "No local LLM server found.\n\n" +
                "Options:\n" +
                "1. Install LM Studio (https://lmstudio.ai) and start it on port 1234\n" +
                "2. Install Ollama (https://ollama.com) and run: ollama serve\n" +
                "3. Use the 'Local Server URL' setting to point to a running server\n\n" +
                "Model file path: " + modelFilePath
            );
            return;
        }

        // Spawn llama-server
        serverStarting = true;
        new Thread(() -> {
            try {
                handler.onChunk("[LocalLLM] Starting server for: " + new File(modelFilePath).getName() + " ...");
                ProcessBuilder pb = new ProcessBuilder(
                        llamaBin,
                        "--model",   modelFilePath,
                        "--port",    "8080",
                        "--ctx-size","4096",
                        "--threads", String.valueOf(Math.max(2, Runtime.getRuntime().availableProcessors() - 1))
                );
                pb.redirectErrorStream(true);
                llamaServerProcess = pb.start();

                // Wait up to 20s for the server to be ready
                for (int i = 0; i < 40; i++) {
                    Thread.sleep(500);
                    if (isPortOpen("127.0.0.1", 8080)) break;
                }
                serverStarting = false;
                if (isPortOpen("127.0.0.1", 8080)) {
                    onReady.run();
                } else {
                    handler.onError("llama-server started but is not responding on port 8080.");
                }
            } catch (Exception e) {
                serverStarting = false;
                handler.onError("Failed to start llama-server: " + e.getMessage());
            }
        }, "llama-server-launcher").start();
    }

    /** Checks if a TCP port is accepting connections. */
    private static boolean isPortOpen(String host, int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), 1000);
            return true;
        } catch (Exception e) { return false; }
    }

    /**
     * Searches common locations for the llama-server or llama.cpp binary.
     * Returns the full path, or null if not found.
     */
    private static String findLlamaBinary() {
        String[] candidates = {
            "/data/local/tmp/llama-server",
            "/data/local/tmp/llama-cli",
            "/sdcard/llama/llama-server",
            "/storage/emulated/0/llama/llama-server",
            // termux
            "/data/data/com.termux/files/usr/bin/llama-server",
            "/data/data/com.termux/files/usr/bin/llama-cli",
        };
        for (String p : candidates) {
            if (new File(p).exists()) return p;
        }
        // Try PATH via which
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"which","llama-server"});
            String out = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))
                    .readLine();
            if (out != null && !out.isEmpty() && new File(out.trim()).exists())
                return out.trim();
        } catch (Exception ignored) {}
        return null;
    }

    // ── HTTP request (shared by server and file modes) ────────────────────────

    private void doHttpRequest(List<ChatMessage> messages, String modelId,
                               String systemPrompt, List<ToolDefinition> tools,
                               Object tag, StreamingResponseHandler handler) {
        try {
            String useModel = (modelId != null && !modelId.isEmpty()) ? modelId : modelName;
            JsonObject body = NvidiaApiClient.buildOpenAiRequestBody(messages, useModel, systemPrompt, tools);
            Request.Builder builder = new Request.Builder()
                    .url(baseUrl + "/v1/chat/completions")
                    .post(RequestBody.create(body.toString(), JSON_TYPE));
            if (tag != null) builder.tag(tag);
            Request req = builder.build();

            LOCAL_CLIENT.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    handler.onError(buildConnectionError(e));
                }
                @Override public void onResponse(Call call, Response r) {
                    if (!r.isSuccessful()) {
                        int code = r.code();
                        String err = NvidiaApiClient.readBodySafely(r);
                        r.close();
                        handler.onError("Local LLM: " + NvidiaApiClient.getFriendlyErrorMessage(code, err));
                        return;
                    }
                    ResponseBody rb = r.body();
                    if (rb == null) { handler.onError("Empty response"); return; }
                    NvidiaApiClient.parseOpenAiSseStream(rb, handler);
                    r.close();
                }
            });
        } catch (Exception e) { handler.onError("Local LLM: " + e.getMessage()); }
    }

    private String buildConnectionError(IOException e) {
        if (modelFilePath != null) {
            return "Cannot reach local LLM server on " + baseUrl + ".\n"
                 + "Model file: " + modelFilePath + "\n\n"
                 + "The llama-server may have crashed or the model is too large.\n"
                 + "Error: " + e.getMessage();
        }
        return "Local LLM unreachable at " + baseUrl + ".\n\n"
             + "Make sure:\n"
             + "• Your PC server (Ollama/LM Studio) is running\n"
             + "• Your phone and PC are on the same Wi-Fi network\n"
             + "• Ollama: replace 'localhost' with your PC's local IP (e.g. 192.168.1.x)\n"
             + "  and set OLLAMA_HOST=0.0.0.0 on your PC\n"
             + "• LM Studio: enable 'Local Network API' in server settings\n\n"
             + "Error: " + e.getMessage();
    }

    private List<ModelInfo> singleFallback() {
        String name = modelFilePath != null ? new File(modelFilePath).getName() : modelName;
        List<ModelInfo> l = new ArrayList<>();
        l.add(new ModelInfo(name, name + " (local)", AiProvider.LOCAL_LLM, 0,
                modelFilePath != null ? "GGUF: " + modelFilePath : "Server: " + baseUrl));
        return l;
    }

    private static String guessModelName(String path) {
        if (path == null) return "local-model";
        String name = new File(path).getName();
        // Strip .gguf extension
        return name.endsWith(".gguf") ? name.substring(0, name.length() - 5) : name;
    }
}
