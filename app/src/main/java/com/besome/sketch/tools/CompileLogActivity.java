package com.besome.sketch.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import mod.hey.studios.util.CompileLogHelper;
import mod.hey.studios.util.Helper;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.util.AddMarginOnApplyWindowInsetsListener;
import pro.sketchware.databinding.CompileLogBinding;
import pro.sketchware.utility.SketchwareUtil;

public class CompileLogActivity extends BaseAppCompatActivity {

    private static final String PREFERENCE_WRAPPED_TEXT = "wrapped_text";
    private static final String PREFERENCE_USE_MONOSPACED_FONT = "use_monospaced_font";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private CompileErrorSaver compileErrorSaver;
    private SharedPreferences logViewerPreferences;

    private CompileLogBinding binding;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = CompileLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.optionsLayout,
                new AddMarginOnApplyWindowInsetsListener(WindowInsetsCompat.Type.navigationBars(), WindowInsetsCompat.CONSUMED));

        logViewerPreferences = getPreferences(Context.MODE_PRIVATE);

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        if (getIntent().getBooleanExtra("showingLastError", false)) {
            binding.topAppBar.setTitle("Last compile log");
        } else {
            binding.topAppBar.setTitle("Compile log");
        }

        String sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null) {
            finish();
            return;
        }

        compileErrorSaver = new CompileErrorSaver(sc_id);

        // AI Fix button → unified AiAssistantBottomSheet
        binding.topAppBar.getMenu().clear();
        binding.topAppBar.inflateMenu(pro.sketchware.R.menu.compile_log_menu);
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == pro.sketchware.R.id.action_ai_fix) {
                openErrorLogAi(sc_id);
                return true;
            }
            return false;
        });

        if (compileErrorSaver.logFileExists()) {
            binding.clearButton.setOnClickListener(v -> {
                if (compileErrorSaver.logFileExists()) {
                    compileErrorSaver.deleteSavedLogs();
                    getIntent().removeExtra("error");
                    SketchwareUtil.toast("Compile logs have been cleared.");
                } else {
                    SketchwareUtil.toast("No compile logs found.");
                }

                setErrorText();
            });
        }

        final String wrapTextLabel = "Wrap text";
        final String monospacedFontLabel = "Monospaced font";
        final String fontSizeLabel = "Font size";

        PopupMenu options = new PopupMenu(this, binding.formatButton);
        options.getMenu().add(wrapTextLabel).setCheckable(true).setChecked(getWrappedTextPreference());
        options.getMenu().add(monospacedFontLabel).setCheckable(true).setChecked(getMonospacedFontPreference());
        options.getMenu().add(fontSizeLabel);

        options.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getTitle().toString()) {
                case wrapTextLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleWrapText(menuItem.isChecked());
                }
                case monospacedFontLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleMonospacedText(menuItem.isChecked());
                }
                case fontSizeLabel -> changeFontSizeDialog();
                default -> {
                    return false;
                }
            }

            return true;
        });

        binding.formatButton.setOnClickListener(v -> options.show());

        applyLogViewerPreferences();

        setErrorText();
    }

    private void setErrorText() {
        String error = getIntent().getStringExtra("error");
        if (error == null) error = compileErrorSaver.getLogsFromFile();
        if (error == null) {
            binding.noContentLayout.setVisibility(View.VISIBLE);
            binding.optionsLayout.setVisibility(View.GONE);
            return;
        }

        binding.optionsLayout.setVisibility(View.VISIBLE);
        binding.noContentLayout.setVisibility(View.GONE);

        binding.tvCompileLog.setText(CompileLogHelper.getColoredLogs(this, error));
        binding.tvCompileLog.setTextIsSelectable(true);
    }


    // ── Error Log AI Assistant ─────────────────────────────────────────────────

    private void openErrorLogAi(String scId) {
        String rawErrors = getIntent().getStringExtra("error");
        if (rawErrors == null) rawErrors = compileErrorSaver.getLogsFromFile();
        final String rawLog = rawErrors != null ? rawErrors : "";
        final String errors = deduplicateErrors(rawLog);

        java.util.List<pro.sketchware.ai.shared.AiPageConfig.Tool> tools = new java.util.ArrayList<>();

        // ── Analysis tools (chat only) ─────────────────────────────────────────
        tools.add(new pro.sketchware.ai.shared.AiPageConfig.Tool("Analyse", pro.sketchware.R.drawable.ic_mtrl_bug_report));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Analyse All Errors",
            pro.sketchware.R.drawable.ic_mtrl_bug_report,
            "Analyse these compile errors and explain each one:\n" + errors));

        // ── Fix tools (AI — stays inside the bottomsheet) ─────────────────────
        String firstError = errors.isEmpty() ? rawLog
            : errors.split("\\n")[0];
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Fix First Error",
            pro.sketchware.R.drawable.ic_mtrl_check,
            "Fix ONLY the first compile error below. Show the exact code change (before → after) "
            + "and tell me where to apply it:\n\n" + firstError));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Fix All Errors",
            pro.sketchware.R.drawable.ic_mtrl_warning,
            "Fix ALL compile errors below one by one. For each error show the exact code change "
            + "(before → after) and where to apply it:\n\n"
            + (errors.isEmpty() ? rawLog : errors).substring(0, Math.min(3000, (errors.isEmpty() ? rawLog : errors).length()))));

        // ── Code Fixes (chat) ──────────────────────────────────────────────────
        tools.add(new pro.sketchware.ai.shared.AiPageConfig.Tool("Code Fixes", pro.sketchware.R.drawable.ic_mtrl_code));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Fix Missing Import",
            pro.sketchware.R.drawable.ic_mtrl_download,
            "Add missing import for: [class or symbol name]"));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Fix Type Mismatch",
            pro.sketchware.R.drawable.ic_mtrl_checklist,
            "Fix the type mismatch. Show corrected code."));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Fix Null Pointer",
            pro.sketchware.R.drawable.ic_mtrl_warning,
            "Add null checks to fix NullPointerException."));

        // ── Resources (chat) ───────────────────────────────────────────────────
        tools.add(new pro.sketchware.ai.shared.AiPageConfig.Tool("Resources", pro.sketchware.R.drawable.ic_mtrl_box));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Add Missing Library",
            pro.sketchware.R.drawable.ic_mtrl_download,
            "Suggest the Maven library needed to fix:\n[paste error line here]\n"
            + "Return: groupId:artifactId:version (latest stable, minSdk 21 compatible)."));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Add String Resource",
            pro.sketchware.R.drawable.ic_mtrl_article,
            "Generate string resource for: [describe what string is needed]\n"
            + "Return: XML entry to add to strings.xml"));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Add Drawable",
            pro.sketchware.R.drawable.ic_mtrl_image,
            "Create a drawable XML for: [describe the drawable needed]\n"
            + "Return: complete res/drawable/name.xml content"));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Fix Style / Theme",
            pro.sketchware.R.drawable.ic_mtrl_palette,
            "Add or fix the style/theme attribute for error:\n[paste error here]\n"
            + "Return: entry to add to styles.xml or themes.xml"));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Fix Manifest",
            pro.sketchware.R.drawable.ic_mtrl_settings,
            "Fix the AndroidManifest error:\n[paste error here]"));

        pro.sketchware.ai.shared.AiPageConfig config = new pro.sketchware.ai.shared.AiPageConfig.Builder()
            .pageTitle("Error Log")
            .scopeLabel(errors.isEmpty() ? "No errors" : "Build errors")
            .inputHint("Describe the error or ask how to fix it…")
            .systemPrompt(
                "You are an Android build error expert inside Sketchware Pro.\n"
                + "Project sc_id: " + scId + "\n\n"
                + "ERRORS (deduplicated):\n"
                + (errors.isEmpty() ? "(none)" : errors.substring(0, Math.min(2000, errors.length()))) + "\n\n"
                + "MANDATORY PIPELINE:\n"
                + "1. Read the FIRST unique error — fix one at a time\n"
                + "2. Root cause: cannot find symbol→import | type mismatch→cast | "
                +    "already defined→duplicate | R.id missing→add resource\n"
                + "3. Show EXACT code change: before → after\n"
                + "4. For missing resources: provide the FULL XML to add\n"
                + "5. For missing libraries: provide groupId:artifactId:version\n"
                + "6. After EACH fix: say '✅ Now rebuild and test before the next fix.'\n"
                + "7. When user rebuilds successfully: ask 'Great! Any more errors?'\n\n"
                + "PULSE RULE: After any fix, ALWAYS end with:\n"
                + "'🔄 Please rebuild now. If it succeeds, let me know. "
                + "If not, paste the new errors.'\n\n"
                + "FORMAT: concise, exact code, rebuild reminder. Reply in user's language.")
            .tools(tools)
            .build();

        pro.sketchware.ai.shared.AiAssistantBottomSheet.newInstance(config)
            .show(getSupportFragmentManager(), "error_log_ai");
    }

    /**
     * Remove duplicate error lines before sending to AI.
     * Deduplication reduces token usage on long error logs.
     */
    private String deduplicateErrors(String rawErrors) {
        if (rawErrors == null || rawErrors.isEmpty()) return "";
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        String[] lines = rawErrors.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) unique.add(trimmed);
        }
        return String.join("\n", unique);
    }

    private void applyLogViewerPreferences() {
        toggleMonospacedText(getMonospacedFontPreference());
        binding.tvCompileLog.setTextSize(getFontSizePreference());
    }

    private boolean getWrappedTextPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_WRAPPED_TEXT, false);
    }

    private boolean getMonospacedFontPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_USE_MONOSPACED_FONT, true);
    }

    private int getFontSizePreference() {
        return logViewerPreferences.getInt(PREFERENCE_FONT_SIZE, 11);
    }

    private void toggleWrapText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_WRAPPED_TEXT, isChecked).apply();

        if (isChecked) {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errVScroll.addView(binding.tvCompileLog);
        } else {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errHScroll.removeAllViews();
            binding.errHScroll.addView(binding.tvCompileLog);
            binding.errVScroll.addView(binding.errHScroll);
        }
    }

    private void toggleMonospacedText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_USE_MONOSPACED_FONT, isChecked).apply();

        if (isChecked) {
            binding.tvCompileLog.setTypeface(Typeface.MONOSPACE);
        } else {
            binding.tvCompileLog.setTypeface(Typeface.DEFAULT);
        }
    }

    private void changeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(10); //Must not be less than setValue(), which is currently 11 in compile_log.xml
        picker.setMaxValue(70);
        picker.setWrapSelectorWheel(false);
        picker.setValue(getFontSizePreference());

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select font size")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    logViewerPreferences.edit().putInt(PREFERENCE_FONT_SIZE, picker.getValue()).apply();

                    binding.tvCompileLog.setTextSize((float) picker.getValue());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
