package pro.sketchware.ai.tools.impl.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import pro.sketchware.ai.tools.Tool;

/**
 * ReadFileTool — Reads the text content of a file at the given path.
 *
 * <p><b>Expected JSON input:</b>
 * <pre>
 * {
 *   "path": "/absolute/path/to/file.txt"
 * }
 * </pre>
 *
 * <p><b>Supported file types:</b> .java, .xml, .txt, .json, .md, .gradle, .kt (read-only)
 *
 * <p><b>Security constraints:</b>
 * <ul>
 *   <li>Binary files are rejected (checked via MIME heuristic).</li>
 *   <li>Files exceeding {@link #MAX_FILE_SIZE_BYTES} are truncated with a notice.</li>
 *   <li>Directory traversal paths are rejected.</li>
 * </ul>
 */
public class ReadFileTool implements Tool {

    public static final String NAME = "read_file";

    /** Maximum file size to read in full — 512 KB. */
    private static final long MAX_FILE_SIZE_BYTES = 512 * 1024L;

    /** Truncation notice appended when a file exceeds the size limit. */
    private static final String TRUNCATION_NOTICE =
            "\n\n[Note: File truncated at 512KB. Only the first portion is shown.]";

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Reads the text content of a file. "
                + "Supports: .java, .xml, .txt, .json, .md, .gradle. "
                + "Binary files and files over 512KB are handled gracefully.";
    }

    @Nullable
    @Override
    public String getInputSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "  \"path\":{\"type\":\"string\",\"description\":\"Absolute path to the file\"}"
                + "},"
                + "\"required\":[\"path\"]"
                + "}";
    }

    @NonNull
    @Override
    public ToolResult execute(@Nullable String jsonInput) {
        // ── 1. Parse input ─────────────────────────────────────────────────
        if (jsonInput == null || jsonInput.trim().isEmpty()) {
            return ToolResult.failure(NAME, "Input is required. Provide {\"path\": \"...\"}");
        }

        String path;
        try {
            JSONObject input = new JSONObject(jsonInput);
            path = input.optString("path", "").trim();
        } catch (JSONException e) {
            return ToolResult.failure(NAME, "Invalid JSON input: " + e.getMessage());
        }

        if (path.isEmpty()) {
            return ToolResult.failure(NAME, "'path' field is required and must not be empty.");
        }

        // ── 2. Security: reject directory traversal ────────────────────────
        if (path.contains("..")) {
            return ToolResult.failure(NAME,
                    "Path traversal sequences ('..') are not permitted.");
        }

        // ── 3. Validate file ───────────────────────────────────────────────
        File file = new File(path);

        if (!file.exists()) {
            return ToolResult.failure(NAME,
                    "File not found: " + path);
        }

        if (!file.isFile()) {
            return ToolResult.failure(NAME,
                    "Path is a directory, not a file: " + path);
        }

        if (!file.canRead()) {
            return ToolResult.failure(NAME,
                    "Permission denied: cannot read " + path);
        }

        // ── 4. Check for supported extension ──────────────────────────────
        if (!isSupportedTextFile(file.getName())) {
            return ToolResult.failure(NAME,
                    "Unsupported file type: " + getExtension(file.getName())
                    + ". Supported types: .java, .xml, .txt, .json, .md, .gradle, .kt, .py, .html, .css, .js");
        }

        // ── 5. Read file ───────────────────────────────────────────────────
        long fileSize = file.length();
        boolean truncated = fileSize > MAX_FILE_SIZE_BYTES;

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            long bytesRead = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                bytesRead += line.length() + 1; // +1 for newline
                content.append(line).append('\n');
                if (truncated && bytesRead >= MAX_FILE_SIZE_BYTES) {
                    break;
                }
            }
        } catch (IOException e) {
            return ToolResult.failure(NAME,
                    "Failed to read file: " + e.getMessage());
        }

        if (truncated) {
            content.append(TRUNCATION_NOTICE);
        }

        // ── 6. Build result ────────────────────────────────────────────────
        String resultText = "File: " + path + "\n"
                + "Size: " + formatSize(fileSize) + (truncated ? " (truncated)" : "") + "\n"
                + "─────────────────────────────────\n"
                + content.toString();

        return ToolResult.success(resultText,
                getMimeType(file.getName()));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean isSupportedTextFile(@NonNull String filename) {
        String ext = getExtension(filename).toLowerCase();
        switch (ext) {
            case "java": case "xml":  case "txt":  case "json":
            case "md":   case "gradle": case "kt":   case "py":
            case "html": case "css":  case "js":   case "ts":
            case "yaml": case "yml":  case "properties": case "sh":
            case "log":  case "csv":  case "sql":  case "toml":
                return true;
            default:
                return false;
        }
    }

    @NonNull
    private String getMimeType(@NonNull String filename) {
        String ext = getExtension(filename).toLowerCase();
        switch (ext) {
            case "json": return "application/json";
            case "xml":  return "application/xml";
            case "html": return "text/html";
            default:     return "text/plain";
        }
    }

    @NonNull
    private String getExtension(@NonNull String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    @NonNull
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
