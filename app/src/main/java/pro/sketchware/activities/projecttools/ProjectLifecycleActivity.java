package pro.sketchware.activities.projecttools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.project.backup.BackupRestoreManager;
import pro.sketchware.project.ProjectCleanupCore;
import pro.sketchware.project.ProjectCloneCore;
import pro.sketchware.project.ProjectExportEnhancer;
import pro.sketchware.utility.SketchwareUtil;

public class ProjectLifecycleActivity extends BaseAppCompatActivity {
    private String scId;
    private TextView outputView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID);
        if (scId == null || scId.trim().isEmpty()) {
            SketchwareUtil.toastError("Project id missing");
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Project Lifecycle");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, pad * 2);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ── Backup & Restore ──────────────────────────────────────────────────
        // Audit/DayDream-comparison fix: these two buttons used to call the
        // ProjectBackupCore/ProjectRestoreCore helpers, which only zipped/unzipped the single
        // .sketchware/data/{scId} folder — producing incomplete backups missing the project's
        // resources (fonts/icons/images/sounds), local libraries, and project descriptor. The
        // app already has a complete, mature backup system (BackupRestoreManager + BackupFactory)
        // that gathers every real project path, offers local-libs/custom-blocks options, and
        // uses a proper .swb file picker for restore. Delegate to it so this screen produces
        // real, restorable backups instead of a lossy data-only zip.
        LinearLayout backupBox = section(content, "Backup & Restore",
                "Create a full project backup (.swb) or recover from a previous one.");
        btn(backupBox, "Create full project backup (.swb)",
                () -> new BackupRestoreManager(this).backup(scId, scId));
        btn(backupBox, "Restore backup", () -> new BackupRestoreManager(this).restore());

        // ── Export & Clone ────────────────────────────────────────────────────
        LinearLayout transferBox = section(content, "Export & Clone",
                "Archive the generated project or duplicate the project data folder.");
        btn(transferBox, "Export generated project",
                () -> run("Exporting…", this::exportGeneratedProject));
        btn(transferBox, "Clone project data", this::showCloneDialog);

        // ── Maintenance ───────────────────────────────────────────────────────
        LinearLayout cleanBox = section(content, "Maintenance",
                "Remove build artifacts and temporary files to free space.");
        btn(cleanBox, "Clean generated temporary files", this::confirmCleanup);

        // ── Output ────────────────────────────────────────────────────────────
        LinearLayout outBox = section(content, "Paths", null);
        outputView = new TextView(this);
        outputView.setTextIsSelectable(true);
        outputView.setTextSize(12f);
        outputView.setText(
                "Project data: " + ProjectToolPaths.getProjectDataDir(scId).getAbsolutePath()
                + "\nGenerated:    " + ProjectToolPaths.getProjectMyscDir(scId).getAbsolutePath()
                + "\nBackups:      " + ProjectToolPaths.getProjectBackupDir(scId).getAbsolutePath()
                + "\nExports:      " + ProjectToolPaths.getProjectExportDir(scId).getAbsolutePath());
        outBox.addView(outputView);

        setContentView(root);
    }

    // ── Section helper ────────────────────────────────────────────────────────

    private LinearLayout section(LinearLayout parent, String title, String subtitle) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardLp);
        card.setCardElevation(dp(1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(12));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15f);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        box.addView(t);

        if (subtitle != null) {
            TextView s = new TextView(this);
            s.setText(subtitle);
            s.setTextSize(12f);
            s.setAlpha(0.65f);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.setMargins(0, dp(2), 0, dp(10));
            s.setLayoutParams(subLp);
            box.addView(s);
        }

        MaterialDivider div = new MaterialDivider(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        divLp.setMargins(0, subtitle == null ? dp(8) : 0, 0, dp(10));
        div.setLayoutParams(divLp);
        box.addView(div);

        card.addView(box);
        parent.addView(card);
        return box;
    }

    private void btn(LinearLayout parent, String label, Runnable action) {
        MaterialButton b = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(4));
        b.setLayoutParams(lp);
        parent.addView(b);
    }

    // ── Business logic (unchanged) ────────────────────────────────────────────

    private String exportGeneratedProject() throws Exception {
        File source = ProjectToolPaths.getProjectMyscDir(scId).exists()
                ? ProjectToolPaths.getProjectMyscDir(scId)
                : ProjectToolPaths.getProjectDataDir(scId);
        File exported = ProjectExportEnhancer.exportZip(source,
                ProjectToolPaths.getProjectExportDir(scId), scId + "_export");
        return "Export written:\n" + exported.getAbsolutePath();
    }

    private void confirmCleanup() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clean generated files?")
                .setMessage("This removes generated build directories and common temporary folders. Project data is not deleted.")
                .setPositiveButton("Clean", (d, w) -> run("Cleaning…", () -> {
                    int removed = 0;
                    removed += ProjectCleanupCore.clean(ProjectToolPaths.getProjectMyscDir(scId));
                    removed += ProjectCleanupCore.clean(ProjectToolPaths.getProjectDataDir(scId));
                    return "Removed " + removed + " temporary entries.";
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCloneDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(scId + "_copy_" + System.currentTimeMillis());
        input.setSelectAllOnFocus(true);
        int p = dp(20);
        input.setPadding(p, p / 2, p, p / 2);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clone project data")
                .setMessage("Enter the new project-data folder name.")
                .setView(input)
                .setPositiveButton("Clone", (d, w) -> {
                    String name = input.getText() == null ? "" :
                            input.getText().toString().trim().replaceAll("[^A-Za-z0-9._-]", "_");
                    if (name.isEmpty()) { SketchwareUtil.toastError("Invalid name"); return; }
                    run("Cloning…", () -> {
                        File target = new File(ProjectToolPaths.getSketchwareDir(),
                                "data" + File.separator + name);
                        ProjectCloneCore.cloneProject(ProjectToolPaths.getProjectDataDir(scId), target);
                        return "Cloned to:\n" + target.getAbsolutePath();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void run(String label, Work work) {
        append(label);
        executor.execute(() -> {
            String text;
            try { text = work.run(); } catch (Exception e) { text = "ERROR: " + e.getMessage(); }
            String result = text;
            runOnUiThread(() -> append(result));
        });
    }

    private void append(String text) {
        if (outputView != null) outputView.append("\n\n" + text);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }

    private interface Work { String run() throws Exception; }
}
