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
     * Builds a concise summary of the older messages so the model understands
     * what was already discussed and decided.
     */
    private static String buildSummaryText(List<ChatMessage> older) {
        StringBuilder sb = new StringBuilder();
        sb.append("[CONVERSATION SUMMARY — ").append(older.size())
          .append(" earlier messages compressed to save tokens]\n\n");

        int idx = 1;
        for (ChatMessage m : older) {
            String role = m.getRole();
            if (!"user".equals(role) && !"assistant".equals(role)) continue;
            String content = m.getContent();
            if (content == null || content.trim().isEmpty()) continue;
            // Keep only first 200 chars of each old message in the summary
            String snippet = content.length() > 200
                    ? content.substring(0, 200) + "…"
                    : content;
            sb.append(idx++).append(". [").append(role).append("] ").append(snippet).append("\n");
        }
        sb.append("\n[End of summary — full conversation continues below]");
        return sb.toString();
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
