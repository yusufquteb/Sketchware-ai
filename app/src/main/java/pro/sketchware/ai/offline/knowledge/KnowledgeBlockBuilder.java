package pro.sketchware.ai.offline.knowledge;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.engine.budget.TokenBudgetChecker;

/**
 * Formats {@link KnowledgeStore} entries into a compact prompt block for the local model,
 * respecting a hard token budget the same way {@code LocalModelProvider.buildToolBlock}
 * already does for the tool list — this class is deliberately built to slot into that same
 * budget-accounting pattern rather than introduce a second, different one.
 *
 * <p>Priority order when the budget is tight: every {@link KnowledgeStore.Priority#CRITICAL}
 * entry goes in first (uncut — see class note below on why), then as many relevance-ranked
 * {@link KnowledgeStore.Priority#NORMAL} entries as still fit. If even the CRITICAL entries
 * alone would exceed the caller's budget, this still includes all of them and lets the
 * caller's own final pre-flight check (already in {@code LocalModelProvider}) catch the
 * overflow — silently dropping a rule the user explicitly marked as critical would defeat
 * the entire point of this feature, so it is a visible over-budget condition instead of a
 * silent one.
 */
public final class KnowledgeBlockBuilder {

    private KnowledgeBlockBuilder() {}

    /** How many NORMAL (relevance-matched) entries to consider at most, before budget trimming. */
    private static final int MAX_NORMAL_CANDIDATES = 4;

    public static final class Result {
        @NonNull public final String block;
        public final int estimatedTokens;

        Result(@NonNull String block, int estimatedTokens) {
            this.block = block;
            this.estimatedTokens = estimatedTokens;
        }
    }

    /**
     * @param store        the knowledge store to read from
     * @param userMessage  the current user message, used only to rank NORMAL entries by
     *                     relevance — CRITICAL entries are included regardless of this text
     * @param budgetTokens maximum size this block may occupy; NORMAL entries are dropped
     *                     (lowest relevance first) once this is exceeded
     */
    @NonNull
    public static Result build(@NonNull KnowledgeStore store, @Nullable String userMessage,
                                int budgetTokens) {
        List<KnowledgeStore.Entry> critical = store.getCriticalEntries();
        List<KnowledgeStore.Entry> relevant = store.searchRelevant(userMessage, MAX_NORMAL_CANDIDATES);

        if (critical.isEmpty() && relevant.isEmpty()) {
            return new Result("", 0);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Project knowledge (must follow):\n");

        // CRITICAL entries always included in full — see class javadoc for why this is
        // intentional rather than budget-trimmed like NORMAL entries.
        for (KnowledgeStore.Entry e : critical) {
            appendEntry(sb, e);
        }

        // NORMAL entries: add one at a time, stopping as soon as the running total would
        // exceed budgetTokens, so a partial fit never happens — either an entry fits whole
        // or it's left out entirely, keeping every included fact complete and unambiguous.
        int runningTokens = TokenBudgetChecker.estimateTokens(sb.toString());
        for (KnowledgeStore.Entry e : relevant) {
            String formatted = formatEntry(e);
            int entryTokens = TokenBudgetChecker.estimateTokens(formatted);
            if (runningTokens + entryTokens > budgetTokens) break;
            sb.append(formatted);
            runningTokens += entryTokens;
        }

        String block = sb.toString().trim();
        return new Result(block, TokenBudgetChecker.estimateTokens(block));
    }

    private static void appendEntry(StringBuilder sb, KnowledgeStore.Entry e) {
        sb.append(formatEntry(e));
    }

    private static String formatEntry(KnowledgeStore.Entry e) {
        return "- [" + categoryLabel(e.category) + "] " + e.title + ": " + e.content + "\n";
    }

    private static String categoryLabel(KnowledgeStore.Category c) {
        switch (c) {
            case RULE:       return "rule";
            case ENV:        return "env";
            case TOOL:       return "tool";
            case PREFERENCE: return "pref";
            default:         return "info";
        }
    }
}
