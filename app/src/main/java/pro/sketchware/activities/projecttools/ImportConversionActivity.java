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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.editor.importer.ImportJavaHelper;
import pro.sketchware.editor.importer.ImportLayoutHelper;
import pro.sketchware.editor.importer.JavaToBlocksPreprocessor;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.io.SafeFileOps;

public class ImportConversionActivity extends BaseAppCompatActivity {

    private String scId;
    private TextInputEditText javaInput, layoutInput;
    private TextView outputView;

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
        toolbar.setTitle("Import Helpers");
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

        // ── Java Analysis ─────────────────────────────────────────────────────
        LinearLayout javaBox = section(content, "Java Analysis",
                "Paste Java source or method body to extract imports, split statements, or save as a snippet.");
        javaInput = textArea(javaBox, "Java source or method body", 7);
        btn(javaBox, "Analyze Java imports", this::analyzeJavaImports);
        btn(javaBox, "Split Java statements", this::splitJavaStatements);
        btn(javaBox, "Save as import snippet", this::saveJavaSnippet);

        // ── Layout XML ────────────────────────────────────────────────────────
        LinearLayout xmlBox = section(content, "Layout XML",
                "Paste an Android XML layout to validate its structure or save it as a reusable snippet.");
        layoutInput = textArea(xmlBox, "Layout XML", 7);
        btn(xmlBox, "Validate layout XML", this::validateLayoutXml);
        btn(xmlBox, "Save layout snippet", this::saveLayoutXml);

        // ── Output ────────────────────────────────────────────────────────────
        LinearLayout outBox = section(content, "Output", null);
        outputView = new TextView(this);
        outputView.setTextIsSelectable(true);
        outputView.setGravity(Gravity.START);
        outputView.setTextSize(12f);
        outputView.setText(
                "Java snippets:   " + new File(ProjectToolPaths.getProjectEditableJavaDir(scId), "imports").getAbsolutePath()
                + "\nLayout snippets: " + new File(ProjectToolPaths.getProjectEditableResDir(scId), "layout").getAbsolutePath());
        outBox.addView(outputView);

        setContentView(root);
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
        lp.setMargins(0, 0, 0, dp(10));
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

    // ── Business logic (unchanged) ────────────────────────────────────────────

    private void analyzeJavaImports() {
        List<String> imports = ImportJavaHelper.importsOf(text(javaInput));
        StringBuilder out = new StringBuilder("Imports found: ").append(imports.size()).append('\n');
        for (String item : imports) out.append("• ").append(item).append('\n');
        append(out.toString());
    }

    private void splitJavaStatements() {
        List<String> stmts = JavaToBlocksPreprocessor.statements(text(javaInput));
        StringBuilder out = new StringBuilder("Statements found: ").append(stmts.size()).append('\n');
        for (String item : stmts) out.append("• ").append(item).append('\n');
        append(out.toString());
    }

    private void saveJavaSnippet() {
        try {
            String source = ImportJavaHelper.stripPackage(text(javaInput));
            if (source.trim().isEmpty()) { SketchwareUtil.toastError("Java content is empty"); return; }
            File dir = new File(ProjectToolPaths.getProjectEditableJavaDir(scId), "imports");
            SafeFileOps.ensureDirectory(dir);
            File out = new File(dir, "Imported_" + stamp() + ".java");
            SafeFileOps.writeUtf8Atomic(out, source);
            append("Saved Java snippet:\n" + out.getAbsolutePath());
        } catch (Exception e) {
            append("ERROR: " + e.getMessage());
        }
    }

    private void validateLayoutXml() {
        try {
            ImportLayoutHelper.parse(text(layoutInput));
            append("Layout XML is valid.");
        } catch (Exception e) {
            append("Layout XML error: " + e.getMessage());
        }
    }

    private void saveLayoutXml() {
        try {
            String xml = text(layoutInput);
            ImportLayoutHelper.parse(xml);
            File dir = new File(ProjectToolPaths.getProjectEditableResDir(scId), "layout");
            SafeFileOps.ensureDirectory(dir);
            File out = new File(dir, "imported_" + stamp().toLowerCase(Locale.US) + ".xml");
            SafeFileOps.writeUtf8Atomic(out, xml);
            append("Saved layout snippet:\n" + out.getAbsolutePath());
        } catch (Exception e) {
            append("ERROR: " + e.getMessage());
        }
    }

    private String stamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private String text(TextInputEditText i) { return i.getText() == null ? "" : i.getText().toString(); }
    private void append(String text) { if (outputView != null) outputView.append("\n\n" + text); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
