package pro.sketchware.ai.api;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;

/**
 * Abstract base class for AI API clients. Each provider extends this class and
 * implements provider-specific request/response handling.
 *
 * <p>Authentication is handled differently per provider:
 * <ul>
 *   <li>Gemini: API key as {@code ?key=} query parameter</li>
 *   <li>Anthropic: {@code x-api-key} header</li>
 *   <li>All others: {@code Authorization: Bearer <key>} header</li>
 * </ul>
 *
 * <p>✅ IMPROVEMENT: OkHttpClient is now a singleton shared across all client instances.
 * Previously every subclass instantiation created a new OkHttpClient with a separate
 * thread-pool, connection-pool, and DNS cache — wasting memory and breaking connection
 * reuse. The singleton is lazily initialised and safe for concurrent access.
 */
public abstract class AiApiClient {

    // ── Shared singleton OkHttpClient ────────────────────────────────────────
    //
    // OkHttp's docs explicitly recommend sharing a single instance across the app.
    // The client is thread-safe and multiplexes connections internally.
    // Individual requests can still be cancelled via Call.cancel() or
    // client.dispatcher().cancelAll() without affecting other in-flight requests.

    private static final long CONNECT_TIMEOUT_SECONDS = 30;
    private static final long WRITE_TIMEOUT_SECONDS   = 60;

    /** Lazily initialised singleton. Guarded by the class lock. */
    private static volatile OkHttpClient sharedClient;

    private static OkHttpClient getSharedClient() {
        if (sharedClient == null) {
            synchronized (AiApiClient.class) {
                if (sharedClient == null) {
                    sharedClient = new OkHttpClient.Builder()
                            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(0, TimeUnit.SECONDS)   // no read timeout — stream can be slow between chunks
                            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return sharedClient;
    }

    // ── Instance state ───────────────────────────────────────────────────────

    /** The shared HTTP client. Subclasses use {@code client.newCall(request).enqueue(...)}. */
    protected final OkHttpClient client;
    protected final String apiKey;
    protected final AiProvider provider;

    /**
     * User-configurable generation parameters (from {@link pro.sketchware.ai.storage.AiPreferences}),
     * applied by {@link AiClientFactory#createClient} right after construction.
     *
     * <p>0 means "no user override" — subclasses fall back to their own provider-tuned default
     * when building the request body. A positive value means the user explicitly configured it
     * (via the "AI Performance Profiles" screen) and it must be sent to the provider.
     */
    protected volatile float userTemperature = 0f;
    protected volatile int userMaxTokens = 0;

    /**
     * Applies user-configured generation parameters to this client instance.
     * Called once by {@link AiClientFactory#createClient} using
     * {@link pro.sketchware.ai.storage.AiPreferences#getTemperature()} and
     * {@link pro.sketchware.ai.storage.AiPreferences#getMaxTokens()}.
     *
     * @param temperature 0 = no override (use provider default); otherwise sent explicitly
     * @param maxTokens   0 = no override (use provider default); otherwise sent explicitly
     */
    public void setGenerationParams(float temperature, int maxTokens) {
        this.userTemperature = temperature;
        this.userMaxTokens = maxTokens;
    }

    /**
     * Constructs a new API client.
     *
     * @param apiKey   the API key for authentication (may be empty for no-auth providers)
     * @param provider the AI provider this client connects to
     */
    protected AiApiClient(String apiKey, AiProvider provider) {
        this.apiKey   = apiKey;
        this.provider = provider;
        this.client   = getSharedClient();
    }

    // ── Abstract API ─────────────────────────────────────────────────────────

    /**
     * Fetches the list of available models from the provider.
     *
     * @return a list of available models
     * @throws IOException if a network or parsing error occurs
     */
    public abstract List<ModelInfo> fetchModels() throws IOException;

    /**
     * Sends a streaming chat request (no tools).
     *
     * @param messages     the conversation history
     * @param modelId      the model identifier to use
     * @param systemPrompt the system instruction, or null if none
     * @param handler      callback for streaming events
     */
    public abstract void sendChatRequest(List<ChatMessage> messages, String modelId,
                                         String systemPrompt, StreamingResponseHandler handler);

    public abstract void sendChatRequest(List<ChatMessage> messages, String modelId,
                                         String systemPrompt, Object tag, StreamingResponseHandler handler);

    /**
     * Sends a streaming chat request with tool definitions.
     *
     * @param messages     the conversation history
     * @param modelId      the model identifier to use
     * @param systemPrompt the system instruction, or null if none
     * @param tools        the list of tool definitions to include
     * @param handler      callback for streaming events
     */
    public abstract void sendChatRequest(List<ChatMessage> messages, String modelId,
                                         String systemPrompt, List<ToolDefinition> tools,
                                         StreamingResponseHandler handler);

    public abstract void sendChatRequest(List<ChatMessage> messages, String modelId,
                                         String systemPrompt, List<ToolDefinition> tools,
                                         Object tag, StreamingResponseHandler handler);

    // ── Helpers for subclasses ────────────────────────────────────────────────

    /**
     * Adds a {@code Authorization: Bearer <apiKey>} header to the request builder.
     * Used by NVIDIA, OpenRouter, Groq, OpenAI, DeepSeek, xAI, Paxsenix.
     */
    protected Request.Builder addBearerAuth(Request.Builder builder) {
        return builder.header("Authorization", "Bearer " + apiKey);
    }

    /** Returns the full URL for the models endpoint. */
    protected String getModelsUrl() {
        return provider.getBaseUrl() + provider.getModelsEndpoint();
    }

    /** Returns the full URL for the chat completions endpoint. */
    protected String getChatUrl() {
        return provider.getBaseUrl() + provider.getChatEndpoint();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Cancels all in-flight requests made by this client instance.
     *
     * <p>Because {@code client} is now the shared singleton, this cancels ALL
     * dispatcher requests — which is the desired behaviour (one agent session at a time).
     */
    public void cancelAll() {
        client.dispatcher().cancelAll();
    }

    /**
     * Cancels in-flight requests tagged with a specific object.
     */
    public void cancelByTag(Object tag) {
        if (tag == null) return;
        for (okhttp3.Call call : client.dispatcher().queuedCalls()) {
            if (tag.equals(call.request().tag())) call.cancel();
        }
        for (okhttp3.Call call : client.dispatcher().runningCalls()) {
            if (tag.equals(call.request().tag())) call.cancel();
        }
    }

    /**
     * Cancels in-flight requests. Does NOT shut down the shared executor or connection pool,
     * since those are shared across all client instances.
     */
    public void shutdown() {
        cancelAll();
        // Note: we intentionally do NOT call client.dispatcher().executorService().shutdown()
        // or client.connectionPool().evictAll() here because the client is shared.
        // The singleton will be GC'd with the process.
    }
}
