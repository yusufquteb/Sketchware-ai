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
 * BuildRepairTool — Phase 4: Autonomous Build Repair Analyzer
 *
 * Reads the compile log, classifies each error into a known failure category,
 * and emits a structured repair plan with exact tool calls the AI should make.
 * This dramatically reduces the AI repair loop from many rounds to usually one.
 *
 * Error categories handled:
 *   MISSING_IMPORT       → "cannot find symbol class Foo" → add_library or patch_file (import)
 *   RESOURCE_NOT_FOUND   → "@id/foo not found" / "resource not found" → add_*_resource or patch_file
 *   TYPE_MISMATCH        → "incompatible types" → patch_file to cast/convert
 *   UNDECLARED_VAR       → "cannot find symbol variable" → patch_file to declare
 *   METHOD_NOT_FOUND     → "cannot find symbol method" → check library/patch_file
 *   DUPLICATE_CLASS      → "duplicate class" → remove_library or patch_file
 *   SYNTAX_ERROR         → ";expected" / "illegal char" → patch_file
 *   AAPT_RESOURCE        → aapt2 errors → fix XML/resources
 *   BUILD_CONFIG         → Gradle/classpath errors → set_build_compiler
 *   UNKNOWN              → raw line, AI must handle manually
 */
public final class BuildRepairTool {

    private BuildRepairTool() {}

    public static class AnalyzeBuildErrorTool implements AgentTool {

        @Override public String getName() { return "analyze_build_error"; }

        @Override public String getDescription() {
            return "Analyzes the latest build failure for a project and produces a structured repair plan. "
                 + "Each detected error is classified (missing import, resource not found, type mismatch, etc.) "
                 + "and paired with exact tool calls you should make to fix it. "
                 + "Use this immediately after a failed build_project call instead of get_compile_logs. "
                 + "Parameters: sc_id (required).";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            scId.addProperty("description", "Project SC ID");
            props.add("sc_id", scId);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = args.has("sc_id") ? args.get("sc_id").getAsString() : null;
            if (scId == null || scId.isEmpty()) return error("sc_id is required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            File logFile = ctx.getProjectCompileLogFile(scId);
            if (!logFile.exists())
                return error("No compile log found for project " + scId + ". Run build_project first.");

            String log = readFile(logFile);
            if (log == null || log.trim().isEmpty())
                return error("Compile log is empty");

            List<RepairItem> items = classify(log, scId);

            if (items.isEmpty()) {
                // No actionable errors found — maybe it actually succeeded or has unusual errors
                String tail = log.length() > 3000 ? "…\n" + log.substring(log.length() - 3000) : log;
                return success("⚠️ No recognizable error patterns found.\n\nRaw log tail:\n" + tail);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# Build Repair Plan: ").append(scId).append("\n");
            sb.append("Found ").append(items.size()).append(" actionable error(s).\n\n");

            for (int i = 0; i < items.size(); i++) {
                RepairItem item = items.get(i);
                sb.append("## Error ").append(i + 1).append(": ").append(item.category).append("\n");
                sb.append("**Raw:** `").append(item.rawLine).append("`\n");
                sb.append("**Diagnosis:** ").append(item.diagnosis).append("\n");
                sb.append("**Fix:** ").append(item.suggestedFix).append("\n");
                if (item.toolCall != null)
                    sb.append("**Tool call:** `").append(item.toolCall).append("`\n");
                sb.append("\n");
            }

            sb.append("---\n");
            sb.append("Apply the fixes above in order, then call build_project again to verify.");

            return success(sb.toString());
        }

        // ── Error classifier ──────────────────────────────────────────────────

        private static final Pattern JAVA_ERROR = Pattern.compile(
                "([\\w/]+\\.java):(\\d+): error: (.+)");
        private static final Pattern CANNOT_FIND_CLASS = Pattern.compile(
                "cannot find symbol.*class (\\w+)|symbol:\\s+class (\\w+)");
        private static final Pattern CANNOT_FIND_METHOD = Pattern.compile(
                "cannot find symbol.*method ([\\w<>]+)|symbol:\\s+method ([\\w<>]+)");
        private static final Pattern CANNOT_FIND_VAR = Pattern.compile(
                "cannot find symbol.*variable (\\w+)|symbol:\\s+variable (\\w+)");
        private static final Pattern RESOURCE_NOT_FOUND = Pattern.compile(
                "resource (\\S+) .* not found|@\\+?id/(\\w+) is not defined|R\\..*\\.(\\w+) cannot be resolved");
        private static final Pattern AAPT_ERROR = Pattern.compile(
                "error: (.+)\\.(xml|9\\.png).*");
        private static final Pattern DUPLICATE_CLASS = Pattern.compile(
                "duplicate class (\\S+)");
        private static final Pattern INCOMPATIBLE_TYPES = Pattern.compile(
                "incompatible types: (\\S+) cannot be converted to (\\S+)");

        private List<RepairItem> classify(String log, String scId) {
            String[] lines = log.split("\n");
            List<RepairItem> items = new ArrayList<>();
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();

            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                // Only look at error lines
                boolean isError = t.contains("error:") || t.startsWith("e: ")
                        || t.matches(".*\\.java:[0-9]+: error.*")
                        || t.startsWith("FAILED:") || t.startsWith("BUILD FAILED")
                        || t.contains("aapt2") && t.contains("error");
                if (!isError) continue;
                if (!seen.add(t)) continue; // deduplicate

                RepairItem item = tryClassify(t, scId);
                if (item != null && items.size() < 15) items.add(item);
            }
            return items;
        }

        private RepairItem tryClassify(String line, String scId) {
            Matcher m;

            // ── Cannot find symbol — class (missing import / library)
            m = CANNOT_FIND_CLASS.matcher(line);
            if (m.find()) {
                String cls = m.group(1) != null ? m.group(1) : m.group(2);
                if (cls != null) {
                    return new RepairItem(
                        "MISSING_IMPORT",
                        line,
                        "Class '" + cls + "' is not imported or its library is missing.",
                        "1. Check if the class is in an unimported standard library (use search_maven "
                            + "to find the Maven coordinate). "
                            + "2. If the library is not yet in the project, use download_dependency. "
                            + "3. If the import is just missing, use patch_file to add 'import <full.package." + cls + ";'",
                        "search_maven(query=\"" + cls + "\")"
                    );
                }
            }

            // ── Cannot find symbol — method
            m = CANNOT_FIND_METHOD.matcher(line);
            if (m.find()) {
                String method = m.group(1) != null ? m.group(1) : m.group(2);
                if (method != null) {
                    return new RepairItem(
                        "METHOD_NOT_FOUND",
                        line,
                        "Method '" + method + "' not found — wrong type, renamed, or missing import.",
                        "Check the object's actual type and the correct method name. "
                            + "Use read_file to read the relevant Java file and verify the correct method call. "
                            + "Then use patch_file to correct the call.",
                        "read_file(sc_id=\"" + scId + "\", path=\"...\")"
                    );
                }
            }

            // ── Cannot find symbol — variable
            m = CANNOT_FIND_VAR.matcher(line);
            if (m.find()) {
                String var = m.group(1) != null ? m.group(1) : m.group(2);
                if (var != null) {
                    return new RepairItem(
                        "UNDECLARED_VARIABLE",
                        line,
                        "Variable '" + var + "' is not declared in scope.",
                        "Declare the variable before its first use. "
                            + "Use read_file to find the relevant location, then patch_file to add the declaration.",
                        "read_file(sc_id=\"" + scId + "\", path=\"...\")"
                    );
                }
            }

            // ── Incompatible types
            m = INCOMPATIBLE_TYPES.matcher(line);
            if (m.find()) {
                return new RepairItem(
                    "TYPE_MISMATCH",
                    line,
                    "Type mismatch: " + m.group(1) + " vs " + m.group(2) + ".",
                    "Add an explicit cast or use the correct type. "
                        + "Use patch_file to apply the correction at the indicated file:line.",
                    null
                );
            }

            // ── Resource not found
            m = RESOURCE_NOT_FOUND.matcher(line);
            if (m.find()) {
                String res = m.group(1) != null ? m.group(1)
                           : m.group(2) != null ? m.group(2)
                           : m.group(3);
                return new RepairItem(
                    "RESOURCE_NOT_FOUND",
                    line,
                    "Resource '" + (res != null ? res : "?") + "' not found in R.",
                    "For strings: use add_string_resource. "
                        + "For colors: use add_color_resource. "
                        + "For drawables: use write_raw_resource_file. "
                        + "For view IDs in XML: change @id/ to @+id/ using patch_file.",
                    "add_string_resource(sc_id=\"" + scId + "\", name=\"...\", value=\"...\")"
                );
            }

            // ── Duplicate class
            m = DUPLICATE_CLASS.matcher(line);
            if (m.find()) {
                String cls = m.group(1);
                return new RepairItem(
                    "DUPLICATE_CLASS",
                    line,
                    "Class '" + cls + "' is defined more than once — usually two libraries contain the same class.",
                    "Use list_libraries to see enabled libraries, then remove_library to remove the duplicate. "
                        + "If the duplicate is in a local library, use detach_local_library.",
                    "list_libraries(sc_id=\"" + scId + "\")"
                );
            }

            // ── AAPT resource XML error
            m = AAPT_ERROR.matcher(line);
            if (m.find()) {
                return new RepairItem(
                    "AAPT_XML_ERROR",
                    line,
                    "AAPT2 failed to process a resource file.",
                    "Read the referenced XML file using read_file, fix the malformed attribute or element, "
                        + "then write it back with write_file.",
                    "read_file(sc_id=\"" + scId + "\", path=\"...\")"
                );
            }

            // ── Syntax errors
            if (line.contains(";expected") || line.contains("illegal character")
                    || line.contains("reached end of file")
                    || line.contains("class, interface, or enum expected")) {
                return new RepairItem(
                    "SYNTAX_ERROR",
                    line,
                    "Java syntax error — missing semicolon, brace, or other syntax problem.",
                    "Use read_file_range to read the indicated file:line, then patch_file to fix the syntax.",
                    "read_file_range(sc_id=\"" + scId + "\", path=\"...\", start=..., end=...)"
                );
            }

            // ── Generic BUILD FAILED
            if (line.startsWith("BUILD FAILED") || line.startsWith("FAILED:")) {
                return new RepairItem(
                    "BUILD_FAILED",
                    line,
                    "Top-level build failure marker — check preceding errors for root cause.",
                    "Look at the errors listed above this line. If no other errors were detected, "
                        + "use get_compile_logs to get the full raw log for manual inspection.",
                    "get_compile_logs(sc_id=\"" + scId + "\")"
                );
            }

            // ── Unclassified error
            if (line.length() > 10) {
                return new RepairItem(
                    "UNKNOWN",
                    line,
                    "Unrecognized error pattern.",
                    "Use get_compile_logs to get the full log and inspect manually.",
                    "get_compile_logs(sc_id=\"" + scId + "\")"
                );
            }

            return null;
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private static class RepairItem {
            final String category;
            final String rawLine;
            final String diagnosis;
            final String suggestedFix;
            final String toolCall;

            RepairItem(String category, String rawLine, String diagnosis,
                       String suggestedFix, String toolCall) {
                this.category    = category;
                this.rawLine     = rawLine.length() > 200 ? rawLine.substring(0, 200) + "…" : rawLine;
                this.diagnosis   = diagnosis;
                this.suggestedFix = suggestedFix;
                this.toolCall    = toolCall;
            }
        }

        private String readFile(File file) {
            if (!file.exists()) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
                return sb.toString();
            } catch (IOException e) { return null; }
        }

        private static ToolResult success(String output) { return ToolResult.success(null, output); }
        private static ToolResult error(String msg) { return ToolResult.failure(null, msg); }
    }
}
