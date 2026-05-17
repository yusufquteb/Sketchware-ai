package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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

            // Summarize repeated errors to avoid overwhelming the AI
            content = summarizeErrors(content);

            if (content.length() > 15000) {
                content = "... (truncated)\n" + content.substring(content.length() - 15000);
            }

            return new ToolResult("", true, content, null);
        }

        private String summarizeErrors(String log) {
            if (log == null || log.isEmpty()) return log;
            String[] lines = log.split("\n");
            if (lines.length < 50) return log;

            StringBuilder sb = new StringBuilder();
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            
            for (String line : lines) {
                String key = line.replaceAll(":[0-9]+:", ":line:"); // Normalize line numbers
                key = key.replaceAll("@[0-9a-f]+", "@addr"); // Normalize addresses
                counts.put(key, counts.getOrDefault(key, 0) + 1);
            }

            int totalLines = lines.length;
            int uniqueLines = counts.size();
            
            if (uniqueLines > totalLines * 0.8) return log; // Not much repetition

            sb.append("--- COMPILE LOG SUMMARY (Total lines: ").append(totalLines).append(") ---\n");
            for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > 5) {
                    sb.append("[Repeated ").append(entry.getValue()).append(" times]: ").append(entry.getKey()).append("\n");
                } else {
                    // Find original line for non-repeated or low-repeat
                    for (String original : lines) {
                        if (original.replaceAll(":[0-9]+:", ":line:").replaceAll("@[0-9a-f]+", "@addr").equals(entry.getKey())) {
                            sb.append(original).append("\n");
                            break;
                        }
                    }
                }
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
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
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
