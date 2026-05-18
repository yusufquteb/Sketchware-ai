package pro.sketchware.ai.file;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FileAttachManager — Stage 3 file picker and content reader.
 *
 * <p>Integrates with the chat input area's attach button to let users
 * select files and inject their content into the chat.
 *
 * <p><b>Supported file types:</b>
 * <ul>
 *   <li>Text files: .java, .xml, .txt, .json, .md, .gradle → content is read and sent</li>
 *   <li>Image files: .jpg, .png → file path/name is sent (content not embedded)</li>
 * </ul>
 *
 * <p><b>Architecture rules:</b>
 * <ul>
 *   <li>File reading runs on a background thread ({@link #ioExecutor}).</li>
 *   <li>Callback is dispatched to the main thread.</li>
 *   <li>Never blocks the UI thread.</li>
 *   <li>Content size limited to {@link #MAX_FILE_SIZE_BYTES}.</li>
 * </ul>
 *
 * <p><b>Usage (in Activity or Fragment):</b>
 * <pre>
 * // 1. Create manager (in onCreate, before setContentView)
 * FileAttachManager attachManager = new FileAttachManager(this);
 * attachManager.registerLauncher(this);  // MUST call before onStart
 *
 * // 2. Set result callback
 * attachManager.setCallback(result -> {
 *     if (result.isImage) {
 *         coordinator.sendUserMessage("[Image: " + result.fileName + "]");
 *     } else {
 *         coordinator.sendUserMessage(result.formattedContent);
 *     }
 * });
 *
 * // 3. Launch picker (on attach button click)
 * attachButton.setOnClickListener(v -> attachManager.openFilePicker());
 * </pre>
 */
public class FileAttachManager {

    private static final String TAG = "FileAttachManager";

    /** Maximum file size to read in full — 256 KB for chat injection. */
    private static final long MAX_FILE_SIZE_BYTES = 256 * 1024L;

    // ─── Supported MIME types ─────────────────────────────────────────────────

    /**
     * MIME type filter for the file picker.
     * Covers: text files + common code files + images.
     */
    private static final String[] SUPPORTED_MIME_TYPES = {
            "text/plain",
            "text/xml",
            "application/xml",
            "application/json",
            "text/x-java-source",
            "text/java",
            "text/markdown",
            "image/jpeg",
            "image/png",
            // Fallback for files with no MIME type detected
            "application/octet-stream",
            "*/*"
    };

    // ─── State ────────────────────────────────────────────────────────────────

    @NonNull
    private final Context applicationContext;

    @NonNull
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FileAttachManager-IO");
        t.setDaemon(true);
        return t;
    });

    @NonNull
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Nullable
    private FileAttachCallback callback;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public FileAttachManager(@NonNull Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers the ActivityResultLauncher for file picking.
     * <b>MUST be called in Activity.onCreate() or Fragment.onCreate() — BEFORE onStart.</b>
     *
     * @param activity the hosting FragmentActivity
     */
    public void registerLauncher(@NonNull FragmentActivity activity) {
        filePickerLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK
                            && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            processSelectedFile(uri);
                        }
                    }
                }
        );
    }

    /**
     * Registers the ActivityResultLauncher for file picking from a Fragment.
     *
     * @param fragment the hosting Fragment
     */
    public void registerLauncher(@NonNull Fragment fragment) {
        filePickerLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK
                            && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            processSelectedFile(uri);
                        }
                    }
                }
        );
    }

    // ─── Callback ─────────────────────────────────────────────────────────────

    public void setCallback(@Nullable FileAttachCallback callback) {
        this.callback = callback;
    }

    // ─── Launch ───────────────────────────────────────────────────────────────

    /**
     * Opens the system file picker with the supported MIME type filter.
     * Call this from the attach button click listener.
     */
    public void openFilePicker() {
        if (filePickerLauncher == null) {
            Log.e(TAG, "registerLauncher() was not called before openFilePicker().");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "text/xml", "application/xml",
                "application/json", "image/jpeg", "image/png",
                "text/x-java-source", "text/java", "text/markdown"
        });
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, false);

        filePickerLauncher.launch(
                Intent.createChooser(intent, "Attach file"));
    }

    // ─── File Processing ──────────────────────────────────────────────────────

    /**
     * Processes the selected file URI on a background thread.
     * Dispatches the result to the main thread via callback.
     */
    private void processSelectedFile(@NonNull Uri uri) {
        ioExecutor.submit(() -> {
            FileAttachResult result = buildResult(uri);
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFileAttached(result);
                }
            });
        });
    }

    @WorkerThread
    @NonNull
    private FileAttachResult buildResult(@NonNull Uri uri) {
        String fileName   = getFileName(uri);
        String mimeType   = applicationContext.getContentResolver().getType(uri);
        boolean isImage   = isImageMime(mimeType) || isImageExtension(fileName);

        if (isImage) {
            // For images: send path/name hint only
            return FileAttachResult.image(fileName, uri.toString());
        }

        // Text/code files: read content
        long fileSize = getFileSize(uri);
        boolean truncated = fileSize > MAX_FILE_SIZE_BYTES;

        StringBuilder content = new StringBuilder();
        try (InputStream is = applicationContext.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return FileAttachResult.error(fileName, "Could not open file.");
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));

            long bytesRead = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                bytesRead += line.length() + 1;
                content.append(line).append('\n');
                if (truncated && bytesRead >= MAX_FILE_SIZE_BYTES) break;
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + fileName, e);
            return FileAttachResult.error(fileName, "Failed to read file: " + e.getMessage());
        }

        // Build a formatted code block for the chat
        String extension = getExtension(fileName);
        String formatted = "```" + extension + "\n"
                + "// File: " + fileName + "\n"
                + content.toString()
                + (truncated ? "\n// [Truncated at 256KB]" : "")
                + "\n```";

        return FileAttachResult.text(fileName, formatted, content.toString());
    }

    // ─── URI helpers ──────────────────────────────────────────────────────────

    @NonNull
    private String getFileName(@NonNull Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = applicationContext.getContentResolver().query(
                    uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception e) {
                Log.w(TAG, "getFileName: " + e.getMessage());
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut >= 0) result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "unknown_file";
    }

    private long getFileSize(@NonNull Uri uri) {
        try (Cursor cursor = applicationContext.getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) {
                    return cursor.getLong(idx);
                }
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private boolean isImageMime(@Nullable String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    private boolean isImageExtension(@NonNull String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                || ext.equals("gif") || ext.equals("webp");
    }

    @NonNull
    private String getExtension(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    // ─── Result + Callback ────────────────────────────────────────────────────

    /**
     * Result of a file attachment operation.
     */
    public static final class FileAttachResult {

        public enum ResultType { TEXT, IMAGE, ERROR }

        @NonNull  public final ResultType type;
        @NonNull  public final String fileName;
        /** Markdown-formatted content for chat injection. */
        @Nullable public final String formattedContent;
        /** Raw file content (unformatted). */
        @Nullable public final String rawContent;
        /** URI string for image files. */
        @Nullable public final String uriString;
        /** Error message if type == ERROR. */
        @Nullable public final String errorMessage;

        public final boolean isImage;

        private FileAttachResult(
                @NonNull ResultType type,
                @NonNull String fileName,
                @Nullable String formattedContent,
                @Nullable String rawContent,
                @Nullable String uriString,
                @Nullable String errorMessage
        ) {
            this.type             = type;
            this.fileName         = fileName;
            this.formattedContent = formattedContent;
            this.rawContent       = rawContent;
            this.uriString        = uriString;
            this.errorMessage     = errorMessage;
            this.isImage          = type == ResultType.IMAGE;
        }

        @NonNull
        static FileAttachResult text(
                @NonNull String fileName,
                @NonNull String formatted,
                @NonNull String raw
        ) {
            return new FileAttachResult(ResultType.TEXT, fileName, formatted, raw, null, null);
        }

        @NonNull
        static FileAttachResult image(@NonNull String fileName, @NonNull String uri) {
            String formatted = "[📎 Image attached: **" + fileName + "**]";
            return new FileAttachResult(ResultType.IMAGE, fileName, formatted, null, uri, null);
        }

        @NonNull
        static FileAttachResult error(@NonNull String fileName, @NonNull String error) {
            return new FileAttachResult(ResultType.ERROR, fileName, null, null, null, error);
        }

        /**
         * Returns the text that should be sent as the user's chat message.
         * For text files: the formatted code block.
         * For images: the image reference string.
         * For errors: a description of the failure.
         */
        @NonNull
        public String getChatText() {
            if (type == ResultType.ERROR) {
                return "⚠️ Failed to attach **" + fileName + "**: " + errorMessage;
            }
            return formattedContent != null ? formattedContent
                    : "[📎 " + fileName + "]";
        }
    }

    /**
     * Callback for file attach results.
     * Always invoked on the main thread.
     */
    public interface FileAttachCallback {
        /**
         * Called when a file has been selected and processed.
         *
         * @param result the attach result — check {@link FileAttachResult#type}
         */
        void onFileAttached(@NonNull FileAttachResult result);
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    /** Shuts down the IO executor. Call from Activity/Fragment onDestroy. */
    public void destroy() {
        ioExecutor.shutdownNow();
    }
}
