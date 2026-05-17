package pro.sketchware.ai.chat.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * TypingIndicatorAnimator — Manages the three-dot pulse animation
 * displayed in the chat when the AI is generating a response.
 *
 * <p>Usage:
 * <pre>
 * TypingIndicatorAnimator anim = new TypingIndicatorAnimator(dot1, dot2, dot3);
 * anim.start();  // when AI starts responding
 * anim.stop();   // when AI response is complete
 * </pre>
 *
 * <p>Animation: each dot pulses alpha 0.4 → 1.0 → 0.4 with a 150ms stagger
 * between dots, creating a wave effect. Total loop duration: ~1200ms.
 *
 * <p>Performance: uses {@link ObjectAnimator} on the alpha property.
 * All animators are cancelled on {@link #stop()} to prevent leaks.
 */
public class TypingIndicatorAnimator {

    private static final long DOT_ANIM_DURATION = 600L;
    private static final long DOT_STAGGER_MS    = 150L;
    private static final float ALPHA_DIM         = 0.3f;
    private static final float ALPHA_BRIGHT      = 1.0f;

    @NonNull  private final View dot1;
    @NonNull  private final View dot2;
    @NonNull  private final View dot3;

    @Nullable private AnimatorSet animatorSet;
    private boolean isRunning = false;

    public TypingIndicatorAnimator(
            @NonNull View dot1,
            @NonNull View dot2,
            @NonNull View dot3
    ) {
        this.dot1 = dot1;
        this.dot2 = dot2;
        this.dot3 = dot3;
    }

    /**
     * Starts the looping dot pulse animation.
     * Safe to call multiple times — no-op if already running.
     */
    public void start() {
        if (isRunning) return;
        isRunning = true;

        // Reset all dots to dim state
        dot1.setAlpha(ALPHA_DIM);
        dot2.setAlpha(ALPHA_DIM);
        dot3.setAlpha(ALPHA_DIM);

        animatorSet = buildAnimatorSet();
        animatorSet.start();
    }

    /**
     * Stops the animation and resets dot alphas.
     * Safe to call even if not running.
     */
    public void stop() {
        isRunning = false;
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        dot1.setAlpha(ALPHA_DIM);
        dot2.setAlpha(ALPHA_DIM);
        dot3.setAlpha(ALPHA_DIM);
    }

    public boolean isRunning() {
        return isRunning;
    }

    // ─── Animation building ───────────────────────────────────────────────────

    @NonNull
    private AnimatorSet buildAnimatorSet() {
        ObjectAnimator anim1 = buildDotAnimator(dot1, 0L);
        ObjectAnimator anim2 = buildDotAnimator(dot2, DOT_STAGGER_MS);
        ObjectAnimator anim3 = buildDotAnimator(dot3, DOT_STAGGER_MS * 2);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(anim1, anim2, anim3);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Loop while still running
                if (isRunning) {
                    animation.start();
                }
            }
        });
        return set;
    }

    @NonNull
    private ObjectAnimator buildDotAnimator(@NonNull View dot, long startDelay) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(dot, "alpha", ALPHA_DIM, ALPHA_BRIGHT, ALPHA_DIM);
        animator.setDuration(DOT_ANIM_DURATION);
        animator.setStartDelay(startDelay);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        return animator;
    }
}
