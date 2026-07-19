package pro.sketchware.ai.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;

/**
 * Lightweight token estimator for Android AI clients.
 *
 * <p>Uses per-provider character-per-token ratios rather than a single global constant.
 * Anthropic's BPE tokenizer is more code-efficient (~3.5 chars/token) compared with the
 * GPT-family default of ~4.0. Running a proper on-device tokenizer is too expensive for
 * a mobile IDE, so these ratios give ±10% accuracy — sufficient for compression decisions.
 *
 * <p>Estimates are used only for proactive compression, not for billing.
 */
public final class TokenEstimator {

    /** Fallback characters per token for unknown providers. */
    private static final double DEFAULT_CHARS_PER_TOKEN = 4.0;

    /** Tokens added per message for role header + structural delimiters. */
    private static final int OVERHEAD_PER_MESSAGE = 4;

    /** Extra tokens for the reply primer (the model's response start). */
    private static final int REPLY_PRIMER_TOKENS = 3;

    private TokenEstimator() {}

    // ── Provider-specific ratios ──────────────────────────────────────────────

    /**
     * Returns the approximate characters-per-token ratio for a given provider.
     * Lower = more tokens per character = more expensive in context terms.
     */
    public static double charsPerToken(@Nullable AiProvider provider) {
        if (provider == null) return DEFAULT_CHARS_PER_TOKEN;
        switch (provider) {
            case ANTHROPIC:
                // Claude uses a more code-efficient BPE tokenizer (~3.5 chars/token for mixed code)
                return 3.5;
            case GEMINI:
            case GOOGLE_AI_STUDIO:
                // Gemini's SentencePiece tokenizer is slightly more efficient than GPT's
                return 3.8;
            case DEEPSEEK:
                // DeepSeek uses a large vocabulary tokenizer, slightly more efficient for code
                return 3.7;
            default:
                // GPT-family, OpenRouter, Groq, and most OpenAI-compatible providers
                return DEFAULT_CHARS_PER_TOKEN;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Estimates the token count for a plain string using the default ratio. */
    public static int estimate(@NonNull String text) {
        return estimate(text, null);
    }

    /** Estimates the token count for a plain string using the provider's ratio. */
    public static int estimate(@NonNull String text, @Nullable AiProvider provider) {
        return Math.max(0, (int) Math.ceil(text.length() / charsPerToken(provider)));
    }

    /**
     * Estimates the total token count for a message list using the default ratio.
     * Accounts for per-message overhead and the reply primer.
     */
    public static int estimate(@NonNull List<ChatMessage> messages) {
        return estimate(messages, null);
    }

    /**
     * Estimates the total token count for a message list using the provider's ratio.
     * Accounts for per-message overhead and the reply primer.
     */
    public static int estimate(@NonNull List<ChatMessage> messages, @Nullable AiProvider provider) {
        int total = REPLY_PRIMER_TOKENS;
        for (ChatMessage m : messages) {
            total += OVERHEAD_PER_MESSAGE;
            if (m.getContent() != null) {
                total += estimate(m.getContent(), provider);
            }
            if (m.getToolCalls() != null) {
                for (pro.sketchware.ai.models.ToolCall tc : m.getToolCalls()) {
                    if (tc.getArguments() != null) total += estimate(tc.getArguments(), provider);
                    if (tc.getName()      != null) total += estimate(tc.getName(),      provider);
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
        return isApproachingLimit(messages, maxContextTokens, null);
    }

    /** Provider-aware variant of {@link #isApproachingLimit(List, int)}. */
    public static boolean isApproachingLimit(
            @NonNull List<ChatMessage> messages,
            int maxContextTokens,
            @Nullable AiProvider provider) {
        if (maxContextTokens <= 0) return false;
        return estimate(messages, provider) > maxContextTokens * 0.85;
    }

    /**
     * Computes the recommended max-messages budget for a given context window.
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
