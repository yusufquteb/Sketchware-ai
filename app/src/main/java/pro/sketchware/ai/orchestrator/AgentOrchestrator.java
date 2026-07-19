package pro.sketchware.ai.orchestrator;

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

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.core.ModelCapabilities;
import pro.sketchware.ai.core.ProviderCapabilities;
import pro.sketchware.ai.core.ToolCallValidator;
import pro.sketchware.ai.engine.ProviderFailoverQueue;
import pro.sketchware.ai.engine.TokenEstimator;
import pro.sketchware.ai.engine.TokenOptimizer;
import pro.sketchware.ai.engine.approval.ApprovalCallback;
import pro.sketchware.ai.engine.approval.ApprovalManager;
import pro.sketchware.ai.engine.risk.ApprovalMode;
import pro.sketchware.ai.engine.validation.RuntimeToolValidator;
import pro.sketchware.ai.engine.validation.ToolValidationResult;
import pro.sketchware.ai.engine.validation.ToolValidator;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.offline.knowledge.KnowledgeBlockBuilder;
import pro.sketchware.ai.offline.knowledge.KnowledgeRetriever;
import pro.sketchware.ai.offline.knowledge.KnowledgeStore;
import pro.sketchware.ai.security.PromptSanitizer;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.ai.tools.ToolRegistry;

/**
 * Coordination layer sitting ABOVE the existing {@link pro.sketchware.ai.engine.AgentExecutor}.
 *
 * IMPORTANT — read before modifying:
 * {@code AgentExecutor} already runs its own autonomous, LLM-driven tool-calling
 * loop (the model decides which tools to call, one turn at a time, up to
 * {@code SAFETY_TOOL_ITERATION_LIMIT} iterations). This class does NOT replace,
 * wrap, or call into that loop. It is a separate, explicit "plan up front, then
 * execute the plan in order" mode: one LLM call produces a full ordered plan as
 * JSON, then every step is executed by calling {@link ToolRegistry} directly —
 * the same way {@code AgentExecutor.executeTool()} does, but without
 * AgentExecutor's per-turn streaming loop, failover queue, or pulse UI.
 *
 * This is a deliberate design decision (see CHANGES.md "Phase 2" section for the
 * full reasoning): the task described "a plan the orchestrator generates once and
 * executes in sequence," which is a different flow than AgentExecutor's turn-by-turn
 * "call a tool, see the result, decide the next tool" loop. Reusing AgentExecutor's
 * loop for this would have required either (a) faking a fixed tool-call sequence as
 * if the model produced it turn-by-turn, which defeats the point of a JSON plan, or
 * (b) modifying AgentExecutor's internals, which rule 3 of this phase's prompt
 * explicitly forbids. If a future phase decides the two flows should merge, that is
 * an explicit architectural decision for that phase, not an implicit side effect of
 * this one.
 *
 * Existing manual tool invocation via the BottomSheet UI (which goes through
 * AgentExecutor) is completely untouched by this class.
 *
 * ── Phase 4: self-correction loop ───────────────────────────────────────────
 * Phase 2 (see the step-execution loop below, and CHANGES.md) deliberately
 * stopped at the first step failure with no retry. Phase 4 closes that loop,
 * but ONLY for "build_project" step failures: on such a failure, control is
 * handed to {@link SelfCorrectionLoop}, which re-builds up to a hard,
 * explicit attempt cap (see {@link SelfCorrectionLoop#DEFAULT_MAX_CORRECTION_ATTEMPTS}
 * and its javadoc for why that default was chosen), attempting an LLM-generated
 * patch between each rebuild. Failures in any OTHER tool are unaffected and
 * still stop the plan immediately exactly as before -- broadening
 * self-correction to non-build steps is an explicit decision for a future
 * phase, not an implicit side effect of this one.
 *
 * ── Automatic run_and_verify_on_device (post-Phase-4 addendum) ─────────────
 * User decision, asked explicitly rather than assumed: after ANY successful
 * build_project step — first-try or via SelfCorrectionLoop — this class now
 * calls run_and_verify_on_device automatically as an extra, unplanned step,
 * instead of only running it when the model puts it in the plan itself. It
 * still goes through executeStep()'s normal approval gate (it's CRITICAL
 * risk), so it requires an ApprovalCallback to be attached via
 * setApprovalCallback()/the executeUserRequest(..., ApprovalCallback) overload
 * or it is denied by default — this addition does NOT bypass approval, it
 * just removes the requirement that the model has to think to ask for it. A
 * verify denial/failure never fails the overall plan (see runAutoVerify()).
 *
 * ── Approval gating (post-Phase-4 addendum) ─────────────────────────────────
 * executeStep() now resolves risk and requires approval for MEDIUM/CRITICAL
 * steps via the same ToolValidator/ApprovalManager pair AgentExecutor uses.
 * This closes a gap this class previously documented as open. Because this
 * class had no UI wiring, the open design question was: what should happen to
 * a CRITICAL step when no approval UI is attached? Answered explicitly (not
 * assumed) as: reuse ApprovalManager's existing callback==null behavior
 * (DENIED), and let a caller opt in to real approval prompts via a new
 * ApprovalCallback parameter on executeUserRequest() / setApprovalCallback() —
 * rather than either always denying with no way to opt in, or hard-stopping
 * plan generation itself. Default behavior with no callback attached remains
 * safe (deny), while leaving room for a future UI to attach one.
 */
public class AgentOrchestrator {

    /** Safety cap on plan size — mirrors the spirit of AgentExecutor's iteration limit. */
    private static final int MAX_PLAN_STEPS = 50;

    public interface Callback {

        /**
         * Point 6 (Orchestrator activation patch): fired for every raw text chunk
         * received from the planning-call model, BEFORE the plan JSON is fully
         * parsed. Mirrors AgentExecutor.AgentCallback#onStreamingChunk so a caller
         * (e.g. an AgentOrchestratorAiDelegate) can forward chunks straight into
         * ChatCoordinator.AiResponseCallback#onTokenReceived, reusing
         * ChatCoordinator's existing token-batching/typing-indicator logic
         * unchanged. Default no-op so existing callers do not need to implement it.
         */
        default void onPlanningChunk(String textDelta) {}

        /**
         * Point 7 (Orchestrator activation patch — final-response synthesis): fired for
         * every raw text chunk of the post-execution synthesis call (see
         * {@link #synthesizeFinalResponse}), analogous to onPlanningChunk but for the
         * natural-language answer generated AFTER all plan steps have run, not the JSON
         * plan itself. Default no-op so existing callers do not need to implement it.
         */
        default void onSynthesisChunk(String textDelta) {}

        /**
         * Point 7: fired once with the fully-synthesized natural-language answer to the
         * user's original request, built from the plan's step outputs — this is what a
         * caller should show as the assistant's final chat message instead of a bare
         * "Plan finished (N step(s))" status. Always fired before onPlanComplete on every
         * exit path (success, step failure, or correction exhaustion) so the user always
         * gets an explanation, not just a status. Not fired (skipped) only if synthesis
         * itself could not run at all (e.g. no API key / network failure) — a caller should
         * fall back to its own step-summary text in that case. Default no-op.
         */
        default void onFinalResponse(String text) {}

        /** Called once the plan has been generated and parsed, before execution starts. */
        void onPlanReady(ExecutionPlan plan);

        void onStepStarted(PlanStep step);

        void onStepCompleted(PlanStep step, ToolResult result);

        /** Called when the whole plan finished executing (all steps succeeded, or execution stopped on a failure). */
        void onPlanComplete(ExecutionPlan plan);

        /** Called when planning itself fails (bad LLM output, API error) — no steps were executed. */
        void onPlanningFailed(String error);

        /**
         * Called when a step fails. For non-build steps this is still followed
         * immediately by onPlanComplete (unchanged Phase 2 behavior -- no retry).
         * For a failed "build_project" step specifically, this is now followed by
         * the self-correction loop (Phase 4) -- see onCorrectionAttemptStarted etc.
         * below -- before onPlanComplete is finally called.
         */
        void onStepFailed(PlanStep step, String error);

        // -- Phase 4: self-correction loop callbacks -- only fired when a
        //    "build_project" step fails and self-correction is attempted. --

        /** A rebuild attempt is starting (attemptNumber is 1-indexed, up to maxAttempts). */
        default void onCorrectionAttemptStarted(int attemptNumber, int maxAttempts) {}

        /** The model proposed a patch for this attempt, about to be applied. */
        default void onPatchProposed(int attemptNumber, String patchSummary) {}

        /** Self-correction succeeded -- the project now builds. */
        default void onCorrected(int attemptsUsed) {}

        /** Self-correction hit the max attempt count without a successful build. */
        default void onExhausted(int attemptsUsed, String lastCompileLog) {}

        /** The patch-generation LLM call itself failed (network/API error, not a build error). */
        default void onCorrectionInfraFailure(int attemptNumber, String error) {}

        /** A snapshot was taken, or a rollback attempted, as part of self-correction. See SelfCorrectionLoop. */
        default void onSnapshotEvent(String message) {}

        // -- Automatic post-build verification (run_and_verify_on_device) --
        // Fired only after a build_project step succeeds (directly, or via
        // self-correction) — see "Automatic run_and_verify_on_device" note in
        // AgentOrchestrator's class javadoc for why this is automatic rather than
        // plan-authored.

        /** About to run run_and_verify_on_device automatically after a successful build. */
        default void onVerifyStarted(String scId) {}

        /** run_and_verify_on_device finished (success or failure) — this does NOT fail the plan either way. */
        default void onVerifyCompleted(String scId, ToolResult result) {}

        void onCancelled();
    }

    private final Context context;
    private final ToolRegistry toolRegistry;
    private final AiPreferences preferences;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    /** Phase 4 -- see class javadoc "Self-correction loop" note below. */
    private final SelfCorrectionLoop selfCorrectionLoop;

    /**
     * Approval gating (added after Phase 4's snapshot/rollback addendum, see
     * CHANGES.md "Approval gating" section). Same ToolValidator/ApprovalManager
     * pair AgentExecutor uses (ApprovalMode.BALANCED — MEDIUM+CRITICAL require
     * approval; matches AgentExecutor's own default in setApprovalCallback()).
     * approvalCallback is null unless a caller supplies one via the
     * executeUserRequest(..., ApprovalCallback) overload; ApprovalManager's
     * existing callback==null handling (deny rather than silently proceed) is
     * relied on as-is, not reimplemented here.
     */
    private final ToolValidator toolValidator = new ToolValidator(ApprovalMode.BALANCED);
    private volatile ApprovalCallback approvalCallback;
    private volatile ApprovalManager approvalManager = new ApprovalManager(ApprovalMode.BALANCED, null);
    private volatile pro.sketchware.ai.engine.snapshot.ProjectSnapshotManager cachedSnapshotManager;

    private volatile AiApiClient currentClient;

    /**
     * Shared knowledge store for {@link #buildPlanningSystemPrompt}, via {@link
     * KnowledgeRetriever} — same wiring pattern as {@code AgentExecutor.knowledgeStore} and
     * {@code LocalModelProvider.knowledgeStore}, so all three engines pull from the same
     * seeded/user-added project knowledge (see {@code KnowledgeRetriever}'s javadoc: "the two
     * engines never drift into two different notions of what the assistant knows" — this makes
     * it three). Lazily created on first use rather than in the constructor, matching
     * AgentExecutor's own lazy-init reasoning (avoids a SQLite open on the constructor path,
     * which can run before the app's first frame).
     */
    private KnowledgeStore knowledgeStore;

    /**
     * Planning-path budget for {@link KnowledgeRetriever}'s knowledge block. Mirrors {@code
     * AgentExecutor.ONLINE_KNOWLEDGE_BLOCK_MAX_TOKENS} — this class only ever talks to cloud
     * providers (see {@code executeUserRequest}'s {@code AiProvider}/{@code modelId}
     * parameters), never the on-device model, so the same generous cloud-path budget applies
     * rather than {@code LocalModelProvider}'s tighter 300-token offline budget.
     */
    private static final int PLANNING_KNOWLEDGE_BLOCK_MAX_TOKENS = 1800;

    public AgentOrchestrator(Context context, ToolRegistry toolRegistry) {
        this.context = context.getApplicationContext();
        this.toolRegistry = toolRegistry;
        this.preferences = AiPreferences.getInstance(this.context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.selfCorrectionLoop = new SelfCorrectionLoop(this.context, toolRegistry);
    }

    /**
     * Optionally wires an approval UI. Mirrors AgentExecutor.setApprovalCallback():
     * must be called before executeUserRequest() if approval prompts should reach
     * a real UI instead of being denied by default. Safe to call again to replace
     * or clear (pass null) the callback between runs.
     */
    public void setApprovalCallback(ApprovalCallback callback) {
        this.approvalCallback = callback;
        this.approvalManager = new ApprovalManager(ApprovalMode.BALANCED, callback);
    }

    private pro.sketchware.ai.engine.snapshot.ProjectSnapshotManager snapshotManager(ToolContext toolContext) {
        if (cachedSnapshotManager == null) {
            cachedSnapshotManager = new pro.sketchware.ai.engine.snapshot.ProjectSnapshotManager(toolContext);
        }
        return cachedSnapshotManager;
    }

    /**
     * Single public entry point. UI wiring for plan progress is still the
     * caller's responsibility via {@code callback} (see CHANGES.md, "Phase 2 —
     * UI wiring is out of scope" — that scope note still applies to progress
     * display; it does NOT apply to approval gating, which is now enforced
     * regardless of whether a UI is attached — see "Approval gating" below).
     *
     * @param prompt           the user's natural-language request
     * @param allowedProjectIds projects the resulting tool calls may touch (same
     *                          meaning as AgentExecutor's allowedProjectIds)
     * @param workspaceId       same meaning as AgentExecutor's workspaceId
     * @param provider          which AI provider to use for the planning call
     * @param modelId           which model to use for the planning call
     * @param callback          progress/result callback, invoked on the main thread
     */
    public void executeUserRequest(String prompt, List<String> allowedProjectIds,
                                    String workspaceId, AiProvider provider, String modelId,
                                    Callback callback) {
        executeUserRequest(prompt, allowedProjectIds, workspaceId, provider, modelId, callback, null);
    }

    /**
     * Point 1 (Orchestrator activation patch): history-aware overload. Prior
     * conversation turns are passed through {@link TokenOptimizer#optimise}
     * (summarise → truncate tool results → provider-aware budget cap) before
     * being sent to the planning call, exactly like AgentExecutor.execute()
     * already does for normal chat turns. Without this, generatePlan() only
     * ever saw the current prompt in isolation.
     *
     * @param history prior conversation turns (may be null/empty for a
     *                fresh conversation — behaves like the no-history overload)
     */
    public void executeUserRequest(String prompt, List<ChatMessage> history,
                                    List<String> allowedProjectIds, String workspaceId,
                                    AiProvider provider, String modelId,
                                    Callback callback, ApprovalCallback approvalCallback) {
        setApprovalCallback(approvalCallback);
        isCancelled.set(false);
        executor.execute(() -> {
            ExecutionPlan plan;
            try {
                plan = generatePlan(prompt, history, provider, modelId, callback);
            } catch (ExecutionPlan.PlanParseException e) {
                mainHandler.post(() -> callback.onPlanningFailed(PromptSanitizer.redactForLog(e.getMessage())));
                return;
            } catch (Exception e) {
                mainHandler.post(() -> callback.onPlanningFailed(
                        "Planning request failed: " + PromptSanitizer.redactForLog(e.getMessage())));
                return;
            }
            if (isCancelled.get()) { mainHandler.post(callback::onCancelled); return; }

            if (plan.getSteps().size() > MAX_PLAN_STEPS) {
                mainHandler.post(() -> callback.onPlanningFailed(
                        "Plan has " + plan.getSteps().size() + " steps, exceeding the safety cap of "
                        + MAX_PLAN_STEPS + ". Ask for a narrower request."));
                return;
            }

            mainHandler.post(() -> callback.onPlanReady(plan));
            runPlan(plan, allowedProjectIds, workspaceId, provider, modelId, callback);
        });
    }

    /**
     * Same as the 6-argument overload, but lets the caller attach an
     * ApprovalCallback for this run so CRITICAL/MEDIUM steps (e.g.
     * run_and_verify_on_device, restore_snapshot) can actually prompt a real UI
     * instead of being denied by default. Equivalent to calling
     * setApprovalCallback(approvalCallback) immediately before this. Passing
     * null here behaves exactly like the 6-argument overload (deny-by-default —
     * see ApprovalManager.requestApproval()'s existing callback==null handling).
     *
     * Design choice (asked explicitly rather than assumed, per this phase's
     * rules): approval defaults to DENIED when no callback is attached, not to
     * silent auto-approval and not to a plan-time hard stop — reusing
     * ApprovalManager's existing "no callback → deny" branch as-is keeps this
     * orchestrator's approval behavior identical to AgentExecutor's, rather than
     * inventing a second policy for the same risk model.
     */
    public void executeUserRequest(String prompt, List<String> allowedProjectIds,
                                    String workspaceId, AiProvider provider, String modelId,
                                    Callback callback, ApprovalCallback approvalCallback) {
        // Delegates to the history-aware overload with an empty history, so the
        // step-execution loop (self-correction, auto-verify, approval gating)
        // exists in exactly one place — see runPlan() below (Orchestrator
        // activation patch, point 1: this also means callers that don't pass
        // history explicitly get the safe empty-list default rather than null).
        executeUserRequest(prompt, java.util.Collections.emptyList(), allowedProjectIds,
                workspaceId, provider, modelId, callback, approvalCallback);
    }

    /**
     * Shared step-execution loop, extracted so both executeUserRequest overloads
     * run identical logic (self-correction, auto-verify, approval gating) instead
     * of duplicating it. No behavior change from the original inline loop.
     */
    private void runPlan(ExecutionPlan plan, List<String> allowedProjectIds, String workspaceId,
                          AiProvider provider, String modelId, Callback callback) {
        ToolContext toolContext = new ToolContext(context, allowedProjectIds, workspaceId);
        toolContext.setCancellationChecker(isCancelled::get);

        for (PlanStep step : plan.getSteps()) {
            if (isCancelled.get()) { mainHandler.post(callback::onCancelled); return; }

            step.setStatus(PlanStep.Status.RUNNING);
            mainHandler.post(() -> callback.onStepStarted(step));

            ToolResult result = executeStep(step, toolContext);

            if (result.isSuccess()) {
                step.setStatus(PlanStep.Status.SUCCEEDED);
                step.setOutput(result.getOutput());
                mainHandler.post(() -> callback.onStepCompleted(step, result));
                if ("build_project".equals(step.getToolName())) {
                    runAutoVerify(extractScId(step), toolContext, callback);
                }
            } else if ("build_project".equals(step.getToolName())) {
                // Phase 4: build failures get one self-correction pass instead of
                // stopping immediately. Every other tool's failure below still
                // stops the plan exactly as in Phase 2 -- see class javadoc.
                mainHandler.post(() -> callback.onStepFailed(step, PromptSanitizer.redactForLog(result.getError())));

                ToolResult correctionResult = selfCorrectionLoop.run(
                        extractScId(step), toolContext, provider, modelId,
                        new SelfCorrectionLoop.Listener() {
                            @Override public void onCorrectionAttemptStarted(int attemptNumber, int maxAttempts) {
                                mainHandler.post(() -> callback.onCorrectionAttemptStarted(attemptNumber, maxAttempts));
                            }
                            @Override public void onPatchProposed(int attemptNumber, String patchSummary) {
                                mainHandler.post(() -> callback.onPatchProposed(attemptNumber, patchSummary));
                            }
                            @Override public void onCorrected(int attemptsUsed) {
                                mainHandler.post(() -> callback.onCorrected(attemptsUsed));
                            }
                            @Override public void onExhausted(int attemptsUsed, String lastCompileLog) {
                                mainHandler.post(() -> callback.onExhausted(attemptsUsed, lastCompileLog));
                            }
                            @Override public void onCorrectionInfraFailure(int attemptNumber, String error) {
                                mainHandler.post(() -> callback.onCorrectionInfraFailure(attemptNumber, error));
                            }
                            @Override public void onSnapshotEvent(String message) {
                                mainHandler.post(() -> callback.onSnapshotEvent(message));
                            }
                        });

                if (correctionResult.isSuccess()) {
                    step.setStatus(PlanStep.Status.SUCCEEDED);
                    step.setOutput(correctionResult.getOutput());
                    mainHandler.post(() -> callback.onStepCompleted(step, correctionResult));
                    runAutoVerify(extractScId(step), toolContext, callback);
                    // continue the plan -- fall through to the next PlanStep in the for-loop
                } else {
                    step.setStatus(PlanStep.Status.FAILED);
                    step.setError(correctionResult.getError());
                    finishPlan(plan, provider, modelId, callback);
                    return;
                }
            } else {
                step.setStatus(PlanStep.Status.FAILED);
                step.setError(result.getError());
                // No self-correction for non-build tools in this phase -- stop and
                // report, same as Phase 2 (rule from Phase 2's prompt, unchanged here).
                mainHandler.post(() -> callback.onStepFailed(step, PromptSanitizer.redactForLog(result.getError())));
                finishPlan(plan, provider, modelId, callback);
                return;
            }
        }
        finishPlan(plan, provider, modelId, callback);
    }

    public void cancel() {
        isCancelled.set(true);
        AiApiClient client = currentClient;
        if (client != null) client.cancelAll();
        executor.shutdownNow();
    }

    /**
     * Point 7 (Orchestrator activation patch — final-response synthesis): the single exit
     * point for runPlan(), on every path (success, step failure, correction exhaustion).
     * Runs synthesizeFinalResponse() first so the user gets an actual natural-language
     * answer via onFinalResponse() before onPlanComplete() fires the existing
     * status/bookkeeping callback. If synthesis can't run at all (no key, network failure),
     * onFinalResponse() is simply skipped — the caller already has its own step-summary
     * fallback text for that case, so this never fails the plan itself.
     */
    private void finishPlan(ExecutionPlan plan, AiProvider provider, String modelId, Callback callback) {
        if (isCancelled.get()) { mainHandler.post(callback::onCancelled); return; }
        String finalText = synthesizeFinalResponse(plan, provider, modelId, callback);
        if (finalText != null && !finalText.isEmpty()) {
            mainHandler.post(() -> callback.onFinalResponse(finalText));
        }
        mainHandler.post(() -> callback.onPlanComplete(plan));
    }

    /**
     * Point 7: makes one more LLM call after all plan steps have run — the user's original
     * request plus a compact transcript of each step's tool name/arguments/result — and asks
     * for a plain conversational answer (no JSON, no step numbers). This closes a real gap:
     * ExecutionPlan only ever carried steps, never a synthesized answer, so a descriptive
     * request like "describe the current layout" had nothing to answer it with beyond the
     * raw plan JSON or a bare "Plan finished (N step(s))" — this call is what actually
     * answers the user's question using the tool results as source of truth.
     *
     * Single attempt, no failover queue (unlike generatePlan) — this is a best-effort
     * follow-up on top of work that already succeeded, not the primary request, so on any
     * failure it returns null and the caller keeps its own fallback text rather than making
     * the user wait through multiple provider retries for a courtesy summary.
     *
     * @return the synthesized answer, or null if synthesis could not run/complete at all
     */
    private String synthesizeFinalResponse(ExecutionPlan plan, AiProvider provider, String modelId, Callback callback) {
        StringBuilder transcript = new StringBuilder();
        for (PlanStep step : plan.getSteps()) {
            transcript.append("- ").append(step.getToolName());
            if (step.getArguments() != null) transcript.append(' ').append(step.getArguments());
            transcript.append(" -> ");
            if (step.getStatus() == PlanStep.Status.SUCCEEDED) {
                String out = step.getOutput();
                transcript.append(out != null && !out.isEmpty() ? PromptSanitizer.redactForLog(out) : "(no output)");
            } else {
                transcript.append("FAILED: ").append(PromptSanitizer.redactForLog(
                        step.getError() != null ? step.getError() : "unknown error"));
            }
            transcript.append('\n');
        }

        String systemPrompt =
                "You just executed a plan of tool calls on the user's Android project on the user's behalf. " +
                "Write a clear, direct, conversational answer to the user's original request below, using the " +
                "tool results as your source of truth. Do not output JSON, step numbers, or tool names — answer " +
                "as if you did the work yourself, in plain prose. Reply in the same language as the user's " +
                "original request.";
        String userPrompt = "User's original request: " + plan.getUserRequest()
                + "\n\nTool results from executing the plan:\n" + transcript;

        String apiKey = preferences.getApiKey(provider);
        if (provider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) return null;
        AiApiClient client = AiClientFactory.createClient(context, provider, apiKey);
        if (client == null) return null;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(null, userPrompt));

        StringBuilder responseBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        String[] errorHolder = new String[1];

        client.sendChatRequest(messages, modelId, systemPrompt, new StreamingResponseHandler() {
            @Override public void onChunk(String textDelta) {
                if (textDelta == null) return;
                responseBuilder.append(textDelta);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSynthesisChunk(textDelta));
                }
            }
            @Override public void onToolCall(pro.sketchware.ai.models.ToolCall toolCall) {
                // Synthesis call sends no tool definitions; a well-behaved model should
                // never emit one here. Ignored defensively if it does.
            }
            @Override public void onComplete(String response) { latch.countDown(); }
            @Override public void onError(String error) { errorHolder[0] = error; latch.countDown(); }
        });

        try {
            long timeoutMs = preferences.getRequestTimeoutSecs() * 1000L;
            if (timeoutMs <= 0) timeoutMs = 120_000L;
            boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            client.shutdown();
            if (!completed || errorHolder[0] != null || responseBuilder.length() == 0) return null;
            return responseBuilder.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Pulls sc_id out of a build_project step's arguments for the self-correction
     * loop. build_project's schema requires sc_id (see BuildTools.BuildProjectTool
     * .getParametersSchema()), and executeStep()'s schema validation already ran
     * before this step could have reached execution, so this is expected to
     * always be present for a build_project step that got this far -- the empty
     * fallback only guards against a null/malformed value defensively.
     */
    private String extractScId(PlanStep step) {
        JsonObject args = step.getArguments();
        if (args != null && args.has("sc_id") && !args.get("sc_id").isJsonNull()) {
            return args.get("sc_id").getAsString();
        }
        return "";
    }

    /**
     * Automatic post-build verification (user decision, not assumed): after
     * ANY successful build_project — whether it succeeded on the first try or
     * only after SelfCorrectionLoop patched it — run_and_verify_on_device is
     * invoked automatically as an extra step, rather than only running when the
     * model explicitly planned it. It goes through the exact same executeStep()
     * path as a normal plan step, so it is STILL approval-gated: since it's
     * CRITICAL risk, it requires an attached ApprovalCallback or it is denied
     * (see executeStep's approval gate). A verify failure or denial does NOT
     * fail the overall plan — this is a best-effort safety check layered on top
     * of a build that already succeeded, not a plan prerequisite; the plan's
     * success/failure still hinges only on the actual planned steps.
     */
    private void runAutoVerify(String scId, ToolContext toolContext, Callback callback) {
        if (scId == null || scId.isEmpty()) return;
        if (toolRegistry.getTool("run_and_verify_on_device") == null) return;
        if (isCancelled.get()) return;

        mainHandler.post(() -> callback.onVerifyStarted(scId));

        JsonObject args = new JsonObject();
        args.addProperty("sc_id", scId);
        PlanStep verifyStep = new PlanStep(-1, "run_and_verify_on_device", args,
                "Automatic post-build crash-on-launch check");

        ToolResult verifyResult = executeStep(verifyStep, toolContext);
        mainHandler.post(() -> callback.onVerifyCompleted(scId, verifyResult));
    }

    // ── Planning ─────────────────────────────────────────────────────────────

    /**
     * Makes one non-streaming-style (but still delivered via the streaming
     * client — see class javadoc) LLM call asking for a full plan as JSON, then
     * parses it. Blocks the calling thread (already off the main thread here)
     * until the model finishes or errors.
     */
    /**
     * Point 1: prior conversation history is now optimised via
     * {@link TokenOptimizer#optimise(List, AiProvider)} (summarise old turns,
     * truncate bulky tool results, cap to a provider-aware message budget)
     * before being sent — previously this method saw ONLY the current prompt,
     * so a plan could be generated with no awareness of what was already
     * discussed or already built in this conversation.
     *
     * Point 4: on failure (network error, bad key, model-not-found, etc.) this
     * now retries down {@link ProviderFailoverQueue}'s ordered list of
     * (provider, model) pairs instead of throwing on the very first failure —
     * identical failover behavior to AgentExecutor.execute()'s tool-calling loop.
     *
     * Point 5: any error surfaced to the caller is redacted via
     * {@link PromptSanitizer#redactForLog} first (API keys/tokens must never
     * reach UI or logs verbatim).
     *
     * Point 6: if {@code callback} is non-null, every raw chunk from the model
     * is forwarded live via {@link Callback#onPlanningChunk} as it streams in,
     * in addition to being accumulated for the final JSON parse — so a caller
     * can show "thinking" text instead of a silent block until the whole plan
     * is ready.
     *
     * @param history conversation history so far; may be null/empty
     * @param callback optional — if provided, receives live onPlanningChunk events
     */
    private ExecutionPlan generatePlan(String prompt, List<ChatMessage> history,
                                        AiProvider provider, String modelId, Callback callback)
            throws Exception {

        List<ProviderFailoverQueue.ProviderModelPair> failoverQueue =
                ProviderFailoverQueue.build(provider, modelId, preferences);

        Exception lastError = null;
        for (ProviderFailoverQueue.ProviderModelPair attempt : failoverQueue) {
            if (isCancelled.get()) throw new Exception("Cancelled.");
            try {
                return attemptGeneratePlan(prompt, history, attempt.provider, attempt.modelId, callback);
            } catch (ExecutionPlan.PlanParseException e) {
                // A malformed JSON response is a model-quality issue, not a
                // connectivity issue — retrying with a different model can
                // genuinely help here, so this also falls through to failover
                // rather than being treated as fatal on the first provider.
                lastError = e;
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new Exception(lastError != null
                ? PromptSanitizer.redactForLog(lastError.getMessage())
                : "All providers/models exhausted without a successful planning response.");
    }

    /** Single (provider, model) planning attempt — no failover inside this method. */
    private ExecutionPlan attemptGeneratePlan(String prompt, List<ChatMessage> history,
                                               AiProvider provider, String modelId, Callback callback)
            throws Exception {
        String apiKey = preferences.getApiKey(provider);
        if (provider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
            throw new Exception("No API key set for " + provider.getDisplayName());
        }
        currentClient = AiClientFactory.createClient(context, provider, apiKey);
        if (currentClient == null) {
            throw new Exception("Failed to create API client for " + provider.getDisplayName());
        }

        // Point 1: run the same optimisation pipeline AgentExecutor.execute() runs
        // on chat turns, so planning respects this provider's real context window.
        List<ChatMessage> messages = new ArrayList<>(
                TokenOptimizer.optimise(new ArrayList<>(history != null ? history : java.util.Collections.emptyList()), provider));
        messages.add(new ChatMessage(null, prompt));

        StringBuilder responseBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        String[] errorHolder = new String[1];

        currentClient.sendChatRequest(messages, modelId, buildPlanningSystemPrompt(provider, modelId, prompt),
                new StreamingResponseHandler() {
                    @Override public void onChunk(String textDelta) {
                        if (textDelta == null) return;
                        responseBuilder.append(textDelta);
                        if (callback != null) {
                            mainHandler.post(() -> callback.onPlanningChunk(textDelta));
                        }
                    }
                    @Override public void onToolCall(pro.sketchware.ai.models.ToolCall toolCall) {
                        // Planning call intentionally sends no tool definitions (see buildPlanningSystemPrompt);
                        // a well-behaved model should never emit one here. Ignored defensively if it does.
                    }
                    @Override public void onComplete(String response) { latch.countDown(); }
                    @Override public void onError(String error) { errorHolder[0] = error; latch.countDown(); }
                });

        long timeoutMs = preferences.getRequestTimeoutSecs() * 1000L;
        if (timeoutMs <= 0) timeoutMs = 120_000L;
        boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        currentClient.shutdown();
        currentClient = null;

        if (!completed) throw new Exception("Planning request timed out.");
        if (errorHolder[0] != null) throw new Exception(PromptSanitizer.redactForLog(errorHolder[0]));

        return ExecutionPlan.fromLlmOutput(responseBuilder.toString(), prompt);
    }

    /**
     * Points 2 + 3: the planning prompt used to unconditionally dump every
     * registered tool's name, description, AND full JSON parameter schema
     * (106+ tools) — easily large enough to blow a small-context provider's
     * entire budget before a single history message is even added. This now:
     *  1. Resolves effective capabilities at the MODEL level via
     *     {@link ModelCapabilities#resolve} (not just the provider default),
     *     matching AgentExecutor.execute()'s existing per-request capability gate.
     *  2. Builds the full name+description+schema listing first, and only
     *     degrades if it doesn't fit: first dropping per-tool schemas (name+
     *     description only — the ToolCallValidator + RuntimeToolValidator in
     *     executeStep() still validate arguments against the REAL schema at
     *     execution time, so omitting it from the prompt loses no safety
     *     guarantee, only some planning precision).
     *  3. If STILL over budget after dropping all schemas (only plausible with
     *     a very small context window and a very large tool catalog), truncates
     *     to the tools that fit and appends a note pointing the model at
     *     list_tools (the existing discovery entry point — see
     *     ToolRegistry.getEssentialTools() javadoc) instead of silently
     *     producing a request that overflows the provider's context.
     */
    private String buildPlanningSystemPrompt(AiProvider provider, String modelId, String userPrompt) {
        String header = "You are a planning module for an Android-development AI agent. "
          + "Given a user request, output ONE JSON object and nothing else — "
          + "no prose, no markdown fences, no explanation before or after it.\n\n"
          + "JSON shape:\n"
          + "{\"steps\":[{\"tool\":\"<tool_name>\",\"arguments\":{...},\"description\":\"<short human-readable reason>\"}]}\n\n"
          + "Rules:\n"
          + "- Use ONLY tool names from the list below. Never invent a tool name.\n"
          + "- \"arguments\" must match that tool's parameter schema exactly.\n"
          + "- Order steps so each one's prerequisites (e.g. a project or activity created by an earlier step) already exist.\n"
          + "- If the request cannot be done with the tools below, return {\"steps\":[]}.\n"
          + "- If a tool you need isn't listed, call list_tools first to discover the full catalog.\n\n"
          + "Available tools:\n";

        ProviderCapabilities caps = ModelCapabilities.resolve(
                provider, modelId, ProviderCapabilities.of(provider));
        // Reserve half the context window for the tool catalog; the rest is left
        // for optimised history + the user prompt + the model's JSON response.
        int toolBudgetTokens = Math.max(500, caps.maxContextTokens / 2);

        List<AgentTool> tools = toolRegistry.getAllTools();

        String toolCatalog;
        // Tier 1: full name + description + schema.
        String full = header + renderTools(tools, /* includeSchema= */ true);
        if (TokenEstimator.estimate(full, provider) <= toolBudgetTokens) {
            toolCatalog = full;
        } else {
            // Tier 2: drop per-tool schemas — execution-time validation still enforces
            // them via ToolCallValidator/RuntimeToolValidator in executeStep().
            String namesOnly = header + renderTools(tools, /* includeSchema= */ false);
            if (TokenEstimator.estimate(namesOnly, provider) <= toolBudgetTokens) {
                toolCatalog = namesOnly;
            } else {
                // Tier 3: truncate to as many tools as fit, point the model at list_tools
                // for the rest (mirrors AgentExecutor's essential-tools-plus-list_tools pattern).
                StringBuilder sb = new StringBuilder(header);
                for (AgentTool tool : tools) {
                    String line = "- " + tool.getName() + ": " + tool.getDescription() + "\n";
                    if (TokenEstimator.estimate(sb.toString() + line, provider) > toolBudgetTokens) break;
                    sb.append(line);
                }
                sb.append("(Additional tools exist beyond this list — call list_tools to discover them.)\n");
                toolCatalog = sb.toString();
            }
        }

        // Knowledge layer wiring: same KnowledgeStore-backed CRITICAL/NORMAL entries
        // AgentExecutor.buildCompactSystemPrompt and LocalModelProvider.buildPromptFromMessages
        // already surface, via the shared KnowledgeRetriever entry point all three engines are
        // meant to call (see KnowledgeRetriever's javadoc). CRITICAL entries (e.g. project-wide
        // rules) are included unconditionally; NORMAL entries are ranked against userPrompt —
        // the request this plan is about to be generated for. Appended after the tool catalog
        // rather than before it, so a tight budget never truncates the JSON-shape/rules header
        // above to make room for it. Failure here is additive-only, matching AgentExecutor's own
        // try/catch around the same call — planning must still work if the store can't be read.
        String knowledgeBlock = "";
        try {
            if (knowledgeStore == null) {
                knowledgeStore = new KnowledgeStore(context);
            }
            KnowledgeBlockBuilder.Result knowledgeResult = KnowledgeRetriever.buildContextBlock(
                    knowledgeStore, userPrompt, null, PLANNING_KNOWLEDGE_BLOCK_MAX_TOKENS);
            if (!knowledgeResult.block.isEmpty()) {
                knowledgeBlock = "\n" + knowledgeResult.block + "\n";
            }
        } catch (Exception e) {
            // Fail open — see method javadoc note above.
        }

        return toolCatalog + knowledgeBlock;
    }

    private String renderTools(List<AgentTool> tools, boolean includeSchema) {
        StringBuilder sb = new StringBuilder();
        for (AgentTool tool : tools) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            if (includeSchema) {
                sb.append("  schema: ").append(tool.getParametersSchema().toString()).append("\n");
            }
        }
        return sb.toString();
    }

    // ── Step execution ──────────────────────────────────────────────────────

    /**
     * Executes a single step by calling the tool directly through ToolRegistry.
     * Applies the same schema + runtime validation AgentExecutor.executeTool()
     * applies, so a plan step cannot bypass safety checks that a normal
     * AgentExecutor-driven tool call would go through. Risk-based approval/snapshot
     * gating now runs here too (ToolValidator + ApprovalManager — same classes
     * AgentExecutor uses), closing the gap this class's javadoc used to flag as
     * open. If no ApprovalCallback was supplied to executeUserRequest(), a
     * CRITICAL/MEDIUM step is DENIED rather than silently executed — see
     * ApprovalManager.requestApproval()'s existing callback==null branch, reused
     * as-is here, not reimplemented.
     */
    private ToolResult executeStep(PlanStep step, ToolContext toolContext) {
        AgentTool tool = toolRegistry.getTool(step.getToolName());
        if (tool == null) {
            return ToolResult.failure(null, "Unknown tool: '" + step.getToolName() + "'");
        }

        JsonObject args = step.getArguments();

        if (args.has("sc_id") && !toolContext.isProjectAllowed(args.get("sc_id").getAsString())) {
            return ToolResult.failure(null, "Access denied: project " + args.get("sc_id").getAsString());
        }
        if (tool.requiresProject() && toolContext.getAllowedProjectIds().isEmpty()) {
            return ToolResult.failure(null, "Tool '" + tool.getName() + "' requires an active project.");
        }

        ToolCallValidator.ValidationResult schemaValidation =
                ToolCallValidator.validate(args, tool.getParametersSchema());
        if (!schemaValidation.valid) {
            return ToolResult.failure(null, "Invalid arguments for '" + tool.getName() + "': "
                    + schemaValidation.errorMessage);
        }

        RuntimeToolValidator.RuntimeValidationResult runtimeCheck =
                RuntimeToolValidator.validate(tool.getName(), args, toolContext);
        if (!runtimeCheck.isValid()) {
            return ToolResult.failure(null, runtimeCheck.getErrorMessage());
        }

        // ── Risk resolution + approval gate (closes the gap this class's javadoc
        // used to flag as open — see "Approval gating" note above the class). Same
        // ToolValidator/ApprovalManager pair AgentExecutor already uses, so a
        // CRITICAL/MEDIUM step behaves identically whether reached via the manual
        // BottomSheet path or this plan-execution path. ──
        ToolValidationResult riskResult = toolValidator.validate(tool, tool.getName(), args, toolContext);
        if (!riskResult.valid) {
            return ToolResult.failure(null, riskResult.reason);
        }

        if (riskResult.requiresSnapshot) {
            String scId = args.has("sc_id") ? args.get("sc_id").getAsString() : null;
            if (scId != null && !scId.isEmpty()) {
                snapshotManager(toolContext).createSnapshot(scId, "before " + tool.getName(), tool.getName());
            }
        }

        if (riskResult.requiresApproval) {
            ApprovalManager.Decision decision = approvalManager.requestApproval(
                    tool, args, riskResult, null, null, null);
            if (decision == ApprovalManager.Decision.DENIED) {
                return ToolResult.failure(null, "Tool '" + tool.getName() + "' requires user approval "
                        + "(risk=" + riskResult.riskLevel + ") but was denied"
                        + (approvalCallback == null ? " — no approval UI is attached to this orchestrator "
                          + "run (see executeUserRequest's approvalCallback parameter)." : "."));
            }
            if (decision == ApprovalManager.Decision.CANCELLED) {
                isCancelled.set(true);
                return ToolResult.failure(null, "Operation cancelled by user.");
            }
            if (decision == ApprovalManager.Decision.TIMEOUT) {
                return ToolResult.failure(null, "Approval timed out for '" + tool.getName() + "'.");
            }
        }

        toolContext.reportProgress("Running " + tool.getName() + "...", -1, true);
        try {
            return tool.execute(args, toolContext);
        } catch (Exception e) {
            return ToolResult.failure(null, "Tool '" + tool.getName() + "' threw an exception: " + e.getMessage());
        }
    }
}
