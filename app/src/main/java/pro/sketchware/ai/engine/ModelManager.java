// nikit overhaul — Task 3 — 2026-05
package pro.sketchware.ai.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * ModelManager — dynamic fallback model system.
 *
 * <p>Models are NOT hardcoded. The user defines their active model list
 * (ACTIVE_MODELS) in AI Settings. At runtime, ModelManager tries each model in
 * order until one succeeds. If all fail the request is failed cleanly.
 *
 * <p>All heavy work must run on a background thread (blocks until completion).
 */
public final class ModelManager {

    private static final String TAG = "ModelManager";

    /** Prefs key for the user's ordered active-model JSON list. */
    public static final String KEY_ACTIVE_MODELS = "active_model_list";

    // ── ActiveModel entry ──────────────────────────────────────────────────────

    /**
     * Entry in the ACTIVE_MODELS list.
     * Stored as JSON: [{"provider":"GROQ","modelId":"llama-3.3-70b-versatile"}, …]
     */
    public static class ActiveModel {
        public String provider; // AiProvider.name()
        public String modelId;

        public ActiveModel() {}

        public ActiveModel(AiProvider p, String modelId) {
            this.provider = p.name();
            this.modelId  = modelId;
        }

        public AiProvider resolveProvider() {
            if (provider == null) return null;
            try { return AiProvider.valueOf(provider); }
            catch (IllegalArgumentException e) { return null; }
        }
    }

    // ── FallbackCallback ───────────────────────────────────────────────────────

    /**
     * Callback for a model-fallback execution attempt.
     * Note: onToolCall here passes the parsed name/args as strings because
     * AIEngine layout tools do not use tool-calling — they are one-shot text responses.
     */
    public interface FallbackCallback {
        /** Called when a model responds successfully. */
        void onSuccess(String modelId, AiProvider provider);
        /** Called after ALL models have been tried and all failed. */
        void onAllFailed(String lastError);
        /** Forwarded streaming text chunk. */
        void onStreamChunk(String chunk);
        /** Forwarded tool-call: name and raw arguments JSON string. */
        void onToolCall(String name, String argsJson);
        /** A single model failed; willRetry indicates another model will be tried. */
        void onError(String error, boolean willRetry);
    }

    // ── Task 3: FailoverState ─────────────────────────────────────────────────

    /** Snapshot of the failover state at the moment a provider switch occurs. */
    public static class FailoverState {
        public final int    currentIndex;
        public final int    attemptCount;
        public final String lastFailReason;
        public final long   elapsedMs;
        public final int    totalModels;

        public FailoverState(int idx, int attempts, String reason, long startMs, int total) {
            this.currentIndex   = idx;
            this.attemptCount   = attempts;
            this.lastFailReason = reason;
            this.elapsedMs      = System.currentTimeMillis() - startMs;
            this.totalModels    = total;
        }
    }

    // ── Task 3: FailoverStateListener ─────────────────────────────────────────

    /** Notified (main thread) each time the engine switches to the next provider. */
    public interface FailoverStateListener {
        void onProviderSwitching(FailoverState state, AiProvider next, String nextModelId);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context       context;
    private final AiPreferences prefs;
    private final Gson          gson       = new Gson();
    private final Handler       mainHandler = new Handler(Looper.getMainLooper());

    /** Shared wait-lock — allows cancel() to unblock the current request immediately. */
    private final Object        activeLock  = new Object();

    /** Set to true to abort the current executeWithFallback call. Reset at start of each call. */
    private final AtomicBoolean cancelFlag  = new AtomicBoolean(false);

    /** Optional listener notified when the engine switches provider. */
    private volatile FailoverStateListener failoverListener;

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs   = AiPreferences.getInstance(context);
    }

    // ── Task 3: Cancel + listener ─────────────────────────────────────────────

    public void setFailoverListener(FailoverStateListener l) {
        this.failoverListener = l;
    }

    /**
     * Cancels the current in-flight executeWithFallback call.
     * Thread-safe. Safe to call even if no request is running.
     */
    public void cancel() {
        cancelFlag.set(true);
        synchronized (activeLock) { activeLock.notifyAll(); }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Executes a chat request using ACTIVE_MODELS with automatic fallback.
     * Tries each model in sequence; stops at first success.
     *
     * <p><b>Must be called from a background thread — this method blocks.</b>
     *
     * @param messages     conversation history
     * @param systemPrompt system instruction (may be null)
     * @param tools        tool definitions (may be null for one-shot requests)
     * @param callback     result/streaming callback
     */
    public void executeWithFallback(
            List<ChatMessage> messages,
            String systemPrompt,
            List<pro.sketchware.ai.api.ToolDefinition> tools,
            FallbackCallback callback) {

        // Reset cancel flag for this new execution
        cancelFlag.set(false);

        List<ActiveModel> models = getActiveModels();
        if (models.isEmpty()) {
            callback.onAllFailed("No active models configured. "
                    + "Go to AI Settings → Active Models and add at least one model.");
            return;
        }

        // Guard: onAllFailed / onSuccess called at most once per execution
        final boolean[] alreadyResolved = { false };

        String lastError    = "Unknown error";
        int    attemptCount = 0;
        long   startMs      = System.currentTimeMillis();

        for (int i = 0; i < models.size(); i++) {
            // Cancellation check before each attempt
            if (cancelFlag.get()) {
                if (!alreadyResolved[0]) { alreadyResolved[0] = true; callback.onAllFailed("Cancelled by user"); }
                return;
            }

            ActiveModel am       = models.get(i);
            AiProvider  provider = am.resolveProvider();
            if (provider == null) { Log.w(TAG, "Skipping unknown provider: " + am.provider); continue; }

            String apiKey = prefs.getApiKey(provider);
            if ((apiKey == null || apiKey.isEmpty()) && !isNoKeyProvider(provider)) { Log.d(TAG, "Skipping " + provider + " — no API key"); continue; }
            if (!prefs.isProviderEnabled(provider)) { Log.d(TAG, "Skipping " + provider + " — disabled"); continue; }
            if (apiKey == null) apiKey = "";

            AiApiClient client = AiClientFactory.createClient(context, provider, apiKey);
            if (client == null) { Log.w(TAG, "No client for provider: " + provider); continue; }

            attemptCount++;

            // Notify listener of provider switch (skip first attempt)
            if (failoverListener != null && i > 0) {
                final FailoverState fs = new FailoverState(i, attemptCount, lastError, startMs, models.size());
                final AiProvider pF = provider; final String mF = am.modelId;
                mainHandler.post(() -> failoverListener.onProviderSwitching(fs, pF, mF));
            }

            if (pro.sketchware.BuildConfig.DEBUG && i > 0) {
                Log.d("PulseEngine", "[Pulse] Switching → " + provider.getDisplayName()
                        + "/" + am.modelId + " (attempt " + attemptCount + ")");
            }

            // ── Try this model with retry for network/timeout errors ────────
            boolean modelSucceeded = false;
            final int MAX_RETRIES = 2;
            for (int retry = 0; retry <= MAX_RETRIES && !modelSucceeded; retry++) {
                if (cancelFlag.get()) {
                    if (!alreadyResolved[0]) { alreadyResolved[0] = true; callback.onAllFailed("Cancelled by user"); }
                    return;
                }
                if (retry > 0) {
                    // Exponential backoff: 2s, 4s
                    long backoffMs = 2000L << (retry - 1);
                    Log.d(TAG, "Retry " + retry + " for " + provider + "/" + am.modelId + " after " + backoffMs + "ms");
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        if (!alreadyResolved[0]) { alreadyResolved[0] = true; callback.onAllFailed("Interrupted"); }
                        return;
                    }
                }

                final boolean[] succeeded   = { false };
                final String[]  errorHolder = { null };

                StreamingResponseHandler handler = new StreamingResponseHandler() {
                    @Override public void onChunk(String textDelta)        { callback.onStreamChunk(textDelta); }
                    @Override public void onToolCall(ToolCall toolCall) {
                        String name    = toolCall != null ? toolCall.getName()      : "";
                        String argsJson = toolCall != null ? toolCall.getArguments() : "{}";
                        callback.onToolCall(name, argsJson);
                    }
                    @Override public void onComplete(String fullResponse)  { succeeded[0] = true;  synchronized (activeLock) { activeLock.notifyAll(); } }
                    @Override public void onError(String error)            { errorHolder[0] = error; synchronized (activeLock) { activeLock.notifyAll(); } }
                };

                Log.d(TAG, "Trying " + provider + " / " + am.modelId + (retry > 0 ? " (retry " + retry + ")" : ""));
                if (tools != null && !tools.isEmpty()) {
                    client.sendChatRequest(messages, am.modelId, systemPrompt, tools, handler);
                } else {
                    client.sendChatRequest(messages, am.modelId, systemPrompt, handler);
                }

                // Block until complete, timeout, or cancelled
                try {
                    synchronized (activeLock) {
                        long timeoutMs = prefs.getRequestTimeoutSecs() * 1000L;
                        long deadline = System.currentTimeMillis() + timeoutMs;
                        while (!succeeded[0] && errorHolder[0] == null) {
                            if (cancelFlag.get()) {
                                if (!alreadyResolved[0]) { alreadyResolved[0] = true; callback.onAllFailed("Cancelled by user"); }
                                return;
                            }
                            long remaining = deadline - System.currentTimeMillis();
                            if (remaining <= 0) { errorHolder[0] = "Timeout"; break; }
                            try { activeLock.wait(Math.min(remaining, 500L)); }
                            catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                if (!alreadyResolved[0]) { alreadyResolved[0] = true; callback.onAllFailed("Interrupted"); }
                                return;
                            }
                        }
                    }
                } finally {
                    client.cancelAll();
                }

                if (succeeded[0]) {
                    modelSucceeded = true;
                    break;
                }

                lastError = errorHolder[0] != null ? errorHolder[0] : "Unknown error";
                boolean isRetryable = isNetworkError(lastError);
                if (!isRetryable) break; // Auth/model errors — skip retries, try next model
                if (retry < MAX_RETRIES) {
                    Log.w(TAG, "Retryable error on " + provider + "/" + am.modelId + ": " + lastError);
                }
            }

            if (modelSucceeded) {
                if (!alreadyResolved[0]) {
                    alreadyResolved[0] = true;
                    if (pro.sketchware.BuildConfig.DEBUG) {
                        Log.d("PulseEngine", "[Pulse] Success: " + provider.getDisplayName()
                                + " after " + attemptCount + " attempt(s) ("
                                + (System.currentTimeMillis() - startMs) + "ms)");
                    }
                    callback.onSuccess(am.modelId, provider);
                }
                return;
            }

            if (pro.sketchware.BuildConfig.DEBUG) {
                Log.d("PulseEngine", "[Pulse] " + provider.getDisplayName() + "/" + am.modelId
                        + " failed: " + lastError + " → switching");
            }
            Log.w(TAG, provider + "/" + am.modelId + " failed: " + lastError);
            callback.onError(lastError, true);
        }

        if (!alreadyResolved[0]) {
            alreadyResolved[0] = true;
            callback.onAllFailed("All " + models.size() + " model(s) failed. Last: " + lastError);
        }
    }
    // ── ACTIVE_MODELS CRUD ────────────────────────────────────────────────────

    /** Returns the user-configured active model list (in order). */
    public List<ActiveModel> getActiveModels() {
        String json = prefs.prefs().getString(KEY_ACTIVE_MODELS, null);
        if (json == null || json.isEmpty()) return buildDefaultActiveModels();
        try {
            Type type = new TypeToken<List<ActiveModel>>() {}.getType();
            List<ActiveModel> list = gson.fromJson(json, type);
            return (list != null && !list.isEmpty()) ? list : buildDefaultActiveModels();
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse active models — using defaults", e);
            return buildDefaultActiveModels();
        }
    }

    /** Saves the user-configured active model list. */
    public void setActiveModels(List<ActiveModel> models) {
        prefs.prefs().edit()
                .putString(KEY_ACTIVE_MODELS, gson.toJson(models))
                .apply();
    }

    /** Adds a model to the end of the active list (no-op if already present). */
    public void addActiveModel(AiProvider provider, String modelId) {
        List<ActiveModel> list = new ArrayList<>(getActiveModels());
        for (ActiveModel m : list) {
            if (provider.name().equals(m.provider) && modelId.equals(m.modelId)) return;
        }
        list.add(new ActiveModel(provider, modelId));
        setActiveModels(list);
    }

    /** Removes a model from the active list. */
    public void removeActiveModel(AiProvider provider, String modelId) {
        List<ActiveModel> list = new ArrayList<>(getActiveModels());
        list.removeIf(m -> provider.name().equals(m.provider) && modelId.equals(m.modelId));
        setActiveModels(list);
    }

    /** Moves the model at {@code index} one position higher in the priority order. */
    public void moveUp(int index) {
        List<ActiveModel> list = new ArrayList<>(getActiveModels());
        if (index <= 0 || index >= list.size()) return;
        ActiveModel tmp = list.get(index - 1);
        list.set(index - 1, list.get(index));
        list.set(index, tmp);
        setActiveModels(list);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Default active-model list when the user hasn't configured anything.
     * Ordered by speed + quality for Sketchware use cases.
     */
    private List<ActiveModel> buildDefaultActiveModels() {
        return new ArrayList<>(Arrays.asList(
                // Free no-key first, then free-with-key, then paid
                new ActiveModel(AiProvider.CHUTES,           AiPreferences.DEFAULT_CHUTES_MODEL),
                new ActiveModel(AiProvider.GOOGLE_AI_STUDIO, AiPreferences.DEFAULT_GOOGLE_AI_STUDIO_MODEL),
                new ActiveModel(AiProvider.SAMBANOVA,        AiPreferences.DEFAULT_SAMBANOVA_MODEL),
                new ActiveModel(AiProvider.GROQ,             AiPreferences.DEFAULT_GROQ_MODEL),
                new ActiveModel(AiProvider.GEMINI,           AiPreferences.DEFAULT_GEMINI_MODEL),
                new ActiveModel(AiProvider.ANTHROPIC,        AiPreferences.DEFAULT_ANTHROPIC_MODEL),
                new ActiveModel(AiProvider.DEEPSEEK,         AiPreferences.DEFAULT_DEEPSEEK_MODEL)
        ));
    }

    /** Returns true for providers that work without an API key. */
    private boolean isNoKeyProvider(AiProvider p) {
        return p == AiProvider.CHUTES;
    }

    /**
     * Returns true if the error is a transient network/timeout issue worth retrying.
     * Auth failures, rate limits, and model errors are not retried.
     */
    private static boolean isNetworkError(String error) {
        if (error == null) return false;
        String lower = error.toLowerCase();
        return lower.contains("timeout")
                || lower.contains("connect")
                || lower.contains("socket")
                || lower.contains("network")
                || lower.contains("i/o")
                || lower.contains("reset")
                || lower.contains("eof")
                || lower.contains("ssl");
    }
}
