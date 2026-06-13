package pro.sketchware.ai.core;

import android.util.Log;
import pro.sketchware.ai.utils.AiLog;

import androidx.annotation.NonNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-provider circuit breaker.
 *
 * <p>Prevents the system from hammering a failing provider with repeated requests.
 * After {@link #FAILURE_THRESHOLD} consecutive failures, the circuit opens and
 * the provider is skipped for {@link #COOLDOWN_MS}. It resets after a success.
 *
 * <p>Thread-safe. Singleton via {@link #getInstance()}.
 */
public final class CircuitBreaker {

    private static final String TAG = "CircuitBreaker";

    /** Number of consecutive failures before opening the circuit. */
    private static final int FAILURE_THRESHOLD = 3;

    /** How long to wait (ms) before allowing the provider through again. */
    private static final long COOLDOWN_MS = 60_000L; // 60 seconds

    private static volatile CircuitBreaker instance;

    @NonNull
    public static CircuitBreaker getInstance() {
        if (instance == null) {
            synchronized (CircuitBreaker.class) {
                if (instance == null) instance = new CircuitBreaker();
            }
        }
        return instance;
    }

    // ── Per-provider state ────────────────────────────────────────────────────

    private static final class ProviderState {
        final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        final AtomicLong    openedAt            = new AtomicLong(0L);
    }

    private final ConcurrentHashMap<String, ProviderState> states = new ConcurrentHashMap<>();

    private CircuitBreaker() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if the circuit is CLOSED (provider allowed through).
     * Returns false if the circuit is OPEN (provider is in cooldown).
     *
     * @param providerKey a unique key for this provider (e.g. AiProvider.name())
     */
    public boolean isAllowed(@NonNull String providerKey) {
        ProviderState state = getOrCreate(providerKey);
        if (state.consecutiveFailures.get() < FAILURE_THRESHOLD) {
            return true;
        }
        long openedAt = state.openedAt.get();
        if (openedAt == 0L) return true;

        long elapsed = System.currentTimeMillis() - openedAt;
        if (elapsed >= COOLDOWN_MS) {
            // Half-open: allow one probe through to check if provider recovered
            AiLog.d(TAG, "[" + providerKey + "] Circuit half-open after " + (elapsed / 1000) + "s cooldown");
            return true;
        }
        AiLog.d(TAG, "[" + providerKey + "] Circuit OPEN — skipping (cooldown "
                + ((COOLDOWN_MS - elapsed) / 1000) + "s remaining)");
        return false;
    }

    /**
     * Records a successful response for this provider.
     * Resets the failure counter and closes the circuit.
     */
    public void recordSuccess(@NonNull String providerKey) {
        ProviderState state = getOrCreate(providerKey);
        int prev = state.consecutiveFailures.getAndSet(0);
        state.openedAt.set(0L);
        if (prev > 0) {
            AiLog.d(TAG, "[" + providerKey + "] Circuit closed after recovery (was " + prev + " failures)");
        }
    }

    /**
     * Records a failure for this provider.
     * Opens the circuit after {@link #FAILURE_THRESHOLD} consecutive failures.
     */
    public void recordFailure(@NonNull String providerKey) {
        ProviderState state = getOrCreate(providerKey);
        int count = state.consecutiveFailures.incrementAndGet();
        if (count >= FAILURE_THRESHOLD && state.openedAt.get() == 0L) {
            state.openedAt.set(System.currentTimeMillis());
            Log.w(TAG, "[" + providerKey + "] Circuit OPENED after " + count
                    + " failures — cooldown " + (COOLDOWN_MS / 1000) + "s");
        }
    }

    /**
     * Returns the current failure count for a provider.
     */
    public int getFailureCount(@NonNull String providerKey) {
        return getOrCreate(providerKey).consecutiveFailures.get();
    }

    /**
     * Returns remaining cooldown in ms for a provider, or 0 if circuit is closed.
     */
    public long getRemainingCooldownMs(@NonNull String providerKey) {
        ProviderState state = getOrCreate(providerKey);
        if (state.consecutiveFailures.get() < FAILURE_THRESHOLD) return 0L;
        long openedAt = state.openedAt.get();
        if (openedAt == 0L) return 0L;
        long elapsed = System.currentTimeMillis() - openedAt;
        return Math.max(0L, COOLDOWN_MS - elapsed);
    }

    /** Manually resets a provider's circuit (e.g. user changes API key in settings). */
    public void reset(@NonNull String providerKey) {
        ProviderState state = getOrCreate(providerKey);
        state.consecutiveFailures.set(0);
        state.openedAt.set(0L);
        AiLog.d(TAG, "[" + providerKey + "] Circuit manually reset");
    }

    /** Resets all circuits (e.g. on app startup). */
    public void resetAll() {
        states.clear();
        AiLog.d(TAG, "All circuits reset");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @NonNull
    private ProviderState getOrCreate(@NonNull String key) {
        ProviderState existing = states.get(key);
        if (existing != null) return existing;
        ProviderState newState = new ProviderState();
        ProviderState prev = states.putIfAbsent(key, newState);
        return prev != null ? prev : newState;
    }
}
