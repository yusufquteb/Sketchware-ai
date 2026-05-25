package pro.sketchware.ai.engine;

import pro.sketchware.ai.engine.TokenOptimizer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.ai.core.AiError;
import pro.sketchware.ai.core.AiHealthMonitor;
import pro.sketchware.ai.core.ProviderCapabilities;
import pro.sketchware.ai.core.ToolCallValidator;
import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.api.StreamBuffer;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.api.ToolDefinition;
import pro.sketchware.ai.diagnostics.AiSessionLogger;
import pro.sketchware.ai.security.PromptSanitizer;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.fix.AiFixSupport;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.ai.tools.ToolRegistry;

public class AgentExecutor {

    /**
     * Pulse callback — called before major AI actions.
     * The UI shows a plan summary with Continue (auto 30s) / Cancel buttons.
     */
    public interface PulseConfirmationCallback {
        void onConfirmationRequired(String plan, Runnable onContinue, Runnable onCancel);
    }

    private PulseConfirmationCallback pulseCallback;

    public void setPulseCallback(PulseConfirmationCallback cb) {
        this.pulseCallback = cb;
    }

    private static final int  SAFETY_TOOL_ITERATION_LIMIT = 200;
    /** Fallback timeout used only when preferences are unavailable. */
    private static final long STREAM_TIMEOUT_MS_DEFAULT  = 120_000L;
    /** Max time between consecutive stream chunks before treating the stream as frozen. */
    private static final long STALL_TIMEOUT_MS = 30_000L;
    /** Default pulse steps — can be overridden by AiPreferences.getPulseSteps(). */
    private static final int  PULSE_STEPS_DEFAULT  = 6;
    /** Countdown seconds before Continue is auto-selected. */
    private static final int  PULSE_AUTO_SECS     = 10;
    /** Ordered failover providers (tried in sequence on timeout/error). */
    /**
     * Failover order — only enabled providers with API keys (or no-key providers) are tried.
     * Order: free-no-key → free-with-key → paid.
     */
    private static final pro.sketchware.ai.models.AiProvider[] FAILOVER_ORDER = {
        // Group 1: Free, no API key
        pro.sketchware.ai.models.AiProvider.CHUTES,
        // Group 2: Free with API key
        pro.sketchware.ai.models.AiProvider.GOOGLE_AI_STUDIO,
        pro.sketchware.ai.models.AiProvider.SAMBANOVA,
        pro.sketchware.ai.models.AiProvider.CEREBRAS,
        pro.sketchware.ai.models.AiProvider.GROQ,
        pro.sketchware.ai.models.AiProvider.HUGGINGFACE,
        pro.sketchware.ai.models.AiProvider.MISTRAL,
        pro.sketchware.ai.models.AiProvider.COHERE,
        pro.sketchware.ai.models.AiProvider.GITHUB_MODELS,
        // Group 3: Paid
        pro.sketchware.ai.models.AiProvider.OPENAI,
        pro.sketchware.ai.models.AiProvider.ANTHROPIC,
        pro.sketchware.ai.models.AiProvider.DEEPSEEK,
        pro.sketchware.ai.models.AiProvider.GEMINI,
        pro.sketchware.ai.models.AiProvider.TOGETHER,
        pro.sketchware.ai.models.AiProvider.DEEPINFRA,
    };

    private final Context context;
    private final ToolRegistry toolRegistry;
    private final AiPreferences preferences;
    private AiSessionLogger sessionLogger;
    /** Latch used by pulse: UI counts down, then releases to let agent continue. */
    private volatile java.util.concurrent.CountDownLatch pulseLatch;
    /** Counts tool calls across all iterations for pulse trigger. */
    private int toolCallCount = 0;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isCancelled;

    private volatile AiApiClient currentClient;

    public interface AgentCallback {
        void onStreamingChunk(String chunk);
        void onAssistantMessage(ChatMessage assistantMessage);
        void onToolCallStarted(ToolCall toolCall);
        void onToolCallProgress(String toolCallId, String status, int progress, boolean indeterminate);
        void onToolCallCompleted(ToolCall toolCall, ToolResult result);
        void onToolMessage(ChatMessage toolMessage);
        void onResponseComplete(ChatMessage assistantMessage);
        void onCancelled();
        void onError(String error);
        void onThinking(String status);
        /** Called when failover to a different provider occurs. Default no-op for backwards compat. */
        default void onFailover(String fromProvider, String toProvider, String toModel) {}
    }

    /** Scope constants — passed from ChatActivity via Intent extras. */
    public static final String SCOPE_GLOBAL  = "global";
    public static final String SCOPE_PROJECT = "project";

    public AgentExecutor(Context context, List<String> workspaceProjectIds, String workspaceId) {
        this(context, workspaceProjectIds, workspaceId, SCOPE_GLOBAL, null);
    }

    /**
     * @param scope           {@link #SCOPE_PROJECT} or {@link #SCOPE_GLOBAL}
     * @param scopedProjectId the single project ID when scope == SCOPE_PROJECT; ignored otherwise
     */
    public AgentExecutor(Context context, List<String> workspaceProjectIds, String workspaceId,
                         String scope, String scopedProjectId) {
        this.context = context.getApplicationContext();
        this.preferences = AiPreferences.getInstance(this.context);
        if (SCOPE_PROJECT.equals(scope) && scopedProjectId != null && !scopedProjectId.isEmpty()) {
            this.toolRegistry = ToolRegistry.createForProject(scopedProjectId);
        } else {
            this.toolRegistry = ToolRegistry.createGlobal();
        }
        this.executor    = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.isCancelled = new AtomicBoolean(false);
        this.sessionLogger = AiSessionLogger.getInstance(this.context);
    }

    /**
     * Forced stop — sets the cancellation flag, cancels all in-flight HTTP calls,
     * and interrupts the executor thread so the agent loop exits immediately
     * even if it is blocked on a CountDownLatch or a slow tool call.
     */
    public void cancel() {
        isCancelled.set(true);
        // 1. Cancel all in-flight OkHttp requests (streaming + tool downloads)
        AiApiClient client = currentClient;
        if (client != null) {
            client.cancelAll();
        }
        // 2. Interrupt the executor thread so CountDownLatch.await() / Thread.sleep()
        //    throw InterruptedException and the loop exits without waiting for the timeout.
        executor.shutdownNow();   // sends interrupt to running thread
    }

    public void execute(List<ChatMessage> conversationHistory, String modelId,
                        AiProvider provider, String systemPrompt,
                        List<String> allowedProjectIds, String workspaceId,
                        AgentCallback callback) {
        execute(conversationHistory, modelId, provider, systemPrompt,
                allowedProjectIds, workspaceId, null, callback);
    }

    public void execute(List<ChatMessage> conversationHistory, String modelId,
                        AiProvider provider, String systemPrompt,
                        List<String> allowedProjectIds, String workspaceId,
                        String pageContext,
                        AgentCallback callback) {
        isCancelled.set(false);
        toolCallCount = 0;

        executor.execute(() -> {
            try {
                String apiKey = preferences.getApiKey(provider);
                if (provider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
                    postError(callback, "No API key set for " + provider.getDisplayName()
                            + ". Please configure it in AI Settings.");
                    return;
                }

                currentClient = AiClientFactory.createClient(context, provider, apiKey);
                if (currentClient == null) {
                    postError(callback, "Failed to create API client for " + provider.getDisplayName());
                    return;
                }
                sessionLogger.logSessionStart(provider.getDisplayName(),
                        preferences.getSelectedModel(provider));

                // mutable model holder for failover reassignment inside while loop
                // (Java doesn't allow reassigning effectively-final params in lambdas)
                final String[] modelHolder = {preferences.getSelectedModel(provider)};

                ToolContext toolContext = new ToolContext(context, allowedProjectIds, workspaceId);
                toolContext.setCancellationChecker(isCancelled::get);
                toolContext.setToolProgressListener((toolCallId, status, progress, indeterminate) ->
                        mainHandler.post(() -> callback.onToolCallProgress(
                                toolCallId, status, progress, indeterminate)));

                // ── Token Optimiser pipeline ─────────────────────────────────
                // Summarise old turns, truncate bulky tool results, cap total size.
                // Pass the provider so the budget adapts to its actual context window.
                List<ChatMessage> messages = TokenOptimizer.optimise(
                        new ArrayList<>(conversationHistory), provider);
                // Use a compact system prompt for providers with small context windows
                // to avoid consuming most of the context budget before the conversation starts.
                ProviderCapabilities caps = ProviderCapabilities.of(provider);
                boolean usingCompactPrompt = (caps.maxContextTokens > 0 && caps.maxContextTokens < 40_000);
                String effectiveSystemPrompt = usingCompactPrompt
                        ? buildCompactSystemPrompt(systemPrompt, allowedProjectIds, pageContext)
                        : buildSystemPrompt(systemPrompt, allowedProjectIds, pageContext);
                List<ToolDefinition> toolDefs    = toolRegistry.getToolDefinitions();
                // Tracks which provider is currently active (changes on failover).
                final pro.sketchware.ai.models.AiProvider[] currentProviderHolder = {provider};
                // All providers tried in this request — prevents infinite failover loops.
                // When provider P fails and we failover to Q, Q is added here so the next
                // failure looks past Q instead of cycling back to it.
                final java.util.Set<pro.sketchware.ai.models.AiProvider> triedProviders =
                        new java.util.HashSet<>();
                triedProviders.add(provider);
                int modelNotFoundRetries = 0;

                int iteration = 0;
                while (!isCancelled.get()) {
                    iteration++;
                    if (iteration > SAFETY_TOOL_ITERATION_LIMIT) {
                        postError(callback,
                                "Agent stopped after an unusually long autonomous loop. "
                                + "Review the tool cards and continue from the latest state if needed.");
                        return;
                    }

                    StringBuilder fullResponse    = new StringBuilder();
                    List<ToolCall> pendingToolCalls = new ArrayList<>();
                    AtomicBoolean hasError         = new AtomicBoolean(false);
                    CountDownLatch streamLatch      = new CountDownLatch(1);
                    String[] streamError            = new String[1];

                    // Log the user message on the first iteration
                    if (iteration == 1 && !messages.isEmpty()) {
                        ChatMessage lastUser = messages.get(messages.size() - 1);
                        if ("user".equals(lastUser.getRole())) {
                            sessionLogger.logUserMessage(lastUser.getContent());
                        }
                    }
                    mainHandler.post(() -> callback.onThinking("Thinking..."));

                    // Use conversationId from the first message as a Tag for cancellation
                    String conversationIdTag = !messages.isEmpty() ? messages.get(0).getConversationId() : null;
                    if (conversationIdTag != null) {
                        currentClient.cancelByTag(conversationIdTag);
                    }

                    final long healthToken = AiHealthMonitor.getInstance().recordRequestStart(provider);

                    // Adaptive chunk buffer: batches I/O-thread chunks at ~20 fps
                    // to reduce UI-thread scheduling overhead on low-end devices.
                    StreamBuffer streamBuffer = new StreamBuffer(batch -> {
                        if (!isCancelled.get()) callback.onStreamingChunk(batch);
                    });

                    currentClient.sendChatRequest(messages, modelHolder[0], effectiveSystemPrompt, toolDefs, conversationIdTag,
                            new StreamingResponseHandler() {
                                @Override
                                public void onChunk(String textDelta) {
                                    if (textDelta == null || isCancelled.get()) return;
                                    fullResponse.append(textDelta);
                                    streamBuffer.append(textDelta);
                                }

                                @Override
                                public void onToolCall(ToolCall toolCall) {
                                    if (toolCall == null || isCancelled.get()) return;
                                    pendingToolCalls.add(toolCall);
                                    mainHandler.post(() -> callback.onToolCallStarted(toolCall));
                                }

                                @Override
                                public void onComplete(String response) {
                                    // Flush remaining buffered chunks before signalling completion
                                    // so the UI sees the full text before the cursor disappears.
                                    try {
                                        streamBuffer.flushNow();
                                    } finally {
                                        streamLatch.countDown();
                                    }
                                }

                                @Override
                                public void onError(String error) {
                                    streamBuffer.cancel();
                                    hasError.set(true);
                                    streamError[0] = error;
                                    streamLatch.countDown();
                                }
                            });

                    long streamTimeoutMs = preferences.getRequestTimeoutSecs() * 1000L;
                    if (streamTimeoutMs <= 0) streamTimeoutMs = STREAM_TIMEOUT_MS_DEFAULT;

                    // Poll loop: checks cancellation + heartbeat stall every 500 ms.
                    long deadline = System.currentTimeMillis() + streamTimeoutMs;
                    boolean completed = false;
                    while (!completed && System.currentTimeMillis() < deadline) {
                        completed = streamLatch.await(500L, TimeUnit.MILLISECONDS);
                        if (completed || hasError.get()) break;
                        if (isCancelled.get()) {
                            streamBuffer.cancel();
                            postCancelled(callback);
                            return;
                        }
                        // Heartbeat stall: stream is open but no data for STALL_TIMEOUT_MS.
                        // Only fire after at least one chunk has arrived (partial response).
                        long msSinceLastChunk = System.currentTimeMillis() - streamBuffer.lastChunkMs.get();
                        if (fullResponse.length() > 0 && msSinceLastChunk > STALL_TIMEOUT_MS) {
                            streamBuffer.cancel();
                            hasError.set(true);
                            streamError[0] = "Stream stalled — no data for "
                                    + (STALL_TIMEOUT_MS / 1000) + "s";
                            break;
                        }
                    }
                    if (isCancelled.get()) { streamBuffer.cancel(); postCancelled(callback); return; }

                    if (!completed || hasError.get()) {
                        String failReason = (streamError[0] != null) ? streamError[0]
                                : (!completed ? "timed out after " + (streamTimeoutMs / 1000) + "s"
                                             : "request error");

                        // ── Smart retries on the SAME provider before failing over ───────
                        if (streamError[0] != null && !isCancelled.get()) {
                            String err = streamError[0];

                            // Retry with compact prompt when the message is too long.
                            // Groq (131k context) gets the full prompt by default, but its
                            // free tier still imposes a hard per-request input limit (~8k tokens).
                            boolean isTooLong = err.contains("Message Too Long")
                                    || err.contains("message too long")
                                    || err.contains("Request too large")
                                    || err.contains("context_length_exceeded")
                                    || err.contains("tokens_limit_reached");
                            if (isTooLong && !usingCompactPrompt) {
                                usingCompactPrompt = true;
                                effectiveSystemPrompt = buildCompactSystemPrompt(
                                        systemPrompt, allowedProjectIds, pageContext);
                                sessionLogger.logFailover(
                                        currentProviderHolder[0].getDisplayName(),
                                        currentProviderHolder[0].getDisplayName(),
                                        err + " → retrying with compact prompt");
                                mainHandler.post(() -> callback.onThinking(
                                        "📏 Message too long → switching to compact prompt..."));
                                continue;
                            }

                            // Stale cached model: reset to the verified default and retry.
                            // Happens when a model ID saved from an API fetch is later removed.
                            boolean isModelNotFound = err.contains("Model Not Found")
                                    || err.contains("model_not_found")
                                    || err.contains("model not found")
                                    || err.contains("does not exist");
                            if (isModelNotFound && modelNotFoundRetries < 2) {
                                pro.sketchware.ai.models.AiProvider currProv = currentProviderHolder[0];
                                String defaultModel = AiProviderModels.getDefaultModel(currProv);
                                if (!defaultModel.isEmpty() && !defaultModel.equals(modelHolder[0])) {
                                    modelNotFoundRetries++;
                                    preferences.clearCachedModels(currProv);
                                    sessionLogger.logFailover(currProv.getDisplayName(),
                                            currProv.getDisplayName(),
                                            "Model not found: " + modelHolder[0]
                                            + " → retrying with " + defaultModel);
                                    modelHolder[0] = defaultModel;
                                    mainHandler.post(() -> callback.onThinking(
                                            "🔄 Model not found → retrying with default model..."));
                                    continue;
                                }
                            }
                        }
                        // ── End smart retries ─────────────────────────────────────────────

                        AiHealthMonitor.getInstance().recordFailure(provider,
                                AiError.fromRawError(failReason, provider.getDisplayName()));

                        // Try to failover to next untried provider
                        pro.sketchware.ai.models.AiProvider failoverProvider =
                                findFailoverProvider(triedProviders, preferences);
                        if (failoverProvider != null && !isCancelled.get()) {
                            String failoverKey = preferences.getApiKey(failoverProvider);
                            String failoverModel = preferences.getSelectedModel(failoverProvider);
                            pro.sketchware.ai.models.AiProvider fromProvider = currentProviderHolder[0];
                            sessionLogger.logFailover(fromProvider.getDisplayName(),
                                failoverProvider.getDisplayName(), failReason);
                            mainHandler.post(() -> callback.onThinking(
                                    "⚡ " + fromProvider.getDisplayName() + " failed"
                                    + " → switching to " + failoverProvider.getDisplayName() + "..."));
                            mainHandler.post(() -> callback.onFailover(
                                    fromProvider.getDisplayName(),
                                    failoverProvider.getDisplayName(),
                                    failoverModel != null ? failoverModel : ""));
                            try { Thread.sleep(800); } catch (InterruptedException ignored2) {}
                            currentClient = pro.sketchware.ai.api.AiClientFactory
                                    .createClient(context, failoverProvider, failoverKey);
                            currentProviderHolder[0] = failoverProvider;
                            triedProviders.add(failoverProvider);
                            modelHolder[0] = failoverModel;
                            continue; // retry this iteration with new provider
                        }

                        // No failover available
                        if (!completed) {
                            postError(callback, "⏱ Provider timed out and no failover is configured.");
                        } else {
                            postError(callback, streamError[0] != null ? streamError[0] : "Unknown AI error");
                        }
                        return;
                    }

                    ChatMessage assistantMsg = ChatMessage.assistantMessage(
                            fullResponse.toString(),
                            pendingToolCalls.isEmpty() ? null : pendingToolCalls);
                    messages.add(assistantMsg);
                    if (fullResponse.length() > 0) {
                        sessionLogger.logAiResponse(fullResponse.toString());
                    }
                    mainHandler.post(() -> callback.onAssistantMessage(assistantMsg));

                    AiHealthMonitor.getInstance().recordSuccess(provider, healthToken);

                    // ── No tool calls → conversation turn complete ──────────────────
                    if (pendingToolCalls.isEmpty()) {
                        mainHandler.post(() -> callback.onResponseComplete(assistantMsg));
                        return;  // ✅ FIX: was missing in original causing infinite loop risk
                    }

                    // ── Execute each tool call ──────────────────────────────────────
                    for (ToolCall tc : pendingToolCalls) {
                        if (isCancelled.get()) {
                            postCancelled(callback);
                            return;
                        }

                        mainHandler.post(() -> callback.onThinking("Running \"" + tc.getName() + "\"..."));
                        sessionLogger.logToolCall(tc.getName(), tc.getArguments());
                        toolContext.beginToolCall(tc.getId());
                        toolContext.reportProgress("Starting...", -1, true);
                        ToolResult result = executeTool(tc, toolContext);
                        toolContext.endToolCall();
                        sessionLogger.logToolResult(tc.getName(), result.isSuccess(),
                                result.isSuccess() ? result.getOutput() : result.getError());

                        ToolResult finalResult = result;
                        mainHandler.post(() -> callback.onToolCallCompleted(tc, finalResult));

                        String toolContent = result.isSuccess()
                                ? (result.getOutput() != null ? result.getOutput() : "")
                                : "Error: " + (result.getError() != null ? result.getError() : "Tool execution failed");
                        ChatMessage toolResultMsg = ChatMessage.toolResultMessage(
                                tc.getId(), tc.getName(), toolContent);
                        messages.add(toolResultMsg);
                        mainHandler.post(() -> callback.onToolMessage(toolResultMsg));

                        // ── Pulse: pause after every N tool calls for Continue/Cancel ────
                        toolCallCount++;
                        int pulseSteps = preferences.getPulseSteps();
                        if (pulseCallback != null && toolCallCount % pulseSteps == 0 && !isCancelled.get()) {
                            pulseLatch = new java.util.concurrent.CountDownLatch(1);
                            final boolean[] cancelled = {false};
                            final String stepSummary = "Tool " + toolCallCount
                                    + ": \"" + tc.getName() + "\" done";
                            mainHandler.post(() -> pulseCallback.onConfirmationRequired(
                                    stepSummary,
                                    () -> pulseLatch.countDown(),          // Continue
                                    () -> { cancelled[0] = true; pulseLatch.countDown(); } // Cancel
                            ));
                            boolean timedOut = !pulseLatch.await(PULSE_AUTO_SECS, java.util.concurrent.TimeUnit.SECONDS);
                            if (timedOut || !cancelled[0]) {
                                // Auto-continue or user pressed Continue
                                mainHandler.post(() -> callback.onThinking("Continuing..."));
                            } else {
                                // User pressed Cancel
                                postCancelled(callback);
                                return;
                            }
                        }

                        // ── Feedback Loop: auto-inject fix instruction on build failure ──
                        if ("build_project".equals(tc.getName()) && !result.isSuccess()
                                && preferences.isAutoFixOnError()) {
                            String errOutput = result.getError() != null ? result.getError() : toolContent;
                            if (errOutput != null && !errOutput.trim().isEmpty()) {
                                // Use AiFixSupport to extract richer error context
                                // Use the first allowed project ID for fix context
                                String scId = (toolContext != null
                                        && !toolContext.getAllowedProjectIds().isEmpty())
                                        ? toolContext.getAllowedProjectIds().get(0) : null;
                                String fixPrompt;
                                if (scId != null && !scId.isEmpty()) {
                                    try {
                                        AiFixSupport.FixContext fixCtx =
                                                AiFixSupport.buildSessionAndPrompt(context, scId, errOutput);
                                        if (fixCtx != null && fixCtx.agentPrompt != null) {
                                            fixPrompt = "SYSTEM: Build failed. Analyse and fix all errors, "
                                                    + "then run build_project again. Fix automatically — do NOT ask the user.\n\n"
                                                    + fixCtx.agentPrompt;
                                        } else {
                                            fixPrompt = buildBasicFixPrompt(errOutput);
                                        }
                                    } catch (Exception ex) {
                                        fixPrompt = buildBasicFixPrompt(errOutput);
                                    }
                                } else {
                                    fixPrompt = buildBasicFixPrompt(errOutput);
                                }
                                ChatMessage feedbackMsg = ChatMessage.systemMessage(fixPrompt);
                                messages.add(feedbackMsg);
                                mainHandler.post(() -> callback.onThinking("Auto-fixing build errors..."));
                            }
                        }
                    }
                    // loop continues → next AI turn with tool results injected
                }

                // ✅ FIX: postCancelled is now only reached when isCancelled exits the while loop
                postCancelled(callback);

            } catch (Exception e) {
                if (isCancelled.get()) {
                    postCancelled(callback);
                } else {
                    postError(callback, "Error: " + e.getMessage());
                }
            } finally {
                AiApiClient client = currentClient;
                if (client != null) {
                    client.shutdown();
                }
                currentClient = null;
            }
        });
    }

    

    private ToolResult executeTool(ToolCall toolCall, ToolContext toolContext) {
        AgentTool tool = toolRegistry.getTool(toolCall.getName());
        if (tool == null) {
            return ToolResult.failure(toolCall.getId(),
                    "Unknown tool: '" + toolCall.getName() + "'. "
                    + "Check the tool catalog above and use only listed tool names.");
        }

        // ── Parse arguments ──────────────────────────────────────────────────
        JsonObject args;
        String argsStr = toolCall.getArguments();
        if (argsStr != null && !argsStr.isEmpty()) {
            try {
                args = JsonParser.parseString(argsStr).getAsJsonObject();
            } catch (Exception e) {
                return ToolResult.failure(toolCall.getId(),
                        "Malformed JSON arguments for '" + toolCall.getName() + "': "
                        + e.getMessage() + ". Regenerate with valid JSON.");
            }
        } else {
            args = new JsonObject();
        }

        // ── Schema validation (Phase 8) ──────────────────────────────────────
        ToolCallValidator.ValidationResult validation =
                ToolCallValidator.validate(args, tool.getParametersSchema());
        if (!validation.valid) {
            return ToolResult.failure(toolCall.getId(),
                    "Invalid arguments for '" + toolCall.getName() + "': "
                    + validation.errorMessage + ". Fix the arguments and retry.");
        }

        // ── Execute with timeout + telemetry (Phase 6) ───────────────────────
        return ToolExecutionGuard.executeWithTimeout(tool, args, toolContext, toolCall.getId());
    }

    // ── System prompt builder ───────────────────────────────────────────────

    /**
     * Compact system prompt for providers with context windows under 40k tokens.
     * Keeps only essential routing rules and a brief tool name list.
     * Saves ~5,000 tokens compared to the full system prompt.
     */
    private String buildCompactSystemPrompt(String userSystemPrompt, List<String> projectIds,
                                             String pageContext) {
        StringBuilder sb = new StringBuilder();
        if (userSystemPrompt != null && !userSystemPrompt.isEmpty()) {
            sb.append(userSystemPrompt.trim());
        } else {
            sb.append(AiPreferences.DEFAULT_SYSTEM_PROMPT.trim());
        }
        sb.append("\n\nYou are an AI assistant for Sketchware Pro. Use the available tools to help.\n");
        sb.append("TOOLS: generate_layout, add_view_xml, describe_layout, build_project, ");
        sb.append("get_event_blocks, set_event_logic, add_block, read_file, write_file, ");
        sb.append("patch_file, list_files, add_string_resource, add_color_resource, ");
        sb.append("search_maven, add_library, analyze_build_error, get_compile_logs.\n");
        sb.append("UI edits: describe_layout → generate_layout. Never use write_file for UI.\n");
        sb.append("Blocks: prefer set_event_logic over add_block for multiple blocks.\n");
        sb.append("Always call tools directly — don't narrate before calling.\n");
        sb.append("BLOCK SPEC FORMAT (add_block — exact values required):\n");
        sb.append("  showToast       → spec='show toast %s.text',              type=' ', parameters=['msg']\n");
        sb.append("  addSourceDir.   → spec='add source directly %s.inputOnly', type=' ', parameters=['code;']\n");
        sb.append("  startActivity   → spec='start activity %m.activity',      type=' ', parameters=['actName']\n");
        sb.append("  finish          → spec='finish',                           type=' ', parameters=[]\n");
        sb.append("  setVariable(s)  → spec='set %s.name = %s.value',          type=' ', parameters=['var','val']\n");
        sb.append("  ifElse          → spec='if %b.condition then',             type=' ', parameters=['']\n");

        if (projectIds != null && projectIds.size() == 1) {
            sb.append("Project sc_id: ").append(projectIds.get(0)).append("\n");
        }
        if (pageContext != null && !pageContext.trim().isEmpty()) {
            sb.append("Context: ").append(pageContext.trim(), 0,
                    Math.min(200, pageContext.trim().length())).append("\n");
        }
        return sb.toString();
    }

    private String buildSystemPrompt(String userSystemPrompt, List<String> projectIds) {
        return buildSystemPrompt(userSystemPrompt, projectIds, null);
    }

    private String buildSystemPrompt(String userSystemPrompt, List<String> projectIds, String pageContext) {
        StringBuilder sb = new StringBuilder();

        // ── Base system prompt ─────────────────────────────────────────────
        if (userSystemPrompt != null && !userSystemPrompt.isEmpty()) {
            sb.append(userSystemPrompt.trim());
        } else {
            sb.append(AiPreferences.DEFAULT_SYSTEM_PROMPT.trim());
        }

        // ── Active tool catalog (dynamic) ──────────────────────────────────
        sb.append("\n\n");
        sb.append("═══════════════════════════════════════════\n");
        sb.append("  ACTIVE TOOLS IN THIS SESSION\n");
        sb.append("═══════════════════════════════════════════\n");

        java.util.List<AgentTool> all = toolRegistry.getAllTools();
        appendToolGroup(sb, all, "PROJECT",
            "list_projects","get_project_info","create_project","delete_project","duplicate_project");
        appendToolGroup(sb, all, "FILES",
            "read_file","write_file","delete_file","list_files","copy_file","move_file");
        appendToolGroup(sb, all, "ACTIVITIES",
            "list_activities","get_screen_source","create_activity","delete_activity");
        appendToolGroup(sb, all, "UI LAYOUT",
            "get_layout","edit_layout","describe_layout","add_view","modify_view","remove_view");
        appendToolGroup(sb, all, "BLOCK LOGIC (Phase 4)",
            "get_activity_events","get_event_blocks","add_block","modify_block","delete_block",
            "get_moreblocks","create_moreblock","delete_moreblock");
        appendToolGroup(sb, all, "RESOURCES",
            "add_string_resource","add_color_resource","list_resources");
        appendToolGroup(sb, all, "LIBRARIES",
            "list_libraries","add_library","remove_library",
            "attach_local_library","detach_local_library","download_dependency","validate_libraries");
        appendToolGroup(sb, all, "BUILD & COMPILE",
            "build_project","build_with_r8","set_build_compiler",
            "get_compile_logs","get_project_structure");
        appendToolGroup(sb, all, "EXPORT",
            "export_to_android_studio");
        appendToolGroup(sb, all, "UI TOOLS — USE THESE FOR ALL SCREEN CHANGES",
            "generate_layout", "add_view_xml", "describe_layout",
            "batch_patch_views", "replace_subtree", "add_view", "modify_view", "remove_view");
        sb.append("\n\u26a1 PREFERRED for UI generation:\n");
        sb.append("   generate_layout: full screen from description.\n");
        sb.append("   add_view_xml: append XML views to existing layout.\n");
        sb.append("   Both use ViewBeanParser → jC.c.put → live canvas reload.\n\n");
        appendToolGroup(sb, all, "CODE ANALYSIS & QUALITY",
            "analyze_code","review_source_code","validate_rtl_layout","analyze_build_error",
            "check_project_health","index_project","search_in_file");
        appendToolGroup(sb, all, "LIBRARY DISCOVERY",
            "search_maven","scan_dependencies","validate_gradle_dependency");
        appendToolGroup(sb, all, "APP TEMPLATES & LOCALIZATION",
            "create_from_template","add_locale_strings","create_locale_strings","extract_strings");
        appendToolGroup(sb, all, "DEVELOPER UTILITIES",
            "web_search","filter_logcat","analyze_unused_resources");
        appendToolGroup(sb, all, "GITHUB INTELLIGENCE",
            "github_compare","github_search");
        appendToolGroup(sb, all, "SURGICAL FILE EDITING",
            "patch_file","append_code","insert_code_at_line","read_file_range");
        appendToolGroup(sb, all, "RESOURCES — DRAWABLES & STRINGS",
            "create_drawable","extract_strings","create_locale_strings",
            "scan_unused_resources","delete_unused_resources",
            "read_raw_resource_file","write_raw_resource_file");
        appendToolGroup(sb, all, "BUILD REPAIR",
            "analyze_build_error","check_project_health","build_with_r8","set_build_compiler");

        // ── Any remaining tools not yet listed above ───────────────────────
        java.util.Set<String> listed = new java.util.HashSet<>(java.util.Arrays.asList(
            "list_projects","get_project_info","create_project","delete_project","duplicate_project",
            "read_file","write_file","delete_file","list_files","copy_file","move_file",
            "global_search","get_recent_logs",
            "list_activities","get_screen_source","create_activity","delete_activity",
            "get_layout","edit_layout","describe_layout","add_view","modify_view","remove_view",
            "add_view_xml","generate_layout","batch_patch_views","replace_subtree",
            "get_activity_events","get_event_blocks","add_block","modify_block","delete_block",
            "get_moreblocks","create_moreblock","delete_moreblock","describe_block_logic",
            "add_string_resource","add_color_resource","list_resources",
            "create_drawable","extract_strings","create_locale_strings",
            "scan_unused_resources","delete_unused_resources",
            "read_raw_resource_file","write_raw_resource_file",
            "list_libraries","add_library","remove_library",
            "attach_local_library","detach_local_library","download_dependency","validate_libraries",
            "search_maven","scan_dependencies","validate_gradle_dependency",
            "build_project","build_with_r8","set_build_compiler",
            "get_compile_logs","get_project_structure","export_to_android_studio",
            "analyze_build_error","check_project_health","index_project",
            "analyze_code","review_source_code","validate_rtl_layout",
            "search_in_file","patch_file","append_code","insert_code_at_line","read_file_range",
            "create_from_template","add_locale_strings",
            "web_search","filter_logcat","analyze_unused_resources",
            "github_compare","github_search"
        ));
        StringBuilder extras = new StringBuilder();
        for (AgentTool t : all) {
            if (!listed.contains(t.getName())) {
                extras.append("  ").append(t.getName()).append("\n");
                extras.append("      ").append(t.getDescription()).append("\n");
            }
        }
        if (extras.length() > 0) {
            sb.append("── OTHER TOOLS ──────────────────────────\n");
            sb.append(extras);
        }

        // ── Comprehensive tool routing rules (replaces vague "critical rules") ──
        sb.append("\n");
        sb.append("╔═══════════════════════════════════════════════════════════╗\n");
        sb.append("║         TOOL ROUTING — MANDATORY. NO EXCEPTIONS.         ║\n");
        sb.append("╠═══════════════════════════════════════════════════════════╣\n");
        sb.append("║                                                           ║\n");
        sb.append("║  ❌ PYTHON / SHELL ARE FORBIDDEN.                        ║\n");
        sb.append("║     Never write python code. Never use execute_shell      ║\n");
        sb.append("║     for anything. Never write <|python_tag|> or           ║\n");
        sb.append("║     any custom scripting tags. Use ONLY the tools below.  ║\n");
        sb.append("║                                                           ║\n");
        sb.append("╠═══════════════╦═══════════════════════════════════════════╣\n");
        sb.append("║ TASK          ║ MANDATORY TOOL (use ONLY this)           ║\n");
        sb.append("╠═══════════════╬═══════════════════════════════════════════╣\n");
        sb.append("║ Create UI     ║ generate_layout(sc_id, activity, desc)   ║\n");
        sb.append("║ Read UI       ║ describe_layout(sc_id, activity)         ║\n");
        sb.append("║ Edit UI       ║ describe_layout → generate_layout(       ║\n");
        sb.append("║               ║   current_layout=xml, desc=change)       ║\n");
        sb.append("║ Add/edit view ║ add_view_xml(sc_id, activity, xml,       ║\n");
        sb.append("║               ║   replace=false) ← DEFAULT, preserves   ║\n");
        sb.append("║               ║   existing views. replace=true only for  ║\n");
        sb.append("║               ║   full screen rebuild.                   ║\n");
        sb.append("║ Remove view   ║ remove_view(sc_id, activity, view_id)    ║\n");
        sb.append("║ Check RTL     ║ validate_rtl_layout(sc_id, activity)     ║\n");
        sb.append("╠═══════════════╬═══════════════════════════════════════════╣\n");
        sb.append("║ Read file     ║ read_file(path)                          ║\n");
        sb.append("║ Write file    ║ write_file(path, content)                ║\n");
        sb.append("║ Find in file  ║ execute_shell('grep -r ...')             ║\n");
        sb.append("║ List files    ║ list_files(directory)                    ║\n");
        sb.append("╠═══════════════╬═══════════════════════════════════════════╣\n");
        sb.append("║ Read logic    ║ get_event_blocks(sc_id, activity, event) ║\n");
        sb.append("║ Add block     ║ add_block(...) — always read first       ║\n");
        sb.append("║ Edit block    ║ modify_block(sc_id, ...)                 ║\n");
        sb.append("╠═══════════════╩═══════════════════════════════════════════╣\n");
        sb.append("║  BLOCK SPEC REFERENCE — exact values for add_block        ║\n");
        sb.append("╠═══════════════╦═══════════════════════════════════════════╣\n");
        sb.append("║ showToast     ║ spec='show toast %s.text'                ║\n");
        sb.append("║               ║ type=' '  parameters=['Hello']           ║\n");
        sb.append("║ addSrcDirect  ║ spec='add source directly %s.inputOnly'  ║\n");
        sb.append("║               ║ type=' '  parameters=['code;']           ║\n");
        sb.append("║ startActivity ║ spec='start activity %m.activity'        ║\n");
        sb.append("║               ║ type=' '  parameters=['activityName']    ║\n");
        sb.append("║ finish        ║ spec='finish'  type=' '  params=[]       ║\n");
        sb.append("║ setVariable   ║ spec='set %s.name = %s.value'            ║\n");
        sb.append("║  (String)     ║ type=' '  parameters=['varName','val']   ║\n");
        sb.append("║ ifElse        ║ spec='if %b.condition then'              ║\n");
        sb.append("║               ║ type=' '  parameters=['']                ║\n");
        sb.append("║ doWhile       ║ spec='repeat while %b.condition'         ║\n");
        sb.append("║               ║ type=' '  parameters=['']                ║\n");
        sb.append("║ showDialog    ║ spec='show message %s.text title %s.title'║\n");
        sb.append("║               ║ type=' '  parameters=['msg','title']     ║\n");
        sb.append("╠═══════════════╬═══════════════════════════════════════════╣\n");
        sb.append("║ Build APK     ║ build_project(sc_id)                     ║\n");
        sb.append("║ Unused res.   ║ scan_unused_resources → show user →      ║\n");
        sb.append("║               ║ delete_unused_resources(confirmed list)  ║\n");
        sb.append("║ Build R8/D8   ║ build_with_r8(sc_id)  — smaller APK     ║\n");
        sb.append("║ Set compiler  ║ set_build_compiler(sc_id, dexer=R8/D8)  ║\n");
        sb.append("║ Build errors  ║ get_compile_logs(sc_id)                  ║\n");
        sb.append("║ Add library   ║ add_library(sc_id, name, version)        ║\n");
        sb.append("╠═══════════════╬═══════════════════════════════════════════╣\n");
        sb.append("║ FORBIDDEN     ║ write_file for UI edits                  ║\n");
        sb.append("║ FORBIDDEN     ║ generate_layout for partial edits →      ║\n");
        sb.append("║               ║   use add_view_xml(replace=false) instead ║\n");
        sb.append("║ FORBIDDEN     ║ Python / shell scripts                   ║\n");
        sb.append("║ FORBIDDEN     ║ <|python_tag|> or any custom tags        ║\n");
        sb.append("║ FORBIDDEN     ║ get_layout / edit_layout (removed)       ║\n");
        sb.append("╚═══════════════╩═══════════════════════════════════════════╝\n");
        sb.append("\n");
        sb.append("WORKFLOW FOR UI EDIT:\n");
        sb.append("  1. describe_layout(sc_id=X, activity_name=Y)\n");
        sb.append("  2. generate_layout(sc_id=X, activity_name=Y, description='the change', current_layout=<xml from step 1>)\n");
        sb.append("  Done. Canvas updates automatically. No file writes needed.\n");
        sb.append("\n");
        sb.append("WORKFLOW FOR NEW UI:\n");
        sb.append("  1. generate_layout(sc_id=X, activity_name=Y, description='full description')\n");
        sb.append("  Done. No describe_layout needed for new screens.\n");
        sb.append("\n");
        // ── BUILD PIPELINE (professional, strict, no guessing) ────────────────
        sb.append("╔═══════════════════════════════════════════════════════════════╗\n");
        sb.append("║              BUILD SECTION — STRICT PIPELINE                  ║\n");
        sb.append("╚═══════════════════════════════════════════════════════════════╝\n");
        sb.append("\n");
        sb.append("PIPELINE A — STANDARD BUILD (D8, default):\n");
        sb.append("  ⚡ DO NOT DESCRIBE STEPS. EXECUTE TOOLS IMMEDIATELY IN ORDER.\n");
        sb.append("  ⚡ DO NOT SAY \"I will now run\". JUST CALL THE TOOL.\n");
        sb.append("  ⚡ DO NOT SAY \"Please wait\". JUST CALL THE TOOL.\n");
        sb.append("  ⚡ DO NOT SIMULATE RESULTS. WAIT FOR REAL TOOL OUTPUT.\n");
        sb.append("\n");
        sb.append("  [EXECUTE NOW — STEP 1] COMBINED CODE ANALYSIS:\n");
        sb.append("    CALL analyze_code(sc_id, file_path) for each Java file — DO IT NOW\n");
        sb.append("    CALL review_source_code(sc_id, file_path) for each Java file — DO IT NOW\n");
        sb.append("    Both in the SAME pass. Do not fix yet. Record real tool output only.\n");
        sb.append("\n");
        sb.append("  [EXECUTE NOW — STEP 2] BUILD:\n");
        sb.append("    CALL build_project(sc_id) — DO IT NOW\n");
        sb.append("    Do not report \"build succeeded\" before the tool returns a result.\n");
        sb.append("\n");
        sb.append("  [EXECUTE NOW — STEP 3 — only if build_project returned failure]:\n");
        sb.append("    CALL get_compile_logs(sc_id) — DO IT NOW\n");
        sb.append("    READ the real log output. Do NOT fabricate error messages.\n");
        sb.append("    DEDUPLICATE: strip line numbers, group identical messages, fix each ONCE.\n");
        sb.append("    Apply fix using the ERROR FIX ROUTING TABLE.\n");
        sb.append("    CALL build_project(sc_id) again — DO IT NOW.\n");
        sb.append("\n");
        sb.append("PIPELINE B — R8 BUILD (large project / APK size reduction):\n");
        sb.append("  ⚡ EXECUTE TOOLS. DO NOT NARRATE STEPS.\n");
        sb.append("  [EXECUTE NOW — STEP 1] CALL set_build_compiler(sc_id, dexer=\"R8\", parallel_ecj=true, java_version=\"1.8\")\n");
        sb.append("  [EXECUTE NOW — STEP 2] CALL build_with_r8(sc_id, parallel_ecj=true)\n");
        sb.append("  [IF FAILS] CALL get_compile_logs → apply fix → CALL build_with_r8 again\n");
        sb.append("  Use Pipeline B ONLY when: APK size reduction requested, project times out with D8, or user asks for R8.\n");
        sb.append("  NEVER mix Pipeline A and B for the same project.\n");
        sb.append("\n");
        sb.append("BUILD COMPILER SETTINGS (set_build_compiler):\n");
        sb.append("  dexer values : \"R8\" | \"D8\" | \"Dx\"\n");
        sb.append("  java_version : \"1.7\" | \"1.8\" | \"11\" | \"15\" | \"16\" | \"17\" | \"20\"\n");
        sb.append("  parallel_ecj : true | false\n");
        sb.append("  Default: dexer=\"D8\", java_version=\"1.8\", parallel_ecj=false\n");
        sb.append("\n");
        sb.append("ERROR DEDUPLICATION (mandatory before any fix):\n");
        sb.append("  1. Strip line numbers from messages (\":42: error\" → \":line: error\")\n");
        sb.append("  2. Group identical normalized messages\n");
        sb.append("  3. Fix each unique error ONCE — one fix may resolve multiple occurrences\n");
        sb.append("  4. Never re-fix the same error\n");
        sb.append("\n");
        sb.append("ERROR FIX ROUTING TABLE (use ONLY these paths per error type):\n");
        sb.append("  ┌─────────────────────────────────────────────────────────────────────┐\n");
        sb.append("  │ ERROR TYPE               → FIX TOOL + PATH                         │\n");
        sb.append("  ├─────────────────────────────────────────────────────────────────────┤\n");
        sb.append("  │ [STRINGS]                                                           │\n");
        sb.append("  │  string/xxx not found    → add_string_resource(sc_id, name, value) │\n");
        sb.append("  │  value typo in XML       → write_raw_resource_file                 │\n");
        sb.append("  │                            path: data/{sc_id}/files/resource/       │\n");
        sb.append("  │                                  values/strings.xml                 │\n");
        sb.append("  ├─────────────────────────────────────────────────────────────────────┤\n");
        sb.append("  │ [COLORS]                                                            │\n");
        sb.append("  │  color/xxx not found     → add_color_resource(sc_id, name, value)  │\n");
        sb.append("  │  color in wrong format   → write_raw_resource_file                 │\n");
        sb.append("  │                            path: data/{sc_id}/files/resource/       │\n");
        sb.append("  │                                  values/colors.xml                  │\n");
        sb.append("  ├─────────────────────────────────────────────────────────────────────┤\n");
        sb.append("  │ [DRAWABLES]                                                         │\n");
        sb.append("  │  drawable/xxx not found  → write_raw_resource_file                 │\n");
        sb.append("  │                            path: data/{sc_id}/files/resource/       │\n");
        sb.append("  │                                  drawable/xxx.xml                   │\n");
        sb.append("  ├─────────────────────────────────────────────────────────────────────┤\n");
        sb.append("  │ [STYLES / THEMES]                                                   │\n");
        sb.append("  │  style/xxx not found     → write_raw_resource_file                 │\n");
        sb.append("  │                            path: values/styles.xml                  │\n");
        sb.append("  │  theme/xxx not found     → write_raw_resource_file                 │\n");
        sb.append("  │                            path: values/themes.xml                  │\n");
        sb.append("  │  attribute conflict      → patch_file → styles.xml or themes.xml   │\n");
        sb.append("  ├─────────────────────────────────────────────────────────────────────┤\n");
        sb.append("  │ [FONTS]                                                             │\n");
        sb.append("  │  font/xxx not found      → write_raw_resource_file                 │\n");
        sb.append("  │                            path: data/{sc_id}/files/resource/       │\n");
        sb.append("  │                                  font/xxx.xml                       │\n");
        sb.append("  ├─────────────────────────────────────────────────────────────────────┤\n");
        sb.append("  │ [JAVA / KOTLIN]                                                     │\n");
        sb.append("  │  cannot find symbol      → patch_file or write_file                │\n");
        sb.append("  │                            path: mysc/{sc_id}/app/src/main/java/   │\n");
        sb.append("  │  package does not exist  → patch_file → fix import in .java        │\n");
        sb.append("  │  @id/ → @+id/ in XML     → patch_file → layout XML only           │\n");
        sb.append("  │  unused import           → patch_file → remove import line         │\n");
        sb.append("  │  setText(int) bug        → patch_file → setText(String.valueOf(x)) │\n");
        sb.append("  ├─────────────────────────────────────────────────────────────────────┤\n");
        sb.append("  │ [LAYOUTS / XML]                                                     │\n");
        sb.append("  │  resource id not found   → patch_file → layout XML in:             │\n");
        sb.append("  │                            mysc/{sc_id}/app/src/main/res/layout/   │\n");
        sb.append("  │  missing width/height    → patch_file → target layout XML          │\n");
        sb.append("  │  malformed XML           → write_file → rewrite specific XML only  │\n");
        sb.append("  ├─────────────────────────────────────────────────────────────────────┤\n");
        sb.append("  │ [LIBRARIES]                                                         │\n");
        sb.append("  │  compatibility error     → validate_libraries(sc_id)               │\n");
        sb.append("  │                            then remove_library or add_library       │\n");
        sb.append("  └─────────────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");
        sb.append("BUILD ABSOLUTE RULES:\n");
        sb.append("  ❌ NEVER describe what you are about to do — CALL THE TOOL DIRECTLY\n");
        sb.append("  ❌ NEVER say \"I will now run X\" — JUST CALL X\n");
        sb.append("  ❌ NEVER say \"Please wait\" or fabricate results before a tool returns\n");
        sb.append("  ❌ NEVER report success/failure before the tool actually returns output\n");
        sb.append("  • analyze_code + review_source_code always run TOGETHER — never one without the other\n");
        sb.append("  • Never call build_project and build_with_r8 for the same project in one session\n");
        sb.append("  • Never modify strings.xml via write_file — use add_string_resource or write_raw_resource_file\n");
        sb.append("  • Never modify colors.xml via write_file — use add_color_resource or write_raw_resource_file\n");
        sb.append("  • Never modify drawables/fonts/styles/themes via add_string_resource or add_color_resource\n");
        sb.append("  • Each resource type has ONE dedicated path — never mix them\n");
        sb.append("  • Never read a file from memory — always use read_file or read_raw_resource_file first\n");
        sb.append("  • clean_build=true only when same error persists after a fix cycle\n");
        sb.append("\n");

        // ── Destructive action guard ───────────────────────────────────────
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════\n");
        sb.append("  DESTRUCTIVE ACTIONS — REQUIRE CONFIRMATION\n");
        sb.append("═══════════════════════════════════════════════\n");
        sb.append("The following require explicit user confirmation before execution:\n");
        sb.append("  delete_project, duplicate_project, create_project\n");
        sb.append("  delete_activity, delete_file, delete_block, delete_moreblock\n");
        sb.append("If the user request is unclear or contains gibberish, ask for\n");
        sb.append("clarification. Never guess intent for destructive operations.\n");

        // ── Page context ───────────────────────────────────────────────────
        if (pageContext != null && !pageContext.trim().isEmpty()) {
            sb.append("\n");
            sb.append("═══════════════════════════════════════\n");
            sb.append("  LAUNCH CONTEXT\n");
            sb.append("═══════════════════════════════════════\n");
            switch (pageContext.trim()) {
                case "errors":
                    sb.append("Launched from: Compile Log screen\n");
                    sb.append("⚡ EXECUTE IMMEDIATELY. DO NOT NARRATE. DO NOT SAY \"I will\".\n");
                    sb.append("Action:\n");
                    sb.append("  CALL get_compile_logs(sc_id) NOW\n");
                    sb.append("  READ real output. DEDUPLICATE: strip line numbers, group identical.\n");
                    sb.append("  For each unique error apply the ERROR FIX ROUTING TABLE:\n");
                    sb.append("    strings → add_string_resource (values/strings.xml)\n");
                    sb.append("    colors  → add_color_resource (values/colors.xml)\n");
                    sb.append("    drawables → write_raw_resource_file (drawable/xxx.xml)\n");
                    sb.append("    styles → write_raw_resource_file (values/styles.xml)\n");
                    sb.append("    themes → write_raw_resource_file (values/themes.xml)\n");
                    sb.append("    fonts → write_raw_resource_file (font/xxx.xml)\n");
                    sb.append("    cannot find symbol / bad import → patch_file (java source)\n");
                    sb.append("    @id/ → @+id/ → patch_file (layout XML only)\n");
                    sb.append("  CALL build_project(sc_id) after all fixes. Repeat until success.\n");
                    sb.append("  FORBIDDEN: Never guess values. Read file first.\n");
                    break;
                case "blocks":
                    sb.append("Launched from: Custom Blocks Manager\n");
                    sb.append("User goal: Manage custom block definitions\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call get_moreblocks to list existing moreblocks\n");
                    sb.append("  2. Use create_moreblock / add_block to add logic\n");
                    sb.append("  3. Use modify_block / delete_moreblock to edit\n");
                    break;
                case "blocks_creator":
                    sb.append("Launched from: Blocks Creator screen\n");
                    sb.append("User goal: Create a complete set of custom blocks\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Ask user what kind of blocks they want\n");
                    sb.append("  2. Call create_moreblock for each function\n");
                    sb.append("  3. Use add_block with addSourceDirectly for Java code\n");
                    break;
                case "library_editor":
                case "libraries": {
                    // Parse sc_id from context lines if present
                    String libScId = null;
                    for (String cl : pageContext.split("\\n")) {
                        if (cl.startsWith("sc_id:")) libScId = cl.replace("sc_id:", "").trim();
                    }
                    sb.append("Launched from: Library Manager\n");
                    if (libScId != null && !libScId.isEmpty())
                        sb.append("Project sc_id: ").append(libScId).append("\n");
                    sb.append("User goal: Search, add, remove, and audit project libraries\n\n");
                    sb.append("AVAILABLE LIBRARY TOOLS:\n");
                    sb.append("  list_libraries(sc_id)          — show all enabled libraries\n");
                    sb.append("  validate_libraries(sc_id)      — check for conflicts/compatibility\n");
                    sb.append("  search_maven(query)            — find a library on Maven Central\n");
                    sb.append("  download_dependency(sc_id,...) — download + attach a Maven lib\n");
                    sb.append("  add_library(sc_id, name)       — enable a built-in library\n");
                    sb.append("  remove_library(sc_id, name)    — disable a library\n");
                    sb.append("  attach_local_library(sc_id,..) — attach a local .jar/.aar\n");
                    sb.append("  detach_local_library(sc_id,..) — detach a local library\n");
                    sb.append("  scan_dependencies(sc_id)       — scan for dependency issues\n\n");
                    sb.append("RULES:\n");
                    sb.append("  • Always call list_libraries first so you know what's already there\n");
                    sb.append("  • Call validate_libraries after adding/removing to confirm no conflicts\n");
                    sb.append("  • Never add a library without confirming the exact Maven coordinate\n");
                    break;
                }
                case "source_editor":
                    sb.append("Launched from: Source Code Editor\n");
                    sb.append("User goal: Review or improve Java source code\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call get_screen_source to read the current code\n");
                    sb.append("  2. Identify improvements (null safety, imports, etc.)\n");
                    sb.append("  3. Use write_file to apply the corrected source\n");
                    sb.append("  4. Call build_project to verify compilation\n");
                    break;
                case "design_editor":
                case "design_editor_with_context": // fall-through
                    sb.append("Launched from: Design Editor (DesignActivity)\n");
                    sb.append("User goal: Edit, generate, or improve the visual UI of the current screen.\n");
                    // ── Parse injected sc_id and current activity ──────────
                    String injectedScId = null;
                    String injectedXmlName = null;
                    String injectedActName = null;
                    boolean multipleProjects = false;
                    for (String contextLine : pageContext.split("\\n")) {
                        if (contextLine.startsWith("sc_id:")) {
                            injectedScId = contextLine.replace("sc_id:", "").trim();
                        } else if (contextLine.startsWith("current_activity:")) {
                            injectedActName = contextLine.replace("current_activity:", "").trim();
                        } else if (contextLine.startsWith("current_xml:")) {
                            injectedXmlName = contextLine.replace("current_xml:", "").trim();
                        } else if (contextLine.startsWith("project_count: multiple")) {
                            multipleProjects = true;
                        }
                    }
                    if (injectedScId != null && !injectedScId.isEmpty()) {
                        sb.append("Active project sc_id: ").append(injectedScId).append("\n");
                    }
                    if (injectedActName != null && !injectedActName.isEmpty()) {
                        sb.append("Currently open screen: ").append(injectedXmlName)
                          .append(" (activity_name=\"").append(injectedActName).append("\")\n");
                    }
                    sb.append("\n");
                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("  UI GENERATION — MANDATORY APPROACH\n");
                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("  RULE: GENERATE vs EDIT\n");
                    sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("• Creating NEW layout from scratch → generate_layout(description=\"...\")\n");
                    sb.append("  Do NOT include current_layout in description for new creation.\n");
                    sb.append("• EDITING/MODIFYING existing layout → FIRST call describe_layout\n");
                    sb.append("  then call generate_layout with description that includes\n");
                    sb.append("  current_layout=<xml from describe_layout> AND your changes.\n");
                    sb.append("  Or use add_view_xml with replace=false to add specific views.\n");
                    sb.append("\n");
                    sb.append("To CREATE or REPLACE an entire screen layout:\n");
                    sb.append("  → Use tool: generate_layout\n");
                    sb.append("  → Required params:\n");
                    final String safeScId = (injectedScId != null && !injectedScId.isEmpty()) ? injectedScId : "<sc_id>";
                    final String safeActName = (injectedActName != null && !injectedActName.isEmpty()) ? injectedActName : "<activity_name>";
                    sb.append("      sc_id:          \"").append(safeScId).append("\"\n");
                    sb.append("      activity_name:  \"").append(safeActName).append("\"\n");
                    sb.append("      description:    <natural language of desired UI>\n");
                    sb.append("  Example:\n");
                    sb.append("    generate_layout({\"sc_id\":\"").append(safeScId)
                      .append("\",\"activity_name\":\"").append(safeActName)
                      .append("\",\"description\":\"calculator with 4x4 button grid\"})\n");
                    sb.append("\n");
                    sb.append("To ADD specific views to existing layout:\n");
                    sb.append("  → Use tool: add_view_xml\n");
                    sb.append("  → Required params:\n");
                    sb.append("      sc_id:         \"").append(safeScId).append("\"\n");
                    sb.append("      activity_name: \"").append(safeActName).append("\"\n");
                    sb.append("      xml:           <Android XML snippet for the view>\n");
                    sb.append("      replace:       false (to merge) or true (to replace all)\n");
                    sb.append("\n");
                    sb.append("To READ current layout:\n");
                    sb.append("  → Use tool: describe_layout\n");
                    sb.append("      sc_id:         \"").append(safeScId).append("\"\n");
                    sb.append("      activity_name: \"").append(safeActName).append("\"\n");
                    sb.append("\n");
                    if (multipleProjects || injectedActName == null) {
                        sb.append("⚠ CONFIRMATION REQUIRED:\n");
                        sb.append("  If the user asks to update the UI but hasn't said WHICH screen,\n");
                        sb.append("  ASK: \"Which screen do you want me to update?\"\n");
                        sb.append("  If there is only one activity (the open one), act immediately without asking.\n");
                    } else {
                        sb.append("✅ You know the target screen: sc_id=\"").append(safeScId)
                          .append("\" activity_name=\"").append(safeActName).append("\"\n");
                        sb.append("  → Act immediately. No need to ask which screen.\n");
                    }
                    sb.append("\n");
                    sb.append("❌ FORBIDDEN for UI editing:\n");
                    sb.append("  - write_file to layout files (wrong format — Sketchware uses encrypted ViewBeans)\n");
                    sb.append("  - Manual JSON ViewBean construction\n");
                    sb.append("  - Using add_view / modify_view (these are JSON tools, not XML)\n");
                    sb.append("\n");
                    sb.append("After generating/editing the layout, the Design Editor canvas reloads automatically.\n");
                    break;
                case "resource_editor":
                    sb.append("Launched from: Resource Editor\n");
                    sb.append("User goal: Edit or add resources (strings, colors, drawables, layouts)\n");
                    sb.append("Action plan:\n");
                    sb.append("  1. Call list_resources to see current resources\n");
                    sb.append("  2. Use add_string_resource / add_color_resource for simple resources\n");
                    sb.append("  3. For raw XML resource files, use read_file + write_file\n");
                    sb.append("  4. When adding android:id in XML, always use @+id/ prefix\n");
                    sb.append("  5. Call build_project to verify\n");
                    break;
                case "build_fix":  // legacy alias — fall through
                case "error_repair": {
                    // Parse sc_id and mode from context lines
                    String repairScId   = null;
                    String repairMode   = "build_fix";
                    for (String cl : pageContext.split("\\n")) {
                        if (cl.startsWith("sc_id:"))  repairScId  = cl.replace("sc_id:", "").trim();
                        if (cl.startsWith("mode:"))   repairMode  = cl.replace("mode:", "").trim();
                    }
                    boolean isHealthCheck = "health_check".equals(repairMode);
                    String sid = (repairScId != null && !repairScId.isEmpty()) ? repairScId : "PROJECT_SC_ID";

                    sb.append("╔═══════════════════════════════════════════════════════════════╗\n");
                    sb.append(isHealthCheck
                            ? "║          PROJECT HEALTH CHECK MODE — MANDATORY               ║\n"
                            : "║            BUILD ERROR REPAIR MODE — MANDATORY               ║\n");
                    sb.append("╚═══════════════════════════════════════════════════════════════╝\n\n");
                    sb.append("sc_id = \"").append(sid).append("\"\n\n");
                    sb.append("⚡ YOU MUST CALL TOOLS NOW. DO NOT WRITE TEXT FIRST.\n");
                    sb.append("⚡ EVERY LINE MUST BE A TOOL CALL OR A DIRECT RESULT OF ONE.\n\n");

                    if (isHealthCheck) {
                        sb.append("MANDATORY SEQUENCE:\n\n");
                        sb.append("TOOL 1 → check_project_health(sc_id=\"").append(sid).append("\")\n");
                        sb.append("  Read the health report. It lists issues by severity.\n\n");
                        sb.append("TOOL 2..N → For each CRITICAL/HIGH issue in the report:\n");
                        sb.append("  missing resource   → add_string_resource / add_color_resource\n");
                        sb.append("  missing drawable   → create_drawable(sc_id, name, template)\n");
                        sb.append("  wrong layout name  → patch_file (R.layout.activity_X → R.layout.X)\n");
                        sb.append("  Material color     → patch_file (R.attr.colorX → com.google.android.material.R.attr.colorX)\n");
                        sb.append("  unused resource    → scan_unused_resources then delete_unused_resources\n");
                        sb.append("  library conflict   → validate_libraries → remove_library\n\n");
                        sb.append("FINAL TOOL → build_project(sc_id=\"").append(sid).append("\")\n");
                        sb.append("  If fails → run analyze_build_error(sc_id=\"").append(sid).append("\") and fix each step.\n\n");
                    } else {
                        sb.append("MANDATORY SEQUENCE — EXECUTE IN THIS EXACT ORDER:\n\n");
                        sb.append("TOOL 1 → analyze_build_error(sc_id=\"").append(sid).append("\")\n");
                        sb.append("  This returns a prioritized repair plan (Stage 1 first).\n");
                        sb.append("  READ EVERY STEP IN THE PLAN.\n\n");
                        sb.append("TOOL 2..N → Apply EACH step in the plan using the EXACT tool listed:\n\n");
                        sb.append("  STAGE 1 — AAPT/XML error:\n");
                        sb.append("    read_file(sc_id, path) → inspect XML → patch_file to fix malformed attribute\n\n");
                        sb.append("  STAGE 2 — Missing resource:\n");
                        sb.append("    missing string   → add_string_resource(sc_id=\"").append(sid).append("\", name=\"NAME\", value=\"VALUE\")\n");
                        sb.append("    missing color    → add_color_resource(sc_id=\"").append(sid).append("\", name=\"NAME\", value=\"#RRGGBB\")\n");
                        sb.append("    missing drawable → create_drawable(sc_id=\"").append(sid).append("\", name=\"NAME\", template=\"rounded_button\")\n");
                        sb.append("    wrong layout ref → patch_file(sc_id, path, old=\"R.layout.activity_X\", new=\"R.layout.X\")\n\n");
                        sb.append("  STAGE 3 — Material color attr:\n");
                        sb.append("    patch_file(sc_id=\"").append(sid).append("\", path=\"...\",\n");
                        sb.append("               old=\"R.attr.colorPrimary\", new=\"com.google.android.material.R.attr.colorPrimary\")\n");
                        sb.append("    (repeat for each colorX variable shown in plan)\n\n");
                        sb.append("  STAGE 4 — Missing import:\n");
                        sb.append("    search_maven(query=\"ClassName\") → download_dependency if found\n");
                        sb.append("    OR patch_file to add the import line\n\n");
                        sb.append("  STAGE 5 — Undeclared variable:\n");
                        sb.append("    read_file_range(sc_id, path, start, end) → patch_file to add declaration\n\n");
                        sb.append("  STAGE 6 — Syntax error:\n");
                        sb.append("    read_file_range → patch_file to add missing ) ; or }\n\n");
                        sb.append("FINAL TOOL → build_project(sc_id=\"").append(sid).append("\")\n");
                        sb.append("  If STILL FAILS → call analyze_build_error again and repeat.\n");
                        sb.append("  If SUCCEEDS    → reply with: ✅ Build successful — all errors fixed.\n\n");
                    }

                    sb.append("ABSOLUTE RULES (non-negotiable):\n");
                    sb.append("  ✅ Call analyze_build_error or check_project_health FIRST — every time\n");
                    sb.append("  ✅ Fix ALL stages before calling build_project (batch fixes, one build)\n");
                    sb.append("  ✅ For Java file edits, use patch_file (search+replace) not write_file\n");
                    sb.append("  ✅ read_file_range before patching if you're unsure of exact content\n");
                    sb.append("  ❌ NEVER write text like \"I will now...\" or \"Let me...\" without calling a tool\n");
                    sb.append("  ❌ NEVER call build_project between individual stage fixes\n");
                    sb.append("  ❌ NEVER use write_file to add strings/colors (use add_string_resource / add_color_resource)\n");
                    sb.append("  ❌ NEVER use R.attr.colorX — always com.google.android.material.R.attr.colorX\n");
                    sb.append("  ❌ NEVER use R.layout.activity_X — Sketchware uses R.layout.X (no 'activity_' prefix)\n");
                    break;
                }
                case "workspace_chat":
                case "workspace":
                    sb.append("Launched from: AI Workspace (general chat)\n");
                    sb.append("The user may ask to CREATE A COMPLETE APP from a description.\n\n");
                    sb.append("╔══════════════════════════════════════════════════════════════╗\n");
                    sb.append("║        FULL APP CREATION PIPELINE (Follow Exactly)          ║\n");
                    sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");
                    sb.append("When user says 'create an app' or describes an app idea:\n\n");
                    sb.append("STEP 1 — PROJECT SETUP:\n");
                    sb.append("  create_project(name, package, app_name)\n");
                    sb.append("  Capture sc_id from the result — use it for ALL subsequent calls.\n\n");
                    sb.append("STEP 2 — SCREENS (one create_activity per screen):\n");
                    sb.append("  create_activity(sc_id, activity_name=\"main\")   ← always lowercase, no 'Activity' suffix\n");
                    sb.append("  create_activity(sc_id, activity_name=\"settings\") ← etc.\n\n");
                    sb.append("STEP 3 — RESOURCES (strings, colors, drawables):\n");
                    sb.append("  add_string_resource for every text label and message\n");
                    sb.append("  add_color_resource for every color used in the UI\n");
                    sb.append("  create_drawable for buttons, backgrounds, icons (use templates)\n\n");
                    sb.append("STEP 4 — LAYOUTS (one generate_layout per screen):\n");
                    sb.append("  generate_layout(sc_id, activity_name, description=\"full UI description\")\n");
                    sb.append("  Use @string/xxx and @color/xxx — NOT hardcoded values.\n");
                    sb.append("  Use @drawable/xxx for backgrounds and button shapes.\n\n");
                    sb.append("STEP 5 — LOGIC (events + blocks per screen):\n");
                    sb.append("  get_activity_events → add_block for each user interaction\n");
                    sb.append("  Typical pattern per screen:\n");
                    sb.append("    add_block(event=onCreate) → initialize variables\n");
                    sb.append("    add_block(event=onClick_btnX) → action code\n\n");
                    sb.append("STEP 6 — BUILD + FIX LOOP:\n");
                    sb.append("  build_project(sc_id)\n");
                    sb.append("  IF fails → analyze_build_error(sc_id) → apply ALL fixes → build_project again\n");
                    sb.append("  Repeat until build succeeds.\n\n");
                    sb.append("RULES:\n");
                    sb.append("  ✅ DO all steps in order — never skip steps\n");
                    sb.append("  ✅ After layout → verify R.id references exist\n");
                    sb.append("  ✅ After adding strings → do NOT add same key again\n");
                    sb.append("  ✅ After adding events → add matching Java logic\n");
                    sb.append("  ❌ NEVER use hardcoded strings in layouts — always @string/\n");
                    sb.append("  ❌ NEVER use R.attr.colorX — use com.google.android.material.R.attr.colorX\n");
                    sb.append("  ❌ NEVER name activity 'MainActivity' — pass 'main' to create_activity\n");
                    break;
                default:
                    sb.append("Launch context: ").append(pageContext.trim()).append("\n");
                    break;
            }
        }

        // ── Scope (Global / Project / Page) ───────────────────────────────
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("  SEARCH & FILE ACCESS SCOPE\n");
        sb.append("═══════════════════════════════════════════════════\n");
        if (projectIds != null && projectIds.size() == 1) {
            String pid = projectIds.get(0);
            sb.append("Scope: PROJECT (sc_id=").append(pid).append(")\n");
            sb.append("• All file, layout, logic, and block operations are restricted to project ").append(pid).append(".\n");
            sb.append("• list_files / read_file / write_file: only paths inside sc_id=").append(pid).append(".\n");
            sb.append("• global_search: searches ONLY the files of project ").append(pid).append(", not other projects.\n");
            sb.append("• You CANNOT read, write, or create files in any other project.\n");
            if (pageContext != null && pageContext.startsWith("design_editor")) {
                sb.append("• Page scope: use generate_layout and add_view_xml — they write directly to the live canvas.\n");
            }
        } else if (projectIds != null && !projectIds.isEmpty()) {
            sb.append("Scope: GLOBAL WORKSPACE\n");
            sb.append("• Projects accessible: ").append(String.join(", ", projectIds)).append("\n");
            sb.append("• global_search: searches ALL projects in this workspace.\n");
            sb.append("• You can create, delete, duplicate, and cross-copy files between workspace projects.\n");
            sb.append("• Always specify sc_id when calling project-specific tools.\n");
        } else {
            sb.append("Scope: GLOBAL (no projects attached)\n");
            sb.append("• Create or add a project first before editing files or logic.\n");
        }

        return sb.toString();
    }

    /**
     * Appends a formatted tool group section to the system prompt.
     * Only includes tools that are actually registered in the current registry.
     */
    private void appendToolGroup(StringBuilder sb, java.util.List<AgentTool> all,
                                  String groupName, String... toolNames) {
        java.util.List<AgentTool> found = new java.util.ArrayList<>();
        for (String name : toolNames) {
            for (AgentTool t : all) {
                if (t.getName().equals(name)) { found.add(t); break; }
            }
        }
        if (found.isEmpty()) return;
        sb.append("── ").append(groupName).append(" ");
        int pad = 40 - groupName.length() - 3;
        for (int i = 0; i < pad; i++) sb.append("─");
        sb.append("\n");
        for (AgentTool t : found) {
            sb.append("  ").append(t.getName());
            int spaces = 30 - t.getName().length();
            for (int i = 0; i < spaces; i++) sb.append(" ");
            String desc = t.getDescription();
            int dot = desc.indexOf(". ");
            if (dot > 0 && dot < 80) desc = desc.substring(0, dot);
            if (desc.length() > 75) desc = desc.substring(0, 72) + "...";
            sb.append(desc).append("\n");
        }
        sb.append("\n");
    }

    /**
     * Builds a basic auto-fix prompt when AiFixSupport cannot resolve deeper context.
     * Truncates the error log to avoid token overflow.
     */
    private String buildBasicFixPrompt(String errOutput) {
        String shortErr = errOutput.trim();
        if (shortErr.length() > 1600) {
            shortErr = shortErr.substring(0, 800)
                    + "\n\n... [middle of log truncated to save tokens] ...\n\n"
                    + shortErr.substring(shortErr.length() - 800);
        }
        return "SYSTEM: Build failed. Follow these steps exactly — no questions, no comments:\n\n"
                + "STEP 1 — DEDUPLICATE errors before fixing:\n"
                + "  Strip line numbers from messages. Group identical normalized errors.\n"
                + "  Fix each unique error ONCE. Never fix the same error twice.\n\n"
                + "STEP 2 — Route each unique error to the correct fix path:\n"
                + "  strings → add_string_resource or write_raw_resource_file (values/strings.xml)\n"
                + "  colors  → add_color_resource or write_raw_resource_file (values/colors.xml)\n"
                + "  drawables → write_raw_resource_file (drawable/xxx.xml)\n"
                + "  styles/themes → write_raw_resource_file (values/styles.xml or themes.xml)\n"
                + "  fonts → write_raw_resource_file (font/xxx.xml)\n"
                + "  'cannot find symbol' → patch_file (Java source)\n"
                + "  '@id/' in XML → patch_file (layout XML: change @id/ to @+id/)\n"
                + "  setText(int) → patch_file (setText(String.valueOf(x)))\n"
                + "  library conflict → validate_libraries then remove_library/add_library\n\n"
                + "STEP 3 — run build_project after all fixes are applied.\n\n"
                + "RULES: Never use write_file for strings/colors. Never guess a missing value — "
                + "read the file first with read_raw_resource_file or read_file.\n\n"
                + "=== BUILD ERRORS ===\n" + shortErr + "\n=== END ===";
    }

    private void postError(AgentCallback callback, String error) {
        // Redact any sensitive data (API keys, tokens) before surfacing to UI or logs.
        String safeError = PromptSanitizer.redactForLog(error);
        sessionLogger.logError(safeError);
        mainHandler.post(() -> callback.onError(safeError));
    }

    /**
     * Finds the next available provider from FAILOVER_ORDER that has not been tried yet.
     * Uses a set of already-tried providers to prevent infinite failover cycles.
     */
    private static pro.sketchware.ai.models.AiProvider findFailoverProvider(
            java.util.Set<pro.sketchware.ai.models.AiProvider> tried, AiPreferences prefs) {
        for (pro.sketchware.ai.models.AiProvider p : FAILOVER_ORDER) {
            if (tried.contains(p)) continue;
            if (!prefs.isProviderEnabled(p)) continue;
            if (!p.requiresApiKey()) return p;
            if (prefs.hasApiKey(p)) return p;
        }
        return null;
    }

    private void postCancelled(AgentCallback callback) {
        mainHandler.post(callback::onCancelled);
    }

    public void shutdown() {
        isCancelled.set(true);
        AiApiClient client = currentClient;
        if (client != null) client.cancelAll();
        if (!executor.isShutdown()) executor.shutdownNow();
    }
}
