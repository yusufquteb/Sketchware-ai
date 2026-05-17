package pro.sketchware.ai.tools;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Tool — Contract that every pluggable tool in the AI platform must implement.
 *
 * <p><b>Architecture invariant:</b>
 * <pre>
 * AI (decision maker) → ToolManager → Tool.execute() → result → AIOrchestrator → UI
 * </pre>
 *
 * <p><b>Rules for every Tool implementation:</b>
 * <ul>
 *   <li>{@link #execute} MUST be called on a worker thread — never on main.</li>
 *   <li>Tools must be stateless and reusable (no mutable instance state).</li>
 *   <li>Tools must never touch the UI layer directly.</li>
 *   <li>Tools must never call the AI model directly.</li>
 *   <li>All failures must be returned via {@link ToolResult#failure} — never throw.</li>
 * </ul>
 *
 * <p><b>Tool identity rules:</b>
 * <ul>
 *   <li>{@link #getName} must be unique across all registered tools.</li>
 *   <li>{@link #getName} must match the {@code "tool_name"} key in AI JSON output exactly.</li>
 *   <li>Name format: {@code snake_case}, e.g. {@code read_file}, {@code parse_json}.</li>
 * </ul>
 */
public interface Tool {

    // ─── Identity ─────────────────────────────────────────────────────────────

    /**
     * Unique snake_case identifier for this tool.
     * Must match the AI's {@code "tool_name"} output exactly.
     *
     * <p>Examples: {@code "read_file"}, {@code "write_file"}, {@code "parse_json"}
     */
    @NonNull
    String getName();

    /**
     * Human-readable description of what this tool does.
     * Shown in the chat UI for TOOL-type messages and in the offline tool picker.
     */
    @NonNull
    String getDescription();

    /**
     * JSON schema describing the expected input format for this tool.
     * Used for:
     * <ul>
     *   <li>AI prompt injection (system context).</li>
     *   <li>Offline UI parameter form generation.</li>
     *   <li>Input validation before execution.</li>
     * </ul>
     *
     * <p>Minimal example: {@code {"type":"object","properties":{"path":{"type":"string"}}}}
     *
     * <p>May return {@code null} if the tool takes no structured input.
     */
    @Nullable
    String getInputSchema();

    // ─── Execution ────────────────────────────────────────────────────────────

    /**
     * Executes the tool with the given JSON input string.
     *
     * <p>This method runs on a background worker thread managed by {@link ToolManager}.
     * It MUST NOT block indefinitely — implement a reasonable timeout internally.
     *
     * @param jsonInput the raw JSON input string from the AI (or offline UI).
     *                  Will be {@code null} or empty for tools that take no input.
     * @return a {@link ToolResult} — never null, never throws.
     */
    @NonNull
    @WorkerThread
    ToolResult execute(@Nullable String jsonInput);

    // ─── ToolResult ───────────────────────────────────────────────────────────

    /**
     * Immutable result returned by every Tool execution.
     * Carries success/failure status, output content, and optional metadata.
     */
    final class ToolResult {

        /** True if the tool completed without error. */
        public final boolean success;

        /**
         * The primary output of the tool.
         * On success: the tool's result (text, JSON string, etc.).
         * On failure: the human-readable error description.
         */
        @NonNull
        public final String content;

        /**
         * Optional MIME type hint for the content.
         * Examples: {@code "text/plain"}, {@code "application/json"}, {@code "image/png"}
         * Null when not applicable.
         */
        @Nullable
        public final String contentType;

        /**
         * Optional raw bytes result — for binary tool outputs (e.g., image data).
         * Null for text-only tools.
         */
        @Nullable
        public final byte[] rawData;

        private ToolResult(
                boolean success,
                @NonNull String content,
                @Nullable String contentType,
                @Nullable byte[] rawData
        ) {
            this.success   = success;
            this.content   = content;
            this.contentType = contentType;
            this.rawData   = rawData;
        }

        /** Creates a plain-text success result. */
        @NonNull
        public static ToolResult success(@NonNull String content) {
            return new ToolResult(true, content, "text/plain", null);
        }

        /** Creates a JSON success result. */
        @NonNull
        public static ToolResult successJson(@NonNull String jsonContent) {
            return new ToolResult(true, jsonContent, "application/json", null);
        }

        /** Creates a success result with explicit MIME type. */
        @NonNull
        public static ToolResult success(
                @NonNull String content,
                @NonNull String contentType
        ) {
            return new ToolResult(true, content, contentType, null);
        }

        /** Creates a failure result with an error message. */
        @NonNull
        public static ToolResult failure(@NonNull String errorMessage) {
            return new ToolResult(false, errorMessage, null, null);
        }

        /** Creates a failure result with context about the tool and error. */
        @NonNull
        public static ToolResult failure(
                @NonNull String toolName,
                @NonNull String errorMessage
        ) {
            return new ToolResult(false,
                    "[" + toolName + " failed] " + errorMessage, null, null);
        }

        @NonNull
        @Override
        public String toString() {
            return "ToolResult{"
                    + "success=" + success
                    + ", contentLen=" + content.length()
                    + ", contentType='" + contentType + '\''
                    + '}';
        }
    }
}
