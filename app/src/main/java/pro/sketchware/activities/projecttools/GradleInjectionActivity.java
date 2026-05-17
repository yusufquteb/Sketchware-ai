package pro.sketchware.activities.projecttools;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Gradle Injection");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        TabLayout tabs = new TabLayout(this);
        tabs.addTab(tabs.newTab().setText("App"));
        tabs.addTab(tabs.newTab().setText("Project"));
        tabs.addTab(tabs.newTab().setText("Properties"));
        root.addView(tabs);

        appPanel = createEditorPanel(root,
                "Code appended to app/build.gradle at generation time.",
                HINT_APP);
        projectPanel = createEditorPanel(root,
                "Code appended to project build.gradle at generation time.",
                HINT_PROJECT);
        propertiesPanel = createEditorPanel(root,
                "Lines appended to gradle.properties at generation time.",
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

    private View createEditorPanel(LinearLayout root, String helperText, int hintId) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        panel.setPadding(pad, pad, pad, pad);

        android.widget.TextView help = new android.widget.TextView(this);
        help.setText(helperText);
        panel.addView(help);

        com.google.android.material.button.MaterialButton hintButton = new com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        hintButton.setId(hintId);
        hintButton.setText("Show examples");
        hintButton.setOnClickListener(v -> showHint(v.getId()));
        panel.addView(hintButton);

        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Optional injection code");
        TextInputEditText input = new TextInputEditText(this);
        input.setMinLines(10);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setHorizontallyScrolling(true);
        inputLayout.addView(input);
        panel.addView(inputLayout, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.setTag(input);
        root.addView(panel, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f));
        return panel;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Save").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
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
