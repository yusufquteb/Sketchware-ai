package pro.sketchware.ai.engine;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.ChatMessage;

/**
 * Token Optimizer — reduces API token consumption without degrading quality.
 *
 * Strategies implemented:
 *  1. Token Budget       — enforces a maximum message-history size (context window cap).
 *  2. Message Summary    — after {@link #SUMMARY_THRESHOLD} assistant+user turns, older
 *                          messages are collapsed into a single SUMMARY system message so
 *                          the model retains context without re-reading every turn.
 *  3. Tool Result Trim   — truncates oversized tool outputs to {@link #MAX_TOOL_CHARS}
 *                          characters (keeps head + tail with an ellipsis).
 *  4. Lazy Context       — callers can request a "slim" version of the history that
 *                          excludes bulky tool result messages when the model only needs
 *                          recent conversational context.
 *
 * All methods are pure functions (no side-effects on the input list).
 */
public final class TokenOptimizer {

    // ── Tuneable constants ────────────────────────────────────────────────────

    /**
     * Number of conversation turns (user + assistant pairs) before summarisation
     * collapses older turns into a single summary message.
     */
    public static final int SUMMARY_THRESHOLD = 10;

    /**
     * Maximum number of characters to keep from a single tool result.
     * Tool results larger than this are trimmed: first 2 000 + last 1 000 chars.
     */
    public static final int MAX_TOOL_CHARS = 3_000;

    /**
     * Hard cap on the number of messages sent in one API request.
     * The most recent {@code TOKEN_BUDGET_MESSAGES} messages are always kept verbatim.
     */
    public static final int TOKEN_BUDGET_MESSAGES = 40;

    private TokenOptimizer() {}

    // ── 1. Token Budget ───────────────────────────────────────────────────────

    /**
     * Trims the history to at most {@link #TOKEN_BUDGET_MESSAGES} entries,
     * always preserving the tail (most recent messages).
     *
     * @param history the full conversation history
     * @return a new list (possibly the same object if already within budget)
     */
    public static List<ChatMessage> applyBudget(List<ChatMessage> history) {
        if (history == null || history.size() <= TOKEN_BUDGET_MESSAGES) return history;
        return new ArrayList<>(history.subList(history.size() - TOKEN_BUDGET_MESSAGES, history.size()));
    }

    // ── 2. Message Summarisation ──────────────────────────────────────────────

    /**
     * Counts how many user+assistant conversational turns are in the history.
     *
     * @return the number of full turns (each turn = 1 user + 1 assistant message)
     */
    public static int countTurns(List<ChatMessage> history) {
        int turns = 0;
        boolean lastWasUser = false;
        for (ChatMessage m : history) {
            if ("user".equals(m.getRole())) {
                lastWasUser = true;
            } else if ("assistant".equals(m.getRole()) && lastWasUser) {
                turns++;
                lastWasUser = false;
            }
        }
        return turns;
    }

    /**
     * If the history has more than {@link #SUMMARY_THRESHOLD} turns, collapses
     * all but the most recent {@code SUMMARY_THRESHOLD / 2} turns into a single
     * SYSTEM summary message prepended to the trimmed list.
     *
     * <p>The summary message describes what was discussed in the collapsed turns
     * so the model keeps track of prior decisions without re-reading them.
     *
     * @param history the full conversation history
     * @return optimised history; original list is NOT mutated
     */
    public static List<ChatMessage> summariseIfNeeded(List<ChatMessage> history) {
        if (history == null) return new ArrayList<>();
        int turns = countTurns(history);
        if (turns <= SUMMARY_THRESHOLD) return history;

        // Split: keep only the last SUMMARY_THRESHOLD/2 turns verbatim
        int keepTurns   = SUMMARY_THRESHOLD / 2;
        int splitIndex  = findSplitIndex(history, turns - keepTurns);

        List<ChatMessage> older  = history.subList(0, splitIndex);
        List<ChatMessage> recent = history.subList(splitIndex, history.size());

        String summaryText = buildSummaryText(older);
        ChatMessage summaryMsg = ChatMessage.systemMessage(summaryText);

        List<ChatMessage> result = new ArrayList<>();
        result.add(summaryMsg);
        result.addAll(recent);
        return result;
    }

    /** Finds the message index where the {@code targetTurn}-th turn starts. */
    private static int findSplitIndex(List<ChatMessage> history, int targetTurn) {
        int turns = 0;
        boolean lastWasUser = false;
        for (int i = 0; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            if ("user".equals(m.getRole())) {
                lastWasUser = true;
            } else if ("assistant".equals(m.getRole()) && lastWasUser) {
                turns++;
                lastWasUser = false;
                if (turns == targetTurn) return i + 1; // start of next turn
            }
        }
        return history.size();
    }

    /**
     * Builds a smart project-state-aware summary of older messages.
     *
     * Instead of truncating every message to 200 chars (lossy), this method:
     *  1. Extracts structured signals from tool calls and results (files written,
     *     libraries added, activities created, build status)
     *  2. Reconstructs a compact project state block
     *  3. Appends short snippets of user requests for intent continuity
     *
     * This gives the model a much more useful compressed context than raw snippets.
     */
    private static String buildSummaryText(List<ChatMessage> older) {
        // ── Signal extraction ──────────────────────────────────────────────────
        java.util.List<String> filesWritten    = new java.util.ArrayList<>();
        java.util.List<String> activitiesAdded = new java.util.ArrayList<>();
        java.util.List<String> libsAdded       = new java.util.ArrayList<>();
        java.util.List<String> userRequests    = new java.util.ArrayList<>();
        String lastBuildStatus = null;
        String lastBuildError  = null;

        for (ChatMessage m : older) {
            String role    = m.getRole();
            String content = m.getContent();
            if (content == null || content.isEmpty()) continue;

            if ("user".equals(role)) {
                // Keep first 120 chars of user messages for intent continuity
                String req = content.trim();
                if (req.length() > 120) req = req.substring(0, 120) + "…";
                userRequests.add(req);

            } else if ("tool".equals(role)) {
                // Extract signals from tool results
                String lower = content.toLowerCase();
                if (lower.contains("written") || lower.contains("file saved")
                        || lower.contains("write_file") || lower.contains("patch_file")) {
                    extractFileRef(content, filesWritten);
                }
                if (lower.contains("activity") && (lower.contains("created") || lower.contains("added"))) {
                    extractActivityRef(content, activitiesAdded);
                }
                if (lower.contains("library") && (lower.contains("added") || lower.contains("downloaded")
                        || lower.contains("attached"))) {
                    extractLibRef(content, libsAdded);
                }
                if (lower.contains("build") && lower.contains("success")) {
                    lastBuildStatus = "✅ Build succeeded";
                } else if (lower.contains("build") && (lower.contains("failed") || lower.contains("error:"))) {
                    lastBuildStatus = "❌ Build failed";
                    // Extract first error line
                    for (String line : content.split("\n")) {
                        if (line.trim().contains("error:")) {
                            lastBuildError = line.trim().length() > 200
                                    ? line.trim().substring(0, 200) : line.trim();
                            break;
                        }
                    }
                }

            } else if ("assistant".equals(role)) {
                // Extract tool call intents from assistant messages (tool_calls embedded in content)
                if (content.contains("write_file") || content.contains("patch_file")) {
                    extractFileRef(content, filesWritten);
                }
            }
        }

        // ── Build summary text ─────────────────────────────────────────────────
        StringBuilder sb = new StringBuilder();
        sb.append("╔══ CONVERSATION SUMMARY ").append(older.size())
          .append(" messages compressed ══╗\n\n");

        // Project state block
        if (!filesWritten.isEmpty() || !activitiesAdded.isEmpty() || !libsAdded.isEmpty()
                || lastBuildStatus != null) {
            sb.append("## Project State (from earlier in session)\n");
            if (!activitiesAdded.isEmpty()) {
                sb.append("  Activities created/modified: ").append(dedup(activitiesAdded)).append("\n");
            }
            if (!filesWritten.isEmpty()) {
                sb.append("  Files written/patched: ").append(dedup(filesWritten)).append("\n");
            }
            if (!libsAdded.isEmpty()) {
                sb.append("  Libraries added: ").append(dedup(libsAdded)).append("\n");
            }
            if (lastBuildStatus != null) {
                sb.append("  Last build: ").append(lastBuildStatus).append("\n");
                if (lastBuildError != null)
                    sb.append("  Last error: ").append(lastBuildError).append("\n");
            }
            sb.append("\n");
        }

        // User intent history (last 5 requests)
        if (!userRequests.isEmpty()) {
            sb.append("## Earlier User Requests\n");
            int start = Math.max(0, userRequests.size() - 5);
            for (int i = start; i < userRequests.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(userRequests.get(i)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("╚══ Full conversation continues below ══╝\n");
        return sb.toString();
    }

    private static void extractFileRef(String content, java.util.List<String> out) {
        // Look for path-like strings: anything with / or ending in .java/.xml/.json
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([\\w/]+\\.(?:java|xml|json|kt|gradle|properties))")
                .matcher(content);
        while (m.find() && out.size() < 10) out.add(m.group(1));
    }

    private static void extractActivityRef(String content, java.util.List<String> out) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("activity[\\s'\"]*[:\\s]+([\\w]+Activity|[\\w]+Fragment)",
                         java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(content);
        while (m.find() && out.size() < 5) out.add(m.group(1));
    }

    private static void extractLibRef(String content, java.util.List<String> out) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([a-z][a-z0-9_]+\\.[a-z][a-z0-9_.]+:[a-z][a-z0-9_\\-]+:[0-9][0-9.]+)")
                .matcher(content);
        while (m.find() && out.size() < 5) out.add(m.group(1));
        // Also extract simple library names
        m = java.util.regex.Pattern
                .compile("(?:library|lib)\\s+['\"]([^'\"]+)['\"]",
                         java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(content);
        while (m.find() && out.size() < 5) out.add(m.group(1));
    }

    private static String dedup(java.util.List<String> list) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>(list);
        java.util.List<String> unique = new java.util.ArrayList<>(seen);
        if (unique.size() > 8) {
            return String.join(", ", unique.subList(0, 8)) + " (+" + (unique.size() - 8) + " more)";
        }
        return String.join(", ", unique);
    }

    // ── 3. Tool Result Truncation ─────────────────────────────────────────────

    /**
     * Returns a new history list where any tool-result message whose content
     * exceeds {@link #MAX_TOOL_CHARS} is trimmed to keep the head and tail,
     * with an ellipsis in the middle.
     *
     * @param history the conversation history
     * @return new list with trimmed tool results
     */
    public static List<ChatMessage> truncateToolResults(List<ChatMessage> history) {
        if (history == null) return new ArrayList<>();
        List<ChatMessage> result = new ArrayList<>(history.size());
        for (ChatMessage m : history) {
            result.add(maybeTrimToolResult(m));
        }
        return result;
    }

    private static ChatMessage maybeTrimToolResult(ChatMessage m) {
        if (!"tool".equals(m.getRole())) return m;
        String content = m.getContent();
        if (content == null || content.length() <= MAX_TOOL_CHARS) return m;

        int headLen = MAX_TOOL_CHARS * 2 / 3;   // ~2 000 chars
        int tailLen = MAX_TOOL_CHARS - headLen;  // ~1 000 chars
        String trimmed = content.substring(0, headLen)
                + "\n… [" + (content.length() - headLen - tailLen) + " chars omitted to save tokens] …\n"
                + content.substring(content.length() - tailLen);
        // Return a new ChatMessage with trimmed content
        return ChatMessage.toolResultMessage(m.getToolCallId(), m.getToolName(), trimmed);
    }

    // ── 4. Lazy Context (slim history) ────────────────────────────────────────

    /**
     * Returns a "slim" copy of the history suitable for a simple conversational
     * query that does not need full tool context.
     *
     * Tool-result messages are replaced with a one-line placeholder so the
     * model knows a tool ran but doesn't re-read its entire output.
     *
     * @param history the full conversation history
     * @return slim history
     */
    public static List<ChatMessage> slimHistory(List<ChatMessage> history) {
        if (history == null) return new ArrayList<>();
        List<ChatMessage> result = new ArrayList<>(history.size());
        for (ChatMessage m : history) {
            if ("tool".equals(m.getRole())) {
                result.add(ChatMessage.toolResultMessage(
                        m.getToolCallId(), m.getToolName(),
                        "[Tool result omitted — use get_compile_logs / read_file to access if needed]"));
            } else {
                result.add(m);
            }
        }
        return result;
    }

    // ── Combined pipeline ─────────────────────────────────────────────────────

    /**
     * Runs the full optimisation pipeline in the recommended order:
     *  summarise → truncate tool results → budget cap.
     *
     * @param history full conversation history
     * @return optimised history ready to send to the API
     */
    public static List<ChatMessage> optimise(List<ChatMessage> history) {
        List<ChatMessage> h = summariseIfNeeded(history);
        h = truncateToolResults(h);
        h = applyBudget(h);
        return h;
    }
}
