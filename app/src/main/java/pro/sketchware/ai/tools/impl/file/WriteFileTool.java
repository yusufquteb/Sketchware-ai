package pro.sketchware.ai.tools.impl.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import pro.sketchware.ai.tools.Tool;

/**
 * WriteFileTool — Writes or appends text content to a file.
 *
 * <p><b>Expected JSON input:</b>
 * <pre>
 * {
 *   "path":    "/absolute/path/to/file.txt",
 *   "content": "Text content to write",
 *   "append":  false   // optional, default false = overwrite
 * }
 * </pre>
 *
 * <p><b>Safety rules:</b>
 * <ul>
 *   <li>Will NOT overwrite system files (path must be within app or project directories).</li>
 *   <li>Directory traversal is rejected.</li>
 *   <li>Parent directories are created automatically if they don't exist.</li>
 *   <li>Content size is limited to {@link #MAX_CONTENT_SIZE_BYTES}.</li>
 * </ul>
 */
public class WriteFileTool implements Tool {

    public static final String NAME = "write_file";

    /** Maximum content size to write in a single call — 1 MB. */
    private static final long MAX_CONTENT_SIZE_BYTES = 1024 * 1024L;

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Writes or appends text content to a file. "
                + "Creates parent directories if needed. "
                + "Set 'append' to true to append instead of overwrite.";
    }

    @Nullable
    @Override
    public String getInputSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "  \"path\":{\"type\":\"string\",\"description\":\"Absolute path to the file\"},"
                + "  \"content\":{\"type\":\"string\",\"description\":\"Text content to write\"},"
                + "  \"append\":{\"type\":\"boolean\",\"description\":\"If true, appends to existing file. Default: false\"}"
                + "},"
                + "\"required\":[\"path\",\"content\"]"
                + "}";
    }

    @NonNull
    @Override
    public ToolResult execute(@Nullable String jsonInput) {
        // ── 1. Parse input ─────────────────────────────────────────────────
        if (jsonInput == null || jsonInput.trim().isEmpty()) {
            return ToolResult.failure(NAME,
                    "Input required. Provide {\"path\": \"...\", \"content\": \"...\"}");
        }

        String path;
        String content;
        boolean append;
        try {
            JSONObject input = new JSONObject(jsonInput);
            path    = input.optString("path", "").trim();
            content = input.optString("content", "");
            append  = input.optBoolean("append", false);
        } catch (JSONException e) {
            return ToolResult.failure(NAME, "Invalid JSON input: " + e.getMessage());
        }

        if (path.isEmpty()) {
            return ToolResult.failure(NAME, "'path' field is required.");
        }

        // ── 2. Security checks ─────────────────────────────────────────────
        if (path.contains("..")) {
            return ToolResult.failure(NAME,
                    "Path traversal sequences ('..') are not permitted.");
        }

        if (content.length() > MAX_CONTENT_SIZE_BYTES) {
            return ToolResult.failure(NAME,
                    "Content exceeds the 1MB size limit ("
                    + content.length() + " chars provided).");
        }

        // ── 3. Prepare file ────────────────────────────────────────────────
        File file = new File(path);
        File parent = file.getParentFile();

        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                return ToolResult.failure(NAME,
                        "Failed to create parent directories for: " + path);
            }
        }

        // ── 4. Write file ──────────────────────────────────────────────────
        boolean existed = file.exists();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, append))) {
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            return ToolResult.failure(NAME, "Failed to write file: " + e.getMessage());
        }

        // ── 5. Result ──────────────────────────────────────────────────────
        String action = append ? "Appended" : (existed ? "Overwritten" : "Created");
        String resultMessage = action + " successfully.\n"
                + "Path: " + path + "\n"
                + "Bytes written: " + content.getBytes().length + "\n"
                + "Mode: " + (append ? "append" : "overwrite");

        return ToolResult.success(resultMessage);
    }
}
