package pro.sketchware.ai.engine;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import pro.sketchware.ai.core.ToolTelemetry;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

/**
 * Wraps {@link AgentTool#execute} with a hard timeout and telemetry recording.
 *
 * <p>Uses a dedicated daemon thread pool that is completely separate from
 * {@link AgentExecutor}'s single executor thread, preventing deadlocks while
 * allowing {@code Future.get(timeout)} to enforce per-tool time limits.
 *
 * <p>All methods are thread-safe and non-blocking from the caller's perspective
 * (the caller blocks for at most {@link #TOOL_TIMEOUT_MS} ms).
 */
public final class ToolExecutionGuard {

    /** Hard timeout for any single tool execution. */
    public static final long TOOL_TIMEOUT_MS = 30_000L;

    /**
     * Shared thread pool for tool execution.
     * Daemon threads so they don't prevent JVM shutdown; cached so idle threads
     * are cleaned up after 60 s without work.
     */
    private static final ExecutorService TOOL_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-tool-exec");
        t.setDaemon(true);
        return t;
    });

    private ToolExecutionGuard() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Executes {@code tool} with the given arguments, enforcing a 30-second timeout
     * and recording timing + outcome in {@link ToolTelemetry}.
     *
     * <p>Never throws — all exceptional paths are folded into {@link ToolResult#failure}.
     *
     * @param tool    the tool to execute
     * @param args    parsed JSON arguments from the model
     * @param context execution context (project IDs, cancellation checker, etc.)
     * @param callId  tool-call ID used to construct the returned ToolResult
     * @return a non-null ToolResult — success or failure
     */
    @NonNull
    public static ToolResult executeWithTimeout(
            @NonNull AgentTool tool,
            @NonNull JsonObject args,
            @NonNull ToolContext context,
            @NonNull String callId) {

        String name  = tool.getName();
        long   token = ToolTelemetry.getInstance().recordStart(name);

        // Check cancellation before even starting
        if (context.isCancelled()) {
            return ToolResult.failure(callId, "Cancelled before start");
        }

        Future<ToolResult> future = TOOL_EXECUTOR.submit(() -> {
            ToolResult r = tool.execute(args, context);
            return r != null ? r : ToolResult.failure(callId, "Tool returned null result");
        });

        try {
            ToolResult result = future.get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Normalise: always propagate the correct callId
            ToolResult normalised = ensureCallId(result, callId);

            if (normalised.isSuccess()) {
                ToolTelemetry.getInstance().recordSuccess(name, token);
            } else {
                ToolTelemetry.getInstance().recordFailure(name, normalised.getError(), token);
            }
            return normalised;

        } catch (TimeoutException e) {
            future.cancel(true);
            String msg = "Tool '" + name + "' timed out after " + (TOOL_TIMEOUT_MS / 1000) + "s";
            ToolTelemetry.getInstance().recordFailure(name, "timeout", token);
            return ToolResult.failure(callId, msg);

        } catch (CancellationException e) {
            ToolTelemetry.getInstance().recordFailure(name, "cancelled", token);
            return ToolResult.failure(callId, "Cancelled");

        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String msg = "Tool '" + name + "' threw: " + cause.getMessage();
            ToolTelemetry.getInstance().recordFailure(name, cause.getMessage(), token);
            return ToolResult.failure(callId, msg);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            ToolTelemetry.getInstance().recordFailure(name, "interrupted", token);
            return ToolResult.failure(callId, "Interrupted");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ToolResult ensureCallId(ToolResult r, String callId) {
        if (r.getToolCallId() != null && !r.getToolCallId().isEmpty()) return r;
        return new ToolResult(callId, r.isSuccess(), r.getOutput(), r.getError());
    }
}
