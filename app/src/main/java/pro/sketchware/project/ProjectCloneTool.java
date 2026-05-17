package pro.sketchware.project;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.utility.io.SafeFileOps;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Clones a project by copying ALL associated data:
 *   • .sketchware/data/{scId}/              → data
 *   • .sketchware/mysc/list/{scId}/project  → project descriptor (sc_id updated)
 *   • .sketchware/resources/{type}/{scId}/  → fonts, icons, images, sounds
 *
 * Fix: Uses lC.b() to generate next ID and lC.a(id, map) to write the project
 * descriptor in Sketchware's encoded format, which lC.a() can actually read back.
 */
public class ProjectCloneTool {

    public static void showDialogNow(Activity activity, String projectID) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Clone project")
                .setMessage("Clone your project? All project data (code, layouts, resources, libraries) will be copied.")
                .setPositiveButton("Clone", (d, w) -> startNow(activity, projectID))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void startNow(Activity activity, String projectID) {
        View progressView = LayoutInflater.from(activity).inflate(R.layout.progress_msg_box, null);
        TextView progressText = progressView.findViewById(R.id.tv_progress);
        if (progressText != null) progressText.setText("Cloning project…");

        AlertDialog progressDialog = new MaterialAlertDialogBuilder(activity)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            String newId = null;
            String errorMsg = null;
            try {
                // ── 1. Generate a new unique sc_id using lC.b() ─────────────
                // lC.b() scans all existing project descriptors (encoded format)
                // and returns max(sc_id) + 1, guaranteeing uniqueness.
                newId = lC.b();

                // ── 2. Load source project descriptor as HashMap ─────────────
                HashMap<String, Object> projectMap = lC.b(projectID);
                if (projectMap == null) {
                    throw new IllegalStateException("Could not load project descriptor for sc_id: " + projectID);
                }

                // ── 3. Update descriptor fields for the clone ────────────────
                projectMap = updateProjectDescriptor(projectMap, newId);

                // ── 4. Write project descriptor in Sketchware's encoded format ─
                // lC.a(id, map) creates mysc/list/{id}/ dir and writes the
                // encoded "project" file that lC.a() (list loader) can read.
                lC.a(newId, projectMap);

                // ── 5. Copy data dir ─────────────────────────────────────────
                File sketchwareDir = new File(
                        android.os.Environment.getExternalStorageDirectory(), ".sketchware");
                File srcData = new File(sketchwareDir, "data/" + projectID);
                File dstData = new File(sketchwareDir, "data/" + newId);
                if (srcData.exists()) SafeFileOps.copyTree(srcData, dstData);

                // ── 6. Copy resource directories ─────────────────────────────
                for (String resType : new String[]{"fonts", "icons", "images", "sounds"}) {
                    File srcRes = new File(sketchwareDir, "resources/" + resType + "/" + projectID);
                    if (srcRes.exists()) {
                        File dstRes = new File(sketchwareDir, "resources/" + resType + "/" + newId);
                        SafeFileOps.copyTree(srcRes, dstRes);
                    }
                }

            } catch (Exception e) {
                errorMsg = e.getMessage();
            }

            final String finalNewId = newId;
            final String finalError = errorMsg;

            activity.runOnUiThread(() -> {
                progressDialog.dismiss();
                if (finalError == null) {
                    // Refresh project list immediately so the clone appears without
                    // the user needing to manually pull-to-refresh.
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).n();
                    }
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("Clone successful")
                            .setMessage("Project cloned successfully as \"" +
                                    yB.c(lC.b(finalNewId), "my_sc_app_name") +
                                    "\".")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    SketchwareUtil.toastError("Clone failed: " + finalError);
                }
            });
        }).start();
    }

    /**
     * Updates sc_id, name and package in the project descriptor HashMap.
     * Appends " (Clone)" to name and ".cloneN" to package to avoid conflicts.
     */
    @SuppressWarnings("unchecked")
    private static HashMap<String, Object> updateProjectDescriptor(
            HashMap<String, Object> map, String newId) {

        // Deep-copy to avoid mutating the cached original
        HashMap<String, Object> clone = new HashMap<>(map);

        clone.put("sc_id", newId);

        // Append (Clone) to app name
        Object name = clone.get("my_sc_app_name");
        if (name instanceof String) {
            String n = (String) name;
            if (!n.endsWith(" (Clone)")) clone.put("my_sc_app_name", n + " (Clone)");
        }

        // Make package name unique
        Object pkg = clone.get("my_sc_pkg_name");
        if (pkg instanceof String) {
            String p = (String) pkg;
            if (!p.endsWith(".clone")) clone.put("my_sc_pkg_name", p + ".clone" + newId);
        }

        return clone;
    }
}
