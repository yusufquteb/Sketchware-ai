package pro.sketchware.ai.offline;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Manages the on-device state of {@link LocalModelCatalog} models: current lifecycle
 * state, file path on disk, download progress, and last error — plus the device-RAM
 * check required before showing a download option (phase requirement: warn, don't block).
 *
 * <p>Model files are stored under {@code context.getFilesDir()/offline_models/} so they
 * survive app updates but are removed on uninstall (same guarantee as everything else the
 * app stores in internal storage — consistent with how {@code AiPreferences} already
 * treats API keys, which is documented there as "Android deletes this on uninstall").
 *
 * <p>Not a singleton on purpose — instantiated per-Activity like most storage helpers in
 * this codebase (see {@code AiPreferences} for the exception, which needs a process-wide
 * migration guard that this class does not).
 */
public class LocalModelManager {

    private static final String PREFS_NAME = "local_model_state";
    private static final String KEY_STATE_PREFIX = "state_";
    private static final String KEY_PROGRESS_PREFIX = "progress_";
    private static final String KEY_ERROR_PREFIX = "error_";
    private static final String KEY_SELECTED_MODEL = "selected_local_model";
    private static final String KEY_PREFER_GPU_BACKEND = "prefer_gpu_backend";
    private static final String KEY_PAUSED_PREFIX = "paused_";

    /** Below this device RAM, a warning is shown but the download is still allowed. */
    public static final int LOW_RAM_WARNING_THRESHOLD_GB = 6;

    private final Context appContext;
    private final SharedPreferences prefs;

    public LocalModelManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Added Phase 5.4: exposes the app Context so LocalModelDownloader can read the
     *  Hugging Face token from AiPreferences without this class needing to know anything
     *  about auth itself — keeps state management and download auth as separate concerns. */
    @NonNull
    public Context getContext() {
        return appContext;
    }

    // ── Paths ────────────────────────────────────────────────────────────────

    /** Directory all local model files live in. Created on first access. */
    @NonNull
    public File getModelsDir() {
        File dir = new File(appContext.getFilesDir(), "offline_models");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** Final path for a model's {@code .litertlm} file (may not exist yet). */
    @NonNull
    public File getModelFile(@NonNull LocalModelCatalog model) {
        return new File(getModelsDir(), model.getFileName());
    }

    /** Temporary path used while downloading, renamed to {@link #getModelFile} on success. */
    @NonNull
    public File getPartialFile(@NonNull LocalModelCatalog model) {
        return new File(getModelsDir(), model.getFileName() + ".part");
    }

    // ── State ────────────────────────────────────────────────────────────────

    @NonNull
    public LocalModelState getState(@NonNull LocalModelCatalog model) {
        // Reconcile against disk on every read so state survives process death mid-download.
        File complete = getModelFile(model);
        if (complete.exists() && complete.length() >= model.getApproxSizeBytes()) {
            return LocalModelState.READY;
        }
        String saved = prefs.getString(KEY_STATE_PREFIX + model.getId(), null);
        if (saved == null) return LocalModelState.NOT_DOWNLOADED;
        try {
            LocalModelState state = LocalModelState.valueOf(saved);
            // A DOWNLOADING state left over from a killed process/background suspension is not
            // actually transferring anymore, but if a resumable .part file is on disk we keep
            // showing it as DOWNLOADING (paused) so the user can resume with one tap instead of
            // starting over from zero — the Range-header resume logic in LocalModelDownloader
            // picks up right where the partial file left off.
            if (state == LocalModelState.DOWNLOADING && !LocalModelDownloader.isActive(model)) {
                if (getPartialFile(model).exists()) {
                    setPaused(model, true);
                    return LocalModelState.DOWNLOADING;
                }
                return LocalModelState.NOT_DOWNLOADED;
            }
            return state;
        } catch (IllegalArgumentException e) {
            return LocalModelState.NOT_DOWNLOADED;
        }
    }

    public void setState(@NonNull LocalModelCatalog model, @NonNull LocalModelState state) {
        prefs.edit().putString(KEY_STATE_PREFIX + model.getId(), state.name()).apply();
    }

    /**
     * True if a DOWNLOADING model is user-paused (or was interrupted by backgrounding/process
     * death and is sitting on a resumable partial file) rather than actively transferring.
     * Only meaningful while {@link #getState} returns {@link LocalModelState#DOWNLOADING}.
     */
    public boolean isPaused(@NonNull LocalModelCatalog model) {
        return prefs.getBoolean(KEY_PAUSED_PREFIX + model.getId(), false);
    }

    public void setPaused(@NonNull LocalModelCatalog model, boolean paused) {
        prefs.edit().putBoolean(KEY_PAUSED_PREFIX + model.getId(), paused).apply();
    }

    /** 0-100, only meaningful while state == DOWNLOADING. */
    public int getProgressPercent(@NonNull LocalModelCatalog model) {
        return prefs.getInt(KEY_PROGRESS_PREFIX + model.getId(), 0);
    }

    public void setProgressPercent(@NonNull LocalModelCatalog model, int percent) {
        prefs.edit().putInt(KEY_PROGRESS_PREFIX + model.getId(), Math.max(0, Math.min(100, percent))).apply();
    }

    @Nullable
    public String getLastError(@NonNull LocalModelCatalog model) {
        return prefs.getString(KEY_ERROR_PREFIX + model.getId(), null);
    }

    public void setLastError(@NonNull LocalModelCatalog model, @Nullable String error) {
        prefs.edit().putString(KEY_ERROR_PREFIX + model.getId(), error).apply();
    }

    /** Deletes the model file (and any partial download) and resets its state. */
    public void deleteModel(@NonNull LocalModelCatalog model) {
        LocalModelDownloader.cancel(model);
        //noinspection ResultOfMethodCallIgnored
        getModelFile(model).delete();
        //noinspection ResultOfMethodCallIgnored
        getPartialFile(model).delete();
        prefs.edit()
                .remove(KEY_STATE_PREFIX + model.getId())
                .remove(KEY_PROGRESS_PREFIX + model.getId())
                .remove(KEY_ERROR_PREFIX + model.getId())
                .remove(KEY_PAUSED_PREFIX + model.getId())
                .apply();
    }

    // ── Selected model (which one LocalModelProvider actually loads) ──────────

    public void setSelectedModel(@NonNull LocalModelCatalog model) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model.getId()).apply();
    }

    @NonNull
    public LocalModelCatalog getSelectedModel() {
        LocalModelCatalog saved = LocalModelCatalog.fromId(prefs.getString(KEY_SELECTED_MODEL, null));
        // Phase 5.6: DEEPSEEK_R1_DISTILL_QWEN_1_5B was re-enabled (no longer hidden from all()),
        // so the special-case fallback that used to treat a saved selection of it as "nothing
        // saved" has been removed — it now resolves normally like every other catalog entry.
        if (saved == null) {
            return LocalModelCatalog.getRecommendedDefault();
        }
        return saved;
    }

    // ── Inference backend (CPU vs GPU) ──────────────────────────────────────
    //
    // Defaults to CPU (false) and, post llama.cpp-migration, this preference is currently a
    // no-op regardless of value: v1 of LlamaCppEngineBridge is CPU-only per the approved
    // migration plan (GPU/Vulkan support is an explicit deferred follow-up). Kept here rather
    // than removed so the AI Settings toggle and its stored preference survive unchanged for
    // when GPU support is actually wired up — this used to gate LiteRT-LM's OpenCL
    // Backend.GPU() path, which had no automatic CPU fallback and was known to hard-fail on
    // some real devices (e.g. certain Exynos/ANGLE-CL combinations); that history is why this
    // stayed an explicit opt-in rather than a silent default even before the engine migration.

    public boolean isGpuBackendPreferred() {
        return prefs.getBoolean(KEY_PREFER_GPU_BACKEND, false);
    }

    public void setGpuBackendPreferred(boolean preferGpu) {
        prefs.edit().putBoolean(KEY_PREFER_GPU_BACKEND, preferGpu).apply();
    }

    // ── RAM check ────────────────────────────────────────────────────────────

    /**
     * Total device RAM in GB (rounded). Used only to decide whether to show the
     * low-RAM warning — never used to block a download outright (phase requirement:
     * the user makes the final call after being warned).
     */
    public int getDeviceRamGb() {
        ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return -1;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return (int) Math.round(info.totalMem / (1024.0 * 1024.0 * 1024.0));
    }

    /** True if the device is below the generic low-RAM warning threshold. */
    public boolean isLowRamDevice() {
        int ram = getDeviceRamGb();
        return ram > 0 && ram < LOW_RAM_WARNING_THRESHOLD_GB;
    }

    /**
     * True if the device is below the specific model's own recommended minimum —
     * this is the finer-grained check the Settings card uses to decide whether to
     * show a per-model warning, as opposed to {@link #isLowRamDevice()} which is a
     * blanket check independent of which model is selected.
     */
    public boolean isBelowModelRecommendation(@NonNull LocalModelCatalog model) {
        int ram = getDeviceRamGb();
        return ram > 0 && ram < model.getMinRamGb();
    }

    /** Free space on internal storage, in bytes. */
    public long getFreeDiskSpaceBytes() {
        return getModelsDir().getUsableSpace();
    }

    /** True if there isn't enough free disk space for the model's approximate size (+10% margin). */
    public boolean isInsufficientDiskSpace(@NonNull LocalModelCatalog model) {
        long needed = (long) (model.getApproxSizeBytes() * 1.10);
        return getFreeDiskSpaceBytes() < needed;
    }
}
