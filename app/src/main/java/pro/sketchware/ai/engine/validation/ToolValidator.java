package pro.sketchware.ai.engine.validation;

import android.util.Log;
import pro.sketchware.ai.utils.AiLog;

import com.google.gson.JsonObject;

import java.io.File;

import pro.sketchware.ai.engine.risk.ApprovalMode;
import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

/**
 * Pre-execution validator for every tool call.
 *
 * Checks (in order):
 *  1. Tool reference is not null
 *  2. Required sc_id argument exists and project is accessible
 *  3. Risk level is resolved
 *  4. Snapshot required flag is set
 *  5. Approval required flag is set based on current ApprovalMode
 *
 * This is the single gatekeeper between LLM output and actual execution.
 * No tool runs without passing through here.
 */
public final class ToolValidator {

    private static final String TAG = "ToolValidator";

    private final ApprovalMode approvalMode;

    public ToolValidator(ApprovalMode approvalMode) {
        this.approvalMode = approvalMode != null ? approvalMode : ApprovalMode.BALANCED;
    }

    /**
     * Validates a tool call before execution.
     *
     * @param tool      the resolved AgentTool (may be null if name not found)
     * @param toolName  the name the LLM requested (for error messages)
     * @param arguments parsed JSON arguments from the LLM
     * @param context   the ToolContext for project access checks
     * @return validation result — check {@link ToolValidationResult#valid} before proceeding
     */
    public ToolValidationResult validate(AgentTool tool, String toolName,
                                         JsonObject arguments, ToolContext context) {
        // 1. Tool must exist
        if (tool == null) {
            Log.w(TAG, "Unknown tool requested: " + toolName);
            return ToolValidationResult.reject("Unknown tool: '" + toolName + "'. "
                    + "Check the tool catalog and use an existing tool name.");
        }

        // 2. If arguments contain sc_id, verify project access
        if (arguments != null && arguments.has("sc_id")) {
            String scId = null;
            try {
                scId = arguments.get("sc_id").getAsString();
            } catch (Exception ignored) {}
            if (scId == null || scId.isEmpty()) {
                return ToolValidationResult.reject("sc_id argument is empty. "
                        + "Provide the project ID of the target Sketchware project.");
            }
            if (!context.isProjectAllowed(scId)) {
                Log.w(TAG, "Project " + scId + " not in allowed list for tool: " + toolName);
                return ToolValidationResult.reject("Project '" + scId + "' is not accessible "
                        + "in the current workspace. Use list_projects to find allowed projects.");
            }
            // Verify the project data directory actually exists on disk
            File projectDir = context.getProjectDataDir(scId);
            if (!projectDir.exists()) {
                return ToolValidationResult.reject("Project directory not found for sc_id='" + scId
                        + "'. The project may have been deleted or is on an unmounted storage.");
            }
        }

        // 3. Resolve risk level from the tool itself
        RiskLevel risk;
        try {
            risk = tool.getRiskLevel();
            if (risk == null) risk = RiskLevel.LOW;
        } catch (Exception e) {
            Log.w(TAG, "getRiskLevel() threw for tool " + toolName + ": " + e.getMessage());
            risk = RiskLevel.LOW;
        }

        boolean needsSnapshot = risk.requiresSnapshot();
        boolean needsApproval = risk.requiresApproval(approvalMode);

        AiLog.d(TAG, "Tool '" + toolName + "' → risk=" + risk
                + " snapshot=" + needsSnapshot + " approval=" + needsApproval);

        return ToolValidationResult.ok(risk, needsApproval, needsSnapshot);
    }

    public ApprovalMode getApprovalMode() {
        return approvalMode;
    }
}
