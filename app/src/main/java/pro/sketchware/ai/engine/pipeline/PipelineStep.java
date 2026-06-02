package pro.sketchware.ai.engine.pipeline;

/**
 * Represents a single step in a Pipeline.
 */
public final class PipelineStep {

    public final int    stepIndex;
    public final String pipelineId;
    public final String toolName;
    public final String description;
    public       StepStatus status;
    public       String     output;
    public       String     error;
    public       long       startedAt;
    public       long       finishedAt;
    public       String     snapshotId;  // snapshot taken before this step (if any)

    public PipelineStep(String pipelineId, int stepIndex, String toolName, String description) {
        this.pipelineId  = pipelineId;
        this.stepIndex   = stepIndex;
        this.toolName    = toolName;
        this.description = description;
        this.status      = StepStatus.PENDING;
    }

    /** Duration in milliseconds, 0 if not finished. */
    public long getDurationMs() {
        if (startedAt == 0 || finishedAt == 0) return 0;
        return finishedAt - startedAt;
    }
}
