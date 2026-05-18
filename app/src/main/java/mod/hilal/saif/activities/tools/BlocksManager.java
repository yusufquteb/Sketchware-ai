package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityBlocksManagerBinding;
import pro.sketchware.databinding.DialogBlockConfigurationBinding;
import pro.sketchware.databinding.DialogPaletteBinding;
import pro.sketchware.databinding.PalletCustomviewBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BlocksManager extends BaseAppCompatActivity {

    boolean isDialogShowing;
    View draggedView;
    private ArrayList<HashMap<String, Object>> all_blocks_list = new ArrayList<>();
    private String blocks_dir;
    private String pallet_dir;
    private int oldPos;
    private int newPos;
    private Activity activity;
    private ArrayList<HashMap<String, Object>> pallet_listmap = new ArrayList<>();
    private ItemTouchHelper itemTouchHelper;
    private ActivityBlocksManagerBinding binding;
    private DialogPaletteBinding dialogBinding;
    private Vibrator vibrator;

    // ── Undo / Redo stacks ──────────────────────────────────────────────
    private static final int MAX_HISTORY = 20;
    private final java.util.ArrayDeque<String> undoStack = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> redoStack = new java.util.ArrayDeque<>();

    /** Call BEFORE any destructive operation to snapshot current state */
    private void pushUndo() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String snap = gson.toJson(all_blocks_list) + "|||" + gson.toJson(pallet_listmap);
        undoStack.push(snap);
        if (undoStack.size() > MAX_HISTORY) {
            // Remove oldest entry
            String[] arr = undoStack.toArray(new String[0]);
            undoStack.clear();
            for (int i = 0; i < arr.length - 1; i++) undoStack.addLast(arr[i]);
        }
        redoStack.clear();
        invalidateOptionsMenu();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) { SketchwareUtil.toast("Nothing to undo"); return; }
        // Save current to redo
        com.google.gson.Gson gson = new com.google.gson.Gson();
        redoStack.push(gson.toJson(all_blocks_list) + "|||" + gson.toJson(pallet_listmap));
        restoreSnapshot(undoStack.pop());
        invalidateOptionsMenu();
    }

    private void performRedo() {
        if (redoStack.isEmpty()) { SketchwareUtil.toast("Nothing to redo"); return; }
        com.google.gson.Gson gson = new com.google.gson.Gson();
        undoStack.push(gson.toJson(all_blocks_list) + "|||" + gson.toJson(pallet_listmap));
        restoreSnapshot(redoStack.pop());
        invalidateOptionsMenu();
    }

    @SuppressWarnings("unchecked")
    private void restoreSnapshot(String snapshot) {
        String[] parts = snapshot.split("\\|\\|\\|", 2);
        com.google.gson.Gson gson = new com.google.gson.Gson();
        java.lang.reflect.Type blocksType = new com.google.gson.reflect.TypeToken<
            ArrayList<HashMap<String, Object>>>(){}.getType();
        java.lang.reflect.Type palletsType = new com.google.gson.reflect.TypeToken<
            ArrayList<HashMap<String, Object>>>(){}.getType();
        all_blocks_list = gson.fromJson(parts[0], blocksType);
        pallet_listmap = gson.fromJson(parts[1], palletsType);
        saveBothFiles();
        readSettings();
        refreshList();
        SketchwareUtil.toast("Restored ✓");
    }

    @Override
    public void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        binding = ActivityBlocksManagerBinding.inflate(getLayoutInflater());
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.background, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        initialize();
    }

    @Override
    public void onStop() {
        super.onStop();
        BlockLoader.refresh();
    }

    private void initialize() {
        activity = this;

        setSupportActionBar(binding.toolbar);

        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        binding.toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        binding.paletteRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.paletteRecycler.setAdapter(new PaletteAdapter(pallet_listmap));
        binding.fab.setOnClickListener(v -> showPaletteDialog(false, null, null, "#ffffff", null));

        readSettings();
        refreshList();
        recycleBin(binding.recycleBinCard);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to   = target.getBindingAdapterPosition();
                // Only snapshot when drag starts (first move event)
                if (oldPos == newPos || (oldPos == 0 && newPos == 0)) pushUndo();
                oldPos = from;
                newPos = to;

                Collections.swap(pallet_listmap, oldPos, newPos);

                Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyItemMoved(oldPos, newPos);
                swapRelatedBlocks(oldPos + 9, newPos + 9);

                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int action) {
                if (action == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.setAlpha(0.7f);
                    draggedView = viewHolder.itemView;
                }
                super.onSelectedChanged(viewHolder, action);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                viewHolder.itemView.setAlpha(1f);
                saveBothFiles(); // both files atomically
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    binding.background.setClipChildren(!isItNearTrash(draggedView, binding.recycleBin));
                    if (isItInTrash(draggedView, binding.recycleBin)) {
                        int pos = viewHolder.getBindingAdapterPosition();
                        binding.recycleBinCard.setAlpha(0.5f);
                        if (!isCurrentlyActive && pos != RecyclerView.NO_POSITION && pos < pallet_listmap.size() && !isDialogShowing) {
                            vibrator.vibrate(40L);
                            showMoveToBinDialog(pos);
                            isDialogShowing = true;
                        }
                        return;
                    }
                }
                binding.recycleBinCard.setAlpha(1f);
                isDialogShowing = false;
            }

        });

        itemTouchHelper.attachToRecyclerView(binding.paletteRecycler);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, 0, "Settings")
            .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_settings))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        // ── AI icon — visible before overflow ──
        menu.add(Menu.NONE, 7, 1, "AI Assistant")
            .setIcon(AppCompatResources.getDrawable(this, pro.sketchware.R.drawable.ic_ai_robot))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        // ── Undo / Redo ──
        MenuItem undoItem = menu.add(Menu.NONE, 10, 2, "↩ Undo");
        undoItem.setEnabled(!undoStack.isEmpty());
        MenuItem redoItem = menu.add(Menu.NONE, 11, 3, "↪ Redo");
        redoItem.setEnabled(!redoStack.isEmpty());
        // ── Overflow — no AI (moved to BottomSheet) ──
        menu.add(Menu.NONE, 2, 4, "↑ Sort Palettes A→Z");
        menu.add(Menu.NONE, 3, 5, "↓ Sort Palettes Z→A");
        menu.add(Menu.NONE, 4, 6, "🗑 Remove Duplicate Blocks");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case 1:  showBlockConfigurationDialog(); break;
            case 7:  openBlockManagerAiAssistant(); break;
            case 10: performUndo(); break;
            case 11: performRedo(); break;
            case 2:  pushUndo(); sortPalettes(true);  break;
            case 3:  pushUndo(); sortPalettes(false); break;
            case 4:  removeDuplicateBlocksGlobal(); break;
            default: return false;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    // ── Sort all palettes (and remap their blocks) A↑/Z↓ ────────────────
    private void sortPalettes(boolean ascending) {
        if (pallet_listmap == null || pallet_listmap.isEmpty()) return;

        // Build old→new index map
        ArrayList<Integer> oldOrder = new ArrayList<>();
        for (int i = 0; i < pallet_listmap.size(); i++) oldOrder.add(i);

        ArrayList<HashMap<String, Object>> sorted = new ArrayList<>(pallet_listmap);
        sorted.sort((a, b) -> {
            String na = a.get("name") != null ? a.get("name").toString() : "";
            String nb = b.get("name") != null ? b.get("name").toString() : "";
            return ascending ? na.compareToIgnoreCase(nb) : nb.compareToIgnoreCase(na);
        });

        // Build remapping: old palette index(+9) → new palette index(+9)
        HashMap<Integer, Integer> remap = new HashMap<>();
        for (int newIdx = 0; newIdx < sorted.size(); newIdx++) {
            for (int oldIdx = 0; oldIdx < pallet_listmap.size(); oldIdx++) {
                if (pallet_listmap.get(oldIdx) == sorted.get(newIdx)) {
                    remap.put(oldIdx + 9, newIdx + 9);
                    break;
                }
            }
        }

        // Remap palette field in every block
        if (all_blocks_list != null) {
            for (Map<String, Object> block : all_blocks_list) {
                Object p = block.get("palette");
                if (p instanceof String) {
                    try {
                        int oldP = Integer.parseInt((String) p);
                        if (remap.containsKey(oldP)) {
                            block.put("palette", String.valueOf(remap.get(oldP)));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        pallet_listmap.clear();
        pallet_listmap.addAll(sorted);

        // Persist both files atomically
        saveBothFiles();
        refreshList();
        SketchwareUtil.toast(ascending ? "Palettes sorted A→Z" : "Palettes sorted Z→A");
    }

    // Helper to get palette name for a block
    private String getPalNameForBlock(HashMap<String, Object> b) {
        try {
            int palIdx = Integer.parseInt(String.valueOf(b.get("palette"))) - 9;
            if (palIdx == -10) return "🗑 Recycle Bin";
            if (pallet_listmap != null && palIdx >= 0 && palIdx < pallet_listmap.size()) {
                return String.valueOf(pallet_listmap.get(palIdx).get("name"));
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    private void removeDuplicateBlocksGlobal() {
        if (all_blocks_list == null) return;

        // Build pairs: key → {firstIdx, [dupIdx1, dupIdx2, ...]}
        java.util.LinkedHashMap<String, Integer> firstSeen = new java.util.LinkedHashMap<>();
        // List of pairs: int[0]=original index, int[1]=duplicate index
        List<int[]> pairs = new ArrayList<>();

        for (int i = 0; i < all_blocks_list.size(); i++) {
            HashMap<String, Object> b = all_blocks_list.get(i);
            String key = b.get("name") + "|" + b.get("spec");
            if (firstSeen.containsKey(key)) {
                pairs.add(new int[]{firstSeen.get(key), i});
            } else {
                firstSeen.put(key, i);
            }
        }

        if (pairs.isEmpty()) {
            SketchwareUtil.toast("No duplicate blocks found");
            return;
        }

        float dp = getResources().getDisplayMetrics().density;
        int dp8 = (int)(8 * dp);
        int dp4 = (int)(4 * dp);

        // ── Root layout ───────────────────────────────────────────────
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);

        // ── Header: Select All ────────────────────────────────────────
        android.widget.CheckBox selectAll = new android.widget.CheckBox(this);
        selectAll.setText("  Select All / Deselect All");
        selectAll.setChecked(false);
        selectAll.setPadding(dp8, dp8, dp8, dp8);
        root.addView(selectAll);

        // ── Column headers (MT Manager style) ─────────────────────────
        android.widget.LinearLayout header = new android.widget.LinearLayout(this);
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        header.setBackgroundColor(0x44888888);
        header.setPadding(dp8, dp4, dp8, dp4);
        android.widget.TextView hOriginal = new android.widget.TextView(this);
        hOriginal.setText("📌 Original");
        hOriginal.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.TextView hDuplicate = new android.widget.TextView(this);
        hDuplicate.setText("🔁 Duplicate");
        hDuplicate.setTypeface(null, android.graphics.Typeface.BOLD);
        hOriginal.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hDuplicate.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(hOriginal);
        header.addView(hDuplicate);
        root.addView(header);

        // ── Scrollable pairs list ─────────────────────────────────────
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout listContainer = new android.widget.LinearLayout(this);
        listContainer.setOrientation(android.widget.LinearLayout.VERTICAL);

        // Each pair: 2 checkboxes side-by-side (user picks which copy to DELETE)
        // checked[i][0]=delete original, checked[i][1]=delete duplicate
        boolean[][] checked = new boolean[pairs.size()][2];
        // Default: mark duplicate (index 1) for deletion
        for (boolean[] row : checked) row[1] = true;

        android.widget.CheckBox[][] cbPairs = new android.widget.CheckBox[pairs.size()][2];

        for (int i = 0; i < pairs.size(); i++) {
            final int fi = i;
            int origIdx = pairs.get(i)[0];
            int dupIdx  = pairs.get(i)[1];
            HashMap<String, Object> orig = all_blocks_list.get(origIdx);
            HashMap<String, Object> dup  = all_blocks_list.get(dupIdx);

            // Row background alternating
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(dp4, dp4, dp4, dp4);
            if (i % 2 == 0) row.setBackgroundColor(0x11FFFFFF);

            // Original side
            android.widget.CheckBox cbOrig = new android.widget.CheckBox(this);
            cbOrig.setChecked(false);
            cbOrig.setText("#" + (origIdx+1) + "  " + orig.get("name")
                + "\n📦 " + getPalNameForBlock(orig));
            cbOrig.setPadding(dp4, dp4, dp4, dp4);
            cbOrig.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            cbOrig.setOnCheckedChangeListener((v, c) -> checked[fi][0] = c);

            // Vertical divider
            android.view.View vDiv = new android.view.View(this);
            vDiv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(1,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT));
            vDiv.setBackgroundColor(0x44888888);

            // Duplicate side
            android.widget.CheckBox cbDup = new android.widget.CheckBox(this);
            cbDup.setChecked(true);
            cbDup.setText("#" + (dupIdx+1) + "  " + dup.get("name")
                + "\n📦 " + getPalNameForBlock(dup));
            cbDup.setPadding(dp4, dp4, dp4, dp4);
            cbDup.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            cbDup.setOnCheckedChangeListener((v, c) -> checked[fi][1] = c);

            cbPairs[i][0] = cbOrig;
            cbPairs[i][1] = cbDup;
            row.addView(cbOrig);
            row.addView(vDiv);
            row.addView(cbDup);
            listContainer.addView(row);

            // Thin separator
            android.view.View sep = new android.view.View(this);
            sep.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
            sep.setBackgroundColor(0x22888888);
            listContainer.addView(sep);
        }

        // Select All logic: selects ALL duplicate (right) column
        selectAll.setOnCheckedChangeListener((btn, chk) -> {
            for (int i = 0; i < pairs.size(); i++) {
                checked[i][1] = chk;
                cbPairs[i][1].setChecked(chk);
            }
        });

        scroll.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            (int)(360 * dp)));
        scroll.addView(listContainer);
        root.addView(scroll);

        new MaterialAlertDialogBuilder(this)
            .setTitle("🗑 " + pairs.size() + " Duplicate Pair(s)")
            .setMessage("Check items to DELETE. Left = original, Right = duplicate.")
            .setView(root)
            .setPositiveButton("💾 Save", (dialog, which) -> {
                // Collect all indices to delete (avoid duplicates in selection)
                java.util.TreeSet<Integer> toDelete = new java.util.TreeSet<>(Collections.reverseOrder());
                for (int i = 0; i < pairs.size(); i++) {
                    if (checked[i][0]) toDelete.add(pairs.get(i)[0]);
                    if (checked[i][1]) toDelete.add(pairs.get(i)[1]);
                }
                if (toDelete.isEmpty()) { SketchwareUtil.toast("Nothing selected"); return; }
                pushUndo();
                for (int idx : toDelete) all_blocks_list.remove(idx);
                saveBothFiles();
                refreshList();
                SketchwareUtil.toast(toDelete.size() + " block(s) removed");
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    // ── Unified AI Assistant (AiAssistantBottomSheet) ────────────────────────
    private void openBlockManagerAiAssistant() {
        java.util.List<pro.sketchware.ai.shared.AiPageConfig.Tool> tools = new java.util.ArrayList<>();

        // ── BLOCK CREATION ──
        tools.add(new pro.sketchware.ai.shared.AiPageConfig.Tool("Create Blocks", pro.sketchware.R.drawable.ic_mtrl_add));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Generate Block Set",
            pro.sketchware.R.drawable.ic_mtrl_add,
            "Create a set of custom blocks for: [describe what you want to build]\n"
            + "Return: block name, spec, return type, Java code for each block."));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Create Single Block",
            pro.sketchware.R.drawable.ic_mtrl_code,
            "Create a custom block for: [describe what it should do]\n"
            + "Return: block name, spec format, return type, Java implementation."));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Create Category",
            pro.sketchware.R.drawable.ic_mtrl_box,
            "Create a complete category of blocks for: [theme or functionality]\n"
            + "Include: 5-8 related blocks with proper specs and implementations."));

        // ── BLOCK MANAGEMENT ──
        tools.add(new pro.sketchware.ai.shared.AiPageConfig.Tool("Management", pro.sketchware.R.drawable.ic_mtrl_settings));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Fix Block Spec",
            pro.sketchware.R.drawable.ic_mtrl_warning,
            "Fix the spec format for this block: [paste current block definition]\n"
            + "Use correct Sketchware spec format: %d=number, %s=string, %b=boolean, %m.type=menu."));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Explain Block",
            pro.sketchware.R.drawable.ic_mtrl_article,
            "Explain what this block does and how to use it: [paste block name or spec]"));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Convert Java to Block",
            pro.sketchware.R.drawable.ic_mtrl_code,
            "Convert this Java code into a Sketchware custom block:\n[paste Java code here]"));
        tools.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Organize Palettes",
            pro.sketchware.R.drawable.ic_mtrl_sort,
            "Suggest how to organize my custom blocks into logical categories.\n"
            + "I currently have: [describe your blocks or paste block names]"));

        pro.sketchware.ai.shared.AiPageConfig config = new pro.sketchware.ai.shared.AiPageConfig.Builder()
            .pageTitle("Block Manager")
            .scopeLabel("Custom Blocks")
            .inputHint("Ask about creating or fixing custom blocks…")
            .systemPrompt(
                "You are a Sketchware custom block expert.\n\n"
                + "SKETCHWARE BLOCK SPEC FORMAT:\n"
                + "  %d = number input slot\n"
                + "  %s = string input slot\n"
                + "  %b = boolean input slot\n"
                + "  %m.xxx = menu selector (e.g. %m.view, %m.string)\n"
                + "  Plain text = label text between slots\n\n"
                + "RETURN TYPES: 'b' (boolean), 'd' (number), 's' (string), ' ' (void)\n\n"
                + "PIPELINE:\n"
                + "1. Understand what the block should DO\n"
                + "2. Design the spec format (inputs + label text)\n"
                + "3. Choose correct return type\n"
                + "4. Write the Java implementation (uses standard Android/Java APIs)\n"
                + "5. VALIDATE: spec must match number of parameters in Java code\n\n"
                + "FORMAT: provide block definition ready to paste into Block Manager. "
                + "Always show: name, spec, return type, java code. "
                + "Reply in user's language.")
            .tools(tools)
            .directActions(null)
            .build();

        pro.sketchware.ai.shared.AiAssistantBottomSheet.newInstance(config)
            .show(getSupportFragmentManager(), "block_manager_ai");
    }

    // ── Groq AI global organizer ─────────────────────────────────────────
    private void launchGroqOrganizerGlobal() {
        // Check API key using the same prefs as IaSettingsActivity
        android.content.SharedPreferences prefs =
            pro.sketchware.ai.storage.AiPreferences.getInstance(this).prefs();
        String apiKey = pro.sketchware.ai.storage.AiPreferences.getInstance(this).getApiKey(pro.sketchware.ai.models.AiProvider.GROQ);
        boolean enabled = !pro.sketchware.ai.storage.AiPreferences.getInstance(this).getApiKey(pro.sketchware.ai.models.AiProvider.GROQ).isEmpty();

        if (apiKey.isEmpty() || !enabled) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Groq API Key Not Configured")
                .setMessage("To use AI block analysis, enable Groq and add your API key in:\n\nSettings → App Settings → API Keys (Groq / Morph)")
                .setPositiveButton("Open AI Settings", (d, w) ->
                    startActivity(new Intent(this, pro.sketchware.ai.activities.AiSettingsActivity.class)))
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        if (pallet_listmap == null || pallet_listmap.isEmpty()) {
            SketchwareUtil.toast("No palettes to analyze");
            return;
        }

        final int MAX_BLOCKS_PER_CHUNK = 150; // ~15 tokens/block → ~2250 tokens safe for free tier
        final int totalPalettes = pallet_listmap.size();

        // Count total blocks across all palettes to determine chunking strategy
        // Distribute palettes into chunks where each chunk ≤ MAX_BLOCKS_PER_CHUNK blocks
        List<int[]> chunks = new ArrayList<>(); // each int[] = {fromIdx, toIdx}
        int chunkStart = 0;
        int blockCount = 0;
        for (int i = 0; i < totalPalettes; i++) {
            int palBlocks = 0;
            if (all_blocks_list != null) {
                final int palIdx = i + 9;
                for (Map<String, Object> b : all_blocks_list) {
                    Object p = b.get("palette");
                    if (p instanceof String) {
                        try { if (Integer.parseInt((String) p) == palIdx) palBlocks++; }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }
            blockCount += palBlocks;
            if (blockCount >= MAX_BLOCKS_PER_CHUNK || i == totalPalettes - 1) {
                chunks.add(new int[]{chunkStart, i + 1});
                chunkStart = i + 1;
                blockCount = 0;
            }
        }

        if (chunks.size() > 1) {
            String[] chunkOptions = new String[chunks.size()];
            for (int c = 0; c < chunks.size(); c++) {
                int from = chunks.get(c)[0];
                int to   = chunks.get(c)[1];
                // Count blocks in this chunk
                int cb = 0;
                if (all_blocks_list != null) {
                    for (int i = from; i < to; i++) {
                        final int palIdx = i + 9;
                        for (Map<String, Object> b : all_blocks_list) {
                            Object p = b.get("palette");
                            if (p instanceof String) {
                                try { if (Integer.parseInt((String) p) == palIdx) cb++; }
                                catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
                chunkOptions[c] = "Palettes " + (from + 1) + "–" + to + "  (" + cb + " blocks)";
            }
            final int[] selectedChunk = {0};
            new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Too Many Blocks")
                .setMessage("Your " + totalPalettes + " palettes contain too many blocks for a single AI request.\n\n"
                    + "Select a range to analyze (≤" + MAX_BLOCKS_PER_CHUNK + " blocks per request):")
                .setSingleChoiceItems(chunkOptions, 0, (d, which) -> selectedChunk[0] = which)
                .setPositiveButton("Analyze This Range", (d, w) -> {
                    int[] c = chunks.get(selectedChunk[0]);
                    launchGroqForChunk(c[0], c[1]);
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        launchGroqForChunk(0, totalPalettes);
    }

    private void launchGroqForChunk(int fromIdx, int toIdx) {
        // Build summary for the given palette range
        StringBuilder sb = new StringBuilder();
        sb.append("Analyzing palettes ").append(fromIdx + 1).append(" to ").append(toIdx)
          .append(" of ").append(pallet_listmap.size()).append("\n\n");
        for (int i = fromIdx; i < toIdx; i++) {
            String name = String.valueOf(pallet_listmap.get(i).get("name"));
            sb.append("📦 Palette #").append(i + 1).append(": ").append(name).append("\n");
            if (all_blocks_list != null) {
                for (Map<String, Object> b : all_blocks_list) {
                    Object p = b.get("palette");
                    if (p instanceof String && Integer.parseInt((String) p) == i + 9) {
                        sb.append("   • [").append(b.get("type")).append("] ")
                          .append(b.get("name")).append(" — ").append(b.get("spec")).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        String prompt = "You are a Sketchware custom block organization expert.\n"
            + "Analyze these palettes and blocks:\n"
            + "1. Duplicate blocks that should be merged\n"
            + "2. Blocks that belong in a different palette\n"
            + "3. Suggested better palette names\n"
            + "4. Blocks with confusing names\n"
            + "5. Overall optimization recommendations\n\n"
            + sb;

        androidx.appcompat.app.AlertDialog loading = new MaterialAlertDialogBuilder(this)
            .setTitle("✨ AI analyzing all palettes…")
            .setMessage("Please wait…")
            .setCancelable(false)
            .create();
        loading.show();

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        exec.execute(() -> {
            String result;
            try {
                // Uses ia_settings shared prefs (same as IaSettingsActivity)
                pro.sketchware.ai.api.GroqApiClientHelper client =
                    pro.sketchware.ai.api.GroqApiClientHelper.getInstance(getApplicationContext());
                result = client.sendMessage(prompt);
            } catch (Exception e) {
                result = "⚠️ Error: " + e.getMessage();
            }
            final String finalResult = result != null ? result : "No response from AI.";
            handler.post(() -> {
                loading.dismiss();
                new MaterialAlertDialogBuilder(this)
                    .setTitle("✨ AI Block Organization Report")
                    .setMessage(finalResult)
                    .setPositiveButton("Close", null)
                    .show();
            });
        });
    }

    // ── AI Create Blocks from natural-language description ────────────────
    @SuppressWarnings("unchecked")
    private void launchAiCreateBlocks() {
        // Guard: check API key
        android.content.SharedPreferences prefs =
            pro.sketchware.ai.storage.AiPreferences.getInstance(this).prefs();
        String apiKey = pro.sketchware.ai.storage.AiPreferences.getInstance(this).getApiKey(pro.sketchware.ai.models.AiProvider.GROQ);
        boolean enabled = !pro.sketchware.ai.storage.AiPreferences.getInstance(this).getApiKey(pro.sketchware.ai.models.AiProvider.GROQ).isEmpty();
        if (apiKey.isEmpty() || !enabled) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Groq API Key Not Configured")
                .setMessage("Enable Groq and add your API key in:\nSettings → App Settings → API Keys (Groq / Morph)")
                .setPositiveButton("Open AI Settings", (d, w) ->
                    startActivity(new android.content.Intent(this,
                        pro.sketchware.ai.activities.AiSettingsActivity.class)))
                .setNegativeButton("Cancel", null).show();
            return;
        }

        // Build palette selector list
        if (pallet_listmap == null || pallet_listmap.isEmpty()) {
            SketchwareUtil.toast("Create a palette first");
            return;
        }

        // Step 1: user describes what blocks they want
        android.widget.EditText descEdit = new android.widget.EditText(this);
        descEdit.setHint("e.g. blocks for managing shared preferences with get, set, clear and contains methods");
        descEdit.setMinLines(3);
        descEdit.setMaxLines(6);
        descEdit.setPadding(48, 24, 48, 24);

        // Palette chooser
        String[] palNames = new String[pallet_listmap.size()];
        for (int i = 0; i < pallet_listmap.size(); i++) {
            palNames[i] = String.valueOf(pallet_listmap.get(i).get("name"));
        }
        final int[] selectedPalette = {0};

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int dp = (int)(getResources().getDisplayMetrics().density * 16);
        root.setPadding(dp, dp/2, dp, dp/2);

        android.widget.TextView palLabel = new android.widget.TextView(this);
        palLabel.setText("Add to palette:");
        palLabel.setPadding(0, dp, 0, 4);
        root.addView(palLabel);

        android.widget.Spinner palSpinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> spinAdap = new android.widget.ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, palNames);
        spinAdap.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        palSpinner.setAdapter(spinAdap);
        palSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) { selectedPalette[0] = pos; }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        root.addView(palSpinner);

        android.widget.TextView descLabel = new android.widget.TextView(this);
        descLabel.setText("Describe the blocks you want:");
        descLabel.setPadding(0, dp, 0, 4);
        root.addView(descLabel);
        root.addView(descEdit);

        new MaterialAlertDialogBuilder(this)
            .setTitle("🤖 AI Create Blocks")
            .setView(root)
            .setPositiveButton("Generate", (dialog, which) -> {
                String desc = descEdit.getText().toString().trim();
                if (desc.isEmpty()) { SketchwareUtil.toast("Please describe the blocks"); return; }

                String palName = palNames[selectedPalette[0]];
                int palIdx = selectedPalette[0] + 9;

                String prompt = "You are a Sketchware custom block creator.\n"
                    + "The user wants blocks for palette: \"" + palName + "\"\n"
                    + "Description: " + desc + "\n\n"
                    + "Generate a JSON array of custom blocks. Each block must have EXACTLY these fields:\n"
                    + "  name: unique_function_name (no spaces, use underscore)\n"
                    + "  spec: display text, use %s for string param, %d for number, %b for boolean\n"
                    + "  type: one of: blank, boolean, d (number), s (string), p (component)\n"
                    + "  opCode: Java code to execute (use %s, %d, %b as placeholders for params)\n"
                    + "  color: hex color like #FF5722\n"
                    + "  typeName: usually empty string\n\n"
                    + "Example:\n"
                    + "[{\"name\":\"get_pref_string\",\"spec\":\"get pref %s default %s\",\"type\":\"s\","
                    + "\"opCode\":\"getSharedPreferences(\\\"prefs\\\",0).getString(%s,%s)\",\"color\":\"#2196F3\",\"typeName\":\"\"}]\n\n"
                    + "Return ONLY the JSON array. No explanation. No markdown.";

                androidx.appcompat.app.AlertDialog loading = new MaterialAlertDialogBuilder(this)
                    .setTitle("🤖 Generating blocks…")
                    .setMessage("Please wait…")
                    .setCancelable(false)
                    .create();
                loading.show();

                java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newSingleThreadExecutor();
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                exec.execute(() -> {
                    String result;
                    try {
                        pro.sketchware.ai.api.GroqApiClientHelper client =
                            pro.sketchware.ai.api.GroqApiClientHelper.getInstance(getApplicationContext());
                        result = client.sendMessage(prompt);
                    } catch (Exception e) {
                        result = null;
                        handler.post(() -> {
                            loading.dismiss();
                            SketchwareUtil.toastError("AI error: " + e.getMessage());
                        });
                        return;
                    }
                    final String jsonResult = result;
                    handler.post(() -> {
                        loading.dismiss();
                        if (jsonResult == null || jsonResult.isEmpty()) {
                            SketchwareUtil.toastError("No response from AI");
                            return;
                        }
                        showAiGeneratedBlocksDialog(jsonResult, palIdx, palName);
                    });
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @SuppressWarnings("unchecked")
    private void showAiGeneratedBlocksDialog(String jsonResult, int palIdx, String palName) {
        // Parse JSON array of blocks
        ArrayList<HashMap<String, Object>> generated = new ArrayList<>();
        try {
            // Strip markdown fences if present
            String json = jsonResult.replaceAll("(?s)```json\\s*|```", "").trim();
            if (!json.startsWith("[")) {
                int start = json.indexOf('[');
                if (start >= 0) json = json.substring(start);
            }
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<
                ArrayList<HashMap<String, Object>>>(){}.getType();
            generated = new com.google.gson.Gson().fromJson(json, type);
        } catch (Exception e) {
            SketchwareUtil.toastError("Could not parse AI response:\n" + jsonResult.substring(0, Math.min(200, jsonResult.length())));
            return;
        }
        if (generated == null || generated.isEmpty()) {
            SketchwareUtil.toast("AI returned no blocks");
            return;
        }

        final ArrayList<HashMap<String, Object>> blocks = generated;
        final boolean[] checked = new boolean[blocks.size()];
        Arrays.fill(checked, true);

        // Build checklist view
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int dp8 = (int)(8 * getResources().getDisplayMetrics().density);

        android.widget.CheckBox selectAll = new android.widget.CheckBox(this);
        selectAll.setText("✅  Select All / Deselect All");
        selectAll.setChecked(true);
        selectAll.setPadding(dp8 * 2, dp8, dp8, dp8);
        root.addView(selectAll);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout listContainer = new android.widget.LinearLayout(this);
        listContainer.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.CheckBox[] cbList = new android.widget.CheckBox[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            final int fi = i;
            HashMap<String, Object> b = blocks.get(i);
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText("📦 " + b.get("name") + "\n   spec: " + b.get("spec")
                + "\n   type: " + b.get("type") + "  |  " + b.get("opCode"));
            cb.setChecked(true);
            cb.setPadding(dp8 * 2, dp8, dp8, dp8);
            cb.setOnCheckedChangeListener((v, c) -> {
                checked[fi] = c;
                boolean allOn = true;
                for (boolean val : checked) if (!val) { allOn = false; break; }
                selectAll.setOnCheckedChangeListener(null);
                selectAll.setChecked(allOn);
                selectAll.setOnCheckedChangeListener((btn, chk) -> {
                    Arrays.fill(checked, chk);
                    for (android.widget.CheckBox box : cbList) box.setChecked(chk);
                });
            });
            cbList[i] = cb;
            listContainer.addView(cb);
        }
        selectAll.setOnCheckedChangeListener((btn, chk) -> {
            Arrays.fill(checked, chk);
            for (android.widget.CheckBox box : cbList) box.setChecked(chk);
        });

        scroll.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            (int)(350 * getResources().getDisplayMetrics().density)));
        scroll.addView(listContainer);
        root.addView(scroll);

        new MaterialAlertDialogBuilder(this)
            .setTitle("🤖 AI Generated " + blocks.size() + " Block(s) → " + palName)
            .setView(root)
            .setPositiveButton("Add Selected", (d, w) -> {
                pushUndo();
                int added = 0;
                for (int i = 0; i < checked.length; i++) {
                    if (!checked[i]) continue;
                    HashMap<String, Object> b = new HashMap<>(blocks.get(i));
                    b.put("palette", String.valueOf(palIdx));
                    // Set required fields with defaults if missing
                    if (!b.containsKey("id"))       b.put("id", String.valueOf(System.currentTimeMillis() + i));
                    if (!b.containsKey("nextBlock")) b.put("nextBlock", -1.0);
                    if (!b.containsKey("subStack1")) b.put("subStack1", -1.0);
                    if (!b.containsKey("subStack2")) b.put("subStack2", -1.0);
                    if (!b.containsKey("typeName"))  b.put("typeName", "");
                    if (!b.containsKey("parameters")) b.put("parameters", new ArrayList<>());
                    all_blocks_list.add(b);
                    added++;
                }
                if (added > 0) {
                    saveBothFiles();
                    refreshList();
                    SketchwareUtil.toast("✅ Added " + added + " block(s) to \"" + palName + "\"");
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        readSettings();
        refreshList();
        refreshCount();
    }

    private void showBlockConfigurationDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_folder_48dp);
        dialog.setTitle("Block configuration");

        DialogBlockConfigurationBinding dialogBinding = DialogBlockConfigurationBinding.inflate(getLayoutInflater());

        dialogBinding.palettesPath.setText(pallet_dir.replace(FileUtil.getExternalStorageDir(), ""));
        dialogBinding.blocksPath.setText(blocks_dir.replace(FileUtil.getExternalStorageDir(), ""));

        dialog.setView(dialogBinding.getRoot());

        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (view, which) -> {
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, Objects.requireNonNull(dialogBinding.palettesPath.getText()).toString());
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, Objects.requireNonNull(dialogBinding.blocksPath.getText()).toString());

            readSettings();
            refreshList();
            view.dismiss();
        });

        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);

        dialog.setNeutralButton("Defaults", (view, which) -> {
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));

            readSettings();
            refreshList();
            view.dismiss();
        });

        dialog.show();
    }

    private void showMoveToBinDialog(int position) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setIcon(R.drawable.ic_mtrl_delete);
        dialog.setTitle(R.string.block_move_to_bin);
        dialog.setMessage(R.string.common_message_confirm);
        dialog.setPositiveButton(R.string.common_word_yes, (v, which) -> {
            pallet_listmap.remove(position);
            Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyItemRemoved(position);
            Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyItemChanged(position);
            draggedView = null;
            pushUndo(); // ← snapshot before delete
            moveRelatedBlocksToRecycleBin(position + 9); // calls saveBothFiles internally
            removeRelatedBlocks(position + 9);           // calls saveBothFiles internally
            refreshCount();
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    private void readSettings() {
        pallet_dir = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
        blocks_dir = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));

        if (FileUtil.isExistFile(blocks_dir) && isValidJson(FileUtil.readFile(blocks_dir))) {
            try {
                all_blocks_list = getGson().fromJson(FileUtil.readFile(blocks_dir), Helper.TYPE_MAP_LIST);

                if (all_blocks_list != null) {
                    return;
                }
                // fall-through to shared handler
            } catch (JsonParseException e) {
                // fall-through to shared handler
            }

            SketchwareUtil.showFailedToParseJsonDialog(this, new File(blocks_dir), "Custom Blocks", v -> readSettings());
        }
    }

    private Boolean isValidJson(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() || element.isJsonArray();
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    private void refreshList() {
        parsePaletteJson:
        {
            String paletteJsonContent;
            if (FileUtil.isExistFile(pallet_dir) && !(paletteJsonContent = FileUtil.readFile(pallet_dir)).isEmpty()) {
                try {
                    pallet_listmap = getGson().fromJson(paletteJsonContent, Helper.TYPE_MAP_LIST);

                    if (pallet_listmap != null) {
                        break parsePaletteJson;
                    }
                    // fall-through to shared handler
                } catch (JsonParseException e) {
                    // fall-through to shared handler
                }

                SketchwareUtil.showFailedToParseJsonDialog(this, new File(pallet_dir), "Custom Block Palettes", v -> refreshList());
            }
            pallet_listmap = new ArrayList<>();
        }

        binding.paletteRecycler.setAdapter(new PaletteAdapter(pallet_listmap));
        binding.recycleSub.setText("Blocks: " + (long) getN(-1));
        refreshCount();
    }

    private double getN(double _p) {
        int n = 0;
        if (all_blocks_list == null) return 0;
        int target = (int) _p;

        for (int i = 0; i < all_blocks_list.size(); i++) {
            Object palObj = all_blocks_list.get(i).get("palette");
            if (palObj == null) continue;
            int palVal;
            if (palObj instanceof Number) {
                palVal = ((Number) palObj).intValue();
            } else {
                try {
                    // Handle both "9" and "9.0" string formats
                    palVal = (int) Double.parseDouble(palObj.toString());
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            if (palVal == target) {
                n++;
            }
        }
        return n;
    }

    private void refreshCount() {
        if (pallet_listmap.isEmpty()) {
            binding.paletteCount.setText("No palettes");
        } else {
            // Count total blocks across all palettes
            int totalBlocks = 0;
            if (all_blocks_list != null) {
                for (HashMap<String, Object> block : all_blocks_list) {
                    Object palObj = block.get("palette");
                    if (palObj == null) continue;
                    int palVal;
                    if (palObj instanceof Number) {
                        palVal = ((Number) palObj).intValue();
                    } else {
                        try { palVal = (int) Double.parseDouble(palObj.toString()); }
                        catch (NumberFormatException e) { continue; }
                    }
                    if (palVal >= 9) totalBlocks++; // palette >= 9 means user palette (not recycle bin)
                }
            }
            binding.paletteCount.setText(pallet_listmap.size() + " Palettes \u2022 " + totalBlocks + " Blocks");
        }
    }

    private void recycleBin(View view) {
        view.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), BlocksManagerDetailsActivity.class);
            intent.putExtra("position", "-1");
            intent.putExtra("dirB", blocks_dir);
            intent.putExtra("dirP", pallet_dir);
            startActivity(intent);
        });
        view.setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Recycle bin")
                    .setMessage("Are you sure you want to empty the recycle bin? " +
                            "Blocks inside will be deleted PERMANENTLY, you CANNOT recover them!")
                    .setPositiveButton("Empty", (dialog, which) -> { pushUndo(); emptyRecyclebin(); })
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .show();
            return true;
        });
    }

    // ── Helper: persist BOTH files atomically ────────────────────────────
    private void saveBothFiles() {
        FileUtil.writeFile(blocks_dir, getGson().toJson(all_blocks_list));
        FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
    }

    private void removeRelatedBlocks(double _p) {
        ArrayList<HashMap<String, Object>> newBlocks = new ArrayList<>();
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (!(Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == _p)) {
                if (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) > _p) {
                    HashMap<String, Object> m = all_blocks_list.get(i);
                    m.put("palette", String.valueOf((long) (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) - 1)));
                    newBlocks.add(m);
                } else {
                    newBlocks.add(all_blocks_list.get(i));
                }
            }
        }
        all_blocks_list = newBlocks;
        saveBothFiles(); // both files atomically
        readSettings();
    }

    private void swapRelatedBlocks(double f, double s) {
        final String TEMP_PALETTE = "TEMP_SWAP";
        for (Map<String, Object> block : all_blocks_list) {
            Object paletteObj = block.get("palette");

            if (paletteObj == null) continue;
            double paletteValue;
            try {
                paletteValue = Double.parseDouble(paletteObj.toString());
            } catch (NumberFormatException e) {
                continue;
            }

            if (paletteValue == f) {
                block.put("palette", TEMP_PALETTE);
            } else if (paletteValue == s) {
                block.put("palette", String.valueOf((long) f));
            }
        }
        for (Map<String, Object> block : all_blocks_list) {
            if (TEMP_PALETTE.equals(block.get("palette"))) {
                block.put("palette", String.valueOf((long) s));
            }
        }
    }

    private void insertBlocksAt(double _p) {
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) > _p || Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == _p) {
                all_blocks_list.get(i).put("palette", String.valueOf((long) (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) + 1)));
            }
        }
        saveBothFiles(); // both files atomically
        readSettings();
        refreshList();
    }

    private void moveRelatedBlocksToRecycleBin(double _p) {
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == _p) {
                all_blocks_list.get(i).put("palette", "-1");
            }
        }
        saveBothFiles(); // both files atomically
        readSettings();
    }

    private void emptyRecyclebin() {
        ArrayList<HashMap<String, Object>> newBlocks = new ArrayList<>();
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (!(Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == -1)) {
                newBlocks.add(all_blocks_list.get(i));
            }
        }
        all_blocks_list = newBlocks;
        saveBothFiles(); // both files atomically
        readSettings();
        refreshList();
    }

    private void showPaletteDialog(boolean isEditing, Integer oldPosition, String oldName, String oldColor, Integer insertAtPosition) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.icon_style_white_96);
        dialog.setTitle(!isEditing ? "Create a new palette" : "Edit palette");

        dialogBinding = DialogPaletteBinding.inflate(getLayoutInflater());

        if (isEditing) {
            dialogBinding.nameEditText.setText(oldName);
            dialogBinding.colorEditText.setText(oldColor.replace("#", ""));
        }

        dialogBinding.openColorPalette.setOnClickListener(v1 -> {
            ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this, 0xFFFFFFFF, false, false);
            colorPickerDialog.a(new ColorPickerDialog.b() {
                @Override
                public void a(int colorInt) {
                    dialogBinding.colorEditText.setText(String.format("%06X", colorInt & 0x00FFFFFF));
                }

                @Override
                public void a(String var1, int var2) {

                }
            });
            colorPickerDialog.showAtLocation(dialogBinding.openColorPalette, Gravity.CENTER, 0, 0);
        });

        dialog.setView(dialogBinding.getRoot());

        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            String nameInput = Objects.requireNonNull(dialogBinding.nameEditText.getText()).toString();
            String colorInput = Objects.requireNonNull(dialogBinding.colorEditText.getText()).toString();

            if (nameInput.isEmpty()) {
                SketchwareUtil.toast("Name cannot be empty", Toast.LENGTH_SHORT);
                return;
            }
            // add hash for the color
            colorInput = "#" + colorInput;

            if (!PropertiesUtil.isHexColor(colorInput)) {
                SketchwareUtil.toast("Please enter a valid HEX color", Toast.LENGTH_SHORT);
                return;
            }

            if (PropertiesUtil.isHexColor(colorInput)) {
                Color.parseColor(colorInput);
                if (!isEditing) {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("name", nameInput);
                    map.put("color", colorInput);

                    if (insertAtPosition == null) {
                        pallet_listmap.add(map);
                        saveBothFiles(); // both files atomically
                        Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyItemInserted(pallet_listmap.size() - 1);
                        readSettings();
                    } else {
                        pallet_listmap.add(insertAtPosition, map);
                        saveBothFiles(); // both files atomically
                        readSettings();
                        Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyItemInserted(insertAtPosition);
                        insertBlocksAt(insertAtPosition + 9);
                    }
                } else {
                    pallet_listmap.get(oldPosition).put("name", nameInput);
                    pallet_listmap.get(oldPosition).put("color", colorInput);
                    saveBothFiles(); // both files atomically
                    readSettings();
                    refreshList();
                }
                refreshCount();
                v.dismiss();
            }
        });

        dialog.setNegativeButton(Helper.getResString(R.string.cancel), null);
        dialog.show();
    }


    private boolean isItInTrash(View draggedView, View trash) {
        if (draggedView == null) return false;

        int[] trashLocation = new int[2];
        trash.getLocationOnScreen(trashLocation);

        int[] draggedLocation = new int[2];
        draggedView.getLocationOnScreen(draggedLocation);

        int draggedY = draggedLocation[1];

        return draggedY <= trashLocation[1] + draggedView.getMeasuredHeight() / 2 && draggedY >= trashLocation[1] - draggedView.getMeasuredHeight() / 2;
    }

    private boolean isItNearTrash(View draggedView, View trash) {
        if (draggedView == null) return false;

        int[] trashLocation = new int[2];
        trash.getLocationOnScreen(trashLocation);

        int[] draggedLocation = new int[2];
        draggedView.getLocationOnScreen(draggedLocation);

        int draggedY = draggedLocation[1];

        return draggedY <= trashLocation[1] + draggedView.getMeasuredHeight() * 2 / 2 && draggedY >= trashLocation[1] - draggedView.getMeasuredHeight() * 2 / 2;
    }


    public class PaletteAdapter extends RecyclerView.Adapter<PaletteAdapter.ViewHolder> {

        private final ArrayList<HashMap<String, Object>> palettes;

        public PaletteAdapter(ArrayList<HashMap<String, Object>> palettes) {
            this.palettes = palettes;

        }

        @NonNull
        @Override
        public PaletteAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PalletCustomviewBinding itemBinding = PalletCustomviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new PaletteAdapter.ViewHolder(itemBinding);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull PaletteAdapter.ViewHolder holder, int position) {
            String paletteColorValue = (String) palettes.get(position).get("color");
            assert paletteColorValue != null;
            int backgroundColor = PropertiesUtil.parseColor(paletteColorValue);

            holder.itemView.setVisibility(View.VISIBLE);
            holder.itemBinding.title.setText(Objects.requireNonNull(pallet_listmap.get(position).get("name")).toString());
            holder.itemBinding.sub.setText("Blocks: " + (long) getN(position + 9));
            holder.itemBinding.color.setBackgroundColor(backgroundColor);
            holder.itemBinding.dragHandler.setVisibility(View.VISIBLE);

            holder.itemBinding.backgroundCard.setOnLongClickListener(v -> {
                final String edit = "Edit";
                final String delete = "Delete";
                final String insert = "Insert";

                PopupMenu popup = new PopupMenu(BlocksManager.this, holder.itemBinding.color);
                Menu menu = popup.getMenu();
                menu.add(edit);
                menu.add(delete);
                menu.add(insert);
                popup.setOnMenuItemClickListener(item -> {
                    int pos = holder.getAbsoluteAdapterPosition();
                    switch (Objects.requireNonNull(item.getTitle()).toString()) {
                        case edit:
                            showPaletteDialog(true, pos,
                                    Objects.requireNonNull(pallet_listmap.get(pos).get("name")).toString(),
                                    Objects.requireNonNull(pallet_listmap.get(pos).get("color")).toString(), null);
                            break;

                        case delete:
                            new MaterialAlertDialogBuilder(BlocksManager.this)
                                    .setTitle(Objects.requireNonNull(pallet_listmap.get(pos).get("name")).toString())
                                    .setMessage("Remove all blocks related to this palette?")
                                    .setPositiveButton("Remove permanently", (dialog, which) -> {
                                        palettes.remove(pos);
                                        notifyItemRemoved(pos);
                                        pushUndo(); // ← snapshot
                                        removeRelatedBlocks(pos + 9); // calls saveBothFiles internally
                                        readSettings();
                                        refreshCount();
                                    })
                                    .setNegativeButton(R.string.common_word_cancel, null)
                                    .setNeutralButton(R.string.block_move_to_bin, (dialog, which) -> {
                                        pushUndo(); // ← snapshot
                                        moveRelatedBlocksToRecycleBin(position + 9); // calls saveBothFiles internally
                                        palettes.remove(pos);
                                        notifyItemRemoved(pos);
                                        removeRelatedBlocks(pos + 9); // calls saveBothFiles internally
                                        readSettings();
                                        refreshCount();
                                    }).show();
                            break;

                        case insert:
                            showPaletteDialog(false, null, null, null, position);
                            break;

                        default:
                    }
                    return true;
                });
                popup.show();

                return true;
            });

            holder.itemBinding.dragHandler.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(holder);
                }

                return false;
            });

            holder.itemBinding.backgroundCard.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), BlocksManagerDetailsActivity.class);
                intent.putExtra("position", String.valueOf((long) (holder.getAbsoluteAdapterPosition() + 9)));
                intent.putExtra("dirB", blocks_dir);
                intent.putExtra("dirP", pallet_dir);
                startActivity(intent);
            });

        }

        @Override
        public int getItemCount() {
            return palettes.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public PalletCustomviewBinding itemBinding;

            public ViewHolder(PalletCustomviewBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }
        }
    }
}
