// nikit overhaul — Task 8 — 2026-05
package dev.aldi.sayuti.editor.manage;

import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.createLibraryMap;
import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.deleteSelectedLocalLibraries;
import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.getAllLocalLibraries;
import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.getLocalLibFile;
import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.getLocalLibraries;
import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.rewriteLocalLibFile;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

import a.a.a.MA;
import a.a.a.mB;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageLocallibrariesBinding;
import pro.sketchware.databinding.ViewItemLocalLibBinding;
import pro.sketchware.databinding.ViewItemLocalLibSearchBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.library.LibraryFolderScanner;
import pro.sketchware.library.LibraryVersionChecker;

import android.os.Environment;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;

//DR
public class ManageLocalLibraryActivity extends BaseAppCompatActivity {
    private final LibraryAdapter adapter = new LibraryAdapter();
    private final SearchAdapter searchAdapter = new SearchAdapter();
    private ArrayList<HashMap<String, Object>> projectUsedLibs;
    private boolean notAssociatedWithProject;
    private boolean searchBarExpanded;
    private BuildSettings buildSettings;
    private ManageLocallibrariesBinding binding;
    private String scId;

    // ── Pagination ────────────────────────────────────────────────────────
    private static final int PAGE_SIZE = 50;
    private int currentPage = 0;
    private List<LocalLibrary> allLibraries = new ArrayList<>();

    // ── Sort ──────────────────────────────────────────────────────────────
    private int sortMode = 3; // 0=name A-Z, 1=name Z-A, 2=size, 3=enabled-first+A-Z (default)

    // ── Shared background executor (avoids thread-pool spam on each click) ──
    private final java.util.concurrent.ExecutorService backgroundExecutor =
            Executors.newSingleThreadExecutor();

    // ── Search debounce (250 ms) ──────────────────────────────────────────
    private final Handler searchDebounceHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ManageLocallibrariesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        {
            View view1 = binding.mainToolbar;
            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> i); // toolbar handles its own insets
        }

        {
            View view1 = binding.contextualToolbarContainer;
            int left = view1.getPaddingLeft();
            int top = view1.getPaddingTop();
            int right = view1.getPaddingRight();
            int bottom = view1.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom);
                return i;
            });
        }

        {
            View view1 = binding.librariesList;
            int left = view1.getPaddingLeft();
            int top = view1.getPaddingTop();
            int right = view1.getPaddingRight();
            int bottom = view1.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }

        {
            View view1 = binding.searchList;
            int left = view1.getPaddingLeft();
            int top = view1.getPaddingTop();
            int right = view1.getPaddingRight();
            int bottom = view1.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }

        // FAB removed – download now via chipDownload

        if (getIntent().hasExtra("sc_id")) {
            scId = Objects.requireNonNull(getIntent().getStringExtra("sc_id"));
            buildSettings = new BuildSettings(scId);
            notAssociatedWithProject = scId.equals("system");
        }

        adapter.setOnLocalLibrarySelectedStateChangedListener(item -> {
            long selectedItemCount = getSelectedLocalLibrariesCount();
            if (selectedItemCount > 0 && adapter.isSelectionModeEnabled) {
                binding.contextualToolbar.setTitle(String.valueOf(selectedItemCount));
                expandContextualToolbar();
            } else {
                adapter.isSelectionModeEnabled = false;
                collapseContextualToolbar();
            }
        });

        binding.librariesList.setAdapter(adapter);
        binding.searchList.setAdapter(searchAdapter);

        // ── Infinite scroll: load next page when user reaches end ────────────
        binding.librariesList.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return; // only when scrolling DOWN
                androidx.recyclerview.widget.LinearLayoutManager lm =
                        (androidx.recyclerview.widget.LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                int total = lm.getItemCount();
                if (lastVisible >= total - 3) { // trigger 3 items before end
                    int start = (currentPage + 1) * PAGE_SIZE;
                    if (start < allLibraries.size()) loadNextPage();
                }
            }
        });

        binding.mainToolbar.setNavigationOnClickListener(v -> {
            if (!mB.a()) {
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        // ── Toolbar overflow menu ─────────────────────────────────────────
        binding.mainToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clean_unused) {
                showRemoveUnusedLibrariesDialog();
                return true;
            }
            return false;
        });

        binding.contextualToolbar.setNavigationOnClickListener(v -> hideContextualToolbarAndClearSelection());
        binding.contextualToolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_select_all) {
                setLocalLibrariesSelected(true);
                binding.contextualToolbar.setTitle(String.valueOf(getSelectedLocalLibrariesCount()));
                return true;
            } else if (id == R.id.action_delete_selected_local_libraries) {
                k();
                backgroundExecutor.execute(() -> {
                    deleteSelectedLocalLibraries(scId, adapter.getLocalLibraries(), projectUsedLibs);
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        h();
                        SketchwareUtil.toast("Deleted successfully");
                        adapter.isSelectionModeEnabled = false;
                        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
                        collapseContextualToolbar();
                    });
                });
                return true;
            } else if (id == R.id.action_remove_unused_libraries) {
                showRemoveUnusedLibrariesDialog();
                return true;
            } else if (id == R.id.action_find_duplicate_libraries) {
                showDuplicateLibrariesDialog();
                return true;
            } else if (id == R.id.action_detect_conflicts) {
                showConflictDetectorDialog();
                return true;
            }
            return false;
        });

        // ── Toolbar menu actions (replaces chips) ────────────────────────────
        binding.mainToolbar.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_search) {
                binding.searchView.show();
                return true;
            } else if (id == R.id.action_refresh_versions) {
                checkAllLibrariesSequentially();
                return true;
            } else if (id == R.id.action_scan_jars) {
                rescanAllLibraryJars();
                return true;
            } else if (id == R.id.action_download) {
                if (getSupportFragmentManager().findFragmentByTag("library_downloader_dialog") != null) return true;
                Bundle bundle = new Bundle();
                bundle.putBoolean("notAssociatedWithProject", notAssociatedWithProject);
                bundle.putSerializable("buildSettings", buildSettings);
                bundle.putString("localLibFile", getLocalLibFile(scId).getAbsolutePath());
                LibraryDownloaderDialogFragment fragment = new LibraryDownloaderDialogFragment();
                fragment.setArguments(bundle);
                fragment.setOnLibraryDownloadedTask(this::runLoadLocalLibrariesTask);
                fragment.show(getSupportFragmentManager(), "library_downloader_dialog");
                return true;
            } else if (id == R.id.action_sort) {
                showSortDialog();
                return true;
            // action_import removed (Task 8) — use action_download toolbar icon to add libraries
            } else if (id == R.id.action_export) {
                exportSelectedOrAll();
                return true;
            } else if (id == R.id.action_backup_all) {
                backupAllLibraries();
                return true;
            } else if (id == R.id.action_restore_backup) {
                restoreFromBackup();
                return true;
            } else if (id == R.id.action_version_history) {
                showVersionHistory();
                return true;
            } else if (id == R.id.action_delete_old_unused) {
                showDeleteOldUnusedDialog();
                return true;
            } else if (id == R.id.action_select) {
                adapter.isSelectionModeEnabled = true;
                adapter.notifyItemRangeChanged(0, adapter.getItemCount());
                expandContextualToolbar();
                binding.contextualToolbar.setTitle("0");
                return true;
            } else if (id == R.id.action_find_duplicate_libraries) {
                showDuplicateLibrariesDialog();
                return true;
            } else if (id == R.id.action_detect_conflicts) {
                showConflictDetectorDialog();
                return true;
            } else if (id == R.id.action_clean_unused) {
                showRemoveUnusedLibrariesDialog();
                return true;
            } else if (id == R.id.action_update_all) {
                batchUpdateAvailableLibraries();
                return true;
            } else if (id == R.id.action_manage_repositories) {
                showManageRepositoriesDialog();
                return true;
            } else if (id == R.id.action_clean_orphans) {
                showOrphanCleanupDialog();
                return true;
            } else if (id == R.id.action_ai) {
                openLibraryAiAssistant();
                return true;
            }
            return false;
        });

        binding.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String value = s.toString().trim();
                if (pendingSearch != null) searchDebounceHandler.removeCallbacks(pendingSearch);
                pendingSearch = () -> searchAdapter.filter(getAdapterLocalLibraries(), value);
                searchDebounceHandler.postDelayed(pendingSearch, 250);
            }

            @Override
            public void onTextChanged(CharSequence newText, int start, int before, int count) {
            }
        });

        runLoadLocalLibrariesTask();

        // Empty state CTA button — mirrors toolbar download action
        binding.btnDownloadEmpty.setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag("library_downloader_dialog") != null) return;
            Bundle bundle = new Bundle();
            bundle.putBoolean("notAssociatedWithProject", notAssociatedWithProject);
            bundle.putSerializable("buildSettings", buildSettings);
            bundle.putString("localLibFile", getLocalLibFile(scId).getAbsolutePath());
            LibraryDownloaderDialogFragment fragment = new LibraryDownloaderDialogFragment();
            fragment.setArguments(bundle);
            fragment.setOnLibraryDownloadedTask(this::runLoadLocalLibrariesTask);
            fragment.show(getSupportFragmentManager(), "library_downloader_dialog");
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchBarExpanded) {
                    hideContextualToolbarAndClearSelection();
                } else if (binding.searchView.isShowing()) {
                    binding.searchView.hide();
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundExecutor.shutdownNow();
        if (pendingSearch != null) searchDebounceHandler.removeCallbacks(pendingSearch);
    }

    private void runLoadLocalLibrariesTask() {
        k();
        new Handler().postDelayed(() -> new LoadLocalLibrariesTask(this).execute(), 500L);
    }

    private List<LocalLibrary> getAdapterLocalLibraries() {
        // Use the full list so search works across ALL libraries, not just the current page
        return allLibraries != null ? allLibraries : adapter.getLocalLibraries();
    }

    private void hideContextualToolbarAndClearSelection() {
        adapter.isSelectionModeEnabled = false;
        if (collapseContextualToolbar()) {
            setLocalLibrariesSelected(false);
        }
    }

    public void setLocalLibrariesSelected(boolean selected) {
        for (LocalLibrary library : getAdapterLocalLibraries()) {
            library.setSelected(selected);
        }
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    private void expandContextualToolbar() {
        searchBarExpanded = true;
        binding.contextualToolbarContainer.setVisibility(android.view.View.VISIBLE);
        binding.appBarLayout.setVisibility(android.view.View.GONE);
    }

    private boolean collapseContextualToolbar() {
        searchBarExpanded = false;
        binding.contextualToolbarContainer.setVisibility(android.view.View.GONE);
        binding.appBarLayout.setVisibility(android.view.View.VISIBLE);
        return true;
    }

    private long getSelectedLocalLibrariesCount() {
        long count = 0;
        for (LocalLibrary library : getAdapterLocalLibraries()) {
            if (library.isSelected()) {
                count++;
            }
        }
        return count;
    }

    // This method is running from the background thread.
    private void loadLibraries() {
        allLibraries = new ArrayList<>(getAllLocalLibraries());
        if (!notAssociatedWithProject) {
            projectUsedLibs = getLocalLibraries(scId);
        }
        // Count usage per library across all projects
        java.util.Map<String, Integer> usageMap = buildUsageMap();
        for (LocalLibrary lib : allLibraries) {
            lib.setUsageCount(usageMap.getOrDefault(lib.getName(), 0));
        }
        // Auto-lookup missing dependencies from lookup table (fast, no network)
        autoFetchMissingDependencies(allLibraries);
        // NOTE: applySortAndPublish is called on the UI thread after this returns (in onPostExecute)
    }

    /** Scans all project local_library files and counts usage per library name. */
    private java.util.Map<String, Integer> buildUsageMap() {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        java.io.File dataRoot = new java.io.File(
            pro.sketchware.utility.FileUtil.getExternalStorageDir(), ".sketchware/data");
        java.io.File[] dirs = dataRoot.listFiles(java.io.File::isDirectory);
        if (dirs == null) return map;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        for (java.io.File dir : dirs) {
            java.io.File lf = new java.io.File(dir, "local_library");
            if (!lf.exists()) continue;
            try {
                String content = pro.sketchware.utility.FileUtil.readFile(lf.getAbsolutePath());
                if (content == null || content.isEmpty()) continue;
                java.util.ArrayList<java.util.HashMap<String, Object>> libs =
                    gson.fromJson(content, mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                for (java.util.HashMap<String, Object> entry : libs) {
                    Object n = entry.get("name");
                    if (n != null) map.merge(n.toString(), 1, Integer::sum);
                }
            } catch (Exception ignored) {}
        }
        return map;
    }

    private void applySortAndPublish(boolean resetPage) {
        List<LocalLibrary> sorted = new ArrayList<>(allLibraries);
        switch (sortMode) {
            case 0: // name A-Z
                sorted.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                break;
            case 1: // name Z-A
                sorted.sort((a, b) -> b.getName().compareToIgnoreCase(a.getName()));
                break;
            case 2: // size descending
                sorted.sort((a, b) -> extractSizeBytes(b.getSize()) - extractSizeBytes(a.getSize()));
                break;
            default: // 3: enabled-first + alphabetical within each group (default)
                sorted.sort((a, b) -> {
                    boolean ia = isUsedLibrary(a.getName()), ib = isUsedLibrary(b.getName());
                    if (ia != ib) return Boolean.compare(ib, ia); // enabled first
                    return a.getName().compareToIgnoreCase(b.getName()); // then A-Z within group
                });
                break;
        }

        if (resetPage) currentPage = 0;
        int end = Math.min((currentPage + 1) * PAGE_SIZE, sorted.size());
        List<LocalLibrary> page = sorted.subList(0, end);

        adapter.setLocalLibraries(page);
        binding.noContentLayout.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private int extractSizeBytes(String sizeStr) {
        try {
            String[] parts = sizeStr.trim().split("\\s+");
            float val = Float.parseFloat(parts[0]);
            String unit = parts.length > 1 ? parts[1].toUpperCase() : "B";
            switch (unit) {
                case "KB": return (int)(val * 1024);
                case "MB": return (int)(val * 1024 * 1024);
                default: return (int) val;
            }
        } catch (Exception e) { return 0; }
    }

    private void loadNextPage() {
        currentPage++;
        applySortAndPublish(false);
    }

    /**
     * For every library without a stored dependency coordinate, queries Maven Central
     * and saves the result to the library's "dependency" file.
     */
    private void autoFetchMissingDependencies(List<LocalLibrary> libs) {
        String localLibsRoot = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/.sketchware/libs/local_libs/";
        // LocalLibrary.fromFile() already ran the scanner; this catches any remaining
        for (LocalLibrary lib : libs) {
            if (lib.getDependency() != null) continue;

            // Jar scan (reads pom.properties / MANIFEST.MF from classes.jar)
            LibraryFolderScanner.ScanResult scan =
                    LibraryFolderScanner.scan(localLibsRoot + lib.getName());
            if (scan != null) {
                lib.setDependency(scan.toCoordinate());
                pro.sketchware.utility.FileUtil.writeFile(
                    localLibsRoot + lib.getName() + "/dependency", scan.toCoordinate());
                continue;
            }

            // Fallback: name-based lookup table
            String coord = LibraryVersionChecker.resolveCoordinate(lib.getName());
            if (coord != null) {
                lib.setDependency(coord);
                pro.sketchware.utility.FileUtil.writeFile(
                    localLibsRoot + lib.getName() + "/dependency", coord);
            }
        }
    }

    private boolean isUsedLibrary(String libraryName) {
        if (!notAssociatedWithProject) {
            for (Map<String, Object> libraryMap : projectUsedLibs) {
                if (libraryName.equals(Objects.requireNonNull(libraryMap.get("name")).toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Library Version Update: propagate new dependency to all projects ─────
    /**
     * Given the old folder name and old/new versions, produces a new folder name.
     * e.g. "jsoup_V_1.12.1", "1.12.1", "1.21.1" → "jsoup_V_1.21.1"
     * Returns null if version not found in the name.
     */
    @androidx.annotation.Nullable
    private String deriveNewLibraryName(String oldName, String oldVersion, String newVersion) {
        if (oldVersion == null || newVersion == null) return null;
        // Try exact substring replace of version (handles all separator styles)
        String v = oldVersion.replace('.', '_');
        String nv = newVersion.replace('.', '_');
        String candidate = oldName.replace(v, nv).replace(oldVersion, newVersion);
        if (!candidate.equals(oldName)) return candidate;
        return null;
    }

    /**
     * Updates all project local_library files when a library folder is renamed or
     * its Maven coordinate changes. Updates: name, dependency, and ALL path fields.
     *
     * Called statically from LibraryDownloaderDialogFragment.
     */
    public static void updateLibraryInAllProjects(
            String oldName, String newName, String oldCoord, String newCoord) {
        String localLibsRoot = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/.sketchware/libs/local_libs/";
        java.io.File dataRoot = new java.io.File(
                pro.sketchware.utility.FileUtil.getExternalStorageDir(), ".sketchware/data");
        java.io.File[] dirs = dataRoot.listFiles(java.io.File::isDirectory);
        if (dirs == null) return;

        com.google.gson.Gson gson = new com.google.gson.Gson();
        boolean nameChanged = !oldName.equals(newName);
        String[] pathKeys = {"jarPath","dexPath","resPath","manifestPath",
                             "pgRulesPath","assetsPath","jniPath","configPath"};

        for (java.io.File dir : dirs) {
            java.io.File lf = new java.io.File(dir, "local_library");
            if (!lf.exists()) continue;
            try {
                String content = pro.sketchware.utility.FileUtil.readFile(lf.getAbsolutePath());
                if (content == null || content.isEmpty()) continue;
                java.util.ArrayList<java.util.HashMap<String, Object>> libs =
                    gson.fromJson(content, mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                boolean changed = false;
                for (java.util.HashMap<String, Object> entry : libs) {
                    Object n = entry.get("name");
                    if (n == null || !oldName.equals(n.toString())) continue;
                    if (newCoord != null) entry.put("dependency", newCoord);
                    if (nameChanged) {
                        entry.put("name", newName);
                        for (String key : pathKeys) {
                            Object val = entry.get(key);
                            if (val instanceof String) {
                                entry.put(key, ((String)val).replace(
                                    localLibsRoot + oldName + "/",
                                    localLibsRoot + newName + "/"));
                            }
                        }
                    }
                    changed = true;
                }
                if (changed) {
                    pro.sketchware.utility.FileUtil.writeFile(lf.getAbsolutePath(), gson.toJson(libs));
                }
            } catch (Exception e) {
                android.util.Log.w("LibUpdate", "Failed: " + dir.getAbsolutePath(), e);
            }
        }
    }

    // Delegate for coord-only updates (name unchanged)
    private void updateDependencyInAllProjects(String libName, String oldCoord, String newCoord) {
        updateLibraryInAllProjects(libName, libName, oldCoord, newCoord);
    }

    /** Updates library reference only in the CURRENT project's local_library file */
    private void updateLibraryInCurrentProject(
            String oldName, String newName, String oldCoord, String newCoord) {
        if (scId == null || notAssociatedWithProject) return;
        String localLibsRoot = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/.sketchware/libs/local_libs/";
        java.io.File lf = new java.io.File(
                pro.sketchware.utility.FileUtil.getExternalStorageDir(),
                ".sketchware/data/" + scId + "/local_library");
        if (!lf.exists()) return;
        try {
            String content = pro.sketchware.utility.FileUtil.readFile(lf.getAbsolutePath());
            if (content == null || content.isEmpty()) return;
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.ArrayList<java.util.HashMap<String, Object>> libs =
                gson.fromJson(content, mod.hey.studios.util.Helper.TYPE_MAP_LIST);
            boolean changed = false;
            boolean nameChanged = !oldName.equals(newName);
            String[] pathKeys = {"jarPath","dexPath","resPath","manifestPath",
                                 "pgRulesPath","assetsPath","jniPath","configPath"};
            for (java.util.HashMap<String, Object> entry : libs) {
                Object n = entry.get("name");
                if (n == null || !oldName.equals(n.toString())) continue;
                if (newCoord != null) entry.put("dependency", newCoord);
                if (nameChanged) {
                    entry.put("name", newName);
                    for (String key : pathKeys) {
                        Object val = entry.get(key);
                        if (val instanceof String) {
                            entry.put(key, ((String) val).replace(
                                localLibsRoot + oldName + "/",
                                localLibsRoot + newName + "/"));
                        }
                    }
                }
                changed = true;
            }
            if (changed) {
                pro.sketchware.utility.FileUtil.writeFile(lf.getAbsolutePath(), gson.toJson(libs));
            }
        } catch (Exception e) {
            android.util.Log.w("LibUpdate", "updateCurrentProject failed", e);
        }
    }


    // ── Remove Unused Libraries (ported from DayDreamCleanUp) ─────────────
    private void showRemoveUnusedLibrariesDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("🧹 Remove Unused Libraries")
            .setMessage("Find and move libraries not used in any project to the recycle bin.\n\nThis may take a moment.")
            .setPositiveButton("Start Cleanup", (d, w) -> {
                androidx.appcompat.app.AlertDialog progress = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Cleaning up…").setMessage("Please wait…").setCancelable(false).create();
                progress.show();
                backgroundExecutor.execute(() -> {
                    int moved = 0; /* ToolCore.cleanupLocalLib: handled by Pro library system */
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        progress.dismiss();
                        String msg = moved > 0
                            ? "✅ Moved " + moved + " unused librar" + (moved == 1 ? "y" : "ies") + " to recycle bin."
                            : "No unused libraries found.";
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Done").setMessage(msg)
                            .setPositiveButton("OK", (dd, ww) -> runLoadLocalLibrariesTask())
                            .show();
                    });
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ── Find & Remove Duplicate Libraries (same base name, different versions) ──
    private void showDuplicateLibrariesDialog() {
        List<LocalLibrary> allLibs = getAdapterLocalLibraries();
        if (allLibs.isEmpty()) { SketchwareUtil.toast("No libraries loaded"); return; }

        // Group by base name (strip version suffix like _V_1.3.0 or _1_2_3)
        java.util.LinkedHashMap<String, List<LocalLibrary>> groups = new java.util.LinkedHashMap<>();
        for (LocalLibrary lib : allLibs) {
            // Normalize: remove trailing version patterns
            String baseName = lib.getName().replaceAll("(?i)_v[_\\d.]+$", "")
                .replaceAll("_\\d+(_\\d+)+$", "").trim();
            if (!groups.containsKey(baseName)) groups.put(baseName, new ArrayList<>());
            groups.get(baseName).add(lib);
        }

        // Keep only groups with 2+ entries
        List<List<LocalLibrary>> dupGroups = new ArrayList<>();
        for (List<LocalLibrary> g : groups.values()) {
            if (g.size() > 1) dupGroups.add(g);
        }

        if (dupGroups.isEmpty()) {
            SketchwareUtil.toast("No duplicate library groups found");
            return;
        }

        float dp = getResources().getDisplayMetrics().density;
        int dp8 = (int)(8*dp), dp4 = (int)(4*dp);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.CheckBox selectAll = new android.widget.CheckBox(this);
        selectAll.setText("  Select All Duplicates");
        selectAll.setChecked(false);
        selectAll.setPadding(dp8, dp8, dp8, dp4);
        root.addView(selectAll);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout listC = new android.widget.LinearLayout(this);
        listC.setOrientation(android.widget.LinearLayout.VERTICAL);

        // Collect all checkboxes for "select all"
        List<android.widget.CheckBox> allCbs = new ArrayList<>();
        // Map checkbox → library name
        java.util.LinkedHashMap<android.widget.CheckBox, String> cbToLib = new java.util.LinkedHashMap<>();

        for (List<LocalLibrary> group : dupGroups) {
            // Group header
            android.widget.TextView groupHeader = new android.widget.TextView(this);
            groupHeader.setText("📚 " + group.get(0).getName().replaceAll("(?i)_v[_\\d.]+$","").replaceAll("_\\d+(_\\d+)+$",""));
            groupHeader.setTextSize(13);
            groupHeader.setPadding(dp8, dp8, dp8, dp4/2);
            groupHeader.setBackgroundColor(0x22FFFFFF);
            listC.addView(groupHeader);

            for (LocalLibrary lib : group) {
                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                cb.setText(lib.getName() + "\n   " + lib.getSize()
                    + (isUsedLibrary(lib.getName()) ? "  ⚠️ used" : ""));
                cb.setChecked(false);
                cb.setPadding(dp8*2, dp4, dp8, dp4);
                cbToLib.put(cb, lib.getName());
                allCbs.add(cb);
                listC.addView(cb);
            }

            android.view.View sep = new android.view.View(this);
            sep.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
            sep.setBackgroundColor(0x33888888);
            listC.addView(sep);
        }

        selectAll.setOnCheckedChangeListener((btn, chk) -> {
            for (android.widget.CheckBox cb : allCbs) cb.setChecked(chk);
        });

        scroll.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (int)(360*dp)));
        scroll.addView(listC);
        root.addView(scroll);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("🔁 " + dupGroups.size() + " Duplicate Group(s)")
            .setMessage("Check the versions you want to DELETE:")
            .setView(root)
            .setPositiveButton("💾 Delete Selected", (d, w) -> {
                List<String> toDelete = new ArrayList<>();
                for (java.util.Map.Entry<android.widget.CheckBox, String> e : cbToLib.entrySet()) {
                    if (e.getKey().isChecked()) toDelete.add(e.getValue());
                }
                if (toDelete.isEmpty()) { SketchwareUtil.toast("Nothing selected"); return; }
                // Mark them selected and delete
                for (LocalLibrary lib : getAdapterLocalLibraries()) {
                    lib.setSelected(toDelete.contains(lib.getName()));
                }
                backgroundExecutor.execute(() -> {
                    deleteSelectedLocalLibraries(scId, adapter.getLocalLibraries(), projectUsedLibs);
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        h();
                        SketchwareUtil.toast("Deleted " + toDelete.size() + " librar" + (toDelete.size()==1?"y":"ies"));
                        adapter.isSelectionModeEnabled = false;
                        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
                    });
                });
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    // ── Sort dialog ───────────────────────────────────────────────────────
    private void showSortDialog() {
        String[] options = {"Name A → Z", "Name Z → A", "Size (largest first)", "Enabled first, A → Z"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Sort Libraries")
            .setSingleChoiceItems(options, sortMode, (d, which) -> {
                sortMode = which;
                d.dismiss();
                new android.os.Handler().post(() -> applySortAndPublish(true));
            })
            .show();
    }

    // ── AI Assistant ──────────────────────────────────────────────────────
    private void openLibraryAiAssistant() {
        pro.sketchware.ai.shared.AiAssistantBottomSheet sheet =
            pro.sketchware.ai.shared.AiAssistantBottomSheet.newInstance(buildLibraryAiConfig());
        sheet.show(getSupportFragmentManager(), "library_ai");
    }

    private pro.sketchware.ai.shared.AiPageConfig buildLibraryAiConfig() {
        java.util.List<pro.sketchware.ai.shared.AiPageConfig.Tool> t = new java.util.ArrayList<>();

        // ── UPDATES ──
        t.add(new pro.sketchware.ai.shared.AiPageConfig.Tool("Updates", R.drawable.ic_mtrl_update));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Find Updates",
            R.drawable.ic_mtrl_refresh,
            "Check all libraries for newer versions. Running now…", "find_updates"));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Update All",
            R.drawable.ic_mtrl_update,
            "Update all libraries that have available updates. Running now…", "update_all"));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Version History",
            R.drawable.ic_mtrl_history,
            "Show version history for library: [enter library name]", "version_history"));

        // ── ANALYSIS ──
        t.add(new pro.sketchware.ai.shared.AiPageConfig.Tool("Analysis", R.drawable.ic_mtrl_warning));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Detect Conflicts",
            R.drawable.ic_mtrl_warning,
            "Detect version conflicts in this project. Running now…", "detect_conflicts"));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Find Duplicates",
            R.drawable.ic_mtrl_filter,
            "Find duplicate or overlapping libraries. Running now…", "find_duplicates"));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Validate All",
            R.drawable.ic_mtrl_check,
            "Run full dependency audit. Running now…", "validate_all"));

        // ── LIBRARY ──
        t.add(new pro.sketchware.ai.shared.AiPageConfig.Tool("Library", R.drawable.ic_mtrl_box));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Search Maven",
            R.drawable.ic_mtrl_download,
            "Search Maven Central for: [enter library name]\n"
            + "Return: groupId:artifactId:version (latest stable)."));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Explain Library",
            R.drawable.ic_mtrl_article,
            "Explain this library: [enter name]\n"
            + "Include: purpose, Android use cases, short code example."));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.ai("Suggest Alternative",
            R.drawable.ic_mtrl_bulb,
            "Suggest an alternative to: [enter name]\n"
            + "Requirements: minSdk 21, actively maintained. Pros/cons vs original."));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Clean Unused",
            R.drawable.ic_mtrl_delete_sweep,
            "Find and list unused libraries. Running scan now…", "clean_unused"));

        // ── MANAGEMENT ──
        t.add(new pro.sketchware.ai.shared.AiPageConfig.Tool("Management", R.drawable.ic_mtrl_settings));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Backup All",
            R.drawable.ic_mtrl_checklist, "Backup all libraries now…", "backup_all"));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Restore Backup",
            R.drawable.ic_mtrl_history, "Restore libraries from backup…", "restore_backup"));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Manage Repos",
            R.drawable.ic_mtrl_code, "Open repository manager…", "manage_repos"));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Clean Orphans",
            R.drawable.ic_mtrl_clear_all, "Scan and clean orphan library files…", "clean_orphans"));
        t.add(pro.sketchware.ai.shared.AiPageConfig.Tool.direct("Scan JARs",
            R.drawable.ic_mtrl_bug_report, "Scan JARs for Maven coordinates…", "scan_jars"));

        return new pro.sketchware.ai.shared.AiPageConfig.Builder()
            .pageTitle("Local Library")
            .scopeLabel(notAssociatedWithProject ? "All Libraries" : "Project " + scId)
            .inputHint("Ask about libraries, search Maven, detect conflicts…")
            .systemPrompt(
                "You are the Library Assistant inside Sketchware Pro (mobile Android IDE).\n"
                + "Project sc_id: " + scId + " | "
                + (notAssociatedWithProject ? "Global browser" : "Project manager") + "\n\n"
                + "PIPELINE (LIBRARY CATEGORY):\n"
                + "1. list_libraries → know current state\n"
                + "2. search_maven → latest stable coords\n"
                + "3. validate_libraries → check conflicts before any recommendation\n"
                + "4. NEVER add/remove without explicit user 'yes'\n"
                + "5. Always show groupId:artifactId:version\n"
                + "6. Flag libraries requiring minSdk > 21\n"
                + "7. Offline: use static known versions, flag as 'offline estimate'\n\n"
                + "FORBIDDEN: add/remove without confirmation | modify files directly\n"
                + "FORMAT: concise, tables for comparisons. Reply in user's language.")
            .tools(t)
            .directActions((actionKey, userInput) -> {
                // Non-destructive read ops — execute directly
                switch (actionKey) {
                    case "find_updates":
                        runOnUiThread(this::checkAllLibrariesSequentially);
                        return "🔄 Checking updates — watch the progress bar above.";
                    case "detect_conflicts":
                        runOnUiThread(this::showConflictDetectorDialog);
                        return "🔍 Opening conflict detector…";
                    case "find_duplicates":
                        runOnUiThread(this::showDuplicateLibrariesDialog);
                        return "🔍 Opening duplicate finder…";
                    case "validate_all":
                        runOnUiThread(this::showConflictDetectorDialog);
                        return "✅ Running validation…";
                    case "manage_repos":
                        runOnUiThread(this::showManageRepositoriesDialog);
                        return "🗄️ Opening repository manager…";
                    case "scan_jars":
                        runOnUiThread(this::rescanAllLibraryJars);
                        return "🔬 Scanning JARs…";
                    case "version_history":
                        runOnUiThread(this::showVersionHistory);
                        return "📋 Opening version history…";
                    case "backup_all":
                        runOnUiThread(this::backupAllLibraries);
                        return "💾 Starting backup…";
                    case "restore_backup":
                        runOnUiThread(this::restoreFromBackup);
                        return "📂 Opening restore dialog…";
                }

                // ── Destructive ops: prompt for backup first ─────────────────
                final String key = actionKey;
                runOnUiThread(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        ManageLocalLibraryActivity.this)
                    .setTitle("Backup before proceeding?")
                    .setMessage("This operation modifies your libraries.\n\n"
                        + "Do you want to create a backup first?")
                    .setPositiveButton("Backup, then proceed", (d, w) -> {
                        backupAllLibraries();
                        // Execute after brief delay to let backup start
                        new android.os.Handler().postDelayed(() -> runOnUiThread(() -> {
                            switch (key) {
                                case "update_all": batchUpdateAvailableLibraries(); break;
                                case "clean_unused": showRemoveUnusedLibrariesDialog(); break;
                                case "clean_orphans": showOrphanCleanupDialog(); break;
                                default: break;
                            }
                        }), 500);
                    })
                    .setNegativeButton("Proceed without backup", (d, w) -> runOnUiThread(() -> {
                        switch (key) {
                            case "update_all": batchUpdateAvailableLibraries(); break;
                            case "clean_unused": showRemoveUnusedLibrariesDialog(); break;
                            case "clean_orphans": showOrphanCleanupDialog(); break;
                            default: break;
                        }
                    }))
                    .setNeutralButton("Cancel", null)
                    .show());
                return "⚠️ A backup confirmation dialog has appeared. Please choose an option.";
            })
            .build();
    }

    /**
     * When a library's dependency coordinate changes (new version), silently update ALL
     * projects that reference it by name — no project needs to be opened.
     * MUST run on a background thread.
     */
    private void updateLibraryInAllProjects(String libName, String newDepCoord) {
        java.io.File dataRoot = new java.io.File(
            pro.sketchware.utility.FileUtil.getExternalStorageDir(), ".sketchware/data");
        java.io.File[] dirs = dataRoot.listFiles(java.io.File::isDirectory);
        if (dirs == null) return;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        int updated = 0;
        for (java.io.File dir : dirs) {
            java.io.File lf = new java.io.File(dir, "local_library");
            if (!lf.exists()) continue;
            try {
                String content = pro.sketchware.utility.FileUtil.readFile(lf.getAbsolutePath());
                if (content == null || content.isEmpty()) continue;
                java.util.ArrayList<java.util.HashMap<String, Object>> libs =
                    gson.fromJson(content, mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                boolean changed = false;
                for (java.util.HashMap<String, Object> entry : libs) {
                    Object n = entry.get("name");
                    if (n != null && libName.equals(n.toString())) {
                        entry.put("dependency", newDepCoord);
                        changed = true;
                    }
                }
                if (changed) {
                    pro.sketchware.utility.FileUtil.writeFile(lf.getAbsolutePath(), gson.toJson(libs));
                    updated++;
                }
            } catch (Exception ignored) {}
        }
        final int count = updated;
        if (count > 0) {
            runOnUiThread(() -> SketchwareUtil.toast(
                "Dependency updated in " + count + " project" + (count == 1 ? "" : "s")));
        }
    }

    // ── Import a .zip or folder as local library ───────────────────────
    private static final int REQUEST_IMPORT_ZIP = 0x5A1;

    private void importLibraryZip() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        startActivityForResult(android.content.Intent.createChooser(intent, "Import library .zip"), REQUEST_IMPORT_ZIP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_ZIP && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri == null) return;
            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    String localLibsPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                            + "/.sketchware/libs/local_libs/";
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is);
                    java.util.zip.ZipEntry ze;
                    String[] libNameHolder = {null};
                    while ((ze = zis.getNextEntry()) != null) {
                        String entryName = ze.getName();
                        if (libNameHolder[0] == null) libNameHolder[0] = entryName.split("/")[0];
                        java.io.File outFile = new java.io.File(localLibsPath + entryName);
                        if (ze.isDirectory()) { outFile.mkdirs(); continue; }
                        outFile.getParentFile().mkdirs();
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                        }
                        zis.closeEntry();
                    }
                    zis.close();
                    is.close();
                    runOnUiThread(() -> {
                        SketchwareUtil.toast("Library imported: " + libNameHolder[0]);
                        runLoadLocalLibrariesTask();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> SketchwareUtil.toastError("Import failed: " + e.getMessage()));
                }
            });
        }
    }

    // ── Export all libraries (or selected) as a .zip ──────────────────
    // ── تصدير المكتبات كـ ZIP ─────────────────────────────────────────────────
    private void exportSelectedOrAll() {
        List<LocalLibrary> selectedLibs = new ArrayList<>();
        for (LocalLibrary lib : allLibraries) {
            if (lib.isSelected()) selectedLibs.add(lib);
        }
        List<LocalLibrary> allLibsCopy = new ArrayList<>(allLibraries);

        if (allLibsCopy.isEmpty()) {
            SketchwareUtil.toast("لا توجد مكتبات للتصدير");
            return;
        }

        int selCount = selectedLibs.size();
        int allCount = allLibsCopy.size();

        // بناء خيارات الديالوج
        String optionSelected = selCount > 0
                ? "📦 المحددة فقط (" + selCount + " مكتبة)"
                : null;
        String optionAll = "📚 كل المكتبات (" + allCount + " مكتبة)";

        if (selCount == 0) {
            // لا يوجد محدد — اسأل مباشرة "تصدير الكل؟"
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("📦 تصدير المكتبات")
                    .setMessage("سيتم تصدير كل المكتبات (" + allCount + ") كملف ZIP واحد.\n\n"
                            + "💡 نصيحة: يمكنك الضغط مطولاً على مكتبة لتحديدها، ثم تحديد "
                            + "عدة مكتبات معاً قبل الضغط على تصدير.")
                    .setPositiveButton("تصدير الكل", (d, w) -> doExport(allLibsCopy))
                    .setNegativeButton(mod.hey.studios.util.Helper.getResString(
                            pro.sketchware.R.string.common_word_cancel), null)
                    .show();
        } else {
            // يوجد محدد — أعطِ خيارَين
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("📦 تصدير المكتبات")
                    .setItems(new String[]{optionSelected, optionAll}, (d, which) -> {
                        if (which == 0) doExport(selectedLibs);
                        else            doExport(allLibsCopy);
                    })
                    .setNegativeButton(mod.hey.studios.util.Helper.getResString(
                            pro.sketchware.R.string.common_word_cancel), null)
                    .show();
        }
    }

    private void doExport(List<LocalLibrary> libs) {
        if (libs.isEmpty()) { SketchwareUtil.toast("لا توجد مكتبات للتصدير"); return; }
        SketchwareUtil.toast("⏳ جارٍ تحضير الـ ZIP…");

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String localLibsPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                        + "/.sketchware/libs/local_libs/";
                java.io.File outDir = new java.io.File(
                        pro.sketchware.utility.FileUtil.getExternalStorageDir() + "/DayDream/exports/");
                outDir.mkdirs();
                String outName = "local_libs_" + libs.size() + "_"
                        + System.currentTimeMillis() + ".zip";
                java.io.File outZip = new java.io.File(outDir, outName);

                try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                        new java.io.FileOutputStream(outZip))) {
                    for (LocalLibrary lib : libs) {
                        zipFolder(new java.io.File(localLibsPath + lib.getName()),
                                lib.getName(), zos);
                    }
                }
                runOnUiThread(() -> {
                    android.content.Intent shareIntent =
                            new android.content.Intent(android.content.Intent.ACTION_SEND);
                    android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                            this, getPackageName() + ".provider", outZip);
                    shareIntent.setType("application/zip");
                    shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, fileUri);
                    shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(android.content.Intent.createChooser(
                            shareIntent, "تصدير " + libs.size() + " مكتبة"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("فشل التصدير: " + e.getMessage()));
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 💾 Backup All Libraries
    // ══════════════════════════════════════════════════════════════════════
    private void backupAllLibraries() {
        String backupDir = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/DayDream/backups/";
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("💾 Backup All Libraries")
            .setMessage("Creates a ZIP of all " + allLibraries.size() + " libraries.\n\n"
                    + "Saved to:\n" + backupDir)
            .setPositiveButton("Backup Now", (d, w) -> {
                SketchwareUtil.toast("⏳ Creating backup…");
                java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        java.io.File out = new java.io.File(backupDir);
                        out.mkdirs();
                        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                                java.util.Locale.getDefault()).format(new java.util.Date());
                        java.io.File zip = new java.io.File(out, "libs_backup_" + ts + ".zip");
                        String localLibsPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                                + "/.sketchware/libs/local_libs/";
                        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                                new java.io.FileOutputStream(zip))) {
                            for (LocalLibrary lib : allLibraries) {
                                zipFolder(new java.io.File(localLibsPath + lib.getName()),
                                        lib.getName(), zos);
                            }
                        }
                        // Save manifest JSON
                        String manifest = new com.google.gson.Gson().toJson(
                            allLibraries.stream()
                                .map(l -> java.util.Map.of(
                                    "name", l.getName(),
                                    "dep",  l.getDependency() != null ? l.getDependency() : "",
                                    "size", l.getSize()))
                                .collect(java.util.stream.Collectors.toList()));
                        pro.sketchware.utility.FileUtil.writeFile(
                            out.getAbsolutePath() + "/manifest_" + ts + ".json", manifest);
                        runOnUiThread(() -> {
                            android.content.Intent shareIntent =
                                new android.content.Intent(android.content.Intent.ACTION_SEND);
                            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                                this, getPackageName() + ".provider", zip);
                            shareIntent.setType("application/zip");
                            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(android.content.Intent.createChooser(shareIntent,
                                    "Backup saved — share or keep"));
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> SketchwareUtil.toastError("Backup failed: " + e.getMessage()));
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ══════════════════════════════════════════════════════════════════════
    // ♻️ Restore from Backup
    // ══════════════════════════════════════════════════════════════════════
    private void restoreFromBackup() {
        String backupDir = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/DayDream/backups/";
        java.io.File dir = new java.io.File(backupDir);
        java.io.File[] zips = dir.listFiles(f -> f.getName().endsWith(".zip")
                && f.getName().startsWith("libs_backup_"));

        if (zips == null || zips.length == 0) {
            SketchwareUtil.toast("No backups found in " + backupDir);
            return;
        }
        // Sort newest first
        java.util.Arrays.sort(zips, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        String[] labels = new String[zips.length];
        java.io.File[] finalZips = zips;
        for (int i = 0; i < zips.length; i++) {
            long mb = zips[i].length() / 1024 / 1024;
            labels[i] = zips[i].getName().replace("libs_backup_", "").replace(".zip", "")
                    + "  (" + mb + " MB)";
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("♻️ Restore from Backup")
            .setItems(labels, (d, which) -> {
                java.io.File selected = finalZips[which];
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Restore " + labels[which] + "?")
                    .setMessage("This will OVERWRITE existing libraries with the same names.\n\n"
                            + "Libraries NOT in the backup will remain untouched.")
                    .setPositiveButton("Restore", (d2, w2) -> {
                        SketchwareUtil.toast("⏳ Restoring…");
                        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                            try {
                                String localLibsPath = pro.sketchware.utility.FileUtil
                                        .getExternalStorageDir()
                                        + "/.sketchware/libs/local_libs/";
                                try (java.util.zip.ZipInputStream zis =
                                        new java.util.zip.ZipInputStream(
                                                new java.io.FileInputStream(selected))) {
                                    java.util.zip.ZipEntry entry;
                                    while ((entry = zis.getNextEntry()) != null) {
                                        java.io.File out = new java.io.File(
                                                localLibsPath + entry.getName());
                                        if (entry.isDirectory()) { out.mkdirs(); continue; }
                                        out.getParentFile().mkdirs();
                                        try (java.io.FileOutputStream fos =
                                                new java.io.FileOutputStream(out)) {
                                            byte[] buf = new byte[8192]; int len;
                                            while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                                        }
                                        zis.closeEntry();
                                    }
                                }
                                runOnUiThread(() -> {
                                    SketchwareUtil.toast("✅ Restore complete");
                                    runLoadLocalLibrariesTask();
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> SketchwareUtil.toastError(
                                        "Restore failed: " + e.getMessage()));
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 📜 Version History
    // ══════════════════════════════════════════════════════════════════════
    private static final String VERSION_HISTORY_FILE =
            pro.sketchware.utility.FileUtil.getExternalStorageDir()
            + "/DayDream/lib_version_history.json";

    /** Call this whenever a library is updated to record its version change */
    static void recordVersionChange(String libName, String oldVersion, String newVersion) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.io.File f = new java.io.File(VERSION_HISTORY_FILE);
            f.getParentFile().mkdirs();
            java.util.List<java.util.Map<String, String>> history = new java.util.ArrayList<>();
            if (f.exists()) {
                String raw = pro.sketchware.utility.FileUtil.readFile(f.getAbsolutePath());
                if (raw != null && !raw.isEmpty()) {
                    try {
                        history = gson.fromJson(raw,
                            new com.google.gson.reflect.TypeToken<
                                java.util.List<java.util.Map<String, String>>>(){}.getType());
                    } catch (Exception ignored) {}
                }
            }
            java.util.Map<String, String> entry = new java.util.LinkedHashMap<>();
            entry.put("lib",  libName);
            entry.put("from", oldVersion != null ? oldVersion : "?");
            entry.put("to",   newVersion);
            entry.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                    java.util.Locale.getDefault()).format(new java.util.Date()));
            history.add(0, entry); // newest first
            if (history.size() > 200) history = history.subList(0, 200); // keep last 200
            pro.sketchware.utility.FileUtil.writeFile(f.getAbsolutePath(), gson.toJson(history));
        } catch (Exception ignored) {}
    }

    private void showVersionHistory() {
        java.io.File f = new java.io.File(VERSION_HISTORY_FILE);
        if (!f.exists()) {
            SketchwareUtil.toast("No version history yet. Update a library to start recording.");
            return;
        }
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String raw = pro.sketchware.utility.FileUtil.readFile(f.getAbsolutePath());
                java.util.List<java.util.Map<String, String>> history =
                    new com.google.gson.Gson().fromJson(raw,
                        new com.google.gson.reflect.TypeToken<
                            java.util.List<java.util.Map<String, String>>>(){}.getType());
                if (history == null || history.isEmpty()) {
                    runOnUiThread(() -> SketchwareUtil.toast("History is empty."));
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (java.util.Map<String, String> e : history) {
                    sb.append("📦 ").append(getDisplayName(e.getOrDefault("lib", "?")))
                      .append("\n   ").append(e.getOrDefault("from","?"))
                      .append(" → ").append(e.getOrDefault("to","?"))
                      .append("   ").append(e.getOrDefault("date",""))
                      .append("\n\n");
                }
                String text = sb.toString().trim();
                runOnUiThread(() ->
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("📜 Version History (" + history.size() + " updates)")
                        .setMessage(text)
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Clear History", (d, w) -> {
                            f.delete();
                            SketchwareUtil.toast("History cleared.");
                        })
                        .show());
            } catch (Exception e) {
                runOnUiThread(() -> SketchwareUtil.toastError("Error: " + e.getMessage()));
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 🗑 Delete Old Unused Libraries (90+ days)
    // ══════════════════════════════════════════════════════════════════════
    private void showDeleteOldUnusedDialog() {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            java.util.Map<String, Integer> usageMap = buildUsageMap();
            String localLibsPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + "/.sketchware/libs/local_libs/";
            long cutoff = System.currentTimeMillis() - (90L * 24 * 3600 * 1000); // 90 days

            List<LocalLibrary> old = new ArrayList<>();
            for (LocalLibrary lib : allLibraries) {
                int used = usageMap.getOrDefault(lib.getName(), 0);
                if (used > 0) continue; // skip if used
                java.io.File folder = new java.io.File(localLibsPath + lib.getName());
                if (folder.lastModified() < cutoff) old.add(lib);
            }

            if (old.isEmpty()) {
                runOnUiThread(() -> SketchwareUtil.toast(
                    "No unused libraries older than 90 days found."));
                return;
            }

            StringBuilder msg = new StringBuilder();
            msg.append("Found ").append(old.size())
               .append(" unused librar").append(old.size() == 1 ? "y" : "ies")
               .append(" not modified in 90+ days:\n\n");
            old.stream().limit(8).forEach(l ->
                msg.append("• ").append(getDisplayName(l.getName())).append("  ")
                   .append(l.getSize()).append("\n"));
            if (old.size() > 8)
                msg.append("… and ").append(old.size() - 8).append(" more\n");

            // Calculate total size
            long totalBytes = old.stream().mapToLong(l -> {
                try {
                    java.io.File f = new java.io.File(localLibsPath + l.getName());
                    return folderSize(f);
                } catch (Exception e) { return 0L; }
            }).sum();
            msg.append("\nTotal: ").append(pro.sketchware.utility.FileUtil.formatFileSize(totalBytes));

            runOnUiThread(() ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("🗑 Delete Old Unused Libraries")
                    .setMessage(msg.toString())
                    .setPositiveButton("Delete All " + old.size(), (d, w) -> {
                        for (LocalLibrary lib : old) {
                            pro.sketchware.utility.FileUtil.deleteFile(
                                localLibsPath + lib.getName());
                        }
                        SketchwareUtil.toast("Deleted " + old.size() + " old libraries.");
                        runLoadLocalLibrariesTask();
                    })
                    .setNeutralButton("Review List", (d, w) -> {
                        String[] items = old.stream()
                            .map(l -> getDisplayName(l.getName()) + "  " + l.getSize())
                            .toArray(String[]::new);
                        boolean[] checked = new boolean[old.size()];
                        java.util.Arrays.fill(checked, true);
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Select libraries to delete")
                            .setMultiChoiceItems(items, checked, (d2, i, c) -> checked[i] = c)
                            .setPositiveButton("Delete Selected", (d2, w2) -> {
                                int count = 0;
                                for (int i = 0; i < old.size(); i++) {
                                    if (checked[i]) {
                                        pro.sketchware.utility.FileUtil.deleteFile(
                                            localLibsPath + old.get(i).getName());
                                        count++;
                                    }
                                }
                                SketchwareUtil.toast("Deleted " + count + " libraries.");
                                runLoadLocalLibrariesTask();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
        });
    }

    private long folderSize(java.io.File f) {
        if (f.isFile()) return f.length();
        long total = 0;
        java.io.File[] children = f.listFiles();
        if (children != null) for (java.io.File c : children) total += folderSize(c);
        return total;
    }

    private void zipFolder(java.io.File folder, String prefix, java.util.zip.ZipOutputStream zos) throws Exception {
        java.io.File[] files = folder.listFiles();
        if (files == null) return;
        for (java.io.File file : files) {
            String name = prefix + "/" + file.getName();
            if (file.isDirectory()) { zipFolder(file, name, zos); continue; }
            zos.putNextEntry(new java.util.zip.ZipEntry(name));
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] buf = new byte[8192]; int len;
                while ((len = fis.read(buf)) > 0) zos.write(buf, 0, len);
            }
            zos.closeEntry();
        }
    }

    /**
     * Re-scans ALL library folders to extract Maven coordinates from embedded jar metadata.
     * Runs in background, updates dependency files, then refreshes the list.
     * This is the most reliable way to identify libraries — reads pom.properties from classes.jar.
     */
    private void rescanAllLibraryJars() {
        binding.progressRefreshAll.setVisibility(android.view.View.VISIBLE);
        binding.progressRefreshAll.setIndeterminate(false);
        binding.progressRefreshAll.setMax(allLibraries.size());

        String localLibsRoot = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/.sketchware/libs/local_libs/";
        List<LocalLibrary> snapshot = new ArrayList<>(allLibraries);

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            int found = 0, skipped = 0;
            for (int i = 0; i < snapshot.size(); i++) {
                LocalLibrary lib = snapshot.get(i);
                final int idx = i;
                runOnUiThread(() -> {
                    binding.progressRefreshAll.setProgressCompat(idx + 1, true);
                    binding.mainToolbar.setSubtitle("Scanning JAR: " + (idx + 1) + " / " + snapshot.size());
                });

                // Scan the jar for embedded Maven metadata
                LibraryFolderScanner.ScanResult scan =
                        LibraryFolderScanner.scan(localLibsRoot + lib.getName());

                if (scan != null) {
                    String coord = scan.toCoordinate();
                    // Update if new or improved (pom.properties > name-lookup)
                    if (lib.getDependency() == null
                            || scan.source.equals("pom.properties")
                            || scan.source.equals("MANIFEST.MF")) {
                        lib.setDependency(coord);
                        pro.sketchware.utility.FileUtil.writeFile(
                            localLibsRoot + lib.getName() + "/dependency", coord);
                    }
                    found++;
                } else {
                    skipped++;
                }
            }

            final int f = found, s = skipped;
            runOnUiThread(() -> {
                binding.mainToolbar.setSubtitle(null);
                binding.progressRefreshAll.setVisibility(android.view.View.GONE);
                binding.progressRefreshAll.setIndeterminate(true);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Scan Complete")
                    .setMessage(
                        "✅ Identified: " + f + " libraries\n" +
                        "❓ Unknown: " + s + " (no Maven metadata in jar)\n\n" +
                        "Known libraries are now ready for version checking.\n" +
                        "Tap Refresh (🔄) to check for updates.")
                    .setPositiveButton("Check Updates", (d, w) -> checkAllLibrariesSequentially())
                    .setNeutralButton("OK", null)
                    .show();
                adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            });
        });
    }

    /**
     * Silently resolves coords from lookup table for all loaded libs (no network),
     * then starts background version checks for the first page.
     * Called automatically after the library list loads.
     */
    private void autoCheckVisibleLibraries() {
        List<LocalLibrary> visible = new ArrayList<>(adapter.getLocalLibraries());
        // Step 1: resolve deps from lookup (instant, no network)
        for (LocalLibrary lib : visible) {
            if (lib.getDependency() == null) {
                String coord = LibraryVersionChecker.resolveCoordinate(lib.getName());
                if (coord != null) {
                    lib.setDependency(coord);
                    String depPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                            + "/.sketchware/libs/local_libs/" + lib.getName() + "/dependency";
                    pro.sketchware.utility.FileUtil.writeFile(depPath, coord);
                }
            }
        }
        // Step 2: check versions in background (low priority, not sequential)
        for (LocalLibrary lib : visible) {
            if (lib.getDependency() != null && !lib.isVersionChecked()) {
                lib.markVersionChecked();
                final LocalLibrary finalLib = lib;
                LibraryVersionChecker.checkLatestVersion(lib.getDependency(), (cur, latest) -> runOnUiThread(() -> {
                    if (latest != null) {
                        finalLib.setLatestVersion(latest);
                        int idx = adapter.getLocalLibraries().indexOf(finalLib);
                        if (idx >= 0) adapter.notifyItemChanged(idx);
                    }
                }));
            }
        }
    }

    // ── Batch update all libraries that have a newer version ─────────────
    // ── Build map: libName → list of OTHER project IDs that use it ──────────
    private java.util.Map<String, List<String>> buildLibToOtherProjectsMap() {
        java.util.Map<String, List<String>> map = new java.util.HashMap<>();
        java.io.File dataRoot = new java.io.File(
            pro.sketchware.utility.FileUtil.getExternalStorageDir(), ".sketchware/data");
        java.io.File[] dirs = dataRoot.listFiles(java.io.File::isDirectory);
        if (dirs == null) return map;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        for (java.io.File dir : dirs) {
            String projectId = dir.getName();
            if (projectId.equals(scId)) continue; // skip current project
            java.io.File lf = new java.io.File(dir, "local_library");
            if (!lf.exists()) continue;
            try {
                String content = pro.sketchware.utility.FileUtil.readFile(lf.getAbsolutePath());
                if (content == null || content.isEmpty()) continue;
                java.util.ArrayList<java.util.HashMap<String, Object>> libs =
                    gson.fromJson(content, mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                for (java.util.HashMap<String, Object> entry : libs) {
                    Object n = entry.get("name");
                    if (n == null) continue;
                    map.computeIfAbsent(n.toString(), k -> new ArrayList<>()).add(projectId);
                }
            } catch (Exception ignored) {}
        }
        return map;
    }

    private void batchUpdateAvailableLibraries() {
        List<LocalLibrary> toUpdate = new ArrayList<>();
        for (LocalLibrary lib : allLibraries) {
            if (lib.getLatestVersion() != null && lib.getDependency() != null) toUpdate.add(lib);
        }
        if (toUpdate.isEmpty()) {
            pro.sketchware.utility.SketchwareUtil.toast("No updates available – tap Refresh first");
            return;
        }

        // Build list of libs that affect OTHER projects
        java.util.Map<String, List<String>> libToOtherProjects = buildLibToOtherProjectsMap();
        List<LocalLibrary> affectsOthers = new ArrayList<>();
        java.util.Set<String> affectedProjectIds = new java.util.LinkedHashSet<>();
        for (LocalLibrary lib : toUpdate) {
            List<String> others = libToOtherProjects.get(lib.getName());
            if (others != null && !others.isEmpty()) {
                affectsOthers.add(lib);
                affectedProjectIds.addAll(others);
            }
        }

        // Summary message
        StringBuilder msg = new StringBuilder();
        msg.append("Found ").append(toUpdate.size())
           .append(" librar").append(toUpdate.size() == 1 ? "y" : "ies")
           .append(" with updates:\n\n");
        toUpdate.stream().limit(6).forEach(l ->
            msg.append("• ").append(getDisplayName(l.getName()))
               .append("  ").append(l.getCurrentVersion())
               .append(" → ").append(l.getLatestVersion()).append("\n"));
        if (toUpdate.size() > 6)
            msg.append("  … and ").append(toUpdate.size() - 6).append(" more\n");

        if (!affectsOthers.isEmpty()) {
            // WARN: other projects will break
            msg.append("\n⚠️  ").append(affectsOthers.size())
               .append(" of these librar").append(affectsOthers.size() == 1 ? "y is" : "ies are")
               .append(" also used in ").append(affectedProjectIds.size())
               .append(" other project").append(affectedProjectIds.size() == 1 ? "" : "s")
               .append(":\n");
            affectsOthers.stream().limit(4).forEach(l ->
                msg.append("  • ").append(getDisplayName(l.getName())).append("\n"));
            if (affectsOthers.size() > 4)
                msg.append("  … and ").append(affectsOthers.size() - 4).append(" more\n");
            msg.append("\nThose projects may fail to build unless their libraries are updated too.");

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("⚠️  Batch Update — Impact Warning")
                .setMessage(msg.toString())
                .setPositiveButton("Update & Fix All Projects", (d, w) ->
                    performBatchDownload(toUpdate, true))
                .setNeutralButton("Update This Library Only", (d, w) ->
                    performBatchDownload(toUpdate, false))
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            // No cross-project impact
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Batch Update")
                .setMessage(msg.toString())
                .setPositiveButton("Update All", (d, w) ->
                    performBatchDownload(toUpdate, true))
                .setNegativeButton("Cancel", null)
                .show();
        }
    }


    private void performBatchDownload(List<LocalLibrary> libs, boolean updateOtherProjects) {
        binding.progressRefreshAll.setVisibility(android.view.View.VISIBLE);
        binding.progressRefreshAll.setIndeterminate(false);
        binding.progressRefreshAll.setMax(libs.size());
        final int[] done = {0};
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            for (LocalLibrary lib : libs) {
                if (lib.getDependency() == null || lib.getLatestVersion() == null) continue;
                String[] parts = lib.getDependency().split(":");
                if (parts.length < 3) continue;
                String newCoord = parts[0] + ":" + parts[1] + ":" + lib.getLatestVersion();
                final int idx = done[0];
                runOnUiThread(() -> {
                    binding.mainToolbar.setSubtitle("Updating: " + (idx + 1) + " / " + libs.size());
                    binding.progressRefreshAll.setProgressCompat(idx+1, true);
                });
                String oldCoord = lib.getDependency();
                String oldName  = lib.getName();
                String newName  = deriveNewLibraryName(oldName, lib.getCurrentVersion(), lib.getLatestVersion());
                if (newName == null) newName = oldName;
                final String fn = newName;
                lib.setDependency(newCoord);
                lib.setLatestVersion(null);
                // Write new coordinate to disk
                String depPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + "/.sketchware/libs/local_libs/" + oldName + "/dependency";
                pro.sketchware.utility.FileUtil.writeFile(depPath, newCoord);
                recordVersionChange(oldName, lib.getCurrentVersion(), lib.getLatestVersion());
                // Rename folder if version changed
                if (!fn.equals(oldName)) {
                    java.io.File oldDir = new java.io.File(
                        pro.sketchware.utility.FileUtil.getExternalStorageDir()
                        + "/.sketchware/libs/local_libs/" + oldName);
                    java.io.File newDir = new java.io.File(
                        pro.sketchware.utility.FileUtil.getExternalStorageDir()
                        + "/.sketchware/libs/local_libs/" + fn);
                    if (oldDir.exists() && !newDir.exists()) oldDir.renameTo(newDir);
                }
                if (updateOtherProjects) {
                    // Update ALL projects (current + others)
                    updateLibraryInAllProjects(oldName, fn, oldCoord, newCoord);
                } else {
                    // Update only the current project's local_library file
                    updateLibraryInCurrentProject(oldName, fn, oldCoord, newCoord);
                }
                done[0]++;
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
            runOnUiThread(() -> {
                binding.mainToolbar.setSubtitle(null);
                binding.progressRefreshAll.setVisibility(android.view.View.GONE);
                binding.progressRefreshAll.setIndeterminate(true);
                String scope = updateOtherProjects ? " (all projects)" : " (this project only)";
                pro.sketchware.utility.SketchwareUtil.toast(done[0] + " librar"
                        + (done[0] == 1 ? "y" : "ies") + " updated" + scope);
                runLoadLocalLibrariesTask();
            });
        });
    }

    // ── Check ALL libraries for updates (sequential with per-item loading) ──
    private volatile boolean isRefreshRunning = false;

    private void checkAllLibrariesSequentially() {
        if (isRefreshRunning) {
            // إيقاف الفحص الجاري عند الضغط مرة ثانية
            isRefreshRunning = false;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                binding.mainToolbar.setSubtitle(null);
                binding.tvUpdateCounter.setVisibility(android.view.View.GONE);
                binding.progressRefreshAll.setVisibility(android.view.View.GONE);
                binding.progressRefreshAll.setIndeterminate(true);
                SketchwareUtil.toast("تم إيقاف الفحص");
            });
            return;
        }

        // Gather ALL libraries (not just current page) from allLibraries
        List<LocalLibrary> all = new ArrayList<>(allLibraries);
        List<LocalLibrary> checkable = new ArrayList<>();
        for (LocalLibrary l : all) {
            // Include if has stored dep, OR if we can resolve coordinate from name
            if (l.getDependency() != null || LibraryVersionChecker.resolveCoordinate(l.getName()) != null) {
                checkable.add(l);
            }
        }

        if (checkable.isEmpty()) {
            SketchwareUtil.toast("No recognizable Maven libraries found");
            return;
        }

        isRefreshRunning = true;
        // Show counter TextView (reliable on all devices unlike SearchBar subtitle)
        binding.tvUpdateCounter.setVisibility(android.view.View.VISIBLE);
        binding.tvUpdateCounter.setText("Checking 0 / " + checkable.size());
        binding.mainToolbar.setSubtitle("0 / " + checkable.size());
        binding.progressRefreshAll.setVisibility(android.view.View.VISIBLE);

        // Run sequentially on background thread
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            for (int i = 0; i < checkable.size(); i++) {
                if (!isRefreshRunning) break;
                final LocalLibrary lib = checkable.get(i);
                final int idx = i;
                final int total = checkable.size();

                // Find adapter position (only visible libs in adapter)
                final int adapterPos = adapter.getLocalLibraries().indexOf(lib);

                // Show spinner on this item (if visible)
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return; // stop safely if page closed
                    int pct = (int)((idx + 1) * 100f / total);
                    if (binding.progressRefreshAll.isIndeterminate()) {
                        binding.progressRefreshAll.setIndeterminate(false);
                        binding.progressRefreshAll.setMax(100);
                    }
                    binding.progressRefreshAll.setProgressCompat(pct, true);
                    // Counter in dedicated TextView (reliable unlike SearchBar subtitle)
                    String counter = (idx + 1) + " / " + total + "   " + lib.getName();
                    binding.tvUpdateCounter.setText(counter);
                    binding.mainToolbar.setSubtitle((idx + 1) + "/" + total);
                    if (adapterPos >= 0) adapter.setCheckingPosition(adapterPos);
                });

                // Synchronous resolve + check (we're already on bg thread)
                final String[] resolvedCoord = {lib.getDependency()};
                if (resolvedCoord[0] == null) {
                    resolvedCoord[0] = LibraryVersionChecker.resolveCoordinate(lib.getName());
                    if (resolvedCoord[0] != null) {
                        lib.setDependency(resolvedCoord[0]);
                        // Persist
                        String depPath = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                                + "/.sketchware/libs/local_libs/" + lib.getName() + "/dependency";
                        pro.sketchware.utility.FileUtil.writeFile(depPath, resolvedCoord[0]);
                    }
                }

                if (resolvedCoord[0] != null) {
                    // Synchronous version check
                    final String[] resultHolder = {null};
                    final Object lock = new Object();
                    LibraryVersionChecker.checkLatestVersionForce(resolvedCoord[0], (cur, latest) -> {
                        resultHolder[0] = latest;
                        synchronized (lock) { lock.notifyAll(); }
                    });
                    // Wait for result (max 8s)
                    synchronized (lock) {
                        try { lock.wait(8000); } catch (InterruptedException ignored) {}
                    }
                    final String latest = resultHolder[0];
                    runOnUiThread(() -> {
                        if (adapterPos >= 0) adapter.setCheckingPosition(-1);
                        if (latest != null) {
                            lib.setLatestVersion(latest);
                            if (adapterPos >= 0) adapter.notifyItemChanged(adapterPos);
                        }
                    });
                } else {
                    runOnUiThread(() -> { if (adapterPos >= 0) adapter.setCheckingPosition(-1); });
                }
            }

            // Done
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                isRefreshRunning = false;
                binding.mainToolbar.setSubtitle(null);
                binding.tvUpdateCounter.setVisibility(android.view.View.GONE);
                binding.progressRefreshAll.setIndeterminate(true);
                binding.progressRefreshAll.setVisibility(android.view.View.GONE);
                SketchwareUtil.toast("Update check complete");
            });
        });
    }

    /**
     * يُرجع اسم المكتبة للعرض بدون لاحقة الإصدار.
     * مثال: "volley_V_1.2.1" → "volley"
     *       "firebase-auth-23.2.0" → "firebase-auth"
     */
    /**
     * يعرض ديالوج لتنزيل مكتبة مفقودة عند تشغيل المشروع.
     * يُستدعى من LibraryRunValidator.
     */
    public static void promptMissingLibraryDownload(
            androidx.appcompat.app.AppCompatActivity activity,
            String libName,
            String dependencyCoord,
            LibraryDownloaderDialogFragment.OnLibraryDownloadedTask onDownloadComplete) {

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle("⚠️ مكتبة مفقودة")
                .setMessage("المكتبة \"" + libName + "\" مطلوبة لتشغيل المشروع ولكنها غير موجودة.\n\n"
                        + "هل تريد تنزيلها الآن؟"
                        + (dependencyCoord != null ? "\n\n" + dependencyCoord : ""))
                .setPositiveButton("⬇ تنزيل", (dialog, which) -> {
                    android.os.Bundle bundle = new android.os.Bundle();
                    bundle.putBoolean("notAssociatedWithProject", true);
                    bundle.putSerializable("buildSettings",
                            new mod.hey.studios.build.BuildSettings("system"));
                    bundle.putString("localLibFile",
                            pro.sketchware.utility.FileUtil.getExternalStorageDir()
                                    + "/.sketchware/data/system/local_library");
                    if (dependencyCoord != null)
                        bundle.putString("prefillDependency", dependencyCoord);

                    LibraryDownloaderDialogFragment fragment = new LibraryDownloaderDialogFragment();
                    fragment.setArguments(bundle);
                    if (onDownloadComplete != null)
                        fragment.setOnLibraryDownloadedTask(onDownloadComplete);
                    fragment.show(activity.getSupportFragmentManager(),
                            "missing_lib_downloader");
                })
                .setNegativeButton(mod.hey.studios.util.Helper.getResString(
                        pro.sketchware.R.string.common_word_cancel), null)
                .show();
    }

    private static String getDisplayName(String fullName) {
        // Keep the full name including version number (e.g. "retrofit-2.9.0")
        return fullName != null ? fullName : "";
    }

    public interface OnLocalLibrarySelectedStateChangedListener {
        void invoke(LocalLibrary library);
    }

    private static class LoadLocalLibrariesTask extends MA {
        private final WeakReference<ManageLocalLibraryActivity> activity;

        public LoadLocalLibrariesTask(ManageLocalLibraryActivity activity) {
            super(activity);
            this.activity = new WeakReference<>(activity);
            activity.addTask(this);
        }

        @Override
        public void a() {
            // UI thread: update RecyclerView THEN dismiss loading dialog
            ManageLocalLibraryActivity act = activity.get();
            if (act != null) {
                act.applySortAndPublish(true);
                act.h();
                // Version checking is MANUAL only — user taps 🔄 Refresh
            }
        }

        @Override
        public void a(String idk) {
            activity.get().h();
        }

        @Override
        public void b() {
            try {
                activity.get().loadLibraries();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ── Conflict Detector ────────────────────────────────────────────────────
    private void showConflictDetectorDialog() {
        androidx.appcompat.app.AlertDialog progress = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Scanning for conflicts…")
            .setMessage("Checking library package names for duplicates…")
            .setCancelable(false)
            .create();
        progress.show();
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            String localLibsRoot = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + "/.sketchware/libs/local_libs/";
            // Map: packageName → list of library names that declare it
            java.util.Map<String, java.util.List<String>> pkgMap = new java.util.LinkedHashMap<>();
            for (LocalLibrary lib : allLibraries) {
                java.io.File configFile = new java.io.File(localLibsRoot + lib.getName() + "/config");
                if (!configFile.exists()) continue;
                try {
                    String pkg = pro.sketchware.utility.FileUtil
                        .readFile(configFile.getAbsolutePath()).trim();
                    if (pkg.isEmpty()) continue;
                    pkgMap.computeIfAbsent(pkg, k -> new java.util.ArrayList<>()).add(lib.getName());
                } catch (Exception ignored) {}
            }
            // Also flag libraries with same dependency groupId:artifactId
            java.util.Map<String, java.util.List<String>> coordMap = new java.util.LinkedHashMap<>();
            for (LocalLibrary lib : allLibraries) {
                if (lib.getDependency() == null) continue;
                String[] parts = lib.getDependency().split(":");
                if (parts.length < 2) continue;
                String ga = parts[0] + ":" + parts[1];
                coordMap.computeIfAbsent(ga, k -> new java.util.ArrayList<>()).add(lib.getName());
            }
            StringBuilder sb = new StringBuilder();
            // Package conflicts
            boolean found = false;
            for (java.util.Map.Entry<String, java.util.List<String>> e : pkgMap.entrySet()) {
                if (e.getValue().size() < 2) continue;
                found = true;
                sb.append("📦 ").append(e.getKey()).append("\n");
                for (String name : e.getValue()) sb.append("   • ").append(name).append("\n");
                sb.append("\n");
            }
            // Version duplicates (same groupId:artifactId, different versions)
            for (java.util.Map.Entry<String, java.util.List<String>> e : coordMap.entrySet()) {
                if (e.getValue().size() < 2) continue;
                found = true;
                sb.append("⚠️ ").append(e.getKey()).append("\n");
                for (String name : e.getValue()) sb.append("   • ").append(name).append("\n");
                sb.append("\n");
            }
            final String result = found ? sb.toString().trim()
                : "✅ No conflicts found!\n\nAll library package names and Maven coordinates are unique.";
            runOnUiThread(() -> {
                progress.dismiss();
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Conflict Report")
                    .setMessage(result)
                    .setPositiveButton("OK", null)
                    .show();
            });
        });
    }

    // ── Library Details Bottom Sheet ──────────────────────────────────────────
    /** Returns map of sc_id → app name for all projects */
    private java.util.Map<String, String> buildProjectNameMap() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        java.io.File dataRoot = new java.io.File(
            pro.sketchware.utility.FileUtil.getExternalStorageDir(), ".sketchware/data");
        java.io.File[] dirs = dataRoot.listFiles(java.io.File::isDirectory);
        if (dirs == null) return map;
        for (java.io.File dir : dirs) {
            String id = dir.getName();
            try {
                java.util.HashMap<String, Object> meta = a.a.a.lC.b(id);
                String name = a.a.a.yB.c(meta, "my_app_name");
                if (name == null || name.isEmpty()) name = "#" + id;
                map.put(id, name);
            } catch (Exception ignored) { map.put(id, "#" + id); }
        }
        return map;
    }

    /** Returns list of sc_ids that use this library */
    private List<String> getProjectsUsingLib(String libName) {
        List<String> ids = new ArrayList<>();
        java.io.File dataRoot = new java.io.File(
            pro.sketchware.utility.FileUtil.getExternalStorageDir(), ".sketchware/data");
        java.io.File[] dirs = dataRoot.listFiles(java.io.File::isDirectory);
        if (dirs == null) return ids;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        for (java.io.File dir : dirs) {
            java.io.File lf = new java.io.File(dir, "local_library");
            if (!lf.exists()) continue;
            try {
                String content = pro.sketchware.utility.FileUtil.readFile(lf.getAbsolutePath());
                if (content == null || content.isEmpty()) continue;
                java.util.ArrayList<java.util.HashMap<String, Object>> libs =
                    gson.fromJson(content, mod.hey.studios.util.Helper.TYPE_MAP_LIST);
                for (java.util.HashMap<String, Object> entry : libs) {
                    Object n = entry.get("name");
                    if (n != null && libName.equals(n.toString())) {
                        ids.add(dir.getName());
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return ids;
    }

    private void showLibraryDetails(LocalLibrary lib) {
        String localLibsRoot = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/.sketchware/libs/local_libs/";
        StringBuilder sb = new StringBuilder();

        // Maven coordinate
        if (lib.getDependency() != null) {
            String[] p = lib.getDependency().split(":");
            sb.append("📦 Maven Coordinate\n")
              .append("   ").append(lib.getDependency()).append("\n");
            if (p.length >= 2) {
                sb.append("   Group:    ").append(p[0]).append("\n");
                sb.append("   Artifact: ").append(p[1]).append("\n");
                if (p.length >= 3) sb.append("   Version:  ").append(p[2]).append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("📦 No Maven coordinate stored\n\n");
        }

        // Size
        sb.append("💾 Size: ").append(lib.getSize()).append("\n\n");

        // Files present
        String[] fileNames = {"classes.jar","classes.dex","classes.aar",
                              "AndroidManifest.xml","res","proguard.txt","config"};
        StringBuilder filesSb = new StringBuilder();
        for (String f : fileNames) {
            java.io.File file = new java.io.File(localLibsRoot + lib.getName() + "/" + f);
            if (file.exists()) filesSb.append("   • ").append(f).append("\n");
        }
        if (filesSb.length() > 0) sb.append("📁 Files:\n").append(filesSb).append("\n");

        // ProGuard
        java.io.File pgFile = new java.io.File(localLibsRoot + lib.getName() + "/proguard.txt");
        if (pgFile.exists()) sb.append("🛡 Has ProGuard rules\n\n");

        // Update status
        if (lib.getLatestVersion() != null) {
            sb.append("🆕 Update available → v").append(lib.getLatestVersion()).append("\n\n");
        } else if (lib.isVersionChecked()) {
            sb.append("✅ Up to date\n\n");
        }

        // Linked projects — clickable button to show list
        int usageCount = lib.getUsageCount();
        sb.append("🔗 Used in ").append(usageCount)
          .append(usageCount == 1 ? " project" : " projects");
        if (usageCount > 0) sb.append(" (tap button below to see)");
        sb.append("\n");

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getDisplayName(lib.getName()))
                .setMessage(sb.toString())
                .setPositiveButton("OK", null);

        // Copy coord
        if (lib.getDependency() != null) {
            builder.setNeutralButton("Copy coord", (d, w) -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("coord", lib.getDependency()));
                pro.sketchware.utility.SketchwareUtil.toast("Copied!");
            });
        }

        // Show linked projects
        if (usageCount > 0) {
            builder.setNegativeButton("🔗 Show Projects", (d, w) ->
                showLinkedProjectsDialog(lib.getName()));
        }

        builder.show();
    }

    private void showLinkedProjectsDialog(String libName) {
        // Run on background thread — reading disk
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            List<String> projectIds = getProjectsUsingLib(libName);
            java.util.Map<String, String> nameMap = buildProjectNameMap();

            // Build display list: "#ID — AppName"
            String[] items = projectIds.stream()
                .map(id -> "#" + id + "  —  " + nameMap.getOrDefault(id, id))
                .toArray(String[]::new);

            runOnUiThread(() -> {
                if (items.length == 0) {
                    pro.sketchware.utility.SketchwareUtil.toast("No projects found using this library");
                    return;
                }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("🔗 Projects using " + getDisplayName(libName))
                    .setItems(items, (d, which) -> {
                        // Open project settings on tap
                        String scId = projectIds.get(which);
                        android.content.Intent intent = new android.content.Intent(
                            this, com.besome.sketch.projects.MyProjectSettingActivity.class);
                        intent.putExtra("sc_id", scId);
                        startActivity(intent);
                    })
                    .setPositiveButton("OK", null)
                    .show();
            });
        });
    }

    // ==================== Repository Management ====================

    private Runnable repoDialogRefresh;

    private static final String REPOSITORIES_JSON_PATH = Environment.getExternalStorageDirectory()
            .getAbsolutePath() + "/.sketchware/libs/repositories.json";

    private static final String[] BUILTIN_REPOS = {
            "Maven Central", "Google Maven", "Jitpack", "Sonatype Snapshots"
    };

    private ArrayList<HashMap<String, Object>> loadCustomRepos() {
        File file = new File(REPOSITORIES_JSON_PATH);
        if (!file.exists()) {
            seedDefaultRepos(file);
        }
        if (file.exists()) {
            try {
                ArrayList<HashMap<String, Object>> list = new Gson().fromJson(
                        pro.sketchware.utility.FileUtil.readFile(file.getAbsolutePath()),
                        new com.google.gson.reflect.TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
                if (list != null) return list;
            } catch (Exception e) {
                android.util.Log.e("ManageLocalLibrary", "Failed to parse repositories.json", e);
            }
        }
        return new ArrayList<>();
    }

    private void seedDefaultRepos(File file) {
        ArrayList<HashMap<String, Object>> defaults = new ArrayList<>();
        String[][] defaultEntries = {
                {"Atlassian", "https://maven.atlassian.com/content/repositories/atlassian-public"},
                {"JCenter", "https://jcenter.bintray.com"},
                {"Sonatype", "https://oss.sonatype.org/content/repositories/releases"},
                {"Spring Milestone", "https://repo.spring.io/libs-milestone"},
                {"Apache Maven", "https://repo.maven.apache.org/maven2"},
        };
        for (String[] entry : defaultEntries) {
            HashMap<String, Object> repo = new HashMap<>();
            repo.put("name", entry[0]);
            repo.put("url", entry[1]);
            defaults.add(repo);
        }
        file.getParentFile().mkdirs();
        pro.sketchware.utility.FileUtil.writeFile(file.getAbsolutePath(), new Gson().toJson(defaults));
    }

    private void saveCustomRepos(ArrayList<HashMap<String, Object>> repos) {
        File file = new File(REPOSITORIES_JSON_PATH);
        file.getParentFile().mkdirs();
        pro.sketchware.utility.FileUtil.writeFile(file.getAbsolutePath(), new Gson().toJson(repos));
    }

    private void showManageRepositoriesDialog() {
        android.view.View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_repositories, null);
        TextView builtinRepos = dialogView.findViewById(R.id.builtin_repos);
        LinearLayout customReposContainer = dialogView.findViewById(R.id.custom_repos_container);
        TextView emptyMessage = dialogView.findViewById(R.id.empty_message);
        ImageButton btnAdd = dialogView.findViewById(R.id.btn_add_repo);

        StringBuilder builtinText = new StringBuilder();
        for (String name : BUILTIN_REPOS) {
            if (builtinText.length() > 0) builtinText.append('\n');
            builtinText.append("• ").append(name);
        }
        builtinRepos.setText(builtinText);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(dialogView);

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_repo_title)
                .setView(scrollView)
                .setPositiveButton(R.string.common_word_ok, null)
                .create();

        Runnable refreshCustomRepos = () -> {
            customReposContainer.removeAllViews();
            ArrayList<HashMap<String, Object>> repos = loadCustomRepos();
            emptyMessage.setVisibility(repos.isEmpty() ? View.VISIBLE : View.GONE);
            for (int i = 0; i < repos.size(); i++) {
                final int index = i;
                HashMap<String, Object> repo = repos.get(i);
                String name = repo.get("name") instanceof String ? (String) repo.get("name") : "";
                String url = repo.get("url") instanceof String ? (String) repo.get("url") : "";

                android.view.View itemView = LayoutInflater.from(this).inflate(R.layout.item_repository, customReposContainer, false);
                ((TextView) itemView.findViewById(R.id.repo_name)).setText(name);
                ((TextView) itemView.findViewById(R.id.repo_url)).setText(url);
                itemView.findViewById(R.id.btn_edit).setOnClickListener(v ->
                        showAddEditRepoDialog(name, url, index));
                itemView.findViewById(R.id.btn_delete).setOnClickListener(v ->
                        showDeleteRepoDialog(name, index));
                customReposContainer.addView(itemView);
            }
        };
        refreshCustomRepos.run();
        repoDialogRefresh = refreshCustomRepos;

        btnAdd.setOnClickListener(v -> showAddEditRepoDialog("", "", -1));
        dialog.setOnDismissListener(d -> repoDialogRefresh = null);
        dialog.show();
    }

    private void refreshRepoDialog() {
        if (repoDialogRefresh != null) repoDialogRefresh.run();
    }

    private void showAddEditRepoDialog(String currentName, String currentUrl, int editIndex) {
        boolean isEdit = editIndex >= 0;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dp24 = (int) (24 * getResources().getDisplayMetrics().density);
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
        int dp8  = (int) (8  * getResources().getDisplayMetrics().density);
        layout.setPadding(dp24, dp16, dp24, 0);

        TextInputLayout nameLayout = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        nameLayout.setHint(getString(R.string.dialog_repo_name_hint));
        nameLayout.setPlaceholderText(getString(R.string.dialog_repo_name_placeholder));
        TextInputEditText nameInput = new TextInputEditText(nameLayout.getContext());
        nameInput.setText(currentName);
        nameInput.setSingleLine(true);
        nameLayout.addView(nameInput);

        TextInputLayout urlLayout = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        urlLayout.setHint(getString(R.string.dialog_repo_url_hint));
        urlLayout.setPlaceholderText(getString(R.string.dialog_repo_url_placeholder));
        TextInputEditText urlInput = new TextInputEditText(urlLayout.getContext());
        urlInput.setText(currentUrl);
        urlInput.setSingleLine(true);
        urlLayout.addView(urlInput);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp8;
        layout.addView(nameLayout, params);
        layout.addView(urlLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        androidx.appcompat.app.AlertDialog addEditDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? R.string.dialog_repo_edit_title : R.string.dialog_repo_add_title)
                .setView(layout)
                .setPositiveButton(R.string.common_word_save, null)
                .setNegativeButton(R.string.common_word_cancel, null)
                .create();
        addEditDialog.show();

        addEditDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            nameLayout.setError(null);
            urlLayout.setError(null);
            String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            String url  = urlInput.getText()  != null ? urlInput.getText().toString().trim()  : "";
            if (name.isEmpty()) { nameLayout.setError(getString(R.string.dialog_repo_error_name_required)); return; }
            if (url.isEmpty())  { urlLayout.setError(getString(R.string.dialog_repo_error_url_required));   return; }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                urlLayout.setError(getString(R.string.dialog_repo_error_url_invalid)); return;
            }
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            ArrayList<HashMap<String, Object>> repos = loadCustomRepos();
            HashMap<String, Object> entry = new HashMap<>();
            entry.put("name", name);
            entry.put("url", url);
            if (isEdit && editIndex < repos.size()) repos.set(editIndex, entry);
            else repos.add(entry);
            saveCustomRepos(repos);
            refreshRepoDialog();
            addEditDialog.dismiss();
        });
    }

    private void showDeleteRepoDialog(String repoName, int index) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setMessage(String.format(getString(R.string.dialog_repo_delete_confirm), repoName))
                .setPositiveButton(R.string.common_word_delete, (d, w) -> {
                    ArrayList<HashMap<String, Object>> repos = loadCustomRepos();
                    if (index < repos.size()) { repos.remove(index); saveCustomRepos(repos); refreshRepoDialog(); }
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showOrphanCleanupDialog() {
        List<LocalLibrary> all = allLibraries != null ? allLibraries : new ArrayList<>();
        List<String> usedNames = new ArrayList<>();
        for (Map<String, Object> m : projectUsedLibs) {
            Object n = m.get("name");
            if (n instanceof String) usedNames.add((String) n);
        }
        List<LocalLibrary> orphans = new ArrayList<>();
        for (LocalLibrary lib : all) {
            if (!usedNames.contains(lib.getName())) orphans.add(lib);
        }
        if (orphans.isEmpty()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_orphan_libs_title)
                    .setMessage(R.string.dialog_orphan_libs_none)
                    .setPositiveButton(R.string.common_word_ok, null)
                    .show();
            return;
        }
        StringBuilder names = new StringBuilder();
        for (LocalLibrary lib : orphans) names.append(lib.getName()).append('\n');
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_orphan_libs_title)
                .setMessage(getString(R.string.dialog_orphan_libs_body) + "\n\n" + names.toString().trim())
                .setPositiveButton(R.string.common_word_delete, (d, w) -> {
                    k();
                    backgroundExecutor.execute(() -> {
                        deleteSelectedLocalLibraries(scId, new ArrayList<>(orphans), projectUsedLibs);
                        runOnUiThread(() -> {
                            if (isDestroyed() || isFinishing()) return;
                            h();
                            runLoadLocalLibrariesTask();
                            SketchwareUtil.toast("Orphaned libraries removed");
                        });
                    });
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    public class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.ViewHolder> {

        private final List<LocalLibrary> localLibraries = new ArrayList<>();
        public boolean isSelectionModeEnabled;
        private int checkingPosition = -1; // index being checked by sequential refresh
        private @Nullable OnLocalLibrarySelectedStateChangedListener onLocalLibrarySelectedStateChangedListener;

        public void setCheckingPosition(int position) {
            int old = checkingPosition;
            checkingPosition = position;
            if (old >= 0 && old < localLibraries.size()) notifyItemChanged(old);
            if (position >= 0 && position < localLibraries.size()) notifyItemChanged(position);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ViewItemLocalLibBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            var binding = holder.binding;
            var library = localLibraries.get(position);

            binding.libraryName.setText(getDisplayName(library.getName()));
            binding.librarySize.setText(library.getSize());
            // Show usage badge — tapping it opens the linked projects list
            if (library.getUsageCount() > 0) {
                String badge = library.getSize()
                    + "  ·  🔗 " + library.getUsageCount()
                    + (library.getUsageCount() == 1 ? " project" : " projects");
                binding.librarySize.setText(badge);
                // ← tap "X projects" to see which ones
                binding.librarySize.setOnClickListener(v -> {
                    if (!isSelectionModeEnabled)
                        showLinkedProjectsDialog(library.getName());
                });
            } else {
                binding.librarySize.setOnClickListener(null);
            }
            binding.libraryName.setSelected(true);
            bindSelectedState(binding.card, library);

            binding.card.setOnClickListener(v -> {
                if (isSelectionModeEnabled) {
                    toggleLocalLibrary(binding.card, library, onLocalLibrarySelectedStateChangedListener);
                } else if (!notAssociatedWithProject) {
                    binding.materialSwitch.performClick();
                }
            });

            binding.card.setOnLongClickListener(v -> {
                if (isSelectionModeEnabled) {
                    return false;
                }

                isSelectionModeEnabled = true;
                toggleLocalLibrary(binding.card, library, onLocalLibrarySelectedStateChangedListener);
                return true;
            });

            binding.materialSwitch.setChecked(false);
            if (!notAssociatedWithProject) {

                binding.materialSwitch.setOnClickListener(v -> onItemClicked(binding, library.getName()));

                for (Map<String, Object> libraryMap : projectUsedLibs) {
                    if (library.getName().equals(Objects.requireNonNull(libraryMap.get("name")).toString())) {
                        binding.materialSwitch.setChecked(true);
                    }
                }
            } else {
                binding.materialSwitch.setEnabled(false);
            }

            // ── Version row (always visible when we have a dep or folder-version) ─
            String currentVersion = library.getCurrentVersion();
            if (currentVersion != null) {
                binding.versionRow.setVisibility(android.view.View.VISIBLE);
                binding.tvCurrentVersion.setText("v" + currentVersion);

                // Show cached update badge immediately
                String cached = library.getLatestVersion();
                if (cached != null) {
                    binding.tvLatestVersion.setText("→ v" + cached);
                    binding.tvLatestVersion.setVisibility(android.view.View.VISIBLE);
                    binding.btnUpdateLib.setVisibility(android.view.View.VISIBLE);
                    binding.btnUpdateLib.setOnClickListener(v ->
                            onUpdateLibraryClicked(library, cached));
                } else {
                    binding.tvLatestVersion.setVisibility(android.view.View.GONE);
                    binding.btnUpdateLib.setVisibility(android.view.View.GONE);

                    // Auto-fetch on first bind if we have a dependency coordinate
                    if (library.getDependency() != null && !library.isVersionChecked()) {
                        library.markVersionChecked();
                        LibraryVersionChecker.checkLatestVersion(library.getDependency(),
                                (cur, latest) -> {
                                    if (latest != null) {
                                        library.setLatestVersion(latest);
                                        // Use post() not runOnUiThread() — safe during RecyclerView layout
                                        binding.getRoot().post(() -> {
                                            int idx = localLibraries.indexOf(library);
                                            if (idx >= 0) notifyItemChanged(idx);
                                        });
                                    }
                                });
                    }
                }
            } else {
                // No version info: show empty version row (refresh button still appears)
                binding.versionRow.setVisibility(android.view.View.GONE);
                binding.tvCurrentVersion.setText("");
                binding.tvLatestVersion.setVisibility(android.view.View.GONE);
                binding.btnUpdateLib.setVisibility(android.view.View.GONE);
            }

            // ── Sequential-check spinner ────────────────────────────────────
            if (position == checkingPosition) {
                // Show animated spinner instead of static icon
                binding.btnRefreshLib.setImageResource(android.R.drawable.ic_popup_sync);
                android.view.animation.RotateAnimation spin = new android.view.animation.RotateAnimation(
                        0f, 360f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
                spin.setDuration(700);
                spin.setRepeatCount(android.view.animation.Animation.INFINITE);
                spin.setInterpolator(new android.view.animation.LinearInterpolator());
                binding.btnRefreshLib.startAnimation(spin);
                binding.btnRefreshLib.setEnabled(false);
            } else {
                binding.btnRefreshLib.clearAnimation();
                binding.btnRefreshLib.setImageResource(pro.sketchware.R.drawable.ic_refresh);
                binding.btnRefreshLib.setEnabled(true);
            }

            // ── Per-library refresh button ───────────────────────────────────
            binding.btnRefreshLib.setOnClickListener(v -> {
                if (library.getDependency() == null) {
                    pro.sketchware.utility.SketchwareUtil.toast("No Maven dependency configured for this library");
                    return;
                }
                binding.btnRefreshLib.setEnabled(false);
                LibraryVersionChecker.checkLatestVersionForce(library.getDependency(),
                        (cur, latest) -> binding.getRoot().post(() -> {
                            binding.btnRefreshLib.setEnabled(true);
                            if (latest != null) {
                                library.setLatestVersion(latest);
                                int idx = localLibraries.indexOf(library);
                                if (idx >= 0) notifyItemChanged(idx);
                            } else {
                                pro.sketchware.utility.SketchwareUtil.toast("Already up to date or check failed");
                            }
                        }));
            });
            // ── Long-press refresh button → show Details + Projects popup ───
            binding.btnRefreshLib.setOnLongClickListener(v -> {
                String[] options = {"📋 Details & Files", "🔗 Linked Projects"};
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        ManageLocalLibraryActivity.this)
                    .setTitle(getDisplayName(library.getName()))
                    .setItems(options, (d, which) -> {
                        if (which == 0) showLibraryDetails(library);
                        else            showLinkedProjectsDialog(library.getName());
                    })
                    .show();
                return true;
            });
        }

        /**
         * Updates the library folder name's version reference in every project
         * that currently uses this library, then updates the in-memory list.
         */
        private void onUpdateLibraryClicked(LocalLibrary library, String newVersion) {
            String dep = library.getDependency();
            if (dep == null) return;
            String[] parts = dep.split(":");
            if (parts.length < 3) return;

            String newCoord  = parts[0] + ":" + parts[1] + ":" + newVersion;
            String oldCoord  = dep;
            String oldName   = library.getName();
            String libsRoot  = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + "/.sketchware/libs/local_libs/";

            // ── الخطوة 1: اختيار نطاق التحديث ──────────────────────────────
            String[] scopeOptions = notAssociatedWithProject
                    ? new String[]{"تحديث لكل المشاريع"}
                    : new String[]{"تحديث للمشروع الحالي فقط", "تحديث لكل المشاريع"};

            final int[] chosenScope = {notAssociatedWithProject ? 1 : 0}; // 0=current, 1=all

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(ManageLocalLibraryActivity.this)
                    .setTitle("🔄 تحديث: " + getDisplayName(parts[1]))
                    .setMessage("الإصدار الحالي: v" + library.getCurrentVersion()
                            + "\nالإصدار الجديد: v" + newVersion)
                    .setSingleChoiceItems(scopeOptions, chosenScope[0],
                            (d, which) -> chosenScope[0] = which)
                    .setPositiveButton("⬇ إعادة تنزيل (موصى به)", (dialog, which) -> {
                        boolean updateAll = (notAssociatedWithProject || chosenScope[0] == 1);
                        android.os.Bundle bundle = new android.os.Bundle();
                        bundle.putBoolean("notAssociatedWithProject", notAssociatedWithProject);
                        bundle.putSerializable("buildSettings", buildSettings);
                        bundle.putString("localLibFile", getLocalLibFile(scId).getAbsolutePath());
                        bundle.putString("prefillDependency", newCoord);
                        bundle.putString("oldLibraryName", oldName);
                        bundle.putString("oldDependencyCoord", oldCoord);
                        bundle.putBoolean("updateAllProjects", updateAll);
                        LibraryDownloaderDialogFragment fragment = new LibraryDownloaderDialogFragment();
                        fragment.setArguments(bundle);
                        fragment.setOnLibraryDownloadedTask(ManageLocalLibraryActivity.this::runLoadLocalLibrariesTask);
                        fragment.show(getSupportFragmentManager(), "library_downloader_dialog");
                    })
                    .setNeutralButton("✏ تحديث البيانات فقط", (dialog, which) -> {
                        boolean updateAll = (notAssociatedWithProject || chosenScope[0] == 1);
                        String newName = deriveNewLibraryName(oldName,
                                library.getCurrentVersion(), newVersion);
                        if (newName != null && !newName.equals(oldName)) {
                            java.io.File oldDir = new java.io.File(libsRoot + oldName);
                            java.io.File newDir = new java.io.File(libsRoot + newName);
                            if (oldDir.exists() && !newDir.exists()) {
                                oldDir.renameTo(newDir);
                                pro.sketchware.utility.FileUtil.writeFile(
                                        libsRoot + newName + "/dependency", newCoord);
                                if (updateAll) updateLibraryInAllProjects(oldName, newName, oldCoord, newCoord);
                                else updateDependencyInAllProjects(oldName, oldCoord, newCoord);
                                pro.sketchware.utility.SketchwareUtil.toast(
                                        "✅ تم إعادة تسمية → " + newName);
                                runLoadLocalLibrariesTask();
                                return;
                            }
                        }
                        library.setDependency(newCoord);
                        library.setLatestVersion(null);
                        pro.sketchware.utility.FileUtil.writeFile(
                                libsRoot + oldName + "/dependency", newCoord);
                        if (updateAll) updateDependencyInAllProjects(oldName, oldCoord, newCoord);
                        int idx = localLibraries.indexOf(library);
                        if (idx >= 0) notifyItemChanged(idx);
                        pro.sketchware.utility.SketchwareUtil.toast(
                                "✅ تم تحديث الإصدار إلى v" + newVersion);
                    })
                    .setNegativeButton(mod.hey.studios.util.Helper.getResString(
                            pro.sketchware.R.string.common_word_cancel), null)
                    .show();
        }

        @Override
        public int getItemCount() {
            return localLibraries.isEmpty() ? 0 : localLibraries.size();
        }

        public void setOnLocalLibrarySelectedStateChangedListener(
                @Nullable OnLocalLibrarySelectedStateChangedListener onLocalLibrarySelectedStateChangedListener) {
            this.onLocalLibrarySelectedStateChangedListener = onLocalLibrarySelectedStateChangedListener;
        }

        private void toggleLocalLibrary(MaterialCardView card, LocalLibrary library,
                                        @Nullable OnLocalLibrarySelectedStateChangedListener onLocalLibrarySelectedStateChangedListener) {
            library.setSelected(!library.isSelected());
            bindSelectedState(card, library);
            if (onLocalLibrarySelectedStateChangedListener != null) {
                onLocalLibrarySelectedStateChangedListener.invoke(library);
            }
            if (library.isSelected() && isUsedLibrary(library.getName())) {
                new MaterialAlertDialogBuilder(ManageLocalLibraryActivity.this)
                        .setTitle("Warning")
                        .setMessage("This library \"" + library.getName() + "\" already used in your project, removing it may break your project\rDo you want to continue removing it?")
                        .setPositiveButton(Helper.getResString(R.string.common_word_yes), (dialog, which) -> dialog.dismiss())
                        .setNegativeButton(Helper.getResString(R.string.common_word_cancel), (dialog, which) -> {
                            toggleLocalLibrary(card, library, onLocalLibrarySelectedStateChangedListener);
                            dialog.dismiss();
                        })
                        .show();
            }
        }

        private void bindSelectedState(MaterialCardView card, LocalLibrary library) {
            card.setChecked(library.isSelected());
        }

        private void onItemClicked(ViewItemLocalLibBinding binding, String name) {
            HashMap<String, Object> localLibrary;
            if (!binding.materialSwitch.isChecked()) {
                // Remove the library from the list
                int indexToRemove = -1;
                for (int i = 0; i < projectUsedLibs.size(); i++) {
                    Map<String, Object> libraryMap = projectUsedLibs.get(i);
                    if (name.equals(Objects.requireNonNull(libraryMap.get("name")).toString())) {
                        indexToRemove = i;
                        break;
                    }
                }
                if (indexToRemove != -1) {
                    projectUsedLibs.remove(indexToRemove);
                }
            } else {
                // Add the library to the list
                // Here, we need to find the dependency string if it exists
                String dependency = null;
                for (Map<String, Object> libraryMap : projectUsedLibs) {
                    if (name.equals(Objects.requireNonNull(libraryMap.get("name")).toString())) {
                        dependency = (String) libraryMap.get("dependency");
                        break;
                    }
                }
                localLibrary = createLibraryMap(name, dependency);
                projectUsedLibs.add(localLibrary);
            }
            rewriteLocalLibFile(scId, new Gson().toJson(projectUsedLibs));
        }

        public List<LocalLibrary> getLocalLibraries() {
            return localLibraries;
        }

        public void setLocalLibraries(List<LocalLibrary> newList) {
            List<LocalLibrary> oldList = new ArrayList<>(localLibraries);
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return oldList.size(); }
                @Override public int getNewListSize() { return newList.size(); }
                @Override public boolean areItemsTheSame(int op, int np) {
                    return oldList.get(op).getName().equals(newList.get(np).getName());
                }
                @Override public boolean areContentsTheSame(int op, int np) {
                    LocalLibrary o = oldList.get(op), n = newList.get(np);
                    return o.isSelected() == n.isSelected()
                            && Objects.equals(o.getSize(), n.getSize())
                            && Objects.equals(o.getLatestVersion(), n.getLatestVersion());
                }
            });
            localLibraries.clear();
            localLibraries.addAll(newList);
            diff.dispatchUpdatesTo(this);
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            private final ViewItemLocalLibBinding binding;

            public ViewHolder(@NonNull ViewItemLocalLibBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private final List<LocalLibrary> filteredLocalLibraries = new ArrayList<>();

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            var binding = ViewItemLocalLibSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            var binding = holder.binding;
            var library = filteredLocalLibraries.get(position);

            binding.libraryName.setText(getDisplayName(library.getName()));
            binding.librarySize.setText(library.getSize());
            // Show usage badge
            if (library.getUsageCount() > 0) {
                binding.librarySize.setText(library.getSize()
                    + "  ·  " + library.getUsageCount()
                    + (library.getUsageCount() == 1 ? " project" : " projects"));
            }
            binding.libraryName.setSelected(true);

            binding.materialSwitch.setChecked(false);
            if (!notAssociatedWithProject) {

                binding.getRoot().setOnClickListener(v -> binding.materialSwitch.performClick());

                binding.materialSwitch.setOnClickListener(v -> {
                    onItemClicked(binding, library.getName());
                    adapter.notifyItemChanged(position);
                });

                for (Map<String, Object> libraryMap : projectUsedLibs) {
                    if (library.getName().equals(Objects.requireNonNull(libraryMap.get("name")).toString())) {
                        binding.materialSwitch.setChecked(true);
                    }
                }
            } else {
                binding.materialSwitch.setEnabled(false);
            }
        }

        @Override
        public int getItemCount() {
            return filteredLocalLibraries.isEmpty() ? 0 : filteredLocalLibraries.size();
        }

        private void onItemClicked(ViewItemLocalLibSearchBinding binding, String name) {
            HashMap<String, Object> localLibrary;
            if (!binding.materialSwitch.isChecked()) {
                // Remove the library from the list
                int indexToRemove = -1;
                for (int i = 0; i < projectUsedLibs.size(); i++) {
                    Map<String, Object> libraryMap = projectUsedLibs.get(i);
                    if (name.equals(Objects.requireNonNull(libraryMap.get("name")).toString())) {
                        indexToRemove = i;
                        break;
                    }
                }
                if (indexToRemove != -1) {
                    projectUsedLibs.remove(indexToRemove);
                }
            } else {
                // Add the library to the list
                // Here, we need to find the dependency string if it exists
                String dependency = null;
                for (Map<String, Object> libraryMap : projectUsedLibs) {
                    if (name.equals(Objects.requireNonNull(libraryMap.get("name")).toString())) {
                        dependency = (String) libraryMap.get("dependency");
                        break;
                    }
                }
                localLibrary = createLibraryMap(name, dependency);
                projectUsedLibs.add(localLibrary);
            }
            rewriteLocalLibFile(scId, new Gson().toJson(projectUsedLibs));
        }

        public void filter(List<LocalLibrary> localLibraries, String query) {
            List<LocalLibrary> newFiltered = new ArrayList<>();
            if (query.isEmpty()) {
                newFiltered.addAll(localLibraries);
            } else {
                String lq = query.toLowerCase();
                for (LocalLibrary library : localLibraries) {
                    if (library.getName().toLowerCase().contains(lq)) {
                        newFiltered.add(library);
                    }
                }
            }
            newFiltered.sort((lib1, lib2) -> Boolean.compare(
                    isUsedLibrary(lib2.getName()), isUsedLibrary(lib1.getName())));

            List<LocalLibrary> oldFiltered = new ArrayList<>(filteredLocalLibraries);
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return oldFiltered.size(); }
                @Override public int getNewListSize() { return newFiltered.size(); }
                @Override public boolean areItemsTheSame(int op, int np) {
                    return oldFiltered.get(op).getName().equals(newFiltered.get(np).getName());
                }
                @Override public boolean areContentsTheSame(int op, int np) {
                    return oldFiltered.get(op).getName().equals(newFiltered.get(np).getName());
                }
            });
            filteredLocalLibraries.clear();
            filteredLocalLibraries.addAll(newFiltered);
            diff.dispatchUpdatesTo(this);
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            private final ViewItemLocalLibSearchBinding binding;

            public ViewHolder(@NonNull ViewItemLocalLibSearchBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}