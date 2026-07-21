package pro.sketchware.ai.tools.build;

import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.ai.models.ToolResult;

/**
 * BuildRepairTool — Autonomous Build Error Analyzer
 *
 * Pipeline order (fixes applied in this order to avoid cascading errors):
 *   STAGE 1 — AAPT/XML resource file errors
 *   STAGE 2 — Missing resources (string/color/drawable/layout)
 *   STAGE 3 — Material color attribute errors (R.attr.colorX → com.google.android.material.R.attr.colorX)
 *   STAGE 4 — Missing imports / libraries
 *   STAGE 5 — Undeclared variables / type mismatches
 *   STAGE 6 — Syntax errors (missing ;  )  }  etc.)
 *   STAGE 7 — Duplicate classes
 *   STAGE 8 — Unknown / unclassified
 *
 * Semantic deduplication: errors with the same category+symbol are grouped
 * into one repair item showing all affected file:line locations.
 */
public final class BuildRepairTool {

    private BuildRepairTool() {}

    public static class AnalyzeBuildErrorTool implements AgentTool {

        @Override public String getName() { return "analyze_build_error"; }

        @Override public String getDescription() {
            return "Analyzes the latest build failure and produces a prioritized repair plan. "
                 + "Errors are deduplicated, grouped by root cause, and ordered so that fixing "
                 + "stage-1 errors (XML/AAPT) eliminates cascading stage-4+ errors. "
                 + "Each item includes exact tool calls. Use immediately after a failed build_project.";
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
                return error("No compile log found. Run build_project first.");

            String log = readFile(logFile);
            if (log == null || log.trim().isEmpty())
                return error("Compile log is empty");

            List<RepairItem> items = buildPipeline(log, scId);

            if (items.isEmpty()) {
                String tail = log.length() > 3000 ? "…\n" + log.substring(log.length() - 3000) : log;
                return success("⚠️ No recognizable error patterns found.\n\nRaw log:\n" + tail);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# Build Repair Plan — project ").append(scId).append("\n");
            sb.append("**").append(items.size()).append(" unique error group(s) found.**\n");
            sb.append("Fix them in the order shown (AAPT/XML errors first — they cascade into Java errors).\n\n");

            for (int i = 0; i < items.size(); i++) {
                RepairItem it = items.get(i);
                sb.append("## Step ").append(i + 1)
                  .append(" [Stage ").append(it.stage).append("] — ").append(it.category).append("\n");
                if (it.locations.size() == 1) {
                    sb.append("**Location:** `").append(it.locations.get(0)).append("`\n");
                } else {
                    sb.append("**Locations (").append(it.locations.size()).append("):**\n");
                    for (String loc : it.locations) sb.append("  • `").append(loc).append("`\n");
                }
                sb.append("**Diagnosis:** ").append(it.diagnosis).append("\n");
                sb.append("**Fix:** ").append(it.suggestedFix).append("\n");
                if (it.toolCall != null)
                    sb.append("**Tool:** `").append(it.toolCall).append("`\n");
                sb.append("\n");
            }

            sb.append("---\n");
            sb.append("Apply all steps above, then call `build_project` to verify.");
            return success(sb.toString());
        }

        // ══ PIPELINE ══════════════════════════════════════════════════════════

        /**
         * Classifies every error line, semantically deduplicates them,
         * then sorts by pipeline stage so the AI fixes them in the right order.
         */
        private List<RepairItem> buildPipeline(String log, String scId) {
            String[] lines = log.split("\n");

            // Key = "stage|category|symbol" → RepairItem (accumulates locations)
            Map<String, RepairItem> grouped = new LinkedHashMap<>();

            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (!isErrorLine(t)) continue;

                RepairItem item = classify(t, scId);
                if (item == null) continue;

                String key = item.stage + "|" + item.category + "|" + item.symbol;
                if (grouped.containsKey(key)) {
                    // Merge: append location only (don't duplicate diagnosis)
                    RepairItem existing = grouped.get(key);
                    if (!item.locations.isEmpty() && existing.locations.size() < 8) {
                        String loc = item.locations.get(0);
                        if (!existing.locations.contains(loc))
                            existing.locations.add(loc);
                    }
                } else {
                    grouped.put(key, item);
                }
            }

            // Sort by stage, cap at 12 groups
            List<RepairItem> result = new ArrayList<>(grouped.values());
            result.sort((a, b) -> Integer.compare(a.stage, b.stage));
            if (result.size() > 12) result = result.subList(0, 12);
            return result;
        }

        private boolean isErrorLine(String t) {
            return t.contains("error:")
                    || t.startsWith("e: ")
                    || t.matches(".*\\.java:[0-9]+: error.*")
                    || t.matches(".*\\.kt:\\s*\\([0-9,]+\\):.*error.*")
                    || t.startsWith("FAILED:")
                    || t.startsWith("BUILD FAILED")
                    || (t.contains("aapt2") && t.contains("error"));
        }

        // ══ PATTERNS ══════════════════════════════════════════════════════════

        // Java compiler: "Foo.java:12: error: cannot find symbol"
        private static final Pattern JAVA_LOC = Pattern.compile(
                "([\\w./]+\\.java):(\\d+)");
        // "symbol:   class Foo"  or  "symbol:   variable colorPrimary"
        private static final Pattern SYMBOL_CLASS = Pattern.compile(
                "symbol:\\s+class (\\w+)|cannot find symbol.*class (\\w+)");
        private static final Pattern SYMBOL_METHOD = Pattern.compile(
                "symbol:\\s+method ([\\w<>]+)\\(|cannot find symbol.*method ([\\w<>]+)");
        private static final Pattern SYMBOL_VAR = Pattern.compile(
                "symbol:\\s+variable (\\w+)|cannot find symbol.*variable (\\w+)");
        // Material color attr: "variable colorPrimary ... location: class attr"
        private static final Pattern MATERIAL_ATTR = Pattern.compile(
                "variable (color[A-Z]\\w+)");
        // Resource not found
        private static final Pattern RES_NOT_FOUND = Pattern.compile(
                "resource (\\S+) .* not found|@\\+?id/(\\w+) is not defined"
                + "|R\\.(string|color|drawable|layout|id|attr)\\.(\\w+) cannot be resolved"
                + "|No resource found that matches.*'(.+?)'");
        // AAPT
        private static final Pattern AAPT = Pattern.compile(
                "([\\w/.-]+\\.(xml|9\\.png|png)).*error.*|aapt2.*error");
        // Missing style/attr from a library (e.g. "style/Theme.SplashScreen not found")
        private static final Pattern MISSING_STYLE = Pattern.compile(
                "resource style/([\\w.]+).*not found");
        private static final Pattern MISSING_ATTR = Pattern.compile(
                "style attribute 'attr/([\\w.]+).*not found");
        private static final Pattern MISSING_RES_LINK = Pattern.compile(
                "error: resource (style|attr)/([\\w.]+).*not found");
        // Duplicate class
        private static final Pattern DUP_CLASS = Pattern.compile(
                "duplicate class (\\S+)");
        // Incompatible types
        private static final Pattern INCOMPAT = Pattern.compile(
                "incompatible types: (\\S+) cannot be converted to (\\S+)");
        // Syntax: missing ) → often "variable declaration not allowed here"
        private static final Pattern SYNTAX_VAR_DECL = Pattern.compile(
                "variable declaration not allowed here");
        private static final Pattern SYNTAX_PAREN = Pattern.compile(
                "'\\)' expected");
        // Activity suffix used in layout (e.g. R.layout.activity_main when it should be R.layout.main)
        private static final Pattern LAYOUT_ACTIVITY = Pattern.compile(
                "R\\.layout\\.activity_(\\w+)");

        private RepairItem classify(String line, String scId) {
            String loc = extractLocation(line);
            Matcher m;

            // ── STAGE 1: AAPT / XML resource file errors ───────────────────

            // Missing style or attr from a library — detected before generic AAPT handler
            // so the diagnosis can name the required library.
            m = MISSING_STYLE.matcher(line);
            if (m.find()) {
                String style = m.group(1);
                String lib = inferLibraryForStyle(style);
                return item(1, "MISSING_LIBRARY_STYLE", style, loc,
                        "Style '" + style + "' not found — it comes from " + lib + " which is not enabled.",
                        "Enable the '" + lib + "' built-in library for this project, or add it as a local library.",
                        "add_library(sc_id=\"" + scId + "\", library=\"" + lib + "\")");
            }
            m = MISSING_ATTR.matcher(line);
            if (m.find()) {
                String attr = m.group(1);
                String lib = inferLibraryForAttr(attr);
                return item(1, "MISSING_LIBRARY_ATTR", attr, loc,
                        "Attribute '" + attr + "' not found — it comes from " + lib + " which is not enabled.",
                        "Enable the '" + lib + "' built-in library for this project.",
                        "add_library(sc_id=\"" + scId + "\", library=\"" + lib + "\")");
            }

            m = AAPT.matcher(line);
            if (m.find() || (line.contains("aapt2") && line.contains("error"))) {
                String file = m.find() ? m.group(1) : "resource file";
                return item(1, "AAPT_XML_ERROR", file, loc,
                        "AAPT2 failed to process resource file '" + file + "'.",
                        "Open the XML with read_file, fix malformed attribute/element, then write_file.",
                        "read_file(sc_id=\"" + scId + "\", path=\"...\")");
            }

            // ── STAGE 2: Missing resources ─────────────────────────────────
            m = LAYOUT_ACTIVITY.matcher(line);
            if (m.find()) {
                String name = m.group(1);
                return item(2, "WRONG_LAYOUT_NAME", "activity_" + name, loc,
                        "R.layout.activity_" + name + " used but Sketchware generates R.layout." + name + " (no 'activity_' prefix).",
                        "Replace R.layout.activity_" + name + " with R.layout." + name + " in the Java file.",
                        "patch_file(sc_id=\"" + scId + "\", path=\"...\", ...)");
            }
            m = RES_NOT_FOUND.matcher(line);
            if (m.find()) {
                String res = firstNonNull(m.group(1), m.group(2), m.group(4), m.group(5), m.group(6));
                String type = m.group(3);
                String fix, tool;
                if ("string".equals(type)) {
                    fix = "Add the missing string: add_string_resource(sc_id, name=\"" + res + "\", value=\"...\")";
                    tool = "add_string_resource(sc_id=\"" + scId + "\", name=\"" + (res != null ? res : "?") + "\", value=\"...\")";
                } else if ("color".equals(type)) {
                    fix = "Add the missing color: add_color_resource(sc_id, name=\"" + res + "\", value=\"#...\")";
                    tool = "add_color_resource(sc_id=\"" + scId + "\", name=\"" + (res != null ? res : "?") + "\", value=\"#...\")";
                } else if ("layout".equals(type)) {
                    fix = "Create the missing layout XML file using write_raw_resource_file or write_file.";
                    tool = "write_file(sc_id=\"" + scId + "\", path=\"app/src/main/res/layout/" + (res != null ? res : "?") + ".xml\", content=\"...\")";
                } else if ("id".equals(type) || (res != null && !res.isEmpty() && line.contains("@id/"))) {
                    fix = "In the XML file change @id/" + res + " to @+id/" + res + " (add the '+' to define it).";
                    tool = "patch_file(sc_id=\"" + scId + "\", path=\"...\", old=\"@id/" + (res != null ? res : "?") + "\", new=\"@+id/" + (res != null ? res : "?") + "\")";
                } else {
                    fix = "Add the missing resource. For drawables use write_raw_resource_file.";
                    tool = "write_raw_resource_file(sc_id=\"" + scId + "\", ...)";
                }
                return item(2, "RESOURCE_NOT_FOUND", res != null ? res : "?", loc,
                        "Resource '" + (res != null ? res : "?") + "' not found in R.",
                        fix, tool);
            }

            // ── STAGE 3: Material color attribute ──────────────────────────
            // Detected when: "cannot find symbol  variable colorPrimary  location: class attr"
            if (line.contains("location: class attr") || line.contains("location: class R.attr")) {
                m = SYMBOL_VAR.matcher(line);
                if (!m.find()) m = MATERIAL_ATTR.matcher(line);
                String attr = m.find() ? (m.group(1) != null ? m.group(1) : m.group(2)) : "colorXxx";
                return item(3, "MATERIAL_COLOR_ATTR", attr, loc,
                        "R.attr." + attr + " is a Material Design 3 attribute — it lives in the Material library's R, not your app's R.",
                        "Replace R.attr." + attr + " with com.google.android.material.R.attr." + attr + " everywhere in the file.",
                        "patch_file(sc_id=\"" + scId + "\", path=\"...\", "
                        + "old=\"R.attr." + attr + "\", new=\"com.google.android.material.R.attr." + attr + "\")");
            }
            // Also catch when the variable pattern matches a color attr name directly
            m = SYMBOL_VAR.matcher(line);
            if (m.find()) {
                String var = m.group(1) != null ? m.group(1) : m.group(2);
                if (var != null && var.startsWith("color") && Character.isUpperCase(var.charAt(5))) {
                    return item(3, "MATERIAL_COLOR_ATTR", var, loc,
                            "'" + var + "' is likely a Material Design color attribute (colorPrimary, colorSurface, etc.). "
                            + "These must be accessed via com.google.android.material.R.attr, not R.attr.",
                            "Replace R.attr." + var + " with com.google.android.material.R.attr." + var + ".",
                            "patch_file(sc_id=\"" + scId + "\", path=\"...\", "
                            + "old=\"R.attr." + var + "\", new=\"com.google.android.material.R.attr." + var + "\")");
                }
            }

            // ── STAGE 4: Missing imports / libraries ───────────────────────
            m = SYMBOL_CLASS.matcher(line);
            if (m.find()) {
                String cls = firstNonNull(m.group(1), m.group(2));
                if (cls != null) {
                    return item(4, "MISSING_IMPORT", cls, loc,
                            "Class '" + cls + "' not found — missing import or library dependency.",
                            "1. Use search_maven to find the library containing '" + cls + "'. "
                            + "2. If found, use download_dependency to add it. "
                            + "3. If it is a standard class (e.g. java.util.*), add the import with patch_file.",
                            "search_maven(query=\"" + cls + "\")");
                }
            }

            // ── STAGE 5: Undeclared variables / type errors ────────────────
            m = SYMBOL_METHOD.matcher(line);
            if (m.find()) {
                String method = firstNonNull(m.group(1), m.group(2));
                if (method != null) {
                    return item(5, "METHOD_NOT_FOUND", method, loc,
                            "Method '" + method + "' not found — wrong type, renamed API, or missing import.",
                            "Use read_file to see the actual type at that line, verify the correct method name, "
                            + "then patch_file to fix it.",
                            "read_file(sc_id=\"" + scId + "\", path=\"...\")");
                }
            }

            m = SYMBOL_VAR.matcher(line);
            if (m.find()) {
                String var = firstNonNull(m.group(1), m.group(2));
                if (var != null) {
                    return item(5, "UNDECLARED_VARIABLE", var, loc,
                            "Variable '" + var + "' not declared in scope.",
                            "Declare '" + var + "' before its first use. "
                            + "Use read_file_range to see the context, then patch_file to add the declaration.",
                            "read_file_range(sc_id=\"" + scId + "\", path=\"...\", start=..., end=...)");
                }
            }

            m = INCOMPAT.matcher(line);
            if (m.find()) {
                return item(5, "TYPE_MISMATCH", m.group(1) + "→" + m.group(2), loc,
                        "Type mismatch: cannot assign " + m.group(1) + " to " + m.group(2) + ".",
                        "Add an explicit cast or call the appropriate conversion method. "
                        + "Use patch_file at the indicated file:line.",
                        null);
            }

            // ── STAGE 6: Syntax errors ─────────────────────────────────────
            if (SYNTAX_VAR_DECL.matcher(line).find()) {
                return item(6, "SYNTAX_MISSING_PAREN", "if/method", loc,
                        "'variable declaration not allowed here' usually means an if() is missing its closing ')' "
                        + "so the compiler sees the next line as the condition.",
                        "Open the file at the indicated line - 1..+5, find the incomplete if(..., and add ') continue;' or ') { ... }'.",
                        "read_file_range(sc_id=\"" + scId + "\", path=\"...\", start=..., end=...)");
            }
            if (SYNTAX_PAREN.matcher(line).find()) {
                return item(6, "SYNTAX_MISSING_PAREN", ")", loc,
                        "Missing closing ')' — usually an if/method call missing the closing parenthesis.",
                        "Open the file at the indicated line, find the unclosed '(', and add the missing ')'.",
                        "read_file_range(sc_id=\"" + scId + "\", path=\"...\", start=..., end=...)");
            }
            if (line.contains(";expected") || line.contains("';' expected")) {
                return item(6, "SYNTAX_MISSING_SEMICOLON", ";", loc,
                        "Missing semicolon at the end of a statement.",
                        "Add ';' at the end of the statement on the indicated line.",
                        "read_file_range(sc_id=\"" + scId + "\", path=\"...\", start=..., end=...)");
            }
            if (line.contains("reached end of file") || line.contains("class, interface, or enum expected")
                    || line.contains("illegal character")) {
                return item(6, "SYNTAX_ERROR", "brace/char", loc,
                        "Syntax error — unmatched brace, illegal character, or premature end of file.",
                        "Open the file, check for unmatched '{' / '}' and illegal characters.",
                        "read_file_range(sc_id=\"" + scId + "\", path=\"...\", start=..., end=...)");
            }

            // ── STAGE 7: Duplicate class ───────────────────────────────────
            m = DUP_CLASS.matcher(line);
            if (m.find()) {
                String cls = m.group(1);
                return item(7, "DUPLICATE_CLASS", cls, loc,
                        "Class '" + cls + "' defined more than once — two libraries contain the same class.",
                        "Use list_libraries to find the duplicate, then remove_library or detach_local_library.",
                        "list_libraries(sc_id=\"" + scId + "\")");
            }

            // ── STAGE 8: BUILD FAILED marker / unclassified ────────────────
            if (line.startsWith("BUILD FAILED") || line.startsWith("FAILED:")) {
                return item(8, "BUILD_FAILED", "top-level", loc,
                        "Top-level BUILD FAILED marker — the real cause is in the steps above.",
                        "Fix the earlier errors first; this marker will disappear automatically.",
                        null);
            }
            if (line.length() > 15) {
                return item(8, "UNKNOWN", line.substring(0, Math.min(30, line.length())), loc,
                        "Unrecognized error pattern.",
                        "Use get_compile_logs to get the full log and inspect manually.",
                        "get_compile_logs(sc_id=\"" + scId + "\")");
            }
            return null;
        }

        // ══ HELPERS ═══════════════════════════════════════════════════════════

        private static String inferLibraryForStyle(String style) {
            if (style.startsWith("Theme.SplashScreen") || style.startsWith("Theme.SplashScreen."))
                return "core-splashscreen-1.0.1";
            if (style.contains("Material3Expressive") || style.contains("Expressive"))
                return "material3-expressive-compat-1.0";
            if (style.startsWith("Preference.") || style.startsWith("PreferenceFragment"))
                return "preference-1.2.1";
            if (style.startsWith("Theme.Material3") || style.startsWith("Widget.Material3")
                    || style.startsWith("ThemeOverlay.Material3") || style.startsWith("MaterialAlertDialog"))
                return "material-1.13.0";
            return "an appropriate library";
        }

        private static String inferLibraryForAttr(String attr) {
            if (attr.startsWith("windowSplashScreen") || attr.equals("postSplashScreenTheme"))
                return "core-splashscreen-1.0.1";
            if (attr.equals("widgetLayout") || attr.equals("switchPreferenceCompatStyle")
                    || attr.equals("preferenceStyle") || attr.equals("seekBarPreferenceStyle"))
                return "preference-1.2.1";
            if (attr.startsWith("color") || attr.startsWith("shape") || attr.startsWith("textAppearance"))
                return "material-1.13.0";
            return "an appropriate library";
        }

        private static RepairItem item(int stage, String category, String symbol,
                                       String location, String diagnosis,
                                       String fix, String toolCall) {
            RepairItem r = new RepairItem(stage, category, symbol, diagnosis, fix, toolCall);
            if (location != null && !location.isEmpty()) r.locations.add(location);
            return r;
        }

        private static String extractLocation(String line) {
            Matcher m = JAVA_LOC.matcher(line);
            if (m.find()) return m.group(1) + ":" + m.group(2);
            // AAPT-style: "path/to/file.xml:10:5: error: ..."
            Matcher am = Pattern.compile("([\\w/.-]+\\.xml):(\\d+)").matcher(line);
            if (am.find()) return am.group(1) + ":" + am.group(2);
            return "";
        }

        private static String firstNonNull(String... vals) {
            for (String v : vals) if (v != null && !v.isEmpty()) return v;
            return null;
        }

        private String readFile(File file) {
            if (!file.exists()) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
                return sb.toString();
            } catch (IOException e) { return null; }
        }

        private static ToolResult success(String o) { return ToolResult.success(null, o); }
        private static ToolResult error(String m) { return ToolResult.failure(null, m); }
    }

    // ══ DATA ══════════════════════════════════════════════════════════════════

    private static final class RepairItem {
        final int stage;
        final String category;
        final String symbol;
        final String diagnosis;
        final String suggestedFix;
        final String toolCall;
        final List<String> locations = new ArrayList<>();

        RepairItem(int stage, String category, String symbol,
                   String diagnosis, String suggestedFix, String toolCall) {
            this.stage       = stage;
            this.category    = category;
            this.symbol      = symbol;
            this.diagnosis   = diagnosis;
            this.suggestedFix = suggestedFix;
            this.toolCall    = toolCall;
        }
    }
}
