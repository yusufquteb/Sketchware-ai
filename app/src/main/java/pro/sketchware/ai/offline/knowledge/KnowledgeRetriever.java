package pro.sketchware.ai.offline.knowledge;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Shared, provider-agnostic entry point for pulling seeded/user-added project knowledge
 * into a prompt. Wraps {@link KnowledgeStore} + {@link KnowledgeBlockBuilder} — introduces
 * no new retrieval mechanism, no new token-estimation method, no new dependency. Both the
 * online path ({@code AgentExecutor.buildCompactSystemPrompt}) and the offline path
 * ({@code LocalModelProvider.buildPromptFromMessages}) are expected to call this same method
 * once wired in, so the two engines never drift into two different notions of "what the
 * assistant knows."
 *
 * <p><b>Not yet wired into either engine.</b> This class is additive only — see the Phase 1
 * planning report and its follow-up. {@code AgentExecutor}, {@code LocalModelProvider}, and
 * {@code AgentOrchestrator} are unmodified; this class exists so it can be dropped into all
 * three call sites in one small change once the person testing this modification confirms the
 * seeded knowledge (see {@link KnowledgeSeeder}) looks correct.
 *
 * <p><b>pageContext as a relevance hint.</b> {@link KnowledgeStore#searchRelevant} ranks NORMAL
 * entries against a single free-text string via FTS4. Rather than inventing a second retrieval
 * path for {@code pageContext} (the four values the dead {@code AgentExecutor.buildSystemPrompt}
 * method used to switch on: {@code "errors"}, {@code "blocks"}, {@code "blocks_creator"},
 * {@code "library_editor"}/{@code "libraries"}), this folds it into the same query string the
 * user's message is matched against — a message sent from the Compile Log screen with
 * pageContext="errors" naturally surfaces the seeded CATEGORY 8 (error logs) entry through
 * ordinary keyword overlap, without a hardcoded switch statement to keep in sync with the
 * seed data.
 */
public final class KnowledgeRetriever {

    private KnowledgeRetriever() {}

    /**
     * Builds the knowledge block to append to a system prompt.
     *
     * @param store        the shared {@link KnowledgeStore} instance
     * @param userMessage  latest user message text (used to rank NORMAL entries by relevance)
     * @param pageContext  optional page-context hint (e.g. "errors", "blocks") — folded into
     *                     the relevance query alongside {@code userMessage}, not treated as a
     *                     separate switch/lookup
     * @param budgetTokens maximum size this block may occupy — see {@link KnowledgeBlockBuilder}
     *                     for how CRITICAL vs NORMAL entries are prioritised within it. Callers
     *                     should pass a provider-appropriate budget: the existing offline budget
     *                     (300 tokens, see {@code LocalModelProvider.MAX_KNOWLEDGE_BLOCK_TOKENS})
     *                     for the on-device model, and a larger budget for cloud providers with
     *                     more headroom (recommended ~1,200-1,800 tokens — see planning report
     *                     §3 for the reasoning; this keeps skeleton + tools + knowledge block
     *                     comfortably under a 4,000-token effective prompt).
     */
    @NonNull
    public static KnowledgeBlockBuilder.Result buildContextBlock(
            @NonNull KnowledgeStore store,
            @Nullable String userMessage,
            @Nullable String pageContext,
            int budgetTokens) {
        String query = combineQuery(userMessage, pageContext);
        return KnowledgeBlockBuilder.build(store, query, budgetTokens);
    }

    /**
     * Folds {@code pageContext} into the relevance query as extra searchable text, rather than
     * replacing {@code userMessage} — a page-context hint should widen what's found, never
     * narrow out something the user's own words already point at.
     */
    @Nullable
    private static String combineQuery(@Nullable String userMessage, @Nullable String pageContext) {
        boolean hasMessage = userMessage != null && !userMessage.trim().isEmpty();
        boolean hasContext = pageContext != null && !pageContext.trim().isEmpty();
        if (!hasMessage && !hasContext) return null;
        if (!hasContext) return userMessage;
        if (!hasMessage) return pageContext.trim();
        return userMessage + " " + pageContext.trim();
    }
}
