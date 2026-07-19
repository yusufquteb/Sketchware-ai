package pro.sketchware.activities.projecttools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.LinkedHashSet;
import java.util.Set;

import mod.hey.studios.project.proguard.ProguardHandler;
import pro.sketchware.R;
import pro.sketchware.settings.GradleSettingsManager;
import pro.sketchware.settings.OneSignalSettingsManager;
import pro.sketchware.settings.PermissionSettingsManager;
import pro.sketchware.settings.ProguardSettingsManager;
import pro.sketchware.settings.ProjectSettingsStore;
import pro.sketchware.settings.StringFogSettingsManager;
import pro.sketchware.settings.ThemeUiSettings;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class AdvancedProjectSettingsActivity extends BaseAppCompatActivity {

    private String scId;
    private TextInputEditText gradleInput, permissionsInput, proguardInput, oneSignalInput, themeInput;
    private MaterialSwitch proguardEnabled, stringFogEnabled, compactMode;
    private GradleSettingsManager gradleSettings;
    private PermissionSettingsManager permissionSettings;
    private ProguardSettingsManager proguardSettings;
    private StringFogSettingsManager stringFogSettings;
    private OneSignalSettingsManager oneSignalSettings;
    private ThemeUiSettings themeSettings;

    // Tab panels
    private View buildPanel;
    private View securityPanel;
    private View uiPanel;

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

        // Root frame for toolbar + tabs + content + FAB overlay
        android.widget.FrameLayout rootFrame = new android.widget.FrameLayout(this);
        rootFrame.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Advanced Settings");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        TabLayout tabs = new TabLayout(this);
        tabs.addTab(tabs.newTab().setText("Build"));
        tabs.addTab(tabs.newTab().setText("Security"));
        tabs.addTab(tabs.newTab().setText("UI"));
        root.addView(tabs);

        // Build panel
        buildPanel = createBuildPanel();
        root.addView(buildPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Security panel
        securityPanel = createSecurityPanel();
        root.addView(securityPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // UI panel
        uiPanel = createUiPanel();
        root.addView(uiPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
        });
        showTab(0);

        rootFrame.addView(root, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Extended FAB pinned bottom-right
        ExtendedFloatingActionButton saveFab = new ExtendedFloatingActionButton(this);
        saveFab.setText("Save");
        saveFab.setIconResource(R.drawable.ic_mtrl_save);
        saveFab.setOnClickListener(v -> saveSettings());
        android.widget.FrameLayout.LayoutParams fabLp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fabLp.gravity = Gravity.BOTTOM | Gravity.END;
        fabLp.setMargins(0, 0, dp(16), dp(24));
        saveFab.setLayoutParams(fabLp);
        rootFrame.addView(saveFab);

        setContentView(rootFrame);
        loadSettings();
    }

    private View createBuildPanel() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, dp(80)); // leave room for FAB
        scroll.addView(content);

        // Permissions card
        LinearLayout permBox = sectionCard(content, "Permissions",
                "Android manifest permissions, one per line.");
        permissionsInput = textArea(permBox, "Permissions, one per line", 5);

        // Gradle notes card
        LinearLayout gradleBox = sectionCard(content, "Gradle Notes",
                "Project-level Gradle notes and global snippet.");
        gradleInput = textArea(gradleBox, "Project Gradle notes / global snippet", 5);

        return scroll;
    }

    private View createSecurityPanel() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, dp(80));
        scroll.addView(content);

        // ProGuard card
        LinearLayout pgBox = sectionCard(content, "ProGuard / R8",
                "Code shrinking and obfuscation.");

        proguardEnabled = materialSwitch(pgBox, "Enable ProGuard / R8");
        proguardInput = textArea(pgBox, "ProGuard rules", 6);
        proguardInput.setTypeface(Typeface.MONOSPACE);
        // Link enabled state
        proguardEnabled.setOnCheckedChangeListener((btn, isChecked) ->
                proguardInput.setEnabled(isChecked));

        // StringFog card
        LinearLayout sfBox = sectionCard(content, "String Encryption",
                "StringFog encrypts string literals at build time.");
        stringFogEnabled = materialSwitch(sfBox, "Enable StringFog string encryption");

        return scroll;
    }

    private View createUiPanel() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, dp(80));
        scroll.addView(content);

        // UI settings card
        LinearLayout uiBox = sectionCard(content, "Theme & Layout",
                "Application theme and display density preferences.");
        themeInput = field(uiBox, "Theme name", InputType.TYPE_CLASS_TEXT);
        compactMode = materialSwitch(uiBox, "Compact project layout");

        // OneSignal card
        LinearLayout osBox = sectionCard(content, "Integrations",
                "Third-party SDK configuration stored per project.");
        oneSignalInput = field(osBox, "OneSignal App ID", InputType.TYPE_CLASS_TEXT);

        return scroll;
    }

    private void showTab(int position) {
        buildPanel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        securityPanel.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        uiPanel.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
    }

    // ── Section / widget helpers ──────────────────────────────────────────────

    private LinearLayout sectionCard(LinearLayout parent, String title, String subtitle) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardLp);
        card.setCardElevation(0f);
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceVariant));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(12));

        android.widget.TextView t = new android.widget.TextView(this);
        t.setText(title);
        t.setTextSize(15f);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        box.addView(t);

        if (subtitle != null) {
            android.widget.TextView s = new android.widget.TextView(this);
            s.setText(subtitle);
            s.setTextSize(12f);
            s.setAlpha(0.65f);
            s.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
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

    private MaterialSwitch materialSwitch(LinearLayout parent, String label) {
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setText(label);
        sw.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        sw.setLayoutParams(lp);
        parent.addView(sw);
        return sw;
    }

    // ── Business logic ────────────────────────────────────────────────────────

    private void loadSettings() {
        gradleInput.setText(gradleSettings.getGradleInjection());
        permissionsInput.setText(String.join("\n", permissionSettings.getPermissions()));
        boolean pgEnabled = proguardSettings.isEnabled();
        proguardEnabled.setChecked(pgEnabled);
        proguardInput.setText(proguardSettings.getRules());
        proguardInput.setEnabled(pgEnabled);
        stringFogEnabled.setChecked(stringFogSettings.isEnabled());
        oneSignalInput.setText(oneSignalSettings.getAppId());
        themeInput.setText(themeSettings.getThemeName());
        compactMode.setChecked(themeSettings.isCompactMode());
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
