package pro.sketchware.ai.orchestrator;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.ai.chat.coordinator.ChatCoordinator;
import pro.sketchware.ai.chat.model.ChatMessage;
import pro.sketchware.ai.tools.Tool;
import pro.sketchware.ai.tools.ToolManager;

/**
 * AIOrchestrator — The INTELLIGENCE LAYER of the AI Chat Platform.
 *
 * <p><b>Core principle — AI is NOT an executor. AI is ONLY a decision maker.</b>
 *
 * <p><b>Mandatory system flow:</b>
 * <pre>
 * User Input
 *     │
 *     ▼
 * ChatCoordinator.sendUserMessage()
 *     │
 *     ▼
 * AIOrchestrator.onUserMessageReady()      ← THIS CLASS
 *     │
 *     ├─► AI Model (decision: text or tool?)
 *     │        │
 *     │        ├─ type="text"  → AiResponseCallback.onStreamingComplete()
 *     │        │                        │
 *     │        │                        ▼
 *     │        │                    ChatCoordinator → UI
 *     │        │
 *     │        └─ type="tool"  → ToolManager.executeToolSync()
 *     │                                │
 *     │                                ▼
 *     │                          Tool.execute()
 *     │                                │
 *     │                                ▼
 *     │                       Tool result → (optional: re-query AI)
 *     │                                │
 *     │                                ▼
 *     │                        AiResponseCallback → ChatCoordinator → UI
 *     │
 *     └─ OFFLINE mode → OfflineModeController handles directly
 * </pre>
 *
 * <p><b>AI Response JSON Format (MANDATORY):</b>
 * <pre>
 * {
 *   "type":       "text" | "tool",
 *   "content":    "Response text (for type=text, or explanation for type=tool)",
 *   "tool_name":  "snake_case_tool_name (required when type=tool)",
 *   "tool_input": "{\"key\":\"value\"} (JSON string, required when type=tool)"
 * }
 * </pre>
 *
 * <p><b>Forbidden:</b>
 * <ul>
 *   <li>AI must NOT execute file operations directly.</li>
 *   <li>AI must NOT update the UI directly.</li>
 *   <li>AI must NOT access Android system APIs.</li>
 *   <li>This class must NOT contain UI code.</li>
 * </ul>
 *
 * <p><b>Thread safety:</b> All public methods may be called from any thread.
 * All AI work runs on {@link #aiExecutor}. All UI updates flow through
 * {@link ChatCoordinator.AiResponseCallback}, which is thread-safe.
 */
public class AIOrchestrator implements ChatCoordinator.AiDelegate {

    private static final String TAG = "AIOrchestrator";

    // ─── Configuration ────────────────────────────────────────────────────────

    /**
     * System prompt injected into every AI request.
     * Instructs the AI to respond ONLY in the mandatory JSON format.
     */
    private static final String SYSTEM_PROMPT =
            "You are an AI assistant integrated into an Android development platform (Sketchware Pro). "
            + "You help developers write code, debug issues, and manage project files.\n\n"
            + "MANDATORY RESPONSE FORMAT — You MUST ALWAYS respond with ONLY valid JSON:\n"
            + "{\n"
            + "  \"type\": \"text\" | \"tool\",\n"
            + "  \"content\": \"Your response or explanation here\",\n"
            + "  \"tool_name\": \"tool_name_here (only when type=tool)\",\n"
            + "  \"tool_input\": \"{\\\"key\\\":\\\"value\\\"} (only when type=tool)\"\n"
            + "}\n\n"
            + "RULES:\n"
            + "- ALWAYS wrap your response in the JSON structure above.\n"
            + "- For conversational replies, use type=text.\n"
            + "- For file/data operations, use type=tool with the appropriate tool_name.\n"
            + "- Never execute tools yourself — only declare which tool to call.\n"
            + "- Available tools: read_file, write_file, delete_file, parse_json, analyze_xml, copy_text.\n"
            + "- NEVER include markdown fences around the JSON response.\n";

    // ─── Dependencies ─────────────────────────────────────────────────────────

    @NonNull
    private final ToolManager toolManager;

    @NonNull
    private final AiModelProvider modelProvider;

    /** Background executor for AI request processing. */
    @NonNull
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AIOrchestrator-BG");
        t.setDaemon(true);
        return t;
    });

    /** True when a cancellation has been requested for the current request. */
    private volatile boolean cancelRequested = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates the AIOrchestrator with a model provider and tool manager.
     *
     * @param modelProvider the AI model backend implementation
     * @param toolManager   the registered tool registry
     */
    public AIOrchestrator(
            @NonNull AiModelProvider modelProvider,
            @NonNull ToolManager toolManager
    ) {
        this.modelProvider = modelProvider;
        this.toolManager   = toolManager;
    }

    // ─── ChatCoordinator.AiDelegate ───────────────────────────────────────────

    @Override
    @WorkerThread
    public void onUserMessageReady(
            @NonNull ChatMessage userMessage,
            @NonNull List<ChatMessage> history,
            @NonNull ChatCoordinator.AiResponseCallback callback
    ) {
        cancelRequested = false;
        Log.d(TAG, "onUserMessageReady: " + userMessage.getId());

        // Signal the UI that the AI is working
        callback.onStreamingStarted();

        // Build conversation context for the AI model
        String conversationContext = buildConversationContext(history);

        // Send to AI model
        String rawAiResponse;
        try {
            rawAiResponse = modelProvider.complete(
                    SYSTEM_PROMPT,
                    conversationContext,
                    userMessage.getText() != null ? userMessage.getText() : ""
            );
        } catch (Exception e) {
            Log.e(TAG, "AI model error", e);
            callback.onError("AI model error: " + e.getMessage());
            return;
        }

        if (cancelRequested) {
            Log.d(TAG, "Request cancelled after AI response.");
            return;
        }

        if (rawAiResponse == null || rawAiResponse.trim().isEmpty()) {
            callback.onError("The AI returned an empty response. Please try again.");
            return;
        }

        // Parse and route the AI response
        processAiResponse(rawAiResponse, callback, userMessage.getText());
    }

    @Override
    public void onCancelRequested() {
        cancelRequested = true;
        modelProvider.cancel();
        Log.d(TAG, "Cancel requested.");
    }

    // ─── AI Response Processing ───────────────────────────────────────────────

    /**
     * Parses the raw AI response JSON and routes it:
     * <ul>
     *   <li>type=text  → deliver as a text response</li>
     *   <li>type=tool  → execute via ToolManager, then deliver result</li>
     *   <li>malformed  → attempt graceful recovery</li>
     * </ul>
     */
    @WorkerThread
    private void processAiResponse(
            @NonNull String rawResponse,
            @NonNull ChatCoordinator.AiResponseCallback callback,
            @Nullable String originalUserText
    ) {
        Log.d(TAG, "Processing AI response: " + summarize(rawResponse));

        // ── 1. Parse AI JSON ───────────────────────────────────────────────
        OrchestratorResponse parsed = parseAiResponse(rawResponse);

        if (parsed == null) {
            // Graceful recovery: treat the whole raw response as text
            Log.w(TAG, "AI response not in JSON format — using raw text as fallback.");
            callback.onStreamingComplete(rawResponse);
            return;
        }

        if (cancelRequested) return;

        // ── 2. Route based on type ─────────────────────────────────────────
        switch (parsed.type) {

            case "text":
                // Direct text response — deliver to UI
                String content = parsed.content != null ? parsed.content : "";
                callback.onStreamingComplete(content);
                break;

            case "tool":
                // Tool execution request
                handleToolCall(parsed, callback, originalUserText);
                break;

            default:
                Log.w(TAG, "Unknown response type: '" + parsed.type + "' — treating as text.");
                callback.onStreamingComplete(
                        parsed.content != null ? parsed.content : rawResponse);
                break;
        }
    }

    /**
     * Handles a tool call decision from the AI:
     * <ol>
     *   <li>Validate tool name and input.</li>
     *   <li>Execute via ToolManager (blocking — on worker thread).</li>
     *   <li>Optionally: feed result back to AI for final answer.</li>
     *   <li>Deliver final response to ChatCoordinator.</li>
     * </ol>
     */
    @WorkerThread
    private void handleToolCall(
            @NonNull OrchestratorResponse parsed,
            @NonNull ChatCoordinator.AiResponseCallback callback,
            @Nullable String originalUserText
    ) {
        String toolName  = parsed.toolName;
        String toolInput = parsed.toolInput;

        // ── Validate tool call ─────────────────────────────────────────────
        if (toolName == null || toolName.isEmpty()) {
            callback.onError("AI requested a tool call but did not specify 'tool_name'.");
            return;
        }

        if (!toolManager.isRegistered(toolName)) {
            callback.onError(
                    "AI requested unknown tool: '" + toolName + "'.\n"
                    + "Available tools: " + getAvailableToolNames());
            return;
        }

        Log.d(TAG, "Executing tool: " + toolName + " | input=" + summarize(toolInput));

        // ── Execute tool ───────────────────────────────────────────────────
        Tool.ToolResult toolResult = toolManager.executeToolSync(toolName, toolInput);

        if (cancelRequested) return;

        // ── Build response from tool result ────────────────────────────────
        if (!toolResult.success) {
            // Tool failed — report the error to the user
            String errorResponse = "**Tool execution failed: " + toolName + "**\n\n"
                    + toolResult.content;
            callback.onStreamingComplete(errorResponse);
            return;
        }

        // ── Optional: re-query AI with tool result for a synthesized answer ──
        // This sends the tool result back to the AI so it can compose
        // a natural language response incorporating the data.
        String finalResponse = synthesizeWithAi(
                toolName, toolResult.content, originalUserText, parsed.content);

        callback.onStreamingComplete(finalResponse);
    }

    /**
     * Optionally re-queries the AI to synthesize a natural language response
     * incorporating the tool result. Falls back to a formatted tool result
     * if the AI is unavailable or the synthesis fails.
     *
     * @param toolName     the tool that was executed
     * @param toolOutput   the raw output from the tool
     * @param userQuestion the user's original question
     * @param aiExplanation the AI's explanation of why it used the tool
     * @return a final response string for the UI
     */
    @WorkerThread
    @NonNull
    private String synthesizeWithAi(
            @NonNull String toolName,
            @NonNull String toolOutput,
            @Nullable String userQuestion,
            @Nullable String aiExplanation
    ) {
        // Build a synthesis prompt
        String synthesisPrompt =
                "Tool '" + toolName + "' was executed successfully. "
                + "Here is the result:\n\n" + toolOutput + "\n\n"
                + "User's original question: " + (userQuestion != null ? userQuestion : "(unknown)") + "\n\n"
                + "Please provide a clear, helpful summary of what the tool found/did. "
                + "Format your response as: {\"type\":\"text\",\"content\":\"...\"}";

        try {
            String synthesisResponse = modelProvider.complete(
                    SYSTEM_PROMPT, "", synthesisPrompt);

            if (synthesisResponse != null && !synthesisResponse.trim().isEmpty()) {
                OrchestratorResponse synth = parseAiResponse(synthesisResponse);
                if (synth != null && synth.content != null && !synth.content.isEmpty()) {
                    return synth.content;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "AI synthesis failed, using formatted tool output.", e);
        }

        // Fallback: format the tool result directly
        return formatToolResult(toolName, toolOutput, aiExplanation);
    }

    /**
     * Formats a tool result into a readable chat message when AI synthesis fails.
     */
    @NonNull
    private String formatToolResult(
            @NonNull String toolName,
            @NonNull String toolOutput,
            @Nullable String aiExplanation
    ) {
        StringBuilder sb = new StringBuilder();

        if (aiExplanation != null && !aiExplanation.isEmpty()) {
            sb.append(aiExplanation).append("\n\n");
        }

        sb.append("**Tool: `").append(toolName).append("`**\n\n");
        sb.append("```\n").append(toolOutput).append("\n```");

        return sb.toString();
    }

    // ─── JSON Parsing ─────────────────────────────────────────────────────────

    /**
     * Parses the AI's structured JSON response.
     * Returns null if parsing fails — caller should apply graceful recovery.
     */
    @Nullable
    private OrchestratorResponse parseAiResponse(@NonNull String raw) {
        String cleaned = raw.trim();

        // Strip markdown fences if present (AI sometimes adds them despite instructions)
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // Find the JSON object boundaries
        int start = cleaned.indexOf('{');
        int end   = cleaned.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            Log.w(TAG, "No JSON object found in AI response.");
            return null;
        }
        cleaned = cleaned.substring(start, end + 1);

        try {
            JSONObject json = new JSONObject(cleaned);
            OrchestratorResponse response = new OrchestratorResponse();
            response.type       = json.optString("type",       "text");
            response.content    = json.optString("content",    null);
            response.toolName   = json.optString("tool_name",  null);
            response.toolInput  = json.optString("tool_input", null);
            return response;
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse AI JSON response: " + e.getMessage());
            return null;
        }
    }

    // ─── Context Building ─────────────────────────────────────────────────────

    /**
     * Builds a conversation context string from the message history.
     *
     * <p>Windowing strategy: keep the last {@code MAX_CONTEXT_TURNS} USER+AI turns.
     * TOOL results and SYSTEM notifications are included unconditionally within
     * that window — they carry critical execution feedback the AI needs to reason
     * correctly about follow-up steps.
     *
     * <p>Format injected into the AI prompt:
     * <pre>
     * Previous conversation:
     * User: ...
     * Assistant: ...
     * Tool result (read_file): ...
     * System: ...
     * </pre>
     */
    private static final int MAX_CONTEXT_TURNS = 20;

    @NonNull
    private String buildConversationContext(@NonNull List<ChatMessage> history) {
        if (history.isEmpty()) return "";

        // Find the start index so we keep at most MAX_CONTEXT_TURNS user/AI turns.
        int turns = 0;
        int start = history.size();
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage.MessageType t = history.get(i).getType();
            if (t == ChatMessage.MessageType.USER || t == ChatMessage.MessageType.AI
                    || t == ChatMessage.MessageType.INTERNAL_ASSISTANT) {
                turns++;
                if (turns >= MAX_CONTEXT_TURNS) {
                    start = i;
                    break;
                }
            }
        }
        if (start == history.size()) start = 0;

        StringBuilder sb = new StringBuilder("Previous conversation:\n");
        for (int i = start; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if (!msg.hasText()) continue;
            switch (msg.getType()) {
                case USER:
                    sb.append("User: ").append(msg.getText()).append('\n');
                    break;
                case AI:
                case INTERNAL_ASSISTANT:
                    sb.append("Assistant: ").append(msg.getText()).append('\n');
                    break;
                case TOOL:
                    // Tool results are essential context — the AI must see what operations returned.
                    String label = msg.getToolName() != null
                            ? "Tool result (" + msg.getToolName() + ")"
                            : "Tool result";
                    sb.append(label).append(": ").append(msg.getText()).append('\n');
                    break;
                case SYSTEM:
                    sb.append("System: ").append(msg.getText()).append('\n');
                    break;
            }
        }

        return sb.toString();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @NonNull
    private String getAvailableToolNames() {
        List<Tool> tools = toolManager.getAllTools();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(tools.get(i).getName());
        }
        sb.append("]");
        return sb.toString();
    }

    @NonNull
    private static String summarize(@Nullable String s) {
        if (s == null) return "null";
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }

    // ─── Internal data structure ──────────────────────────────────────────────

    /**
     * Parsed representation of the AI's structured JSON response.
     */
    private static class OrchestratorResponse {
        @NonNull  String type      = "text";
        @Nullable String content   = null;
        @Nullable String toolName  = null;
        @Nullable String toolInput = null;
    }

    // ─── AiModelProvider interface ────────────────────────────────────────────

    /**
     * Abstraction over the actual AI model backend.
     *
     * <p>Implementations may use:
     * <ul>
     *   <li>OpenAI API (ChatGPT-4, GPT-4o)</li>
     *   <li>Anthropic API (Claude)</li>
     *   <li>Google Gemini API</li>
     *   <li>Local on-device model (GGUF via llama.cpp)</li>
     *   <li>Any other LLM backend</li>
     * </ul>
     *
     * <p>The implementation is injected at startup — the orchestrator
     * has ZERO awareness of which backend is active.
     */
    public interface AiModelProvider {

        /**
         * Sends a complete prompt to the AI model and returns the raw response.
         * MUST be called on a worker thread.
         *
         * @param systemPrompt   the system/instruction prompt
         * @param context        conversation history context (may be empty)
         * @param userMessage    the current user message
         * @return raw AI response string, or null on failure
         * @throws Exception on network errors, API errors, etc.
         */
        @WorkerThread
        @Nullable
        String complete(
                @NonNull String systemPrompt,
                @NonNull String context,
                @NonNull String userMessage
        ) throws Exception;

        /**
         * Cancels the current in-progress request, if any.
         * Safe to call from any thread. Should be a no-op if no request is active.
         */
        void cancel();
    }
}
