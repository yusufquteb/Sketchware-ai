package pro.sketchware;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.tools.CollectErrorActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import com.google.android.material.color.DynamicColors;

import pro.sketchware.activities.settings.PermissionsActivity;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.utility.theme.ThemeManager;

public class SketchApplication extends Application {
    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    // ── App-exit detection (no lifecycle-process dependency) ───────────────────
    // Standard started-activity-count pattern: every Activity stop that drops the count to
    // zero schedules a short-delayed check: if still zero after the delay, no new Activity
    // started in the meantime (a normal Activity-to-Activity transition always overlaps —
    // the new one starts before the old one stops), so the whole app really went to the
    // background/was exited, not just navigated within.
    private int startedActivityCount = 0;
    private final Handler appExitHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingExitCheck;
    private static final long APP_EXIT_CHECK_DELAY_MS = 700;

    private void registerAppExitDetector() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(@NonNull Activity activity) {
                startedActivityCount++;
                if (pendingExitCheck != null) {
                    appExitHandler.removeCallbacks(pendingExitCheck);
                    pendingExitCheck = null;
                }
            }

            @Override public void onActivityStopped(@NonNull Activity activity) {
                startedActivityCount--;
                if (startedActivityCount <= 0) {
                    pendingExitCheck = () -> {
                        if (startedActivityCount <= 0) {
                            onAppExited();
                        }
                    };
                    appExitHandler.postDelayed(pendingExitCheck, APP_EXIT_CHECK_DELAY_MS);
                }
            }

            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    /**
     * Fires once the app has been fully backgrounded/exited (all Activities stopped, no new
     * one started within {@link #APP_EXIT_CHECK_DELAY_MS}). Clears the floating per-screen AI
     * assistant's (AiAssistantBottomSheet) conversation history — those are ad-hoc, page-scoped
     * chats not meant to persist indefinitely, unlike the dedicated Agent tab's own history
     * (untouched here; see ConversationManager#deleteAllAssistantSheetConversations).
     */
    private void onAppExited() {
        new Thread(() -> {
            try {
                new ConversationManager(this).deleteAllAssistantSheetConversations();
            } catch (Throwable ignored) {
            }
        }, "assistant-chat-cleanup").start();
    }

    @Override
    public void onCreate() {
        mApplicationContext = getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
                Intent intent = new Intent(getApplicationContext(), CollectErrorActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("error", Log.getStackTraceString(throwable));
                startActivity(intent);
                Process.killProcess(Process.myPid());
                System.exit(1);
            }
        });
        super.onCreate();
        registerAppExitDetector();
        // Apply dynamic colors (Material You) to every Activity automatically.
        // Required when any theme parent uses Theme.Material3Expressive.DynamicColors.*
        // Without this, colorAmber and other expressive tokens are null → crash on SDK 31+.
        DynamicColors.applyToActivitiesIfAvailable(this);
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
            }
            // Firebase Messaging auto-init is disabled in the manifest so its SDK
            // doesn't request POST_NOTIFICATIONS on its own as soon as an Activity
            // starts. It's enabled manually here, but only once the user has been
            // through the onboarding Permissions screen — on first run it stays off
            // until PermissionsActivity.finishOnboarding() turns it on.
            if (!PermissionsActivity.shouldShowOnFirstRun(this)) {
                FirebaseMessaging.getInstance().setAutoInitEnabled(true);
            }
        } catch (Throwable ignored) {
        }
        ThemeManager.applyTheme(this, ThemeManager.getCurrentTheme(this));

        // Seed the AI knowledge store from assets/ai_system_prompt.txt + agent_protocols.json +
        // AiCapabilityManifest, so both the offline (4096-token) and online assistant can pull
        // relevance-ranked, token-budgeted slices of the project rules/environment instead of
        // never receiving them (offline) or paying their full ~5,200-token cost on every request
        // (online). One-shot and idempotent — see KnowledgeSeeder#seedIfNeeded. Runs off the main
        // thread since it does string parsing + SQLite/pref writes; failure here must never block
        // app startup, so all exceptions are swallowed the same way the try block above does.
        new Thread(() -> {
            try {
                pro.sketchware.ai.offline.knowledge.KnowledgeSeeder.seedIfNeeded(
                        this, new pro.sketchware.ai.offline.knowledge.KnowledgeStore(this));
            } catch (Throwable ignored) {
            }
        }, "ai-knowledge-seed").start();
    }
}
