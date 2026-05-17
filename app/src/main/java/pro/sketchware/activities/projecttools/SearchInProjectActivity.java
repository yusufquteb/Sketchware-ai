package pro.sketchware.activities.projecttools;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.util.ProjectSearchUtil;
import pro.sketchware.util.ProjectSearchUtil.SearchResult;
import pro.sketchware.util.ProjectSearchUtil.FileFilter;

public class SearchInProjectActivity extends BaseAppCompatActivity {

    private static final int MAX_RESULTS = 200;

    private String scId;
    private TextView statusView;
    private final List<SearchResult> results = new ArrayList<>();
    private SearchAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FileFilter activeFilter = FileFilter.ALL;
    private boolean caseSensitive   = false;
    private boolean useRegex        = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        scId = getIntent().getStringExtra("sc_id");
        if (scId == null || scId.trim().isEmpty()) {
            SketchwareUtil.toastError("Project id missing");
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Search in Project");
        toolbar.setSubtitle("Project " + scId);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Search text or regex...");
        int pad = dp(12);
        inputLayout.setPadding(pad, pad / 2, pad, 0);
        TextInputEditText searchInput = new TextInputEditText(this);
        inputLayout.addView(searchInput);
        root.addView(inputLayout);

        LinearLayout optRow = new LinearLayout(this);
        optRow.setOrientation(LinearLayout.HORIZONTAL);
        optRow.setGravity(Gravity.CENTER_VERTICAL);
        optRow.setPadding(pad, dp(4), pad, dp(4));

        CheckBox cbCase = new CheckBox(this);
        cbCase.setText("Case-sensitive");
        cbCase.setChecked(caseSensitive);
        cbCase.setOnCheckedChangeListener((b, checked) -> {
            caseSensitive = checked;
            triggerSearch(searchInput);
        });
        optRow.addView(cbCase);

        View spacer = new View(this);
        optRow.addView(spacer, new LinearLayout.LayoutParams(dp(20), 1));

        CheckBox cbRegex = new CheckBox(this);
        cbRegex.setText("Regex");
        cbRegex.setChecked(useRegex);
        cbRegex.setOnCheckedChangeListener((b, checked) -> {
            useRegex = checked;
            triggerSearch(searchInput);
        });
        optRow.addView(cbRegex);
        root.addView(optRow);

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        ChipGroup chipGroup = new ChipGroup(this);
        chipGroup.setSingleSelection(true);
        chipGroup.setPadding(pad, 0, pad, 0);
        for (FileFilter f : FileFilter.values()) {
            Chip chip = new Chip(this);
            chip.setText(f.name());
            chip.setCheckable(true);
            chip.setChecked(f == activeFilter);
            chip.setOnCheckedChangeListener((v, checked) -> {
                if (checked) {
                    activeFilter = f;
                    triggerSearch(searchInput);
                }
            });
            chipGroup.addView(chip);
        }
        chipScroll.addView(chipGroup);
        root.addView(chipScroll);

        statusView = new TextView(this);
        statusView.setPadding(pad, dp(6), pad, dp(6));
        statusView.setTextSize(12f);
        statusView.setTextColor(0xFF888888);
        statusView.setText("Type at least 2 characters...");
        root.addView(statusView);

        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new SearchAdapter();
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        searchInput.addTextChangedListener(new TextWatcher() {
            private Runnable pending;
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                if (pending != null) searchInput.removeCallbacks(pending);
                pending = () -> runSearch(e == null ? "" : e.toString().trim());
                searchInput.postDelayed(pending, 300);
            }
        });
    }

    private void triggerSearch(TextInputEditText input) {
        String q = input.getText() == null ? "" : input.getText().toString().trim();
        runSearch(q);
    }

    private void runSearch(String query) {
        if (query.length() < 2) {
            results.clear();
            adapter.notifyDataSetChanged();
            statusView.setText("Type at least 2 characters...");
            return;
        }
        statusView.setText("Searching...");
        executor.execute(() -> {
            // FIX: Corrected the path to ProjectToolPaths
            List<SearchResult> found = ProjectSearchUtil.globalSearch(
                    ProjectToolPaths.getProjectDataDir(scId), 
                    query, caseSensitive, useRegex, activeFilter);
            
            runOnUiThread(() -> {
                results.clear();
                results.addAll(found);
                adapter.notifyDataSetChanged();
                statusView.setText(found.size() + " results for \"" + query + "\""
                        + (activeFilter != FileFilter.ALL ? " in " + activeFilter.name() : ""));
            });
        });
    }

    private final class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            int p = dp(12);
            row.setPadding(p, p, p, p);
            row.setClickable(true);
            row.setFocusable(true);
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);

            LinearLayout topRow = new LinearLayout(parent.getContext());
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvName = new TextView(parent.getContext());
            tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
            tvName.setSingleLine(true);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.START);
            topRow.addView(tvName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvBadge = new TextView(parent.getContext());
            tvBadge.setTextSize(10f);
            tvBadge.setPadding(dp(5), dp(1), dp(5), dp(1));
            tvBadge.setTextColor(0xFFFFFFFF);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(10));
            tvBadge.setBackground(bg);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.setMarginStart(dp(6));
            tvBadge.setLayoutParams(badgeLp);
            topRow.addView(tvBadge);
            row.addView(topRow);

            TextView tvPath = new TextView(parent.getContext());
            tvPath.setTextSize(11f);
            tvPath.setTextColor(0xFF888888);
            tvPath.setSingleLine(true);
            tvPath.setEllipsize(android.text.TextUtils.TruncateAt.START);
            row.addView(tvPath);

            TextView tvPreview = new TextView(parent.getContext());
            tvPreview.setTypeface(Typeface.MONOSPACE);
            tvPreview.setTextSize(12f);
            tvPreview.setPadding(dp(6), dp(4), dp(6), dp(4));
            tvPreview.setBackgroundColor(0x11000000);
            row.addView(tvPreview);

            return new VH(row, tvName, tvPath, tvPreview, tvBadge, (android.graphics.drawable.GradientDrawable) tvBadge.getBackground());
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SearchResult r = results.get(position);
            h.tvName.setText(r.filePath.substring(r.filePath.lastIndexOf(File.separator) + 1));
            h.tvPath.setText(r.filePath + ":" + r.lineNumber);
            h.tvPreview.setText(r.lineContent);
            h.tvBadge.setText(r.editable ? "editable" : "generated");
            h.badgeBg.setColor(r.editable ? 0xFF2196F3 : 0xFF9E9E9E);
            h.itemView.setOnClickListener(v -> openResult(r));
        }

        @Override
        public int getItemCount() { return results.size(); }

        final class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPath, tvPreview, tvBadge;
            android.graphics.drawable.GradientDrawable badgeBg;
            VH(@NonNull View v, TextView name, TextView path, TextView preview, TextView badge, android.graphics.drawable.GradientDrawable badgeBg) {
                super(v);
                this.tvName = name; this.tvPath = path; this.tvPreview = preview; this.tvBadge = badge; this.badgeBg = badgeBg;
            }
        }
    }

    private void openResult(SearchResult r) {
        Intent i = new Intent(this, mod.hey.studios.code.SrcCodeEditor.class);
        i.putExtra("title", r.filePath);
        i.putExtra("content", r.filePath);
        startActivity(i);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
