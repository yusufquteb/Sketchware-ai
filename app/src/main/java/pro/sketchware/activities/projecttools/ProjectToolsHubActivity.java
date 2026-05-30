package pro.sketchware.activities.projecttools;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.project.backup.BackupRestoreManager;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class ProjectToolsHubActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    public static final int RESULT_BUILD_SIGNED_APK = 1001;
    public static final int RESULT_BUILD_SIGNED_AAB = 1002;

    private String scId;
    // Holds category LinearLayout containers for chip filtering
    private final List<LinearLayout> sectionContainers = new ArrayList<>();

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

        // Horizontally scrollable ChipGroup for filtering
        android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams hScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hScroll.setLayoutParams(hScrollLp);

        ChipGroup chipGroup = new ChipGroup(this);
        chipGroup.setSingleSelection(true);
        int chipPad = dp(16);
        chipGroup.setPadding(chipPad, dp(8), chipPad, dp(8));

        String[] chipLabels = {"All", "Project", "Files", "Build", "Debug", "Settings"};
        for (int i = 0; i < chipLabels.length; i++) {
            Chip chip = new Chip(this, null,
                    com.google.android.material.R.attr.chipStyle);
            chip.setText(chipLabels[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            final String category = chipLabels[i];
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    filterSections(category);
                }
            });
            chipGroup.addView(chip);
        }
        hScroll.addView(chipGroup);
        root.addView(hScroll);

        // Content: NestedScrollView
        NestedScrollView scrollView = new NestedScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, dp(32));

        // ── Project Section ───────────────────────────────────────────────────
        LinearLayout projectSection = new LinearLayout(this);
        projectSection.setOrientation(LinearLayout.VERTICAL);
        projectSection.setTag("Project");

        GridLayout projectGrid = createGrid();

        addGridItem(projectGrid, R.drawable.ic_mtrl_edit, "Rename",
                "Change the project name", () -> showRenameDialog());
        addGridItem(projectGrid, R.drawable.ic_mtrl_download, "Import backup",
                "Restore a .swb backup file",
                () -> new BackupRestoreManager(this, null).restore());
        addGridItem(projectGrid, R.drawable.ic_mtrl_history, "Backup & Restore",
                "Backup, restore, or clean project files",
                () -> startActivity(new Intent(this, ProjectLifecycleActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));

        projectSection.addView(projectGrid);
        content.addView(projectSection);
        sectionContainers.add(projectSection);

        // ── Files Section ─────────────────────────────────────────────────────
        LinearLayout filesSection = new LinearLayout(this);
        filesSection.setOrientation(LinearLayout.VERTICAL);
        filesSection.setTag("Files");

        GridLayout filesGrid = createGrid();

        addGridItem(filesGrid, R.drawable.ic_mtrl_folder, "File Manager",
                "Browse and edit project files",
                () -> startActivity(new Intent(this, ProjectFileManagerActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));
        addGridItem(filesGrid, R.drawable.ic_mtrl_frame_source, "Search",
                "Find text across project files",
                () -> startActivity(new Intent(this, SearchInProjectActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));
        addGridItem(filesGrid, R.drawable.ic_mtrl_database_edit, "Logic / View Data",
                "Browse encrypted Sketchware data files",
                () -> startActivity(new Intent(this, ProjectFileManagerActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));

        filesSection.addView(filesGrid);
        content.addView(filesSection);
        sectionContainers.add(filesSection);

        // ── Build Section ─────────────────────────────────────────────────────
        LinearLayout buildSection = new LinearLayout(this);
        buildSection.setOrientation(LinearLayout.VERTICAL);
        buildSection.setTag("Build");

        GridLayout buildGrid = createGrid();

        addGridItem(buildGrid, R.drawable.ic_mtrl_inject, "Gradle Injection",
                "Inject code into build scripts",
                () -> startActivity(new Intent(this, GradleInjectionActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));
        addGridItem(buildGrid, R.drawable.ic_mtrl_export, "Import Helpers",
                "Analyze imports and fix statements",
                () -> startActivity(new Intent(this, ImportConversionActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));
        addGridItem(buildGrid, R.drawable.ic_mtrl_apk_document, "Build Signed APK",
                "Export a signed APK for release",
                () -> { setResult(RESULT_BUILD_SIGNED_APK); finish(); });
        addGridItem(buildGrid, R.drawable.ic_mtrl_apk_install, "Build Signed AAB",
                "Export a signed AAB bundle",
                () -> { setResult(RESULT_BUILD_SIGNED_AAB); finish(); });

        buildSection.addView(buildGrid);
        content.addView(buildSection);
        sectionContainers.add(buildSection);

        // ── Debug Section ─────────────────────────────────────────────────────
        LinearLayout debugSection = new LinearLayout(this);
        debugSection.setOrientation(LinearLayout.VERTICAL);
        debugSection.setTag("Debug");

        GridLayout debugGrid = createGrid();

        addGridItem(debugGrid, R.drawable.ic_mtrl_bug_report, "Build Diagnostics",
                "Diagnose compiler errors and logs",
                () -> startActivity(new Intent(this, BuildDiagnosticsActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));
        addGridItem(debugGrid, R.drawable.ic_mtrl_package, "Library Diagnostics",
                "Check library DEX and dependencies",
                () -> startActivity(new Intent(this, ProjectLibraryDiagnosticsActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));
        addGridItem(debugGrid, R.drawable.ic_mtrl_terminal, "Developer Tools",
                "Keystore, class cloning, system logs",
                () -> startActivity(new Intent(this, DeveloperToolsActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));
        addGridItem(debugGrid, R.drawable.ic_mtrl_terminal, "Terminal",
                "Run shell commands",
                () -> startActivity(new Intent(this, pro.sketchware.activities.terminal.TerminalActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));

        debugSection.addView(debugGrid);
        content.addView(debugSection);
        sectionContainers.add(debugSection);

        // ── Settings Section ──────────────────────────────────────────────────
        LinearLayout settingsSection = new LinearLayout(this);
        settingsSection.setOrientation(LinearLayout.VERTICAL);
        settingsSection.setTag("Settings");

        GridLayout settingsGrid = createGrid();

        addGridItem(settingsGrid, R.drawable.ic_mtrl_settings, "Advanced Settings",
                "Gradle, ProGuard, and build config",
                () -> startActivity(new Intent(this, AdvancedProjectSettingsActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));
        addGridItem(settingsGrid, R.drawable.ic_mtrl_sync, "Git Workflow",
                "Pull, push, and manage branches",
                () -> startActivity(new Intent(this, GitWorkflowActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));
        addGridItem(settingsGrid, R.drawable.ic_mtrl_screen, "Activities",
                "Add, clone, or rename screens",
                () -> startActivity(new Intent(this, ActivityManagerActivity.class)
                        .putExtra(EXTRA_SC_ID, scId)));

        settingsSection.addView(settingsGrid);
        content.addView(settingsSection);
        sectionContainers.add(settingsSection);

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private GridLayout createGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.setMargins(0, 0, 0, dp(8));
        grid.setLayoutParams(gridLp);
        return grid;
    }

    private void addGridItem(GridLayout grid, int iconRes, String title,
                             String description, Runnable action) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(20));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setCardElevation(0f);
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> action.run());

        GridLayout.LayoutParams cardLp = new GridLayout.LayoutParams();
        cardLp.width = 0;
        cardLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        cardLp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardLp.setMargins(dp(6), dp(6), dp(6), dp(6));
        card.setLayoutParams(cardLp);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Icon wrapper
        LinearLayout iconWrapper = new LinearLayout(this);
        iconWrapper.setGravity(Gravity.CENTER);
        int wrapSize = dp(40);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(wrapSize, wrapSize);
        wrapLp.setMargins(0, 0, 0, dp(10));
        iconWrapper.setLayoutParams(wrapLp);
        iconWrapper.setBackground(makeRoundedBackground(
                ThemeUtils.getColor(this, R.attr.colorSecondaryContainer), dp(12)));

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSecondaryContainer));
        int imgSize = dp(28);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(imgSize, imgSize));
        iconWrapper.addView(iconView);
        inner.addView(iconWrapper);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14f);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, 0, 0, dp(2));
        titleView.setLayoutParams(titleLp);
        inner.addView(titleView);

        // Description
        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextSize(11f);
        descView.setAlpha(0.65f);
        descView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        inner.addView(descView);

        card.addView(inner);
        grid.addView(card);
    }

    private void filterSections(String category) {
        for (LinearLayout container : sectionContainers) {
            Object tag = container.getTag();
            if ("All".equals(category)) {
                container.setVisibility(View.VISIBLE);
            } else {
                container.setVisibility(category.equals(tag) ? View.VISIBLE : View.GONE);
            }
        }
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

    private android.graphics.drawable.GradientDrawable makeRoundedBackground(int color, int radius) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(radius);
        bg.setColor(color);
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
