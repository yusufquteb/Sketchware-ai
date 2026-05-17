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

import pro.sketchware.utility.SketchwareUtil;

public class ProjectToolsHubActivity extends BaseAppCompatActivity {
    public static final String EXTRA_SC_ID = "sc_id";
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
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addSection(content, "Files", "Browse, edit, search, and inspect project files.", new Action[]{new Action("File manager", ProjectFileManagerActivity.class), new Action("Search in project", SearchInProjectActivity.class)});
        addSection(content, "Source control", "Clone, configure, inspect, pull, and push Git-backed projects.", new Action[]{new Action("Git workflow", GitWorkflowActivity.class)});
        addSection(content, "Project lifecycle", "Backup, restore, export, clone, and clean the current project.", new Action[]{new Action("Backup / restore / cleanup", ProjectLifecycleActivity.class)});
        addSection(content, "Build", "Review compiler inputs, Kotlin files, ViewBinding outputs, cache keys, and R8 command previews.", new Action[]{new Action("Compiler diagnostics", BuildDiagnosticsActivity.class), new Action("Gradle injection", GradleInjectionActivity.class)});
        addSection(content, "Libraries", "Validate built-in and local libraries, classpath entries, conflicts, and metadata.", new Action[]{new Action("Library diagnostics", ProjectLibraryDiagnosticsActivity.class)});
        addSection(content, "Import and conversion", "Analyze Java imports, split Java statements, validate layouts, and save import snippets.", new Action[]{new Action("Import helpers", ImportConversionActivity.class)});
        addSection(content, "Tools", "Read logs, clone Java classes, and inspect keystore SHA-1 values.", new Action[]{new Action("Developer tools", DeveloperToolsActivity.class)});
        addSection(content, "Project settings", "Edit project-scoped Gradle, permission, ProGuard, StringFog, notification, and UI settings.", new Action[]{new Action("Advanced settings", AdvancedProjectSettingsActivity.class)});
        setContentView(root);
    }

    private void addSection(LinearLayout parent, String title, String subtitle, Action[] actions) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardLp);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(17f);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(titleView);
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(13f);
        subtitleView.setPadding(0, dp(3), 0, dp(8));
        box.addView(subtitleView);
        for (Action action : actions) {
            MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(action.label);
            button.setOnClickListener(v -> startActivity(new Intent(this, action.activity).putExtra(EXTRA_SC_ID, scId)));
            box.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.addView(box);
        parent.addView(card);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class Action { final String label; final Class<?> activity; Action(String label, Class<?> activity) { this.label = label; this.activity = activity; } }
}
