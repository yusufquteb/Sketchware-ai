package pro.sketchware.activities.projecttools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.widget.NestedScrollView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.util.GradleInjectionManager;
import pro.sketchware.utility.SketchwareUtil;

public class GradleInjectionActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";
    private static final int HINT_APP = 10001;
    private static final int HINT_PROJECT = 10002;
    private static final int HINT_PROPERTIES = 10003;

    private String scId;
    private View appPanel;
    private View projectPanel;
    private View propertiesPanel;
    private TextInputEditText appInput;
    private TextInputEditText projectInput;
    private TextInputEditText propertiesInput;

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

        String projectName = getProjectName();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Gradle Injection");
        toolbar.setSubtitle(projectName);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        setSupportActionBar(toolbar);
        root.addView(toolbar);

        TabLayout tabs = new TabLayout(this);
        tabs.addTab(tabs.newTab().setText("App build.gradle"));
        tabs.addTab(tabs.newTab().setText("Project build.gradle"));
        tabs.addTab(tabs.newTab().setText("gradle.properties"));
        root.addView(tabs);

        appPanel = createEditorPanel(root,
                "Injected into app/build.gradle at build time",
                HINT_APP);
        projectPanel = createEditorPanel(root,
                "Injected into project build.gradle at build time",
                HINT_PROJECT);
        propertiesPanel = createEditorPanel(root,
                "Injected into gradle.properties at build time",
                HINT_PROPERTIES);

        appInput = (TextInputEditText) appPanel.getTag();
        projectInput = (TextInputEditText) projectPanel.getTag();
        propertiesInput = (TextInputEditText) propertiesPanel.getTag();

        appInput.setText(GradleInjectionManager.readAppGradleInject(scId));
        projectInput.setText(GradleInjectionManager.readProjectGradleInject(scId));
        propertiesInput.setText(GradleInjectionManager.readPropertiesInject(scId));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
        });
        showTab(0);

        setContentView(root);
    }

    private View createEditorPanel(LinearLayout root, String chipText, int hintId) {
        NestedScrollView scrollView = new NestedScrollView(this);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        panel.setPadding(pad, pad, pad, pad);

        // Info chip (non-clickable)
        Chip infoChip = new Chip(this);
        infoChip.setText(chipText);
        infoChip.setClickable(false);
        infoChip.setCheckable(false);
        infoChip.setFocusable(false);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.setMargins(0, 0, 0, dp(12));
        infoChip.setLayoutParams(chipLp);
        panel.addView(infoChip);

        // Monospace TextInputLayout
        TextInputLayout inputLayout = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        inputLayout.setHint("Optional injection code");
        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setTypeface(Typeface.MONOSPACE);
        input.setMinLines(15);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setHorizontallyScrolling(false);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inputLayout.addView(input);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(0, 0, 0, dp(12));
        inputLayout.setLayoutParams(inputLp);
        panel.addView(inputLayout);

        // "Show examples" outlined button
        MaterialButton hintButton = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        hintButton.setId(hintId);
        hintButton.setText("Show examples");
        hintButton.setOnClickListener(v -> showHint(v.getId()));
        panel.addView(hintButton);

        panel.setTag(input);
        scrollView.addView(panel);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return scrollView;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showTab(int position) {
        appPanel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        projectPanel.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        propertiesPanel.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
    }

    private void saveAll() {
        GradleInjectionManager.writeAppGradleInject(scId, getText(appInput));
        GradleInjectionManager.writeProjectGradleInject(scId, getText(projectInput));
        GradleInjectionManager.writePropertiesInject(scId, getText(propertiesInput));
        SketchwareUtil.toast("Gradle injections saved");
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    private void showHint(int which) {
        String message;
        if (which == HINT_APP) {
            message = "Examples for app/build.gradle:\n\n"
                    + "dependencies {\n    implementation 'com.squareup.okhttp3:okhttp:4.12.0'\n}\n\n"
                    + "android {\n    buildFeatures {\n        buildConfig true\n    }\n}";
        } else if (which == HINT_PROJECT) {
            message = "Examples for top-level build.gradle:\n\n"
                    + "buildscript {\n    dependencies {\n        classpath 'com.google.gms:google-services:4.4.2'\n    }\n}";
        } else {
            message = "Examples for gradle.properties:\n\n"
                    + "org.gradle.parallel=true\n"
                    + "org.gradle.caching=true\n"
                    + "org.gradle.jvmargs=-Xmx4g";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Injection examples")
                .setMessage(message)
                .setPositiveButton("OK", null)
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem saveItem = menu.add(Menu.NONE, 1, Menu.NONE, "Save");
        saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        saveItem.setIcon(R.drawable.ic_mtrl_save);
        menu.add(Menu.NONE, 2, Menu.NONE, "Clear all");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            saveAll();
            return true;
        }
        if (item.getItemId() == 2) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Clear all injections?")
                    .setMessage("This removes all custom Gradle injection snippets for this project.")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        appInput.setText("");
                        projectInput.setText("");
                        propertiesInput.setText("");
                        saveAll();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
