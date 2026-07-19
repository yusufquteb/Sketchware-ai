package pro.sketchware.activities.projecttools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.utility.SketchwareUtil;

/**
 * CodeDiffActivity — مقارنة ملفين جنبًا إلى جنب أو موحّد.
 *
 * المميزات:
 *   ✅ Unified diff مع أرقام الأسطر المزدوجة (left | right)
 *   ✅ word-level highlighting داخل الأسطر المتغيرة
 *   ✅ إحصائيات: +added / -deleted / =unchanged
 *   ✅ تبديل بين Unified وSplit (side-by-side) عبر Chip
 *   ✅ Context lines قابل للضبط (3 / 5 / All)
 *   ✅ نسخ الـ diff الكامل
 *   ✅ تمرير أفقي للأسطر الطويلة
 */
public class CodeDiffActivity extends BaseAppCompatActivity {

    public static final String EXTRA_ORIGINAL = "original";
    public static final String EXTRA_MODIFIED  = "modified";
    public static final String EXTRA_TITLE     = "title";

    // ── View mode ──────────────────────────────────────────────────────────
    private enum ViewMode { UNIFIED, SPLIT }

    // ── Context lines ──────────────────────────────────────────────────────
    private static final int[] CONTEXT_OPTIONS = { 3, 5, Integer.MAX_VALUE };
    private int contextIndex = 0; // default: 3 lines

    // ── State ──────────────────────────────────────────────────────────────
    private ViewMode viewMode = ViewMode.UNIFIED;
    private String original, modified;
    private String[] origLines, modLines;
    private List<DiffLine> diff;

    // ── Views ──────────────────────────────────────────────────────────────
    private TextView tvStats;
    private LinearLayout diffContainer;
    private HorizontalScrollView hScroll;

    // ── Lifecycle ──────────────────────────────────────────────────────────
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        original = getIntent().getStringExtra(EXTRA_ORIGINAL);
        modified  = getIntent().getStringExtra(EXTRA_MODIFIED);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        if (original == null || modified == null) {
            SketchwareUtil.toastError("Missing content");
            finish();
            return;
        }

        origLines = original.split("\n", -1);
        modLines  = modified.split("\n", -1);
        diff = computeDiff(origLines, modLines);

        // ── Layout ─────────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Code Diff");
        if (title != null) toolbar.setSubtitle(title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        // Stats row
        tvStats = new TextView(this);
        int pad = dp(12);
        tvStats.setPadding(pad, dp(6), pad, dp(6));
        tvStats.setTypeface(tvStats.getTypeface(), Typeface.BOLD);
        root.addView(tvStats);

        // Mode chips + context chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        ChipGroup chipGroup = new ChipGroup(this);
        chipGroup.setSingleSelection(false);
        chipGroup.setPadding(pad, 0, pad, dp(4));

        Chip chipUnified = new Chip(this);
        chipUnified.setText("Unified");
        chipUnified.setCheckable(true);
        chipUnified.setChecked(true);
        chipUnified.setOnCheckedChangeListener((v, checked) -> {
            if (checked) { viewMode = ViewMode.UNIFIED; renderDiff(); }
        });
        chipGroup.addView(chipUnified);

        Chip chipSplit = new Chip(this);
        chipSplit.setText("Split");
        chipSplit.setCheckable(true);
        chipSplit.setOnCheckedChangeListener((v, checked) -> {
            if (checked) { viewMode = ViewMode.SPLIT; renderDiff(); }
        });
        chipGroup.addView(chipSplit);

        Chip chipContext = new Chip(this);
        chipContext.setText("Context: 3");
        chipContext.setOnClickListener(v -> {
            contextIndex = (contextIndex + 1) % CONTEXT_OPTIONS.length;
            int ctx = CONTEXT_OPTIONS[contextIndex];
            chipContext.setText("Context: " + (ctx == Integer.MAX_VALUE ? "All" : ctx));
            renderDiff();
        });
        chipGroup.addView(chipContext);

        chipScroll.addView(chipGroup);
        root.addView(chipScroll);

        // Diff view
        hScroll = new HorizontalScrollView(this);
        hScroll.setFillViewport(true);
        ScrollView vScroll = new ScrollView(this);
        diffContainer = new LinearLayout(this);
        diffContainer.setOrientation(LinearLayout.VERTICAL);
        diffContainer.setPadding(0, 0, 0, dp(32));
        vScroll.addView(diffContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        hScroll.addView(vScroll);
        root.addView(hScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        renderDiff();
    }

    // ── Diff computation (LCS) ─────────────────────────────────────────────

    private enum LineType { EQUAL, ADD, DELETE }

    private static final class DiffLine {
        final LineType type;
        final String   text;
        final int      origNum;
        final int      modNum;

        DiffLine(LineType type, String text, int origNum, int modNum) {
            this.type = type; this.text = text;
            this.origNum = origNum; this.modNum = modNum;
        }
    }

    private List<DiffLine> computeDiff(String[] orig, String[] mod) {
        int m = orig.length, n = mod.length;
        // Space-optimised LCS for large files
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--)
            for (int j = n - 1; j >= 0; j--)
                dp[i][j] = orig[i].equals(mod[j])
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);

        List<DiffLine> result = new ArrayList<>();
        int i = 0, j = 0, oi = 1, mi = 1;
        while (i < m && j < n) {
            if (orig[i].equals(mod[j])) {
                result.add(new DiffLine(LineType.EQUAL, orig[i], oi++, mi++)); i++; j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                result.add(new DiffLine(LineType.DELETE, orig[i], oi++, -1)); i++;
            } else {
                result.add(new DiffLine(LineType.ADD, mod[j], -1, mi++)); j++;
            }
        }
        while (i < m) result.add(new DiffLine(LineType.DELETE, orig[i++], oi++, -1));
        while (j < n) result.add(new DiffLine(LineType.ADD,    mod[j++], -1, mi++));
        return result;
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    private void renderDiff() {
        diffContainer.removeAllViews();
        int ctx = CONTEXT_OPTIONS[contextIndex];

        // Stats
        int added = 0, deleted = 0, equal = 0;
        for (DiffLine d : diff) {
            if (d.type == LineType.ADD)    added++;
            else if (d.type == LineType.DELETE) deleted++;
            else equal++;
        }
        tvStats.setText("+" + added + "  −" + deleted + "  =" + equal
                + "   (" + origLines.length + " → " + modLines.length + " lines)");

        if (viewMode == ViewMode.UNIFIED) {
            renderUnified(ctx);
        } else {
            renderSplit(ctx);
        }
    }

    private void renderUnified(int ctx) {
        boolean[] visible = computeVisible(ctx);
        int n = diff.size();
        boolean prevHidden = false;

        for (int i = 0; i < n; i++) {
            if (!visible[i]) {
                if (!prevHidden) addSeparator("⋯");
                prevHidden = true;
                continue;
            }
            prevHidden = false;
            DiffLine line = diff.get(i);
            addUnifiedRow(line, i, n);
        }
    }

    private void addUnifiedRow(DiffLine line, int idx, int total) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Gutter
        String gutter;
        int bgColor, fgColor;
        switch (line.type) {
            case ADD:
                gutter = String.format("       %4s  + ", line.modNum);
                bgColor = 0x22009688; fgColor = 0xFF1B5E20; break;
            case DELETE:
                gutter = String.format("  %4s        - ", line.origNum);
                bgColor = 0x22F44336; fgColor = 0xFFB71C1C; break;
            default:
                gutter = String.format("  %4s  %4s    ", line.origNum, line.modNum);
                bgColor = 0; fgColor = 0xFF888888; break;
        }

        row.setBackgroundColor(bgColor);

        TextView tvGutter = new TextView(this);
        tvGutter.setTypeface(Typeface.MONOSPACE);
        tvGutter.setTextSize(11f);
        tvGutter.setText(gutter);
        tvGutter.setTextColor(0xFF607D8B);
        row.addView(tvGutter);

        TextView tvCode = new TextView(this);
        tvCode.setTypeface(Typeface.MONOSPACE);
        tvCode.setTextSize(12f);
        tvCode.setSingleLine(true);

        if (line.type != LineType.EQUAL) {
            // Word-level diff highlight vs adjacent line
            CharSequence highlighted = highlightWords(line, idx, total);
            tvCode.setText(highlighted);
            tvCode.setTextColor(fgColor);
        } else {
            tvCode.setText(line.text);
            tvCode.setTextColor(0xFF333333);
        }
        row.addView(tvCode);

        diffContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void renderSplit(int ctx) {
        boolean[] visible = computeVisible(ctx);
        int n = diff.size();

        // Pair up DELETE+ADD
        int i = 0;
        boolean prevHidden = false;
        while (i < n) {
            if (!visible[i]) {
                if (!prevHidden) addSeparator("⋯");
                prevHidden = true;
                i++; continue;
            }
            prevHidden = false;
            DiffLine line = diff.get(i);

            if (line.type == LineType.DELETE && i + 1 < n
                    && diff.get(i + 1).type == LineType.ADD) {
                addSplitRow(line, diff.get(i + 1));
                i += 2;
            } else if (line.type == LineType.DELETE) {
                addSplitRow(line, null);
                i++;
            } else if (line.type == LineType.ADD) {
                addSplitRow(null, line);
                i++;
            } else {
                addSplitRow(line, line);
                i++;
            }
        }
    }

    private void addSplitRow(DiffLine left, DiffLine right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int colWidth = dp(340);

        // Left pane
        LinearLayout leftPane = makeSplitPane(left, colWidth, true);
        row.addView(leftPane, new LinearLayout.LayoutParams(colWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0xFF9E9E9E);
        row.addView(div, new LinearLayout.LayoutParams(dp(1),
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Right pane
        LinearLayout rightPane = makeSplitPane(right, colWidth, false);
        row.addView(rightPane, new LinearLayout.LayoutParams(colWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        diffContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout makeSplitPane(DiffLine line, int width, boolean isLeft) {
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.HORIZONTAL);
        pane.setGravity(Gravity.CENTER_VERTICAL);

        if (line == null) {
            pane.setBackgroundColor(0x11000000);
            pane.addView(new View(this), new LinearLayout.LayoutParams(width, dp(22)));
            return pane;
        }

        int bgColor;
        int fgColor;
        String gutterLabel;
        switch (line.type) {
            case ADD:
                bgColor = 0x2200C853; fgColor = 0xFF1B5E20;
                gutterLabel = String.format("%4s + ", line.modNum); break;
            case DELETE:
                bgColor = 0x22F44336; fgColor = 0xFFB71C1C;
                gutterLabel = String.format("%4s - ", line.origNum); break;
            default:
                bgColor = 0; fgColor = 0xFF333333;
                gutterLabel = String.format("%4s   ", isLeft ? line.origNum : line.modNum); break;
        }
        pane.setBackgroundColor(bgColor);

        TextView tvG = new TextView(this);
        tvG.setTypeface(Typeface.MONOSPACE);
        tvG.setTextSize(10f);
        tvG.setTextColor(0xFF607D8B);
        tvG.setText(gutterLabel);
        tvG.setPadding(dp(4), 0, dp(4), 0);
        pane.addView(tvG);

        TextView tvCode = new TextView(this);
        tvCode.setTypeface(Typeface.MONOSPACE);
        tvCode.setTextSize(12f);
        tvCode.setTextColor(fgColor);
        tvCode.setSingleLine(true);
        tvCode.setText(line.text);
        pane.addView(tvCode, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return pane;
    }

    // ── Word-level highlight ───────────────────────────────────────────────

    private CharSequence highlightWords(DiffLine line, int idx, int total) {
        // Find the paired line (DELETE↔ADD) for word diff
        String paired = null;
        if (line.type == LineType.DELETE && idx + 1 < total
                && diff.get(idx + 1).type == LineType.ADD) {
            paired = diff.get(idx + 1).text;
        } else if (line.type == LineType.ADD && idx > 0
                && diff.get(idx - 1).type == LineType.DELETE) {
            paired = diff.get(idx - 1).text;
        }
        if (paired == null) return line.text;

        // Token-level LCS
        String[] tokA = tokenize(line.type == LineType.DELETE ? line.text : paired);
        String[] tokB = tokenize(line.type == LineType.DELETE ? paired : line.text);
        String[] mine = line.type == LineType.DELETE ? tokA : tokB;

        boolean[] changed = wordDiff(tokA, tokB, line.type == LineType.DELETE);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < mine.length; i++) {
            int start = sb.length();
            sb.append(mine[i]);
            if (changed[i]) {
                sb.setSpan(new BackgroundColorSpan(
                        line.type == LineType.DELETE ? 0x66F44336 : 0x6600C853),
                        start, sb.length(), 0);
            }
        }
        return sb;
    }

    private String[] tokenize(String s) {
        List<String> tokens = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || !Character.isLetterOrDigit(s.charAt(i))) {
                if (i > start) tokens.add(s.substring(start, i));
                if (i < s.length()) tokens.add(String.valueOf(s.charAt(i)));
                start = i + 1;
            }
        }
        return tokens.toArray(new String[0]);
    }

    private boolean[] wordDiff(String[] a, String[] b, boolean forA) {
        int m = a.length, n = b.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--)
            for (int j = n - 1; j >= 0; j--)
                dp[i][j] = a[i].equals(b[j])
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);

        boolean[] changed = new boolean[forA ? m : n];
        int i = 0, j = 0;
        while (i < m && j < n) {
            if (a[i].equals(b[j])) { i++; j++; }
            else if (dp[i + 1][j] >= dp[i][j + 1]) {
                if (forA) changed[i] = true;
                i++;
            } else {
                if (!forA) changed[j] = true;
                j++;
            }
        }
        while (i < m) { if (forA) changed[i] = true; i++; }
        while (j < n) { if (!forA) changed[j] = true; j++; }
        return changed;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean[] computeVisible(int ctx) {
        int n = diff.size();
        boolean[] vis = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (diff.get(i).type != LineType.EQUAL) {
                // Mark ctx lines around each change
                for (int d = Math.max(0, i - ctx); d <= Math.min(n - 1, i + ctx); d++) {
                    vis[d] = true;
                }
            }
        }
        return vis;
    }

    private void addSeparator(String label) {
        TextView tv = new TextView(this);
        tv.setPadding(dp(12), dp(4), dp(12), dp(4));
        tv.setTextSize(11f);
        tv.setTextColor(0xFF9E9E9E);
        tv.setBackgroundColor(0xFF1A1A2E & 0x22FFFFFF | 0x11000000);
        tv.setText(label + "  (hidden context lines)");
        tv.setTypeface(Typeface.MONOSPACE);
        diffContainer.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ── Options menu ───────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, 1, "Copy diff");
        menu.add(Menu.NONE, 2, 2, "Copy original");
        menu.add(Menu.NONE, 3, 3, "Copy modified");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        switch (item.getItemId()) {
            case 1:
                StringBuilder sb = new StringBuilder();
                for (DiffLine d : diff) {
                    char prefix = d.type == LineType.ADD ? '+' : d.type == LineType.DELETE ? '-' : ' ';
                    sb.append(prefix).append(d.text).append('\n');
                }
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("diff", sb.toString()));
                SketchwareUtil.toast("Diff copied");
                return true;
            case 2:
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("original", original));
                SketchwareUtil.toast("Original copied");
                return true;
            case 3:
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("modified", modified));
                SketchwareUtil.toast("Modified copied");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
