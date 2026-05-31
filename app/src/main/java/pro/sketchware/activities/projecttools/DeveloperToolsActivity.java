package pro.sketchware.activities.projecttools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.tools.ClassCloneTool;
import pro.sketchware.tools.KeystoreTool;
import pro.sketchware.tools.LogReader;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.R;

public class DeveloperToolsActivity extends BaseAppCompatActivity {

    private String scId;
    private TextInputEditText keystorePathInput, keystorePasswordInput, keystoreAliasInput;
    private TextInputEditText sourceClassInput, targetClassInput, oldClassNameInput, newClassNameInput;
    private TextView outputView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Developer Tools");
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

        // ── Keystore Inspector ────────────────────────────────────────────────
        LinearLayout ksBox = section(content, "Keystore Inspector",
                "Compute the SHA-1 fingerprint of a signing keystore.");
        keystorePathInput = field(ksBox, "Keystore path", InputType.TYPE_CLASS_TEXT);
        keystorePasswordInput = field(ksBox, "Keystore password",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keystoreAliasInput = field(ksBox, "Keystore alias", InputType.TYPE_CLASS_TEXT);
        addActionButton(ksBox, "Compute keystore SHA-1", "Computing SHA-1...", this::computeSha1);

        // ── Class Cloning ─────────────────────────────────────────────────────
        LinearLayout cloneBox = section(content, "Java Class Cloning",
                "Duplicate a Java class file and rename the class declaration inside.");
        sourceClassInput = field(cloneBox, "Source Java class path", InputType.TYPE_CLASS_TEXT);
        targetClassInput = field(cloneBox, "Target Java class path", InputType.TYPE_CLASS_TEXT);
        oldClassNameInput = field(cloneBox, "Old class name", InputType.TYPE_CLASS_TEXT);
        newClassNameInput = field(cloneBox, "New class name", InputType.TYPE_CLASS_TEXT);
        addActionButton(cloneBox, "Clone Java class", "Cloning class...", this::cloneClass);

        // ── System Logs ───────────────────────────────────────────────────────
        LinearLayout sysBox = section(content, "System Logs",
                "Read the latest logcat output for debugging.");
        addActionButton(sysBox, "Read latest system log", "Reading logs...", this::readLogs);

        // ── Output ────────────────────────────────────────────────────────────
        addOutputSection(content);

        setContentView(root);
    }

    private LinearLayout section(LinearLayout parent, String title, String subtitle) {
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

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15f);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        box.addView(t);

        if (subtitle != null) {
            TextView s = new TextView(this);
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

    private void addActionButton(LinearLayout parent, String label, String runningLabel, Work work) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnLp.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(btnLp);

        CircularProgressIndicator progress = new CircularProgressIndicator(this);
        progress.setIndeterminate(true);
        progress.setVisibility(android.view.View.GONE);
        int progressSize = dp(24);
        LinearLayout.LayoutParams progressLp =
                new LinearLayout.LayoutParams(progressSize, progressSize);
        progress.setLayoutParams(progressLp);

        button.setOnClickListener(v -> {
            button.setEnabled(false);
            progress.setVisibility(android.view.View.VISIBLE);
            append(runningLabel);
            executor.execute(() -> {
                String result;
                try {
                    result = work.run();
                } catch (Exception e) {
                    result = "ERROR: " + e.getMessage();
                }
                String finalResult = result;
                runOnUiThread(() -> {
                    progress.setVisibility(android.view.View.GONE);
                    button.setEnabled(true);
                    append(finalResult);
                });
            });
        });

        row.addView(button);
        row.addView(progress);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(4));
        row.setLayoutParams(rowLp);
        parent.addView(row);
    }

    private void addOutputSection(LinearLayout parent) {
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

        TextView title = new TextView(this);
        title.setText("Output");
        title.setTextSize(15f);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        box.addView(title);

        MaterialDivider div = new MaterialDivider(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        divLp.setMargins(0, dp(8), 0, dp(12));
        div.setLayoutParams(divLp);
        box.addView(div);

        // Scrollable output: HorizontalScrollView > ScrollView > TextView (monospace)
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        ScrollView vScroll = new ScrollView(this);
        vScroll.setFillViewport(true);

        outputView = new TextView(this);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextIsSelectable(true);
        outputView.setGravity(Gravity.START | Gravity.TOP);
        outputView.setTextSize(12f);
        outputView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        outputView.setText("Tools are ready.");
        int outPad = dp(8);
        outputView.setPadding(outPad, outPad, outPad, outPad);

        vScroll.addView(outputView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hScroll.addView(vScroll, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams hScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200));
        box.addView(hScroll, hScrollLp);

        // Clear output button
        MaterialButton clearBtn = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        clearBtn.setText("Clear output");
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clearLp.setMargins(0, dp(8), 0, 0);
        clearBtn.setLayoutParams(clearLp);
        clearBtn.setOnClickListener(v -> {
            if (outputView != null) outputView.setText("Output cleared.");
        });
        box.addView(clearBtn);

        card.addView(box);
        parent.addView(card);
    }

    private String computeSha1() throws Exception {
        return "SHA-1: " + KeystoreTool.sha1(
                new File(text(keystorePathInput)), text(keystorePasswordInput), text(keystoreAliasInput));
    }

    private String cloneClass() throws Exception {
        ClassCloneTool.cloneJavaClass(new File(text(sourceClassInput)),
                new File(text(targetClassInput)), text(oldClassNameInput), text(newClassNameInput));
        return "Class copied to:\n" + text(targetClassInput);
    }

    private String readLogs() throws Exception {
        List<String> lines = LogReader.read(300);
        StringBuilder out = new StringBuilder("System log lines: ").append(lines.size()).append('\n');
        for (String line : lines) out.append(line).append('\n');
        return out.toString();
    }

    private String text(TextInputEditText i) {
        return i.getText() == null ? "" : i.getText().toString().trim();
    }

    private void append(String text) {
        if (outputView != null) outputView.append("\n\n" + text);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private interface Work {
        String run() throws Exception;
    }
}
