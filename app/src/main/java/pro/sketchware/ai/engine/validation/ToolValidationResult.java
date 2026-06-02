package pro.sketchware.ai.engine.validation;

import pro.sketchware.ai.engine.risk.RiskLevel;

/**
 * Result of pre-execution tool validation.
 *
 * Holds:
 *  - valid: whether the tool call should proceed
 *  - reason: human-readable reason if invalid
 *  - riskLevel: resolved risk level for this call
 *  - requiresApproval: whether the Approval Layer must be invoked
 *  - requiresSnapshot: whether a project snapshot must be created first
 */
public final class ToolValidationResult {

    public final boolean valid;
    public final String  reason;
    public final RiskLevel riskLevel;
    public final boolean requiresApproval;
    public final boolean requiresSnapshot;

    private ToolValidationResult(boolean valid, String reason, RiskLevel riskLevel,
                                 boolean requiresApproval, boolean requiresSnapshot) {
        this.valid            = valid;
        this.reason           = reason;
        this.riskLevel        = riskLevel;
        this.requiresApproval = requiresApproval;
        this.requiresSnapshot = requiresSnapshot;
    }

    public static ToolValidationResult ok(RiskLevel level, boolean approval, boolean snapshot) {
        return new ToolValidationResult(true, null, level, approval, snapshot);
    }

    public static ToolValidationResult reject(String reason) {
        return new ToolValidationResult(false, reason, RiskLevel.LOW, false, false);
    }

    @Override
    public String toString() {
        return "ToolValidationResult{valid=" + valid
                + ", risk=" + riskLevel
                + ", approval=" + requiresApproval
                + ", snapshot=" + requiresSnapshot
                + (reason != null ? ", reason='" + reason + "'" : "")
                + "}";
    }
}
