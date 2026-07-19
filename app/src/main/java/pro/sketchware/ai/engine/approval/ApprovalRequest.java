package pro.sketchware.ai.engine.approval;

import pro.sketchware.ai.engine.diff.FileDiff;
import pro.sketchware.ai.engine.risk.RiskLevel;

/**
 * Encapsulates all information needed to show an approval dialog to the user.
 */
public final class ApprovalRequest {

    public final String    toolName;
    public final String    toolDescription;
    public final RiskLevel riskLevel;
    public final String    scId;
    public final FileDiff  diff;          // may be null for CRITICAL ops without file content
    public final String    summary;       // one-line summary of what will change

    public ApprovalRequest(String toolName, String toolDescription, RiskLevel riskLevel,
                           String scId, FileDiff diff, String summary) {
        this.toolName        = toolName;
        this.toolDescription = toolDescription;
        this.riskLevel       = riskLevel;
        this.scId            = scId;
        this.diff            = diff;
        this.summary         = summary;
    }
}
