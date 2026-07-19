// nikit overhaul — Task 3 — 2026-05
package pro.sketchware.ai.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import pro.sketchware.ai.utils.AiLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * AIEngine — the central orchestrator for all AI layout operations.
 *
 * <p>Integrates:
 * <ul>
 *   <li>{@link ModelManager}  — dynamic fallback model selection from ACTIVE_MODELS</li>
 *   <li>{@link PromptBuilder} — per-tool prompt templates with guardrails</li>
 *   <li>{@link XMLValidator}  — output validation + auto-fix</li>
 *   <li>{@link CacheManager}  — response caching to skip duplicate calls</li>
 *   <li>{@link PulseController} — Continue/Cancel checkpoints (optional)</li>
 * </ul>
 *
 * <p>Each tool goes through a strict 7-step pipeline:
 * <ol>
 *   <li>Sanitize input</li>
 *   <li>Build context</li>
 *   <li>Build prompt</li>
 *   <li>Check cache</li>
 *   <li>Execute AI (with model fallback)</li>
 *   <li>Validate + auto-fix output</li>
 *   <li>Deliver result on main thread</li>
 * </ol>
 *
 * <p>All heavy work runs on a dedicated background thread. Callbacks are always
 * delivered on the main thread. The UI thread is NEVER blocked.
 */
public final class AIEngine {

    private static final String TAG = "AIEngine";

    // ── Conversation ID used for AIEngine-internal messages ───────────────────
    private static final String CONV_ID = "ai_engine";

    // ── Tool IDs ──────────────────────────────────────────────────────────────

    public static final String TOOL_GENERATE_UI = "GENERATE_UI";
    public static final String TOOL_MODIFY_UI   = "MODIFY_UI";
    public static final String TOOL_FIX_CODE    = "FIX_CODE";
    public static final String TOOL_OPTIMIZE    = "OPTIMIZE";
    public static final String TOOL_RTL_CONVERT = "RTL_CONVERT"; // pure logic, no AI
    public static final String TOOL_EXPLAIN     = "EXPLAIN";

    // ── Callback ──────────────────────────────────────────────────────────────

    /** Result callback — always called on the main thread. */
    public interface EngineCallback {
        /** Progress update (e.g. "Validating…"). */
        void onProgress(String message);
        /** Final success. xml is clean, validated, possibly auto-fixed. */
        void onSuccess(String xml, boolean fromCache, boolean wasAutoFixed);
        /** Unrecoverable failure after all retries and fallbacks exhausted. */
        void onError(String errorMessage);
    }

    /** Optional streaming chunk callback for real-time display. */
    public interface StreamCallback {
        void onChunk(String chunk);
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private final Context           context;
    private final ModelManager      modelManager;
    private final CacheManager      cache;
    private final AiPreferences     prefs;
    private final ExecutorService   executor;
    private final Handler           mainHandler;
    private final AtomicBoolean     cancelled;

    private PulseController pulseController;
    private StreamCallback  streamCallback;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AIEngine(Context context) {
        this.context      = context.getApplicationContext();
        this.modelManager = new ModelManager(context);
        this.cache        = CacheManager.getInstance();
        this.prefs        = AiPreferences.getInstance(context);
        this.executor     = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AIEngine-Worker");
            t.setDaemon(true);
            return t;
        });
        this.mainHandler  = new Handler(Looper.getMainLooper());
        this.cancelled    = new AtomicBoolean(false);
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /** Attaches a PulseController for Continue/Cancel checkpoints. */
    public void setPulseController(PulseController pulse) {
        this.pulseController = pulse;
    }

    /** Attaches a streaming chunk callback for real-time token display. */
    public void setStreamCallback(StreamCallback cb) {
        this.streamCallback = cb;
    }

    /**
     * Task 3: Wires a {@link ModelManager.FailoverStateListener} so the AI BottomSheet
     * can receive real-time provider-switching events and update the provider chip.
     * The listener is always called on the main thread.
     */
    public void setFailoverListener(ModelManager.FailoverStateListener l) {
        modelManager.setFailoverListener(l);
    }

    // ── Tool execution ────────────────────────────────────────────────────────

    /**
     * Generates a new UI layout from a free-text description.
     */
    public void generateUi(String userPrompt, String activityName,
                            String projectPkg, EngineCallback callback) {
        String prompt = PromptBuilder.buildGenerateUiPrompt(userPrompt, activityName, projectPkg);
        submitToExecutor(TOOL_GENERATE_UI, prompt, callback);
    }

    /**
     * Modifies an EXISTING layout according to user instructions.
     */
    public void modifyUi(String userPrompt, String existingXml,
                          String activityName, EngineCallback callback) {
        String prompt = PromptBuilder.buildModifyUiPrompt(userPrompt, existingXml, activityName);
        submitToExecutor(TOOL_MODIFY_UI, prompt, callback);
    }

    /**
     * Converts a layout to RTL using PURE LOGIC — no AI call made.
     * Result is delivered synchronously but callback still fires on main thread.
     */
    public void convertRtl(String xml, EngineCallback callback) {
        executor.execute(() -> {
            notifyProgress(callback, "Applying RTL conversion…");
            try {
                RTLConverter.ConversionResult result = RTLConverter.convert(xml);
                XMLValidator.ValidationResult vr =
                        XMLValidator.validate(result.xml, true /* autoFix */);

                String summary = result.changesApplied > 0
                        ? "RTL: " + result.changesApplied + " change(s) applied"
                        : "Layout is already RTL compatible";
                AiLog.d(TAG, summary);
                for (String entry : result.changeLog) AiLog.d(TAG, "  • " + entry);

                mainHandler.post(() ->
                        callback.onSuccess(vr.xml, false, vr.wasAutoFixed));
            } catch (Exception e) {
                Log.e(TAG, "RTL conversion error", e);
                mainHandler.post(() -> callback.onError("RTL error: " + e.getMessage()));
            }
        });
    }

    /**
     * Fixes a broken/invalid XML layout. First tries auto-fix; falls back to AI.
     */
    public void fixCode(String brokenXml, EngineCallback callback) {
        executor.execute(() -> {
            notifyProgress(callback, "Analyzing XML…");

            // Step 1: try pure auto-fix first (no AI)
            XMLValidator.ValidationResult quick =
                    XMLValidator.validate(brokenXml, true /* autoFix */);
            if (quick.valid) {
                AiLog.d(TAG, "Auto-fix resolved all issues without AI");
                mainHandler.post(() -> callback.onSuccess(quick.xml, false, true));
                return;
            }

            // Step 2: AI-assisted fix
            notifyProgress(callback, "Auto-fix insufficient — asking AI to fix…");
            String prompt = PromptBuilder.buildFixPrompt(brokenXml, quick.issueReport());
            runPipelineOnCurrentThread(TOOL_FIX_CODE, prompt, callback);
        });
    }

    /**
     * Optimizes a layout for performance and best practices.
     */
    public void optimize(String xml, String activityName, EngineCallback callback) {
        String prompt = PromptBuilder.buildOptimizePrompt(xml, activityName);
        submitToExecutor(TOOL_OPTIMIZE, prompt, callback);
    }

    /**
     * Explains a layout in plain text.
     *
     * @param language "English" or "Arabic"
     */
    public void explain(String xml, String language, EngineCallback callback) {
        String prompt = PromptBuilder.buildExplainPrompt(xml, language);
        // Explain returns plain text — skip XML extraction/validation
        executor.execute(() -> runRawPipelineOnCurrentThread(TOOL_EXPLAIN, prompt, callback));
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    /** Cancels any in-flight AI request. */
    public void cancel() {
        cancelled.set(true);
        AiLog.d(TAG, "AIEngine cancelled");
    }

    /** Resets cancelled state. Must be called before starting a new request. */
    public void reset() {
        cancelled.set(false);
    }

    /** Returns true if cancellation has been requested. */
    public boolean isCancelled() {
        return cancelled.get();
    }

    // ── Internal pipeline ─────────────────────────────────────────────────────

    /** Submits the full validated pipeline to the background executor. */
    private void submitToExecutor(String tool, String prompt, EngineCallback callback) {
        executor.execute(() -> runPipelineOnCurrentThread(tool, prompt, callback));
    }

    /**
     * Full 7-step pipeline: cache → pulse → AI → extract → validate → cache → deliver.
     * Must run on a background thread.
     */
    private void runPipelineOnCurrentThread(String tool, String prompt, EngineCallback callback) {
        if (cancelled.get()) {
            mainHandler.post(() -> callback.onError("Cancelled"));
            return;
        }

        // ── Step 1: Determine first model (for cache key) ────────────────────
        List<ModelManager.ActiveModel> models = modelManager.getActiveModels();
        String firstModelId = models.isEmpty() ? "default" : models.get(0).modelId;

        // ── Step 2: Cache lookup ─────────────────────────────────────────────
        String cached = cache.get(tool, prompt, firstModelId);
        if (cached != null) {
            notifyProgress(callback, "Loaded from cache…");
            AiLog.d(TAG, "[" + tool + "] Cache hit");
            XMLValidator.ValidationResult vr = XMLValidator.validate(cached, true);
            mainHandler.post(() -> callback.onSuccess(vr.xml, true, vr.wasAutoFixed));
            return;
        }

        // ── Step 3: Pulse checkpoint ─────────────────────────────────────────
        if (pulseController != null) {
            notifyProgress(callback, "Waiting for confirmation…");
            boolean proceed = pulseController.await(
                    "AI will generate layout using " + firstModelId);
            if (!proceed) {
                mainHandler.post(() -> callback.onError("Cancelled by user"));
                return;
            }
        }

        // ── Step 4: Build conversation history ───────────────────────────────
        notifyProgress(callback, "Sending to AI…");
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.userMessage(CONV_ID, prompt));

        // ── Step 5: Execute with model fallback ──────────────────────────────
        final StringBuilder responseBuilder = new StringBuilder();
        final String[]      errorHolder     = { null };
        final boolean[]     succeeded       = { false };

        ModelManager.FallbackCallback fb = new ModelManager.FallbackCallback() {
            @Override
            public void onStreamChunk(String chunk) {
                responseBuilder.append(chunk);
                if (streamCallback != null) {
                    mainHandler.post(() -> streamCallback.onChunk(chunk));
                }
            }
            @Override
            public void onToolCall(String name, String argsJson) {
                // AIEngine one-shot layout tools do not use tool-calling
            }
            @Override
            public void onSuccess(String modelId, AiProvider provider) {
                succeeded[0] = true;
                AiLog.d(TAG, "[" + tool + "] Success via " + provider + "/" + modelId);
            }
            @Override
            public void onAllFailed(String lastError) {
                errorHolder[0] = lastError;
            }
            @Override
            public void onError(String error, boolean willRetry) {
                notifyProgress(callback, "Model failed — trying next…");
            }
        };

        modelManager.executeWithFallback(messages, buildSystemPrompt(), null, fb);

        if (cancelled.get()) {
            mainHandler.post(() -> callback.onError("Cancelled"));
            return;
        }

        if (!succeeded[0]) {
            String msg = errorHolder[0] != null ? errorHolder[0] : "All models failed";
            mainHandler.post(() -> callback.onError(msg));
            return;
        }

        // ── Step 6: Extract XML + Validate + Auto-fix ────────────────────────
        notifyProgress(callback, "Validating output…");
        String rawResponse = responseBuilder.toString();
        XMLValidator.ValidationResult vr = XMLValidator.validate(rawResponse, true /* autoFix */);

        if (!vr.valid && !vr.wasAutoFixed) {
            Log.w(TAG, "[" + tool + "] Invalid XML after auto-fix:\n" + vr.issueReport());
            mainHandler.post(() -> callback.onError(
                    "AI returned invalid XML that couldn't be auto-fixed:\n"
                            + vr.issueReport()));
            return;
        }

        // ── Step 7: Cache + deliver ───────────────────────────────────────────
        cache.put(tool, prompt, firstModelId, vr.xml);
        AiLog.d(TAG, "[" + tool + "] Done — issues=" + vr.issues.size()
                + " autoFixed=" + vr.wasAutoFixed + " size=" + vr.xml.length());
        mainHandler.post(() -> callback.onSuccess(vr.xml, false, vr.wasAutoFixed));
    }

    /**
     * Raw pipeline — no XML extraction or validation.
     * Used for plain-text tools (EXPLAIN). Must run on a background thread.
     */
    private void runRawPipelineOnCurrentThread(String tool, String prompt,
                                               EngineCallback callback) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.userMessage(CONV_ID, prompt));

        final StringBuilder sb   = new StringBuilder();
        final boolean[]     done = { false };
        final String[]      err  = { null };

        ModelManager.FallbackCallback fb = new ModelManager.FallbackCallback() {
            @Override public void onStreamChunk(String c) { sb.append(c); }
            @Override public void onToolCall(String n, String a) { /* not used for EXPLAIN */ }
            @Override public void onSuccess(String m, AiProvider p) { done[0] = true; }
            @Override public void onAllFailed(String e) { err[0] = e; }
            @Override public void onError(String e, boolean r) {}
        };

        modelManager.executeWithFallback(messages, null, null, fb);

        if (done[0]) {
            mainHandler.post(() -> callback.onSuccess(sb.toString(), false, false));
        } else {
            String msg = err[0] != null ? err[0] : "All models failed";
            mainHandler.post(() -> callback.onError(msg));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Posts a progress update to the callback on the main thread.
     * Named notifyProgress to avoid conflict with java.lang.Object.notify().
     */
    private void notifyProgress(EngineCallback cb, String msg) {
        mainHandler.post(() -> cb.onProgress(msg));
    }

    private static String buildSystemPrompt() {
        return "You are an expert Android layout engineer embedded in Sketchware Pro. "
                + "Always output ONLY valid Android XML inside ```xml … ``` fences. "
                + "Never include explanations inside the XML. "
                + "Never remove existing views unless explicitly asked. "
                + "All android:id values must use @+id/snake_case format.";
    }
}
