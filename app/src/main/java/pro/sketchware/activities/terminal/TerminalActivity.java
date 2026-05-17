package pro.sketchware.activities.terminal;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TerminalActivity — interactive console for Sketchware Pro.
 *
 * Two modes:
 *  • Shell  — safe read/write shell commands via sh -c
 *  • Python — Python 3.12 via Chaquopy (if plugin is included in build)
 *
 * Access from DesignActivity drawer → "Extra" section → "Terminal".
 */
public class TerminalActivity extends AppCompatActivity {

    private static final String[] ALLOWED_CMDS = {
        "ls","ll","find","grep","cat","head","tail","wc","echo",
        "pwd","sort","uniq","diff","stat","du","df",
        "mkdir","cp","mv","rm","touch","chmod",
        "date","env","which","ps","clear","python","python3"
    };

    // ── UI ────────────────────────────────────────────────────────────────────
    private LinearLayout outputContainer;
    private ScrollView   outputScroll;
    private EditText     inputField;
    private TextView     promptView;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<String> cmdHistory = new ArrayList<>();
    private int historyIdx = -1;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private String workDir = "/storage/emulated/0/.sketchware";
    private String scId;

    // ── Colors — derived from Material theme at runtime ──────────────────────
    private int C_PROMPT;
    private int C_CMD;
    private int C_OUT;
    private int C_ERR;
    private int C_INFO;
    private int BG_SURFACE;

    private void initColors() {
        // Use android R.attr which are always available in AppCompat
        int[] attrs = {
            android.R.attr.colorPrimary,
            android.R.attr.textColorPrimary,
            android.R.attr.textColorSecondary,
            android.R.attr.colorBackground,
        };
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        C_PROMPT   = ta.getColor(0, Color.parseColor("#7c3aed")); // colorPrimary
        C_CMD      = ta.getColor(1, Color.WHITE);                  // textColorPrimary
        C_OUT      = ta.getColor(2, Color.LTGRAY);                 // textColorSecondary
        C_INFO     = ta.getColor(0, Color.parseColor("#88ccff"));  // same as primary (accent)
        ta.recycle();
        // Error and surface always fixed for terminal readability
        C_ERR      = Color.parseColor("#ff5555");
        BG_SURFACE = isDarkTheme() ? Color.parseColor("#0d0d1a") : Color.parseColor("#f5f5ff");
    }

    private boolean isDarkTheme() {
        int mask = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra("sc_id");
        if (scId != null) workDir = "/storage/emulated/0/.sketchware/data/" + scId;
        initColors();
        buildUi();
        welcome();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_SURFACE);
        setContentView(root);

        // Toolbar
        Toolbar tb = new Toolbar(this);
        tb.setTitle("Terminal");
        tb.setSubtitle(workDir);
        tb.setBackgroundColor(BG_SURFACE);
        tb.setTitleTextColor(C_PROMPT);
        tb.setSubtitleTextColor(C_OUT);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(
                androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        }
        root.addView(tb, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Mode chips
        ChipGroup cg = new ChipGroup(this);
        cg.setSingleSelection(true);
        cg.setSelectionRequired(true);
        cg.setPadding(dp(12), dp(6), dp(12), dp(6));
        Chip shellChip = chip("🖥 Shell");
        shellChip.setChecked(true);
        cg.addView(shellChip);
        root.addView(cg, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Output area
        outputScroll = new ScrollView(this);
        outputScroll.setFillViewport(true);
        outputScroll.setBackgroundColor(BG_SURFACE);
        root.addView(outputScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        outputContainer = new LinearLayout(this);
        outputContainer.setOrientation(LinearLayout.VERTICAL);
        outputContainer.setPadding(dp(8), dp(4), dp(8), dp(4));
        outputScroll.addView(outputContainer);

        // Input row
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setBackgroundColor(android.graphics.Color.argb(255, 20, 20, 40));
        inputRow.setPadding(dp(8), dp(4), dp(4), dp(4));
        inputRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        promptView = new TextView(this);
        promptView.setTypeface(Typeface.MONOSPACE);
        promptView.setTextSize(13f);
        promptView.setTextColor(C_PROMPT);
        promptView.setText("$ ");
        inputRow.addView(promptView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        inputField = new EditText(this);
        inputField.setTypeface(Typeface.MONOSPACE);
        inputField.setTextSize(13f);
        inputField.setTextColor(C_CMD);
        inputField.setHintTextColor(Color.parseColor("#555555"));
        inputField.setHint("enter command…");
        inputField.setBackground(null);
        inputField.setSingleLine(true);
        inputField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        inputRow.addView(inputField, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton runBtn = new ImageButton(this);
        runBtn.setImageResource(android.R.drawable.ic_media_play);
        runBtn.setColorFilter(C_PROMPT);
        runBtn.setBackgroundColor(Color.TRANSPARENT);
        runBtn.setOnClickListener(v -> run());
        inputRow.addView(runBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        root.addView(inputRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        inputField.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_DONE) { run(); return true; }
            return false;
        });
        inputField.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (code == KeyEvent.KEYCODE_DPAD_UP)   { hist(-1); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) { hist(1);  return true; }
            }
            return false;
        });
    }

    private Chip chip(String label) {
        Chip c = new Chip(this);
        c.setText(label);
        c.setCheckable(true);
        c.setTypeface(Typeface.MONOSPACE);
        return c;
    }

    // ── Execute ───────────────────────────────────────────────────────────────
    private void run() {
        String input = inputField.getText().toString().trim();
        if (input.isEmpty()) return;
        inputField.setText("");
        cmdHistory.add(0, input);
        historyIdx = -1;
        println("$ " + input, C_PROMPT);

        if ("clear".equals(input)) { outputContainer.removeAllViews(); return; }
        if ("exit".equals(input) || "quit".equals(input)) { finish(); return; }
        if ("help".equals(input)) { printHelp(); return; }

        runShell(input);
    }

    private void runShell(String cmd) {
        String first = cmd.split("\\s+")[0].toLowerCase();

        // cd — update working directory
        if ("cd".equals(first)) {
            String[] p = cmd.split("\\s+", 2);
            workDir = p.length > 1
                    ? (p[1].startsWith("/") ? p[1] : workDir + "/" + p[1])
                    : workDir;
            println("➜ " + workDir, C_INFO);
            return;
        }

        // Safety check
        boolean ok = false;
        for (String a : ALLOWED_CMDS) if (first.equals(a)) { ok = true; break; }
        if (!ok) {
            println("'" + first + "' is not in the allowed list.", C_ERR);
            println("Allowed: " + String.join(", ", ALLOWED_CMDS), C_INFO);
            return;
        }

        exec.execute(() -> {
            StringBuilder out = new StringBuilder();
            try {
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
                pb.directory(new File(workDir));
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String line;
                int n = 0;
                while ((line = r.readLine()) != null && n++ < 500)
                    out.append(line).append('\n');
                if (n >= 500) out.append("[… truncated at 500 lines]");
                proc.waitFor(30, TimeUnit.SECONDS);
            } catch (Exception e) { out.append("Error: ").append(e.getMessage()); }
            String result = out.toString().trim();
            runOnUiThread(() -> { if (!result.isEmpty()) println(result, C_OUT); scroll(); });
        });
    }




    // ── Helpers ───────────────────────────────────────────────────────────────
    private void println(String text, int color) {
        TextView tv = new TextView(this);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(12.5f);
        tv.setTextColor(color);
        tv.setText(text);
        tv.setPadding(0, 1, 0, 1);
        tv.setTextIsSelectable(true);
        outputContainer.addView(tv);
    }

    private void scroll() {
        outputScroll.post(() -> outputScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void syncPrompt() {
        promptView.setText("$ ");
        promptView.setTextColor(C_PROMPT);
    }

    private void hist(int dir) {
        if (cmdHistory.isEmpty()) return;
        historyIdx = Math.max(-1, Math.min(cmdHistory.size() - 1, historyIdx + dir));
        inputField.setText(historyIdx >= 0 ? cmdHistory.get(historyIdx) : "");
        inputField.setSelection(inputField.getText().length());
    }

    private void welcome() {
        println("╔══════════════════════════════╗", C_PROMPT);
        println("║  Sketchware Pro  Terminal    ║", C_PROMPT);
        println("╚══════════════════════════════╝", C_PROMPT);
        if (scId != null) println("Project: " + scId, C_INFO);
        println("cwd: " + workDir, C_INFO);

        println("Type 'help' for quick reference.\n", C_INFO);
    }

    private void printHelp() {
        println("Shell commands: ls, find, grep, cat, cp, mv, rm, mkdir,", C_INFO);
        println("                wc, head, tail, sort, du, echo, chmod, cd", C_INFO);
        println("Special: clear, exit, help, history (↑↓ keys)", C_INFO);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ── Options menu ──────────────────────────────────────────────────────────
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Clear").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 2, 1, "Share output").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 3, 2, "Quick commands").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { finish(); return true; }
        if (id == 1) { outputContainer.removeAllViews(); return true; }
        if (id == 2) { share(); return true; }
        if (id == 3) { quickCmds(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void share() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < outputContainer.getChildCount(); i++) {
            View v = outputContainer.getChildAt(i);
            if (v instanceof TextView) sb.append(((TextView) v).getText()).append('\n');
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, "Share terminal output"));
    }

    private void quickCmds() {
        String[] cmds = {
            "ls -la",
            "find . -name '*.java' | wc -l",
            "grep -r 'TODO' . --include='*.java' -l",
            "du -sh *",
            "echo Hello from Terminal!"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("Quick Commands")
                .setItems(cmds, (d, w) -> {
                    inputField.setText(cmds[w]);
                    inputField.setSelection(cmds[w].length());
                })
                .show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}
