package pro.sketchware.ai.offline;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import pro.sketchware.ai.storage.AiPreferences;

/**
 * Downloads a {@code .litertlm} model file from Hugging Face with resume support.
 *
 * <p><b>Why OkHttp directly instead of WorkManager/DownloadManager:</b> this project has
 * no existing runtime dependency on either. {@code androidx.work:work-runtime} only
 * appears as a library the AI tools can *add to user Sketchware projects*
 * ({@code LibraryDiscoveryTools}, {@code DaydreamToolsSection}) and as an optional forced
 * inclusion for built APKs ({@code LibraryExtrasSettings}) — it is not linked into the
 * Sketchware-ai app itself. Android's system {@code DownloadManager} does support resume
 * natively, but doesn't give fine-grained progress callbacks without polling its own
 * ContentProvider, and would still need a custom completion/foreground-service story to
 * match the existing "streaming callback" pattern the rest of {@code ai/api} already
 * uses. Since the app already has a shared, battle-tested {@link OkHttpClient} singleton
 * pattern in {@link pro.sketchware.ai.api.AiApiClient}, a plain OkHttp download with a
 * {@code Range} header for resume is the smallest change that fits the existing
 * architecture — no new dependency, no new background-execution surface to reason about.
 *
 * <p>Resume works by requesting {@code Range: bytes=<partial-file-size>-} and appending to
 * the existing {@code .part} file; the server confirms with HTTP 206 (Partial Content).
 * If the server ignores the Range header and returns 200 (full content) instead, the
 * partial file is discarded and the download restarts from zero — this is standard HTTP
 * behavior and Hugging Face's CDN (Cloudflare) does support Range requests.
 */
public final class LocalModelDownloader {

    /** Callback for download progress/completion, delivered on the main thread. */
    public interface DownloadCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onComplete(@NonNull File modelFile);
        void onError(@NonNull String message);
    }

    private static final long CONNECT_TIMEOUT_SECONDS = 30;
    private static final int BUFFER_SIZE = 64 * 1024;

    /** Tracks in-flight downloads by model ID so LocalModelManager can query/cancel them. */
    private static final Map<String, Call> activeCalls = new ConcurrentHashMap<>();

    private static volatile OkHttpClient sharedClient;

    private static OkHttpClient getClient() {
        if (sharedClient == null) {
            synchronized (LocalModelDownloader.class) {
                if (sharedClient == null) {
                    sharedClient = new OkHttpClient.Builder()
                            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(0, TimeUnit.SECONDS) // large file, no fixed read timeout
                            .writeTimeout(0, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return sharedClient;
    }

    private LocalModelDownloader() {}

    public static boolean isActive(@NonNull LocalModelCatalog model) {
        return activeCalls.containsKey(model.getId());
    }

    /** True cancel: aborts the transfer and discards the partial file (used by "Cancel" in the UI). */
    public static void cancel(@NonNull LocalModelCatalog model) {
        Call call = activeCalls.remove(model.getId());
        if (call != null) call.cancel();
    }

    /**
     * Pauses an in-flight download: aborts the current network transfer but deliberately
     * keeps the {@code .part} file on disk so {@link #download} can resume from that byte
     * offset later via the {@code Range} header. This is also what naturally happens when
     * the app is backgrounded and Android suspends/kills the process mid-transfer — in both
     * cases the partial file is the resume point, so pause is really just "cancel the call,
     * keep the bytes."
     */
    public static void pause(@NonNull LocalModelManager manager, @NonNull LocalModelCatalog model) {
        Call call = activeCalls.remove(model.getId());
        if (call != null) call.cancel();
        // Keep state as DOWNLOADING (paused) rather than ERROR/NOT_DOWNLOADED — the .part
        // file is intact and getState() already treats a non-active DOWNLOADING model with
        // a resumable .part file as paused-and-resumable.
        manager.setPaused(model, true);
    }

    /**
     * Starts (or resumes) downloading {@code model} into {@code manager}'s models directory.
     * Safe to call from any thread; the network transfer runs on OkHttp's own dispatcher
     * thread pool, and all callback methods are marshalled back to the main thread.
     *
     * <p>Phase 5.4: for {@link LocalModelCatalog#isGated()} entries (currently the Gemma-family
     * ones), Hugging Face requires an {@code Authorization: Bearer <token>} header or the
     * request comes back HTTP 401 regardless of the URL being otherwise correct — this was
     * confirmed as the actual cause of field-reported Gemma download failures. If the model is
     * gated and no token is saved in {@link AiPreferences}, this fails fast with a clear
     * message before making any network call, rather than letting the request go out and
     * surfacing a raw "HTTP 401" to the user.
     */
    public static void download(@NonNull LocalModelManager manager, @NonNull LocalModelCatalog model,
                                 @NonNull DownloadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        File partialFile = manager.getPartialFile(model);
        long resumeFrom = partialFile.exists() ? partialFile.length() : 0L;

        String hfToken = model.isGated()
                ? AiPreferences.getInstance(manager.getContext()).getHuggingFaceToken()
                : null;
        if (model.isGated() && (hfToken == null || hfToken.isEmpty())) {
            String msg = "\"" + model.getDisplayName() + "\" requires a Hugging Face access "
                    + "token — this model is gated (you must accept Google's Gemma license on "
                    + "Hugging Face first). Add your token in AI Settings, then try again.";
            manager.setState(model, LocalModelState.ERROR);
            manager.setLastError(model, msg);
            mainHandler.post(() -> callback.onError(msg));
            return;
        }

        Request.Builder requestBuilder = new Request.Builder().url(model.getDownloadUrl());
        if (resumeFrom > 0) {
            requestBuilder.header("Range", "bytes=" + resumeFrom + "-");
        }
        if (hfToken != null) {
            requestBuilder.header("Authorization", "Bearer " + hfToken);
        }

        manager.setState(model, LocalModelState.DOWNLOADING);
        manager.setPaused(model, false);
        manager.setLastError(model, null);

        Call call = getClient().newCall(requestBuilder.build());
        activeCalls.put(model.getId(), call);

        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                activeCalls.remove(model.getId());
                if (call.isCanceled()) return; // user-initiated cancel, not an error
                manager.setState(model, LocalModelState.ERROR);
                manager.setLastError(model, e.getMessage() != null ? e.getMessage() : "Network error");
                mainHandler.post(() -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Network error while downloading"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                boolean isResumedTransfer = response.code() == 206;
                boolean restartingFromZero = resumeFrom > 0 && !isResumedTransfer;

                if (!response.isSuccessful()) {
                    activeCalls.remove(model.getId());
                    manager.setState(model, LocalModelState.ERROR);
                    String msg;
                    if (model.isGated() && (response.code() == 401 || response.code() == 403)) {
                        // A valid token still gets 401/403 from Hugging Face if the account
                        // hasn't accepted the model's license yet — this is the #1 real-world
                        // cause of "401 even after adding a token" reports, not a bad token.
                        msg = "HTTP " + response.code() + ": Hugging Face rejected the request "
                                + "for \"" + model.getDisplayName() + "\". Your token is set, but "
                                + "you also need to open the model page on huggingface.co ("
                                + model.getHfRepo() + "), log in with the SAME account, and click "
                                + "\"Agree\"/\"Accept\" on the Gemma license — then retry the download.";
                    } else {
                        msg = "HTTP " + response.code() + " downloading model";
                    }
                    manager.setLastError(model, msg);
                    mainHandler.post(() -> callback.onError(msg));
                    response.close();
                    return;
                }

                if (restartingFromZero) {
                    //noinspection ResultOfMethodCallIgnored
                    partialFile.delete();
                }

                long alreadyDownloaded = restartingFromZero ? 0L : resumeFrom;
                long contentLength = response.body() != null ? response.body().contentLength() : -1;
                long totalBytes = contentLength >= 0
                        ? alreadyDownloaded + contentLength
                        : model.getApproxSizeBytes();

                try (Response r = response;
                     InputStream in = r.body() != null ? r.body().byteStream() : null;
                     RandomAccessFile out = new RandomAccessFile(partialFile, "rw")) {

                    if (in == null) throw new IOException("Empty response body");

                    out.seek(alreadyDownloaded);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long downloaded = alreadyDownloaded;
                    int lastReportedPercent = -1;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (call.isCanceled()) {
                            activeCalls.remove(model.getId());
                            return;
                        }
                        out.write(buffer, 0, read);
                        downloaded += read;
                        int percent = totalBytes > 0 ? (int) Math.min(100, (downloaded * 100) / totalBytes) : 0;
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent;
                            manager.setProgressPercent(model, percent);
                            long finalDownloaded = downloaded;
                            long finalTotal = totalBytes;
                            int finalPercent = percent;
                            mainHandler.post(() -> callback.onProgress(finalPercent, finalDownloaded, finalTotal));
                        }
                    }

                    activeCalls.remove(model.getId());

                    File finalFile = manager.getModelFile(model);
                    if (!partialFile.renameTo(finalFile)) {
                        throw new IOException("Could not finalize downloaded file (rename failed)");
                    }
                    manager.setState(model, LocalModelState.READY);
                    manager.setProgressPercent(model, 100);
                    mainHandler.post(() -> callback.onComplete(finalFile));

                } catch (IOException e) {
                    activeCalls.remove(model.getId());
                    manager.setState(model, LocalModelState.ERROR);
                    String msg = e.getMessage() != null ? e.getMessage() : "I/O error while downloading";
                    manager.setLastError(model, msg);
                    mainHandler.post(() -> callback.onError(msg));
                }
            }
        });
    }

    @Nullable
    public static String describeError(@NonNull LocalModelManager manager, @NonNull LocalModelCatalog model) {
        return manager.getLastError(model);
    }
}
