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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.tools.ClassCloneTool;
import pro.sketchware.tools.KeystoreTool;
import pro.sketchware.tools.LogReader;
import pro.sketchware.utility.SketchwareUtil;

public class DeveloperToolsActivity extends BaseAppCompatActivity {
    private String scId;
    private TextInputEditText keystorePathInput, keystorePasswordInput, keystoreAliasInput, sourceClassInput, targetClassInput, oldClassNameInput, newClassNameInput;
    private TextView outputView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle savedInstanceState) { enableEdgeToEdgeNoContrast(); super.onCreate(savedInstanceState); scId = getIntent().getStringExtra(ProjectToolsHubActivity.EXTRA_SC_ID); if (scId == null || scId.trim().isEmpty()) { SketchwareUtil.toastError("Project id missing"); finish(); return; } LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle("Developer Tools"); toolbar.setSubtitle("Project " + scId); toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); toolbar.setNavigationOnClickListener(v -> finish()); root.addView(toolbar); ScrollView scrollView = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); int pad = dp(16); content.setPadding(pad, pad, pad, pad * 2); scrollView.addView(content); root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); keystorePathInput = addInput(content, "Keystore path", InputType.TYPE_CLASS_TEXT, 1); keystorePasswordInput = addInput(content, "Keystore password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 1); keystoreAliasInput = addInput(content, "Keystore alias", InputType.TYPE_CLASS_TEXT, 1); addButton(content, "Compute keystore SHA-1", () -> run("Computing SHA-1", this::computeSha1)); sourceClassInput = addInput(content, "Source Java class path", InputType.TYPE_CLASS_TEXT, 1); targetClassInput = addInput(content, "Target Java class path", InputType.TYPE_CLASS_TEXT, 1); oldClassNameInput = addInput(content, "Old class name", InputType.TYPE_CLASS_TEXT, 1); newClassNameInput = addInput(content, "New class name", InputType.TYPE_CLASS_TEXT, 1); addButton(content, "Clone Java class", () -> run("Cloning class", this::cloneClass)); addButton(content, "Read latest system log", () -> run("Reading logs", this::readLogs)); outputView = new TextView(this); outputView.setTextIsSelectable(true); outputView.setGravity(Gravity.START); outputView.setPadding(0, dp(12), 0, 0); outputView.setText("Tools are ready."); content.addView(outputView); setContentView(root); }
    private TextInputEditText addInput(LinearLayout parent, String hint, int inputType, int minLines) { TextInputLayout layout = new TextInputLayout(this); layout.setHint(hint); TextInputEditText input = new TextInputEditText(this); input.setInputType(inputType); input.setMinLines(minLines); if (minLines > 1) input.setGravity(Gravity.TOP | Gravity.START); layout.addView(input); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, dp(8)); parent.addView(layout, lp); return input; }
    private void addButton(LinearLayout parent, String label, Runnable action) { MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle); button.setText(label); button.setOnClickListener(v -> action.run()); parent.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); }
    private String computeSha1() throws Exception { return "SHA-1: " + KeystoreTool.sha1(new File(text(keystorePathInput)), text(keystorePasswordInput), text(keystoreAliasInput)); }
    private String cloneClass() throws Exception { File source = new File(text(sourceClassInput)); File target = new File(text(targetClassInput)); ClassCloneTool.cloneJavaClass(source, target, text(oldClassNameInput), text(newClassNameInput)); return "Class copied to: " + target.getAbsolutePath(); }
    private String readLogs() throws Exception { List<String> lines = LogReader.read(300); StringBuilder out = new StringBuilder("System log lines: ").append(lines.size()).append('\n'); for (String line : lines) out.append(line).append('\n'); return out.toString(); }
    private void run(String label, Work work) { append(label + "..."); executor.execute(() -> { String text; try { text = work.run(); } catch (Exception e) { text = "ERROR: " + e.getMessage(); } String finalText = text; runOnUiThread(() -> append(finalText)); }); }
    private String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString().trim(); }
    private void append(String text) { if (outputView != null) outputView.append("\n\n" + text); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
    private interface Work { String run() throws Exception; }
}
