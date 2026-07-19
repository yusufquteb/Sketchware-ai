package pro.sketchware.ai.orchestrator;

import android.content.Context;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.engine.snapshot.ProjectSnapshotManager;
import pro.sketchware.ai.engine.snapshot.SnapshotMetadata;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.ai.tools.ToolRegistry;
import pro.sketchware.ai.tools.build.BuildTools;
import pro.sketchware.ai.tools.build.CompileTools;
import pro.sketchware.ai.tools.build.BuildRepairTool;

/**
 * Phase 4 — closes the loop AgentOrchestrator (Phase 2) deliberately left open:
 * AgentOrchestrator.executeStep() stops at the first failure with no retry
 * (see its "onStepFailed" callback and the comment directly above the call to
 * it — verified in this session, not assumed). This class is invoked BY
 * AgentOrchestrator specifically when the failed step's tool name is
 * "build_project" (i.e. only build failures get self-correction — a failure
 * in, say, add_library, still stops the plan immediately as before; that
 * behavior is intentionally unchanged. Broadening self-correction to
 * non-build steps is out of scope for this phase).
 *
 * Loop shape: build → (if fail) get filtered errors → ask LLM for a patch →
 * apply patch → rebuild → repeat, up to {@link #DEFAULT_MAX_CORRECTION_ATTEMPTS}
 * times, then stop and report final failure with the last compile log.
 *
 * ── Snapshot / rollback between attempts ────────────────────────────────────
 * Before the first patch is applied, this loop takes ONE baseline snapshot via
 * the existing {@link ProjectSnapshotManager} (same manager used by
 * CreateSnapshotTool/RestoreSnapshotTool — no new backup mechanism introduced).
 * Verified before wiring this: ProjectSnapshotManager.createSnapshot() copies
 * ToolContext.getProjectDataDir(scId), i.e. ".sketchware/data/{scId}/" — and
 * FilePathUtil.getPathJava/getPathResource/getPathAssets (which is where
 * patch_file/write_file/append_code/insert_code_at_line actually write, per
 * FileTools.resolveEditablePath) all resolve to
 * ".sketchware/data/{scId}/files/..." — i.e. INSIDE that same directory. So the
 * one baseline snapshot does cover everything a patch step here can touch.
 *
 * If attempt N's patch is applied but the rebuild still fails, the project is
 * rolled back to the baseline snapshot BEFORE attempt N+1 generates its next
 * patch. This is deliberate: without rollback, attempt N+1 would be patching
 * on top of attempt N's possibly-wrong (and possibly partially-applied) patch,
 * so a bad fix could compound instead of attempt N+1 getting a clean, correctly
 * diagnosed starting point. The baseline snapshot itself is left in place (not
 * deleted) after the loop ends either way, so a human can still restore it
 * manually via restore_snapshot if the final result is unwanted.
 * A snapshot failure (e.g. storage full) does not abort the loop — it degrades
 * to the old (Phase-4-without-rollback) behavior with a note to the listener,
 * rather than blocking self-correction entirely over a backup that isn't
 * strictly required for the loop to function, only for its safety property.
 *
 * ── Why DEFAULT_MAX_CORRECTION_ATTEMPTS = 3 ─────────────────────────────────
 * Chosen explicitly (per PROMPT_PHASE_4.md rule 3 — not left implicit):
 *   - 1 attempt is too fragile: a single transient/multi-error build routinely
 *     needs more than one fix pass even when each individual fix is correct
 *     (e.g. fixing error A can reveal error B that was masked by A failing
 *     the compile first — BuildRepairTool's own staged pipeline assumes this).
 *   - Unbounded retry risks the model oscillating between two incorrect
 *     "fixes" that flip-flop a single error forever, burning API cost/time
 *     with no forward progress and no visibility to the user until something
 *     external (quota, timeout) stops it.
 *   - 3 gives room for "fix A, reveal B, fix B" (2 attempts) plus one spare
 *     attempt for a fix that didn't fully take, while keeping worst-case
 *     latency/cost bounded to 3 full build cycles — each of which is already
 *     expensive (full ECJ + D8 + AAPT2, no incremental compilation per the
 *     removal described in the task's "top of mind" — every attempt here is
 *     a full rebuild). Configurable via the constructor for callers who want
 *     a different tradeoff; this is a default, not a hardcoded ceiling.
 */
public final class SelfCorrectionLoop {

    public static final int DEFAULT_MAX_CORRECTION_ATTEMPTS = 3;

    public interface Listener {
        /** Called before each rebuild attempt (attempt 1 = the original failed build already happened). */
        void onCorrectionAttemptStarted(int attemptNumber, int maxAttempts);

        /** Called after the LLM proposes a patch, before it's applied. */
        void onPatchProposed(int attemptNumber, String patchSummary);

        /** Called when a rebuild after a correction attempt succeeds. */
        void onCorrected(int attemptsUsed);

        /** Called when the max attempt count is reached without a successful build. */
        void onExhausted(int attemptsUsed, String lastCompileLog);

        /** Called if the LLM patch-generation call itself fails (network/API error, not a build error). */
        void onCorrectionInfraFailure(int attemptNumber, String error);

        /**
         * A baseline snapshot was taken before the first patch attempt, or the
         * project was rolled back to that baseline before retrying after a
         * failed attempt. See SelfCorrectionLoop's "Snapshot / rollback" note
         * for why every attempt starts from the same known state rather than
         * stacking on top of a previous attempt's possibly-broken patch.
         */
        default void onSnapshotEvent(String message) {}
    }

    private final Context context;
    private final ToolRegistry toolRegistry;
    private final AiPreferences preferences;
    private final int maxAttempts;

    public SelfCorrectionLoop(Context context, ToolRegistry toolRegistry) {
        this(context, toolRegistry, DEFAULT_MAX_CORRECTION_ATTEMPTS);
    }

    public SelfCorrectionLoop(Context context, ToolRegistry toolRegistry, int maxAttempts) {
        this.context = context.getApplicationContext();
        this.toolRegistry = toolRegistry;
        this.preferences = AiPreferences.getInstance(this.context);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /**
     * Runs the correction loop after an initial build_project failure.
     * Blocking call — intended to be invoked from AgentOrchestrator's background
     * executor thread, same as the rest of its step execution.
     *
     * @param scId          the project that failed to build
     * @param toolContext   same ToolContext the failing step ran under
     * @param provider      AI provider to use for patch-generation calls
     * @param modelId       model id to use for patch-generation calls
     * @param listener      progress callbacks
     * @return the final ToolResult of the last build_project call made (success or failure)
     */
    public ToolResult run(String scId, ToolContext toolContext, AiProvider provider, String modelId, Listener listener) {
        String lastCompileLog = readCompileLog(scId, toolContext);

        ProjectSnapshotManager snapshotManager = new ProjectSnapshotManager(toolContext);
        String baselineSnapshotId = createBaselineSnapshot(scId, snapshotManager, listener);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (toolContext.isCancelled()) {
                return ToolResult.failure(null, "Self-correction cancelled.");
            }

            // Roll back to the known-good baseline before every retry (not before
            // attempt 1 -- there's nothing to roll back yet). See class javadoc
            // "Snapshot / rollback between attempts" for why this happens BEFORE
            // generating the next patch rather than after the previous one fails.
            if (attempt > 1 && baselineSnapshotId != null) {
                boolean restored = snapshotManager.restoreSnapshot(scId, baselineSnapshotId);
                listener.onSnapshotEvent(restored
                        ? "Rolled back to baseline snapshot '" + baselineSnapshotId + "' before attempt " + attempt + "."
                        : "WARNING: rollback to baseline snapshot '" + baselineSnapshotId
                          + "' failed before attempt " + attempt + " -- continuing on top of attempt "
                          + (attempt - 1) + "'s unresolved changes.");
            }

            listener.onCorrectionAttemptStarted(attempt, maxAttempts);

            // ── 1. Get filtered (ERROR-only) diagnostics + raw context for this attempt ──
            String filteredErrors = getFilteredErrors(scId, toolContext);
            if (filteredErrors == null || filteredErrors.trim().isEmpty()) {
                filteredErrors = lastCompileLog != null ? lastCompileLog : "(no compile log available)";
            }

            // Also pull the deterministic, non-LLM repair plan from Phase 3's
            // BuildRepairTool — it's already staged/prioritized and cites exact
            // file:line locations; feeding it to the LLM alongside the raw
            // filtered errors gives the model a head start instead of
            // re-deriving the same classification from scratch every attempt.
            String repairPlan = getRepairPlan(scId, toolContext);

            // ── 2. Ask the LLM for a concrete patch (as tool calls to apply) ──
            PatchPlan patchPlan;
            try {
                patchPlan = generatePatchPlan(scId, filteredErrors, repairPlan, provider, modelId);
            } catch (Exception e) {
                listener.onCorrectionInfraFailure(attempt, e.getMessage());
                return ToolResult.failure(null,
                        "Self-correction stopped: patch-generation request failed on attempt "
                        + attempt + ": " + e.getMessage());
            }

            if (patchPlan.steps.isEmpty()) {
                // Model returned no actionable patch — treat as exhausted rather than
                // looping on nothing, since another identical attempt won't help.
                listener.onExhausted(attempt, lastCompileLog);
                return ToolResult.failure(null,
                        "Self-correction stopped: the model proposed no patch on attempt "
                        + attempt + ". Last compile log:\n" + safeTail(lastCompileLog));
            }

            listener.onPatchProposed(attempt, patchPlan.summary);

            // ── 3. Apply the proposed patch via existing, already-validated tools ──
            for (PlanStep patchStep : patchPlan.steps) {
                if (toolContext.isCancelled()) {
                    return ToolResult.failure(null, "Self-correction cancelled while applying patch.");
                }
                ToolResult applyResult = applyPatchStep(patchStep, toolContext);
                if (!applyResult.isSuccess()) {
                    // A patch step failing to apply (e.g. search_string not found because
                    // the model's proposed patch doesn't match current file content) is
                    // treated as this attempt's failure — counted against maxAttempts,
                    // not retried again within the same attempt, to keep the retry count
                    // honest and bounded exactly as rule 3 requires.
                    lastCompileLog = "Patch step '" + patchStep.getToolName()
                            + "' failed to apply: " + applyResult.getError();
                    if (attempt == maxAttempts) {
                        listener.onExhausted(attempt, lastCompileLog);
                        return ToolResult.failure(null,
                                "Self-correction exhausted after " + attempt + " attempt(s). "
                                + "Last failure: " + lastCompileLog);
                    }
                    // fall through to next attempt
                    break;
                }
            }

            // ── 4. Rebuild ──
            ToolResult rebuildResult = rebuild(scId, toolContext);
            if (rebuildResult.isSuccess()) {
                listener.onCorrected(attempt);
                return rebuildResult;
            }

            lastCompileLog = readCompileLog(scId, toolContext);
            if (attempt == maxAttempts) {
                listener.onExhausted(attempt, lastCompileLog);
                return ToolResult.failure(null,
                        "Self-correction exhausted after " + maxAttempts + " attempt(s) — build still failing.\n"
                        + "Last compile log:\n" + safeTail(lastCompileLog));
            }
            // else: loop continues to next attempt
        }

        // Unreachable given the loop above always returns by the last iteration,
        // but kept as an explicit safety net rather than relying on that.
        return ToolResult.failure(null, "Self-correction ended without a definitive result after "
                + maxAttempts + " attempt(s).");
    }

    // ── Snapshot helper ─────────────────────────────────────────────────────

    /**
     * Takes one baseline snapshot before any patch is applied. Returns the
     * snapshot ID, or null if snapshotting failed -- a null return does NOT
     * abort the loop (see class javadoc); callers just skip rollback and the
     * loop behaves as it did before this safety net was added.
     */
    private String createBaselineSnapshot(String scId, ProjectSnapshotManager snapshotManager, Listener listener) {
        try {
            SnapshotMetadata meta = snapshotManager.createSnapshot(
                    scId, "self-correction baseline (before Phase 4 auto-patch attempts)", "build_project");
            if (meta == null) {
                listener.onSnapshotEvent(
                        "WARNING: could not create baseline snapshot for " + scId
                        + " -- self-correction will proceed WITHOUT rollback-between-attempts protection.");
                return null;
            }
            listener.onSnapshotEvent("Baseline snapshot created: '" + meta.snapshotId + "'.");
            return meta.snapshotId;
        } catch (Exception e) {
            listener.onSnapshotEvent(
                    "WARNING: snapshot creation threw (" + e.getMessage()
                    + ") -- self-correction will proceed WITHOUT rollback-between-attempts protection.");
            return null;
        }
    }

    // ── Step helpers ────────────────────────────────────────────────────────

    private ToolResult rebuild(String scId, ToolContext toolContext) {
        AgentTool buildTool = toolRegistry.getTool("build_project");
        if (buildTool == null) {
            return ToolResult.failure(null, "build_project tool not registered.");
        }
        JsonObject args = new JsonObject();
        args.addProperty("sc_id", scId);
        try {
            return buildTool.execute(args, toolContext);
        } catch (Exception e) {
            return ToolResult.failure(null, "Rebuild threw: " + e.getMessage());
        }
    }

    private String getFilteredErrors(String scId, ToolContext toolContext) {
        AgentTool getLogsTool = toolRegistry.getTool("get_compile_logs");
        if (getLogsTool == null) return null;
        JsonObject args = new JsonObject();
        args.addProperty("sc_id", scId);
        try {
            ToolResult result = getLogsTool.execute(args, toolContext);
            // GetCompileLogsTool.extractRootCause() already isolates the "ROOT CAUSE"
            // (error-line) section from noise before returning — see CompileTools.java.
            // This is a DIFFERENT filter implementation from CompileLogActivity's
            // filterErrorsOnly() (which uses CompileDiagnostic.Severity parsing); both
            // independently narrow to error-relevant content but are not the same code
            // path. Documented explicitly in CHANGES.md Phase 4 — see the "verification"
            // section for why they were not unified in this phase.
            return result.isSuccess() ? result.getOutput() : result.getError();
        } catch (Exception e) {
            return null;
        }
    }

    private String getRepairPlan(String scId, ToolContext toolContext) {
        AgentTool repairTool = toolRegistry.getTool("analyze_build_error");
        if (repairTool == null) return null;
        JsonObject args = new JsonObject();
        args.addProperty("sc_id", scId);
        try {
            ToolResult result = repairTool.execute(args, toolContext);
            return result.isSuccess() ? result.getOutput() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String readCompileLog(String scId, ToolContext toolContext) {
        File logFile = toolContext.getProjectCompileLogFile(scId);
        if (logFile == null || !logFile.exists()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(logFile.toPath()));
        } catch (Exception e) {
            return null;
        }
    }

    private String safeTail(String log) {
        if (log == null) return "(none)";
        int max = 3000;
        return log.length() <= max ? log : "…\n" + log.substring(log.length() - max);
    }

    private ToolResult applyPatchStep(PlanStep step, ToolContext toolContext) {
        AgentTool tool = toolRegistry.getTool(step.getToolName());
        if (tool == null) {
            return ToolResult.failure(null, "Unknown patch tool: '" + step.getToolName() + "'");
        }
        try {
            return tool.execute(step.getArguments(), toolContext);
        } catch (Exception e) {
            return ToolResult.failure(null, "Patch tool '" + tool.getName() + "' threw: " + e.getMessage());
        }
    }

    // ── LLM patch generation ────────────────────────────────────────────────

    private static final class PatchPlan {
        final List<PlanStep> steps;
        final String summary;
        PatchPlan(List<PlanStep> steps, String summary) {
            this.steps = steps;
            this.summary = summary;
        }
    }

    /**
     * One LLM call: feed filtered errors + the deterministic repair plan back in,
     * ask for concrete patch_file / write_file / append_code / insert_code_at_line
     * tool calls (the same surgical-mutation tools listed in ToolRegistry — no new
     * write path is introduced here). Reuses the exact same request pattern
     * AgentOrchestrator.generatePlan() already uses (see that method — this is
     * deliberately kept parallel rather than diverging into a second style of
     * LLM call).
     */
    private PatchPlan generatePatchPlan(String scId, String filteredErrors, String repairPlan,
                                         AiProvider provider, String modelId) throws Exception {
        String apiKey = preferences.getApiKey(provider);
        if (provider.requiresApiKey() && (apiKey == null || apiKey.isEmpty())) {
            throw new Exception("No API key set for " + provider.getDisplayName());
        }
        AiApiClient client = AiClientFactory.createClient(context, provider, apiKey);
        if (client == null) {
            throw new Exception("Failed to create API client for " + provider.getDisplayName());
        }

        String systemPrompt = buildPatchSystemPrompt(scId);
        String userPrompt = "COMPILE ERRORS (filtered to ERROR severity):\n" + filteredErrors
                + (repairPlan != null && !repairPlan.isEmpty()
                        ? "\n\nDETERMINISTIC REPAIR PLAN (from analyze_build_error, may help but verify against the actual errors above):\n" + repairPlan
                        : "");

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(null, userPrompt));

        StringBuilder responseBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        String[] errorHolder = new String[1];

        client.sendChatRequest(messages, modelId, systemPrompt, new StreamingResponseHandler() {
            @Override public void onChunk(String textDelta) {
                if (textDelta != null) responseBuilder.append(textDelta);
            }
            @Override public void onToolCall(pro.sketchware.ai.models.ToolCall toolCall) {
                // Patch-generation call sends no tool definitions (same design as
                // AgentOrchestrator's planning call) — a well-behaved model should
                // never emit one here; ignored defensively if it does.
            }
            @Override public void onComplete(String response) { latch.countDown(); }
            @Override public void onError(String error) { errorHolder[0] = error; latch.countDown(); }
        });

        long timeoutMs = preferences.getRequestTimeoutSecs() * 1000L;
        if (timeoutMs <= 0) timeoutMs = 120_000L;
        boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        client.shutdown();

        if (!completed) throw new Exception("Patch-generation request timed out.");
        if (errorHolder[0] != null) throw new Exception(errorHolder[0]);

        return parsePatchPlan(responseBuilder.toString(), scId);
    }

    private String buildPatchSystemPrompt(String scId) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a build-error patch generator for an Android project inside an on-device IDE.\n")
          .append("Project sc_id: ").append(scId).append("\n\n")
          .append("Given compile errors, output ONE JSON object and nothing else — no prose, no markdown fences.\n\n")
          .append("JSON shape:\n")
          .append("{\"summary\":\"<one-line human-readable summary of the fix>\",")
          .append("\"steps\":[{\"tool\":\"<tool_name>\",\"arguments\":{...}}]}\n\n")
          .append("Rules:\n")
          .append("- Use ONLY these tools: patch_file, write_file, append_code, insert_code_at_line, read_file, read_file_range.\n")
          .append("- Every mutating step MUST include \"sc_id\":\"").append(scId).append("\" in its arguments.\n")
          .append("- Prefer patch_file (search_string/replace_string) for small, surgical fixes — it is the\n")
          .append("  lowest-risk option since it fails safely (no-op) if the file no longer matches.\n")
          .append("- Only use write_file (full file replace) when the change is structural enough that a\n")
          .append("  surgical patch would be unreliable.\n")
          .append("- Fix the FIRST/root-cause error group first — do not attempt to fix every error in one\n")
          .append("  pass if they cascade from one root cause (mirrors analyze_build_error's staging).\n")
          .append("- If you cannot determine a safe fix from the information given, return {\"steps\":[]}\n")
          .append("  rather than guessing destructively.\n");
        return sb.toString();
    }

    private PatchPlan parsePatchPlan(String rawOutput, String scId) throws Exception {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            throw new Exception("Empty patch-generation response from model.");
        }
        String jsonText = extractJsonObject(rawOutput);
        if (jsonText == null) {
            throw new Exception("No JSON object found in patch-generation response.");
        }
        JsonObject root;
        try {
            root = com.google.gson.JsonParser.parseString(jsonText).getAsJsonObject();
        } catch (Exception e) {
            throw new Exception("Malformed patch JSON: " + e.getMessage());
        }

        String summary = root.has("summary") && !root.get("summary").isJsonNull()
                ? root.get("summary").getAsString() : "(no summary provided)";

        List<PlanStep> steps = new ArrayList<>();
        if (root.has("steps") && root.get("steps").isJsonArray()) {
            int i = 0;
            for (com.google.gson.JsonElement el : root.getAsJsonArray("steps")) {
                if (!el.isJsonObject()) continue;
                JsonObject stepObj = el.getAsJsonObject();
                String tool = stepObj.has("tool") && !stepObj.get("tool").isJsonNull()
                        ? stepObj.get("tool").getAsString() : null;
                if (tool == null || tool.isEmpty()) continue;
                JsonObject args = stepObj.has("arguments") && stepObj.get("arguments").isJsonObject()
                        ? stepObj.getAsJsonObject("arguments") : new JsonObject();
                // Defensive: force sc_id even if the model forgot it, since every
                // mutating tool in this registry requires it for the access check.
                if (!args.has("sc_id")) {
                    args.addProperty("sc_id", scId);
                }
                steps.add(new PlanStep(i++, tool, args, null));
            }
        }
        return new PatchPlan(steps, summary);
    }

    /** Same balanced-brace JSON extraction approach as ExecutionPlan.extractJsonObject — kept
     *  as a local copy rather than making that method public, since it's a small, self-contained
     *  utility and this class shouldn't widen ExecutionPlan's API surface for one caller. */
    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) { escape = false; }
                else if (c == '\\') { escape = true; }
                else if (c == '"') { inString = false; }
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return null;
    }
}
