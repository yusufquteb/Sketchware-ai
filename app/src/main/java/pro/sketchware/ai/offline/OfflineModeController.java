package pro.sketchware.ai.offline;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import pro.sketchware.ai.utils.AiLog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import pro.sketchware.ai.chat.coordinator.ChatCoordinator;
import pro.sketchware.ai.chat.model.ChatMessage;
import pro.sketchware.ai.tools.Tool;
import pro.sketchware.ai.tools.ToolManager;

/**
 * OfflineModeController — Direct tool execution WITHOUT AI involvement.
 *
 * <p><b>Purpose:</b> When the AI model is unavailable (no network, API quota exceeded,
 * model loading), users can still invoke tools directly from the chat UI.
 *
 * <p><b>System flow in offline mode:</b>
 * <pre>
 * User selects tool from OfflineToolPicker
 *         │
 *         ▼
 * OfflineModeController.executeToolDirectly()
 *         │
 *         ▼
 * ToolManager.executeToolSync()
 *         │
 *         ▼
 * Tool.execute() (on background thread)
 *         │
 *         ▼
 * ToolResult → ChatCoordinator.addToolResultMessage() → UI
 * </pre>
 *
 * <p><b>Rules:</b>
 * <ul>
 *   <li>Tools are INDEPENDENT from AI availability — they always work offline.</li>
 *   <li>No AI calls are made in this path.</li>
 *   <li>Results flow back through ChatCoordinator (same UI path as AI responses).</li>
 *   <li>The user sees TOOL-type messages for tool results.</li>
 *   <li>The user sees INTERNAL_ASSISTANT messages for status updates.</li>
 * </ul>
 */
public class OfflineModeController {

    private static final String TAG = "OfflineModeController";

    // ─── Dependencies ─────────────────────────────────────────────────────────

    @NonNull
    private final ToolManager toolManager;

    @NonNull
    private final ChatCoordinator coordinator;

    @NonNull
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── State ────────────────────────────────────────────────────────────────

    private volatile boolean isOfflineModeActive = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public OfflineModeController(
            @NonNull ToolManager toolManager,
            @NonNull ChatCoordinator coordinator
    ) {
        this.toolManager  = toolManager;
        this.coordinator  = coordinator;
    }

    // ─── Offline Mode Toggle ──────────────────────────────────────────────────

    /**
     * Activates offline mode. In offline mode, user messages are NOT sent to AI —
     * they are processed locally through available tools.
     *
     * <p>Call this when network is unavailable or the AI API fails.
     */
    public void activateOfflineMode() {
        if (isOfflineModeActive) return;
        isOfflineModeActive = true;
        AiLog.d(TAG, "Offline mode ACTIVATED.");

        // Post a system message to notify the user
        mainHandler.post(() ->
            coordinator.addSystemMessage(
                "🔌 **Offline Mode Active**\n"
                + "AI is not available. You can still use tools directly.\n"
                + "Type a tool command or tap the tools icon."
            )
        );
    }

    /**
     * Deactivates offline mode, restoring full AI-powered operation.
     */
    public void deactivateOfflineMode() {
        if (!isOfflineModeActive) return;
        isOfflineModeActive = false;
        AiLog.d(TAG, "Offline mode DEACTIVATED.");

        mainHandler.post(() ->
            coordinator.addSystemMessage("✅ **Online Mode Restored** — AI is now available.")
        );
    }

    /**
     * Returns true if offline mode is currently active.
     */
    public boolean isOfflineModeActive() {
        return isOfflineModeActive;
    }

    // ─── Direct Tool Execution ────────────────────────────────────────────────

    /**
     * Executes a tool directly (without AI) and routes the result through ChatCoordinator.
     *
     * <p>This is the primary entry point for offline tool usage.
     * Runs the tool on a background thread via ToolManager.
     *
     * @param toolName  the snake_case name of the tool to execute
     * @param jsonInput the JSON input for the tool (may be null)
     */
    public void executeToolDirectly(
            @NonNull String toolName,
            @Nullable String jsonInput
    ) {
        AiLog.d(TAG, "executeToolDirectly: " + toolName);

        // Show "executing" status in chat
        mainHandler.post(() ->
            coordinator.addInternalAssistantMessage(
                "⚙️ Running tool: **`" + toolName + "`**…"
            )
        );

        // Execute on ToolManager's background thread pool
        toolManager.executeTool(toolName, jsonInput, (name, result) -> {
            AiLog.d(TAG, "Tool '" + name + "' completed: success=" + result.success);

            // Route result back to UI via ChatCoordinator on main thread
            mainHandler.post(() -> {
                if (result.success) {
                    coordinator.addToolResultMessage(name, result.content);
                } else {
                    coordinator.addInternalAssistantMessage(
                        "❌ **Tool failed: `" + name + "`**\n\n" + result.content
                    );
                }
            });
        });
    }

    /**
     * Executes a tool directly with a pre-built {@link Tool.ToolResult} callback.
     * Used when the caller needs the result for further processing.
     *
     * @param toolName  the snake_case name of the tool to execute
     * @param jsonInput the JSON input for the tool
     * @param callback  receives the result when execution completes (on tool thread)
     */
    public void executeToolWithCallback(
            @NonNull String toolName,
            @Nullable String jsonInput,
            @NonNull ToolManager.ToolExecutionCallback callback
    ) {
        AiLog.d(TAG, "executeToolWithCallback: " + toolName);
        toolManager.executeTool(toolName, jsonInput, callback);
    }

    // ─── Tool Listing (for Offline Picker UI) ─────────────────────────────────

    /**
     * Returns an unmodifiable list of all registered tools.
     * Used by the OfflineToolPickerBottomSheet to render the tool list.
     */
    @NonNull
    public List<Tool> getAvailableTools() {
        return toolManager.getAllTools();
    }

    /**
     * Returns the tool registered under the given name, or null.
     *
     * @param toolName the snake_case tool name
     */
    @Nullable
    public Tool getTool(@NonNull String toolName) {
        return toolManager.getTool(toolName);
    }

    // ─── Offline Message Interception ──────────────────────────────────────────

    /**
     * Called by ChatCoordinator when a user message arrives in offline mode.
     * Attempts to detect tool-intent from the message text and auto-execute.
     *
     * <p>Pattern matching rules (simple heuristic for offline mode):
     * <ul>
     *   <li>Message starts with "/" → treat as tool command</li>
     *   <li>Message contains "read file", "open file" → read_file</li>
     *   <li>Message contains "copy" → copy_text</li>
     *   <li>Message contains "parse json" → parse_json</li>
     *   <li>Otherwise → show available tools list</li>
     * </ul>
     *
     * @param userMessage the user's input message
     * @return true if a tool was auto-triggered, false if no match
     */
    public boolean handleOfflineMessage(@NonNull ChatMessage userMessage) {
        if (!isOfflineModeActive) return false;

        String text = userMessage.getText();
        if (text == null || text.trim().isEmpty()) return false;

        String lower = text.trim().toLowerCase();

        // Tool command syntax: /tool_name {json}
        if (lower.startsWith("/")) {
            return parseAndExecuteToolCommand(text.trim());
        }

        // Natural language tool detection
        if (lower.contains("read file") || lower.contains("open file")) {
            showToolPrompt("read_file",
                    "Please provide the file path. Example:\n"
                    + "`/read_file {\"path\": \"/path/to/file.txt\"}`");
            return true;
        }
        if (lower.contains("copy")) {
            showToolPrompt("copy_text",
                    "Please provide the text to copy. Example:\n"
                    + "`/copy_text {\"text\": \"Your text here\"}`");
            return true;
        }
        if (lower.contains("parse json")) {
            showToolPrompt("parse_json",
                    "Please provide JSON text to parse. Example:\n"
                    + "`/parse_json {\"json_text\": \"{\\\"key\\\":\\\"value\\\"}\"}`");
            return true;
        }

        // No tool match — show available tools
        showAvailableToolsHelp();
        return true;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Parses a /tool_name {json} command from the user's message.
     * Returns true if a valid tool command was found and dispatched.
     */
    private boolean parseAndExecuteToolCommand(@NonNull String text) {
        // Format: /tool_name or /tool_name {"key":"value"}
        int spaceIdx = text.indexOf(' ');
        String toolName;
        String jsonInput = null;

        if (spaceIdx < 0) {
            toolName = text.substring(1); // strip leading "/"
        } else {
            toolName  = text.substring(1, spaceIdx);
            jsonInput = text.substring(spaceIdx + 1).trim();
        }

        if (!toolManager.isRegistered(toolName)) {
            mainHandler.post(() ->
                coordinator.addInternalAssistantMessage(
                    "❓ Unknown tool: **`" + toolName + "`**\n\n"
                    + getAvailableToolsText()
                )
            );
            return true;
        }

        executeToolDirectly(toolName, jsonInput);
        return true;
    }

    private void showToolPrompt(@NonNull String toolName, @NonNull String hint) {
        Tool tool = toolManager.getTool(toolName);
        String desc = tool != null ? tool.getDescription() : "";

        mainHandler.post(() ->
            coordinator.addInternalAssistantMessage(
                "**Tool: `" + toolName + "`**\n"
                + (desc.isEmpty() ? "" : "_" + desc + "_\n\n")
                + hint
            )
        );
    }

    private void showAvailableToolsHelp() {
        mainHandler.post(() ->
            coordinator.addInternalAssistantMessage(
                "🔌 **Offline Mode** — Available commands:\n\n"
                + getAvailableToolsText()
                + "\n\nUsage: `/tool_name {\"key\":\"value\"}`"
            )
        );
    }

    @NonNull
    private String getAvailableToolsText() {
        List<Tool> tools = toolManager.getAllTools();
        if (tools.isEmpty()) return "_No tools registered._";

        StringBuilder sb = new StringBuilder();
        for (Tool tool : tools) {
            sb.append("• **`/").append(tool.getName()).append("`** — ")
              .append(tool.getDescription()).append('\n');
        }
        return sb.toString().trim();
    }
}
