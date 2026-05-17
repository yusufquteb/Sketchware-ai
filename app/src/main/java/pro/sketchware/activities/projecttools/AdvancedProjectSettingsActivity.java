package pro.sketchware.activities.projecttools;

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
    @Override public void onCreate(Bundle savedInstanceState) { enableEdgeToEdgeNoContrast(); super.onCreate(savedInstanceState); scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID); if (scId == null || scId.trim().isEmpty()) { SketchwareUtil.toastError("Project id missing"); finish(); return; } ProjectSettingsStore store = new ProjectSettingsStore(this, scId); gradleSettings = new GradleSettingsManager(store); permissionSettings = new PermissionSettingsManager(store); proguardSettings = new ProguardSettingsManager(store); stringFogSettings = new StringFogSettingsManager(store); oneSignalSettings = new OneSignalSettingsManager(store); themeSettings = new ThemeUiSettings(store); LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle("Advanced Settings"); toolbar.setSubtitle("Project " + scId); toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); toolbar.setNavigationOnClickListener(v -> finish()); root.addView(toolbar); ScrollView scrollView = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); int pad = dp(16); content.setPadding(pad, pad, pad, pad * 2); scrollView.addView(content); root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); gradleInput = addTextArea(content, "Project Gradle notes / global snippet", 6); permissionsInput = addTextArea(content, "Permissions, one per line", 6); proguardEnabled = addCheckBox(content, "Enable ProGuard/R8 setting mirror"); proguardInput = addTextArea(content, "ProGuard rules", 8); stringFogEnabled = addCheckBox(content, "Enable StringFog setting mirror"); oneSignalInput = addInput(content, "OneSignal App ID", InputType.TYPE_CLASS_TEXT); themeInput = addInput(content, "Theme name", InputType.TYPE_CLASS_TEXT); compactMode = addCheckBox(content, "Compact project UI setting"); MaterialButton save = new MaterialButton(this); save.setText("Save settings"); save.setOnClickListener(v -> saveSettings()); content.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); statusView = new TextView(this); statusView.setTextIsSelectable(true); statusView.setGravity(Gravity.START); statusView.setPadding(0, dp(12), 0, 0); content.addView(statusView); setContentView(root); loadSettings(); }
    private TextInputEditText addInput(LinearLayout parent, String hint, int inputType) { TextInputLayout layout = new TextInputLayout(this); layout.setHint(hint); TextInputEditText input = new TextInputEditText(this); input.setInputType(inputType); layout.addView(input); parent.addView(layout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); return input; }
    private TextInputEditText addTextArea(LinearLayout parent, String hint, int minLines) { TextInputLayout layout = new TextInputLayout(this); layout.setHint(hint); TextInputEditText input = new TextInputEditText(this); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); input.setMinLines(minLines); input.setGravity(Gravity.TOP | Gravity.START); input.setHorizontallyScrolling(true); layout.addView(input); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, dp(8)); parent.addView(layout, lp); return input; }
    private CheckBox addCheckBox(LinearLayout parent, String text) { CheckBox box = new CheckBox(this); box.setText(text); parent.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); return box; }
    private void loadSettings() { gradleInput.setText(gradleSettings.getGradleInjection()); permissionsInput.setText(String.join("\n", permissionSettings.getPermissions())); proguardEnabled.setChecked(proguardSettings.isEnabled()); proguardInput.setText(proguardSettings.getRules()); stringFogEnabled.setChecked(stringFogSettings.isEnabled()); oneSignalInput.setText(oneSignalSettings.getAppId()); themeInput.setText(themeSettings.getThemeName()); compactMode.setChecked(themeSettings.isCompactMode()); statusView.setText("Settings loaded. Use the dedicated ProGuard, permission, StringFog, or Gradle screens for their specialized editors; this page keeps a project-scoped mirror used by tools and diagnostics."); }
    private void saveSettings() { gradleSettings.setGradleInjection(text(gradleInput)); permissionSettings.setPermissions(nonBlankLines(text(permissionsInput))); proguardSettings.setEnabled(proguardEnabled.isChecked()); proguardSettings.setRules(text(proguardInput)); stringFogSettings.setEnabled(stringFogEnabled.isChecked()); oneSignalSettings.setAppId(text(oneSignalInput)); themeSettings.setThemeName(text(themeInput)); themeSettings.setCompactMode(compactMode.isChecked()); try { ProguardHandler handler = new ProguardHandler(scId); handler.setProguardEnabled(proguardEnabled.isChecked()); pro.sketchware.utility.io.SafeFileOps.writeUtf8Atomic(new java.io.File(ProjectToolPaths.getProjectDataDir(scId), "proguard-rules.pro"), text(proguardInput)); } catch (Throwable ignored) {} statusView.setText("Saved settings for project " + scId + "."); SketchwareUtil.toast("Settings saved"); }
    private Set<String> nonBlankLines(String value) { Set<String> out = new LinkedHashSet<>(); for (String line : value.split("\\R")) { String trimmed = line.trim(); if (!trimmed.isEmpty()) out.add(trimmed); } return out; }
    private String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
