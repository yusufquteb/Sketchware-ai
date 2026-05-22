package pro.sketchware.ai.engine;

import androidx.annotation.NonNull;

import java.util.List;

import pro.sketchware.ai.models.ChatMessage;

/**
 * Lightweight token estimator for Android AI clients.
 *
 * <p>Uses the industry-standard approximation of <b>~4 characters per token</b>
 * (accurate within ±15% for English/code content across GPT-family and Gemini models).
 * Each message also incurs a fixed overhead for the role header and structural delimiters.
 *
 * <p>This is intentionally simple — running a proper BPE tokenizer on-device is too
 * expensive for a mobile IDE. The estimate is used only for proactive compression
 * decisions, not for billing.
 */
public final class TokenEstimator {

    /** Approximate characters per token — conservative estimate. */
    private static final int CHARS_PER_TOKEN = 4;

    /** Tokens added per message for role header + structural delimiters. */
    private static final int OVERHEAD_PER_MESSAGE = 4;

    /** Extra tokens for the reply primer (the model's response start). */
    private static final int REPLY_PRIMER_TOKENS = 3;

    private TokenEstimator() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /** Estimates the token count for a plain string. */
    public static int estimate(@NonNull String text) {
        return Math.max(0, (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN));
    }

    /**
     * Estimates the total token count for a message list.
     * Accounts for per-message overhead and the reply primer.
     */
    public static int estimate(@NonNull List<ChatMessage> messages) {
        int total = REPLY_PRIMER_TOKENS;
        for (ChatMessage m : messages) {
            total += OVERHEAD_PER_MESSAGE;
            if (m.getContent() != null) {
                total += estimate(m.getContent());
            }
            // Count tool_calls arguments if present
            if (m.getToolCalls() != null) {
                for (pro.sketchware.ai.models.ToolCall tc : m.getToolCalls()) {
                    if (tc.getArguments() != null) total += estimate(tc.getArguments());
                    if (tc.getName()      != null) total += estimate(tc.getName());
                }
            }
        }
        return total;
    }

    /**
     * Returns {@code true} when the estimated token count exceeds 85% of the
     * provider's reported context window — triggering proactive compression.
     */
    public static boolean isApproachingLimit(
            @NonNull List<ChatMessage> messages,
            int maxContextTokens) {
        if (maxContextTokens <= 0) return false;
        return estimate(messages) > maxContextTokens * 0.85;
    }

    /**
     * Computes the recommended max-messages budget for a given context window.
     *
     * <p>Assumes an average message size of 200 characters (~50 tokens).
     * The result is clamped between {@code minBudget} and {@code maxBudget}.
     */
    public static int budgetForContextWindow(int maxContextTokens, int minBudget, int maxBudget) {
        if (maxContextTokens <= 0) return maxBudget;
        // Reserve 30% of context for system prompt + tool defs + response headroom
        int usableTokens = (int) (maxContextTokens * 0.70);
        int avgTokensPerMessage = 50;
        int computed = usableTokens / avgTokensPerMessage;
        return Math.max(minBudget, Math.min(maxBudget, computed));
    }
}
