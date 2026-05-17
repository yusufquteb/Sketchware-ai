package pro.sketchware.activities.projecttools;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.git.GitConfig;
import pro.sketchware.git.GitConfigStore;
import pro.sketchware.git.GitDependencyImporter;
import pro.sketchware.git.GitPatchApplier;
import pro.sketchware.git.GitQuickLook;
import pro.sketchware.git.GitRepositoryCore;
import pro.sketchware.git.GitResult;
import pro.sketchware.git.GitUrlNormalizer;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.io.SafeFileOps;

public class GitWorkflowActivity extends BaseAppCompatActivity {
    private String scId;
    private TextInputEditText urlInput, tokenInput, branchInput, titleInput, descriptionInput, patchFileInput;
    private TextView outputView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID);
        if (scId == null || scId.trim().isEmpty()) { SketchwareUtil.toastError("Project id missing"); finish(); return; }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle("Git Workflow"); toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); toolbar.setNavigationOnClickListener(v -> finish()); root.addView(toolbar);
        ScrollView scrollView = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); int pad = dp(16); content.setPadding(pad, pad, pad, pad * 2); scrollView.addView(content); root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        urlInput = addInput(content, "Remote repository URL", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 1);
        tokenInput = addInput(content, "Access token / password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 1);
        branchInput = addInput(content, "Branch", InputType.TYPE_CLASS_TEXT, 1);
        titleInput = addInput(content, "Commit title", InputType.TYPE_CLASS_TEXT, 1);
        descriptionInput = addInput(content, "Commit description", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, 4);
        GitConfig config = GitConfigStore.load(this, scId); urlInput.setText(config.remoteUrl); tokenInput.setText(config.token); branchInput.setText(config.branch == null || config.branch.isEmpty() ? "main" : config.branch); titleInput.setText("Project update");
        addButton(content, "Save Git configuration", () -> { GitConfigStore.save(this, readConfig()); append("Saved configuration for " + scId + "."); });
        addButton(content, "Clone / refresh remote mirror", () -> runAsync("Cloning remote mirror", () -> GitRepositoryCore.cloneRepository(ProjectToolPaths.getProjectGitMirrorDir(scId), readConfig())));
        addButton(content, "Import Gradle files from mirror", () -> runAsyncText("Importing Gradle files", this::importGradleFilesFromMirror));
        addButton(content, "Pull current project repository", () -> runAsync("Pulling current project", () -> GitRepositoryCore.pull(ProjectToolPaths.getProjectDataDir(scId), readConfig())));
        addButton(content, "Commit and push current project", this::confirmPush);
        addButton(content, "Switch current project branch", () -> runAsync("Switching branch", () -> GitRepositoryCore.switchBranch(ProjectToolPaths.getProjectDataDir(scId), readConfig(), getText(branchInput))));
        patchFileInput = addInput(content, "Patch file path", InputType.TYPE_CLASS_TEXT, 1);
        addButton(content, "Apply patch to current project", () -> runAsync("Applying patch", this::applyPatch));
        addButton(content, "Show current project status", () -> runAsyncText("Reading status", this::statusSummary));
        addButton(content, "Show recent commits from mirror", () -> runAsyncText("Reading commit log", this::recentCommits));
        outputView = new TextView(this); outputView.setTextIsSelectable(true); outputView.setGravity(Gravity.START); outputView.setPadding(0, dp(12), 0, 0); outputView.setText("Project repository: " + ProjectToolPaths.getProjectDataDir(scId).getAbsolutePath() + "\nRemote mirror: " + ProjectToolPaths.getProjectGitMirrorDir(scId).getAbsolutePath()); content.addView(outputView);
        setContentView(root);
    }

    private TextInputEditText addInput(LinearLayout parent, String hint, int inputType, int minLines) { TextInputLayout layout = new TextInputLayout(this); layout.setHint(hint); TextInputEditText input = new TextInputEditText(this); input.setInputType(inputType); input.setMinLines(minLines); if (minLines > 1) input.setGravity(Gravity.TOP | Gravity.START); layout.addView(input); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, dp(8)); parent.addView(layout, lp); return input; }
    private void addButton(LinearLayout parent, String label, Runnable runnable) { MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); button.setText(label); button.setOnClickListener(v -> runnable.run()); parent.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); }
    private void confirmPush() { new MaterialAlertDialogBuilder(this).setTitle("Commit and push?").setMessage("This stages the current project data, creates a commit if there are changes, and pushes to origin.").setPositiveButton("Push", (d, w) -> runAsync("Pushing project", () -> GitRepositoryCore.push(ProjectToolPaths.getProjectDataDir(scId), readConfig(), getText(titleInput), getText(descriptionInput)))).setNegativeButton(android.R.string.cancel, null).show(); }
    private GitConfig readConfig() { return new GitConfig(scId, GitUrlNormalizer.normalize(getText(urlInput)), getText(tokenInput), getText(branchInput)); }
    private void runAsync(String label, GitCall call) { append(label + "..."); GitConfigStore.save(this, readConfig()); executor.execute(() -> { GitResult result = call.run(); runOnUiThread(() -> append((result.success ? "OK: " : "ERROR: ") + result.message)); }); }
    private void runAsyncText(String label, TextCall call) { append(label + "..."); GitConfigStore.save(this, readConfig()); executor.execute(() -> { String text; try { text = call.run(); } catch (Exception e) { text = "ERROR: " + e.getMessage(); } String finalText = text; runOnUiThread(() -> append(finalText)); }); }
    private String importGradleFilesFromMirror() throws Exception { File mirror = ProjectToolPaths.getProjectGitMirrorDir(scId); List<File> files = GitDependencyImporter.collectDependencyFiles(mirror); if (files.isEmpty()) return "No Gradle-related files found in mirror."; File targetDir = new File(ProjectToolPaths.getProjectGradleInjectionDir(scId), "git-import"); SafeFileOps.ensureDirectory(targetDir); StringBuilder out = new StringBuilder("Copied Gradle-related files into ").append(targetDir.getAbsolutePath()).append('\n'); for (File source : files) { File target = new File(targetDir, source.getName().replace('/', '_')); java.nio.file.Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); out.append("• ").append(source.getAbsolutePath()).append(" → ").append(target.getName()).append('\n'); } return out.toString(); }
    private String statusSummary() throws Exception { File repo = ProjectToolPaths.getProjectDataDir(scId); if (!new File(repo, ".git").isDirectory()) return "Current project is not initialized as a Git repository. Use commit/push after setting a remote, or clone a mirror first."; try (Git git = Git.open(repo)) { Status s = git.status().call(); StringBuilder out = new StringBuilder(); out.append("Branch: ").append(git.getRepository().getBranch()).append('\n'); out.append("Clean: ").append(s.isClean()).append('\n'); appendSet(out, "Added", s.getAdded()); appendSet(out, "Changed", s.getChanged()); appendSet(out, "Modified", s.getModified()); appendSet(out, "Missing", s.getMissing()); appendSet(out, "Removed", s.getRemoved()); appendSet(out, "Untracked", s.getUntracked()); return out.toString(); } }
    private GitResult applyPatch() { String path = getText(patchFileInput); if (path.isEmpty()) return GitResult.fail("Patch file path is empty", null); return GitPatchApplier.applyPatch(ProjectToolPaths.getProjectDataDir(scId), new File(path)); }
    private String recentCommits() throws Exception { File mirror = ProjectToolPaths.getProjectGitMirrorDir(scId); if (!new File(mirror, ".git").isDirectory()) return "Remote mirror has not been cloned yet."; StringBuilder out = new StringBuilder("Recent commits:\n"); for (String line : GitQuickLook.recentCommits(mirror, 20)) out.append("• ").append(line).append('\n'); return out.toString(); }
    private void appendSet(StringBuilder out, String name, java.util.Set<String> values) { out.append(name).append(": ").append(values.size()).append('\n'); int count = 0; for (String value : values) { if (count++ >= 25) { out.append("  …").append(values.size() - 25).append(" more\n"); break; } out.append("  • ").append(value).append('\n'); } }
    private String getText(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString().trim(); }
    private void append(String text) { if (outputView != null) outputView.append("\n\n" + text); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
    private interface GitCall { GitResult run(); }
    private interface TextCall { String run() throws Exception; }
}
