package pro.sketchware.activities.projecttools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
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

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.tools.ClassCloneTool;
import pro.sketchware.tools.KeystoreTool;
import pro.sketchware.tools.LogReader;
import pro.sketchware.utility.SketchwareUtil;

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
        btn(ksBox, "Compute keystore SHA-1", () -> run("Computing SHA-1…", this::computeSha1));

        // ── Class Cloning ─────────────────────────────────────────────────────
        LinearLayout cloneBox = section(content, "Java Class Cloning",
                "Duplicate a Java class file and rename the class declaration inside.");
        sourceClassInput = field(cloneBox, "Source Java class path", InputType.TYPE_CLASS_TEXT);
        targetClassInput = field(cloneBox, "Target Java class path", InputType.TYPE_CLASS_TEXT);
        oldClassNameInput = field(cloneBox, "Old class name", InputType.TYPE_CLASS_TEXT);
        newClassNameInput = field(cloneBox, "New class name", InputType.TYPE_CLASS_TEXT);
        btn(cloneBox, "Clone Java class", () -> run("Cloning class…", this::cloneClass));

        // ── System Logs ───────────────────────────────────────────────────────
        LinearLayout sysBox = section(content, "System Logs",
                "Read the latest logcat output for debugging.");
        btn(sysBox, "Read latest system log", () -> run("Reading logs…", this::readLogs));

        // ── Output ────────────────────────────────────────────────────────────
        LinearLayout outBox = section(content, "Output", null);
        outputView = new TextView(this);
        outputView.setTextIsSelectable(true);
        outputView.setGravity(Gravity.START);
        outputView.setTextSize(12f);
        outputView.setText("Tools are ready.");
        outBox.addView(outputView);

        setContentView(root);
    }

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

    private void btn(LinearLayout parent, String label, Runnable action) {
        MaterialButton b = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(4));
        b.setLayoutParams(lp);
        parent.addView(b);
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

    private void run(String label, Work work) {
        append(label);
        executor.execute(() -> {
            String result;
            try { result = work.run(); } catch (Exception e) { result = "ERROR: " + e.getMessage(); }
            String final_ = result;
            runOnUiThread(() -> append(final_));
        });
    }

    private String text(TextInputEditText i) { return i.getText() == null ? "" : i.getText().toString().trim(); }
    private void append(String text) { if (outputView != null) outputView.append("\n\n" + text); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
    private interface Work { String run() throws Exception; }
}
