package pro.sketchware.activities.projecttools;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.ai.tools.blocks.BlockLogicReader;
import pro.sketchware.ai.tools.blocks.BlockLogicWriter;
import pro.sketchware.editor.importer.JavaToBlocksPreprocessor;
import pro.sketchware.util.SketchwareFileEncryptor;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class JavaToBlocksActivity extends BaseAppCompatActivity {

    private String scId;
    private TextInputEditText javaInput;
    private TextView outputView;
    private Spinner activitySpinner, eventSpinner;
    private BlockLogicReader.LogicFile logicFile;
    private final List<String> activityNames = new ArrayList<>();
    private final List<String> eventNames = new ArrayList<>();
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

        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Java → Blocks");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, dp(88));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Info chip
        Chip infoChip = new Chip(this);
        infoChip.setText("Each Java statement → addSourceDirectly block");
        infoChip.setClickable(false);
        infoChip.setCheckable(false);
        infoChip.setFocusable(false);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.setMargins(0, 0, 0, dp(12));
        infoChip.setLayoutParams(chipLp);
        content.addView(infoChip);

        // Target event card
        LinearLayout targetBox = sectionCard(content, "Target Event",
                "Choose the activity and event where blocks will be inserted.");

        TextView actLabel = label(targetBox, "Activity:");
        activitySpinner = spinner(targetBox);
        TextView evLabel = label(targetBox, "Event:");
        eventSpinner = spinner(targetBox);

        activitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < activityNames.size()) updateEventSpinner(activityNames.get(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Java input card
        LinearLayout inputBox = sectionCard(content, "Java Code",
                "Paste a Java method body. Each statement becomes one block.");

        TextInputLayout til = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("Java method body...");
        javaInput = new TextInputEditText(til.getContext());
        javaInput.setTypeface(Typeface.MONOSPACE);
        javaInput.setMinLines(10);
        javaInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        javaInput.setGravity(Gravity.TOP | Gravity.START);
        til.addView(javaInput);
        LinearLayout.LayoutParams tilLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tilLp.setMargins(0, 0, 0, dp(12));
        til.setLayoutParams(tilLp);
        inputBox.addView(til);

        MaterialButton previewBtn = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        previewBtn.setText("Preview conversion");
        previewBtn.setOnClickListener(v -> previewConversion());
        inputBox.addView(previewBtn);

        // Output card
        LinearLayout outBox = sectionCard(content, "Output", null);
        outputView = new TextView(this);
        outputView.setTextIsSelectable(true);
        outputView.setTextSize(12f);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        outputView.setText("Loading logic file...");
        outBox.addView(outputView);

        rootFrame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // FAB
        ExtendedFloatingActionButton fab = new ExtendedFloatingActionButton(this);
        fab.setText("Insert blocks");
        fab.setIconResource(R.drawable.ic_mtrl_save);
        fab.setOnClickListener(v -> insertBlocks());
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fabLp.gravity = Gravity.BOTTOM | Gravity.END;
        fabLp.setMargins(0, 0, dp(16), dp(24));
        fab.setLayoutParams(fabLp);
        rootFrame.addView(fab);

        setContentView(rootFrame);
        loadLogicFile();
    }

    private void loadLogicFile() {
        executor.execute(() -> {
            File lf = new File(ProjectToolPaths.getProjectDataDir(scId), "logic");
            BlockLogicReader.LogicFile loaded = BlockLogicReader.read(lf, scId);
            runOnUiThread(() -> {
                if (loaded == null || loaded.events.isEmpty()) {
                    outputView.setText("No events found in logic file.\nBuild your project first to generate logic data.");
                    return;
                }
                logicFile = loaded;
                activityNames.clear();
                activityNames.addAll(loaded.activityNames());
                ArrayAdapter<String> aa = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, activityNames);
                aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                activitySpinner.setAdapter(aa);
                if (!activityNames.isEmpty()) updateEventSpinner(activityNames.get(0));
                outputView.setText("Loaded " + loaded.events.size() + " events across "
                        + activityNames.size() + " activities.\n\nPaste Java code and tap \"Insert blocks\".");
            });
        });
    }

    private void updateEventSpinner(String actName) {
        if (logicFile == null) return;
        eventNames.clear();
        for (BlockLogicReader.EventEntry e : logicFile.eventsForActivity(actName)) {
            eventNames.add(e.eventName);
        }
        ArrayAdapter<String> aa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, eventNames);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        eventSpinner.setAdapter(aa);
    }

    private void previewConversion() {
        String code = text(javaInput);
        if (code.trim().isEmpty()) { SketchwareUtil.toastError("Paste Java code first"); return; }
        List<String> stmts = JavaToBlocksPreprocessor.statements(code);
        StringBuilder sb = new StringBuilder("Preview — ")
                .append(stmts.size()).append(" block(s):\n");
        for (int i = 0; i < stmts.size(); i++) {
            sb.append("\n[").append(i + 1).append("] addSourceDirectly:\n   ").append(stmts.get(i)).append('\n');
        }
        outputView.setText(sb);
    }

    private void insertBlocks() {
        if (logicFile == null) { SketchwareUtil.toastError("Logic file not loaded"); return; }
        String code = text(javaInput);
        if (code.trim().isEmpty()) { SketchwareUtil.toastError("Paste Java code first"); return; }

        int actPos = activitySpinner.getSelectedItemPosition();
        int evPos  = eventSpinner.getSelectedItemPosition();
        if (actPos < 0 || evPos < 0 || actPos >= activityNames.size() || evPos >= eventNames.size()) {
            SketchwareUtil.toastError("Select a target event first");
            return;
        }
        String actName = activityNames.get(actPos);
        String evName  = eventNames.get(evPos);
        String fullEvent = actName + ".java_" + evName;

        List<String> stmts = JavaToBlocksPreprocessor.statements(code);
        if (stmts.isEmpty()) { SketchwareUtil.toastError("No statements found"); return; }

        executor.execute(() -> {
            File logicFileRef = new File(ProjectToolPaths.getProjectDataDir(scId), "logic");
            BlockLogicWriter writer = new BlockLogicWriter(logicFileRef, scId);
            int inserted = 0;
            StringBuilder errors = new StringBuilder();

            for (String stmt : stmts) {
                JsonObject block = new JsonObject();
                block.addProperty("opCode",    "addSourceDirectly");
                block.addProperty("spec",      "add source directly %s.inputOnly");
                block.addProperty("type",      " ");
                block.addProperty("typeName",  "");
                block.addProperty("target",    "");
                block.addProperty("nextBlock", -1);
                block.addProperty("subStack1", -1);
                block.addProperty("subStack2", -1);
                JsonArray params = new JsonArray();
                params.add(stmt);
                block.add("parameters", params);
                String err = writer.addBlock(logicFile, fullEvent, block, -1);
                if (err == null) inserted++;
                else errors.append("\nError: ").append(err);
            }

            String serialised = BlockLogicReader.serialise(logicFile);
            boolean saved = SketchwareFileEncryptor.encryptAndSaveFile(scId, "logic", serialised);
            final int finalInserted = inserted;
            final String finalErrors = errors.toString();
            runOnUiThread(() -> {
                if (saved) {
                    SketchwareUtil.toast(finalInserted + " blocks inserted into " + evName);
                    outputView.setText("✓ Inserted " + finalInserted + " block(s) into:\n"
                            + fullEvent + "\n\nReopen the Logic Editor to see the changes."
                            + (finalErrors.isEmpty() ? "" : "\n" + finalErrors));
                } else {
                    SketchwareUtil.toastError("Failed to save logic file");
                    outputView.setText("Save failed." + finalErrors);
                }
            });
        });
    }

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
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sLp.setMargins(0, dp(2), 0, dp(10));
            s.setLayoutParams(sLp);
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

    private TextView label(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        parent.addView(tv);
        return tv;
    }

    private Spinner spinner(LinearLayout parent) {
        Spinner sp = new Spinner(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(12));
        sp.setLayoutParams(lp);
        parent.addView(sp);
        return sp;
    }

    private String text(TextInputEditText i) { return i.getText() == null ? "" : i.getText().toString(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
