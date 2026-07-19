package pro.sketchware.ai.engine;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PulseController — manages the Continue/Cancel checkpoint flow inside the AI pipeline.
 *
 * <p>When the AI agent reaches a major action boundary (e.g. every N tool calls),
 * the engine can pause and show a "pulse" dialog. The user sees:
 *
 * <pre>
 *   [ Plan summary ]
 *   [ Continue (10s) ]  [ Cancel ]
 * </pre>
 *
 * <p>If the user does nothing within {@link #autoSecs} seconds, Continue is selected
 * automatically. If the user taps Cancel, the pipeline is aborted.
 *
 * <p>Usage — from a background thread:
 * <pre>
 *   PulseController pulse = new PulseController(10, uiCallback);
 *
 *   // Blocks the calling thread until user decides or timeout:
 *   boolean shouldContinue = pulse.await("AI will now modify 3 files…");
 *   if (!shouldContinue) throw new CancelledException();
 * </pre>
 */
public final class PulseController {

    /** Callback for showing the pulse UI on the main thread. */
    public interface PulseUiCallback {
        /**
         * Called on the main thread to show the pulse dialog.
         *
         * @param planSummary    brief description of what the AI plans to do next
         * @param countdown      initial countdown seconds (e.g. 10)
         * @param onCountdownTick called every second with remaining seconds — update the button label
         * @param onContinue     call this when user taps Continue (or countdown hits 0)
         * @param onCancel       call this when user taps Cancel
         */
        void showPulse(
                String planSummary,
                int countdown,
                TickCallback onCountdownTick,
                Runnable onContinue,
                Runnable onCancel);

        /** Called on main thread to dismiss the pulse UI after decision. */
        void dismissPulse();
    }

    /** Tick callback for live countdown display. */
    public interface TickCallback {
        /** @param secondsRemaining seconds left before auto-continue */
        void onTick(int secondsRemaining);
    }

    private final int             autoSecs;
    private final PulseUiCallback uiCallback;
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    /**
     * @param autoSecs   seconds before auto-continue (recommended: 10)
     * @param uiCallback the UI-layer callback (must be set before calling await)
     */
    public PulseController(int autoSecs, PulseUiCallback uiCallback) {
        this.autoSecs   = autoSecs;
        this.uiCallback = uiCallback;
    }

    /**
     * Pauses the calling (background) thread and shows a Continue/Cancel pulse dialog.
     * Blocks until the user decides OR the countdown expires.
     *
     * @param planSummary human-readable description of what happens next
     * @return {@code true} → continue pipeline, {@code false} → cancel/abort
     */
    public boolean await(String planSummary) {
        if (uiCallback == null) return true; // no UI hooked up — always continue

        CountDownLatch latch    = new CountDownLatch(1);
        AtomicBoolean  decision = new AtomicBoolean(true); // default = continue

        // Countdown timer runs on main thread
        CountDownTimer[] timerRef = {null};

        Runnable onContinue = () -> {
            decision.set(true);
            if (timerRef[0] != null) timerRef[0].cancel();
            mainHandler.post(() -> uiCallback.dismissPulse());
            latch.countDown();
        };

        Runnable onCancel = () -> {
            decision.set(false);
            if (timerRef[0] != null) timerRef[0].cancel();
            mainHandler.post(() -> uiCallback.dismissPulse());
            latch.countDown();
        };

        // Post show to main thread
        mainHandler.post(() -> {
            CountDownTimer timer = new CountDownTimer(autoSecs * 1000L, 1000L) {
                @Override public void onTick(long ms) {
                    int secs = (int) (ms / 1000) + 1;
                    // Notify UI to update button label
                    uiCallback.showPulse(planSummary, secs,
                            remaining -> { /* already shown, just tick */ },
                            onContinue, onCancel);
                }
                @Override public void onFinish() {
                    // Auto-continue when countdown hits 0
                    onContinue.run();
                }
            };
            timerRef[0] = timer;
            uiCallback.showPulse(planSummary, autoSecs,
                    remaining -> { /* handled via onTick */ },
                    onContinue, onCancel);
            timer.start();
        });

        // Block background thread
        try {
            latch.await(autoSecs + 5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return decision.get();
    }

    // ── Static factory for the simplest case ─────────────────────────────────

    /**
     * Creates a PulseController with a 10-second countdown.
     */
    public static PulseController withDefault(PulseUiCallback uiCallback) {
        return new PulseController(10, uiCallback);
    }

    /**
     * Creates a "headless" PulseController that always continues without showing UI.
     * Useful for automated/test flows.
     */
    public static PulseController headless() {
        return new PulseController(0, null);
    }
}
