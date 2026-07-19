package pro.sketchware.ai.fix;

import android.content.Context;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Fx;
import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.project.ProjectSettings;

/**
 * AiFixSupport — bridges compile errors to the nikit AI agent.
 *
 * When a Sketchware project fails to compile, this class:
 *   1. Parses the raw compile log to extract the file name, line number, and error message.
 *   2. Locates the matching activity and block event responsible for the error.
 *   3. Packages all context into an AiFixSession.
 *   4. Builds a system prompt that can be injected into the AI agent's ChatActivity
 *      so it can analyse and fix the error automatically.
 *
 * Integration: Call {@link #buildSessionAndPrompt(Context, String, String)} from the
 * compile result callback, then start ChatActivity with the returned prompt as
 * EXTRA_INITIAL_PROMPT.
 */
public class AiFixSupport {

    private static final Pattern JAVA_ERROR_PATTERN =
            Pattern.compile("([A-Za-z0-9_]+\\.java):(\\d+):\\s*error:\\s*([^\\r\\n]+)");
    private static final Pattern GENERIC_JAVA_FILE_PATTERN =
            Pattern.compile("([A-Za-z0-9_]+\\.java)");
    private static final Pattern ECLIPSE_ERROR_PATTERN =
            Pattern.compile("([A-Za-z0-9_]+\\.java) \\(at line (\\d+)\\)");

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public static class FixContext {
        public final AiFixSession session;
        public final String agentPrompt;

        FixContext(AiFixSession session, String agentPrompt) {
            this.session     = session;
            this.agentPrompt = agentPrompt;
        }
    }

    /**
     * Parses the compile log, resolves the erring event, and returns a FixContext
     * ready to be sent to the AI agent's ChatActivity.
     *
     * @param context Android context
     * @param scId    Sketchware project ID
     * @param rawLog  Raw compile output string
     * @return FixContext, or null if the log could not be parsed
     */
    public static FixContext buildSessionAndPrompt(Context context, String scId, String rawLog) {
        AiFixSession session = buildSession(context, scId, rawLog);
        if (session == null) return null;

        String prompt = buildAgentPrompt(session);
        return new FixContext(session, prompt);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session building
    // ─────────────────────────────────────────────────────────────────────────

    static AiFixSession buildSession(Context context, String scId, String rawLog) {
        AiFixSession session = new AiFixSession();
        session.sessionId = UUID.randomUUID().toString();
        session.scId      = scId;
        session.rawLog    = rawLog == null ? "" : rawLog;
        session.createdAt = System.currentTimeMillis();

        ParsedError parsed = parseCompileError(rawLog);
        session.errorFileName  = parsed.fileName;
        session.errorLine      = parsed.lineNumber;
        session.errorMessage   = parsed.message;

        if (scId == null || scId.trim().isEmpty()
                || parsed.fileName.isEmpty()
                || !parsed.fileName.endsWith(".java")) {
            return session;
        }

        try {
            ProjectFileBean fileBean = findProjectFile(scId, parsed.fileName);
            if (fileBean == null) return session;

            // Generate full Java source for context
            yq sourceGenerator = new yq(context, scId);
            sourceGenerator.a(jC.c(scId), jC.b(scId), jC.a(scId));
            String fullSource = sourceGenerator.getFileSrc(
                    fileBean.getJavaName(), jC.b(scId), jC.a(scId), jC.c(scId));

            if (fullSource != null && !fullSource.trim().isEmpty()) {
                session.targetJavaSource = fullSource;
            }

            session.targetJavaName = fileBean.getJavaName();

            // Find best matching event block
            boolean viewBinding = new ProjectSettings(scId)
                    .getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, "false")
                    .equals("true");

            ArrayList<CandidateEvent> candidates = buildCandidateEvents(scId, fileBean);
            CandidateMatch best = null;

            for (CandidateEvent candidate : candidates) {
                HashMap<String, ArrayList<BlockBean>> logicMap =
                        jC.a(scId).b(fileBean.getJavaName());
                ArrayList<BlockBean> blocks = logicMap.get(candidate.logicKey);
                if ((blocks == null || blocks.isEmpty()) && candidate.alternateLogicKey != null) {
                    blocks = logicMap.get(candidate.alternateLogicKey);
                }
                if (blocks == null || blocks.isEmpty()) continue;

                String eventCode = new Fx(
                        fileBean.getActivityName(),
                        sourceGenerator.N,
                        blocks,
                        viewBinding).a();

                if (eventCode == null) continue;

                ArrayList<int[]> occurrences = findOccurrences(fullSource, eventCode);
                for (int[] occ : occurrences) {
                    int startLine = lineOfIndex(fullSource, occ[0]);
                    int endLine   = lineOfIndex(fullSource, occ[1]);
                    int score = scoreCandidate(candidate, parsed, startLine, endLine);
                    if (best == null || score > best.score) {
                        best = new CandidateMatch(candidate, score);
                    }
                }
            }

            if (best != null) {
                session.targetId        = best.event.targetId;
                session.targetEventName = best.event.eventName;
                session.targetEventText = best.event.eventText;
            }

        } catch (Exception e) {
            android.util.Log.w("AiFixSupport", "Session build partial failure", e);
        }

        return session;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Agent prompt construction
    // ─────────────────────────────────────────────────────────────────────────

    private static String buildAgentPrompt(AiFixSession session) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## Compile Error — Auto-Fix Request\n\n");
        prompt.append("A build error occurred in project **").append(session.scId).append("**.\n\n");

        prompt.append("### Error Details\n");
        prompt.append("- **File:** ").append(session.errorFileName).append("\n");
        if (session.errorLine > 0) {
            prompt.append("- **Line:** ").append(session.errorLine).append("\n");
        }
        prompt.append("- **Message:** ").append(session.errorMessage).append("\n\n");

        if (session.hasResolvedTarget()) {
            prompt.append("### Resolved Target\n");
            prompt.append("- **Activity:** ").append(session.targetJavaName).append("\n");
            prompt.append("- **Event:** ").append(session.targetEventName).append("\n");
            prompt.append("- **View ID:** ").append(session.targetId).append("\n\n");
        }

        prompt.append("### Raw Compile Log\n```\n");
        String log = session.rawLog;
        if (log.length() > 2000) log = log.substring(0, 2000) + "\n... [truncated]";
        prompt.append(log).append("\n```\n\n");

        if (session.targetJavaSource != null && !session.targetJavaSource.isEmpty()) {
            prompt.append("### Generated Java Source (first 3000 chars)\n```java\n");
            String src = session.targetJavaSource;
            if (src.length() > 3000) src = src.substring(0, 3000) + "\n... [truncated]";
            prompt.append(src).append("\n```\n\n");
        }

        prompt.append("Please analyse the error and:\n");
        prompt.append("1. Explain what is causing the compile error.\n");
        prompt.append("2. Use the available block tools to fix the problematic event logic.\n");
        prompt.append("3. Rebuild the project after applying the fix.\n");

        return prompt.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static ParsedError parseCompileError(String rawLog) {
        ParsedError result = new ParsedError();
        if (rawLog == null || rawLog.trim().isEmpty()) return result;

        Matcher m = JAVA_ERROR_PATTERN.matcher(rawLog);
        if (m.find()) {
            result.fileName   = m.group(1);
            result.lineNumber = safeInt(m.group(2));
            result.message    = m.group(3).trim();
            return result;
        }

        Matcher eclipse = ECLIPSE_ERROR_PATTERN.matcher(rawLog);
        if (eclipse.find()) {
            result.fileName   = eclipse.group(1);
            result.lineNumber = safeInt(eclipse.group(2));
            result.message    = firstErrorLine(rawLog);
            return result;
        }

        Matcher generic = GENERIC_JAVA_FILE_PATTERN.matcher(rawLog);
        if (generic.find()) result.fileName = generic.group(1);
        result.message = firstErrorLine(rawLog);
        return result;
    }

    private static String firstErrorLine(String log) {
        if (log == null) return "";
        for (String line : log.split("\\r?\\n")) {
            String lower = line.toLowerCase();
            if (lower.contains(" error") || lower.contains("error:")) return line.trim();
        }
        String[] lines = log.split("\\r?\\n");
        return lines.length > 0 ? lines[0].trim() : "";
    }

    private static int safeInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    private static ProjectFileBean findProjectFile(String scId, String javaFileName) {
        ArrayList<ProjectFileBean> all = new ArrayList<>();
        all.addAll(jC.b(scId).b());
        all.addAll(jC.b(scId).c());
        for (ProjectFileBean f : all) {
            if (javaFileName.equals(f.getJavaName())) return f;
        }
        return null;
    }

    private static ArrayList<CandidateEvent> buildCandidateEvents(
            String scId, ProjectFileBean fileBean) {
        ArrayList<CandidateEvent> list = new ArrayList<>();

        CandidateEvent init = new CandidateEvent(
                "onCreate", "initializeLogic", "initializeLogic", "onCreate_initializeLogic");
        init.alternateLogicKey = "initializeLogic_initializeLogic";
        list.add(init);

        for (EventBean ev : jC.a(scId).g(fileBean.getJavaName())) {
            list.add(new CandidateEvent(
                    ev.targetId, ev.eventName, ev.eventName,
                    ev.targetId + "_" + ev.eventName));
        }

        for (Pair<String, String> mb : jC.a(scId).i(fileBean.getJavaName())) {
            list.add(new CandidateEvent(
                    mb.first, "moreBlock", "moreBlock", mb.first + "_moreBlock"));
        }

        return list;
    }

    private static ArrayList<int[]> findOccurrences(String source, String snippet) {
        ArrayList<int[]> out = new ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            int idx = source.indexOf(snippet, start);
            if (idx < 0) break;
            out.add(new int[]{idx, idx + snippet.length()});
            start = idx + 1;
        }
        return out;
    }

    private static int lineOfIndex(String text, int idx) {
        int line = 1;
        for (int i = 0; i < idx && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static int scoreCandidate(CandidateEvent ev, ParsedError err, int start, int end) {
        int score = 0;
        if (err.lineNumber >= start && err.lineNumber <= end) {
            score += 1000;
        } else if (err.lineNumber > 0) {
            score -= Math.min(Math.abs(err.lineNumber - start), Math.abs(err.lineNumber - end));
        }
        if (err.message.contains(ev.eventName))  score += 120;
        if (err.message.contains(ev.targetId))   score += 140;
        if ("initializeLogic".equals(ev.eventName)) score += 20;
        if ("moreBlock".equals(ev.eventName))       score += 10;
        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal data classes
    // ─────────────────────────────────────────────────────────────────────────

    private static class ParsedError {
        String fileName   = "";
        int    lineNumber = -1;
        String message    = "";
    }

    private static class CandidateEvent {
        final String targetId;
        final String eventName;
        final String eventText;
        final String logicKey;
        String alternateLogicKey;

        CandidateEvent(String targetId, String eventName, String eventText, String logicKey) {
            this.targetId  = targetId;
            this.eventName = eventName;
            this.eventText = eventText;
            this.logicKey  = logicKey;
        }
    }

    private static class CandidateMatch {
        final CandidateEvent event;
        final int score;

        CandidateMatch(CandidateEvent event, int score) {
            this.event = event;
            this.score = score;
        }
    }
}
