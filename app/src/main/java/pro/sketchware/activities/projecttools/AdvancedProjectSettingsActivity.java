package pro.sketchware.activities.projecttools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
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

import java.util.LinkedHashSet;
import java.util.Set;

import mod.hey.studios.project.proguard.ProguardHandler;
import pro.sketchware.settings.GradleSettingsManager;
import pro.sketchware.settings.OneSignalSettingsManager;
import pro.sketchware.settings.PermissionSettingsManager;
import pro.sketchware.settings.ProguardSettingsManager;
import pro.sketchware.settings.ProjectSettingsStore;
import pro.sketchware.settings.StringFogSettingsManager;
import pro.sketchware.settings.ThemeUiSettings;
import pro.sketchware.utility.SketchwareUtil;

public class AdvancedProjectSettingsActivity extends BaseAppCompatActivity {

    private String scId;
    private TextInputEditText gradleInput, permissionsInput, proguardInput, oneSignalInput, themeInput;
    private CheckBox proguardEnabled, stringFogEnabled, compactMode;
    private TextView statusView;
    private GradleSettingsManager gradleSettings;
    private PermissionSettingsManager permissionSettings;
    private ProguardSettingsManager proguardSettings;
    private StringFogSettingsManager stringFogSettings;
    private OneSignalSettingsManager oneSignalSettings;
    private ThemeUiSettings themeSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID);
        if (scId == null || scId.trim().isEmpty()) {
            SketchwareUtil.toastError("Project id missing");
            finish();
            return;
        }

        ProjectSettingsStore store = new ProjectSettingsStore(this, scId);
        gradleSettings = new GradleSettingsManager(store);
        permissionSettings = new PermissionSettingsManager(store);
        proguardSettings = new ProguardSettingsManager(store);
        stringFogSettings = new StringFogSettingsManager(store);
        oneSignalSettings = new OneSignalSettingsManager(store);
        themeSettings = new ThemeUiSettings(store);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Advanced Settings");
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
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ── Build Configuration ───────────────────────────────────────────────
        LinearLayout buildBox = section(content, "Build Configuration",
                "Project-level Gradle notes and global snippet injected into the build.");
        gradleInput = textArea(buildBox, "Project Gradle notes / global snippet", 5);

        // ── Permissions ───────────────────────────────────────────────────────
        LinearLayout permBox = section(content, "Permissions",
                "Android manifest permissions to include, one per line.");
        permissionsInput = textArea(permBox, "Permissions, one per line", 5);

        // ── Security ──────────────────────────────────────────────────────────
        LinearLayout secBox = section(content, "Security",
                "ProGuard/R8 shrinking and StringFog string encryption settings.");
        proguardEnabled = checkBox(secBox, "Enable ProGuard / R8 setting mirror");
        proguardInput = textArea(secBox, "ProGuard rules", 6);
        stringFogEnabled = checkBox(secBox, "Enable StringFog setting mirror");

        // ── Integrations ──────────────────────────────────────────────────────
        LinearLayout integBox = section(content, "Integrations",
                "Third-party SDK configuration stored per project.");
        oneSignalInput = field(integBox, "OneSignal App ID", InputType.TYPE_CLASS_TEXT);

        // ── UI ────────────────────────────────────────────────────────────────
        LinearLayout uiBox = section(content, "UI",
                "Application theme and display density preferences.");
        themeInput = field(uiBox, "Theme name", InputType.TYPE_CLASS_TEXT);
        compactMode = checkBox(uiBox, "Compact project UI setting");

        // ── Save button + status ──────────────────────────────────────────────
        MaterialButton save = new MaterialButton(this);
        save.setText("Save settings");
        save.setOnClickListener(v -> saveSettings());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.setMargins(0, 0, 0, dp(8));
        save.setLayoutParams(saveLp);
        content.addView(save);

        statusView = new TextView(this);
        statusView.setTextIsSelectable(true);
        statusView.setGravity(Gravity.START);
        statusView.setTextSize(12f);
        statusView.setAlpha(0.75f);
        content.addView(statusView);

        setContentView(root);
        loadSettings();
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

    private TextInputEditText textArea(LinearLayout parent, String hint, int minLines) {
        TextInputLayout til = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint(hint);
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setMinLines(minLines);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setHorizontallyScrolling(false);
        til.addView(et);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        til.setLayoutParams(lp);
        parent.addView(til);
        return et;
    }

    private CheckBox checkBox(LinearLayout parent, String label) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(4));
        box.setLayoutParams(lp);
        parent.addView(box);
        return box;
    }

    // ── Business logic ────────────────────────────────────────────────────────

    private void loadSettings() {
        gradleInput.setText(gradleSettings.getGradleInjection());
        permissionsInput.setText(String.join("\n", permissionSettings.getPermissions()));
        proguardEnabled.setChecked(proguardSettings.isEnabled());
        proguardInput.setText(proguardSettings.getRules());
        stringFogEnabled.setChecked(stringFogSettings.isEnabled());
        oneSignalInput.setText(oneSignalSettings.getAppId());
        themeInput.setText(themeSettings.getThemeName());
        compactMode.setChecked(themeSettings.isCompactMode());
        statusView.setText(
                "Settings loaded. Use the dedicated ProGuard, permission, StringFog, or Gradle "
                + "screens for their specialized editors; this page keeps a project-scoped mirror "
                + "used by tools and diagnostics.");
    }

    private void saveSettings() {
        gradleSettings.setGradleInjection(text(gradleInput));
        permissionSettings.setPermissions(nonBlankLines(text(permissionsInput)));
        proguardSettings.setEnabled(proguardEnabled.isChecked());
        proguardSettings.setRules(text(proguardInput));
        stringFogSettings.setEnabled(stringFogEnabled.isChecked());
        oneSignalSettings.setAppId(text(oneSignalInput));
        themeSettings.setThemeName(text(themeInput));
        themeSettings.setCompactMode(compactMode.isChecked());
        try {
            ProguardHandler handler = new ProguardHandler(scId);
            handler.setProguardEnabled(proguardEnabled.isChecked());
            pro.sketchware.utility.io.SafeFileOps.writeUtf8Atomic(
                    new java.io.File(ProjectToolPaths.getProjectDataDir(scId), "proguard-rules.pro"),
                    text(proguardInput));
        } catch (Throwable ignored) {}
        statusView.setText("Saved settings for project " + scId + ".");
        SketchwareUtil.toast("Settings saved");
    }

    private Set<String> nonBlankLines(String value) {
        Set<String> out = new LinkedHashSet<>();
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
