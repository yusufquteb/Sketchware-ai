package pro.sketchware;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.besome.sketch.tools.CollectErrorActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import com.google.android.material.color.DynamicColors;

import pro.sketchware.activities.settings.PermissionsActivity;
import pro.sketchware.utility.theme.ThemeManager;

public class SketchApplication extends Application {
    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
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
