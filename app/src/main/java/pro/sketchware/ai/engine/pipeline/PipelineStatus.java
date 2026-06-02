package pro.sketchware.ai.engine.pipeline;

public enum PipelineStatus {
    CREATED,
    READY,
    RUNNING,
    WAITING_APPROVAL,
    PAUSED,
    FAILED,
    ROLLING_BACK,
    COMPLETED
}
