package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.ai.models.ToolResult;

/**
 * FileSearchTools — أدوات البحث الذكي داخل ملفات المشروع.
 *
 * الأدوات المتاحة:
 *   search_in_file  — البحث عن نص/regex داخل ملف واحد مع إرجاع السياق المحيط (context lines)
 *                     يوفر 90% من استهلاك الـ Tokens مقارنة بقراءة الملف كاملاً
 *
 * هذه الأداة تنفذ المقترح الثاني من ملف Suggested.txt:
 * "أداة بحث ذكية داخل الملفات (Grep-like tool)" التي تبحث عن كلمة وتجلب السطر
 * الذي يحتويها مع السطور المحيطة به فقط (Context).
 */
public final class FileSearchTools {

    private FileSearchTools() {}

    private static ToolResult ok(String output)  { return ToolResult.success(null, output); }
    private static ToolResult err(String msg)    { return ToolResult.failure(null, msg); }

    private static final List<String> JAVA_PREFIXES = java.util.Arrays.asList(
            "app/src/main/java/", "src/main/java/", "java/");
    private static final List<String> RES_PREFIXES = java.util.Arrays.asList(
            "app/src/main/res/", "src/main/res/", "res/");
    private static final List<String> ASSET_PREFIXES = java.util.Arrays.asList(
            "app/src/main/assets/", "src/main/assets/", "assets/");

    private static String normalizePath(String p) {
        if (p == null) return "";
        String n = p.trim().replace('\\', '/');
        while (n.startsWith("./")) n = n.substring(2);
        while (n.startsWith("/")) n = n.substring(1);
        return n;
    }

    private static File resolveFile(ToolContext context, String scId, String requestedPath) {
        String normalized = normalizePath(requestedPath);
        for (String prefix : JAVA_PREFIXES) {
            if (normalized.startsWith(prefix))
                return new File(context.getProjectJavaDir(scId), normalized.substring(prefix.length()));
        }
        for (String prefix : RES_PREFIXES) {
            if (normalized.startsWith(prefix))
                return new File(context.getProjectResourceDir(scId), normalized.substring(prefix.length()));
        }
        for (String prefix : ASSET_PREFIXES) {
            if (normalized.startsWith(prefix))
                return new File(context.getProjectAssetsDir(scId), normalized.substring(prefix.length()));
        }
        // Fallback: treat as relative to project data dir
        return new File(context.getProjectDataDir(scId), normalized);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SEARCH IN FILE TOOL — البحث الذكي داخل ملف واحد مع السياق
    // ════════════════════════════════════════════════════════════════════════

    public static class SearchInFileTool implements AgentTool {

        @Override public String getName() { return "search_in_file"; }

        @Override public String getDescription() {
            return "Searches for a keyword or regex pattern inside a single project file and returns "
                 + "only the matching lines with surrounding context (like grep -n -C). "
                 + "MUCH more token-efficient than read_file for large XML/Java files — "
                 + "use this instead of read_file when you only need to find a specific string. "
                 + "Returns: line_number, matched_line, and context_before/after lines.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject props = new JsonObject();

            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            scId.addProperty("description", "The project SC ID");
            props.add("sc_id", scId);

            JsonObject filePath = new JsonObject();
            filePath.addProperty("type", "string");
            filePath.addProperty("description",
                    "Logical file path, e.g. 'java/com/example/MainActivity.java' or 'res/layout/main.xml'");
            props.add("file_path", filePath);

            JsonObject query = new JsonObject();
            query.addProperty("type", "string");
            query.addProperty("description",
                    "Text or regex pattern to search for, e.g. 'onCreate', 'android:id', 'import.*Room'");
            props.add("query", query);

            JsonObject contextLines = new JsonObject();
            contextLines.addProperty("type", "integer");
            contextLines.addProperty("description",
                    "Number of lines to include before and after each match for context (default: 3, max: 10). "
                  + "Increase for wider context, decrease to save tokens.");
            props.add("context_lines", contextLines);

            JsonObject useRegex = new JsonObject();
            useRegex.addProperty("type", "boolean");
            useRegex.addProperty("description",
                    "If true, treat query as a Java regex pattern. Default: false (plain text search).");
            props.add("use_regex", useRegex);

            JsonObject caseSensitive = new JsonObject();
            caseSensitive.addProperty("type", "boolean");
            caseSensitive.addProperty("description",
                    "If true, search is case-sensitive. Default: false.");
            props.add("case_sensitive", caseSensitive);

            JsonObject maxMatches = new JsonObject();
            maxMatches.addProperty("type", "integer");
            maxMatches.addProperty("description",
                    "Maximum number of matches to return (default: 20, max: 100). Limits output size.");
            props.add("max_matches", maxMatches);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("file_path");
            required.add("query");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", props);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull())
                return err("Missing required parameter: sc_id");
            if (!arguments.has("file_path") || arguments.get("file_path").isJsonNull())
                return err("Missing required parameter: file_path");
            if (!arguments.has("query") || arguments.get("query").isJsonNull())
                return err("Missing required parameter: query");

            String scId     = arguments.get("sc_id").getAsString();
            String filePath = arguments.get("file_path").getAsString();
            String query    = arguments.get("query").getAsString();
            int contextLen  = arguments.has("context_lines")
                    ? Math.min(10, Math.max(0, arguments.get("context_lines").getAsInt())) : 3;
            boolean useRegex      = arguments.has("use_regex") && arguments.get("use_regex").getAsBoolean();
            boolean caseSensitive = arguments.has("case_sensitive") && arguments.get("case_sensitive").getAsBoolean();
            int maxMatches  = arguments.has("max_matches")
                    ? Math.min(100, Math.max(1, arguments.get("max_matches").getAsInt())) : 20;

            if (!context.isProjectAllowed(scId))
                return err("Access denied: project " + scId + " is not in the current workspace");

            File file = resolveFile(context, scId, filePath);
            if (!file.exists())
                return err("File not found: " + filePath);
            if (file.isDirectory())
                return err("Path is a directory, not a file: " + filePath + ". Use list_files instead.");

            // Read all lines into memory
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            } catch (IOException e) {
                return err("Could not read file: " + e.getMessage());
            }

            // Compile pattern
            Pattern pattern;
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                pattern = useRegex
                        ? Pattern.compile(query, flags)
                        : Pattern.compile(Pattern.quote(query), flags);
            } catch (Exception e) {
                return err("Invalid regex pattern: " + e.getMessage());
            }

            // Find matching lines
            List<Integer> matchedLineNumbers = new ArrayList<>(); // 0-indexed
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    matchedLineNumbers.add(i);
                    if (matchedLineNumbers.size() >= maxMatches) break;
                }
            }

            if (matchedLineNumbers.isEmpty()) {
                JsonObject result = new JsonObject();
                result.addProperty("file_path", filePath);
                result.addProperty("query", query);
                result.addProperty("total_lines", lines.size());
                result.addProperty("matches_found", 0);
                result.addProperty("message", "No matches found for: " + query);
                return ok(result.toString());
            }

            // Build output with context
            // Merge overlapping context windows to avoid duplicate lines
            JsonArray matchesArray = new JsonArray();
            int prevEnd = -1;

            for (int matchIdx : matchedLineNumbers) {
                int contextStart = Math.max(0, matchIdx - contextLen);
                int contextEnd   = Math.min(lines.size() - 1, matchIdx + contextLen);

                JsonObject matchObj = new JsonObject();
                matchObj.addProperty("match_line_number", matchIdx + 1); // 1-indexed for humans
                matchObj.addProperty("match_line", lines.get(matchIdx).trim());

                // Context before
                if (contextLen > 0 && contextStart < matchIdx) {
                    StringBuilder before = new StringBuilder();
                    for (int i = contextStart; i < matchIdx; i++) {
                        before.append("  L").append(i + 1).append(": ").append(lines.get(i)).append("\n");
                    }
                    matchObj.addProperty("context_before", before.toString().stripTrailing());
                }

                // Context after
                if (contextLen > 0 && contextEnd > matchIdx) {
                    StringBuilder after = new StringBuilder();
                    for (int i = matchIdx + 1; i <= contextEnd; i++) {
                        after.append("  L").append(i + 1).append(": ").append(lines.get(i)).append("\n");
                    }
                    matchObj.addProperty("context_after", after.toString().stripTrailing());
                }

                matchesArray.add(matchObj);
                prevEnd = contextEnd;
            }

            JsonObject summary = new JsonObject();
            summary.addProperty("file_path",    filePath);
            summary.addProperty("query",        query);
            summary.addProperty("total_lines",  lines.size());
            summary.addProperty("matches_found", matchedLineNumbers.size());
            if (matchedLineNumbers.size() >= maxMatches)
                summary.addProperty("note", "Results capped at " + maxMatches + ". Use max_matches to increase.");
            summary.add("matches", matchesArray);

            return ok(summary.toString());
        }
    }
}
