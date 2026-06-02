package pro.sketchware.ai.engine.budget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.ai.engine.TokenEstimator;
import pro.sketchware.ai.models.ChatMessage;

/**
 * Tracks token usage across conversation turns and caches per-project summaries.
 *
 * The budget is advisory — it does not hard-block the agent, but lets callers
 * decide when to trim history or inject only the summary instead of full project data.
 */
public final class ContextBudgetManager {

    private static final int DEFAULT_BUDGET_TOKENS = 8_000;

    private final int budgetTokens;
    private final Map<String, ProjectSummary> cache = new HashMap<>();

    public ContextBudgetManager(int budgetTokens) {
        this.budgetTokens = budgetTokens > 0 ? budgetTokens : DEFAULT_BUDGET_TOKENS;
    }

    // ── Summary cache ─────────────────────────────────────────────────────────

    public void cacheProjectSummary(ProjectSummary summary) {
        if (summary != null) cache.put(summary.scId, summary);
    }

    /** Returns the cached summary if it is still fresh, null otherwise. */
    public ProjectSummary getCachedSummary(String scId) {
        ProjectSummary s = cache.get(scId);
        return (s != null && !s.isStale()) ? s : null;
    }

    public void invalidate(String scId) {
        cache.remove(scId);
    }

    /** Returns a one-line context string for the given project, or "" if not cached. */
    public String buildProjectContext(String scId) {
        ProjectSummary s = cache.get(scId);
        return s != null ? s.toContextLine() : "";
    }

    // ── Token budget ──────────────────────────────────────────────────────────

    public int estimateTokens(List<ChatMessage> messages) {
        return TokenEstimator.estimate(messages);
    }

    public int remainingBudget(List<ChatMessage> messages) {
        return budgetTokens - estimateTokens(messages);
    }

    public boolean isOverBudget(List<ChatMessage> messages) {
        return remainingBudget(messages) < 0;
    }
}
