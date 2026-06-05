package pro.sketchware.ai.engine.validation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import pro.sketchware.ai.tools.ToolContext;

/**
 * Runtime pre-flight checks for tool arguments — runs AFTER schema validation but BEFORE
 * actual execution.
 *
 * <p>Catches errors that JSON-Schema validation cannot catch:
 * <ul>
 *   <li>Referenced file does not exist on disk</li>
 *   <li>Project sc_id is not in the allowed set</li>
 *   <li>Destination parent directory is missing for write operations</li>
 *   <li>Tool requires an open project but none is provided</li>
 * </ul>
 *
 * <p>Returns a {@link RuntimeValidationResult} with an actionable error message so the
 * AI can self-correct on retry rather than producing an opaque failure.
 */
public final class RuntimeToolValidator {

    // Tools that read an existing file — path must exist before we attempt to open it
    private static final Set<String> FILE_READ_TOOLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "read_file", "read_file_range", "patch_file",
                    "append_to_file", "insert_lines", "delete_lines",
                    "get_screen_source", "copy_file"
            )));

    // Tools that write a new or existing file — parent directory must be reachable
    private static final Set<String> FILE_WRITE_TOOLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "write_file", "patch_file", "append_to_file",
                    "insert_lines", "delete_lines", "copy_file"
            )));

    // Tools that require at least one project to be in scope
    private static final Set<String> PROJECT_SCOPED_TOOLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "build_project", "get_compile_logs", "analyze_build_error",
                    "get_project_info", "list_activities", "get_screen_source",
                    "generate_layout", "add_view_xml", "describe_layout",
                    "get_event_blocks", "set_event_logic", "add_block",
                    "add_string_resource", "add_color_resource", "add_drawable",
                    "add_library", "remove_library", "list_libraries",
                    "create_snapshot", "restore_snapshot", "list_snapshots",
                    "validate_rtl", "audit_material_design", "get_project_health",
                    "export_to_android_studio"
            )));

    private RuntimeToolValidator() {}

    /**
     * Runs runtime pre-flight checks for the given tool call.
     *
     * @param toolName    name of the tool being called
     * @param args        parsed arguments (may be empty, not null)
     * @param toolContext execution context providing allowed projects and file paths
     * @return non-null result; call {@link RuntimeValidationResult#isValid()} to check
     */
    @NonNull
    public static RuntimeValidationResult validate(
            @NonNull String toolName,
            @NonNull JsonObject args,
            @Nullable ToolContext toolContext) {

        // ── 1. Project scope check ─────────────────────────────────────────────
        if (PROJECT_SCOPED_TOOLS.contains(toolName)) {
            if (toolContext == null || toolContext.getAllowedProjectIds().isEmpty()) {
                return RuntimeValidationResult.fail(
                        "Tool '" + toolName + "' requires an open project. "
                        + "Ask the user to open or select a project first.");
            }
            if (args.has("sc_id")) {
                String scId = args.get("sc_id").getAsString();
                if (!toolContext.isProjectAllowed(scId)) {
                    return RuntimeValidationResult.fail(
                            "Project sc_id '" + scId + "' is not in the current session scope. "
                            + "Use list_projects to find valid sc_id values.");
                }
            }
        }

        // ── 2. File existence check for read/patch operations ─────────────────
        if (FILE_READ_TOOLS.contains(toolName) && args.has("path")) {
            String path = args.get("path").getAsString();
            if (path != null && !path.isEmpty()) {
                File f = new File(path);
                if (!f.exists()) {
                    return RuntimeValidationResult.fail(
                            "File not found: \"" + path + "\". "
                            + "Use list_files or read_file on the parent directory to verify the path.");
                }
                if (!f.isFile()) {
                    return RuntimeValidationResult.fail(
                            "Path \"" + path + "\" is a directory, not a file. "
                            + "Use list_files to browse its contents.");
                }
            }
        }

        // ── 3. Parent directory check for write operations ────────────────────
        if (FILE_WRITE_TOOLS.contains(toolName) && args.has("path")) {
            String path = args.get("path").getAsString();
            if (path != null && !path.isEmpty()) {
                File parent = new File(path).getParentFile();
                if (parent != null && !parent.exists()) {
                    return RuntimeValidationResult.fail(
                            "Parent directory does not exist: \"" + parent.getAbsolutePath() + "\". "
                            + "Create the directory first or verify the path.");
                }
            }
        }

        return RuntimeValidationResult.ok();
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public static final class RuntimeValidationResult {
        private final boolean valid;
        @Nullable private final String errorMessage;

        private RuntimeValidationResult(boolean valid, @Nullable String errorMessage) {
            this.valid        = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() { return valid; }

        @Nullable
        public String getErrorMessage() { return errorMessage; }

        static RuntimeValidationResult ok()             { return new RuntimeValidationResult(true,  null); }
        static RuntimeValidationResult fail(String msg) { return new RuntimeValidationResult(false, msg);  }
    }
}
