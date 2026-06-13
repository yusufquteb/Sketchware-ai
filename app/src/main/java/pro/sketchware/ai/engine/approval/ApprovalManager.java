package pro.sketchware.ai.engine.approval;

import android.util.Log;
import pro.sketchware.ai.utils.AiLog;

import com.google.gson.JsonObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import pro.sketchware.ai.engine.diff.DiffEngine;
import pro.sketchware.ai.engine.diff.FileDiff;
import pro.sketchware.ai.engine.risk.ApprovalMode;
import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.engine.validation.ToolValidationResult;
import pro.sketchware.ai.tools.AgentTool;

/**
 * Manages the approval flow for MEDIUM and CRITICAL tool calls.
 *
 * Called from AgentExecutor after Tool Validation passes but before execution.
 *
 * Approval flow:
 *   1. Compute diff (for MEDIUM ops with file content)
 *   2. Build ApprovalRequest
 *   3. Post to UI via ApprovalCallback
 *   4. Block agent thread on CountDownLatch (max 60s, then auto-proceed based on mode)
 *   5. Return APPROVED / DENIED / TIMEOUT result
 *
 * Autonomous mode:
 *   - MEDIUM → auto-approved if within trusted session (15 min window)
 *   - CRITICAL → always requires approval, no auto-approve
 */
public final class ApprovalManager {

    private static final String TAG              = "ApprovalManager";
    private static final long   TIMEOUT_SECONDS  = 60L;
    private static final long   TRUSTED_WINDOW_MS= 15 * 60 * 1000L; // 15 minutes

    public enum Decision { APPROVED, DENIED, CANCELLED, TIMEOUT }

    private final ApprovalMode     approvalMode;
    private final ApprovalCallback callback;

    /** Timestamp of last explicit user approval (for Autonomous trusted-session logic). */
    private volatile long lastApprovalTimestamp = 0;

    public ApprovalManager(ApprovalMode mode, ApprovalCallback callback) {
        this.approvalMode = mode != null ? mode : ApprovalMode.BALANCED;
        this.callback     = callback;
    }

    /**
     * Requests user approval for a tool call if required.
     *
     * @param tool        the tool about to execute
     * @param arguments   the JSON arguments (for diff context)
     * @param validation  the validation result (contains risk level)
     * @param oldContent  optional: current file content (for diff display)
     * @param newContent  optional: proposed file content (for diff display)
     * @param filename    optional: file name for diff header
     * @return APPROVED if execution should proceed; DENIED/CANCELLED if not
     */
    public Decision requestApproval(AgentTool tool, JsonObject arguments,
                                    ToolValidationResult validation,
                                    String oldContent, String newContent, String filename) {
        if (!validation.requiresApproval) {
            return Decision.APPROVED;
        }

        // Autonomous mode: MEDIUM ops auto-approved within trusted session
        if (approvalMode == ApprovalMode.AUTONOMOUS
                && validation.riskLevel == RiskLevel.MEDIUM
                && isTrustedSession()) {
            AiLog.d(TAG, "Auto-approved (autonomous trusted session): " + tool.getName());
            return Decision.APPROVED;
        }

        // No callback registered (e.g. background/headless mode) → block
        if (callback == null) {
            Log.w(TAG, "No ApprovalCallback set — denying " + tool.getName());
            return Decision.DENIED;
        }

        // Build diff if file content provided
        FileDiff diff = null;
        if (oldContent != null && newContent != null && filename != null) {
            diff = DiffEngine.diff(filename, oldContent, newContent);
        }

        String scId = null;
        if (arguments != null && arguments.has("sc_id")) {
            try { scId = arguments.get("sc_id").getAsString(); } catch (Exception ignored) {}
        }

        String summary = buildSummary(tool, validation.riskLevel, diff);

        ApprovalRequest request = new ApprovalRequest(
                tool.getName(), tool.getDescription(),
                validation.riskLevel, scId, diff, summary);

        // Block agent thread while UI shows the dialog
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean  approved   = new AtomicBoolean(false);
        AtomicBoolean  cancelled  = new AtomicBoolean(false);

        callback.onApprovalRequired(request,
                /* onApproved  */ () -> { approved.set(true);  latch.countDown(); },
                /* onDenied    */ () -> {                       latch.countDown(); },
                /* onCancelled */ () -> { cancelled.set(true); latch.countDown(); });

        try {
            boolean responded = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!responded) {
                Log.w(TAG, "Approval timed out for " + tool.getName()
                        + " — defaulting to DENIED");
                return Decision.TIMEOUT;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Decision.CANCELLED;
        }

        if (cancelled.get())  return Decision.CANCELLED;
        if (!approved.get())  return Decision.DENIED;

        // Record approval for trusted session
        lastApprovalTimestamp = System.currentTimeMillis();
        Log.i(TAG, "Approved: " + tool.getName());
        return Decision.APPROVED;
    }

    private boolean isTrustedSession() {
        return lastApprovalTimestamp > 0
                && (System.currentTimeMillis() - lastApprovalTimestamp) < TRUSTED_WINDOW_MS;
    }

    private String buildSummary(AgentTool tool, RiskLevel risk, FileDiff diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(risk.name()).append("] ");
        sb.append(tool.getName());
        if (diff != null && diff.hasChanges()) {
            sb.append(" — ").append(diff.getSummary());
        }
        return sb.toString();
    }
}
