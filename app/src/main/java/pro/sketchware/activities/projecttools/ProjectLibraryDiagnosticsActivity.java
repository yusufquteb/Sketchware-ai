package pro.sketchware.activities.projecttools;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.editor.manage.library.ManageLibraryActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity;
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
    private TextInputEditText currentLibraryInput;
    private TextInputEditText replacementLibraryInput;
    private TextView diagnosticsView;
    private final LibraryUpdateManager libraryUpdateManager = new LibraryUpdateManager();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra("sc_id");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Library Diagnostics");
        toolbar.setSubtitle(scId == null ? "" : "Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        int pad = dp(16);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        MaterialButton manageBuiltIn = new MaterialButton(this);
        manageBuiltIn.setText("Open built-in libraries");
        manageBuiltIn.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManageLibraryActivity.class);
            intent.putExtra("sc_id", scId);
            startActivity(intent);
        });
        content.addView(manageBuiltIn);

        MaterialButton manageLocal = new MaterialButton(this);
        manageLocal.setText("Open local libraries");
        manageLocal.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManageLocalLibraryActivity.class);
            intent.putExtra("sc_id", scId);
            startActivity(intent);
        });
        content.addView(manageLocal);

        currentLibraryInput = addInput(content, "Current library artifact path", InputType.TYPE_CLASS_TEXT);
        replacementLibraryInput = addInput(content, "Replacement library artifact path", InputType.TYPE_CLASS_TEXT);

        MaterialButton replaceLibrary = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        replaceLibrary.setText("Replace library artifact with backup");
        replaceLibrary.setOnClickListener(v -> replaceLibraryArtifact());
        content.addView(replaceLibrary);

        MaterialButton undoLibraryReplace = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        undoLibraryReplace.setText("Undo last library replacement");
        undoLibraryReplace.setOnClickListener(v -> undoLastLibraryReplacement());
        content.addView(undoLibraryReplace);

        MaterialButton refresh = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        refresh.setText("Refresh diagnostics");
        refresh.setOnClickListener(v -> refreshDiagnostics());
        content.addView(refresh);

        diagnosticsView = new TextView(this);
        diagnosticsView.setTextIsSelectable(true);
        diagnosticsView.setPadding(0, pad, 0, 0);
        content.addView(diagnosticsView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f));

        setContentView(root);
        refreshDiagnostics();
    }

    private TextInputEditText addInput(LinearLayout parent, String hint, int inputType) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(inputType);
        layout.addView(input);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        parent.addView(layout, lp);
        return input;
    }

    private void replaceLibraryArtifact() {
        try {
            File current = new File(inputText(currentLibraryInput));
            File replacement = new File(inputText(replacementLibraryInput));
            if (inputText(currentLibraryInput).isEmpty() || inputText(replacementLibraryInput).isEmpty()) {
                diagnosticsView.setText("Both library paths are required.");
                return;
            }
            if (!replacement.isFile()) {
                diagnosticsView.setText("Replacement artifact does not exist: " + replacement.getAbsolutePath());
                return;
            }
            libraryUpdateManager.replace(current, replacement, new File(ProjectToolPaths.getProjectBackupDir(scId), "libraries"));
            diagnosticsView.setText("Replaced library artifact: " + current.getAbsolutePath());
        } catch (Exception e) {
            diagnosticsView.setText("Library replacement failed: " + e.getMessage());
        }
    }

    private void undoLastLibraryReplacement() {
        try {
            boolean restored = libraryUpdateManager.undoLast();
            diagnosticsView.setText(restored ? "Restored last replaced library artifact." : "No in-memory library replacement to undo.");
        } catch (Exception e) {
            diagnosticsView.setText("Undo failed: " + e.getMessage());
        }
    }

    private String inputText(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

        ArrayList<HashMap<String, Object>> attachedLibraries = LocalLibrariesUtil.getLocalLibraries(scId);
        report.append("Attached local libraries: ").append(attachedLibraries.size()).append("\n");
        for (HashMap<String, Object> map : attachedLibraries) {
            Object name = map.get("name");
            if (name != null) {
                report.append("• ").append(name).append("\n");
            }
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
            for (LocalLibraryMetadata library : metadata) {
                linker.add(library);
            }
            report.append("Classpath artifacts linked: ").append(linker.classpath().size()).append("\n");
            List<String> conflicts = new LibraryConflictChecker().findNameConflicts(metadata);
            report.append("Name conflicts: ").append(conflicts.size()).append("\n");
            for (String conflict : conflicts) {
                report.append("• ").append(conflict).append("\n");
            }
            report.append("Health issues: ").append(issues.size()).append("\n");
            for (LibraryHealthChecker.Issue issue : issues) {
                report.append("• [").append(issue.severity).append("] ").append(issue.message);
                if (issue.file != null) {
                    report.append(" — ").append(issue.file.getAbsolutePath());
                }
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
}
