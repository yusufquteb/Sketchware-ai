package pro.sketchware.ai.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Lightweight task classifier that recommends whether to use a fast/cheap model
 * ("quick" profile) or a powerful reasoning model ("deep" profile) for a given
 * user message.
 *
 * <p>Classification is rule-based (regex + heuristics) — no ML, no I/O,
 * sub-millisecond execution. It integrates with the existing Quick/Deep profile
 * system in {@link pro.sketchware.ai.storage.AiPreferences}.
 *
 * <p>Design intent: keep this simple. The routing decision is advisory — the
 * user's explicit profile choice always takes precedence.
 */
public final class ModelRouter {

    private ModelRouter() {}

    // ── Task taxonomy ─────────────────────────────────────────────────────────

    /**
     * Coarse task classification for routing purposes.
     */
    public enum TaskType {
        /** Short conversational question, no coding required. */
        QUICK_REPLY,
        /** Implementation, bug fix, refactoring, or multi-step coding. */
        CODING,
        /** Code/concept explanation, analysis, debugging help. */
        ANALYSIS,
        /** Design, UX, creative content generation. */
        CREATIVE,
        /** Long transcript, multi-file context, or context-heavy review. */
        LONG_CONTEXT,
    }

    /** Profile key constant matching {@code AiPreferences.PROFILE_QUICK}. */
    public static final String PROFILE_QUICK = "quick";
    /** Profile key constant matching {@code AiPreferences.PROFILE_DEEP}. */
    public static final String PROFILE_DEEP  = "deep";

    // ── Patterns ──────────────────────────────────────────────────────────────

    private static final Pattern P_CODING = Pattern.compile(
            "(?i)\\b(build|create|implement|add|write|generate|refactor|"
            + "migrate|convert|fix|debug|repair|update|modify|rename|delete|"
            + "remove|extract|integrate|setup|configure|deploy|test|"
            + "add feature|add method|add class|add activity)\\b");

    private static final Pattern P_ANALYSIS = Pattern.compile(
            "(?i)\\b(explain|describe|what is|what are|how does|how do|"
            + "why does|why is|tell me|summarize|summarise|overview|"
            + "understand|review|analyse|analyze|show me)\\b");

    private static final Pattern P_CREATIVE = Pattern.compile(
            "(?i)\\b(design|ui|ux|layout|color|theme|palette|icon|"
            + "material|style|animation|creative|suggest|idea|brainstorm)\\b");

    private static final Pattern P_LONG_CONTEXT = Pattern.compile(
            "(?i)(file[s]?/|src/|res/|/java/|/layout/|all file|entire project|"
            + "across the codebase|multiple file|full project)");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Classifies {@code userMessage} into a {@link TaskType}.
     *
     * @param userMessage the raw user message (may be null/empty)
     * @return a non-null TaskType; defaults to {@link TaskType#QUICK_REPLY} when uncertain
     */
    @NonNull
    public static TaskType classify(@Nullable String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) return TaskType.QUICK_REPLY;
        String msg = userMessage.trim();

        // Long messages or multi-file references → LONG_CONTEXT
        if (msg.length() > 500 || P_LONG_CONTEXT.matcher(msg).find()) {
            return TaskType.LONG_CONTEXT;
        }

        // Explicit coding intent → CODING
        if (P_CODING.matcher(msg).find()) return TaskType.CODING;

        // Analysis/explanation → ANALYSIS
        if (P_ANALYSIS.matcher(msg).find()) return TaskType.ANALYSIS;

        // Creative/design → CREATIVE
        if (P_CREATIVE.matcher(msg).find()) return TaskType.CREATIVE;

        // Short message, no strong signal → QUICK_REPLY
        if (msg.length() < 80) return TaskType.QUICK_REPLY;

        // Default for medium-length ambiguous messages
        return TaskType.ANALYSIS;
    }

    /**
     * Returns the recommended profile ("quick" or "deep") for the given task type.
     *
     * <ul>
     *   <li>{@code QUICK_REPLY} → "quick" (fast, cheap model)</li>
     *   <li>{@code ANALYSIS}    → "quick" (conversational, moderate complexity)</li>
     *   <li>{@code CODING}      → "deep" (requires strong reasoning)</li>
     *   <li>{@code CREATIVE}    → "deep" (creative generation benefits from capable models)</li>
     *   <li>{@code LONG_CONTEXT}→ "deep" (needs large context window)</li>
     * </ul>
     *
     * @param taskType the classified task type
     * @return {@link #PROFILE_QUICK} or {@link #PROFILE_DEEP}
     */
    @NonNull
    public static String recommendedProfile(@NonNull TaskType taskType) {
        switch (taskType) {
            case QUICK_REPLY:
            case ANALYSIS:
                return PROFILE_QUICK;
            case CODING:
            case CREATIVE:
            case LONG_CONTEXT:
            default:
                return PROFILE_DEEP;
        }
    }

    /**
     * Convenience method: classifies {@code userMessage} and returns the
     * recommended profile in one call.
     *
     * @param userMessage the raw user message
     * @return {@link #PROFILE_QUICK} or {@link #PROFILE_DEEP}
     */
    @NonNull
    public static String recommendProfile(@Nullable String userMessage) {
        return recommendedProfile(classify(userMessage));
    }
}
