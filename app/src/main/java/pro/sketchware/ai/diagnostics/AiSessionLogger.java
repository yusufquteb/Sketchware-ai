package pro.sketchware.ai.diagnostics;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AiSessionLogger — writes a Markdown diagnostic log for every AI session.
 *
 * <p>Output: {@code <external-files>/ai_logs/ai_session_<datetime>.md}
 * <p>Each file is at most 2 MB; a new file is started when the limit is reached.
 * <p>Thread-safe: all writes are serialised through a single background thread.
 * <p>Usage: call {@link #getInstance(Context)} once; then call the log methods
 * from any thread. Enable/disable via {@link #setEnabled(boolean)}.
 */
public final class AiSessionLogger {

    private static final String TAG          = "AiSessionLogger";
    private static final long   MAX_BYTES    = 2L * 1024 * 1024; // 2 MB per file
    private static final int    MAX_ARG_LEN  = 500;
    private static final int    MAX_OUT_LEN  = 800;
    private static final int    MAX_MSG_LEN  = 1000;

    private static volatile AiSessionLogger instance;

    private final File           logDir;
    private       File           currentFile;
    private final AtomicBoolean  enabled = new AtomicBoolean(true);

    private final ExecutorService writerThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-session-logger");
        t.setDaemon(true);
        return t;
    });

    private final SimpleDateFormat fileFmt = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private AiSessionLogger(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        logDir = new File(base != null ? base : ctx.getFilesDir(), "ai_logs");
        logDir.mkdirs();
        rotateFile();
    }

    public static AiSessionLogger getInstance(Context ctx) {
        if (instance == null) {
            synchronized (AiSessionLogger.class) {
                if (instance == null) {
                    instance = new AiSessionLogger(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ── Control ───────────────────────────────────────────────────────────────

    public void setEnabled(boolean on) { enabled.set(on); }
    public boolean isEnabled()         { return enabled.get(); }

    // ── Public log API ────────────────────────────────────────────────────────

    /**
     * Call once at the start of each new AI request (before any streaming).
     */
    public void logSessionStart(String provider, String model) {
        write("---\n## " + ts() + " — Session Start\n**Provider:** `" + provider
                + "` | **Model:** `" + model + "`\n");
    }

    /**
     * Logs the user message that triggered this AI turn.
     */
    public void logUserMessage(String text) {
        write("\n**👤 User:** " + clip(text, MAX_MSG_LEN) + "\n");
    }

    /**
     * Logs the full AI response at end of turn (not each streaming chunk).
     */
    public void logAiResponse(String text) {
        write("\n**🤖 AI:** " + clip(text, MAX_MSG_LEN) + "\n");
    }

    /**
     * Logs a tool invocation — name + JSON args.
     */
    public void logToolCall(String toolName, String argsJson) {
        write("\n> ⚙️ **`" + toolName + "`**  \n> Args: `" + clip(argsJson, MAX_ARG_LEN) + "`\n");
    }

    /**
     * Logs the outcome of a tool call.
     */
    public void logToolResult(String toolName, boolean success, String output) {
        String icon = success ? "✅" : "❌";
        write("> " + icon + " `" + toolName + "`: " + clip(output, MAX_OUT_LEN) + "\n");
    }

    /**
     * Logs an AI engine error (API error, timeout, failover, etc.).
     */
    public void logError(String error) {
        write("\n> ❌ **Error:** " + clip(error, MAX_OUT_LEN) + "\n");
    }

    /**
     * Logs a status/thinking update from the agent.
     */
    public void logThinking(String status) {
        write("> 💭 _" + clip(status, 200) + "_\n");
    }

    /**
     * Logs a provider failover event.
     */
    public void logFailover(String from, String to, String reason) {
        write("\n> ⚡ **Failover** `" + from + "` → `" + to + "` (" + clip(reason, 200) + ")\n");
    }

    // ── File helpers ──────────────────────────────────────────────────────────

    /** Returns the path of the current log file (for display in Settings). */
    public String getCurrentLogPath() {
        return currentFile != null ? currentFile.getAbsolutePath() : "(logging not started)";
    }

    /** Lists all log files in this session's log directory, newest first. */
    public File[] listLogs() {
        File[] files = logDir.listFiles(f -> f.getName().startsWith("ai_session") && f.getName().endsWith(".md"));
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    /** Deletes all log files older than {@code maxAgeMs} milliseconds. */
    public void pruneOldLogs(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        File[] all = logDir.listFiles();
        if (all == null) return;
        for (File f : all) {
            if (f.lastModified() < cutoff) f.delete();
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void write(String text) {
        if (!enabled.get()) return;
        writerThread.execute(() -> {
            try {
                if (currentFile != null && currentFile.length() > MAX_BYTES) {
                    rotateFile();
                }
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(currentFile, true))) {
                    bw.write(text);
                    bw.newLine();
                }
            } catch (IOException e) {
                Log.w(TAG, "Log write failed: " + e.getMessage());
            }
        });
    }

    private void rotateFile() {
        currentFile = new File(logDir, "ai_session_" + fileFmt.format(new Date()) + ".md");
    }

    private String ts() {
        return timeFmt.format(new Date());
    }

    /** Clips and sanitises a string for Markdown inline usage. */
    private static String clip(String s, int max) {
        if (s == null) return "(null)";
        s = s.replace("\r", "").replace("`", "'");
        // Collapse excessive newlines
        s = s.replaceAll("\n{3,}", "\n\n").replace("\n", " ↵ ");
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
