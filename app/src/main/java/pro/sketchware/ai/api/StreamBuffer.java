package pro.sketchware.ai.api;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive chunk buffer for streaming AI responses.
 *
 * <p>Collects incoming text chunks from the OkHttp I/O thread and flushes
 * them to the UI thread in batches at ~20 fps (50 ms intervals). This
 * reduces RecyclerView invalidation overhead on low-end Android devices
 * without any perceptible latency increase for the user.
 *
 * <p>Also exposes {@link #lastChunkMs} so callers can implement heartbeat
 * stall detection: if the stream is open but no chunks have arrived for
 * {@code STALL_TIMEOUT_MS}, the caller can treat the stream as frozen.
 *
 * <p>Thread-safe. All flush callbacks are posted to the main thread.
 */
public final class StreamBuffer {

    /** Max flush frequency: 50 ms ≈ 20 fps — sufficient for visible text streaming. */
    private static final long FLUSH_INTERVAL_MS = 50L;

    /**
     * Notified on the main thread with the batched text chunk when the buffer flushes.
     */
    public interface FlushCallback {
        void onFlush(@NonNull String batchedText);
    }

    private final FlushCallback callback;
    private final Handler        mainHandler  = new Handler(Looper.getMainLooper());
    private final StringBuilder  buffer       = new StringBuilder();
    private final AtomicBoolean  flushPending = new AtomicBoolean(false);

    /**
     * Timestamp of the last received chunk (updated from any thread).
     * Used by the caller to detect heartbeat stalls.
     */
    public final AtomicLong lastChunkMs = new AtomicLong(System.currentTimeMillis());

    public StreamBuffer(@NonNull FlushCallback callback) {
        this.callback = callback;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Appends a chunk to the buffer. Called from the OkHttp I/O thread.
     * Schedules a flush after {@link #FLUSH_INTERVAL_MS} if one is not already pending.
     */
    public void append(@NonNull String chunk) {
        if (chunk.isEmpty()) return;
        lastChunkMs.set(System.currentTimeMillis());
        synchronized (buffer) {
            buffer.append(chunk);
        }
        if (flushPending.compareAndSet(false, true)) {
            mainHandler.postDelayed(this::doFlush, FLUSH_INTERVAL_MS);
        }
    }

    /**
     * Forces an immediate flush of any buffered content.
     * Call this when the stream completes so the final partial batch is delivered
     * before the {@code onComplete} callback fires.
     *
     * <p>Safe to call from any thread.
     */
    public void flushNow() {
        mainHandler.removeCallbacksAndMessages(null);
        flushPending.set(false);
        doFlush();
    }

    /**
     * Discards buffered content and cancels any pending flush.
     * Call this on cancellation to prevent stale chunks reaching the UI.
     *
     * <p>Safe to call from any thread.
     */
    public void cancel() {
        mainHandler.removeCallbacksAndMessages(null);
        flushPending.set(false);
        synchronized (buffer) {
            buffer.setLength(0);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void doFlush() {
        String batch;
        synchronized (buffer) {
            if (buffer.length() == 0) return;
            batch = buffer.toString();
            buffer.setLength(0);
        }
        callback.onFlush(batch);
    }
}
