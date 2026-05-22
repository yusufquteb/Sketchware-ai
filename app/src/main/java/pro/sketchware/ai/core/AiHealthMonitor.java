package pro.sketchware.ai.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * Lightweight AI health monitor.
 *
 * <p>Tracks per-provider metrics (request count, success rate, average latency)
 * and exposes a summary for display in the AI settings health dashboard.
 *
 * <p>Thread-safe singleton. All callbacks fire on the main thread.
 */
public final class AiHealthMonitor {

    private static final String TAG = "AiHealthMonitor";

    private static volatile AiHealthMonitor instance;

    @NonNull
    public static AiHealthMonitor getInstance() {
        if (instance == null) {
            synchronized (AiHealthMonitor.class) {
                if (instance == null) instance = new AiHealthMonitor();
            }
        }
        return instance;
    }

    // ── Per-provider metrics ──────────────────────────────────────────────────

    public static final class ProviderMetrics {
        public final AtomicInteger totalRequests   = new AtomicInteger(0);
        public final AtomicInteger successRequests = new AtomicInteger(0);
        public final AtomicInteger failedRequests  = new AtomicInteger(0);
        /** Running total latency in ms for averaging. */
        final AtomicLong    totalLatencyMs  = new AtomicLong(0L);
        /** Timestamp of the last successful request. 0 = never. */
        public final AtomicLong    lastSuccessMs   = new AtomicLong(0L);
        /** Timestamp of the last failure. 0 = never. */
        public final AtomicLong    lastFailureMs   = new AtomicLong(0L);

        /** Returns the success rate as a 0–100 integer. */
        public int successRatePercent() {
            int total = totalRequests.get();
            if (total == 0) return -1;
            return (successRequests.get() * 100) / total;
        }

        /** Returns average latency in ms, or -1 if no successful requests. */
        public long averageLatencyMs() {
            int successes = successRequests.get();
            if (successes == 0) return -1L;
            return totalLatencyMs.get() / successes;
        }

        /** Returns a human-readable status label. */
        @NonNull
        public String statusLabel() {
            int rate = successRatePercent();
            if (rate < 0)   return "No data";
            if (rate >= 90) return "Healthy";
            if (rate >= 60) return "Degraded";
            return "Unhealthy";
        }
    }

    private final ConcurrentHashMap<String, ProviderMetrics> metrics =
            new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Listener notified when any metric changes. */
    @Nullable
    private volatile HealthListener listener;

    private AiHealthMonitor() {}

    // ── Recording API ─────────────────────────────────────────────────────────

    /**
     * Records the start of a request. Returns a token to pass to
     * {@link #recordSuccess} or {@link #recordFailure}.
     */
    public long recordRequestStart(@NonNull AiProvider provider) {
        metricsFor(provider).totalRequests.incrementAndGet();
        return System.currentTimeMillis();
    }

    /** Records a successful completion. {@code startToken} from {@link #recordRequestStart}. */
    public void recordSuccess(@NonNull AiProvider provider, long startToken) {
        ProviderMetrics m = metricsFor(provider);
        long latency = System.currentTimeMillis() - startToken;
        m.successRequests.incrementAndGet();
        m.totalLatencyMs.addAndGet(latency);
        m.lastSuccessMs.set(System.currentTimeMillis());
        Log.d(TAG, "[" + provider.name() + "] success latency=" + latency + "ms"
                + " rate=" + m.successRatePercent() + "%");
        notifyListener();
    }

    /** Records a failure. */
    public void recordFailure(@NonNull AiProvider provider, @Nullable AiError error) {
        ProviderMetrics m = metricsFor(provider);
        m.failedRequests.incrementAndGet();
        m.lastFailureMs.set(System.currentTimeMillis());
        Log.d(TAG, "[" + provider.name() + "] failure"
                + (error != null ? " category=" + error.category : "")
                + " rate=" + m.successRatePercent() + "%");
        notifyListener();
    }

    // ── Query API ─────────────────────────────────────────────────────────────

    /** Returns current metrics for a provider (creates empty entry if first access). */
    @NonNull
    public ProviderMetrics getMetrics(@NonNull AiProvider provider) {
        return metricsFor(provider);
    }

    /** Returns true if the provider has any recorded data. */
    public boolean hasData(@NonNull AiProvider provider) {
        return metricsFor(provider).totalRequests.get() > 0;
    }

    /**
     * Builds a multi-line diagnostic string for display in settings / debug panel.
     * Only includes providers that have metrics data.
     */
    @NonNull
    public String buildDiagnosticReport(@NonNull Context context) {
        AiPreferences prefs = AiPreferences.getInstance(context);
        StringBuilder sb = new StringBuilder();
        sb.append("AI Health Report\n");
        sb.append("════════════════\n");
        for (AiProvider provider : AiProvider.values()) {
            ProviderMetrics m = metrics.get(provider.name());
            if (m == null || m.totalRequests.get() == 0) continue;

            sb.append("\n").append(provider.getDisplayName()).append("\n");
            sb.append("  Status:   ").append(m.statusLabel()).append("\n");
            sb.append("  Requests: ").append(m.totalRequests.get())
              .append(" (✓").append(m.successRequests.get())
              .append(" ✗").append(m.failedRequests.get()).append(")\n");
            long avgMs = m.averageLatencyMs();
            if (avgMs >= 0) {
                sb.append("  Avg latency: ").append(avgMs).append("ms\n");
            }
            long cbCooldown = CircuitBreaker.getInstance().getRemainingCooldownMs(provider.name());
            if (cbCooldown > 0) {
                sb.append("  ⚠️ Circuit open — cooldown ").append(cbCooldown / 1000).append("s\n");
            }
        }
        if (sb.length() == "AI Health Report\n════════════════\n".length()) {
            sb.append("\nNo requests recorded yet.");
        }
        return sb.toString();
    }

    /** Resets all metrics (e.g. on settings reset). */
    public void reset() {
        metrics.clear();
        CircuitBreaker.getInstance().resetAll();
        Log.d(TAG, "All health metrics reset");
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    /** Callback interface for real-time metric updates. */
    public interface HealthListener {
        void onMetricsUpdated();
    }

    public void setListener(@Nullable HealthListener l) {
        this.listener = l;
    }

    private void notifyListener() {
        HealthListener l = listener;
        if (l != null) mainHandler.post(l::onMetricsUpdated);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @NonNull
    private ProviderMetrics metricsFor(@NonNull AiProvider provider) {
        return metrics.computeIfAbsent(provider.name(), k -> new ProviderMetrics());
    }
}
