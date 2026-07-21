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

import pro.sketchware.ai.models.ToolResult;

public class CompileTools {

    public static class GetCompileLogsTool implements AgentTool {
        @Override
        public String getName() {
            return "get_compile_logs";
        }

        @Override
        public String getDescription() {
            return "Gets the last compilation logs for a project. Useful for debugging build errors. "
                    + "Common errors and fixes: "
                    + "'resource not found @id/X' -> open the XML file with read_file and change @id/ to @+id/; "
                    + "'cannot find symbol' -> check Java imports; "
                    + "'missing resource' -> create the missing drawable/string/color resource. "
                    + "For raw XML files (design.xml etc.), always use read_file + write_file, NOT describe_layout.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            scId.addProperty("description", "The project SC ID");
            properties.add("sc_id", scId);
            schema.add("properties", properties);
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            if (scId == null || scId.isEmpty()) {
                return new ToolResult("", false, null, "sc_id is required");
            }
            if (!context.isProjectAllowed(scId)) {
                return new ToolResult("", false, null, "Project " + scId + " is not in this workspace");
            }

            File logFile = new File(context.getSketchwareDir(), "data/" + scId + "/compile_log");
            if (!logFile.exists()) {
                return new ToolResult("", true, "No compile logs found for project " + scId, null);
            }

            String content = readFile(logFile);
            if (content == null) {
                return new ToolResult("", false, null, "Failed to read compile logs");
            }

            String result = extractRootCause(content);
            return new ToolResult("", true, result, null);
        }

        /**
         * Extracts actionable root-cause errors from a Sketchware build log.
         * Returns a compact report: root cause section + truncated full log.
         */
        private String extractRootCause(String log) {
            if (log == null || log.isEmpty()) return "(empty log)";

            String[] lines = log.split("\n");
            java.util.List<String> errors   = new java.util.ArrayList<>();
            java.util.List<String> warnings = new java.util.ArrayList<>();

            for (String line : lines) {
                String trimmed = line.trim();
                // Java compiler errors: "Foo.java:12: error: ..."
                // AAPT2 errors:         "error: ..."
                // Kotlin errors:        "e: .../Foo.kt: (12, 5): ..."
                if (trimmed.matches(".*\\berror:\\b.*") ||
                    trimmed.startsWith("e: ") ||
                    trimmed.matches(".*\\.java:[0-9]+: error.*") ||
                    trimmed.matches(".*\\.kt:\\s*\\([0-9]+.*\\):.*") ||
                    trimmed.startsWith("FAILED:") ||
                    trimmed.startsWith("BUILD FAILED")) {
                    errors.add(line);
                } else if (trimmed.contains("warning:") || trimmed.startsWith("w: ")) {
                    warnings.add(line);
                }
            }

            StringBuilder sb = new StringBuilder();

            if (!errors.isEmpty()) {
                sb.append("━━━ ROOT CAUSE (").append(errors.size()).append(" error(s)) ━━━\n");
                // Deduplicate and cap at 20 unique errors
                java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
                for (String e : errors) {
                    seen.add(e.trim());
                    if (seen.size() >= 20) break;
                }
                for (String e : seen) sb.append(e).append("\n");
                sb.append("\n");
            } else {
                sb.append("━━━ No explicit error lines found ━━━\n\n");
            }

            if (!warnings.isEmpty()) {
                int shown = Math.min(warnings.size(), 5);
                sb.append("━━━ First ").append(shown).append(" warning(s) (of ").append(warnings.size()).append(") ━━━\n");
                for (int i = 0; i < shown; i++) sb.append(warnings.get(i)).append("\n");
                sb.append("\n");
            }

            // Append tail of full log (most recent context)
            int maxTailChars = 6000;
            sb.append("━━━ Full log (last ").append(maxTailChars).append(" chars) ━━━\n");
            if (log.length() <= maxTailChars) {
                sb.append(log);
            } else {
                sb.append("... (truncated)\n").append(log, log.length() - maxTailChars, log.length());
            }

            return sb.toString();
        }
    }

    public static class GetProjectStructureTool implements AgentTool {
        @Override
        public String getName() {
            return "get_project_structure";
        }

        @Override
        public String getDescription() {
            return "Gets the full project structure including activities, files, resources, and libraries.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            scId.addProperty("description", "The project SC ID");
            properties.add("sc_id", scId);
            schema.add("properties", properties);
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String scId = arguments.has("sc_id") ? arguments.get("sc_id").getAsString() : null;
            if (scId == null || scId.isEmpty()) {
                return new ToolResult("", false, null, "sc_id is required");
            }
            if (!context.isProjectAllowed(scId)) {
                return new ToolResult("", false, null, "Project " + scId + " is not in this workspace");
            }

            JsonObject structure = new JsonObject();
            structure.addProperty("sc_id", scId);

            File dataDir = new File(context.getSketchwareDir(), "data/" + scId);
            File sourceDir = new File(context.getSketchwareDir(), "mysc/" + scId);

            File projectFile = new File(dataDir, "project");
            if (projectFile.exists()) {
                String content = readFile(projectFile);
                if (content != null) {
                    try {
                        structure.add("project_metadata", com.google.gson.JsonParser.parseString(content));
                    } catch (Exception ignored) {}
                }
            }

            File fileFile = new File(dataDir, "file");
            if (fileFile.exists()) {
                String content = readFile(fileFile);
                if (content != null) {
                    try {
                        structure.add("activities", com.google.gson.JsonParser.parseString(content));
                    } catch (Exception ignored) {}
                }
            }

            if (dataDir.exists()) {
                JsonArray dataFiles = new JsonArray();
                File[] files = dataDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("name", f.getName());
                        entry.addProperty("type", f.isDirectory() ? "directory" : "file");
                        entry.addProperty("size", f.length());
                        dataFiles.add(entry);
                    }
                }
                structure.add("data_files", dataFiles);
            }

            if (sourceDir.exists()) {
                JsonArray sourceFiles = listDirRecursive(sourceDir, "");
                structure.add("source_files", sourceFiles);
            }

            return new ToolResult("", true, structure.toString(), null);
        }

        private JsonArray listDirRecursive(File dir, String prefix) {
            JsonArray result = new JsonArray();
            File[] files = dir.listFiles();
            if (files == null) return result;
            for (File f : files) {
                String path = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
                JsonObject entry = new JsonObject();
                entry.addProperty("path", path);
                entry.addProperty("type", f.isDirectory() ? "directory" : "file");
                if (f.isFile()) entry.addProperty("size", f.length());
                result.add(entry);
                if (f.isDirectory()) {
                    JsonArray children = listDirRecursive(f, path);
                    for (int i = 0; i < children.size(); i++) result.add(children.get(i));
                }
            }
            return result;
        }
    }

    private static String readFile(File file) {
        if (!file.exists()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return null;
        }
    }
}
