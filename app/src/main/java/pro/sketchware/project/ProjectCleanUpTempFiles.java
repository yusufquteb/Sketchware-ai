package pro.sketchware.project;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import pro.sketchware.R;

/**
 * Mirrors DayDreamCleanUpTemporaryFiles from open source.
 * Cleans build temp files for a single project.
 */
public class ProjectCleanUpTempFiles {

    public static void showDialogNow(Activity activity, String projectID) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Clean up temporary files")
                .setMessage("Remove temporary build files for this project to free up storage space.")
                .setPositiveButton("Clean up", (d, w) -> startNow(activity, projectID))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void startNow(Activity activity, String projectID) {
        View progressView = LayoutInflater.from(activity).inflate(R.layout.progress_msg_box, null);
        // no layout_progress ID in progress_msg_box
        TextView progressText = progressView.findViewById(pro.sketchware.R.id.tv_progress);
        if (progressText != null) progressText.setText("Cleaning up...");
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(activity)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            boolean result = false;
            try {
                // Clean build temp dirs for specific project
                File myscDir = new File(
                    android.os.Environment.getExternalStorageDirectory(),
                    ".sketchware/mysc/" + projectID);
                if (myscDir.exists()) {
                    for (String temp : new String[]{"build", ".gradle", "tmp", "temp"}) {
                        File tempDir = new File(myscDir, temp);
                        if (tempDir.exists()) { deleteRecursive(tempDir); }
                    }
                    result = true;
                }
            } catch (Exception ignored) {}

            final boolean success = result;
            activity.runOnUiThread(() -> {
                progressDialog.dismiss();
                if (success) {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("Done")
                            .setMessage("Cleaned up temporary files.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("Error")
                            .setMessage("Unable to clean up temporary files.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) for (File c : files) deleteRecursive(c);
        }
        f.delete();
    }
}
