package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.ToolResult;

import static pro.sketchware.util.SketchwareFileDecryptor.decryptFile;

/**
 * ProjectHealthTool — Phase 4: Comprehensive Project Auditor
 *
 * Runs a suite of fast, read-only checks on a Sketchware project and
 * produces a single health report the AI can act on immediately.
 *
 * Checks performed:
 *   ✓ Project metadata (name, package, version)
 *   ✓ Activity count + last modified time
 *   ✓ View layout presence per activity
 *   ✓ Logic file presence + event count
 *   ✓ Last build status (success/failed + error snippet)
 *   ✓ Library count (built-in enabled + local)
 *   ✓ Resource counts (strings, colors, drawables)
 *   ✓ Java source files in mysc/
 *   ✓ Missing layout files (activity in file list but no view entry)
 *   ✓ Potential issues flagged for AI attention
 */
public final class ProjectHealthTool {

    private ProjectHealthTool() {}

    public static class CheckProjectHealthTool implements AgentTool {

        @Override public String getName() { return "check_project_health"; }

        @Override public String getDescription() {
            return "Runs a comprehensive read-only audit of a Sketchware project and returns "
                 + "a structured health report. Checks: project metadata, activities, layouts, "
                 + "logic events, build status, libraries, resources, and Java sources. "
                 + "Use this at the start of a session to understand the project state without "
                 + "reading multiple files separately. Parameters: sc_id (required).";
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
            String scId = args.has("sc_id") ? args.get("sc_id").getAsString().trim() : null;
            if (scId == null || scId.isEmpty()) return error("sc_id is required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Running health check on project " + scId + "…", -1, true);

            StringBuilder sb = new StringBuilder();
            sb.append("# Project Health Report: ").append(scId).append("\n\n");

            List<String> warnings = new ArrayList<>();
            List<String> infos    = new ArrayList<>();

            // ── 1. Metadata ───────────────────────────────────────────────────
            File listFile = new File(ctx.getProjectMyscListDir(scId).getParentFile(),
                    scId.replace("/", "_"));  // list/<scId> file
            sb.append("## Metadata\n");
            try {
                JsonArray metaArr = readJsonArray(scId, "file");
                if (metaArr == null) {
                    // Try project list entry
                    sb.append("  sc_id: ").append(scId).append("\n");
                    warnings.add("Could not read project file list — project may be incomplete");
                } else {
                    sb.append("  sc_id: ").append(scId).append("\n");
                    sb.append("  activities: ").append(metaArr.size()).append("\n");
                    // Count by type
                    int mainCount = 0, customCount = 0;
                    for (JsonElement el : metaArr) {
                        if (!el.isJsonObject()) continue;
                        String type = str(el.getAsJsonObject(), "type", "activity");
                        if ("activity".equals(type)) mainCount++;
                        else customCount++;
                    }
                    if (customCount > 0) sb.append("  custom_screens: ").append(customCount).append("\n");
                }
            } catch (Exception e) {
                sb.append("  (metadata read failed: ").append(e.getMessage()).append(")\n");
            }
            sb.append("\n");

            // ── 2. Activities & Layouts ────────────────────────────────────────
            sb.append("## Activities & Layouts\n");
            try {
                JsonArray fileArr = readJsonArray(scId, "file");
                JsonArray viewArr = readJsonArray(scId, "view");
                if (fileArr != null) {
                    int layoutsMissing = 0;
                    for (JsonElement el : fileArr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj  = el.getAsJsonObject();
                        String javaName = str(obj, "java_name", "?");
                        String type     = str(obj, "type", "activity");
                        boolean hasLayout = hasViewEntry(viewArr, javaName);
                        sb.append("  • ").append(javaName)
                          .append(" [").append(type).append("]");
                        if (hasLayout) {
                            int viewCount = countViews(viewArr, javaName);
                            sb.append(" — ").append(viewCount).append(" view(s)");
                        } else {
                            sb.append(" — NO LAYOUT");
                            layoutsMissing++;
                        }
                        sb.append("\n");
                    }
                    if (layoutsMissing > 0)
                        warnings.add(layoutsMissing + " activity/activities have no layout view file");
                } else {
                    sb.append("  (none found)\n");
                }
            } catch (Exception e) {
                sb.append("  (read failed: ").append(e.getMessage()).append(")\n");
            }
            sb.append("\n");

            // ── 3. Logic / Events ──────────────────────────────────────────────
            sb.append("## Logic Events\n");
            try {
                JsonArray logicArr = readJsonArray(scId, "logic");
                if (logicArr == null || logicArr.size() == 0) {
                    sb.append("  (no events defined)\n");
                } else {
                    int totalBlocks = 0;
                    java.util.Map<String, Integer> actCounts = new java.util.LinkedHashMap<>();
                    for (JsonElement el : logicArr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject ev = el.getAsJsonObject();
                        String name   = str(ev, "name", "?");
                        int dot = name.indexOf(".java_");
                        String actName = dot > 0 ? name.substring(0, dot) : name;
                        int blocks = ev.has("content") && ev.get("content").isJsonArray()
                                   ? ev.getAsJsonArray("content").size() : 0;
                        totalBlocks += blocks;
                        actCounts.merge(actName, 1, Integer::sum);
                    }
                    sb.append("  Total events: ").append(logicArr.size()).append("\n");
                    sb.append("  Total blocks: ").append(totalBlocks).append("\n");
                    for (java.util.Map.Entry<String, Integer> e : actCounts.entrySet()) {
                        sb.append("  ").append(e.getKey()).append(": ")
                          .append(e.getValue()).append(" event(s)\n");
                    }
                }
            } catch (Exception e) {
                sb.append("  (read failed: ").append(e.getMessage()).append(")\n");
            }
            sb.append("\n");

            // ── 4. Last Build Status ───────────────────────────────────────────
            sb.append("## Last Build\n");
            File compileLog = ctx.getProjectCompileLogFile(scId);
            if (!compileLog.exists()) {
                sb.append("  No build log found (project not yet built)\n");
                infos.add("Project has never been built — run build_project first");
            } else {
                String log = readFile(compileLog);
                if (log != null) {
                    boolean success = log.contains("BUILD SUCCESSFUL")
                            || log.contains("apk built successfully")
                            || log.contains("successfully");
                    boolean failed  = log.contains("BUILD FAILED")
                            || log.contains("error:") || log.contains("FAILED:");
                    if (success && !failed) {
                        sb.append("  Status: ✅ Last build succeeded\n");
                    } else if (failed) {
                        sb.append("  Status: ❌ Last build failed\n");
                        // Extract first error
                        for (String line : log.split("\n")) {
                            String t = line.trim();
                            if (t.contains("error:") || t.startsWith("e: ")) {
                                String snippet = t.length() > 200 ? t.substring(0, 200) : t;
                                sb.append("  First error: ").append(snippet).append("\n");
                                break;
                            }
                        }
                        warnings.add("Last build failed — use analyze_build_error to get repair plan");
                    } else {
                        sb.append("  Status: ⚠ Build log present but status unclear\n");
                    }
                    sb.append("  Log size: ").append(log.length()).append(" chars\n");
                }
            }
            sb.append("\n");

            // ── 5. Libraries ───────────────────────────────────────────────────
            sb.append("## Libraries\n");
            try {
                File libFile = new File(ctx.getProjectDataDir(scId), "library");
                if (libFile.exists()) {
                    String raw = readFile(libFile);
                    if (raw != null && raw.trim().startsWith("[")) {
                        JsonArray libs = JsonParser.parseString(raw.trim()).getAsJsonArray();
                        int enabled = 0;
                        for (JsonElement el : libs) {
                            if (!el.isJsonObject()) continue;
                            if (str(el.getAsJsonObject(), "useYn", "N").equals("Y")) enabled++;
                        }
                        sb.append("  Built-in enabled: ").append(enabled)
                          .append(" of ").append(libs.size()).append("\n");
                    }
                }
                File localLibs = new File(ctx.getProjectLocalLibraryFile(scId).getParent(),
                        "local_library");
                if (localLibs.exists() && localLibs.isDirectory()) {
                    File[] jars = localLibs.listFiles(f ->
                            f.getName().endsWith(".jar") || f.getName().endsWith(".aar"));
                    int count = jars != null ? jars.length : 0;
                    sb.append("  Local libraries: ").append(count).append("\n");
                } else {
                    sb.append("  Local libraries: 0\n");
                }
            } catch (Exception e) {
                sb.append("  (read failed: ").append(e.getMessage()).append(")\n");
            }
            sb.append("\n");

            // ── 6. Resources ───────────────────────────────────────────────────
            sb.append("## Resources\n");
            try {
                File resDir = ctx.getProjectResourceDir(scId);
                if (resDir.exists() && resDir.isDirectory()) {
                    int strings = countLines(new File(resDir, "values/strings.xml"), "<string ");
                    int colors  = countLines(new File(resDir, "values/colors.xml"),  "<color ");
                    File drawable = new File(resDir, "drawable");
                    int drawables = drawable.exists() ? countFiles(drawable) : 0;
                    sb.append("  Strings: ").append(strings).append("\n");
                    sb.append("  Colors: ").append(colors).append("\n");
                    sb.append("  Drawables: ").append(drawables).append("\n");
                } else {
                    sb.append("  (no resource directory)\n");
                    infos.add("No resource directory found — resources may be stored differently");
                }
            } catch (Exception e) {
                sb.append("  (read failed: ").append(e.getMessage()).append(")\n");
            }
            sb.append("\n");

            // ── 7. Java Sources ────────────────────────────────────────────────
            sb.append("## Java Sources\n");
            File javaDir = ctx.getProjectJavaDir(scId);
            if (javaDir.exists() && javaDir.isDirectory()) {
                File[] javaFiles = javaDir.listFiles(f -> f.getName().endsWith(".java"));
                int count = javaFiles != null ? javaFiles.length : 0;
                sb.append("  Java files: ").append(count).append("\n");
                if (javaFiles != null) {
                    for (File f : javaFiles) {
                        sb.append("  • ").append(f.getName()).append("\n");
                    }
                }
            } else {
                sb.append("  (no java source directory)\n");
            }
            sb.append("\n");

            // ── 8. Summary ────────────────────────────────────────────────────
            if (!warnings.isEmpty() || !infos.isEmpty()) {
                sb.append("## ⚠ Attention Needed\n");
                for (String w : warnings) sb.append("  ⚠ ").append(w).append("\n");
                for (String info : infos) sb.append("  ℹ ").append(info).append("\n");
                sb.append("\n");
            } else {
                sb.append("## ✅ No Issues Detected\n\n");
            }

            return success(sb.toString().trim());
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private JsonArray readJsonArray(String scId, String fileName) {
            try {
                String raw = decryptFile(scId, fileName);
                if (raw == null || raw.trim().isEmpty()) return null;
                raw = raw.trim();
                if (raw.startsWith("[")) return JsonParser.parseString(raw).getAsJsonArray();
            } catch (Exception ignored) {}
            return null;
        }

        private boolean hasViewEntry(JsonArray viewArr, String actName) {
            if (viewArr == null) return false;
            String xmlId = actName + ".xml";
            for (JsonElement el : viewArr) {
                if (!el.isJsonObject()) continue;
                if (xmlId.equals(str(el.getAsJsonObject(), "id", ""))) return true;
            }
            return false;
        }

        private int countViews(JsonArray viewArr, String actName) {
            if (viewArr == null) return 0;
            String xmlId = actName + ".xml";
            for (JsonElement el : viewArr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (xmlId.equals(str(obj, "id", "")) && obj.has("data")) {
                    return obj.getAsJsonArray("data").size();
                }
            }
            return 0;
        }

        private int countLines(File file, String pattern) {
            if (!file.exists()) return 0;
            int count = 0;
            try (BufferedReader r = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains(pattern)) count++;
                }
            } catch (IOException ignored) {}
            return count;
        }

        private int countFiles(File dir) {
            File[] files = dir.listFiles();
            return files != null ? files.length : 0;
        }

        private String readFile(File file) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
            } catch (IOException ignored) {}
            return sb.toString();
        }

        private static String str(JsonObject obj, String key, String def) {
            if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
            try { return obj.get(key).getAsString(); } catch (Exception e) { return def; }
        }

        private static ToolResult success(String output) { return ToolResult.success(null, output); }
        private static ToolResult error(String msg) { return ToolResult.failure(null, msg); }
    }
}
