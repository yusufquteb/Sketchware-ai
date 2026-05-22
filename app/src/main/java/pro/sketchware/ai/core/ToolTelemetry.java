package pro.sketchware.ai.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-tool execution metrics — the tool counterpart to {@link AiHealthMonitor}.
 *
 * <p>Tracks total calls, failed calls, and cumulative duration for each tool.
 * Thread-safe; metrics are updated from the tool-executor thread and read
 * from any thread (typically the UI thread for the diagnostic report).
 */
public final class ToolTelemetry {

    private static final ToolTelemetry INSTANCE = new ToolTelemetry();

    public static ToolTelemetry getInstance() { return INSTANCE; }

    private ToolTelemetry() {}

    // ── Per-tool stats ────────────────────────────────────────────────────────

    private static final class ToolStats {
        final AtomicLong totalCalls    = new AtomicLong();
        final AtomicLong failedCalls   = new AtomicLong();
        final AtomicLong totalDurationMs = new AtomicLong();
        volatile long   lastCallMs;
        volatile String lastError;
    }

    private final ConcurrentHashMap<String, ToolStats> stats = new ConcurrentHashMap<>();

    // ── Listener ──────────────────────────────────────────────────────────────

    public interface TelemetryListener { void onUpdated(); }

    private volatile TelemetryListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setListener(@Nullable TelemetryListener l) { this.listener = l; }

    private void notifyListener() {
        TelemetryListener l = listener;
        if (l != null) mainHandler.post(l::onUpdated);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Records the start of a tool call and returns a start token for timing. */
    public long recordStart(@NonNull String toolName) {
        stats.computeIfAbsent(toolName, k -> new ToolStats())
             .lastCallMs = System.currentTimeMillis();
        return System.currentTimeMillis();
    }

    /** Records a successful tool execution. */
    public void recordSuccess(@NonNull String toolName, long startToken) {
        ToolStats s = stats.computeIfAbsent(toolName, k -> new ToolStats());
        s.totalCalls.incrementAndGet();
        s.totalDurationMs.addAndGet(System.currentTimeMillis() - startToken);
        notifyListener();
    }

    /** Records a failed tool execution. */
    public void recordFailure(@NonNull String toolName, @Nullable String error, long startToken) {
        ToolStats s = stats.computeIfAbsent(toolName, k -> new ToolStats());
        s.totalCalls.incrementAndGet();
        s.failedCalls.incrementAndGet();
        s.totalDurationMs.addAndGet(System.currentTimeMillis() - startToken);
        s.lastError = error;
        notifyListener();
    }

    /** Resets all recorded metrics. */
    public void reset() {
        stats.clear();
        notifyListener();
    }

    /**
     * Builds a formatted diagnostic report sorted by total call count (descending).
     * Returns a short "No tool calls recorded yet." if no data.
     */
    @NonNull
    public String buildReport() {
        if (stats.isEmpty()) return "No tool calls recorded yet.";

        // Sort by total calls descending
        List<Map.Entry<String, ToolStats>> entries = new ArrayList<>(stats.entrySet());
        entries.sort((a, b) -> Long.compare(
                b.getValue().totalCalls.get(),
                a.getValue().totalCalls.get()));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "%-28s %5s %5s %6s %s\n",
                "TOOL", "CALLS", "FAILS", "AVG ms", "LAST ERROR"));
        sb.append("─".repeat(72)).append('\n');

        for (Map.Entry<String, ToolStats> e : entries) {
            ToolStats s = e.getValue();
            long total   = s.totalCalls.get();
            long failed  = s.failedCalls.get();
            long avgMs   = total > 0 ? s.totalDurationMs.get() / total : 0;
            String err   = s.lastError != null
                    ? s.lastError.substring(0, Math.min(s.lastError.length(), 28))
                    : "";
            sb.append(String.format(Locale.US, "%-28s %5d %5d %6d  %s\n",
                    e.getKey(), total, failed, avgMs, err));
        }
        return sb.toString();
    }

    /** Returns all tool names that have been called at least once. */
    @NonNull
    public List<String> getTrackedTools() {
        return Collections.list(stats.keys());
    }
}
