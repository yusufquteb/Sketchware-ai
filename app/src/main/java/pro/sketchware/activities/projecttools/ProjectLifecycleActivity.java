package pro.sketchware.activities.projecttools;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.project.ProjectBackupCore;
import pro.sketchware.project.ProjectCleanupCore;
import pro.sketchware.project.ProjectCloneCore;
import pro.sketchware.project.ProjectExportEnhancer;
import pro.sketchware.project.ProjectRestoreCore;
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
        if (scId == null || scId.trim().isEmpty()) { SketchwareUtil.toastError("Project id missing"); finish(); return; }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle("Project Lifecycle"); toolbar.setSubtitle("Project " + scId); toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); toolbar.setNavigationOnClickListener(v -> finish()); root.addView(toolbar);
        ScrollView scrollView = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); int pad = dp(16); content.setPadding(pad, pad, pad, pad * 2); scrollView.addView(content); root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addButton(content, "Create project-data backup", () -> run("Creating backup", this::createBackup));
        addButton(content, "Export generated project", () -> run("Exporting generated project", this::exportGeneratedProject));
        addButton(content, "Clean generated temporary files", this::confirmCleanup);
        addButton(content, "Clone project data", this::showCloneDialog);
        addButton(content, "Restore backup into project data", this::showRestoreDialog);
        outputView = new TextView(this); outputView.setTextIsSelectable(true); outputView.setGravity(Gravity.START); outputView.setPadding(0, dp(12), 0, 0); outputView.setText("Project data: " + ProjectToolPaths.getProjectDataDir(scId).getAbsolutePath() + "\nGenerated project: " + ProjectToolPaths.getProjectMyscDir(scId).getAbsolutePath() + "\nBackups: " + ProjectToolPaths.getProjectBackupDir(scId).getAbsolutePath() + "\nExports: " + ProjectToolPaths.getProjectExportDir(scId).getAbsolutePath()); content.addView(outputView);
        setContentView(root);
    }
    private void addButton(LinearLayout parent, String label, Runnable action) { MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); button.setText(label); button.setOnClickListener(v -> action.run()); parent.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); }
    private String createBackup() throws Exception { File backup = ProjectBackupCore.backup(ProjectToolPaths.getProjectDataDir(scId), ProjectToolPaths.getProjectBackupDir(scId), scId); return "Backup written: " + backup.getAbsolutePath(); }
    private String exportGeneratedProject() throws Exception { File source = ProjectToolPaths.getProjectMyscDir(scId).exists() ? ProjectToolPaths.getProjectMyscDir(scId) : ProjectToolPaths.getProjectDataDir(scId); File exported = ProjectExportEnhancer.exportZip(source, ProjectToolPaths.getProjectExportDir(scId), scId + "_export"); return "Export written: " + exported.getAbsolutePath(); }
    private void confirmCleanup() { new MaterialAlertDialogBuilder(this).setTitle("Clean generated files?").setMessage("This removes generated build directories and common temporary folders. Project data is not deleted.").setPositiveButton("Clean", (d, w) -> run("Cleaning", () -> { int removed = 0; removed += ProjectCleanupCore.clean(ProjectToolPaths.getProjectMyscDir(scId)); removed += ProjectCleanupCore.clean(ProjectToolPaths.getProjectDataDir(scId)); return "Removed " + removed + " temporary entries."; })).setNegativeButton(android.R.string.cancel, null).show(); }
    private void showCloneDialog() { EditText input = new EditText(this); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setText(scId + "_copy_" + System.currentTimeMillis()); input.setSelectAllOnFocus(true); int pad = dp(20); input.setPadding(pad, pad / 2, pad, pad / 2); new MaterialAlertDialogBuilder(this).setTitle("Clone project data").setMessage("Enter the new project-data folder name. This copies only project data files; project list metadata is not modified.").setView(input).setPositiveButton("Clone", (d, w) -> { String name = input.getText() == null ? "" : input.getText().toString().trim().replaceAll("[^A-Za-z0-9._-]", "_"); if (name.isEmpty()) { SketchwareUtil.toastError("Invalid clone name"); return; } run("Cloning", () -> { File target = new File(ProjectToolPaths.getSketchwareDir(), "data" + File.separator + name); ProjectCloneCore.cloneProject(ProjectToolPaths.getProjectDataDir(scId), target); return "Project data cloned to: " + target.getAbsolutePath(); }); }).setNegativeButton(android.R.string.cancel, null).show(); }
    private void showRestoreDialog() { EditText input = new EditText(this); input.setSingleLine(false); input.setMinLines(2); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setHint("/storage/emulated/0/.sketchware/backups/...zip"); int pad = dp(20); input.setPadding(pad, pad / 2, pad, pad / 2); new MaterialAlertDialogBuilder(this).setTitle("Restore backup").setMessage("Restoring replaces the current project-data folder. Create a backup first if needed.").setView(input).setPositiveButton("Restore", (d, w) -> { String path = input.getText() == null ? "" : input.getText().toString().trim(); if (path.isEmpty()) { SketchwareUtil.toastError("Backup path missing"); return; } new MaterialAlertDialogBuilder(this).setTitle("Replace project data?").setMessage("Current project data will be replaced by the selected backup.").setPositiveButton("Replace", (cd, cw) -> run("Restoring", () -> { ProjectRestoreCore.restore(new File(path), ProjectToolPaths.getProjectDataDir(scId), true); return "Restored backup from: " + path; })).setNegativeButton(android.R.string.cancel, null).show(); }).setNegativeButton(android.R.string.cancel, null).show(); }
    private void run(String label, Work work) { append(label + "..."); executor.execute(() -> { String text; try { text = work.run(); } catch (Exception e) { text = "ERROR: " + e.getMessage(); } String finalText = text; runOnUiThread(() -> append(finalText)); }); }
    private void append(String text) { if (outputView != null) outputView.append("\n\n" + text); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
    private interface Work { String run() throws Exception; }
}
