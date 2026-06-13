package pro.sketchware.ai.engine.pipeline;

import android.content.Context;
import android.util.Log;
import pro.sketchware.ai.utils.AiLog;

import java.util.List;
import java.util.UUID;

/**
 * High-level API for Pipeline lifecycle management.
 *
 * Usage pattern:
 * <pre>
 *   PipelineStateManager mgr = new PipelineStateManager(context);
 *   Pipeline p = mgr.createPipeline("Material3 Audit", scId, workspaceId);
 *   mgr.addStep(p, "validate_rtl_layout", "Check RTL violations");
 *   mgr.addStep(p, "audit_material3",     "Scan Material 3 issues");
 *   mgr.start(p);
 *
 *   // Before each step:
 *   mgr.beginStep(p, step);
 *   // ... execute tool ...
 *   mgr.stepSucceeded(p, step, output);
 *   // or:
 *   mgr.stepFailed(p, step, error);
 *
 *   mgr.complete(p);
 * </pre>
 */
public final class PipelineStateManager {

    private static final String TAG = "PipelineStateMgr";

    private final PipelineDatabase db;

    public PipelineStateManager(Context context) {
        this.db = PipelineDatabase.getInstance(context);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public Pipeline createPipeline(String name, String scId, String workspaceId) {
        String id = "pipe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Pipeline p = new Pipeline(id, name, scId, workspaceId);
        p.status = PipelineStatus.CREATED;
        db.insertPipeline(p);
        Log.i(TAG, "Pipeline created: " + id + " [" + name + "]");
        return p;
    }

    public PipelineStep addStep(Pipeline pipeline, String toolName, String description) {
        PipelineStep step = pipeline.addStep(toolName, description);
        db.insertStep(step);
        return step;
    }

    public void start(Pipeline pipeline) {
        pipeline.status = PipelineStatus.RUNNING;
        pipeline.startedAt = System.currentTimeMillis();
        db.updatePipelineStatus(pipeline.pipelineId, PipelineStatus.RUNNING,
                pipeline.currentStepIndex, null);
        Log.i(TAG, "Pipeline started: " + pipeline.pipelineId);
    }

    public void beginStep(Pipeline pipeline, PipelineStep step) {
        step.status    = StepStatus.RUNNING;
        step.startedAt = System.currentTimeMillis();
        pipeline.currentStepIndex = step.stepIndex;
        db.updateStep(step);
        db.updatePipelineStatus(pipeline.pipelineId, PipelineStatus.RUNNING,
                step.stepIndex, null);
        AiLog.d(TAG, "Step " + step.stepIndex + " [" + step.toolName + "] started");
    }

    public void stepSucceeded(Pipeline pipeline, PipelineStep step, String output) {
        step.status     = StepStatus.SUCCESS;
        step.output     = output;
        step.finishedAt = System.currentTimeMillis();
        db.updateStep(step);
        AiLog.d(TAG, "Step " + step.stepIndex + " succeeded in " + step.getDurationMs() + "ms");
    }

    public void stepFailed(Pipeline pipeline, PipelineStep step, String error) {
        step.status     = StepStatus.FAILED;
        step.error      = error;
        step.finishedAt = System.currentTimeMillis();
        db.updateStep(step);
        Log.w(TAG, "Step " + step.stepIndex + " [" + step.toolName + "] failed: " + error);
    }

    public void skipStep(Pipeline pipeline, PipelineStep step, String reason) {
        step.status     = StepStatus.SKIPPED;
        step.output     = reason;
        step.finishedAt = System.currentTimeMillis();
        db.updateStep(step);
        AiLog.d(TAG, "Step " + step.stepIndex + " skipped: " + reason);
    }

    public void recordSnapshot(PipelineStep step, String snapshotId) {
        step.snapshotId = snapshotId;
        db.updateStep(step);
    }

    public void waitForApproval(Pipeline pipeline) {
        pipeline.status = PipelineStatus.WAITING_APPROVAL;
        db.updatePipelineStatus(pipeline.pipelineId, PipelineStatus.WAITING_APPROVAL,
                pipeline.currentStepIndex, null);
    }

    public void resumeFromApproval(Pipeline pipeline) {
        pipeline.status = PipelineStatus.RUNNING;
        db.updatePipelineStatus(pipeline.pipelineId, PipelineStatus.RUNNING,
                pipeline.currentStepIndex, null);
    }

    public void complete(Pipeline pipeline) {
        pipeline.status     = PipelineStatus.COMPLETED;
        pipeline.finishedAt = System.currentTimeMillis();
        db.updatePipelineStatus(pipeline.pipelineId, PipelineStatus.COMPLETED,
                pipeline.currentStepIndex, null);
        Log.i(TAG, "Pipeline completed: " + pipeline.pipelineId
                + " (" + pipeline.progressPercent() + "% steps done)");
    }

    public void fail(Pipeline pipeline, String error) {
        pipeline.status       = PipelineStatus.FAILED;
        pipeline.errorMessage = error;
        pipeline.finishedAt   = System.currentTimeMillis();
        db.updatePipelineStatus(pipeline.pipelineId, PipelineStatus.FAILED,
                pipeline.currentStepIndex, error);
        Log.e(TAG, "Pipeline FAILED: " + pipeline.pipelineId + " — " + error);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Pipeline getPipeline(String pipelineId) {
        Pipeline p = db.getPipeline(pipelineId);
        if (p != null) {
            p.steps.addAll(db.getSteps(pipelineId));
        }
        return p;
    }

    public List<Pipeline> getActivePipelines(String workspaceId) {
        List<Pipeline> list = db.getActivePipelines(workspaceId);
        for (Pipeline p : list) {
            p.steps.addAll(db.getSteps(p.pipelineId));
        }
        return list;
    }
}
