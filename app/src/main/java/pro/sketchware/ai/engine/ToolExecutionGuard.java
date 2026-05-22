package pro.sketchware.ai.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * Wraps {@link AgentTool#execute} with:
 * <ol>
 *   <li>A hard 30-second timeout per execution attempt.</li>
 *   <li>One automatic retry (500 ms backoff) for transient errors
 *       (timeout, network, connection reset).</li>
 *   <li>Telemetry recording on every outcome path.</li>
 * </ol>
 *
 * <p>Uses a dedicated daemon thread pool entirely separate from
 * {@link AgentExecutor}'s single executor thread, preventing deadlocks while
 * allowing {@code Future.get(timeout)} to enforce per-tool time limits.
 *
 * <p>All public methods are thread-safe and never throw.
 */
public final class ToolExecutionGuard {

    /** Hard timeout for a single tool execution attempt. */
    public static final long TOOL_TIMEOUT_MS = 30_000L;

    /** Backoff before the single retry on transient failures. */
    private static final long RETRY_BACKOFF_MS = 500L;

    private static final ExecutorService TOOL_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-tool-exec");
        t.setDaemon(true);
        return t;
    });

    private ToolExecutionGuard() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Executes {@code tool} with the given arguments, timeout, optional retry,
     * and telemetry. Never throws.
     *
     * @param tool    the tool to execute
     * @param args    parsed JSON arguments
     * @param context execution context (project IDs, cancellation, progress)
     * @param callId  tool-call ID for the returned ToolResult
     * @return non-null ToolResult — success or descriptive failure
     */
    @NonNull
    public static ToolResult executeWithTimeout(
            @NonNull AgentTool tool,
            @NonNull JsonObject args,
            @NonNull ToolContext context,
            @NonNull String callId) {

        if (context.isCancelled()) {
            return ToolResult.failure(callId, "Cancelled before start");
        }

        String name  = tool.getName();
        long   token = ToolTelemetry.getInstance().recordStart(name);

        ToolResult result = attempt(tool, args, context, callId, name, token);

        // ── Single retry for transient errors ─────────────────────────────────
        if (!result.isSuccess() && isTransient(result.getError()) && !context.isCancelled()) {
            try { Thread.sleep(RETRY_BACKOFF_MS); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return result;
            }
            // Reset telemetry token so retry duration is measured independently
            long retryToken = ToolTelemetry.getInstance().recordStart(name);
            result = attempt(tool, args, context, callId, name, retryToken);
        }

        return result;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @NonNull
    private static ToolResult attempt(AgentTool tool, JsonObject args,
                                       ToolContext context, String callId,
                                       String name, long token) {
        Future<ToolResult> future = TOOL_EXECUTOR.submit(() -> {
            ToolResult r = tool.execute(args, context);
            return r != null ? r : ToolResult.failure(callId, "Tool returned null result");
        });

        try {
            ToolResult r = future.get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            ToolResult norm = ensureCallId(r, callId);
            if (norm.isSuccess()) {
                ToolTelemetry.getInstance().recordSuccess(name, token);
            } else {
                ToolTelemetry.getInstance().recordFailure(name, norm.getError(), token);
            }
            return norm;

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

    /** Returns true for errors that are worth retrying (network / timeout, not logic errors). */
    private static boolean isTransient(@Nullable String error) {
        if (error == null) return false;
        String low = error.toLowerCase(java.util.Locale.US);
        return low.contains("timeout")
                || low.contains("timed out")
                || low.contains("connection")
                || low.contains("network")
                || low.contains("socket")
                || low.contains("econnreset")
                || low.contains("unreachable");
    }

    private static ToolResult ensureCallId(ToolResult r, String callId) {
        if (r.getToolCallId() != null && !r.getToolCallId().isEmpty()) return r;
        return new ToolResult(callId, r.isSuccess(), r.getOutput(), r.getError());
    }
}
