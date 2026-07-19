package pro.sketchware.activities.projecttools;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.core.widget.NestedScrollView;

import com.besome.sketch.editor.manage.library.LibraryCategoryView;
import com.besome.sketch.editor.manage.library.LibraryItemView;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

/**
 * Project Tools hub. Laid out the same way as the Library Manager / App Settings
 * screens: a section title followed by a card-style list of items
 * ({@link LibraryCategoryView} + {@link LibraryItemView}).
 */
public class ProjectToolsHubActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    public static final int RESULT_BUILD_SIGNED_APK = 1001;
    public static final int RESULT_BUILD_SIGNED_AAB = 1002;
    public static final int RESULT_SHOW_SOURCE_CODE = 1003;
    public static final int RESULT_SHOW_APK_SIGNATURES = 1004;
    public static final int RESULT_DIRECT_JAVA_EDITOR = 1005;

    private String scId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra(EXTRA_SC_ID);
        if (scId == null || scId.trim().isEmpty()) {
            SketchwareUtil.toastError("Project id missing");
            finish();
            return;
        }
        buildUi();
    }

    private void buildUi() {
        String projectName = getProjectName();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

        // AppBarLayout + MaterialToolbar
        AppBarLayout appBarLayout = new AppBarLayout(this);
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Project Tools");
        toolbar.setSubtitle(projectName);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        appBarLayout.addView(toolbar, new AppBarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(appBarLayout);

        // Content: NestedScrollView containing stacked category cards, exactly like
        // Library Manager / App Settings (title above each card list).
        NestedScrollView scrollView = new NestedScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, dp(32));

        // ── Project ──────────────────────────────────────────────────────────
        LibraryCategoryView projectCategory = new LibraryCategoryView(this);
        projectCategory.setTitle("Project");
        content.addView(projectCategory);

        projectCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_edit, "Rename",
                "Change the project name", v -> showRenameDialog()), true);
        projectCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_history, "Backup & Restore",
                "Backup, restore, or clean project files",
                v -> startActivity(new Intent(this, ProjectLifecycleActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), false);

        // ── Build ────────────────────────────────────────────────────────────
        LibraryCategoryView buildCategory = new LibraryCategoryView(this);
        buildCategory.setTitle("Build");
        content.addView(buildCategory);

        buildCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_inject, "Gradle Injection",
                "Inject code into build scripts",
                v -> startActivity(new Intent(this, GradleInjectionActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), true);
        buildCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_code, "Java → Blocks",
                "Convert Java statements to addSourceDirectly blocks",
                v -> startActivity(new Intent(this, JavaToBlocksActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), true);
        buildCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_export, "Import Helpers",
                "Analyze imports and fix statements",
                v -> startActivity(new Intent(this, ImportConversionActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), true);
        buildCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_apk_document, "Build Signed APK",
                "Export a signed APK for release",
                v -> { setResult(RESULT_BUILD_SIGNED_APK); finish(); }), true);
        buildCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_apk_install, "Build Signed AAB",
                "Export a signed AAB bundle",
                v -> { setResult(RESULT_BUILD_SIGNED_AAB); finish(); }), false);

        // ── Debug ────────────────────────────────────────────────────────────
        LibraryCategoryView debugCategory = new LibraryCategoryView(this);
        debugCategory.setTitle("Debug");
        content.addView(debugCategory);

        debugCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_bug_report, "Build Diagnostics",
                "Diagnose compiler errors and logs",
                v -> startActivity(new Intent(this, BuildDiagnosticsActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), true);
        debugCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_package, "Library Diagnostics",
                "Check library DEX and dependencies",
                v -> startActivity(new Intent(this, ProjectLibraryDiagnosticsActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), true);
        debugCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_terminal, "Developer Tools",
                "Keystore, class cloning, system logs",
                v -> startActivity(new Intent(this, DeveloperToolsActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), true);
        // Moved here from the Design screen's overflow menu:
        debugCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_frame_source, "Show source code",
                "View the generated source for the current screen",
                v -> { setResult(RESULT_SHOW_SOURCE_CODE); finish(); }), true);
        debugCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_shield_lock, "Show Apk signatures",
                "Inspect the signature of the last built APK",
                v -> { setResult(RESULT_SHOW_APK_SIGNATURES); finish(); }), true);
        debugCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_java, "Direct activity Java editor",
                "Manually edit the generated Java for the current screen",
                v -> { setResult(RESULT_DIRECT_JAVA_EDITOR); finish(); }), false);

        // ── Settings ─────────────────────────────────────────────────────────
        LibraryCategoryView settingsCategory = new LibraryCategoryView(this);
        settingsCategory.setTitle("Settings");
        content.addView(settingsCategory);

        settingsCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_settings, "Advanced Settings",
                "Gradle, ProGuard, and build config",
                v -> startActivity(new Intent(this, AdvancedProjectSettingsActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), true);
        settingsCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_sync, "Git Workflow",
                "Pull, push, and manage branches",
                v -> startActivity(new Intent(this, GitWorkflowActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), true);
        settingsCategory.addLibraryItem(createItem(R.drawable.ic_mtrl_screen, "Activities",
                "Add, clone, or rename screens",
                v -> startActivity(new Intent(this, ActivityManagerActivity.class)
                        .putExtra(EXTRA_SC_ID, scId))), false);

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LibraryItemView createItem(int iconRes, String title, String description,
                                        View.OnClickListener listener) {
        LibraryItemView item = new LibraryItemView(this);
        item.setHideEnabled();
        item.icon.setImageResource(iconRes);
        item.title.setText(title);
        item.description.setText(description);
        item.setOnClickListener(listener);
        return item;
    }

    private void showRenameDialog() {
        HashMap<String, Object> map = lC.b(scId);
        String currentName = map != null ? yB.c(map, "my_sc_app_name") : "";

        EditText editText = new EditText(this);
        editText.setText(currentName);
        editText.setSingleLine();
        int hPad = dp(20);
        editText.setPadding(hPad, dp(12), hPad, dp(12));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename project")
                .setMessage("Enter a new name for this project:")
                .setView(editText)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newName = editText.getText() == null ? "" : editText.getText().toString().trim();
                    if (newName.isEmpty()) {
                        SketchwareUtil.toastError("Name cannot be empty");
                        return;
                    }
                    HashMap<String, Object> projectMap = lC.b(scId);
                    if (projectMap != null) {
                        projectMap.put("my_sc_app_name", newName);
                        lC.a(scId, projectMap);
                        SketchwareUtil.toast("Project renamed to: " + newName);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String getProjectName() {
        try {
            HashMap<String, Object> map = lC.b(scId);
            if (map != null) {
                String name = yB.c(map, "my_sc_app_name");
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {}
        return "Project " + scId;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
