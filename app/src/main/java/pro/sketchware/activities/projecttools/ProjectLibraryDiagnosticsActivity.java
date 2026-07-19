package pro.sketchware.activities.projecttools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import pro.sketchware.library.LibraryConflictChecker;
import pro.sketchware.library.LibraryFolderScanner;
import pro.sketchware.library.LibraryHealthChecker;
import pro.sketchware.library.LibraryProjectLinker;
import pro.sketchware.library.LibraryUpdateManager;
import pro.sketchware.library.LibraryVersionChecker;
import pro.sketchware.library.LocalLibraryMetadata;
import pro.sketchware.util.library.BuiltInLibraryCompatibilityMatrix;
import pro.sketchware.util.library.BuiltInLibraryManager;
import pro.sketchware.utility.FileUtil;

public class ProjectLibraryDiagnosticsActivity extends BaseAppCompatActivity {

    private String scId;
    private TextInputEditText currentLibraryInput, replacementLibraryInput;
    private TextView diagnosticsView;
    private final LibraryUpdateManager libraryUpdateManager = new LibraryUpdateManager();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Library Diagnostics");
        toolbar.setSubtitle(scId == null ? "" : "Project " + scId);
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

        // ── Artifact Management ───────────────────────────────────────────────
        LinearLayout artifactBox = section(content, "Artifact Management",
                "Swap a library artifact with a replacement file, or undo the last swap.");
        currentLibraryInput = field(artifactBox, "Current library artifact path",
                InputType.TYPE_CLASS_TEXT);
        replacementLibraryInput = field(artifactBox, "Replacement library artifact path",
                InputType.TYPE_CLASS_TEXT);
        btn(artifactBox, "Replace library artifact with backup", this::replaceLibraryArtifact);
        btn(artifactBox, "Undo last library replacement", this::undoLastLibraryReplacement);

        // ── Diagnostics ───────────────────────────────────────────────────────
        LinearLayout diagBox = section(content, "Diagnostics",
                "Full report of built-in and local library health for this project.");
        btn(diagBox, "Refresh diagnostics", this::refreshDiagnostics);
        diagnosticsView = new TextView(this);
        diagnosticsView.setTextIsSelectable(true);
        diagnosticsView.setTextSize(12f);
        LinearLayout.LayoutParams diagLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        diagLp.setMargins(0, dp(8), 0, 0);
        diagnosticsView.setLayoutParams(diagLp);
        diagBox.addView(diagnosticsView);

        setContentView(root);
        refreshDiagnostics();
    }

    // ── Section / widget helpers ──────────────────────────────────────────────

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
        divLp.setMargins(0, subtitle == null ? dp(8) : 0, 0, dp(12));
        div.setLayoutParams(divLp);
        box.addView(div);

        card.addView(box);
        parent.addView(card);
        return box;
    }

    private TextInputEditText field(LinearLayout parent, String hint, int inputType) {
        TextInputLayout til = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint(hint);
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(inputType);
        til.addView(et);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        til.setLayoutParams(lp);
        parent.addView(til);
        return et;
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

    private void replaceLibraryArtifact() {
        try {
            String currentPath = inputText(currentLibraryInput);
            String replacementPath = inputText(replacementLibraryInput);
            if (currentPath.isEmpty() || replacementPath.isEmpty()) {
                diagnosticsView.setText("Both library paths are required.");
                return;
            }
            File replacement = new File(replacementPath);
            if (!replacement.isFile()) {
                diagnosticsView.setText("Replacement artifact does not exist: " + replacement.getAbsolutePath());
                return;
            }
            libraryUpdateManager.replace(new File(currentPath), replacement,
                    new File(ProjectToolPaths.getProjectBackupDir(scId), "libraries"));
            diagnosticsView.setText("Replaced library artifact: " + currentPath);
        } catch (Exception e) {
            diagnosticsView.setText("Library replacement failed: " + e.getMessage());
        }
    }

    private void undoLastLibraryReplacement() {
        try {
            boolean restored = libraryUpdateManager.undoLast();
            diagnosticsView.setText(restored
                    ? "Restored last replaced library artifact."
                    : "No in-memory library replacement to undo.");
        } catch (Exception e) {
            diagnosticsView.setText("Undo failed: " + e.getMessage());
        }
    }

    private void refreshDiagnostics() {
        StringBuilder report = new StringBuilder();
        BuiltInLibraryCompatibilityMatrix.ValidationResult validationResult =
                BuiltInLibraryCompatibilityMatrix.validate(scId);
        report.append("Built-in library configuration: ")
                .append(validationResult.isValid() ? "healthy" : "needs attention")
                .append("\n\n");

        if (validationResult.getErrors().isEmpty()) {
            report.append("No built-in library conflicts detected.\n\n");
        } else {
            report.append("Issues:\n");
            for (String error : validationResult.getErrors()) {
                report.append("• ").append(error).append("\n");
            }
            report.append('\n');
        }

        report.append("Required transitive built-in libraries:\n");
        for (String library : validationResult.getRequiredLibraries()) {
            report.append("• ").append(library).append("\n");
        }
        report.append('\n');

        ArrayList<HashMap<String, Object>> attachedLibraries =
                LocalLibrariesUtil.getLocalLibraries(scId);
        report.append("Attached local libraries: ").append(attachedLibraries.size()).append("\n");
        for (HashMap<String, Object> map : attachedLibraries) {
            Object name = map.get("name");
            if (name != null) report.append("• ").append(name).append("\n");
        }
        report.append('\n');

        String localLibFile = LocalLibrariesUtil.getLocalLibFile(scId).getAbsolutePath();
        report.append("Local library descriptor: ").append(localLibFile).append("\n");
        if (FileUtil.isExistFile(localLibFile)) {
            String descriptor = FileUtil.readFile(localLibFile);
            if (!TextUtils.isEmpty(descriptor)) {
                report.append("Descriptor bytes: ").append(descriptor.length()).append("\n");
            }
        }
        report.append('\n');

        try {
            BuiltInLibraryManager manager = new BuiltInLibraryManager(scId);
            List<String> missingClasspath = manager.validateClasspath();
            report.append("Built-in classpath check: ")
                    .append(missingClasspath.isEmpty() ? "ok" : "missing entries")
                    .append("\n");
            for (String missing : missingClasspath) {
                report.append("• missing classes.jar: ").append(missing).append("\n");
            }
            report.append('\n');
        } catch (Throwable t) {
            report.append("Built-in classpath check failed: ").append(t.getMessage()).append("\n\n");
        }

        try {
            File[] roots = new File[]{
                    new File(ProjectToolPaths.getSketchwareDir(), "libs"),
                    new File(ProjectToolPaths.getProjectDataDir(scId), "local_libraries"),
                    new File(ProjectToolPaths.getProjectDataDir(scId), "libs")
            };
            ArrayList<LocalLibraryMetadata> metadata = new ArrayList<>();
            for (File root : roots) {
                metadata.addAll(LibraryFolderScanner.scan(root));
            }
            LibraryHealthChecker healthChecker = new LibraryHealthChecker();
            List<LibraryHealthChecker.Issue> issues = healthChecker.check(metadata);
            report.append("Library artifact scan: ").append(metadata.size()).append(" jar/aar files\n");
            LibraryProjectLinker linker = new LibraryProjectLinker();
            for (LocalLibraryMetadata library : metadata) linker.add(library);
            report.append("Classpath artifacts linked: ").append(linker.classpath().size()).append("\n");
            List<String> conflicts = new LibraryConflictChecker().findNameConflicts(metadata);
            report.append("Name conflicts: ").append(conflicts.size()).append("\n");
            for (String conflict : conflicts) report.append("• ").append(conflict).append("\n");
            report.append("Health issues: ").append(issues.size()).append("\n");
            for (LibraryHealthChecker.Issue issue : issues) {
                report.append("• [").append(issue.severity).append("] ").append(issue.message);
                if (issue.file != null) report.append(" — ").append(issue.file.getAbsolutePath());
                report.append("\n");
            }
            report.append("Version compare sanity: 1.10.0 vs 1.9.9 = ")
                    .append(LibraryVersionChecker.compareVersions("1.10.0", "1.9.9"))
                    .append("\n");
        } catch (Throwable t) {
            report.append("Library artifact scan failed: ").append(t.getMessage()).append("\n");
        }

        diagnosticsView.setText(report.toString());
    }

    private String inputText(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
