package pro.sketchware.ai.tools;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * ToolManager — Central registry and execution engine for all AI platform tools.
 *
 * <p><b>Architecture position:</b>
 * <pre>
 * AIOrchestrator ──► ToolManager ──► Tool.execute() ──► ToolResult
 *                         │
 *                    OfflineModeController (direct access for user-triggered tools)
 * </pre>
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Maintain a registry of all registered {@link Tool} implementations.</li>
 *   <li>Execute tools on a dedicated background thread pool (never on main thread).</li>
 *   <li>Enforce execution timeouts to prevent tool hangs.</li>
 *   <li>Provide tool listing for offline UI (tool picker).</li>
 * </ul>
 *
 * <p><b>Tool execution rules:</b>
 * <ul>
 *   <li>All executions run on {@link #toolExecutor} — NEVER on main thread.</li>
 *   <li>Each tool execution is wrapped in a timeout: {@link #TOOL_TIMEOUT_SECONDS}.</li>
 *   <li>Tool instances are reused across calls — tools MUST be stateless.</li>
 *   <li>Unknown tool names return a {@link Tool.ToolResult#failure} gracefully.</li>
 * </ul>
 *
 * <p><b>Thread safety:</b> Registration should happen at app startup (single-threaded).
 * {@link #executeTool} is safe to call from any thread.
 */
public class ToolManager {

    private static final String TAG = "ToolManager";

    /** Maximum wall-clock seconds a single tool execution may take. */
    private static final long TOOL_TIMEOUT_SECONDS = 30L;

    // ─── Singleton ────────────────────────────────────────────────────────────

    @Nullable
    private static volatile ToolManager instance;

    @NonNull
    public static ToolManager getInstance() {
        if (instance == null) {
            synchronized (ToolManager.class) {
                if (instance == null) {
                    instance = new ToolManager();
                }
            }
        }
        return instance;
    }

    // ─── Registry ─────────────────────────────────────────────────────────────

    /**
     * Ordered map: tool name → tool instance.
     * LinkedHashMap preserves insertion order for the offline tool picker.
     */
    @NonNull
    private final Map<String, Tool> registry = new LinkedHashMap<>();

    // ─── Executor ─────────────────────────────────────────────────────────────

    /**
     * Fixed thread pool for tool execution.
     * 3 threads allow parallel tool calls during complex AI agentic flows.
     */
    @NonNull
    private final ExecutorService toolExecutor = Executors.newFixedThreadPool(
            3, r -> {
                Thread t = new Thread(r, "ToolManager-Worker");
                t.setDaemon(true);
                return t;
            });

    // ─── Private constructor ──────────────────────────────────────────────────

    private ToolManager() {}

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a tool in the manager.
     * Must be called before any AI request that might trigger this tool.
     * Calling this with a duplicate name REPLACES the previous registration.
     *
     * @param tool the tool implementation to register
     */
    public synchronized void register(@NonNull Tool tool) {
        String name = tool.getName();
        if (registry.containsKey(name)) {
            Log.w(TAG, "Replacing existing tool: " + name);
        }
        registry.put(name, tool);
        Log.d(TAG, "Registered tool: " + name + " — " + tool.getDescription());
    }

    /**
     * Registers multiple tools at once.
     *
     * @param tools the tools to register
     */
    public synchronized void registerAll(@NonNull List<Tool> tools) {
        for (Tool tool : tools) {
            register(tool);
        }
    }

    /**
     * Returns the tool registered under the given name, or null if not found.
     *
     * @param name the exact snake_case tool name (must match AI output)
     */
    @Nullable
    public synchronized Tool getTool(@NonNull String name) {
        return registry.get(name);
    }

    /**
     * Returns an unmodifiable ordered list of all registered tools.
     * Used by the offline tool picker UI.
     */
    @NonNull
    public synchronized List<Tool> getAllTools() {
        return Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }

    /**
     * Returns true if a tool with the given name is registered.
     *
     * @param name the tool name to check
     */
    public synchronized boolean isRegistered(@NonNull String name) {
        return registry.containsKey(name);
    }

    /**
     * Returns the count of registered tools.
     */
    public synchronized int getToolCount() {
        return registry.size();
    }

    // ─── Execution ────────────────────────────────────────────────────────────

    /**
     * Executes the named tool asynchronously on the tool executor thread pool.
     *
     * <p>Safe to call from any thread. The callback is invoked on the tool executor
     * thread — the caller is responsible for dispatching to main if needed.
     *
     * @param toolName  the exact name of the tool to execute
     * @param jsonInput the JSON input string for the tool (may be null)
     * @param callback  receives the {@link Tool.ToolResult} when execution finishes
     * @return a {@link Future} that can be used to cancel the execution
     */
    @NonNull
    public Future<?> executeTool(
            @NonNull String toolName,
            @Nullable String jsonInput,
            @NonNull ToolExecutionCallback callback
    ) {
        return toolExecutor.submit(() -> {
            Tool.ToolResult result = executeToolSync(toolName, jsonInput);
            callback.onToolResult(toolName, result);
        });
    }

    /**
     * Executes the named tool synchronously on the calling thread.
     * <b>Must be called from a worker thread — NEVER from main thread.</b>
     *
     * <p>Wraps execution in a timeout via an inner {@link Future}.
     *
     * @param toolName  the exact name of the tool to execute
     * @param jsonInput the JSON input string for the tool (may be null)
     * @return the {@link Tool.ToolResult} — never null
     */
    @NonNull
    @WorkerThread
    public Tool.ToolResult executeToolSync(
            @NonNull String toolName,
            @Nullable String jsonInput
    ) {
        Tool tool;
        synchronized (this) {
            tool = registry.get(toolName);
        }

        if (tool == null) {
            Log.w(TAG, "executeToolSync: unknown tool '" + toolName + "'");
            return Tool.ToolResult.failure(
                    toolName,
                    "Tool '" + toolName + "' is not registered. "
                    + "Available tools: " + getToolNameList()
            );
        }

        Log.d(TAG, "Executing tool: " + toolName + " | input=" + summarize(jsonInput));

        // Wrap in a timeout-enforced inner future
        final Tool finalTool = tool;
        Future<Tool.ToolResult> future = toolExecutor.submit(() -> {
            try {
                return finalTool.execute(jsonInput);
            } catch (Exception e) {
                Log.e(TAG, "Tool '" + toolName + "' threw uncaught exception", e);
                return Tool.ToolResult.failure(
                        toolName,
                        "Unexpected error during execution: " + e.getMessage()
                );
            }
        });

        try {
            Tool.ToolResult result = future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Log.d(TAG, "Tool '" + toolName + "' completed: success=" + result.success);
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            Log.e(TAG, "Tool '" + toolName + "' timed out after " + TOOL_TIMEOUT_SECONDS + "s");
            return Tool.ToolResult.failure(
                    toolName,
                    "Tool execution timed out after " + TOOL_TIMEOUT_SECONDS + " seconds."
            );
        } catch (java.util.concurrent.ExecutionException e) {
            Log.e(TAG, "Tool '" + toolName + "' execution error", e);
            return Tool.ToolResult.failure(
                    toolName,
                    "Execution error: " + (e.getCause() != null
                            ? e.getCause().getMessage() : e.getMessage())
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Tool.ToolResult.failure(toolName, "Tool execution was interrupted.");
        }
    }

    // ─── Shutdown ─────────────────────────────────────────────────────────────

    /**
     * Shuts down the tool executor. Call from Application.onTerminate or a ViewModel.
     * After calling this, no further tools can be executed.
     */
    public void shutdown() {
        toolExecutor.shutdownNow();
        Log.d(TAG, "ToolManager executor shut down.");
    }

    // ─── Callback interface ───────────────────────────────────────────────────

    /**
     * Callback delivered when an async tool execution completes.
     * Invoked on the tool executor thread — dispatch to main as needed.
     */
    public interface ToolExecutionCallback {
        /**
         * @param toolName the name of the tool that executed
         * @param result   the execution result (success or failure)
         */
        void onToolResult(@NonNull String toolName, @NonNull Tool.ToolResult result);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    @NonNull
    private String getToolNameList() {
        StringBuilder sb = new StringBuilder("[");
        synchronized (this) {
            boolean first = true;
            for (String name : registry.keySet()) {
                if (!first) sb.append(", ");
                sb.append(name);
                first = false;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @NonNull
    private static String summarize(@Nullable String s) {
        if (s == null) return "null";
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
