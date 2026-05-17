package pro.sketchware.ai.engine;

/**
 * PromptBuilder — builds per-tool, context-aware prompts for the AI engine.
 *
 * <p>Every tool has its own prompt template with strict guardrails baked in.
 * Context (existing XML, Java code, user request, screen info) is injected
 * cleanly to maximize accuracy and minimize hallucinations.
 *
 * <p>Design principles:
 * <ul>
 *   <li>Prompts are deterministic — same inputs → same prompt structure</li>
 *   <li>Every prompt ends with "OUTPUT:" to force the model into output mode</li>
 *   <li>Guardrails are repeated inline so the model can't miss them</li>
 *   <li>XML output is always fenced with {@code ```xml} so extraction is reliable</li>
 * </ul>
 */
public final class PromptBuilder {

    private PromptBuilder() {}

    // ── Shared guardrails appended to every prompt ────────────────────────────

    private static final String GUARDRAILS =
            "\n\n⚠️ STRICT RULES — NEVER VIOLATE:\n"
            + "• Output ONLY valid Android XML inside ```xml … ``` fences\n"
            + "• Never remove existing views unless explicitly asked\n"
            + "• Never change existing android:id values\n"
            + "• Never use deprecated attributes (layout_marginStart = OK, paddingLeft prefer Start/End)\n"
            + "• Never put logic or explanations inside the XML\n"
            + "• All IDs must follow @+id/snake_case format\n"
            + "• Root must always have android:layout_width and android:layout_height\n"
            + "• Never emit ```java, only ```xml\n";

    // ── Tool: GENERATE_UI ──────────────────────────────────────────────────────

    /**
     * Builds a prompt to generate a brand-new layout from a free-text description.
     *
     * @param userRequest   what the user wants (e.g. "login screen with email and password")
     * @param activityName  name of the activity/screen being designed
     * @param projectPkg    app package name (for contextual decisions)
     * @return full system+user prompt string
     */
    public static String buildGenerateUiPrompt(
            String userRequest, String activityName, String projectPkg) {
        return "You are an expert Android layout engineer specializing in Sketchware Pro.\n"
                + "Generate a complete, modern Android XML layout for the following request.\n\n"
                + "Activity: " + safe(activityName) + "\n"
                + "Package: "  + safe(projectPkg)  + "\n"
                + "Screen size target: phone (360–420 dp wide)\n\n"
                + "User request:\n" + safe(userRequest) + "\n\n"
                + "Requirements:\n"
                + "• Use ConstraintLayout or LinearLayout as root\n"
                + "• Apply Material Design 3 style (rounded corners, proper spacing)\n"
                + "• Use android:layout_width/height properly\n"
                + "• Give every interactive view a meaningful android:id\n"
                + "• Use @color/colorPrimary for branding elements\n"
                + "• Margins: 16dp standard, 8dp inner gaps\n"
                + GUARDRAILS
                + "\nOUTPUT (full XML only):";
    }

    // ── Tool: MODIFY_UI ────────────────────────────────────────────────────────

    /**
     * Builds a prompt to modify an EXISTING layout according to user instructions.
     * The existing XML is injected so the model can make precise, surgical changes.
     *
     * @param userRequest   what to change (e.g. "make the button red and add a title TextView")
     * @param existingXml   the current layout XML (will be injected as context)
     * @param activityName  screen being modified
     * @return full prompt string
     */
    public static String buildModifyUiPrompt(
            String userRequest, String existingXml, String activityName) {
        return "You are an expert Android layout engineer.\n"
                + "Modify the existing layout below according to the user's instructions.\n\n"
                + "Activity: " + safe(activityName) + "\n\n"
                + "=== EXISTING LAYOUT ===\n"
                + "```xml\n" + safe(existingXml) + "\n```\n\n"
                + "=== USER MODIFICATION REQUEST ===\n"
                + safe(userRequest) + "\n\n"
                + "Instructions:\n"
                + "• Make ONLY the requested changes — do not restructure everything\n"
                + "• Preserve ALL existing android:id values exactly as-is\n"
                + "• Preserve views NOT mentioned in the request\n"
                + "• Output the COMPLETE modified XML (not just the changed parts)\n"
                + GUARDRAILS
                + "\nOUTPUT (complete modified XML only):";
    }

    // ── Tool: FIX_CODE ─────────────────────────────────────────────────────────

    /**
     * Builds a prompt to fix broken/invalid XML layout.
     *
     * @param brokenXml   the invalid XML that needs fixing
     * @param errorReport diagnostic info from XMLValidator (may be empty)
     * @return full prompt string
     */
    public static String buildFixPrompt(String brokenXml, String errorReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert Android layout debugger.\n")
          .append("Fix the following broken Android XML layout.\n\n");

        if (errorReport != null && !errorReport.isEmpty()) {
            sb.append("=== DIAGNOSTIC ERRORS ===\n")
              .append(errorReport).append("\n\n");
        }

        sb.append("=== BROKEN LAYOUT ===\n")
          .append("```xml\n").append(safe(brokenXml)).append("\n```\n\n")
          .append("Fix:\n")
          .append("• Close all unclosed tags\n")
          .append("• Fix malformed attribute syntax\n")
          .append("• Add missing required attributes (layout_width, layout_height)\n")
          .append("• Remove unknown/unsupported attributes\n")
          .append("• Keep all original IDs and view structure intact\n")
          .append(GUARDRAILS)
          .append("\nOUTPUT (fixed XML only):");
        return sb.toString();
    }

    // ── Tool: OPTIMIZE ────────────────────────────────────────────────────────

    /**
     * Builds a prompt to optimize a layout for performance and best practices.
     *
     * @param xml          the layout to optimize
     * @param activityName screen name
     * @return full prompt string
     */
    public static String buildOptimizePrompt(String xml, String activityName) {
        return "You are an expert Android performance engineer.\n"
                + "Optimize the following layout for maximum performance and best practices.\n\n"
                + "Activity: " + safe(activityName) + "\n\n"
                + "=== CURRENT LAYOUT ===\n"
                + "```xml\n" + safe(xml) + "\n```\n\n"
                + "Optimizations to apply:\n"
                + "• Flatten view hierarchy (reduce nesting depth)\n"
                + "• Replace nested LinearLayouts with ConstraintLayout where beneficial\n"
                + "• Remove redundant wrapper layouts\n"
                + "• Use merge tag for root if this is an included layout\n"
                + "• Ensure all IDs are unique\n"
                + "• Preserve all functionality — do NOT remove views\n"
                + GUARDRAILS
                + "\nOUTPUT (optimized XML only):";
    }

    // ── Tool: RTL_PROMPT (for AI-assisted RTL advice only) ────────────────────

    /**
     * Builds a prompt for AI-based RTL conversion advice.
     * Note: Actual RTL attribute replacement is done by {@link RTLConverter} (pure logic).
     * This prompt is used only when AI explanation/review of RTL issues is requested.
     *
     * @param xml the layout to review for RTL issues
     * @return full prompt string
     */
    public static String buildRtlReviewPrompt(String xml) {
        return "You are an Android RTL (right-to-left) accessibility expert.\n"
                + "Review the following layout for RTL compatibility issues and list them clearly.\n\n"
                + "```xml\n" + safe(xml) + "\n```\n\n"
                + "Report:\n"
                + "• Attributes using 'left'/'right' that should be 'start'/'end'\n"
                + "• Missing layoutDirection or textDirection attributes\n"
                + "• Gravity values that break RTL\n"
                + "• Do NOT output modified XML — output a numbered list of issues only\n"
                + "\nOUTPUT (numbered issue list only):";
    }

    // ── Tool: EXPLAIN ──────────────────────────────────────────────────────────

    /**
     * Builds a prompt to explain what a layout does in plain English/Arabic.
     *
     * @param xml      the layout XML
     * @param language "English" or "Arabic"
     * @return full prompt string
     */
    public static String buildExplainPrompt(String xml, String language) {
        boolean arabic = "Arabic".equalsIgnoreCase(language);
        return (arabic
                ? "أنت خبير أندرويد. اشرح تخطيط XML التالي بالعربية بشكل واضح وبسيط.\n\n"
                : "You are an Android expert. Explain the following XML layout clearly and concisely.\n\n")
                + "```xml\n" + safe(xml) + "\n```\n\n"
                + (arabic
                   ? "اشرح: ما الشاشة التي يمثلها، العناصر المرئية، وترتيبها. لا تخرج XML."
                   : "Explain: what screen it represents, the visual elements, and their layout structure. Do NOT output XML.")
                + "\nOUTPUT:";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Null-safe string: replaces null with empty string. */
    private static String safe(String s) {
        return s != null ? s : "";
    }

    /**
     * Extracts the XML block from an AI response.
     * The model should always output {@code ```xml … ```} but this handles edge cases:
     * raw XML, missing fences, extra text around the block.
     *
     * @param aiResponse the raw AI text response
     * @return extracted XML string, or the raw response if no fence found
     */
    public static String extractXmlFromResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) return "";

        // Primary: ```xml … ``` fence
        int start = aiResponse.indexOf("```xml");
        if (start >= 0) {
            start += 6;
            // skip optional newline right after ```xml
            if (start < aiResponse.length() && aiResponse.charAt(start) == '\n') start++;
            int end = aiResponse.indexOf("```", start);
            if (end > start) return aiResponse.substring(start, end).trim();
        }

        // Secondary: ``` … ``` fence (no language marker)
        start = aiResponse.indexOf("```");
        if (start >= 0) {
            start += 3;
            if (start < aiResponse.length() && aiResponse.charAt(start) == '\n') start++;
            int end = aiResponse.indexOf("```", start);
            if (end > start) return aiResponse.substring(start, end).trim();
        }

        // Tertiary: response starts with a valid XML tag
        String trimmed = aiResponse.trim();
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<LinearLayout")
                || trimmed.startsWith("<ConstraintLayout")
                || trimmed.startsWith("<RelativeLayout")
                || trimmed.startsWith("<FrameLayout")
                || trimmed.startsWith("<ScrollView")
                || trimmed.startsWith("<androidx.")) {
            return trimmed;
        }

        // Fallback: return as-is and let XMLValidator decide
        return aiResponse.trim();
    }
}
