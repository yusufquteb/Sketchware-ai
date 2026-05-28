package pro.sketchware.activities.projecttools;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
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
    private TextInputEditText inputUrl, inputToken, inputBranch;
    private TextInputEditText inputCommitTitle, inputCommitDesc;
    private TextInputEditText inputPatch;
    private TextView outputView;
    private TextView tvLoadingLabel;
    private LinearProgressIndicator progressBar;
    private MaterialCardView cardLoading;
    private List<MaterialButton> actionButtons;
    private boolean advancedVisible = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_git_workflow);

        scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID);
        if (scId == null || scId.trim().isEmpty()) {
            SketchwareUtil.toastError("Project id missing");
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar   = findViewById(R.id.progress_bar);
        cardLoading   = findViewById(R.id.card_loading);
        tvLoadingLabel = findViewById(R.id.tv_loading_label);

        inputUrl          = findViewById(R.id.input_url);
        inputToken        = findViewById(R.id.input_token);
        inputBranch       = findViewById(R.id.input_branch);
        inputCommitTitle  = findViewById(R.id.input_commit_title);
        inputCommitDesc   = findViewById(R.id.input_commit_desc);
        inputPatch        = findViewById(R.id.input_patch);
        outputView        = findViewById(R.id.output_view);

        actionButtons = Arrays.asList(
                (MaterialButton) findViewById(R.id.btn_pull),
                (MaterialButton) findViewById(R.id.btn_push),
                (MaterialButton) findViewById(R.id.btn_status),
                (MaterialButton) findViewById(R.id.btn_log),
                (MaterialButton) findViewById(R.id.btn_clone_mirror),
                (MaterialButton) findViewById(R.id.btn_import_gradle),
                (MaterialButton) findViewById(R.id.btn_switch_branch),
                (MaterialButton) findViewById(R.id.btn_apply_patch)
        );

        loadSavedConfig();
        wireButtons();
        setupAdvancedToggle();

        outputView.setText(
                "Repository: " + ProjectToolPaths.getProjectDataDir(scId).getAbsolutePath()
                + "\nMirror:     " + ProjectToolPaths.getProjectGitMirrorDir(scId).getAbsolutePath()
        );
    }

    private void loadSavedConfig() {
        GitConfig config = GitConfigStore.load(this, scId);
        inputUrl.setText(config.remoteUrl);
        inputToken.setText(config.token);
        inputBranch.setText(config.branch == null || config.branch.isEmpty() ? "main" : config.branch);
        inputCommitTitle.setText("Project update");
    }

    private void wireButtons() {
        MaterialButton btnSave        = findViewById(R.id.btn_save_config);
        MaterialButton btnPull        = actionButtons.get(0);
        MaterialButton btnPush        = actionButtons.get(1);
        MaterialButton btnStatus      = actionButtons.get(2);
        MaterialButton btnLog         = actionButtons.get(3);
        MaterialButton btnCloneMirror = actionButtons.get(4);
        MaterialButton btnImportGradle = actionButtons.get(5);
        MaterialButton btnSwitchBranch = actionButtons.get(6);
        MaterialButton btnApplyPatch  = actionButtons.get(7);
        MaterialButton btnClearOutput = findViewById(R.id.btn_clear_output);

        btnSave.setOnClickListener(v -> {
            GitConfigStore.save(this, readConfig());
            appendOutput("Configuration saved for project " + scId + ".");
        });

        btnPull.setOnClickListener(v ->
                runAsync("Pulling from remote…",
                        () -> GitRepositoryCore.pull(ProjectToolPaths.getProjectDataDir(scId), readConfig()))
        );

        btnPush.setOnClickListener(v -> confirmPush());

        btnStatus.setOnClickListener(v ->
                runAsyncText("Reading repository status…", this::statusSummary)
        );

        btnLog.setOnClickListener(v ->
                runAsyncText("Loading commit log…", this::recentCommits)
        );

        btnCloneMirror.setOnClickListener(v ->
                runAsync("Cloning remote mirror…",
                        () -> GitRepositoryCore.cloneRepository(ProjectToolPaths.getProjectGitMirrorDir(scId), readConfig()))
        );

        btnImportGradle.setOnClickListener(v ->
                runAsyncText("Importing Gradle files…", this::importGradleFilesFromMirror)
        );

        btnSwitchBranch.setOnClickListener(v ->
                runAsync("Switching branch…",
                        () -> GitRepositoryCore.switchBranch(ProjectToolPaths.getProjectDataDir(scId), readConfig(), getText(inputBranch)))
        );

        btnApplyPatch.setOnClickListener(v ->
                runAsync("Applying patch…", this::applyPatch)
        );

        btnClearOutput.setOnClickListener(v -> outputView.setText(""));
    }

    private void setupAdvancedToggle() {
        LinearLayout header  = findViewById(R.id.advanced_header);
        LinearLayout content = findViewById(R.id.advanced_content);
        ImageView    chevron = findViewById(R.id.advanced_chevron);

        header.setOnClickListener(v -> {
            advancedVisible = !advancedVisible;
            content.setVisibility(advancedVisible ? View.VISIBLE : View.GONE);
            ObjectAnimator.ofFloat(chevron, View.ROTATION, advancedVisible ? 180f : 0f)
                    .setDuration(200)
                    .start();
        });
    }

    // ── Loading state ─────────────────────────────────────────────────────────

    private void setLoading(boolean busy, String label) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        cardLoading.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy && label != null) tvLoadingLabel.setText(label);
        for (MaterialButton btn : actionButtons) btn.setEnabled(!busy);
    }

    // ── Async helpers ─────────────────────────────────────────────────────────

    private void runAsync(String label, GitCall call) {
        setLoading(true, label);
        GitConfigStore.save(this, readConfig());
        executor.execute(() -> {
            GitResult result = call.run();
            runOnUiThread(() -> {
                setLoading(false, null);
                appendOutput((result.success ? "✓ " : "✗ ") + result.message);
            });
        });
    }

    private void runAsyncText(String label, TextCall call) {
        setLoading(true, label);
        GitConfigStore.save(this, readConfig());
        executor.execute(() -> {
            String text;
            try {
                text = call.run();
            } catch (Exception e) {
                text = "✗ " + e.getMessage();
            }
            String finalText = text;
            runOnUiThread(() -> {
                setLoading(false, null);
                appendOutput(finalText);
            });
        });
    }

    // ── Git operations ────────────────────────────────────────────────────────

    private void confirmPush() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Commit and push?")
                .setMessage("Stages all project changes, creates a commit, and pushes to origin.")
                .setPositiveButton("Push", (d, w) ->
                        runAsync("Pushing to remote…", () -> GitRepositoryCore.push(
                                ProjectToolPaths.getProjectDataDir(scId),
                                readConfig(),
                                getText(inputCommitTitle),
                                getText(inputCommitDesc)
                        ))
                )
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private GitConfig readConfig() {
        return new GitConfig(scId, GitUrlNormalizer.normalize(getText(inputUrl)), getText(inputToken), getText(inputBranch));
    }

    private String importGradleFilesFromMirror() throws Exception {
        File mirror = ProjectToolPaths.getProjectGitMirrorDir(scId);
        List<File> files = GitDependencyImporter.collectDependencyFiles(mirror);
        if (files.isEmpty()) return "No Gradle-related files found in mirror.";
        File targetDir = new File(ProjectToolPaths.getProjectGradleInjectionDir(scId), "git-import");
        SafeFileOps.ensureDirectory(targetDir);
        StringBuilder out = new StringBuilder("Copied to ").append(targetDir.getAbsolutePath()).append('\n');
        for (File source : files) {
            File target = new File(targetDir, source.getName().replace('/', '_'));
            java.nio.file.Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            out.append("  • ").append(source.getName()).append('\n');
        }
        return out.toString();
    }

    private String statusSummary() throws Exception {
        File repo = ProjectToolPaths.getProjectDataDir(scId);
        if (!new File(repo, ".git").isDirectory()) {
            return "Not a Git repository. Push to initialize, or clone a mirror first.";
        }
        try (Git git = Git.open(repo)) {
            Status s = git.status().call();
            StringBuilder out = new StringBuilder();
            out.append("Branch: ").append(git.getRepository().getBranch()).append('\n');
            out.append("Clean:  ").append(s.isClean()).append('\n');
            appendSet(out, "Added",     s.getAdded());
            appendSet(out, "Changed",   s.getChanged());
            appendSet(out, "Modified",  s.getModified());
            appendSet(out, "Missing",   s.getMissing());
            appendSet(out, "Removed",   s.getRemoved());
            appendSet(out, "Untracked", s.getUntracked());
            return out.toString();
        }
    }

    private GitResult applyPatch() {
        String path = getText(inputPatch);
        if (path.isEmpty()) return GitResult.fail("Patch file path is empty", null);
        return GitPatchApplier.applyPatch(ProjectToolPaths.getProjectDataDir(scId), new File(path));
    }

    private String recentCommits() throws Exception {
        File mirror = ProjectToolPaths.getProjectGitMirrorDir(scId);
        if (!new File(mirror, ".git").isDirectory()) return "Mirror has not been cloned yet.";
        StringBuilder out = new StringBuilder("Recent commits:\n");
        for (String line : GitQuickLook.recentCommits(mirror, 20)) {
            out.append("  • ").append(line).append('\n');
        }
        return out.toString();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void appendSet(StringBuilder out, String name, java.util.Set<String> values) {
        if (values.isEmpty()) return;
        out.append(name).append(": ").append(values.size()).append('\n');
        int count = 0;
        for (String value : values) {
            if (count++ >= 10) { out.append("    …").append(values.size() - 10).append(" more\n"); break; }
            out.append("    • ").append(value).append('\n');
        }
    }

    private void appendOutput(String text) {
        if (outputView == null) return;
        CharSequence current = outputView.getText();
        outputView.setText(current.length() == 0 ? text : current + "\n\n" + text);
    }

    private String getText(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private interface GitCall  { GitResult run(); }
    private interface TextCall { String run() throws Exception; }
}
