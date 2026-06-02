package pro.sketchware.ai.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
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
 * <p>Each classification now returns a {@link ClassificationResult} carrying both
 * the task type and a confidence score [0.0–1.0]. When confidence falls below
 * {@link #LOW_CONFIDENCE_THRESHOLD}, callers should prefer a safer fallback
 * (e.g., ANALYSIS or the deep profile).
 *
 * <p>Design intent: keep this simple. The routing decision is advisory — the
 * user's explicit profile choice always takes precedence.
 */
public final class ModelRouter {

    private ModelRouter() {}

    /** Minimum confidence below which the result should be treated as uncertain. */
    public static final float LOW_CONFIDENCE_THRESHOLD = 0.65f;

    // ── Task taxonomy ─────────────────────────────────────────────────────────

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

    /**
     * Result of a classification: task type plus a confidence score in [0.0–1.0].
     * Confidence reflects how many distinct signals matched and how unambiguous they were.
     */
    public static final class ClassificationResult {
        public final TaskType type;
        /** 0.0 = no signal, 1.0 = very confident. */
        public final float confidence;

        private ClassificationResult(TaskType type, float confidence) {
            this.type       = type;
            this.confidence = Math.max(0f, Math.min(1f, confidence));
        }

        /** Returns true when confidence is below {@link #LOW_CONFIDENCE_THRESHOLD}. */
        public boolean isUncertain() {
            return confidence < LOW_CONFIDENCE_THRESHOLD;
        }
    }

    /** Profile key constant — matches the value stored by AiSettingsActivity's toggle. */
    public static final String PROFILE_QUICK = "QUICK";
    /** Profile key constant — matches the value stored by AiSettingsActivity's toggle. */
    public static final String PROFILE_DEEP  = "DEEP";

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
     * Classifies {@code userMessage} and returns a {@link ClassificationResult}
     * with both the task type and a confidence score.
     *
     * <p>Confidence calculation:
     * <ul>
     *   <li>Strong structural signal (long context, path refs): 0.95</li>
     *   <li>Multiple matching keywords in the same category: 0.85+</li>
     *   <li>Single keyword match: 0.75</li>
     *   <li>Short message, no keywords: 0.60 (uncertain QUICK_REPLY)</li>
     *   <li>Medium length, ambiguous: 0.55 (uncertain ANALYSIS)</li>
     * </ul>
     */
    @NonNull
    public static ClassificationResult classifyWithConfidence(@Nullable String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new ClassificationResult(TaskType.QUICK_REPLY, 0.90f);
        }
        String msg = userMessage.trim();

        // Strong structural signal — very high confidence
        if (msg.length() > 500 || P_LONG_CONTEXT.matcher(msg).find()) {
            return new ClassificationResult(TaskType.LONG_CONTEXT, 0.95f);
        }

        // Count keyword matches per category to score confidence
        int codingMatches    = countMatches(P_CODING,    msg);
        int analysisMatches  = countMatches(P_ANALYSIS,  msg);
        int creativeMatches  = countMatches(P_CREATIVE,  msg);

        int maxMatches = Math.max(codingMatches, Math.max(analysisMatches, creativeMatches));

        // Ambiguity penalty: reduce confidence when two categories score equally
        boolean ambiguous = (codingMatches > 0 && analysisMatches > 0 && codingMatches == analysisMatches)
                || (codingMatches > 0 && creativeMatches > 0 && codingMatches == creativeMatches);

        if (maxMatches == 0) {
            // No keyword signal
            if (msg.length() < 80) return new ClassificationResult(TaskType.QUICK_REPLY, 0.60f);
            return new ClassificationResult(TaskType.ANALYSIS, 0.55f);
        }

        float baseConfidence = maxMatches >= 3 ? 0.90f : (maxMatches == 2 ? 0.80f : 0.75f);
        if (ambiguous) baseConfidence -= 0.15f;

        if (codingMatches >= analysisMatches && codingMatches >= creativeMatches) {
            return new ClassificationResult(TaskType.CODING,    baseConfidence);
        }
        if (creativeMatches > analysisMatches) {
            return new ClassificationResult(TaskType.CREATIVE,  baseConfidence);
        }
        return new ClassificationResult(TaskType.ANALYSIS, baseConfidence);
    }

    /**
     * Classifies {@code userMessage} into a {@link TaskType}.
     * When confidence is below {@link #LOW_CONFIDENCE_THRESHOLD}, falls back to
     * {@link TaskType#ANALYSIS} as a safe default.
     */
    @NonNull
    public static TaskType classify(@Nullable String userMessage) {
        ClassificationResult result = classifyWithConfidence(userMessage);
        if (result.isUncertain() && result.type == TaskType.QUICK_REPLY) {
            // Uncertain short messages might actually need a capable model
            return TaskType.ANALYSIS;
        }
        return result.type;
    }

    /**
     * Returns the recommended profile for the given task type.
     * QUICK_REPLY and ANALYSIS → QUICK; everything else → DEEP.
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
     * Classifies {@code userMessage} and returns the recommended profile.
     * Low-confidence classifications fall back to PROFILE_DEEP to avoid routing
     * a complex task to an underpowered model.
     */
    @NonNull
    public static String recommendProfile(@Nullable String userMessage) {
        ClassificationResult result = classifyWithConfidence(userMessage);
        if (result.isUncertain()) return PROFILE_DEEP;
        return recommendedProfile(result.type);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int countMatches(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }
}
