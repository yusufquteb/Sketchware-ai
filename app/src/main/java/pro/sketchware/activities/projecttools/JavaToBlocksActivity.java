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
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.ai.tools.blocks.BlockLogicReader;
import pro.sketchware.ai.tools.blocks.BlockLogicWriter;
import pro.sketchware.editor.importer.JavaToBlocksConverter;
import pro.sketchware.util.SketchwareFileEncryptor;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class JavaToBlocksActivity extends BaseAppCompatActivity {

    private String scId;

    // Single Method tab
    private TextInputEditText singleInput;
    private TextView singlePreviewView;
    private Spinner activitySpinner, eventSpinner;
    private ExtendedFloatingActionButton singleFab;

    // Full Class tab
    private TextInputEditText classInput;
    private TextView classAnalysisView;
    private ExtendedFloatingActionButton classFab;

    // Shared state
    private BlockLogicReader.LogicFile logicFile;
    private final List<String> activityNames = new ArrayList<>();
    private final List<String> eventNames    = new ArrayList<>();
    private final ExecutorService executor   = Executors.newSingleThreadExecutor();

    // Tab panels
    private View singlePanel;
    private View classPanel;

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

        String projectName = loadProjectName();

        // ── Root layout ───────────────────────────────────────────────────
        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ── Toolbar ───────────────────────────────────────────────────────
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Java → Blocks");
        toolbar.setSubtitle(projectName);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        // ── TabLayout ─────────────────────────────────────────────────────
        TabLayout tabs = new TabLayout(this);
        tabs.addTab(tabs.newTab().setText("Single Method"));
        tabs.addTab(tabs.newTab().setText("Full Class"));
        root.addView(tabs);

        // ── Content container ─────────────────────────────────────────────
        FrameLayout tabContent = new FrameLayout(this);
        LinearLayout.LayoutParams tabContentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        tabContent.setLayoutParams(tabContentLp);
        root.addView(tabContent);

        // ── Build tabs ────────────────────────────────────────────────────
        singlePanel = buildSingleMethodPanel();
        classPanel  = buildFullClassPanel();
        tabContent.addView(singlePanel);
        tabContent.addView(classPanel);

        // ── FABs ──────────────────────────────────────────────────────────
        singleFab = makeFab("Insert blocks", R.drawable.ic_mtrl_save, v -> insertBlocks());
        classFab  = makeFab("Convert all",   R.drawable.ic_mtrl_save, v -> convertAll());

        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fabLp.gravity = Gravity.BOTTOM | Gravity.END;
        fabLp.setMargins(0, 0, dp(16), dp(24));

        rootFrame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootFrame.addView(singleFab, fabLp);

        FrameLayout.LayoutParams fabLp2 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fabLp2.gravity = Gravity.BOTTOM | Gravity.END;
        fabLp2.setMargins(0, 0, dp(16), dp(24));
        rootFrame.addView(classFab, fabLp2);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
        });
        showTab(0);

        setContentView(rootFrame);
        loadLogicFile();
    }

    // ── Tab building ──────────────────────────────────────────────────────────

    private View buildSingleMethodPanel() {
        NestedScrollView scroll = new NestedScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, dp(96));

        // Info chip
        Chip infoChip = new Chip(this);
        infoChip.setText("Auto-detect method signature");
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
        label(targetBox, "Activity:");
        activitySpinner = spinner(targetBox);
        label(targetBox, "Event:");
        eventSpinner = spinner(targetBox);

        activitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < activityNames.size()) updateEventSpinner(activityNames.get(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Java input card
        LinearLayout inputBox = sectionCard(content, "Java Code",
                "Paste a Java method body or full method signature. Each statement becomes one block.");

        TextInputLayout til = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("Paste Java method or its body...");
        singleInput = new TextInputEditText(til.getContext());
        singleInput.setTypeface(Typeface.MONOSPACE);
        singleInput.setMinLines(12);
        singleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        singleInput.setGravity(Gravity.TOP | Gravity.START);
        til.addView(singleInput);
        LinearLayout.LayoutParams tilLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tilLp.setMargins(0, 0, 0, dp(12));
        til.setLayoutParams(tilLp);
        inputBox.addView(til);

        MaterialButton previewBtn = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        previewBtn.setText("Preview blocks");
        previewBtn.setOnClickListener(v -> previewSingle());
        inputBox.addView(previewBtn);

        // Output card
        LinearLayout outBox = sectionCard(content, "Block Preview", null);
        singlePreviewView = new TextView(this);
        singlePreviewView.setTextIsSelectable(true);
        singlePreviewView.setTextSize(12f);
        singlePreviewView.setTypeface(Typeface.MONOSPACE);
        singlePreviewView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        singlePreviewView.setText("Loading logic file...");
        outBox.addView(singlePreviewView);

        scroll.addView(content);
        return scroll;
    }

    private View buildFullClassPanel() {
        NestedScrollView scroll = new NestedScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, dp(96));

        // Info chip
        Chip infoChip = new Chip(this);
        infoChip.setText("Paste a full Java class to auto-classify all methods");
        infoChip.setClickable(false);
        infoChip.setCheckable(false);
        infoChip.setFocusable(false);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.setMargins(0, 0, 0, dp(12));
        infoChip.setLayoutParams(chipLp);
        content.addView(infoChip);

        // Java class input card
        LinearLayout inputBox = sectionCard(content, "Java Class Source",
                "Paste full Java class source. All methods will be auto-detected and classified.");

        TextInputLayout til = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("Paste Java class source...");
        classInput = new TextInputEditText(til.getContext());
        classInput.setTypeface(Typeface.MONOSPACE);
        classInput.setMinLines(15);
        classInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        classInput.setGravity(Gravity.TOP | Gravity.START);
        til.addView(classInput);
        LinearLayout.LayoutParams tilLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tilLp.setMargins(0, 0, 0, dp(12));
        til.setLayoutParams(tilLp);
        inputBox.addView(til);

        MaterialButton analyzeBtn = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        analyzeBtn.setText("Analyze class");
        analyzeBtn.setOnClickListener(v -> analyzeClass());
        inputBox.addView(analyzeBtn);

        // Analysis output card
        LinearLayout outBox = sectionCard(content, "Analysis", null);
        classAnalysisView = new TextView(this);
        classAnalysisView.setTextIsSelectable(true);
        classAnalysisView.setTextSize(12f);
        classAnalysisView.setTypeface(Typeface.MONOSPACE);
        classAnalysisView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        classAnalysisView.setText("Paste a Java class and tap \"Analyze class\".");
        outBox.addView(classAnalysisView);

        scroll.addView(content);
        return scroll;
    }

    private void showTab(int position) {
        singlePanel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        classPanel.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        singleFab.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        classFab.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
    }

    // ── Logic file loading ────────────────────────────────────────────────────

    private void loadLogicFile() {
        executor.execute(() -> {
            File lf = new File(ProjectToolPaths.getProjectDataDir(scId), "logic");
            BlockLogicReader.LogicFile loaded = BlockLogicReader.read(lf, scId);
            runOnUiThread(() -> {
                if (loaded == null || loaded.events.isEmpty()) {
                    String msg = "No events found in logic file.\nBuild your project first to generate logic data.";
                    singlePreviewView.setText(msg);
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
                singlePreviewView.setText("Loaded " + loaded.events.size() + " events across "
                        + activityNames.size() + " activities.\n\nPaste Java code and tap \"Preview blocks\".");
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

    // ── Single Method tab logic ───────────────────────────────────────────────

    private void previewSingle() {
        String code = text(singleInput);
        if (code.trim().isEmpty()) { SketchwareUtil.toastError("Paste Java code first"); return; }

        // Check if input contains a method signature
        java.util.regex.Pattern sigPat = java.util.regex.Pattern.compile(
                "(public|private|protected)\\s+(static\\s+)?\\w+\\s+\\w+\\s*\\(");
        boolean hasSignature = sigPat.matcher(code).find();

        StringBuilder sb = new StringBuilder();

        if (hasSignature) {
            List<JavaToBlocksConverter.ParsedMethod> methods = JavaToBlocksConverter.parseMethods(code);
            if (methods.isEmpty()) {
                sb.append("Could not parse method signature. Treating as raw body.\n\n");
                appendBodyPreview(sb, code);
            } else {
                JavaToBlocksConverter.ParsedMethod method = methods.get(0);
                JavaToBlocksConverter.MethodKind kind = JavaToBlocksConverter.classify(method);
                sb.append("Method:      ").append(method.name).append("\n");
                sb.append("Kind:        ").append(kind.name()).append("\n");
                sb.append("Params:      ").append(method.params.isEmpty() ? "(none)" : method.params).append("\n");
                sb.append("Override:    ").append(method.isOverride).append("\n\n");
                List<JsonObject> blocks = JavaToBlocksConverter.convertMethodBody(method.body);
                sb.append("Blocks (").append(blocks.size()).append("):\n");
                appendBlocksPreview(sb, blocks);
            }
        } else {
            appendBodyPreview(sb, code);
        }

        singlePreviewView.setText(sb);
    }

    private void appendBodyPreview(StringBuilder sb, String code) {
        List<JsonObject> blocks = JavaToBlocksConverter.convertMethodBody(code);
        sb.append("Blocks (").append(blocks.size()).append("):\n");
        appendBlocksPreview(sb, blocks);
    }

    private void appendBlocksPreview(StringBuilder sb, List<JsonObject> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            JsonObject b = blocks.get(i);
            sb.append("\n[").append(i + 1).append("] ")
              .append(b.get("opCode").getAsString()).append(":\n   ");
            if (b.has("parameters") && b.get("parameters").isJsonArray()) {
                JsonArray params = b.getAsJsonArray("parameters");
                if (params.size() > 0) {
                    sb.append(params.get(0).getAsString());
                    if (params.size() > 1) sb.append(" → ").append(params.get(1).getAsString());
                }
            }
            sb.append('\n');
        }
    }

    private void insertBlocks() {
        if (logicFile == null) { SketchwareUtil.toastError("Logic file not loaded"); return; }
        String code = text(singleInput);
        if (code.trim().isEmpty()) { SketchwareUtil.toastError("Paste Java code first"); return; }

        int actPos = activitySpinner.getSelectedItemPosition();
        int evPos  = eventSpinner.getSelectedItemPosition();
        if (actPos < 0 || evPos < 0 || actPos >= activityNames.size() || evPos >= eventNames.size()) {
            SketchwareUtil.toastError("Select a target event first");
            return;
        }
        String actName  = activityNames.get(actPos);
        String evName   = eventNames.get(evPos);
        String fullEvent = actName + ".java_" + evName;

        // Determine body to convert
        String body = code;
        java.util.regex.Pattern sigPat = java.util.regex.Pattern.compile(
                "(public|private|protected)\\s+(static\\s+)?\\w+\\s+\\w+\\s*\\(");
        if (sigPat.matcher(code).find()) {
            List<JavaToBlocksConverter.ParsedMethod> methods = JavaToBlocksConverter.parseMethods(code);
            if (!methods.isEmpty()) body = methods.get(0).body;
        }

        final String finalBody = body;
        executor.execute(() -> {
            File logicFileRef = new File(ProjectToolPaths.getProjectDataDir(scId), "logic");
            BlockLogicWriter writer = new BlockLogicWriter(logicFileRef, scId);
            List<JsonObject> blocks = JavaToBlocksConverter.convertMethodBody(finalBody);
            int inserted = 0;
            StringBuilder errors = new StringBuilder();

            for (JsonObject block : blocks) {
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
                    singlePreviewView.setText("Inserted " + finalInserted + " block(s) into:\n"
                            + fullEvent + "\n\nReopen the Logic Editor to see the changes."
                            + (finalErrors.isEmpty() ? "" : "\n" + finalErrors));
                } else {
                    SketchwareUtil.toastError("Failed to save logic file");
                    singlePreviewView.setText("Save failed." + finalErrors);
                }
            });
        });
    }

    // ── Full Class tab logic ──────────────────────────────────────────────────

    private void analyzeClass() {
        String source = text(classInput);
        if (source.trim().isEmpty()) { SketchwareUtil.toastError("Paste Java class source first"); return; }

        List<JavaToBlocksConverter.ParsedMethod> methods = JavaToBlocksConverter.parseMethods(source);
        if (methods.isEmpty()) {
            classAnalysisView.setText("No methods found. Make sure the source contains public/private/protected methods.");
            return;
        }

        String defaultActName = activityNames.isEmpty() ? "MainActivity" : activityNames.get(0);

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(methods.size()).append(" method(s):\n");

        for (JavaToBlocksConverter.ParsedMethod method : methods) {
            JavaToBlocksConverter.MethodKind kind = JavaToBlocksConverter.classify(method);
            List<JsonObject> blocks = JavaToBlocksConverter.convertMethodBody(method.body);
            sb.append("\n").append(method.name).append(" → ").append(kind.name()).append("\n");
            sb.append("  Blocks: ").append(blocks.size()).append("\n");

            switch (kind) {
                case LIFECYCLE_EVENT:
                    sb.append("  Action: add blocks to \"").append(method.name).append("\" event\n");
                    break;
                case KNOWN_EVENT:
                    sb.append("  Action: add blocks to \"").append(method.name).append("\" event\n");
                    break;
                case MORE_BLOCK:
                    sb.append("  Action: create More Block \"onMoreBlock_").append(method.name).append("\"\n");
                    sb.append("  Entry:  ").append(defaultActName).append(".java_onMoreBlock_").append(method.name).append("\n");
                    break;
            }

            // Show block types summary
            if (!blocks.isEmpty()) {
                sb.append("  Block types: ");
                java.util.Map<String, Integer> opCounts = new java.util.LinkedHashMap<>();
                for (JsonObject b : blocks) {
                    String op = b.get("opCode").getAsString();
                    opCounts.merge(op, 1, Integer::sum);
                }
                boolean first = true;
                for (java.util.Map.Entry<String, Integer> e : opCounts.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(e.getKey());
                    if (e.getValue() > 1) sb.append("(x").append(e.getValue()).append(")");
                    first = false;
                }
                sb.append("\n");
            }
        }

        int totalBlocks = 0;
        for (JavaToBlocksConverter.ParsedMethod m : methods) {
            totalBlocks += JavaToBlocksConverter.convertMethodBody(m.body).size();
        }
        sb.append("\nTotal blocks to insert: ").append(totalBlocks);

        classAnalysisView.setText(sb);
    }

    private void convertAll() {
        if (logicFile == null) { SketchwareUtil.toastError("Logic file not loaded"); return; }
        String source = text(classInput);
        if (source.trim().isEmpty()) { SketchwareUtil.toastError("Paste Java class source first"); return; }

        List<JavaToBlocksConverter.ParsedMethod> methods = JavaToBlocksConverter.parseMethods(source);
        if (methods.isEmpty()) { SketchwareUtil.toastError("No methods found"); return; }

        String defaultActName = activityNames.isEmpty() ? "MainActivity" : activityNames.get(0);

        executor.execute(() -> {
            File logicFileRef = new File(ProjectToolPaths.getProjectDataDir(scId), "logic");
            BlockLogicWriter writer = new BlockLogicWriter(logicFileRef, scId);

            int totalInserted = 0;
            int totalMethods  = 0;
            StringBuilder errors = new StringBuilder();

            for (JavaToBlocksConverter.ParsedMethod method : methods) {
                JavaToBlocksConverter.MethodKind kind = JavaToBlocksConverter.classify(method);
                List<JsonObject> blocks = JavaToBlocksConverter.convertMethodBody(method.body);
                if (blocks.isEmpty()) continue;

                String fullEventName;
                if (kind == JavaToBlocksConverter.MethodKind.MORE_BLOCK) {
                    fullEventName = defaultActName + ".java_onMoreBlock_" + method.name;
                    // Create event if it doesn't exist
                    if (logicFile.findEvent(fullEventName) == null) {
                        BlockLogicReader.EventEntry newEvent = new BlockLogicReader.EventEntry(
                                fullEventName, new JsonArray());
                        logicFile.events.add(newEvent);
                    }
                } else {
                    // LIFECYCLE_EVENT or KNOWN_EVENT — find the matching event in logicFile
                    String found = null;
                    for (BlockLogicReader.EventEntry ev : logicFile.events) {
                        if (ev.eventName.equals(method.name) || ev.eventName.endsWith("_" + method.name)) {
                            found = ev.name;
                            break;
                        }
                    }
                    if (found != null) {
                        fullEventName = found;
                    } else {
                        // Create a new event entry with the default activity prefix
                        fullEventName = defaultActName + ".java_" + method.name;
                        if (logicFile.findEvent(fullEventName) == null) {
                            BlockLogicReader.EventEntry newEvent = new BlockLogicReader.EventEntry(
                                    fullEventName, new JsonArray());
                            logicFile.events.add(newEvent);
                        }
                    }
                }

                for (JsonObject block : blocks) {
                    String err = writer.addBlock(logicFile, fullEventName, block, -1);
                    if (err == null) totalInserted++;
                    else errors.append("\n").append(method.name).append(": ").append(err);
                }
                totalMethods++;
            }

            String serialised = BlockLogicReader.serialise(logicFile);
            boolean saved = SketchwareFileEncryptor.encryptAndSaveFile(scId, "logic", serialised);

            final int fi = totalInserted;
            final int fm = totalMethods;
            final String fe = errors.toString();
            runOnUiThread(() -> {
                if (saved) {
                    SketchwareUtil.toast(fi + " blocks from " + fm + " methods inserted");
                    classAnalysisView.setText("Converted " + fm + " method(s), inserted " + fi + " block(s).\n\n"
                            + "Reopen the Logic Editor to see the changes."
                            + (fe.isEmpty() ? "" : "\n\nErrors:\n" + fe));
                } else {
                    SketchwareUtil.toastError("Failed to save logic file");
                    classAnalysisView.setText("Save failed." + fe);
                }
            });
        });
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private ExtendedFloatingActionButton makeFab(String text, int iconRes, View.OnClickListener listener) {
        ExtendedFloatingActionButton fab = new ExtendedFloatingActionButton(this);
        fab.setText(text);
        fab.setIconResource(iconRes);
        fab.setOnClickListener(listener);
        return fab;
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

    private String loadProjectName() {
        try {
            HashMap<String, Object> map = lC.b(scId);
            if (map != null) {
                String name = yB.c(map, "my_sc_app_name");
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {}
        return "Project " + scId;
    }

    private String text(TextInputEditText i) { return i.getText() == null ? "" : i.getText().toString(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
