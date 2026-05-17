package pro.sketchware.activities.projecttools;

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
    @Override public void onCreate(Bundle savedInstanceState) { enableEdgeToEdgeNoContrast(); super.onCreate(savedInstanceState); scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID); if (scId == null || scId.trim().isEmpty()) { SketchwareUtil.toastError("Project id missing"); finish(); return; } LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle("Import Helpers"); toolbar.setSubtitle("Project " + scId); toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); toolbar.setNavigationOnClickListener(v -> finish()); root.addView(toolbar); ScrollView scrollView = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); int pad = dp(16); content.setPadding(pad, pad, pad, pad * 2); scrollView.addView(content); root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); javaInput = addTextArea(content, "Java source or method body", 8); addButton(content, "Analyze Java imports", this::analyzeJavaImports); addButton(content, "Split Java statements", this::splitJavaStatements); addButton(content, "Save Java import snippet", this::saveJavaSnippet); layoutInput = addTextArea(content, "Layout XML", 8); addButton(content, "Validate layout XML", this::validateLayoutXml); addButton(content, "Save layout XML", this::saveLayoutXml); outputView = new TextView(this); outputView.setTextIsSelectable(true); outputView.setGravity(Gravity.START); outputView.setPadding(0, dp(12), 0, 0); outputView.setText("Java snippets: " + new File(ProjectToolPaths.getProjectEditableJavaDir(scId), "imports").getAbsolutePath() + "\nLayout snippets: " + new File(ProjectToolPaths.getProjectEditableResDir(scId), "layout").getAbsolutePath()); content.addView(outputView); setContentView(root); }
    private TextInputEditText addTextArea(LinearLayout parent, String hint, int minLines) { TextInputLayout layout = new TextInputLayout(this); layout.setHint(hint); TextInputEditText input = new TextInputEditText(this); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); input.setMinLines(minLines); input.setGravity(Gravity.TOP | Gravity.START); input.setHorizontallyScrolling(true); layout.addView(input); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, dp(8)); parent.addView(layout, lp); return input; }
    private void addButton(LinearLayout parent, String label, Runnable action) { MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); button.setText(label); button.setOnClickListener(v -> action.run()); parent.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); }
    private void analyzeJavaImports() { List<String> imports = ImportJavaHelper.importsOf(text(javaInput)); StringBuilder out = new StringBuilder("Imports found: ").append(imports.size()).append('\n'); for (String item : imports) out.append("• ").append(item).append('\n'); append(out.toString()); }
    private void splitJavaStatements() { List<String> statements = JavaToBlocksPreprocessor.statements(text(javaInput)); StringBuilder out = new StringBuilder("Statements found: ").append(statements.size()).append('\n'); for (String item : statements) out.append("• ").append(item).append('\n'); append(out.toString()); }
    private void saveJavaSnippet() { try { String source = ImportJavaHelper.stripPackage(text(javaInput)); if (source.trim().isEmpty()) { SketchwareUtil.toastError("Java content is empty"); return; } File dir = new File(ProjectToolPaths.getProjectEditableJavaDir(scId), "imports"); SafeFileOps.ensureDirectory(dir); File out = new File(dir, "Imported_" + stamp() + ".java"); SafeFileOps.writeUtf8Atomic(out, source); append("Saved Java snippet: " + out.getAbsolutePath()); } catch (Exception e) { append("ERROR: " + e.getMessage()); } }
    private void validateLayoutXml() { try { ImportLayoutHelper.parse(text(layoutInput)); append("Layout XML parsed successfully."); } catch (Exception e) { append("Layout XML error: " + e.getMessage()); } }
    private void saveLayoutXml() { try { String xml = text(layoutInput); ImportLayoutHelper.parse(xml); File dir = new File(ProjectToolPaths.getProjectEditableResDir(scId), "layout"); SafeFileOps.ensureDirectory(dir); File out = new File(dir, "imported_" + stamp().toLowerCase(Locale.US) + ".xml"); SafeFileOps.writeUtf8Atomic(out, xml); append("Saved layout XML: " + out.getAbsolutePath()); } catch (Exception e) { append("ERROR: " + e.getMessage()); } }
    private String stamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    private void append(String text) { if (outputView != null) outputView.append("\n\n" + text); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
