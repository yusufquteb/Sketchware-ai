package pro.sketchware.ai.engine.risk;

/**
 * Risk classification for every tool execution.
 *
 * LOW      → read-only or trivially reversible; execute immediately.
 * MEDIUM   → modifies files or state; show diff and require approval in Balanced/Conservative mode.
 * CRITICAL → destructive, irreversible, or executes arbitrary code; always require explicit approval.
 */
public enum RiskLevel {

    /** Read-only operations: no approval needed in any mode. */
    LOW,

    /** File modifications: requires Snapshot + Diff before approval in Balanced mode. */
    MEDIUM,

    /** Destructive / execution: always requires explicit user approval. */
    CRITICAL;

    public boolean requiresSnapshot() {
        return this == MEDIUM || this == CRITICAL;
    }

    public boolean requiresApproval(ApprovalMode mode) {
        switch (mode) {
            case CONSERVATIVE: return this != LOW;
            case BALANCED:     return this == CRITICAL || this == MEDIUM;
            case AUTONOMOUS:   return this == CRITICAL;
            default:           return true;
        }
    }
}
