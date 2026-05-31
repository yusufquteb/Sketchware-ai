package pro.sketchware.activities.projecttools;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.code.SrcCodeEditor;
import pro.sketchware.R;
import pro.sketchware.ai.activities.ChatActivity;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.Conversation;
import pro.sketchware.ai.models.Workspace;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.ai.storage.ConversationManager;
import pro.sketchware.ai.storage.WorkspaceManager;
import pro.sketchware.util.ProjectSearchUtil;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

/**
 * ProjectFileManagerActivity — المتحكم الأساسي في ملفات المشروع.
 *
 * <p>الميزات الشاملة:
 * <ul>
 *   <li>عرض شجرة الملفات الكاملة عبر {@link ProjectToolPaths}</li>
 *   <li>تشفير وفك تشفير الملفات (AES-128) من قائمة الضغط المطول</li>
 *   <li>إنشاء ملفات ومجلدات جديدة</li>
 *   <li>إعادة تسمية الملفات</li>
 *   <li>نسخ المسار إلى الحافظة</li>
 *   <li>حذف الملفات مع تأكيد</li>
 *   <li>البحث في الملفات مع تأخير debounce</li>
 *   <li>ترتيب بالاسم أو الحجم أو التاريخ</li>
 *   <li>إطلاق مساعد AI مرتبط بالمشروع</li>
 *   <li>عرض معلومات الملف (الحجم، التاريخ، المسار)</li>
 *   <li>فتح الملفات في محرر الكود</li>
 * </ul>
 */
public class ProjectFileManagerActivity extends BaseAppCompatActivity {

    private enum SortMode { NAME, SIZE, DATE }

    // ── State ─────────────────────────────────────────────────────────────────
    private String scId;
    private RecyclerView recyclerView;
    private RecyclerView searchRecyclerView;
    private final Map<String, Boolean> expandState = new HashMap<>();
    private final List<FileNode> visibleNodes = new ArrayList<>();
    private final List<ProjectSearchUtil.SearchResult> searchResults = new ArrayList<>();
    private FileTreeAdapter adapter;
    private SearchResultAdapter searchAdapter;
    private String filterQuery = "";
    private String lastSearchQuery = "";
    private boolean isSearchMode = false;
    private ProjectSearchUtil.FileFilter activeFilter = ProjectSearchUtil.FileFilter.ALL;
    private SortMode sortMode = SortMode.NAME;
    private boolean showGenerated = false;
    private TextView statusView;
    private View chipScrollView;
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();

    // ─────────────────────────────────────────────────────────────────────────
    // onCreate
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        scId = getIntent().getStringExtra("sc_id");
        if (TextUtils.isEmpty(scId)) {
            SketchwareUtil.toastError("Project id missing");
            finish();
            return;
        }

        // Root frame allows FAB overlay
        android.widget.FrameLayout rootFrame = new android.widget.FrameLayout(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

        // ── Toolbar ──────────────────────────────────────────────────────────
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("File Manager");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_file_manager);
        toolbar.setOnMenuItemClickListener(item -> onMenuItemSelected(item));
        root.addView(toolbar);

        // ── Search Bar ───────────────────────────────────────────────────────
        TextInputLayout searchLayout = new TextInputLayout(this, null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        searchLayout.setHint("Search files...");
        searchLayout.setStartIconDrawable(R.drawable.ic_mtrl_search);

        float radius = dp(12);
        searchLayout.setShapeAppearanceModel(
                searchLayout.getShapeAppearanceModel().toBuilder()
                        .setAllCorners(CornerFamily.ROUNDED, radius).build());

        int pad = dp(16);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(pad, dp(8), pad, dp(8));
        searchLayout.setLayoutParams(searchLp);

        TextInputEditText searchInput = new TextInputEditText(searchLayout.getContext());
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        searchLayout.addView(searchInput);
        root.addView(searchLayout);

        // ── File type filter chips (search mode only) ────────────────────────
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ChipGroup chipGroup = new ChipGroup(this);
        chipGroup.setSingleSelection(true);
        chipGroup.setPadding(pad, 0, pad, dp(4));
        for (ProjectSearchUtil.FileFilter f : ProjectSearchUtil.FileFilter.values()) {
            Chip chip = new Chip(this);
            chip.setText(f.name());
            chip.setCheckable(true);
            chip.setChecked(f == activeFilter);
            chip.setOnCheckedChangeListener((v, checked) -> {
                if (checked) { activeFilter = f; if (isSearchMode) doSearch(lastSearchQuery); }
            });
            chipGroup.addView(chip);
        }
        chipScroll.addView(chipGroup);
        chipScroll.setVisibility(View.GONE);
        chipScrollView = chipScroll;
        root.addView(chipScroll);

        // ── Status bar ───────────────────────────────────────────────────────
        statusView = new TextView(this);
        statusView.setPadding(pad, 0, pad, dp(8));
        statusView.setTextSize(12f);
        statusView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        root.addView(statusView);

        // ── Tree RecyclerView ─────────────────────────────────────────────────
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileTreeAdapter();
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ── Search results RecyclerView (hidden initially) ───────────────────
        searchRecyclerView = new RecyclerView(this);
        searchRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new SearchResultAdapter();
        searchRecyclerView.setAdapter(searchAdapter);
        searchRecyclerView.setVisibility(View.GONE);
        root.addView(searchRecyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rootFrame.addView(root);

        // ── AI Assistant FAB ─────────────────────────────────────────────────
        ExtendedFloatingActionButton aiFab = new ExtendedFloatingActionButton(this);
        aiFab.setText("AI Assistant");
        aiFab.setIconResource(R.drawable.ic_mtrl_add);
        aiFab.setBackgroundTintList(ColorStateList.valueOf(
                ThemeUtils.getColor(this, R.attr.colorPrimaryContainer)));
        aiFab.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnPrimaryContainer));
        aiFab.setIconTint(ColorStateList.valueOf(
                ThemeUtils.getColor(this, R.attr.colorOnPrimaryContainer)));

        android.widget.FrameLayout.LayoutParams fabLp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fabLp.gravity = Gravity.BOTTOM | Gravity.END;
        fabLp.setMargins(0, 0, dp(16), dp(16));
        aiFab.setLayoutParams(fabLp);
        aiFab.setOnClickListener(v -> launchAiAssistant());
        rootFrame.addView(aiFab);

        setContentView(rootFrame);

        // ── Search debounce ──────────────────────────────────────────────────
        searchInput.addTextChangedListener(new TextWatcher() {
            private Runnable pending;
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                if (pending != null) searchInput.removeCallbacks(pending);
                String q = e == null ? "" : e.toString().trim();
                pending = () -> {
                    if (q.length() >= 2) {
                        enterSearchMode(q);
                    } else {
                        exitSearchMode();
                    }
                };
                searchInput.postDelayed(pending, 300);
            }
        });

        refreshTree();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menu
    // ─────────────────────────────────────────────────────────────────────────

    private boolean onMenuItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_sort_name) {
            sortMode = SortMode.NAME;
            refreshTree();
            return true;
        } else if (id == R.id.menu_sort_size) {
            sortMode = SortMode.SIZE;
            refreshTree();
            return true;
        } else if (id == R.id.menu_sort_date) {
            sortMode = SortMode.DATE;
            refreshTree();
            return true;
        } else if (id == R.id.menu_toggle_generated) {
            showGenerated = !showGenerated;
            item.setTitle(showGenerated ? "Hide Generated" : "Show Generated");
            refreshTree();
            return true;
        } else if (id == R.id.menu_expand_all) {
            expandAll();
            return true;
        } else if (id == R.id.menu_collapse_all) {
            collapseAll();
            return true;
        } else if (id == R.id.menu_search_in_project) {
            launchSearchInProject();
            return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AI Assistant
    // ─────────────────────────────────────────────────────────────────────────

    private void launchAiAssistant() {
        WorkspaceManager wm = new WorkspaceManager(this);
        ConversationManager cm = new ConversationManager(this);
        AiPreferences prefs = AiPreferences.getInstance(this);

        List<Workspace> workspaces = wm.getAllWorkspaces();
        Workspace targetWs = null;
        for (Workspace ws : workspaces) {
            if (ws.hasProject(scId)) {
                targetWs = ws;
                break;
            }
        }

        if (targetWs == null) {
            targetWs = new Workspace("Project " + scId, "Auto-created workspace for FileManager AI");
            targetWs.addProject(scId);
            wm.saveWorkspace(targetWs);
        }

        AiProvider provider = prefs.getSelectedProvider();
        String modelId = prefs.getSelectedModel(provider);
        Conversation conv = new Conversation(targetWs.getId(), "FileManager Assistant", modelId, provider.name());
        cm.saveConversation(conv);

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conv.getId());
        intent.putExtra(ChatActivity.EXTRA_WORKSPACE_ID, targetWs.getId());
        intent.putExtra(ChatActivity.EXTRA_PROJECT_ID, scId);
        intent.putExtra(ChatActivity.EXTRA_PAGE_CONTEXT, "file_manager");
        intent.putExtra(ChatActivity.EXTRA_INITIAL_PROMPT,
                "I am in the File Manager for project " + scId + ". Help me manage the files.");
        startActivity(intent);
    }

    private void launchSearchInProject() {
        Intent intent = new Intent(this, SearchInProjectActivity.class);
        intent.putExtra("sc_id", scId);
        startActivity(intent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tree Building (via ProjectToolPaths)
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshTree() {
        if (isSearchMode) { adapter.notifyDataSetChanged(); return; }
        visibleNodes.clear();
        addRoot("Project Data", ProjectToolPaths.getProjectDataDir(scId), true);
        if (showGenerated) {
            addRoot("Generated Build", ProjectToolPaths.getProjectGeneratedAppDir(scId), false);
        }
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void addRoot(String label, File dir, boolean editable) {
        FileNode node = new FileNode(dir, label, 0, editable, true);
        if (passesFilter(node)) visibleNodes.add(node);
        if (dir.exists() && isExpanded(dir)) {
            buildChildren(dir, 1, editable);
        }
    }

    private void buildChildren(File dir, int depth, boolean editable) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            switch (sortMode) {
                case SIZE: return Long.compare(a.length(), b.length());
                case DATE: return Long.compare(b.lastModified(), a.lastModified());
                default:   return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File child : children) {
            FileNode node = new FileNode(child, child.getName(), depth, editable, false);
            if (passesFilter(node)) visibleNodes.add(node);
            if (child.isDirectory() && isExpanded(child)) {
                buildChildren(child, depth + 1, editable);
            }
        }
    }

    private boolean passesFilter(FileNode node) {
        if (filterQuery.isEmpty()) return true;
        return node.label.toLowerCase(Locale.ROOT).contains(filterQuery)
                || node.file.getAbsolutePath().toLowerCase(Locale.ROOT).contains(filterQuery);
    }

    private boolean isExpanded(File file) {
        return Boolean.TRUE.equals(expandState.get(file.getAbsolutePath()));
    }

    private void toggleExpanded(File file) {
        expandState.put(file.getAbsolutePath(), !isExpanded(file));
        refreshTree();
    }

    private void enterSearchMode(String query) {
        lastSearchQuery = query;
        isSearchMode = true;
        recyclerView.setVisibility(View.GONE);
        searchRecyclerView.setVisibility(View.VISIBLE);
        chipScrollView.setVisibility(View.VISIBLE);
        doSearch(query);
    }

    private void exitSearchMode() {
        isSearchMode = false;
        recyclerView.setVisibility(View.VISIBLE);
        searchRecyclerView.setVisibility(View.GONE);
        chipScrollView.setVisibility(View.GONE);
        filterQuery = "";
        refreshTree();
    }

    private void doSearch(String query) {
        statusView.setText("Searching...");
        searchExecutor.execute(() -> {
            List<ProjectSearchUtil.SearchResult> found = ProjectSearchUtil.globalSearch(
                    ProjectToolPaths.getProjectDataDir(scId),
                    query, false, false, activeFilter);
            runOnUiThread(() -> {
                searchResults.clear();
                searchResults.addAll(found);
                searchAdapter.notifyDataSetChanged();
                statusView.setText(found.size() + " results for \"" + query + "\""
                        + (activeFilter != ProjectSearchUtil.FileFilter.ALL
                                ? " in " + activeFilter.name() : ""));
            });
        });
    }

    private void expandAll() {
        expandAllDir(ProjectToolPaths.getProjectDataDir(scId));
        if (showGenerated) expandAllDir(ProjectToolPaths.getProjectGeneratedAppDir(scId));
        refreshTree();
    }

    private void expandAllDir(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        expandState.put(dir.getAbsolutePath(), true);
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) expandAllDir(child);
            }
        }
    }

    private void collapseAll() {
        expandState.clear();
        refreshTree();
    }

    private void updateStatus() {
        long totalSize = 0;
        int fileCount = 0;
        for (FileNode n : visibleNodes) {
            if (n.file.isFile()) {
                totalSize += n.file.length();
                fileCount++;
            }
        }
        statusView.setText(visibleNodes.size() + " items (" + fileCount + " files, "
                + formatSize(totalSize) + ") • Sort: " + sortMode.name().toLowerCase(Locale.ROOT));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void openForEdit(FileNode node) {
        if (!node.file.isFile()) return;
        // For Sketchware project data files (encrypted, no extension), show decrypted content
        if (isSketchwareDataFile(node.file)) {
            openDecrypted(node);
        } else {
            Intent intent = new Intent(this, SrcCodeEditor.class);
            intent.putExtra("content", node.file.getAbsolutePath());
            intent.putExtra("title", node.file.getName());
            intent.putExtra("path", node.file.getAbsolutePath());
            intent.putExtra("isEditable", node.editable);
            startActivity(intent);
        }
    }

    private boolean isSketchwareDataFile(File file) {
        // Project data files: no extension, inside .sketchware/data/{scId}/
        if (file.getName().contains(".")) return false;
        File dataDir = ProjectToolPaths.getProjectDataDir(scId);
        return ProjectToolPaths.isUnder(file, dataDir);
    }

    private void openDecrypted(FileNode node) {
        // Derive relative name for decryption (e.g. "view", "logic", "file")
        File dataDir = ProjectToolPaths.getProjectDataDir(scId);
        String relPath = ProjectToolPaths.relativize(dataDir, node.file);

        new Thread(() -> {
            String content = SketchwareFileDecryptor.decryptFile(scId, relPath);
            runOnUiThread(() -> showDecryptedContentDialog(node, content));
        }).start();
    }

    private void showDecryptedContentDialog(FileNode node, String content) {
        if (content == null || content.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(node.label + " (empty or unreadable)")
                    .setMessage("The file is empty or could not be decrypted.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // Scrollable text viewer
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.MONOSPACE);
        int p = dp(16);
        tv.setPadding(p, p, p, p);
        tv.setTextIsSelectable(true);
        scroll.addView(tv);

        new MaterialAlertDialogBuilder(this)
                .setTitle(node.label + " (decrypted)")
                .setView(scroll)
                .setPositiveButton("Copy All", (d, w) -> {
                    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("content", content));
                    SketchwareUtil.toast("Content copied");
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showFileMenu(FileNode node) {
        String[] options;
        if (node.file.isDirectory()) {
            options = new String[]{"New File", "New Folder", "Rename", "Copy Path", "File Info", "Delete"};
        } else if (isSketchwareDataFile(node.file)) {
            options = new String[]{"View Decrypted", "Copy Path", "File Info"};
        } else {
            options = new String[]{"Edit", "Rename", "Copy Path", "File Info", "Delete"};
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(node.label)
                .setItems(options, (d, i) -> handleFileMenuAction(node, options[i]))
                .show();
    }

    private void handleFileMenuAction(FileNode node, String action) {
        switch (action) {
            case "Edit":
                openForEdit(node);
                break;
            case "View Decrypted":
                openDecrypted(node);
                break;
            case "Rename":
                renameFile(node);
                break;
            case "Copy Path":
                copyPath(node);
                break;
            case "File Info":
                showFileInfo(node);
                break;
            case "Delete":
                confirmDelete(node);
                break;
            case "New File":
                createNewItem(node, false);
                break;
            case "New Folder":
                createNewItem(node, true);
                break;
        }
    }

    private void copyPath(FileNode node) {
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("path", node.file.getAbsolutePath()));
        SketchwareUtil.toast("Path copied: " + node.file.getAbsolutePath());
    }

    private void showFileInfo(FileNode node) {
        File f = node.file;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        String info = "Name: " + f.getName()
                + "\nPath: " + f.getAbsolutePath()
                + "\nType: " + (f.isDirectory() ? "Directory" : "File")
                + "\nSize: " + formatSize(f.length())
                + "\nModified: " + sdf.format(new Date(f.lastModified()))
                + "\nEditable: " + (node.editable ? "Yes" : "No (Read-only)")
                + "\nGenerated: " + (ProjectToolPaths.isGeneratedFile(scId, f) ? "Yes" : "No");

        new MaterialAlertDialogBuilder(this)
                .setTitle("File Information")
                .setMessage(info)
                .setPositiveButton("Copy Path", (d, w) -> copyPath(node))
                .setNegativeButton("Close", null)
                .show();
    }

    private void renameFile(FileNode node) {
        EditText input = new EditText(this);
        input.setText(node.file.getName());
        input.setSelectAllOnFocus(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename")
                .setView(input)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        File newFile = new File(node.file.getParent(), newName);
                        if (node.file.renameTo(newFile)) {
                            refreshTree();
                            SketchwareUtil.toast("Renamed to: " + newName);
                        } else {
                            SketchwareUtil.toastError("Rename failed");
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewItem(FileNode parentNode, boolean isFolder) {
        File parentDir = parentNode.file.isDirectory() ? parentNode.file : parentNode.file.getParentFile();
        EditText input = new EditText(this);
        input.setHint(isFolder ? "Folder name" : "File name (e.g. MyClass.java)");
        new MaterialAlertDialogBuilder(this)
                .setTitle(isFolder ? "New Folder" : "New File")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        File newFile = new File(parentDir, name);
                        try {
                            boolean success = isFolder ? newFile.mkdirs() : newFile.createNewFile();
                            if (success) {
                                // Auto-expand parent
                                expandState.put(parentDir.getAbsolutePath(), true);
                                refreshTree();
                                SketchwareUtil.toast("Created: " + name);
                            } else {
                                SketchwareUtil.toastError("Already exists or creation failed");
                            }
                        } catch (IOException e) {
                            SketchwareUtil.toastError("Error: " + e.getMessage());
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(FileNode node) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + (node.file.isDirectory() ? "Folder" : "File"))
                .setMessage("Are you sure you want to delete \"" + node.label + "\"?\n"
                        + (node.file.isDirectory() ? "This will delete all contents." : ""))
                .setPositiveButton("Delete", (d, w) -> {
                    FileUtil.deleteFile(node.file.getAbsolutePath());
                    refreshTree();
                    SketchwareUtil.toast("Deleted: " + node.label);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView Adapter
    // ─────────────────────────────────────────────────────────────────────────

    private final class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setClickable(true);
            row.setFocusable(true);
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            row.setBackgroundResource(outValue.resourceId);

            View indent = new View(context);
            row.addView(indent, new LinearLayout.LayoutParams(0, 1));

            ImageView arrow = new ImageView(context);
            int arrowSize = dp(24);
            arrow.setLayoutParams(new LinearLayout.LayoutParams(arrowSize, arrowSize));
            arrow.setPadding(dp(4), dp(4), dp(4), dp(4));
            row.addView(arrow);

            ImageView icon = new ImageView(context);
            int iconSize = dp(24);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.setMarginStart(dp(4));
            iconLp.setMarginEnd(dp(12));
            icon.setLayoutParams(iconLp);
            row.addView(icon);

            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(context);
            title.setTextSize(16f);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setSingleLine(true);
            title.setMaxLines(1);
            title.setTextColor(ThemeUtils.getColor(context, R.attr.colorOnSurface));
            title.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            textContainer.addView(title);

            TextView subtitle = new TextView(context);
            subtitle.setTextSize(11f);
            subtitle.setTextColor(ThemeUtils.getColor(context, R.attr.colorOnSurfaceVariant));
            textContainer.addView(subtitle);

            row.addView(textContainer, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return new VH(row, indent, arrow, icon, title, subtitle);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            FileNode node = visibleNodes.get(position);
            holder.indent.getLayoutParams().width = node.depth * dp(20);
            holder.indent.requestLayout();
            // Reset text properties to prevent corruption during view recycling
            holder.title.setText(null);
            holder.title.setText(node.label);
            holder.title.setEllipsize(TextUtils.TruncateAt.END);
            holder.title.setSingleLine(true);

            if (node.isRoot) {
                holder.title.setTypeface(null, Typeface.BOLD);
                holder.subtitle.setText(node.editable ? "Editable Section" : "Read-only Section");
            } else {
                holder.title.setTypeface(null, Typeface.NORMAL);
                if (node.file.isDirectory()) {
                    File[] contents = node.file.listFiles();
                    int count = contents == null ? 0 : contents.length;
                    holder.subtitle.setText(count + " items");
                } else {
                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.ROOT);
                    holder.subtitle.setText(formatSize(node.file.length())
                            + " • " + sdf.format(new Date(node.file.lastModified())));
                }
            }

            if (node.file.isDirectory()) {
                holder.arrow.setVisibility(View.VISIBLE);
                holder.arrow.setImageResource(R.drawable.ic_mtrl_arrow_right);
                holder.arrow.setRotation(isExpanded(node.file) ? 90 : 0);
                holder.icon.setImageResource(R.drawable.ic_mtrl_folder);
                holder.icon.setColorFilter(ThemeUtils.getColor(
                        holder.itemView.getContext(), R.attr.colorPrimary));
            } else {
                holder.arrow.setVisibility(View.INVISIBLE);
                holder.icon.setImageResource(getFileIcon(node.file.getName()));
                holder.icon.setColorFilter(ThemeUtils.getColor(
                        holder.itemView.getContext(), R.attr.colorOnSurfaceVariant));
            }

            holder.itemView.setOnClickListener(v -> {
                if (node.file.isDirectory()) {
                    // Animate arrow rotation
                    float from = isExpanded(node.file) ? 90 : 0;
                    float to = isExpanded(node.file) ? 0 : 90;
                    ObjectAnimator anim = ObjectAnimator.ofFloat(holder.arrow, "rotation", from, to);
                    anim.setDuration(200);
                    anim.setInterpolator(new AccelerateDecelerateInterpolator());
                    anim.start();
                    toggleExpanded(node.file);
                } else {
                    openForEdit(node);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                showFileMenu(node);
                return true;
            });
        }

        @Override
        public int getItemCount() { return visibleNodes.size(); }

        class VH extends RecyclerView.ViewHolder {
            View indent;
            ImageView arrow, icon;
            TextView title, subtitle;

            VH(View v, View i, ImageView a, ImageView ic, TextView t, TextView s) {
                super(v);
                indent = i;
                arrow = a;
                icon = ic;
                title = t;
                subtitle = s;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search Result Adapter
    // ─────────────────────────────────────────────────────────────────────────

    private final class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            int p = dp(12);
            row.setPadding(p, p, p, p);
            row.setClickable(true);
            row.setFocusable(true);
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);

            LinearLayout topRow = new LinearLayout(parent.getContext());
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvName = new TextView(parent.getContext());
            tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
            tvName.setSingleLine(true);
            tvName.setEllipsize(TextUtils.TruncateAt.START);
            tvName.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
            topRow.addView(tvName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvBadge = new TextView(parent.getContext());
            tvBadge.setTextSize(10f);
            tvBadge.setPadding(dp(5), dp(1), dp(5), dp(1));
            tvBadge.setTextColor(0xFFFFFFFF);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(10));
            tvBadge.setBackground(bg);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.setMarginStart(dp(6));
            tvBadge.setLayoutParams(badgeLp);
            topRow.addView(tvBadge);
            row.addView(topRow);

            TextView tvPath = new TextView(parent.getContext());
            tvPath.setTextSize(11f);
            tvPath.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurfaceVariant));
            tvPath.setSingleLine(true);
            tvPath.setEllipsize(TextUtils.TruncateAt.START);
            row.addView(tvPath);

            TextView tvPreview = new TextView(parent.getContext());
            tvPreview.setTypeface(Typeface.MONOSPACE);
            tvPreview.setTextSize(12f);
            tvPreview.setPadding(dp(6), dp(4), dp(6), dp(4));
            tvPreview.setBackgroundColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorSurfaceVariant));
            row.addView(tvPreview);

            return new VH(row, tvName, tvPath, tvPreview, tvBadge,
                    (android.graphics.drawable.GradientDrawable) tvBadge.getBackground());
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ProjectSearchUtil.SearchResult r = searchResults.get(position);
            String fileName = r.filePath.contains(java.io.File.separator)
                    ? r.filePath.substring(r.filePath.lastIndexOf(java.io.File.separator) + 1)
                    : r.filePath;
            h.tvName.setText(fileName);
            h.tvPath.setText(r.filePath + ":" + r.lineNumber);
            h.tvPreview.setText(r.lineContent.trim());
            h.tvBadge.setText(r.editable ? "editable" : "generated");
            h.badgeBg.setColor(r.editable ? 0xFF2196F3 : 0xFF9E9E9E);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ProjectFileManagerActivity.this, SrcCodeEditor.class);
                i.putExtra("title", fileName);
                i.putExtra("content", r.filePath);
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() { return searchResults.size(); }

        final class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPath, tvPreview, tvBadge;
            android.graphics.drawable.GradientDrawable badgeBg;
            VH(@NonNull View v, TextView n, TextView p, TextView prev, TextView b,
               android.graphics.drawable.GradientDrawable bg) {
                super(v); tvName = n; tvPath = p; tvPreview = prev; tvBadge = b; badgeBg = bg;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private int getFileIcon(String name) {
        if (name.endsWith(".java") || name.endsWith(".kt")) return R.drawable.ic_mtrl_java;
        if (name.endsWith(".xml"))  return R.drawable.ic_mtrl_code;
        if (name.endsWith(".json")) return R.drawable.ic_mtrl_code;
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp"))
            return R.drawable.ic_mtrl_image;
        if (name.endsWith(".gradle") || name.endsWith(".kts")) return R.drawable.ic_mtrl_code;
        return R.drawable.ic_mtrl_file;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp),
                "KMGTPE".charAt(exp - 1));
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        searchExecutor.shutdownNow();
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FileNode model
    // ─────────────────────────────────────────────────────────────────────────

    private static class FileNode {
        File file;
        String label;
        int depth;
        boolean editable;
        boolean isRoot;

        FileNode(File f, String l, int d, boolean e, boolean r) {
            file = f;
            label = l;
            depth = d;
            editable = e;
            isRoot = r;
        }
    }
}
