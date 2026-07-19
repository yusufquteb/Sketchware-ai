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

import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

public class TerminalActivity extends BaseAppCompatActivity {

    private static final String[] ALLOWED_CMDS = {
        // File / text tools (original set)
        "ls","ll","find","grep","cat","head","tail","wc","echo",
        "pwd","sort","uniq","diff","stat","du","df",
        "mkdir","cp","mv","rm","touch","chmod",
        "date","env","which","ps","clear","python","python3",
        // Archives
        "zip","unzip","tar","gzip","gunzip",
        // Network fetch (also gated by NETWORK_CMDS confirmation — see run())
        "curl","wget",
        // Git
        "git",
        // Java / Gradle build tools
        "javac","java","jar","gradle"
    };

    /**
     * Commands that reach outside this device (git clone/pull/push, curl,
     * wget) get a one-line confirmation echoed before running, since unlike
     * the rest of the whitelist they can transfer data over the network.
     * This is not a permission dialog — the command still executes, this is
     * just a visible heads-up in the terminal output so it's not silent.
     */
    private static final String[] NETWORK_CMDS = { "curl", "wget" };

    /** git subcommands that touch the network (clone/pull/push/fetch/remote). */
    private static final String[] NETWORK_GIT_SUBCMDS = { "clone", "pull", "push", "fetch", "remote" };


    private LinearLayout outputContainer;
    private ScrollView outputScroll;
    private EditText inputField;
    private TextView promptView;

    private final List<String> cmdHistory = new ArrayList<>();
    private int historyIdx = -1;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private String workDir = "/storage/emulated/0/.sketchware";
    private String scId;

    private int C_PROMPT, C_CMD, C_OUT, C_ERR, C_INFO, BG_SURFACE;

    private void initColors() {
        int[] attrs = {
            android.R.attr.colorPrimary,
            android.R.attr.textColorPrimary,
            android.R.attr.textColorSecondary,
            android.R.attr.colorBackground,
        };
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        C_PROMPT = ta.getColor(0, Color.parseColor("#7c3aed"));
        C_CMD    = ta.getColor(1, Color.WHITE);
        C_OUT    = ta.getColor(2, Color.LTGRAY);
        C_INFO   = ta.getColor(0, Color.parseColor("#88ccff"));
        ta.recycle();
        C_ERR      = Color.parseColor("#ff5555");
        BG_SURFACE = isDarkTheme() ? Color.parseColor("#0d0d1a") : Color.parseColor("#f5f5ff");
    }

    private boolean isDarkTheme() {
        int mask = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra("sc_id");
        if (scId != null) workDir = "/storage/emulated/0/.sketchware/data/" + scId;
        initColors();
        buildUi();
        welcome();
    }

    private void buildUi() {
        // Root with FAB overlay using FrameLayout
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        setContentView(frame);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_SURFACE);
        frame.addView(root, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

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

        // Output area
        outputScroll = new ScrollView(this);
        outputScroll.setFillViewport(true);
        outputScroll.setBackgroundColor(BG_SURFACE);
        root.addView(outputScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        outputContainer = new LinearLayout(this);
        outputContainer.setOrientation(LinearLayout.VERTICAL);
        outputContainer.setPadding(dp(8), dp(4), dp(8), dp(72));
        outputScroll.addView(outputContainer);

        // Input row
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setBackgroundColor(Color.argb(255, 20, 20, 40));
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

        // FAB — command palette
        FloatingActionButton fab = new FloatingActionButton(this);
        fab.setImageResource(android.R.drawable.ic_menu_more);
        fab.setOnClickListener(v -> showCommandPalette());
        android.widget.FrameLayout.LayoutParams fabLp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        fabLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        fabLp.setMargins(0, 0, dp(16), dp(80));
        fab.setLayoutParams(fabLp);
        frame.addView(fab);
    }

    // ── Command palette (BottomSheet) ─────────────────────────────────────────

    private static final String[][] PALETTE_SECTIONS = {
        { "Files",
            "ls -la",
            "find . -name '*.java'",
            "find . -name '*.java' | wc -l",
            "grep -r 'TODO' . --include='*.java' -l",
            "du -sh *",
            "cat proguard-rules.pro"
        },
        { "Project",
            "ls data/",
            "find data/ -name 'logic' | head -20",
            "find data/ -name 'view'  | head -20",
            "find data/ -name 'file'  | head -20",
            "du -sh data/*",
            "ls mysc/"
        },
        { "Git",
            "git status",
            "git log --oneline -10",
            "git diff",
            "git add .",
            "git commit -m \"\"",
            "git pull",
            "git push"
        },
        { "Build",
            "./gradlew assembleDebug",
            "./gradlew clean",
            "javac -version",
            "java -version"
        },
        { "Archives",
            "unzip -l ",
            "zip -r out.zip ",
            "tar -tf "
        },
        { "System",
            "pwd",
            "date",
            "env | grep -i sketch",
            "df -h",
            "ps | grep sketch",
            "echo Hello from Terminal!"
        }
    };

    private void showCommandPalette() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        container.setPadding(p, p, p, p * 2);

        TextView title = new TextView(this);
        title.setText("Command Palette");
        title.setTextSize(17f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, 0, 0, dp(12));
        title.setLayoutParams(titleLp);
        container.addView(title);

        for (String[] section : PALETTE_SECTIONS) {
            TextView header = new TextView(this);
            header.setText(section[0]);
            header.setTextSize(13f);
            header.setAlpha(0.6f);
            header.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hLp.setMargins(0, dp(8), 0, dp(4));
            header.setLayoutParams(hLp);
            container.addView(header);

            for (int i = 1; i < section.length; i++) {
                final String cmd = section[i];
                MaterialButton btn = new MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btn.setText(cmd);
                btn.setTypeface(Typeface.MONOSPACE);
                btn.setTextSize(12f);
                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                btnLp.setMargins(0, 0, 0, dp(4));
                btn.setLayoutParams(btnLp);
                btn.setOnClickListener(v -> {
                    inputField.setText(cmd);
                    inputField.setSelection(cmd.length());
                    sheet.dismiss();
                });
                container.addView(btn);
            }
        }

        sv.addView(container);
        sheet.setContentView(sv);
        sheet.show();
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

        if ("cd".equals(first)) {
            String[] p = cmd.split("\\s+", 2);
            workDir = p.length > 1
                    ? (p[1].startsWith("/") ? p[1] : workDir + "/" + p[1])
                    : workDir;
            println("→ " + workDir, C_INFO);
            return;
        }

        // "./gradlew" (or "gradlew") is a script inside the project, not a
        // system command — its "first word" as split above would be the
        // literal "./gradlew" string, which will never match ALLOWED_CMDS.
        // Treat it as its own allowed case instead of adding "./gradlew" to
        // the whitelist (which wouldn't work the same way for other paths).
        boolean isGradlew = first.equals("./gradlew") || first.equals("gradlew");

        boolean ok = isGradlew;
        if (!ok) {
            for (String a : ALLOWED_CMDS) if (first.equals(a)) { ok = true; break; }
        }
        if (!ok) {
            println("'" + first + "' is not in the allowed list.", C_ERR);
            println("Allowed: " + String.join(", ", ALLOWED_CMDS) + ", ./gradlew", C_INFO);
            return;
        }

        if (isNetworkCommand(first, cmd)) {
            println("⚠ network command — this will access the internet", C_INFO);
        }

        // Gradle builds (and to a lesser extent git clone/pull over slow
        // connections) can legitimately take much longer than the original
        // 30s budget meant for quick file/text commands.
        long timeoutSeconds = (isGradlew || "git".equals(first)) ? 300 : 30;

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
                proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) { out.append("Error: ").append(e.getMessage()); }
            String result = out.toString().trim();
            runOnUiThread(() -> { if (!result.isEmpty()) println(result, C_OUT); scroll(); });
        });
    }

    /** True for curl/wget, or any git subcommand that talks to a remote. */
    private boolean isNetworkCommand(String first, String fullCmd) {
        for (String c : NETWORK_CMDS) if (first.equals(c)) return true;
        if ("git".equals(first)) {
            String[] parts = fullCmd.trim().split("\\s+");
            if (parts.length >= 2) {
                String sub = parts[1].toLowerCase();
                for (String s : NETWORK_GIT_SUBCMDS) if (sub.equals(s)) return true;
            }
        }
        return false;
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
        println("Tap the (+) button for ready-made commands.\n", C_INFO);
    }

    private void printHelp() {
        println("Files/text: ls, find, grep, cat, cp, mv, rm, mkdir,", C_INFO);
        println("            wc, head, tail, sort, du, echo, chmod, cd", C_INFO);
        println("Archives:   zip, unzip, tar, gzip, gunzip", C_INFO);
        println("Git:        git (status/log/diff/branch/add/commit/", C_INFO);
        println("            clone/pull/push/fetch/remote — network ones", C_INFO);
        println("            are flagged before running)", C_INFO);
        println("Build:      javac, java, jar, gradle, ./gradlew", C_INFO);
        println("Network:    curl, wget — flagged before running", C_INFO);
        println("Special:    clear, exit, help  |  history: ↑↓ keys", C_INFO);
        println("Tap the (+) FAB for the command palette.", C_INFO);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ── Options menu ──────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Clear").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 2, 1, "Share output").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { finish(); return true; }
        if (id == 1) { outputContainer.removeAllViews(); return true; }
        if (id == 2) { share(); return true; }
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}
