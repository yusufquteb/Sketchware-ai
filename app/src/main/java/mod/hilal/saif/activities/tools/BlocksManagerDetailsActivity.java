package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BlocksManagerDetailsActivity extends BaseAppCompatActivity {

    private static final String BLOCK_EXPORT_PATH = new File(FileUtil.getExternalStorageDir(), ".sketchware/resources/block/export/").getAbsolutePath();

    private final ArrayList<HashMap<String, Object>> filtered_list = new ArrayList<>();
    private final ArrayList<Integer> reference_list = new ArrayList<>();
    private ArrayList<HashMap<String, Object>> all_blocks_list = new ArrayList<>();
    private String blocks_path = "";
    private String mode = "normal";
    private ArrayList<HashMap<String, Object>> pallet_list = new ArrayList<>();
    private String pallet_path = "";
    private int palette = 0;
    private Parcelable listViewSavedState;

    private Toolbar toolbar;
    private ListView block_list;
    private LinearLayout background;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fab_button;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocks_manager_details);

        background = findViewById(R.id.background);
        block_list = findViewById(R.id.block_list);
        fab_button = findViewById(R.id.fab_button);

        initialize();
        _receive_intents();
    }

    private void initialize() {

        toolbar = (Toolbar) getLayoutInflater().inflate(R.layout.toolbar_improved, background, false);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(view -> onBackPressed());
        background.addView(toolbar, 0);

        fab_button.setOnClickListener(v -> {
            Object paletteColor = pallet_list.get(palette - 9).get("color");
            if (paletteColor instanceof String) {
                Intent intent = new Intent(getApplicationContext(), BlocksManagerCreatorActivity.class);
                intent.putExtra("mode", "add");
                intent.putExtra("color", (String) paletteColor);
                intent.putExtra("path", blocks_path);
                intent.putExtra("pallet", String.valueOf(palette));
                startActivity(intent);
            } else {
                SketchwareUtil.toastError("Invalid color of palette #" + (palette - 9));
            }
        });
    }

    public void openFileExplorerImport() {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"json"});
        options.setTitle("Select a JSON file");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                if (FileUtil.readFile(file.getAbsolutePath()).isEmpty()) {
                    SketchwareUtil.toastError("The selected file is empty!");
                } else if (FileUtil.readFile(file.getAbsolutePath()).equals("[]")) {
                    SketchwareUtil.toastError("The selected file is empty!");
                } else {
                    try {
                        ArrayList<HashMap<String, Object>> readMap = getGson().fromJson(FileUtil.readFile(file.getAbsolutePath()), Helper.TYPE_MAP_LIST);
                        _importBlocks(readMap);
                    } catch (JsonParseException e) {
                        SketchwareUtil.toastError("Invalid JSON file");
                    }
                }
            }
        };

        FilePickerDialogFragment dialog = new FilePickerDialogFragment(options, callback);

        dialog.show(getSupportFragmentManager(), "filePickerDialog");
    }

    @Override
    public void onStop() {
        super.onStop();
        listViewSavedState = block_list.onSaveInstanceState();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (listViewSavedState != null) {
            block_list.onRestoreInstanceState(listViewSavedState);
            _refreshLists();
        }
    }

    @Override
    public void onBackPressed() {
        if (mode.equals("editor")) {
            mode = "normal";
            Parcelable savedState = block_list.onSaveInstanceState();
            block_list.setAdapter(new Adapter(filtered_list));
            ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
            block_list.onRestoreInstanceState(savedState);
            fabButtonVisibility(true);
            onCreateOptionsMenu(toolbar.getMenu());
        } else {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        if (Integer.parseInt(getIntent().getStringExtra("position")) != -1) {
            if (mode.equals("normal")) {
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Swap").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_swap_vertical)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Import");
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Export");
                // ── Block tools ────────────────────────────────────────
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "↑ Sort A→Z");
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "↓ Sort Z→A");
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "🗑 Remove Duplicates");
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "✨ AI Optimize");
            } else {
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Swap").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        String title = menuItem.getTitle().toString();
        switch (title) {
            case "Swap":
                if (mode.equals("normal")) {
                    mode = "editor";
                    fabButtonVisibility(false);
                } else {
                    mode = "normal";
                    fabButtonVisibility(true);
                }
                Parcelable savedInstanceState = block_list.onSaveInstanceState();
                block_list.setAdapter(new Adapter(filtered_list));
                ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
                block_list.onRestoreInstanceState(savedInstanceState);
                onCreateOptionsMenu(toolbar.getMenu());
                break;

            case "Import":
                openFileExplorerImport();
                break;

            case "Export":
                Object paletteName = pallet_list.get(palette - 9).get("name");
                if (paletteName instanceof String) {
                    String exportTo = new File(BLOCK_EXPORT_PATH, paletteName + ".json").getAbsolutePath();
                    FileUtil.writeFile(exportTo, getGson().toJson(filtered_list));
                    SketchwareUtil.toast("Successfully exported blocks to:\n" + exportTo, Toast.LENGTH_LONG);
                } else {
                    SketchwareUtil.toastError("Invalid name of palette #" + (palette - 9));
                }
                break;

            case "↑ Sort A→Z":
                sortBlocksAlpha(true);
                break;

            case "↓ Sort Z→A":
                sortBlocksAlpha(false);
                break;

            case "🗑 Remove Duplicates":
                removeDuplicateBlocks();
                break;

            case "✨ AI Optimize":
                launchGroqOptimizer();
                break;

            default:
                return false;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    // ── Block Tools ───────────────────────────────────────────────────────

    private void sortBlocksAlpha(boolean ascending) {
        filtered_list.sort((a, b) -> {
            String na = a.get("name") != null ? a.get("name").toString() : "";
            String nb = b.get("name") != null ? b.get("name").toString() : "";
            return ascending ? na.compareToIgnoreCase(nb) : nb.compareToIgnoreCase(na);
        });
        // Write sorted positions back into all_blocks_list
        for (int i = 0; i < filtered_list.size(); i++) {
            int ref = reference_list.get(i);
            all_blocks_list.set(ref, filtered_list.get(i));
        }
        // ── Persist BOTH files atomically ─────────────────────────────
        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
        FileUtil.writeFile(pallet_path, getGson().toJson(pallet_list)); // palette unchanged but keep in sync
        _refreshLists();
        SketchwareUtil.toast(ascending ? "Sorted A→Z" : "Sorted Z→A");
    }

    private void removeDuplicateBlocks() {
        // Build map: key → first occurrence index (to keep), rest are duplicates
        java.util.LinkedHashMap<String, Integer> firstSeen = new java.util.LinkedHashMap<>();
        java.util.List<Integer> dupIndices = new ArrayList<>();
        java.util.List<String> dupLabels = new ArrayList<>();

        for (int i = 0; i < all_blocks_list.size(); i++) {
            HashMap<String, Object> b = all_blocks_list.get(i);
            Object p = b.get("palette");
            if (!(p instanceof String)) continue;
            try { if (Integer.parseInt((String) p) != palette) continue; }
            catch (NumberFormatException ignored) { continue; }

            String key = b.get("name") + "|" + b.get("spec");
            if (firstSeen.containsKey(key)) {
                dupIndices.add(i);
                dupLabels.add(String.valueOf(b.get("name"))
                    + "  (type: " + b.get("type") + ")");
            } else {
                firstSeen.put(key, i);
            }
        }

        if (dupIndices.isEmpty()) {
            SketchwareUtil.toast("No duplicate blocks found");
            return;
        }

        // Build checklist: all duplicates pre-checked
        boolean[] checked = new boolean[dupIndices.size()];
        java.util.Arrays.fill(checked, true);
        String[] items = dupLabels.toArray(new String[0]);

        new MaterialAlertDialogBuilder(this)
            .setTitle("🗑 Remove Duplicate Blocks")
            .setMessage("Found " + dupIndices.size() + " duplicate(s). Select which to delete:")
            .setMultiChoiceItems(items, checked, (dialog, which, isChecked) ->
                checked[which] = isChecked)
            .setPositiveButton("Delete Selected", (dialog, which) -> {
                java.util.List<Integer> toDelete = new ArrayList<>();
                for (int i = 0; i < checked.length; i++) {
                    if (checked[i]) toDelete.add(dupIndices.get(i));
                }
                if (toDelete.isEmpty()) { SketchwareUtil.toast("Nothing selected"); return; }
                Collections.sort(toDelete, Collections.reverseOrder());
                for (int idx : toDelete) all_blocks_list.remove(idx);
                FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
                FileUtil.writeFile(pallet_path, getGson().toJson(pallet_list));
                _refreshLists();
                SketchwareUtil.toast(toDelete.size() + " duplicate(s) removed");
            })
            .setNegativeButton("Keep All", null)
            .show();
    }

    private void launchGroqOptimizer() {
        android.content.SharedPreferences prefs =
            getSharedPreferences("ia_settings", android.content.Context.MODE_PRIVATE);
        String apiKey = prefs.getString("groq_api_key", "");
        boolean enabled = prefs.getBoolean("groq_enabled", false);
        if (apiKey.isEmpty() || !enabled) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Groq API Key Not Configured")
                .setMessage("Enable Groq and add your API key in:\nSettings → App Settings → API Keys (Groq / Morph)")
                .setPositiveButton("Open AI Settings", (d, w) ->
                    startActivity(new Intent(this, pro.sketchware.ai.activities.AiSettingsActivity.class)))
                .setNegativeButton("Cancel", null).show();
            return;
        }
        if (filtered_list.isEmpty()) { SketchwareUtil.toast("No blocks to analyze"); return; }

        String palName = (palette >= 9 && palette - 9 < pallet_list.size())
            ? String.valueOf(pallet_list.get(palette - 9).get("name")) : "Unknown";

        // Build block summary — respect token limit
        StringBuilder sb = new StringBuilder();
        int blockCount = 0;
        final int MAX_BLOCKS = 80;
        for (HashMap<String, Object> b : filtered_list) {
            if (blockCount >= MAX_BLOCKS) { sb.append("... (truncated)\n"); break; }
            sb.append("• [").append(b.get("type")).append("] ")
              .append(b.get("name")).append(" — spec: ").append(b.get("spec"))
              .append(" | opCode: ").append(b.get("opCode")).append("\n");
            blockCount++;
        }
        if (filtered_list.size() > MAX_BLOCKS) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Large Palette")
                .setMessage("This palette has " + filtered_list.size() + " blocks.\n"
                    + "AI will analyze the first " + MAX_BLOCKS + " blocks.")
                .setPositiveButton("Continue", (d, w) -> runGroqAnalysis(palName, sb.toString(), filtered_list))
                .setNegativeButton("Cancel", null).show();
            return;
        }
        runGroqAnalysis(palName, sb.toString(), filtered_list);
    }

    @SuppressWarnings("unchecked")
    private void runGroqAnalysis(String palName, String blocksSummary,
                                 List<HashMap<String, Object>> currentBlocks) {
        String prompt = "You are a Sketchware custom block assistant.\n"
            + "Analyze this palette \"" + palName + "\" and return a JSON array of suggestions.\n"
            + "Each suggestion must have:\n"
            + "  action: \"delete\" | \"edit\" | \"create\"\n"
            + "  name: block name (for delete/edit = existing name; for create = new name)\n"
            + "  reason: short explanation\n"
            + "  new_spec: (for edit/create) new spec text\n"
            + "  new_opCode: (for edit/create) new Java opCode\n"
            + "  new_type: (for edit/create) block type: blank/boolean/d/s\n\n"
            + "Palette blocks:\n" + blocksSummary + "\n\n"
            + "Return ONLY the JSON array. No markdown. No explanation outside JSON.";

        AlertDialog loading = new MaterialAlertDialogBuilder(this)
            .setTitle("✨ AI analyzing…").setMessage("Please wait…").setCancelable(false).create();
        loading.show();

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        exec.execute(() -> {
            String result;
            try {
                result = pro.sketchware.ai.api.GroqApiClientHelper.getInstance(this).sendMessage(prompt);
            } catch (Exception e) {
                result = null;
                handler.post(() -> { loading.dismiss(); SketchwareUtil.toastError("AI error: " + e.getMessage()); });
                return;
            }
            final String json = result;
            handler.post(() -> {
                loading.dismiss();
                if (json == null || json.isEmpty()) { SketchwareUtil.toastError("No AI response"); return; }
                showAiSuggestionsDialog(palName, json, currentBlocks);
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void showAiSuggestionsDialog(String palName, String jsonRaw,
                                         List<HashMap<String, Object>> currentBlocks) {
        ArrayList<HashMap<String, Object>> suggestions = new ArrayList<>();
        try {
            String json = jsonRaw.replaceAll("(?s)```json\\s*|```", "").trim();
            int start = json.indexOf('[');
            if (start >= 0) json = json.substring(start);
            java.lang.reflect.Type t = new com.google.gson.reflect.TypeToken<
                ArrayList<HashMap<String, Object>>>(){}.getType();
            suggestions = new com.google.gson.Gson().fromJson(json, t);
        } catch (Exception e) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("✨ AI Analysis — " + palName)
                .setMessage("Could not parse AI response:\n" + jsonRaw.substring(0, Math.min(500, jsonRaw.length())))
                .setPositiveButton("Close", null).show();
            return;
        }
        if (suggestions == null || suggestions.isEmpty()) {
            SketchwareUtil.toast("AI has no suggestions for this palette");
            return;
        }

        final ArrayList<HashMap<String, Object>> finalSugg = suggestions;
        final boolean[] checked = new boolean[finalSugg.size()];
        Arrays.fill(checked, true);

        float dp = getResources().getDisplayMetrics().density;
        int dp8 = (int)(8 * dp);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.CheckBox selectAll = new android.widget.CheckBox(this);
        selectAll.setText("  Select All / Deselect All");
        selectAll.setChecked(true);
        selectAll.setPadding(dp8, dp8, dp8, dp8/2);
        root.addView(selectAll);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout listC = new android.widget.LinearLayout(this);
        listC.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.CheckBox[] cbList = new android.widget.CheckBox[finalSugg.size()];
        for (int i = 0; i < finalSugg.size(); i++) {
            final int fi = i;
            HashMap<String, Object> s = finalSugg.get(i);
            String action = String.valueOf(s.getOrDefault("action","?"));
            String name   = String.valueOf(s.getOrDefault("name","?"));
            String reason = String.valueOf(s.getOrDefault("reason",""));
            String emoji  = action.equals("delete") ? "🗑" : action.equals("edit") ? "✏️" : "➕";

            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(emoji + " [" + action.toUpperCase() + "] " + name + "\n   " + reason);
            cb.setChecked(true);
            cb.setPadding(dp8*2, dp8/2, dp8, dp8/2);
            cb.setOnCheckedChangeListener((v, c) -> {
                checked[fi] = c;
                boolean allOn = true;
                for (boolean val : checked) if (!val) { allOn = false; break; }
                selectAll.setOnCheckedChangeListener(null);
                selectAll.setChecked(allOn);
                selectAll.setOnCheckedChangeListener((btn, chk) -> {
                    Arrays.fill(checked, chk);
                    for (android.widget.CheckBox box : cbList) if (box != null) box.setChecked(chk);
                });
            });
            cbList[i] = cb;
            listC.addView(cb);
        }
        selectAll.setOnCheckedChangeListener((btn, chk) -> {
            Arrays.fill(checked, chk);
            for (android.widget.CheckBox box : cbList) if (box != null) box.setChecked(chk);
        });

        scroll.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (int)(360*dp)));
        scroll.addView(listC);
        root.addView(scroll);

        new MaterialAlertDialogBuilder(this)
            .setTitle("✨ AI — " + finalSugg.size() + " Suggestion(s) for " + palName)
            .setView(root)
            .setPositiveButton("💾 Apply Selected", (d, w) -> {
                int applied = 0;
                for (int i = 0; i < finalSugg.size(); i++) {
                    if (!checked[i]) continue;
                    HashMap<String, Object> s = finalSugg.get(i);
                    String action = String.valueOf(s.getOrDefault("action", ""));
                    String name   = String.valueOf(s.getOrDefault("name", ""));
                    switch (action) {
                        case "delete":
                            for (int j = all_blocks_list.size()-1; j >= 0; j--) {
                                if (name.equals(all_blocks_list.get(j).get("name"))
                                    && String.valueOf(palette).equals(
                                        String.valueOf(all_blocks_list.get(j).get("palette")))) {
                                    all_blocks_list.remove(j); applied++; break;
                                }
                            }
                            break;
                        case "edit":
                            for (HashMap<String, Object> b : all_blocks_list) {
                                if (name.equals(b.get("name"))
                                    && String.valueOf(palette).equals(String.valueOf(b.get("palette")))) {
                                    if (s.containsKey("new_spec"))   b.put("spec",   s.get("new_spec"));
                                    if (s.containsKey("new_opCode")) b.put("opCode", s.get("new_opCode"));
                                    if (s.containsKey("new_type"))   b.put("type",   s.get("new_type"));
                                    applied++; break;
                                }
                            }
                            break;
                        case "create":
                            HashMap<String, Object> nb = new HashMap<>();
                            nb.put("name",     name);
                            nb.put("spec",     s.getOrDefault("new_spec",   name));
                            nb.put("opCode",   s.getOrDefault("new_opCode", ""));
                            nb.put("type",     s.getOrDefault("new_type",   "blank"));
                            nb.put("typeName", "");
                            nb.put("palette",  String.valueOf(palette));
                            nb.put("id",       String.valueOf(System.currentTimeMillis()));
                            nb.put("nextBlock", -1.0);
                            nb.put("subStack1", -1.0);
                            nb.put("subStack2", -1.0);
                            nb.put("color",    "#607D8B");
                            nb.put("parameters", new ArrayList<>());
                            all_blocks_list.add(nb); applied++;
                            break;
                    }
                }
                if (applied > 0) {
                    _saveBothFiles();
                    _refreshLists();
                    SketchwareUtil.toast("✅ Applied " + applied + " change(s)");
                }
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void _receive_intents() {
        palette = Integer.parseInt(getIntent().getStringExtra("position"));
        pallet_path = getIntent().getStringExtra("dirP");
        blocks_path = getIntent().getStringExtra("dirB");
        _refreshLists();
        if (palette == -1) {
            getSupportActionBar().setTitle("Recycle Bin");
            fab_button.setVisibility(View.GONE);
        } else {
            Object paletteName = pallet_list.get(palette - 9).get("name");

            if (paletteName instanceof String) {
                getSupportActionBar().setTitle("Manage Block");
                getSupportActionBar().setSubtitle((String) paletteName);
            }
        }
    }

    private void _refreshLists() {
        filtered_list.clear();
        reference_list.clear();
        String paletteFileContent = FileUtil.readFile(pallet_path);
        String blocksFileContent = FileUtil.readFile(blocks_path);
        if (paletteFileContent.isEmpty()) {
            FileUtil.writeFile(pallet_path, "[]");
            paletteFileContent = "[]";
        }
        if (blocksFileContent.isEmpty()) {
            FileUtil.writeFile(blocks_path, "[]");
            blocksFileContent = "[]";
        }

        parseLists:
        {
            try {
                pallet_list = getGson().fromJson(paletteFileContent, Helper.TYPE_MAP_LIST);

                if (pallet_list != null) {
                    break parseLists;
                }
                // fall-through to shared error handling
            } catch (JsonParseException e) {
                // fall-through to shared error handling
            }

            SketchwareUtil.showFailedToParseJsonDialog(this, new File(pallet_path), "Custom Block Palettes", v -> _refreshLists());
            pallet_list = new ArrayList<>();
        }

        parseBlocks:
        {
            try {
                all_blocks_list = getGson().fromJson(blocksFileContent, Helper.TYPE_MAP_LIST);

                if (all_blocks_list != null) {
                    break parseBlocks;
                }
                // fall-through to shared error handling
            } catch (JsonParseException e) {
                // fall-through to shared error handling
            }

            SketchwareUtil.showFailedToParseJsonDialog(this, new File(blocks_path), "Custom Blocks", v -> _refreshLists());
            all_blocks_list = new ArrayList<>();
        }

        for (int i = 0; i < all_blocks_list.size(); i++) {
            HashMap<String, Object> block = all_blocks_list.get(i);

            Object blockPalette = block.get("palette");
            if (blockPalette instanceof String) {
                try {
                    if (Integer.parseInt((String) blockPalette) == palette) {
                        reference_list.add(i);
                        filtered_list.add(block);
                    }
                } catch (NumberFormatException e) {
                    SketchwareUtil.toastError("Invalid palette entry in block #" + (i + 1));
                }
            }
        }
        Parcelable onSaveInstanceState = block_list.onSaveInstanceState();
        block_list.setAdapter(new Adapter(filtered_list));
        ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
        block_list.onRestoreInstanceState(onSaveInstanceState);
    }

    // ── Helper: persist BOTH files atomically ────────────────────────────
    private void _saveBothFiles() {
        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
        FileUtil.writeFile(pallet_path, getGson().toJson(pallet_list));
    }

    private void _swapitems(int sourcePosition, int targetPosition) {
        Collections.swap(all_blocks_list, sourcePosition, targetPosition);
        _saveBothFiles();
        _refreshLists();
    }

    private void _showItemPopup(View view, int position) {
        if (palette == -1) {
            PopupMenu popupMenu = new PopupMenu(this, view);
            Menu menu = popupMenu.getMenu();
            menu.add("Delete permanently");
            menu.add("Restore");
            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getTitle().toString()) {
                    case "Delete permanently":
                        _deleteBlock(position);
                        break;

                    case "Restore":
                        _changePallette(position);
                        break;

                    default:
                        return false;
                }
                return true;
            });
            popupMenu.show();
            return;
        }
        PopupMenu popupMenu = new PopupMenu(this, view);
        Menu menu = popupMenu.getMenu();
        menu.add("Insert above");
        menu.add("Delete");
        menu.add("Duplicate");
        menu.add("Move to palette");
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "Duplicate":
                    _duplicateBlock(position);
                    break;

                case "Insert above":
                    Object paletteColor = pallet_list.get(palette - 9).get("color");
                    if (paletteColor instanceof String) {
                        Intent intent = new Intent(getApplicationContext(), BlocksManagerCreatorActivity.class);
                        intent.putExtra("mode", "insert");
                        intent.putExtra("path", blocks_path);
                        intent.putExtra("color", (String) paletteColor);
                        intent.putExtra("pos", String.valueOf(position));
                        startActivity(intent);
                    } else {
                        SketchwareUtil.toastError("Invalid color of palette #" + (palette - 9));
                    }
                    break;

                case "Move to palette":
                    _changePallette(position);
                    break;

                case "Delete":
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Delete block?")
                            .setMessage("Are you sure you want to delete this block?")
                            .setPositiveButton("Recycle bin", (dialog, which) -> _moveToRecycleBin(position))
                            .setNegativeButton(R.string.common_word_cancel, null)
                            .setNeutralButton("Delete permanently", (dialog, which) -> _deleteBlock(position))
                            .show();
                    break;

                default:
                    return false;
            }
            return true;
        });
        popupMenu.show();
    }

    private void _duplicateBlock(int position) {
        HashMap<String, Object> block = new HashMap<>(all_blocks_list.get(position));
        Object blockName = block.get("name");

        if (blockName instanceof String) {
            if (((String) blockName).matches("(?s).*_copy[0-9][0-9]")) {
                block.put("name", ((String) blockName).replaceAll("_copy[0-9][0-9]", "_copy" + SketchwareUtil.getRandom(11, 99)));
            } else {
                block.put("name", blockName + "_copy" + SketchwareUtil.getRandom(11, 99));
            }
        }
        all_blocks_list.add(position + 1, block);
        _saveBothFiles(); // both files atomically
        _refreshLists();
    }

    private void _deleteBlock(int position) {
        all_blocks_list.remove(position);
        _saveBothFiles(); // both files atomically
        _refreshLists();
    }

    private void _moveToRecycleBin(int position) {
        all_blocks_list.get(position).put("palette", "-1");
        _saveBothFiles(); // both files atomically
        _refreshLists();
    }

    private void _changePallette(int position) {
        ArrayList<String> paletteNames = new ArrayList<>();
        for (int j = 0, pallet_listSize = pallet_list.size(); j < pallet_listSize; j++) {
            HashMap<String, Object> palette = pallet_list.get(j);
            Object name = palette.get("name");

            if (name instanceof String) {
                paletteNames.add((String) name);
            } else {
                SketchwareUtil.toastError("Invalid name of Custom Block palette #" + (j + 1));
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setNegativeButton(R.string.common_word_cancel, null);
        if (palette == -1) {
            AtomicInteger restoreToChoice = new AtomicInteger(-1);
            builder.setTitle("Restore to")
                    .setSingleChoiceItems(paletteNames.toArray(new String[0]), -1, (dialog, which) -> restoreToChoice.set(which))
                    .setPositiveButton("Restore", (dialog, which) -> {
                        if (restoreToChoice.get() != -1) {
                            all_blocks_list.get(position).put("palette", String.valueOf(restoreToChoice.get() + 9));
                            Collections.swap(all_blocks_list, position, all_blocks_list.size() - 1);
                            _saveBothFiles(); // both files atomically
                            _refreshLists();
                        }
                    });
        } else {
            AtomicInteger moveToChoice = new AtomicInteger(palette - 9);
            builder.setTitle("Move to")
                    .setSingleChoiceItems(paletteNames.toArray(new String[0]), palette - 9, (dialog, which) -> moveToChoice.set(which))
                    .setPositiveButton("Move", (dialog, which) -> {
                        all_blocks_list.get(position).put("palette", String.valueOf(moveToChoice.get() + 9));
                        Collections.swap(all_blocks_list, position, all_blocks_list.size() - 1);
                        _saveBothFiles(); // both files atomically
                        _refreshLists();
                    });
        }
        builder.show();
    }

    private void _importBlocks(ArrayList<HashMap<String, Object>> blocks) {
        try {
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Integer> toAdd = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                Object blockName = blocks.get(i).get("name");

                if (blockName instanceof String) {
                    names.add((String) blockName);
                } else {
                    SketchwareUtil.toastError("Invalid name entry of Custom Block #" + (i + 1) + " in Blocks to import");
                }
            }
            MaterialAlertDialogBuilder import_dialog = new MaterialAlertDialogBuilder(this);
            import_dialog.setTitle("Import blocks")
                    .setMultiChoiceItems(names.toArray(new CharSequence[0]), null, (dialog, which, isChecked) -> {
                        if (isChecked) {
                            toAdd.add(which);
                        } else {
                            toAdd.remove((Integer) which);
                        }
                    })
                    .setPositiveButton("Import", (dialog, which) -> {
                        for (int i = 0; i < blocks.size(); i++) {
                            if (toAdd.contains(i)) {
                                HashMap<String, Object> map = blocks.get(i);
                                map.put("palette", String.valueOf(palette));
                                all_blocks_list.add(map);
                            }
                        }
                        _saveBothFiles(); // both files atomically
                        _refreshLists();
                        SketchwareUtil.toast("Imported successfully");
                    })
                    .setNegativeButton("Reverse", (dialog, which) -> {
                        for (int i = 0; i < blocks.size(); i++) {
                            if (!toAdd.contains(i)) {
                                HashMap<String, Object> map = blocks.get(i);
                                map.put("palette", String.valueOf(palette));
                                all_blocks_list.add(map);
                            }
                        }
                        _saveBothFiles(); // both files atomically
                        _refreshLists();
                        SketchwareUtil.toast("Imported successfully");
                    })
                    .setNeutralButton("All", (dialog, which) -> {
                        for (int i = 0; i < blocks.size(); i++) {
                            HashMap<String, Object> map = blocks.get(i);
                            map.put("palette", String.valueOf(palette));
                            all_blocks_list.add(map);
                        }
                        _saveBothFiles(); // both files atomically
                        _refreshLists();
                        SketchwareUtil.toast("Imported successfully");
                    })
                    .show();
        } catch (Exception e) {
            SketchwareUtil.toastError("An error occurred! [" + e.getMessage() + "]");
        }
    }

    private void fabButtonVisibility(boolean visible) {
        if (visible) {
            ObjectAnimator.ofFloat(fab_button, "translationX", fab_button.getTranslationX(), -50.0f, 0.0f).setDuration(400L).start();
        } else {
            ObjectAnimator.ofFloat(fab_button, "translationX", fab_button.getTranslationX(), -50.0f, 250.0f).setDuration(400L).start();
        }
    }

    private class Adapter extends BaseAdapter {

        private final ArrayList<HashMap<String, Object>> blocks;

        public Adapter(ArrayList<HashMap<String, Object>> data) {
            blocks = data;
        }

        @Override
        public int getCount() {
            return blocks.size();
        }

        @Override
        public HashMap<String, Object> getItem(int position) {
            return blocks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.block_customview, parent, false);
            }

            HashMap<String, Object> block = blocks.get(position);

            LinearLayout background = convertView.findViewById(R.id.background);
            TextView name = convertView.findViewById(R.id.name);
            TextView spec = convertView.findViewById(R.id.spec);
            CardView upLayout = convertView.findViewById(R.id.up_layout);
            CardView downLayout = convertView.findViewById(R.id.down_layout);
            LinearLayout down = convertView.findViewById(R.id.down);
            LinearLayout up = convertView.findViewById(R.id.up);

            if (mode.equals("normal")) {
                downLayout.setVisibility(View.GONE);
                upLayout.setVisibility(View.GONE);
            } else {
                downLayout.setVisibility(position != blocks.size() - 1 ? View.VISIBLE : View.GONE);
                upLayout.setVisibility(position != 0 ? View.VISIBLE : View.GONE);
            }

            Object blockName = block.get("name");
            if (blockName instanceof String) {
                name.setText((String) blockName);
                spec.setHint("");
            } else {
                name.setText("");
                name.setHint("(Invalid block name entry)");
            }

            Object blockSpec = block.get("spec");
            if (blockSpec instanceof String) {
                spec.setText((String) blockSpec);
                spec.setHint("");
            } else {
                spec.setText("");
                spec.setHint("(Invalid block spec entry)");
            }

            Object blockType = block.get("type");
            if (blockType instanceof String) {
                switch ((String) blockType) {
                    case " ":
                    case "regular":
                        spec.setBackgroundResource(R.drawable.block_ori);
                        break;

                    case "b":
                        spec.setBackgroundResource(R.drawable.block_boolean);
                        break;

                    case "c":
                    case "e":
                        spec.setBackgroundResource(R.drawable.if_else);
                        break;

                    case "d":
                        spec.setBackgroundResource(R.drawable.block_num);
                        break;

                    case "f":
                        spec.setBackgroundResource(R.drawable.block_stop);
                        break;

                    default:
                        spec.setBackgroundResource(R.drawable.block_string);
                        break;
                }
            } else {
                spec.setBackgroundResource(R.drawable.block_string);
            }

            if (palette == -1) {
                spec.getBackground().setColorFilter(new PorterDuffColorFilter(0xff9e9e9e, PorterDuff.Mode.MULTIPLY));
            } else {
                if (block.containsKey("color")) {
                    Object blockColor = block.get("color");

                    if (blockColor instanceof String) {
                        int color = -1;
                        try {
                            color = Color.parseColor((String) blockColor);
                        } catch (IllegalArgumentException e) {
                            SketchwareUtil.toastError("Invalid color entry in block #" + (position + 1));
                        }

                        if (color != -1) {
                            spec.getBackground().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                        }
                    } else {
                        SketchwareUtil.toastError("Invalid color entry in block #" + (position + 1));
                    }
                } else {
                    HashMap<String, Object> paletteObject = pallet_list.get(palette - 9);
                    Object paletteColor = paletteObject.get("color");

                    if (paletteColor instanceof String) {
                        try {
                            spec.getBackground().setColorFilter(new PorterDuffColorFilter(
                                    Color.parseColor((String) paletteColor),
                                    PorterDuff.Mode.MULTIPLY
                            ));
                        } catch (IllegalArgumentException e) {
                            SketchwareUtil.toastError("Invalid color in Custom Block palette #" + (palette - 8));
                        }
                    }
                }
            }
            up.setOnClickListener(v -> {
                if (position > 0) {
                    _swapitems(reference_list.get(position), reference_list.get(position - 1));
                }
            });
            down.setOnClickListener(v -> {
                if (position < filtered_list.size() - 1) {
                    _swapitems(reference_list.get(position), reference_list.get(position + 1));
                }
            });
            if (mode.equals("normal")) {
                background.setOnClickListener(v -> {
                    if (palette == -1) {
                        _showItemPopup(background, reference_list.get(position));
                    } else {
                        Object paletteColor = pallet_list.get(palette - 9).get("color");

                        if (paletteColor instanceof String) {
                            Intent intent = new Intent(getApplicationContext(), BlocksManagerCreatorActivity.class);
                            intent.putExtra("mode", "edit");
                            intent.putExtra("color", (String) paletteColor);
                            intent.putExtra("path", blocks_path);
                            intent.putExtra("pos", String.valueOf(reference_list.get(position)));
                            startActivity(intent);
                        }
                    }
                });
                background.setOnLongClickListener(v -> {
                    _showItemPopup(background, reference_list.get(position));
                    return true;
                });
            }
            return convertView;
        }
    }
}
