package pro.sketchware.activities.tools;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

public class ProjectCleanUpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_cleanup);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        initialize();
    }

    private void initialize() {
        findViewById(R.id.ln_cleanuptemporaryfiles).setOnClickListener(v -> cleanUpTemporaryFiles());
        // local library cleanup and recycle bin not available in pro
    }

    private void cleanUpTemporaryFiles() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clean up temporary files")
                .setMessage("Remove temporary build files (build/, .gradle/, tmp/) from all projects to free storage space.")
                .setPositiveButton("Clean up", (d, w) -> startCleanup())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startCleanup() {
        View progressView = LayoutInflater.from(this).inflate(R.layout.progress_msg_box, null);
        // progressView is already a LinearLayout from progress_msg_box
        TextView progressText = progressView.findViewById(pro.sketchware.R.id.tv_progress);
        if (progressText != null) progressText.setText("Cleaning up...");
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            int cleaned = 0;
            try {
                File myscDir = new File(
                    android.os.Environment.getExternalStorageDirectory(),
                    ".sketchware/mysc");
                if (myscDir.exists() && myscDir.isDirectory()) {
                    File[] projects = myscDir.listFiles();
                    if (projects != null) {
                        for (File project : projects) {
                            if (!project.isDirectory()) continue;
                            for (String temp : new String[]{"build", ".gradle", "tmp", "temp"}) {
                                File tempDir = new File(project, temp);
                                if (tempDir.exists()) {
                                    deleteRecursive(tempDir);
                                    cleaned++;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            final int total = cleaned;
            runOnUiThread(() -> {
                progressDialog.dismiss();
                String msg = total > 0
                    ? "Cleaned up temporary files in " + total + " project(s)."
                    : "Nothing to clean up.";
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Done")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }).start();
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) for (File c : files) deleteRecursive(c);
        }
        f.delete();
    }
}
