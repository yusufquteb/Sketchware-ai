package pro.sketchware.ai.tools.impl.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import pro.sketchware.ai.tools.Tool;

/**
 * DeleteFileTool — Deletes a file (or empty directory) at the given path.
 *
 * <p><b>Expected JSON input:</b>
 * <pre>
 * {
 *   "path":    "/absolute/path/to/file.txt",
 *   "confirm": true    // must be explicitly true to prevent accidental deletion
 * }
 * </pre>
 *
 * <p><b>Safety rules:</b>
 * <ul>
 *   <li>{@code "confirm": true} is mandatory — no silent deletions.</li>
 *   <li>Directory traversal is rejected.</li>
 *   <li>Directories are only deleted if empty.</li>
 * </ul>
 */
public class DeleteFileTool implements Tool {

    public static final String NAME = "delete_file";

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Deletes a file at the given path. "
                + "Requires 'confirm: true' to prevent accidental deletion. "
                + "Directories are only deleted if empty.";
    }

    @Nullable
    @Override
    public String getInputSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "  \"path\":{\"type\":\"string\",\"description\":\"Absolute path to the file\"},"
                + "  \"confirm\":{\"type\":\"boolean\",\"description\":\"Must be true to confirm deletion\"}"
                + "},"
                + "\"required\":[\"path\",\"confirm\"]"
                + "}";
    }

    @NonNull
    @Override
    public ToolResult execute(@Nullable String jsonInput) {
        // ── 1. Parse input ─────────────────────────────────────────────────
        if (jsonInput == null || jsonInput.trim().isEmpty()) {
            return ToolResult.failure(NAME,
                    "Input required. Provide {\"path\": \"...\", \"confirm\": true}");
        }

        String path;
        boolean confirm;
        try {
            JSONObject input = new JSONObject(jsonInput);
            path    = input.optString("path", "").trim();
            confirm = input.optBoolean("confirm", false);
        } catch (JSONException e) {
            return ToolResult.failure(NAME, "Invalid JSON input: " + e.getMessage());
        }

        if (path.isEmpty()) {
            return ToolResult.failure(NAME, "'path' field is required.");
        }

        // ── 2. Confirm gate ────────────────────────────────────────────────
        if (!confirm) {
            return ToolResult.failure(NAME,
                    "Deletion requires explicit confirmation. "
                    + "Set 'confirm' to true in the input to proceed.");
        }

        // ── 3. Security checks ─────────────────────────────────────────────
        if (path.contains("..")) {
            return ToolResult.failure(NAME,
                    "Path traversal sequences ('..') are not permitted.");
        }

        // ── 4. Validate target ─────────────────────────────────────────────
        File file = new File(path);

        if (!file.exists()) {
            return ToolResult.failure(NAME, "File not found: " + path);
        }

        if (file.isDirectory()) {
            String[] children = file.list();
            if (children != null && children.length > 0) {
                return ToolResult.failure(NAME,
                        "Cannot delete non-empty directory. "
                        + "It contains " + children.length + " item(s).");
            }
        }

        // ── 5. Delete ──────────────────────────────────────────────────────
        boolean deleted = file.delete();

        if (!deleted) {
            return ToolResult.failure(NAME,
                    "Failed to delete: " + path
                    + ". The file may be locked or you lack permission.");
        }

        return ToolResult.success(
                "Successfully deleted: " + path + "\n"
                + "Type: " + (file.isDirectory() ? "directory" : "file")
        );
    }
}
