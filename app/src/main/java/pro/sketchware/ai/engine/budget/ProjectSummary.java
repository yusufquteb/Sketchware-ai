package pro.sketchware.ai.engine.budget;

/**
 * Cached summary of a Sketchware project — avoids re-scanning large projects on every turn.
 */
public final class ProjectSummary {

    public final String scId;
    public final String projectName;
    public final int    activityCount;
    public final int    layoutCount;
    public final int    estimatedTokens;
    public final long   generatedAt;

    public ProjectSummary(String scId, String projectName,
                          int activityCount, int layoutCount,
                          int estimatedTokens, long generatedAt) {
        this.scId            = scId;
        this.projectName     = projectName;
        this.activityCount   = activityCount;
        this.layoutCount     = layoutCount;
        this.estimatedTokens = estimatedTokens;
        this.generatedAt     = generatedAt;
    }

    /** Returns true when the summary is older than 30 minutes and should be refreshed. */
    public boolean isStale() {
        return (System.currentTimeMillis() - generatedAt) > 30 * 60 * 1000L;
    }

    /** Single-line context string injected into the system prompt. */
    public String toContextLine() {
        return "Project " + scId + " (" + projectName + "): "
                + activityCount + " activities, "
                + layoutCount + " layouts";
    }
}
