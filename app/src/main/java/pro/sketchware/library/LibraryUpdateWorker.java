package pro.sketchware.library;

import dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity;
import dev.aldi.sayuti.editor.manage.LocalLibraryItem;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Proposal 24 — Background update checker using AlarmManager.
 *
 * Does NOT require WorkManager dependency.
 * Uses a BroadcastReceiver fired once daily to check for library updates
 * and post a notification if any are found.
 *
 * Schedule with: {@link #scheduleDaily(Context)}
 * Cancel with:   {@link #cancelSchedule(Context)}
 */
public class LibraryUpdateWorker extends BroadcastReceiver {

    private static final String TAG        = "LibUpdateWorker";
    private static final String CHANNEL_ID = "local_lib_updates";
    private static final int    NOTIF_ID   = 0xD4D5;
    private static final int    ALARM_RC   = 0xD4D6;

    private static final String ACTION_CHECK =
            "pro.sketchware.library.ACTION_CHECK_LIB_UPDATES";

    // ── AlarmManager schedule ─────────────────────────────────────────────────

    /** Schedule a daily background check. Safe to call multiple times. */
    public static void scheduleDaily(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(ctx);

        // Fire 6 hours from now, then every 24 hours
        long triggerAt = System.currentTimeMillis()
                + TimeUnit.HOURS.toMillis(6);

        try {
            am.setInexactRepeating(
                    AlarmManager.RTC,
                    triggerAt,
                    AlarmManager.INTERVAL_DAY,
                    pi);
            Log.i(TAG, "Scheduled daily library update check");
        } catch (Exception e) {
            Log.w(TAG, "Failed to schedule alarm", e);
        }
    }

    /** Cancel the scheduled check */
    public static void cancelSchedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(buildPendingIntent(ctx));
            Log.i(TAG, "Cancelled library update check");
        }
    }

    private static PendingIntent buildPendingIntent(Context ctx) {
        Intent intent = new Intent(ctx, LibraryUpdateWorker.class);
        intent.setAction(ACTION_CHECK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(ctx, ALARM_RC, intent, flags);
    }

    // ── BroadcastReceiver ─────────────────────────────────────────────────────

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION_CHECK.equals(intent.getAction())) return;
        Log.i(TAG, "Running library update check");

        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            try {
                List<LocalLibraryItem> libs = LocalLibraryManager.getAllLibraries(false);
                if (libs.isEmpty()) return;

                List<LocalLibraryItem> checkable = new ArrayList<>();
                for (LocalLibraryItem lib : libs) {
                    if (!lib.pinned) checkable.add(lib);
                }

                final List<LocalLibraryItem> withUpdates = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(1);

                LibraryUpdateManager.checkForUpdates(checkable,
                        new LibraryUpdateManager.CheckCallback() {
                            @Override
                            public void onChecked(LocalLibraryItem item,
                                                  boolean updateAvailable,
                                                  String latestVersion) {}

                            @Override
                            public void onAllChecked(List<LocalLibraryItem> results) {
                                for (LocalLibraryItem item : results) {
                                    if (item.isUpdateAvailable) withUpdates.add(item);
                                }
                                latch.countDown();
                            }
                        });

                if (!latch.await(3, TimeUnit.MINUTES)) {
                    Log.w(TAG, "Version check timed out");
                    return;
                }

                if (!withUpdates.isEmpty()) {
                    showUpdateNotification(ctx, withUpdates);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "Background check failed", e);
            }
        });
        exec.shutdown();
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private static void showUpdateNotification(Context ctx, List<LocalLibraryItem> updates) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Library Updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Notifies when local libraries have updates");
            nm.createNotificationChannel(ch);
        }

        String title = updates.size() == 1
                ? updates.get(0).name + " has an update"
                : updates.size() + " libraries have updates";

        StringBuilder body = new StringBuilder();
        int shown = Math.min(updates.size(), 4);
        for (int i = 0; i < shown; i++) {
            LocalLibraryItem lib = updates.get(i);
            body.append("• ").append(lib.name)
                .append("  ").append(lib.version)
                .append(" → ").append(lib.latestVersion).append("\n");
        }
        if (updates.size() > shown) {
            body.append("…and ").append(updates.size() - shown).append(" more");
        }

        Intent tapIntent = new Intent(ctx, ManageLocalLibraryActivity.class);
        tapIntent.putExtra("sc_id", "system");
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, tapIntent, piFlags);

        Notification notif = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(body.toString().trim())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(body.toString().trim()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();

        nm.notify(NOTIF_ID, notif);
        Log.i(TAG, "Posted update notification for " + updates.size() + " libs");
    }
}
