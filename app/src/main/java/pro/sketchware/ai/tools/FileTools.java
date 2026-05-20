package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pro.sketchware.ai.models.ToolResult;

/**
 * File tools operate on the editable Sketchware source roots under .sketchware/data/{sc_id}/files.
 * Common Android Studio style aliases such as app/src/main/java and app/src/main/res are normalized
 * automatically so the agent does not accidentally mutate generated build output under .sketchware/mysc.
 */
public final class FileTools {

    private FileTools() {
    }

    private static final List<String> JAVA_PREFIXES = Arrays.asList(
            "app/src/main/java/", "src/main/java/", "java/");
    private static final List<String> RES_PREFIXES = Arrays.asList(
            "app/src/main/res/", "src/main/res/", "res/");
    private static final List<String> ASSET_PREFIXES = Arrays.asList(
            "app/src/main/assets/", "src/main/assets/", "assets/");
    private static final List<String> GENERATED_PREFIXES = Arrays.asList(
            "app/", "bin/", "gen/");

    private static ToolResult success(String output) {
        return ToolResult.success(null, output);
    }

    private static ToolResult error(String message) {
        return ToolResult.failure(null, message);
    }

    private static final class ResolvedPath {
        final File file;
        final String logicalPath;
        final String root;
        final boolean editable;

        ResolvedPath(File file, String logicalPath, String root, boolean editable) {
            this.file = file;
            this.logicalPath = logicalPath;
            this.root = root;
            this.editable = editable;
        }
    }

    private static String normalizePath(String relativePath) {
        if (relativePath == null) {
            return "";
        }
        String normalized = relativePath.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static boolean isPathSafe(String relativePath) {
        String normalized = normalizePath(relativePath);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static String stripAnyPrefix(String normalized, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (normalized.equals(prefix.substring(0, prefix.length() - 1))) {
                return "";
            }
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length());
            }
        }
        return null;
    }

    private static ResolvedPath resolveEditablePath(ToolContext context, String scId, String requestedPath) {
        String normalized = normalizePath(requestedPath);
        if (!isPathSafe(normalized)) {
            return null;
        }

        String relative = stripAnyPrefix(normalized, JAVA_PREFIXES);
        if (relative != null) {
            return new ResolvedPath(new File(context.getProjectJavaDir(scId), relative),
                    relative.isEmpty() ? "java" : "java/" + relative, "java", true);
        }

        relative = stripAnyPrefix(normalized, RES_PREFIXES);
        if (relative != null) {
            return new ResolvedPath(new File(context.getProjectResourceDir(scId), relative),
                    relative.isEmpty() ? "res" : "res/" + relative, "res", true);
        }

        relative = stripAnyPrefix(normalized, ASSET_PREFIXES);
        if (relative != null) {
            return new ResolvedPath(new File(context.getProjectAssetsDir(scId), relative),
                    relative.isEmpty() ? "assets" : "assets/" + relative, "assets", true);
        }

        File dataDir = context.getProjectDataDir(scId);
        if ("project".equals(normalized) || "file".equals(normalized) || "library".equals(normalized)
                || "resource".equals(normalized) || "view".equals(normalized)
                || "local_library".equals(normalized) || "compile_log".equals(normalized)
                || "proguard-rules.pro".equals(normalized)) {
            return new ResolvedPath(new File(dataDir, normalized), normalized, "project-data", true);
        }

        return null;
    }

    private static ResolvedPath resolveReadablePath(ToolContext context, String scId, String requestedPath) {
        String normalized = normalizePath(requestedPath);
        if (normalized.isEmpty()) {
            return new ResolvedPath(context.getProjectDataDir(scId), "", "project-roots", true);
        }
        if (!isPathSafe(normalized)) {
            return null;
        }

        ResolvedPath editable = resolveEditablePath(context, scId, normalized);
        if (editable != null) {
            return editable;
        }

        for (String prefix : GENERATED_PREFIXES) {
            if (normalized.equals(prefix.substring(0, prefix.length() - 1)) || normalized.startsWith(prefix)) {
                return new ResolvedPath(new File(context.getProjectMyscDir(scId), normalized), normalized,
                        "generated", false);
            }
        }

        if (normalized.startsWith("mysc/")) {
            String relative = normalized.substring("mysc/".length());
            return new ResolvedPath(new File(context.getProjectMyscDir(scId), relative), relative,
                    "generated", false);
        }

        return new ResolvedPath(new File(context.getProjectDataDir(scId), normalized), normalized,
                "project-data", false);
    }

    private static String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        }
        return sb.toString();
    }

    private static void writeFileContent(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    private static JsonObject buildFileInfo(File file, String logicalPath) {
        JsonObject result = new JsonObject();
        result.addProperty("file_path", logicalPath);
        result.addProperty("name", file.getName());
        result.addProperty("type", file.isDirectory() ? "directory" : "file");
        if (file.isFile()) {
            result.addProperty("size", file.length());
        }
        return result;
    }

    private static String summarizeRoots() {
        JsonArray roots = new JsonArray();
        for (String root : new String[]{"java", "res", "assets", "project", "file", "library", "local_library", "compile_log"}) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", root);
            entry.addProperty("type", "root");
            roots.add(entry);
        }
        return roots.toString();
    }

    public static class ReadFileTool implements AgentTool {

        @Override
        public String getName() {
            return "read_file";
        }

        @Override
        public String getDescription() {
            return "Reads an editable project file. Use logical roots like java/, res/, and assets/. "
                    + "Android Studio style aliases such as app/src/main/java/... are normalized automatically.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject pathProp = new JsonObject();
            pathProp.addProperty("type", "string");
            pathProp.addProperty("description", "Logical file path such as java/com/example/MainActivity.java or res/layout/main.xml");
            properties.add("file_path", pathProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("file_path");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("file_path") || arguments.get("file_path").isJsonNull()) {
                return error("Missing required parameter: file_path");
            }

            String scId = arguments.get("sc_id").getAsString();
            String filePath = arguments.get("file_path").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            ResolvedPath resolved = resolveReadablePath(context, scId, filePath);
            if (resolved == null) {
                return error("Invalid file path: path must be relative and cannot contain '..'");
            }

            if (!resolved.file.exists()) {
                return error("File not found: " + filePath);
            }

            if (resolved.file.isDirectory()) {
                return error("Path is a directory, not a file: " + filePath);
            }

            try {
                String content = readFileContent(resolved.file);
                // Return RAW content directly to avoid AI agent failing on large JSON metadata
                // This makes the agent see the file content immediately as text.
                return success(content);
            } catch (IOException e) {
                return error("Failed to read file: " + e.getMessage());
            }
        }
    }

    public static class WriteFileTool implements AgentTool {

        @Override
        public String getName() {
            return "write_file";
        }

        @Override
        public String getDescription() {
            return "Writes content to an editable Sketchware project source file. Use java/, res/, or assets/ paths. "
                    + "Generated build output under .sketchware/mysc is never written directly.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject pathProp = new JsonObject();
            pathProp.addProperty("type", "string");
            pathProp.addProperty("description", "Logical file path relative to java/, res/, assets/, or a supported project metadata file");
            properties.add("file_path", pathProp);

            JsonObject contentProp = new JsonObject();
            contentProp.addProperty("type", "string");
            contentProp.addProperty("description", "The content to write to the file");
            properties.add("content", contentProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("file_path");
            required.add("content");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("file_path") || arguments.get("file_path").isJsonNull()) {
                return error("Missing required parameter: file_path");
            }
            if (!arguments.has("content") || arguments.get("content").isJsonNull()) {
                return error("Missing required parameter: content");
            }

            String scId = arguments.get("sc_id").getAsString();
            String filePath = arguments.get("file_path").getAsString();
            String content = arguments.get("content").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            ResolvedPath resolved = resolveEditablePath(context, scId, filePath);
            if (resolved == null) {
                return error("Unsupported editable path. Use java/, res/, assets/, or supported project metadata files instead of generated mysc output.");
            }

            try {
                writeFileContent(resolved.file, content);
                // Return simple raw success message
                return success("SUCCESS: File '" + filePath + "' written (" + content.length() + " bytes)");
            } catch (IOException e) {
                return error("Failed to write file: " + e.getMessage());
            }
        }
    }

    public static class DeleteFileTool implements AgentTool {

        @Override
        public String getName() {
            return "delete_file";
        }

        @Override
        public String getDescription() {
            return "Deletes an editable project file or directory from java/, res/, assets/, or supported project metadata paths.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject pathProp = new JsonObject();
            pathProp.addProperty("type", "string");
            pathProp.addProperty("description", "Logical file path relative to the editable project roots");
            properties.add("file_path", pathProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("file_path");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("file_path") || arguments.get("file_path").isJsonNull()) {
                return error("Missing required parameter: file_path");
            }

            String scId = arguments.get("sc_id").getAsString();
            String filePath = arguments.get("file_path").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            ResolvedPath resolved = resolveEditablePath(context, scId, filePath);
            if (resolved == null) {
                return error("Unsupported editable path. Delete files only from java/, res/, assets/, or supported project metadata files.");
            }
            if (!resolved.file.exists()) {
                return error("File not found: " + filePath);
            }

            boolean deleted = resolved.file.isDirectory() ? deleteRecursive(resolved.file) : resolved.file.delete();
            if (!deleted) {
                return error("Failed to delete file: " + filePath);
            }

            JsonObject result = new JsonObject();
            result.addProperty("file_path", resolved.logicalPath);
            result.addProperty("message", "File deleted successfully");
            return success(result.toString());
        }
    }

    public static class ListFilesTool implements AgentTool {

        @Override
        public String getName() {
            return "list_files";
        }

        @Override
        public String getDescription() {
            return "Lists files and directories from the editable project roots. "
                    + "Use an empty directory parameter to discover the logical roots exposed to the agent.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject dirProp = new JsonObject();
            dirProp.addProperty("type", "string");
            dirProp.addProperty("description", "Directory path relative to the logical roots. Leave empty to list top-level roots.");
            properties.add("directory", dirProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }

            String scId = arguments.get("sc_id").getAsString();
            String directory = arguments.has("directory") && !arguments.get("directory").isJsonNull()
                    ? arguments.get("directory").getAsString() : "";

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            if (normalizePath(directory).isEmpty()) {
                return success(summarizeRoots());
            }

            ResolvedPath resolved = resolveReadablePath(context, scId, directory);
            if (resolved == null) {
                return error("Invalid directory path: path must be relative and cannot contain '..'");
            }
            if (!resolved.file.exists()) {
                return error("Directory not found: " + directory);
            }
            if (!resolved.file.isDirectory()) {
                return error("Path is not a directory: " + directory);
            }

            JsonArray entries = new JsonArray();
            File[] files = resolved.file.listFiles();
            if (files != null) {
                for (File file : files) {
                    String logicalBase = resolved.logicalPath.isEmpty() ? "" : resolved.logicalPath + "/";
                    String logicalPath = logicalBase + file.getName();
                    JsonObject entry = buildFileInfo(file, logicalPath);
                    entry.addProperty("editable", resolved.editable);
                    entries.add(entry);
                }
            }

            return success(entries.toString());
        }
    }

    public static class CopyFileTool implements AgentTool {

        @Override
        public String getName() {
            return "copy_file";
        }

        @Override
        public String getDescription() {
            return "Copies a file within or between projects using the editable logical roots.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject srcScIdProp = new JsonObject();
            srcScIdProp.addProperty("type", "string");
            srcScIdProp.addProperty("description", "SC ID of the source project");
            properties.add("source_sc_id", srcScIdProp);

            JsonObject srcPathProp = new JsonObject();
            srcPathProp.addProperty("type", "string");
            srcPathProp.addProperty("description", "Source file path in a logical project root");
            properties.add("source_path", srcPathProp);

            JsonObject tgtScIdProp = new JsonObject();
            tgtScIdProp.addProperty("type", "string");
            tgtScIdProp.addProperty("description", "SC ID of the target project");
            properties.add("target_sc_id", tgtScIdProp);

            JsonObject tgtPathProp = new JsonObject();
            tgtPathProp.addProperty("type", "string");
            tgtPathProp.addProperty("description", "Target file path in a logical project root");
            properties.add("target_path", tgtPathProp);

            JsonArray required = new JsonArray();
            required.add("source_sc_id");
            required.add("source_path");
            required.add("target_sc_id");
            required.add("target_path");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("source_sc_id") || arguments.get("source_sc_id").isJsonNull()) {
                return error("Missing required parameter: source_sc_id");
            }
            if (!arguments.has("source_path") || arguments.get("source_path").isJsonNull()) {
                return error("Missing required parameter: source_path");
            }
            if (!arguments.has("target_sc_id") || arguments.get("target_sc_id").isJsonNull()) {
                return error("Missing required parameter: target_sc_id");
            }
            if (!arguments.has("target_path") || arguments.get("target_path").isJsonNull()) {
                return error("Missing required parameter: target_path");
            }

            String sourceScId = arguments.get("source_sc_id").getAsString();
            String sourcePath = arguments.get("source_path").getAsString();
            String targetScId = arguments.get("target_sc_id").getAsString();
            String targetPath = arguments.get("target_path").getAsString();

            if (!context.isProjectAllowed(sourceScId)) {
                return error("Access denied: source project " + sourceScId + " is not in the current workspace");
            }
            if (!context.isProjectAllowed(targetScId)) {
                return error("Access denied: target project " + targetScId + " is not in the current workspace");
            }

            ResolvedPath sourceResolved = resolveReadablePath(context, sourceScId, sourcePath);
            ResolvedPath targetResolved = resolveEditablePath(context, targetScId, targetPath);
            if (sourceResolved == null || targetResolved == null) {
                return error("Unsupported source or target path. Copy using the logical project roots only.");
            }
            if (!sourceResolved.file.exists() || sourceResolved.file.isDirectory()) {
                return error("Source file not found: " + sourcePath);
            }

            try {
                copyFile(sourceResolved.file, targetResolved.file);
                JsonObject result = new JsonObject();
                result.addProperty("source", sourceScId + ":" + sourceResolved.logicalPath);
                result.addProperty("target", targetScId + ":" + targetResolved.logicalPath);
                result.addProperty("message", "File copied successfully");
                return success(result.toString());
            } catch (IOException e) {
                return error("Failed to copy file: " + e.getMessage());
            }
        }
    }

    public static class MoveFileTool implements AgentTool {

        @Override
        public String getName() {
            return "move_file";
        }

        @Override
        public String getDescription() {
            return "Moves or renames an editable project file within a logical project root.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject properties = new JsonObject();

            JsonObject scIdProp = new JsonObject();
            scIdProp.addProperty("type", "string");
            scIdProp.addProperty("description", "The project SC ID");
            properties.add("sc_id", scIdProp);

            JsonObject srcPathProp = new JsonObject();
            srcPathProp.addProperty("type", "string");
            srcPathProp.addProperty("description", "Current file path in a logical project root");
            properties.add("source_path", srcPathProp);

            JsonObject tgtPathProp = new JsonObject();
            tgtPathProp.addProperty("type", "string");
            tgtPathProp.addProperty("description", "New file path in a logical project root");
            properties.add("target_path", tgtPathProp);

            JsonArray required = new JsonArray();
            required.add("sc_id");
            required.add("source_path");
            required.add("target_path");

            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return error("Missing required parameter: sc_id");
            }
            if (!arguments.has("source_path") || arguments.get("source_path").isJsonNull()) {
                return error("Missing required parameter: source_path");
            }
            if (!arguments.has("target_path") || arguments.get("target_path").isJsonNull()) {
                return error("Missing required parameter: target_path");
            }

            String scId = arguments.get("sc_id").getAsString();
            String sourcePath = arguments.get("source_path").getAsString();
            String targetPath = arguments.get("target_path").getAsString();

            if (!context.isProjectAllowed(scId)) {
                return error("Access denied: project " + scId + " is not in the current workspace");
            }

            ResolvedPath sourceResolved = resolveEditablePath(context, scId, sourcePath);
            ResolvedPath targetResolved = resolveEditablePath(context, scId, targetPath);
            if (sourceResolved == null || targetResolved == null) {
                return error("Unsupported source or target path. Move files only within the logical project roots.");
            }
            if (!sourceResolved.file.exists()) {
                return error("Source file not found: " + sourcePath);
            }

            File targetParent = targetResolved.file.getParentFile();
            if (targetParent != null && !targetParent.exists()) {
                targetParent.mkdirs();
            }

            boolean moved = sourceResolved.file.renameTo(targetResolved.file);
            if (!moved) {
                try {
                    copyFile(sourceResolved.file, targetResolved.file);
                    moved = sourceResolved.file.delete();
                    if (!moved) {
                        targetResolved.file.delete();
                        return error("Failed to remove source file after copying");
                    }
                } catch (IOException e) {
                    return error("Failed to move file: " + e.getMessage());
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("source_path", sourceResolved.logicalPath);
            result.addProperty("target_path", targetResolved.logicalPath);
            result.addProperty("message", "File moved successfully");
            return success(result.toString());
        }
    }
    
    
    
    

    public static class GlobalSearchTool implements AgentTool {
        @Override public String getName() { return "global_search"; }
        @Override public String getDescription() { return "Search for a keyword across project files. Pass sc_id to restrict to one project (Project scope), or omit sc_id to search all allowed workspace projects (Global scope)."; }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject q = new JsonObject(); q.addProperty("type", "string");
            q.addProperty("description", "Keyword or regex to search for");
            p.add("query", q);
            JsonObject sc = new JsonObject(); sc.addProperty("type", "string");
            sc.addProperty("description", "Optional: restrict search to this project sc_id. Omit for workspace-wide search.");
            p.add("sc_id", sc);
            JsonObject schema = new JsonObject(); schema.addProperty("type", "object");
            schema.add("properties", p);
            // sc_id is optional — only query is required
            JsonArray req = new JsonArray(); req.add("query");
            schema.add("required", req);
            return schema;
        }
        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            // sc_id is now OPTIONAL:
            // - present  → search only that project (Project scope)
            // - absent   → search all allowed workspace projects (Global scope)
            String scId = (arguments.has("sc_id") && !arguments.get("sc_id").isJsonNull())
                    ? arguments.get("sc_id").getAsString().trim() : null;

            if (!arguments.has("query") || arguments.get("query").isJsonNull()) {
                return error("Missing required parameter: query");
            }
            String query = arguments.get("query").getAsString();
            boolean caseSensitive = arguments.has("case_sensitive") && arguments.get("case_sensitive").getAsBoolean();
            boolean useRegex      = arguments.has("use_regex")      && arguments.get("use_regex").getAsBoolean();
            pro.sketchware.util.ProjectSearchUtil.FileFilter fileFilter = arguments.has("file_filter")
                    ? pro.sketchware.util.ProjectSearchUtil.FileFilter.valueOf(arguments.get("file_filter").getAsString())
                    : pro.sketchware.util.ProjectSearchUtil.FileFilter.ALL;

            // Determine which projects to search
            List<String> targets = new java.util.ArrayList<>();
            if (scId != null && !scId.isEmpty()) {
                // Project scope — single project
                if (!context.isProjectAllowed(scId)) {
                    return error("Access denied: project " + scId + " is not in the current workspace");
                }
                targets.add(scId);
            } else {
                // Global scope — all allowed workspace projects
                targets.addAll(context.getAllowedProjectIds());
                if (targets.isEmpty()) return error("No projects in workspace to search");
            }

            try {
                com.google.gson.JsonArray resultArray = new com.google.gson.JsonArray();
                for (String pid : targets) {
                    List<pro.sketchware.util.ProjectSearchUtil.SearchResult> found =
                        pro.sketchware.util.ProjectSearchUtil.globalSearch(
                            new java.io.File(context.getProjectDataDir(pid), ""),
                            query, caseSensitive, useRegex, fileFilter);
                    for (pro.sketchware.util.ProjectSearchUtil.SearchResult r : found) {
                        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                        obj.addProperty("sc_id",        pid);
                        obj.addProperty("file_path",    r.filePath);
                        obj.addProperty("line_number",  r.lineNumber);
                        obj.addProperty("line_content", r.lineContent);
                        obj.addProperty("editable",     r.editable);
                        resultArray.add(obj);
                    }
                }
                // Summary wrapper
                com.google.gson.JsonObject summary = new com.google.gson.JsonObject();
                summary.addProperty("query",         query);
                summary.addProperty("scope",         scId != null ? "project:" + scId : "workspace");
                summary.addProperty("total_matches", resultArray.size());
                summary.add("results",               resultArray);
                return success(summary.toString());

            } catch (Exception e) {
                return error("Search failed: " + e.getMessage());
            }
        }
    }

    public static class GetRecentLogsTool implements AgentTool {
        @Override public String getName() { return "get_recent_logs"; }
        @Override public String getDescription() { return "Get recent logcat entries for debugging."; }
        @Override public JsonObject getParametersSchema() { return new JsonObject(); }
        @Override
        public ToolResult execute(JsonObject args, ToolContext context) {
            try {
                return success(pro.sketchware.util.LogcatManager.getRecentLogs(null));
            } catch (Exception e) { return error(e.getMessage()); }
        }
    }

    public static class PatchFileTool implements AgentTool {
        @Override public String getName() { return "patch_file"; }
        @Override public String getDescription() { return "Replace a specific text block in a file. Uses surgical mutation to save tokens."; }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type", "string"); p.add("sc_id", sc);
            JsonObject path = new JsonObject(); path.addProperty("type", "string"); p.add("file_path", path);
            JsonObject find = new JsonObject(); find.addProperty("type", "string"); p.add("search_string", find);
            JsonObject replace = new JsonObject(); replace.addProperty("type", "string"); p.add("replace_string", replace);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("file_path"); req.add("search_string"); req.add("replace_string");
            JsonObject s = new JsonObject(); s.addProperty("type", "object"); s.add("properties", p); s.add("required", req);
            return s;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext context) {
            String scId = args.get("sc_id").getAsString();
            String path = args.get("file_path").getAsString();
            String find = args.get("search_string").getAsString();
            String replace = args.get("replace_string").getAsString();
            if (!context.isProjectAllowed(scId)) return error("Access denied");
            ResolvedPath resolved = resolveEditablePath(context, scId, path);
            if (resolved == null || !resolved.file.exists()) return error("File not found");
            try {
                boolean changed = pro.sketchware.util.ProjectExecutor.patchFile(resolved.file, find, replace);
                return success(changed ? "File patched successfully" : "Search string not found, no changes made");
            } catch (IOException e) { return error("Patch failed: " + e.getMessage()); }
        }
    }

    public static class AppendCodeTool implements AgentTool {
        @Override public String getName() { return "append_code"; }
        @Override public String getDescription() { return "Append code to the end of a file."; }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type", "string"); p.add("sc_id", sc);
            JsonObject path = new JsonObject(); path.addProperty("type", "string"); p.add("file_path", path);
            JsonObject content = new JsonObject(); content.addProperty("type", "string"); p.add("content", content);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("file_path"); req.add("content");
            JsonObject s = new JsonObject(); s.addProperty("type", "object"); s.add("properties", p); s.add("required", req);
            return s;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext context) {
            String scId = args.get("sc_id").getAsString();
            String path = args.get("file_path").getAsString();
            String content = args.get("content").getAsString();
            if (!context.isProjectAllowed(scId)) return error("Access denied");
            ResolvedPath resolved = resolveEditablePath(context, scId, path);
            if (resolved == null || !resolved.file.exists()) return error("File not found");
            try {
                pro.sketchware.util.ProjectExecutor.appendCode(resolved.file, content);
                return success("Code appended successfully");
            } catch (IOException e) { return error("Append failed: " + e.getMessage()); }
        }
    }

    public static class InsertCodeAtLineTool implements AgentTool {
        @Override public String getName() { return "insert_code_at_line"; }
        @Override public String getDescription() { return "Insert code at a specific line number."; }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type", "string"); p.add("sc_id", sc);
            JsonObject path = new JsonObject(); path.addProperty("type", "string"); p.add("file_path", path);
            JsonObject line = new JsonObject(); line.addProperty("type", "integer"); p.add("line_number", line);
            JsonObject content = new JsonObject(); content.addProperty("type", "string"); p.add("content", content);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("file_path"); req.add("line_number"); req.add("content");
            JsonObject s = new JsonObject(); s.addProperty("type", "object"); s.add("properties", p); s.add("required", req);
            return s;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext context) {
            String scId = args.get("sc_id").getAsString();
            String path = args.get("file_path").getAsString();
            int line = args.get("line_number").getAsInt();
            String content = args.get("content").getAsString();
            if (!context.isProjectAllowed(scId)) return error("Access denied");
            ResolvedPath resolved = resolveEditablePath(context, scId, path);
            if (resolved == null || !resolved.file.exists()) return error("File not found");
            try {
                pro.sketchware.util.ProjectExecutor.insertCodeAtLine(resolved.file, line, content);
                return success("Code inserted successfully at line " + line);
            } catch (IOException e) { return error("Insert failed: " + e.getMessage()); }
        }
    }

    public static class ReadFileRangeTool implements AgentTool {
        @Override public String getName() { return "read_file_range"; }
        @Override public String getDescription() { return "Read a specific range of lines from a file to save tokens (Headless Analysis)."; }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type", "string"); p.add("sc_id", sc);
            JsonObject path = new JsonObject(); path.addProperty("type", "string"); p.add("file_path", path);
            JsonObject start = new JsonObject(); start.addProperty("type", "integer"); p.add("start_line", start);
            JsonObject end = new JsonObject(); end.addProperty("type", "integer"); p.add("end_line", end);
            JsonArray req = new JsonArray(); req.add("sc_id"); req.add("file_path"); req.add("start_line"); req.add("end_line");
            JsonObject s = new JsonObject(); s.addProperty("type", "object"); s.add("properties", p); s.add("required", req);
            return s;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext context) {
            String scId = args.get("sc_id").getAsString();
            String path = args.get("file_path").getAsString();
            int start = args.get("start_line").getAsInt();
            int end = args.get("end_line").getAsInt();
            if (!context.isProjectAllowed(scId)) return error("Access denied");
            ResolvedPath resolved = resolveReadablePath(context, scId, path);
            if (resolved == null || !resolved.file.exists()) return error("File not found");
            try {
                String content = pro.sketchware.util.ProjectExecutor.readFileRange(resolved.file, start, end);
                return success(content);
            } catch (IOException e) { return error("Read failed: " + e.getMessage()); }
        }
    }
}
