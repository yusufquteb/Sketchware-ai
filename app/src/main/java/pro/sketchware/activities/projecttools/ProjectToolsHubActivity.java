package pro.sketchware.activities.projecttools;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class ProjectToolsHubActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

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
        root.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

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
        content.setPadding(pad, pad, pad, dp(32));

        addSection(content,
                R.drawable.ic_mtrl_screen,
                "Screens",
                "Add, clone, or rename activities.",
                new Action[]{
                        new Action("Manage Activities", ActivityManagerActivity.class)
                });

        addSection(content,
                R.drawable.ic_mtrl_folder,
                "Files",
                "Browse, edit, and search project files.",
                new Action[]{
                        new Action("File Manager", ProjectFileManagerActivity.class),
                        new Action("Search in Project", SearchInProjectActivity.class)
                });

        addSection(content,
                R.drawable.ic_mtrl_frame_source,
                "Source Control",
                "Clone, push, pull, and manage Git history.",
                new Action[]{
                        new Action("Git Workflow", GitWorkflowActivity.class)
                });

        addSection(content,
                R.drawable.ic_mtrl_package,
                "Build",
                "Compiler, Gradle, and library diagnostics.",
                new Action[]{
                        new Action("Compiler Diagnostics", BuildDiagnosticsActivity.class),
                        new Action("Gradle Injection", GradleInjectionActivity.class),
                        new Action("Library Diagnostics", ProjectLibraryDiagnosticsActivity.class)
                });

        addSection(content,
                R.drawable.ic_mtrl_apk_document,
                "Release Builds",
                "Build a signed APK or AAB for distribution.",
                new Action[]{
                        new Action("Build Signed APK  →", RESULT_BUILD_SIGNED_APK),
                        new Action("Build Signed AAB  →", RESULT_BUILD_SIGNED_AAB)
                });

        addSection(content,
                R.drawable.ic_mtrl_bookmark,
                "Project Lifecycle",
                "Backup, restore, export, clone, and clean.",
                new Action[]{
                        new Action("Backup / Restore / Cleanup", ProjectLifecycleActivity.class)
                });

        addSection(content,
                R.drawable.ic_mtrl_export,
                "Import & Conversion",
                "Analyze imports, split statements, validate layouts.",
                new Action[]{
                        new Action("Import Helpers", ImportConversionActivity.class)
                });

        addSection(content,
                R.drawable.ic_mtrl_terminal,
                "Developer Tools",
                "Logs, keystore info, Java class tools, and shell terminal.",
                new Action[]{
                        new Action("Developer Tools", DeveloperToolsActivity.class),
                        new Action("Terminal", pro.sketchware.activities.terminal.TerminalActivity.class)
                });

        addSection(content,
                R.drawable.ic_mtrl_settings,
                "Project Settings",
                "Gradle config, permissions, ProGuard, and UI settings.",
                new Action[]{
                        new Action("Advanced Settings", AdvancedProjectSettingsActivity.class)
                });

        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void addSection(LinearLayout parent, int iconRes, String title,
                            String subtitle, Action[] actions) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setRadius(dp(16));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardLp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(12));

        // Header row: icon + title/subtitle
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        // Icon container (tinted circle)
        LinearLayout iconWrap = new LinearLayout(this);
        iconWrap.setGravity(Gravity.CENTER);
        int iconSize = dp(40);
        LinearLayout.LayoutParams iconWrapLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconWrapLp.setMarginEnd(dp(14));
        iconWrap.setLayoutParams(iconWrapLp);
        iconWrap.setBackgroundTintList(ColorStateList.valueOf(
                ThemeUtils.getColor(this, R.attr.colorSecondaryContainer)));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        // Rounded background
        iconWrap.setBackground(makeRoundedBackground(ThemeUtils.getColor(this, R.attr.colorSecondaryContainer), dp(10)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSecondaryContainer));
        int imgSize = dp(22);
        icon.setLayoutParams(new LinearLayout.LayoutParams(imgSize, imgSize));
        iconWrap.addView(icon);
        header.addView(iconWrap);

        // Title + subtitle stack
        LinearLayout textStack = new LinearLayout(this);
        textStack.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15f);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        textStack.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(12f);
        subtitleView.setAlpha(0.65f);
        subtitleView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(1), 0, 0);
        subtitleView.setLayoutParams(subLp);
        textStack.addView(subtitleView);

        header.addView(textStack, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(header);

        if (actions.length > 0) {
            MaterialDivider divider = new MaterialDivider(this);
            divider.setDividerColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            divLp.setMargins(0, dp(12), 0, dp(8));
            divider.setLayoutParams(divLp);
            box.addView(divider);

            for (int i = 0; i < actions.length; i++) {
                Action action = actions[i];
                MaterialButton button = new MaterialButton(
                        this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setText(action.label);
                button.setTextSize(13f);
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
        }

        card.addView(box);
        parent.addView(card);
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
