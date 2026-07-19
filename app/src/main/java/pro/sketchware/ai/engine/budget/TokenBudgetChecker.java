package pro.sketchware.ai.engine.budget;

import java.util.List;

import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * Internal pre-flight check, NOT an {@link pro.sketchware.ai.tools.AgentTool} —
 * the LLM never calls this directly. It is meant to be invoked from the send
 * path right before a request leaves the device, so an oversized payload is
 * caught before spending a failed (or absurdly expensive) API call.
 *
 * INTEGRATION POINT (not yet wired — see CHANGES.md "Phase 3" section):
 * the natural call site is {@code AgentExecutor.execute()}'s per-iteration
 * block, immediately after {@code messages = TokenOptimizer.optimise(...)} and
 * before {@code currentClient.sendChatRequest(...)} is invoked
 * (around the `List<ChatMessage> messages = TokenOptimizer.optimise(...)` line).
 * That file was intentionally NOT modified in this phase, per this phase's own
 * rules (no new tool needing device testing beyond compile-level verification,
 * and to avoid touching AgentExecutor's already-large, sensitive control flow
 * without an explicit go-ahead) — wiring it in is a one-line call left for the
 * next session:
 * <pre>
 *   TokenBudgetChecker.Result budgetCheck =
 *       TokenBudgetChecker.check(messages, effectiveSystemPrompt, preferences);
 *   if (!budgetCheck.withinBudget) {
 *       postError(callback, budgetCheck.message);
 *       return;
 *   }
 * </pre>
 * {@link pro.sketchware.ai.orchestrator.AgentOrchestrator}'s planning call (a single, usually short, one-shot
 * request) is a lower priority integration point than AgentExecutor's per-turn
 * loop, since payload growth mainly comes from long conversation history, which
 * only AgentExecutor accumulates.
 *
 * ESTIMATION METHOD (design decision — no tokenizer is bundled in this project,
 * and pulling in a real tokenizer per-provider is out of scope for this phase):
 * plain character-count heuristic, 4 characters ≈ 1 token, which is the same
 * rough ratio commonly used for English text with GPT/Claude/Llama-family
 * tokenizers. This will under- or over-count for non-English text or code-heavy
 * payloads, but is good enough as a coarse guard against runaway payloads — it
 * is NOT meant to match a provider's exact billed token count.
 */
public final class TokenBudgetChecker {

    private TokenBudgetChecker() {}

    /** Used by {@link AiPreferences#getMaxPayloadTokens()} when the user never configured a limit. */
    public static final int DEFAULT_MAX_PAYLOAD_TOKENS = 100_000;

    private static final int CHARS_PER_TOKEN = 4;

    public static final class Result {
        public final boolean withinBudget;
        public final int estimatedTokens;
        public final int limit;
        public final String message;

        private Result(boolean withinBudget, int estimatedTokens, int limit, String message) {
            this.withinBudget = withinBudget;
            this.estimatedTokens = estimatedTokens;
            this.limit = limit;
            this.message = message;
        }
    }

    /** Rough estimate: total characters across all message content / 4. */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN);
    }

    /**
     * Estimates the total payload size (system prompt + full message history) and
     * compares it against the configured limit.
     *
     * @param messages      the conversation history about to be sent
     * @param systemPrompt  the resolved system prompt about to be sent (compact or full)
     * @param preferences   used to read the configured limit; pass null to use
     *                      {@link #DEFAULT_MAX_PAYLOAD_TOKENS}
     */
    public static Result check(List<ChatMessage> messages, String systemPrompt, AiPreferences preferences) {
        int limit = preferences != null ? preferences.getMaxPayloadTokens() : DEFAULT_MAX_PAYLOAD_TOKENS;
        if (limit <= 0) {
            // 0 is the "never configured, no cap" sentinel used elsewhere in AiPreferences
            // (see getMaxTokens()) — treat consistently here as "no limit enforced".
            return new Result(true, 0, 0, null);
        }

        int total = estimateTokens(systemPrompt);
        if (messages != null) {
            for (ChatMessage m : messages) {
                total += estimateTokens(m.getContent());
            }
        }

        if (total > limit) {
            String msg = "Estimated payload (~" + total + " tokens) exceeds the configured limit ("
                    + limit + " tokens). Trim the conversation or raise the limit in AI Settings.";
            return new Result(false, total, limit, msg);
        }
        return new Result(true, total, limit, null);
    }
}
