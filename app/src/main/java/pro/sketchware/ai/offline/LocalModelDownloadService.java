package pro.sketchware.ai.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.ai.activities.AiSettingsActivity;

/**
 * Foreground {@link Service} that owns the actual network transfer for {@link LocalModelCatalog}
 * downloads, so a model download survives the user leaving {@code AiSettingsActivity}, switching
 * apps, or locking the screen — it only stops when the transfer finishes, errors out, or the user
 * explicitly cancels it (from the notification or from the Settings screen).
 *
 * <p><b>Why this exists.</b> Before this class, {@link AiSettingsActivity} called
 * {@link LocalModelDownloader#download} directly. That call itself runs on OkHttp's own
 * dispatcher thread (not the UI thread), so it technically kept running while the Activity was
 * merely backgrounded — but with no foreground service and no notification, Android has no
 * signal this process is doing user-visible work, and can (and eventually will) kill the process
 * to reclaim memory once nothing is in the foreground, silently dropping a multi-GB transfer
 * partway through. Wrapping the same download call in a foreground service with a visible,
 * ongoing notification is what actually earns the "let this keep running" guarantee from the OS.
 *
 * <p><b>What actually changed vs. before.</b> The underlying transfer logic in
 * {@link LocalModelDownloader} (OkHttp call, {@code Range}-header resume, {@code .part} file
 * handling) is completely unchanged — this service is a thin wrapper that (1) calls
 * {@link LocalModelDownloader#download} from inside {@link #onStartCommand}, (2) turns its
 * {@link LocalModelDownloader.DownloadCallback} progress events into a live-updating
 * notification instead of (or in addition to) a UI callback, and (3) exposes pause/resume/cancel
 * as notification actions that map straight onto the pause/cancel methods that already existed
 * on {@link LocalModelDownloader}.
 *
 * <p><b>Multiple concurrent downloads.</b> Each in-flight model gets its own notification (ID
 * derived from {@link LocalModelCatalog#getId()}'s hash, offset from {@link #NOTIFICATION_ID_BASE}
 * so it can't collide with unrelated notification IDs elsewhere in the app), so pausing one
 * model's download doesn't affect another's. The service itself stays alive (in the foreground,
 * via whichever notification was started most recently) as long as {@link #activeModelIds} is
 * non-empty, and calls {@link #stopSelf()} once the last tracked download finishes, errors, or is
 * cancelled — there is deliberately no single "download complete" state for the service as a
 * whole, since with N concurrent downloads the service's job isn't done until all N are.
 *
 * <p><b>Notification actions are {@code PendingIntent}s back into this same service</b> (not a
 * separate {@code BroadcastReceiver}) — {@link #onStartCommand} dispatches on the intent's
 * {@code action} string ({@link #ACTION_PAUSE}/{@link #ACTION_RESUME}/{@link #ACTION_CANCEL}) the
 * same way it dispatches the initial {@link #ACTION_START} that kicks a download off. This keeps
 * all the download-control logic in one class instead of splitting it across a service and a
 * receiver that would both need direct access to {@link LocalModelManager}/
 * {@link LocalModelDownloader}.
 * <p><b>Notification permission.</b> On API 33+ (Android 13+), a user who has not granted
 * {@code POST_NOTIFICATIONS} (requested centrally in {@code PermissionsActivity}) simply won't
 * see this service's notifications — {@link NotificationManager#notify} silently no-ops without
 * that permission rather than throwing. That does not affect the foreground service or the
 * download itself: {@link #startForeground} still succeeds and the transfer still runs to
 * completion; the user just loses the visible progress/pause/stop controls until they grant the
 * permission. This mirrors how {@code AiBackgroundService} already behaves in this codebase.
 */
public class LocalModelDownloadService extends Service {

    private static final String CHANNEL_ID = "ModelDownloadChannel";
    private static final int NOTIFICATION_ID_BASE = 20_000; // offset away from other notif IDs in the app

    public static final String ACTION_START = "pro.sketchware.ai.offline.action.START_DOWNLOAD";
    public static final String ACTION_PAUSE = "pro.sketchware.ai.offline.action.PAUSE_DOWNLOAD";
    public static final String ACTION_RESUME = "pro.sketchware.ai.offline.action.RESUME_DOWNLOAD";
    public static final String ACTION_CANCEL = "pro.sketchware.ai.offline.action.CANCEL_DOWNLOAD";
    public static final String EXTRA_MODEL_ID = "model_id";

    /** Model IDs this service is currently tracking a notification for (active or paused). */
    private static final Map<String, Boolean> activeModelIds = new HashMap<>();

    private LocalModelManager modelManager;

    @Override
    public void onCreate() {
        super.onCreate();
        modelManager = new LocalModelManager(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            // Service was restarted by the system (e.g. after process death) with no intent to
            // act on — nothing to resume automatically; the user re-taps "Resume" from Settings,
            // which re-launches this service with a fresh ACTION_START/ACTION_RESUME intent.
            stopSelfIfIdle();
            return START_NOT_STICKY;
        }

        String modelId = intent.getStringExtra(EXTRA_MODEL_ID);
        LocalModelCatalog model = LocalModelCatalog.fromId(modelId);
        if (model == null) {
            stopSelfIfIdle();
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_START:
            case ACTION_RESUME:
                startOrResumeDownload(model);
                break;
            case ACTION_PAUSE:
                LocalModelDownloader.pause(modelManager, model);
                activeModelIds.put(model.getId(), true); // stays tracked, just not transferring
                updateNotification(model, modelManager.getProgressPercent(model), true);
                break;
            case ACTION_CANCEL:
                LocalModelDownloader.cancel(model);
                //noinspection ResultOfMethodCallIgnored
                modelManager.getPartialFile(model).delete();
                modelManager.setState(model, LocalModelState.NOT_DOWNLOADED);
                modelManager.setPaused(model, false);
                modelManager.setProgressPercent(model, 0);
                stopTracking(model);
                break;
            default:
                break;
        }

        return START_NOT_STICKY;
    }

    private void startOrResumeDownload(@NonNull LocalModelCatalog model) {
        activeModelIds.put(model.getId(), false);
        // Post an initial "starting…" notification immediately and call startForeground before
        // the network call even begins — startForeground must be called shortly after the
        // service starts (Android enforces this, more strictly from API 31+) or the system kills
        // the service as misbehaving, regardless of how fast the download itself begins.
        int notifId = notificationIdFor(model);
        startForeground(notifId, buildNotification(model, modelManager.getProgressPercent(model), false, false));

        LocalModelDownloader.download(modelManager, model, new LocalModelDownloader.DownloadCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                updateNotification(model, percent, false);
            }

            @Override
            public void onComplete(@NonNull java.io.File modelFile) {
                showTerminalNotification(model, true, null);
                stopTracking(model);
            }

            @Override
            public void onError(@NonNull String message) {
                showTerminalNotification(model, false, message);
                stopTracking(model);
            }
        });
    }

    /** Stops tracking one model's download and shuts the service down once none are left. */
    private void stopTracking(@NonNull LocalModelCatalog model) {
        activeModelIds.remove(model.getId());
        NotificationManager nm = getSystemService(NotificationManager.class);
        stopSelfIfIdle();
        // Note: stopSelfIfIdle() may call stopForeground(); if other downloads are still active
        // the foreground notification simply belongs to whichever one is still running — this
        // model's own notification (already posted as a normal, non-ongoing notification by
        // showTerminalNotification) is left in place for the user to see/dismiss either way.
    }

    private void stopSelfIfIdle() {
        if (activeModelIds.isEmpty()) {
            // Service.STOP_FOREGROUND_REMOVE only exists from API 33 — this project's minSdk is
            // 26, so the boolean overload (deprecated on newer APIs but still the only one that
            // works everywhere down to 26) is used instead via the SDK-gated call below.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else {
                //noinspection deprecation
                stopForeground(true);
            }
            stopSelf();
        }
    }

    // ── Notifications ───────────────────────────────────────────────────────

    private int notificationIdFor(@NonNull LocalModelCatalog model) {
        return NOTIFICATION_ID_BASE + Math.abs(model.getId().hashCode() % 10_000);
    }

    private void updateNotification(@NonNull LocalModelCatalog model, int percent, boolean paused) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(notificationIdFor(model), buildNotification(model, percent, paused, false));
        }
    }

    /** Posted once a download finishes or fails — replaces the ongoing progress notification. */
    private void showTerminalNotification(@NonNull LocalModelCatalog model, boolean success, @Nullable String errorMessage) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        Intent openIntent = new Intent(this, AiSettingsActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, notificationIdFor(model),
                openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mtrl_download)
                .setContentTitle(success
                        ? model.getDisplayName() + " ready"
                        : model.getDisplayName() + " download failed")
                .setContentText(success
                        ? "Downloaded and ready to use offline."
                        : (errorMessage != null ? errorMessage : "Download failed."))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(success
                        ? "Downloaded and ready to use offline."
                        : (errorMessage != null ? errorMessage : "Download failed.")))
                .setContentIntent(contentIntent)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        nm.notify(notificationIdFor(model), builder.build());
    }

    /**
     * Builds the ongoing progress notification with Pause/Resume + Stop actions. All three
     * actions are {@code PendingIntent}s that re-enter this same service via
     * {@link #onStartCommand} with the corresponding {@code ACTION_*} string — see the class
     * javadoc for why this is a single-service dispatch instead of a separate receiver.
     */
    @NonNull
    private Notification buildNotification(@NonNull LocalModelCatalog model, int percent, boolean paused, boolean indeterminate) {
        Intent openIntent = new Intent(this, AiSettingsActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, notificationIdFor(model),
                openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mtrl_download)
                .setContentTitle(model.getDisplayName())
                .setContentText(paused ? "Paused — " + percent + "%" : "Downloading — " + percent + "%")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, percent, indeterminate);

        if (paused) {
            builder.addAction(new NotificationCompat.Action(
                    R.drawable.ic_play_white_48dp, "Resume", actionPendingIntent(model, ACTION_RESUME)));
        } else {
            builder.addAction(new NotificationCompat.Action(
                    R.drawable.ic_pause_white_48dp, "Pause", actionPendingIntent(model, ACTION_PAUSE)));
        }
        builder.addAction(new NotificationCompat.Action(
                R.drawable.ic_stop_sign, "Stop", actionPendingIntent(model, ACTION_CANCEL)));

        return builder.build();
    }

    @NonNull
    private PendingIntent actionPendingIntent(@NonNull LocalModelCatalog model, @NonNull String action) {
        Intent intent = new Intent(this, LocalModelDownloadService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_MODEL_ID, model.getId());
        // Distinct request code per (model, action) pair so these PendingIntents don't collide
        // and overwrite each other's extras — FLAG_UPDATE_CURRENT would otherwise let a later
        // "pause model B" PendingIntent silently replace an earlier "pause model A" one if they
        // shared a request code.
        int requestCode = notificationIdFor(model) * 10 + action.hashCode() % 10;
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Model Downloads", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progress for on-device AI model downloads");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ── Static helpers for callers (Activity, adapter) ─────────────────────

    /** True if this service currently has an active or paused-but-tracked download for {@code model}. */
    public static boolean isTracking(@NonNull LocalModelCatalog model) {
        return activeModelIds.containsKey(model.getId());
    }

    public static void start(@NonNull Context context, @NonNull LocalModelCatalog model) {
        sendAction(context, model, ACTION_START);
    }

    public static void resume(@NonNull Context context, @NonNull LocalModelCatalog model) {
        sendAction(context, model, ACTION_RESUME);
    }

    public static void pause(@NonNull Context context, @NonNull LocalModelCatalog model) {
        sendAction(context, model, ACTION_PAUSE);
    }

    public static void cancel(@NonNull Context context, @NonNull LocalModelCatalog model) {
        sendAction(context, model, ACTION_CANCEL);
    }

    private static void sendAction(@NonNull Context context, @NonNull LocalModelCatalog model, @NonNull String action) {
        Intent intent = new Intent(context, LocalModelDownloadService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_MODEL_ID, model.getId());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
