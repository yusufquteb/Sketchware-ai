package pro.sketchware.ai.fix;

/**
 * Holds the AI-proposed fix for a compile error.
 *
 * After the AI analyses the error context in an AiFixSession,
 * it produces an AiFixSuggestion with the corrected block logic or source code.
 */
public class AiFixSuggestion {

    public final String sessionId;
    public final String fixDescription;
    public final String originalCode;
    public final String fixedCode;
    public final boolean isApplicable;

    public AiFixSuggestion(
            String sessionId,
            String fixDescription,
            String originalCode,
            String fixedCode,
            boolean isApplicable) {
        this.sessionId      = sessionId;
        this.fixDescription = fixDescription;
        this.originalCode   = originalCode;
        this.fixedCode      = fixedCode;
        this.isApplicable   = isApplicable;
    }

    /** Creates a suggestion that cannot be applied automatically. */
    public static AiFixSuggestion informational(String sessionId, String description) {
        return new AiFixSuggestion(sessionId, description, null, null, false);
    }

    /** Creates a suggestion with a specific code fix ready to apply. */
    public static AiFixSuggestion withFix(
            String sessionId, String description, String original, String fixed) {
        return new AiFixSuggestion(sessionId, description, original, fixed, true);
    }
}
