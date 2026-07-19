package pro.sketchware.ai.engine.diff;

import java.io.File;

import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.ai.tools.blocks.BlockLogicReader;

/**
 * Connects the existing {@link DiffEngine} to the Blocks write path
 * (add_block / modify_block / delete_block), which the Phase-3 audit flagged
 * as not wired to diff display before this change (see CHANGES.md, Phase 3,
 * item 4).
 *
 * HOW THIS ACTUALLY HOOKS IN, AND WHY IT'S NOT A TRUE PRE-EXECUTION APPROVAL
 * GATE (read before assuming this matches patch_file's behaviour):
 *
 * The one real call site that turns a diff into a blocking approval prompt is
 * {@code AgentExecutor.executeTool()} → {@code approvalManager.requestApproval(
 * tool, args, riskResult, oldContent, newContent, filename)} — called BEFORE
 * {@code ToolExecutionGuard.executeWithTimeout(...)} runs the tool. That
 * requires oldContent/newContent to be known before the tool's own execute()
 * runs.
 *
 * {@code BlockLogicWriter.addBlock()/modifyBlock()/deleteBlock()} all call the
 * writer's private {@code write()} internally, which validates AND persists to
 * disk in the same step — there is no exposed "compute the new file text
 * without saving it" method to call ahead of time. Adding one would mean
 * changing {@code BlockLogicWriter}'s internals, which is a larger, riskier
 * change than this phase's scope for a diff-wiring task.
 *
 * So this class captures {@code oldText} (the logic file's content) BEFORE the
 * block tool executes, and exposes {@link #buildAfterDiff} to be called AFTER
 * the tool has already run and persisted its change, using the same
 * {@link DiffEngine} the rest of the app uses. AgentExecutor.executeTool()
 * (or, going forward, {@link pro.sketchware.ai.orchestrator.AgentOrchestrator})
 * attaches the resulting {@link FileDiff} summary to the tool's result message
 * so the user/LLM sees exactly what changed — this is a "show what happened"
 * diff, not a "confirm before it happens" gate, for add_block/modify_block/
 * delete_block specifically. Wiring an actual pre-execution gate for these
 * three tools is left as a follow-up (would require adding a
 * "computeWithoutPersisting" method to BlockLogicWriter first).
 */
public final class BlockDiffSupport {

    private BlockDiffSupport() {}

    /** Tool names this connector applies to. */
    public static boolean appliesTo(String toolName) {
        return "add_block".equals(toolName) || "modify_block".equals(toolName)
                || "delete_block".equals(toolName);
    }

    /**
     * Reads the current (pre-mutation) raw logic file text for diff purposes.
     * Call this BEFORE invoking the tool's execute(). Returns "" if the file
     * doesn't exist yet (e.g. first block ever added to a fresh project) or
     * null on read failure — callers should treat both as "no before-state to
     * diff against" rather than fail the tool call.
     */
    public static String captureBeforeText(ToolContext ctx, String scId) {
        try {
            File f = logicFile(ctx, scId);
            if (!f.exists()) return "";
            return BlockLogicReader.readDecryptedPublic(f);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads the post-mutation logic file text and produces a unified diff
     * against the text captured by {@link #captureBeforeText}. Call this AFTER
     * the tool's execute() has run and returned success.
     *
     * @return a diff summary string suitable for appending to the tool's
     *         result output, or null if no meaningful diff could be computed
     *         (e.g. beforeText was null due to a read failure).
     */
    public static String buildAfterDiff(ToolContext ctx, String scId, String toolName, String beforeText) {
        if (beforeText == null) return null;
        try {
            File f = logicFile(ctx, scId);
            String afterText = f.exists() ? BlockLogicReader.readDecryptedPublic(f) : "";
            FileDiff diff = DiffEngine.diff(scId + "/logic (" + toolName + ")", beforeText, afterText, 2);
            if (!diff.hasChanges()) return null;
            return diff.getSummary();
        } catch (Exception e) {
            return null;
        }
    }

    private static File logicFile(ToolContext ctx, String scId) {
        return new File(ctx.getProjectDataDir(scId), "logic");
    }
}
