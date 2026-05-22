package pro.sketchware.ai.core;

/**
 * Immutable state machine for the AI chat lifecycle.
 *
 * <p>Transition rules:
 * <pre>
 * IDLE ──────────────────────────────────► PREPARING
 * PREPARING ─────────────────────────────► STREAMING | ERROR | CANCELLED
 * STREAMING ─────────────────────────────► EXECUTING_TOOL | COMPLETED | ERROR | CANCELLED
 * EXECUTING_TOOL ─────────────────────────► STREAMING | WAITING_CONFIRMATION | RETRYING | ERROR | CANCELLED
 * WAITING_CONFIRMATION ───────────────────► STREAMING | CANCELLED
 * RETRYING ───────────────────────────────► STREAMING | ERROR | CANCELLED
 * COMPLETED ──────────────────────────────► IDLE
 * ERROR ──────────────────────────────────► IDLE
 * CANCELLED ──────────────────────────────► IDLE
 * </pre>
 *
 * <p>All UI must react from state only — never from scattered boolean flags.
 */
public enum ChatState {

    /** No AI activity. Input is enabled. */
    IDLE,

    /** User message submitted; building context, selecting provider/model. */
    PREPARING,

    /** Receiving streaming tokens from the AI provider. */
    STREAMING,

    /** AI requested a tool call; tool is executing. */
    EXECUTING_TOOL,

    /** Pulse checkpoint reached; waiting for user Continue/Cancel. */
    WAITING_CONFIRMATION,

    /** A provider failed; retrying with next model in fallback chain. */
    RETRYING,

    /** AI turn completed successfully. Transitions to IDLE after UI update. */
    COMPLETED,

    /** An unrecoverable error occurred. Transitions to IDLE after error display. */
    ERROR,

    /** User or system requested cancellation. Transitions to IDLE after cleanup. */
    CANCELLED;

    // ── Transition guards ─────────────────────────────────────────────────────

    /** Returns true if AI is actively running (input should be disabled). */
    public boolean isActive() {
        return this == PREPARING
                || this == STREAMING
                || this == EXECUTING_TOOL
                || this == WAITING_CONFIRMATION
                || this == RETRYING;
    }

    /** Returns true if the state accepts user-initiated cancellation. */
    public boolean isCancellable() {
        return isActive();
    }

    /** Returns true if the state is terminal and should transition back to IDLE. */
    public boolean isTerminal() {
        return this == COMPLETED || this == ERROR || this == CANCELLED;
    }

    /** Returns true if input field should be enabled. */
    public boolean inputEnabled() {
        return this == IDLE || this == COMPLETED || this == ERROR || this == CANCELLED;
    }

    /**
     * Returns the status label for the typing indicator bar.
     * Returns null when the indicator should be hidden.
     */
    public String getStatusLabel(String providerName) {
        switch (this) {
            case PREPARING:         return "Preparing…";
            case STREAMING:         return providerName != null ? "Thinking with " + providerName + "…" : "Thinking…";
            case EXECUTING_TOOL:    return "Running tool…";
            case WAITING_CONFIRMATION: return "Waiting for confirmation…";
            case RETRYING:          return "Switching provider…";
            default:                return null;
        }
    }
}
