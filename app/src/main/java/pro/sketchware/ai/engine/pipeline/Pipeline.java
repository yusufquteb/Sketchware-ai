package pro.sketchware.ai.engine.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * A Pipeline groups a sequence of tool steps into a trackable execution unit.
 *
 * Pipelines are persisted in SQLite so they survive app crashes and can be resumed.
 */
public final class Pipeline {

    public final  String         pipelineId;
    public final  String         name;
    public final  String         scId;
    public final  String         workspaceId;
    public        PipelineStatus status;
    public        long           createdAt;
    public        long           startedAt;
    public        long           finishedAt;
    public        int            currentStepIndex;
    public        String         errorMessage;

    /** Ordered list of steps — loaded lazily from DB. */
    public final List<PipelineStep> steps = new ArrayList<>();

    public Pipeline(String pipelineId, String name, String scId, String workspaceId) {
        this.pipelineId   = pipelineId;
        this.name         = name;
        this.scId         = scId;
        this.workspaceId  = workspaceId;
        this.status       = PipelineStatus.CREATED;
        this.createdAt    = System.currentTimeMillis();
    }

    public PipelineStep addStep(String toolName, String description) {
        PipelineStep step = new PipelineStep(pipelineId, steps.size(), toolName, description);
        steps.add(step);
        return step;
    }

    public PipelineStep currentStep() {
        if (steps.isEmpty() || currentStepIndex >= steps.size()) return null;
        return steps.get(currentStepIndex);
    }

    public boolean isTerminal() {
        return status == PipelineStatus.COMPLETED
                || status == PipelineStatus.FAILED;
    }

    /** Progress 0-100. */
    public int progressPercent() {
        if (steps.isEmpty()) return 0;
        long done = 0;
        for (PipelineStep s : steps) {
            if (s.status == StepStatus.SUCCESS || s.status == StepStatus.SKIPPED) done++;
        }
        return (int) (done * 100L / steps.size());
    }
}
