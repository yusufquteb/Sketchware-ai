package pro.sketchware.ai.engine.approval;

/**
 * Callback for the Approval Layer.
 *
 * The UI layer implements this to show an Approval Dialog.
 * onApproved  → agent continues execution.
 * onDenied    → agent skips this tool call and informs the LLM.
 * onCancelled → agent stops entirely.
 */
public interface ApprovalCallback {
    void onApprovalRequired(ApprovalRequest request, Runnable onApproved,
                            Runnable onDenied, Runnable onCancelled);
}
