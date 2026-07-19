package pro.sketchware.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * LlmDownloader handles the direct download of LLM models from HuggingFace/etc.
 * to the local storage, bypassing the need for external apps or pre-existing chats.
 */
public class LlmDownloader {

    private static final String TAG = "LlmDownloader";

    public interface DownloadListener {
        void onProgress(int progress);
        void onSuccess(File file);
        void onError(String error);
    }

    public static void downloadModel(Context context, String modelUrl, String fileName, DownloadListener listener) {
        new Thread(() -> {
            try {
                // Ensure directory exists
                File storageDir = new File(context.getExternalFilesDir(null), "llm_models");
                if (!storageDir.exists()) storageDir.mkdirs();
                File targetFile = new File(storageDir, fileName);

                if (targetFile.exists()) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onSuccess(targetFile));
                    return;
                }

                URL url = new URL(modelUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int fileLength = connection.getContentLength();
                
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream output = new FileOutputStream(targetFile)) {

                    byte[] data = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        output.write(data, 0, count);
                        if (fileLength > 0) {
                            int progress = (int) (total * 100 / fileLength);
                            new Handler(Looper.getMainLooper()).post(() -> listener.onProgress(progress));
                        }
                    }
                }
                
                new Handler(Looper.getMainLooper()).post(() -> listener.onSuccess(targetFile));

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                new Handler(Looper.getMainLooper()).post(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }
}
