package pro.sketchware.activities.projecttools;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;

import pro.sketchware.utility.SketchwareUtil;

public class ProjectToolsHubActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    /** Result codes sent back to DesignActivity when the user picks a release build. */
    public static final int RESULT_BUILD_SIGNED_APK = 1001;
    public static final int RESULT_BUILD_SIGNED_AAB = 1002;

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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Project Tools");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, pad * 2);

        // ── Screens ──────────────────────────────────────────────────────────
        addSection(content, "Screens",
                "Add, clone, or rename activities in the current project.",
                new Action[]{
                        new Action("Manage activities", ActivityManagerActivity.class)
                });

        // ── Files ─────────────────────────────────────────────────────────────
        addSection(content, "Files",
                "Browse, edit, and search project files.",
                new Action[]{
                        new Action("File manager", ProjectFileManagerActivity.class),
                        new Action("Search in project", SearchInProjectActivity.class)
                });

        // ── Source control ────────────────────────────────────────────────────
        addSection(content, "Source control",
                "Clone, configure, pull, and push Git-backed projects.",
                new Action[]{
                        new Action("Git workflow", GitWorkflowActivity.class)
                });

        // ── Build ─────────────────────────────────────────────────────────────
        addSection(content, "Build",
                "Review compiler inputs, Gradle config, and library diagnostics.",
                new Action[]{
                        new Action("Compiler diagnostics", BuildDiagnosticsActivity.class),
                        new Action("Gradle injection", GradleInjectionActivity.class),
                        new Action("Library diagnostics", ProjectLibraryDiagnosticsActivity.class)
                });

        // ── Release builds ────────────────────────────────────────────────────
        addSection(content, "Release builds",
                "Build a signed APK or Android App Bundle (AAB) for distribution.",
                new Action[]{
                        new Action("Build signed APK", RESULT_BUILD_SIGNED_APK),
                        new Action("Build signed AAB", RESULT_BUILD_SIGNED_AAB)
                });

        // ── Project lifecycle ─────────────────────────────────────────────────
        addSection(content, "Project lifecycle",
                "Backup, restore, export, clone, and clean the current project.",
                new Action[]{
                        new Action("Backup / restore / cleanup", ProjectLifecycleActivity.class)
                });

        // ── Import & conversion ───────────────────────────────────────────────
        addSection(content, "Import and conversion",
                "Analyze Java imports, split statements, and validate layouts.",
                new Action[]{
                        new Action("Import helpers", ImportConversionActivity.class)
                });

        // ── Developer tools ───────────────────────────────────────────────────
        addSection(content, "Developer tools",
                "Read logs, clone Java classes, inspect keystore SHA-1, and run shell commands.",
                new Action[]{
                        new Action("Developer tools", DeveloperToolsActivity.class),
                        new Action("Terminal", pro.sketchware.activities.terminal.TerminalActivity.class)
                });

        // ── Project settings ──────────────────────────────────────────────────
        addSection(content, "Project settings",
                "Edit Gradle, permissions, ProGuard, and advanced UI settings.",
                new Action[]{
                        new Action("Advanced settings", AdvancedProjectSettingsActivity.class)
                });

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void addSection(LinearLayout parent, String title, String subtitle, Action[] actions) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardLp);
        card.setCardElevation(dp(1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(10));

        // Title
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15f);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(titleView);

        // Subtitle
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(12f);
        subtitleView.setAlpha(0.65f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(2), 0, dp(10));
        subtitleView.setLayoutParams(subLp);
        box.addView(subtitleView);

        // Divider
        MaterialDivider divider = new MaterialDivider(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divLp.setMargins(0, 0, 0, dp(8));
        divider.setLayoutParams(divLp);
        box.addView(divider);

        // Action buttons
        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            MaterialButton button = new MaterialButton(
                    this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(action.label);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < actions.length - 1) btnLp.setMargins(0, 0, 0, dp(4));
            button.setLayoutParams(btnLp);
            if (action.activity != null) {
                button.setOnClickListener(v ->
                        startActivity(new Intent(this, action.activity)
                                .putExtra(EXTRA_SC_ID, scId)));
            } else {
                final int resultCode = action.resultCode;
                button.setOnClickListener(v -> {
                    setResult(resultCode);
                    finish();
                });
            }
            box.addView(button);
        }

        card.addView(box);
        parent.addView(card);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Action {
        final String label;
        final Class<?> activity;
        final int resultCode;

        Action(String label, Class<?> activity) {
            this.label = label;
            this.activity = activity;
            this.resultCode = 0;
        }

        Action(String label, int resultCode) {
            this.label = label;
            this.activity = null;
            this.resultCode = resultCode;
        }
    }
}
