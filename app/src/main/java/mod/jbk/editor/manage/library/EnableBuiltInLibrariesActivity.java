package mod.jbk.editor.manage.library;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import a.a.a.MA;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.util.LogUtil;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class EnableBuiltInLibrariesActivity extends BaseAppCompatActivity {
    private static final String TAG = "EnableBuiltInLibraries";

    private View root;
    private androidx.appcompat.widget.Toolbar toolbar;
    private LinearLayout content;
    private View actualContent;
    private View noContent;
    private TextView tvEnable;
    private TextView tvTitle;
    private TextView itemDesc;
    private MaterialSwitch libSwitch;
    private View layoutSwitchCard;
    private ExtendedFloatingActionButton selectLibrariesButton;

    private String sc_id;
    private boolean isEnablingEnabled;
    private List<BuiltInLibraries.BuiltInLibrary> enabledLibraries;
    private Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> config;

    private static File getConfigPath(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware" + File.separator + "data" + File.separator + sc_id + File.separator + "enabled_library");
    }

    private static void saveConfig(String sc_id, boolean isEnablingEnabled, List<BuiltInLibraries.BuiltInLibrary> enabledLibraries) {
        List<String> enabledLibraryNames = enabledLibraries.stream()
                .map(BuiltInLibraries.BuiltInLibrary::getName)
                .collect(Collectors.toList());
        Pair<Boolean, List<String>> config = new Pair<>(isEnablingEnabled, enabledLibraryNames);
        FileUtil.writeFile(getConfigPath(sc_id).getAbsolutePath(), new Gson().toJson(config));
    }

    @Nullable
    private static Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> readConfig(String sc_id) {
        File configPath = getConfigPath(sc_id);
        if (configPath.isFile()) {
            String content = FileUtil.readFile(configPath.getAbsolutePath());
            String errorMessage;
            try {
                Pair<Boolean, List<String>> config = new Gson().fromJson(content, new TypeToken<>() {
                });
                if (config != null) {
                    List<BuiltInLibraries.BuiltInLibrary> libraries = config.second.stream()
                            .map(s -> BuiltInLibraries.BuiltInLibrary.ofName(s).orElse(null))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    return new Pair<>(config.first, libraries);
                }
                errorMessage = "read config was null";
            } catch (Exception e) {
                errorMessage = Log.getStackTraceString(e);
            }
            LogUtil.e(TAG, "Couldn't parse config: " + errorMessage);
        }
        return null;
    }

    @NonNull
    public static Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> readConfigCompat(String sc_id) {
        Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> config = readConfig(sc_id);
        if (config != null) {
            return config;
        }
        return new Pair<>(false, Collections.emptyList());
    }

    /** Enables a built-in library by name (e.g. "gson-2.13.1") for the given project. No-op if already enabled. */
    public static void enableBuiltInLibrary(String sc_id, String libraryName) {
        BuiltInLibraries.BuiltInLibrary.ofName(libraryName).ifPresent(lib -> {
            Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> existing = readConfigCompat(sc_id);
            List<BuiltInLibraries.BuiltInLibrary> libs = new java.util.ArrayList<>(existing.second);
            if (!libs.contains(lib)) {
                libs.add(lib);
                saveConfig(sc_id, true, libs);
            }
        });
    }

    public static boolean isEnablingEnabled(String sc_id) {
        Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> config = readConfig(sc_id);
        return config != null && config.first;
    }

    @NonNull
    public static List<BuiltInLibraries.BuiltInLibrary> getEnabledLibraries(String sc_id) {
        Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> config = readConfig(sc_id);
        if (config != null && config.first) {
            return config.second;
        }
        return Collections.emptyList();
    }

    @DrawableRes
    public static int getItemIcon() {
        return R.drawable.ic_mtrl_design;
    }

    public static String getItemTitle() {
        return "Enable built-in libraries";
    }

    public static String getDefaultItemDescription() {
        return "Manually include built-in dependencies";
    }

    public static String getSelectedLibrariesItemDescription() {
        return "%1$d built-in libraries enabled";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isStoragePermissionGranted()) {
            finish();
            return;
        }

        setContentView(R.layout.manage_library_exclude_builtin_libraries);
        root = findViewById(android.R.id.content);
        toolbar = findViewById(R.id.toolbar);
        content = findViewById(R.id.content);
        actualContent = findViewById(R.id.actual_content);
        noContent = findViewById(R.id.no_content);
        tvEnable = findViewById(R.id.tv_enable);
        tvTitle = findViewById(R.id.tv_title);
        itemDesc = findViewById(R.id.item_desc);
        libSwitch = findViewById(R.id.lib_switch);
        layoutSwitchCard = findViewById(R.id.layout_switch_card);
        selectLibrariesButton = findViewById(R.id.exclude_library);

        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getItemTitle());
        toolbar.setNavigationOnClickListener(view -> onBackPressed());

        tvEnable.setText(Helper.getResString(R.string.design_library_settings_title_enabled));
        tvTitle.setText("Manually enabled built-in libraries");
        selectLibrariesButton.setText("Enable library");

        if (noContent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) noContent;
            if (group.getChildCount() > 0 && group.getChildAt(0) instanceof TextView) {
                ((TextView) group.getChildAt(0)).setText("No manually enabled built-in libraries");
            }
            if (group.getChildCount() > 1 && group.getChildAt(1) instanceof TextView) {
                ((TextView) group.getChildAt(1)).setText("Enable this feature and select the built-in libraries you want to force into this project even when blocks or components do not auto-enable them.");
            }
        }

        selectLibrariesButton.setOnClickListener(v -> showSelectBuiltInLibrariesDialog());
        layoutSwitchCard.setOnClickListener(v -> libSwitch.setChecked(!libSwitch.isChecked()));
        libSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isEnablingEnabled = isChecked;
            refresh();
        });
        config = readConfig(sc_id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Reset")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.history_24px))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        if ("Reset".contentEquals(menuItem.getTitle())) {
            showResetDialog();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        if (config != null && config.first.equals(isEnablingEnabled) && config.second.equals(enabledLibraries)) {
            super.onBackPressed();
            return;
        }

        String validationError = validateSelection(isEnablingEnabled, enabledLibraries);
        if (validationError != null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Can't save built-in library configuration")
                    .setMessage(validationError)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        k();
        try {
            new SaveConfigTask(this).schedule(500L);
        } catch (Exception e) {
            onSaveError(e);
        }
    }

    private String validateSelection(boolean enable, List<BuiltInLibraries.BuiltInLibrary> selectedLibraries) {
        if (!enable) {
            return null;
        }

        LinkedHashSet<String> selected = selectedLibraries.stream()
                .map(BuiltInLibraries.BuiltInLibrary::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> excluded = ExcludeBuiltInLibrariesActivity.getExcludedLibraries(sc_id).stream()
                .map(BuiltInLibraries.BuiltInLibrary::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        ArrayList<String> issues = new ArrayList<>();
        for (String libraryName : selected) {
            if (excluded.contains(libraryName)) {
                issues.add("'" + libraryName + "' is excluded in Exclude built-in libraries. Remove it from exclusions or disable it here.");
                continue;
            }
            Optional<BuiltInLibraries.BuiltInLibrary> library = BuiltInLibraries.BuiltInLibrary.ofName(libraryName);
            if (library.isPresent()) {
                for (String dependency : library.get().getDependencyNames()) {
                    if (excluded.contains(dependency)) {
                        issues.add("'" + libraryName + "' depends on excluded library '" + dependency + "'. Remove the exclusion first.");
                    }
                }
            }
        }
        return issues.isEmpty() ? null : String.join("\n\n", issues);
    }

    private void onSaveError(Throwable throwable) {
        String errorMessage = "Couldn't save configuration: " + throwable.getMessage();
        LogUtil.e(TAG, errorMessage, throwable);
        onSaveError(errorMessage);
    }

    private void onSaveError(String errorMessage) {
        SketchwareUtil.toastError(errorMessage);
        h();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("sc_id", sc_id);
        outState.putBoolean("isEnablingEnabled", isEnablingEnabled);
        outState.putParcelableArrayList("enabledLibraryNames", new ArrayList<>(enabledLibraries));
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (savedInstanceState == null) {
            isEnablingEnabled = isEnablingEnabled(sc_id);
            enabledLibraries = getEnabledLibraries(sc_id);
        } else {
            isEnablingEnabled = savedInstanceState.getBoolean("isEnablingEnabled");
            enabledLibraries = savedInstanceState.getParcelableArrayList("enabledLibraryNames");
        }
        refresh();
    }

    private void refresh() {
        libSwitch.setChecked(isEnablingEnabled);
        if (isEnablingEnabled) {
            selectLibrariesButton.show();
        } else {
            selectLibrariesButton.hide();
        }

        String libraries = enabledLibraries.stream()
                .map(BuiltInLibraries.BuiltInLibrary::getName)
                .collect(Collectors.joining(", "));
        libraries = isEnablingEnabled ? libraries : "";

        MaterialFadeThrough transition = new MaterialFadeThrough();
        TransitionManager.beginDelayedTransition(content, transition);

        actualContent.setVisibility(libraries.isEmpty() ? View.GONE : View.VISIBLE);
        noContent.setVisibility(libraries.isEmpty() ? View.VISIBLE : View.GONE);
        itemDesc.setText(libraries);
    }

    private void showResetDialog() {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.rollback_96)
                .setTitle(Helper.getResString(R.string.common_word_reset))
                .setMessage("Reset manually enabled built-in libraries? This action cannot be undone.")
                .setPositiveButton(Helper.getResString(R.string.common_word_reset), (v, which) -> {
                    saveConfig(sc_id, false, Collections.emptyList());
                    libSwitch.setChecked(false);
                    enabledLibraries = Collections.emptyList();
                    refresh();
                    v.dismiss();
                })
                .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                .show();
    }

    private void showSelectBuiltInLibrariesDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_libraries, null, false);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerView);
        com.google.android.material.textfield.TextInputEditText searchInput = dialogView.findViewById(R.id.searchInput);

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle("Select built-in libraries");

        TypedArray typedArray = obtainStyledAttributes(null, new int[0]);
        try {
            Method method = View.class.getDeclaredMethod("initializeScrollbars", TypedArray.class);
            method.setAccessible(true);
            method.invoke(recyclerView, typedArray);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            LogUtil.e(TAG, "Couldn't add scrollbars to RecyclerView", e);
        }
        typedArray.recycle();
        recyclerView.setVerticalScrollBarEnabled(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        BuiltInLibraryAdapter adapter = new BuiltInLibraryAdapter(enabledLibraries);
        adapter.setHasStableIds(true);
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.setView(dialogView);
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            enabledLibraries = adapter.getSelectedBuiltInLibraries();
            v.dismiss();
            refresh();
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private static class SaveConfigTask extends MA {
        private final WeakReference<EnableBuiltInLibrariesActivity> activity;

        public SaveConfigTask(EnableBuiltInLibrariesActivity activity) {
            super(activity);
            this.activity = new WeakReference<>(activity);
            activity.a(this);
        }

        @Override
        public void a() {
            EnableBuiltInLibrariesActivity target = activity.get();
            if (target == null) return;
            target.h();
            target.setResult(RESULT_OK);
            target.finish();
        }

        @Override
        public void a(String s) {
            EnableBuiltInLibrariesActivity target = activity.get();
            if (target != null) {
                target.onSaveError("Couldn't save configuration: " + s);
            }
        }

        @Override
        public void b() {
            EnableBuiltInLibrariesActivity target = activity.get();
            if (target != null) {
                saveConfig(target.sc_id, target.isEnablingEnabled, target.enabledLibraries);
            }
        }
    }

    private static class BuiltInLibraryAdapter extends RecyclerView.Adapter<BuiltInLibraryAdapter.ViewHolder> {
        private final List<BuiltInLibraries.BuiltInLibrary> libraries;
        private final Map<Integer, Void> checkedIndices;
        private List<BuiltInLibraries.BuiltInLibrary> filteredLibraries;

        public BuiltInLibraryAdapter(List<BuiltInLibraries.BuiltInLibrary> enabledLibraries) {
            libraries = Arrays.asList(BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES);
            libraries.sort(Comparator.comparing(BuiltInLibraries.BuiltInLibrary::getName, String.CASE_INSENSITIVE_ORDER));
            filteredLibraries = new ArrayList<>(libraries);
            checkedIndices = new HashMap<>();
            for (BuiltInLibraries.BuiltInLibrary enabledLibrary : enabledLibraries) {
                int index = libraries.indexOf(enabledLibrary);
                if (index >= 0) {
                    checkedIndices.put(index, null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return filteredLibraries.size();
        }

        @Override
        public long getItemId(int position) {
            return libraries.indexOf(filteredLibraries.get(position));
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.manage_library_exclude_builtin_libraries_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BuiltInLibraries.BuiltInLibrary library = filteredLibraries.get(position);
            int originalPosition = libraries.indexOf(library);
            holder.selected.setChecked(checkedIndices.containsKey(originalPosition));
            holder.name.setText(library.getName());
            Optional<String> packageName = library.getPackageName();
            if (packageName.isPresent()) {
                holder.packageName.setVisibility(View.VISIBLE);
                holder.packageName.setText(packageName.get());
            } else {
                holder.packageName.setVisibility(View.GONE);
            }
            View.OnClickListener selectingListener = v -> {
                CheckBox selected = holder.selected;
                if (v.getId() != R.id.chk_select) {
                    selected.setChecked(!selected.isChecked());
                }
                if (selected.isChecked()) {
                    checkedIndices.put(originalPosition, null);
                } else {
                    checkedIndices.remove(originalPosition);
                }
            };
            holder.selected.setOnClickListener(selectingListener);
            holder.selectableItem.setOnClickListener(selectingListener);
        }

        public List<BuiltInLibraries.BuiltInLibrary> getSelectedBuiltInLibraries() {
            Set<Integer> keySet = checkedIndices.keySet();
            List<BuiltInLibraries.BuiltInLibrary> selectedLibraries = new ArrayList<>(keySet.size());
            for (int i : keySet) {
                selectedLibraries.add(libraries.get(i));
            }
            return selectedLibraries;
        }

        public void filter(String query) {
            String lowerQuery = query.toLowerCase();
            List<BuiltInLibraries.BuiltInLibrary> newFiltered = new ArrayList<>();
            for (BuiltInLibraries.BuiltInLibrary library : libraries) {
                if (library.getName().toLowerCase().contains(lowerQuery) || library.getPackageName().orElse("").toLowerCase().contains(lowerQuery)) {
                    newFiltered.add(library);
                }
            }
            List<BuiltInLibraries.BuiltInLibrary> oldFiltered = filteredLibraries;
            filteredLibraries = newFiltered;
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return oldFiltered.size(); }
                @Override public int getNewListSize() { return newFiltered.size(); }
                @Override public boolean areItemsTheSame(int op, int np) {
                    return oldFiltered.get(op).getName().equals(newFiltered.get(np).getName());
                }
                @Override public boolean areContentsTheSame(int op, int np) { return true; }
            }).dispatchUpdatesTo(this);
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout selectableItem;
            final CheckBox selected;
            final TextView name;
            final TextView packageName;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                selectableItem = itemView.findViewById(R.id.view_item);
                selected = itemView.findViewById(R.id.chk_select);
                name = itemView.findViewById(R.id.tv_screen_name);
                packageName = itemView.findViewById(R.id.tv_activity_name);
            }
        }
    }
}
