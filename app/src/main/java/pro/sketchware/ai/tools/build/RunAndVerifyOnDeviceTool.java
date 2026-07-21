package pro.sketchware.ai.tools.build;

import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yq;
import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.models.ToolResult;

/**
 * RunAndVerifyOnDeviceTool — Phase 4, scoped strictly per PROMPT_PHASE_4.md rule 4:
 * "verify no crash on launch only", NOT deeper behavioral/UI testing. Any expansion
 * of this tool's scope requires asking the user first — do not add it here.
 *
 * Pipeline: install the already-built debug APK (build_project must have
 * succeeded first — this tool does not build) → launch its main activity →
 * wait a short, configurable grace period → inspect logcat for a crash signature
 * that occurred after launch → report crash / no-crash.
 *
 * ── Install path (verified against existing code, not assumed) ─────────────
 * DesignActivity.installBuiltApk() has two paths:
 *   1. Root (libsu `pm install -S`)     → programmatic, returns a real exit code.
 *   2. Non-root (ACTION_VIEW intent)    → hands off to the system's package
 *      installer UI and requires a human tap to confirm. Fire-and-forget from
 *      the app's point of view — there is no callback for success/failure.
 * This tool reuses path 1 verbatim (via Shell.cmd, same as DesignActivity) when
 * root is available, because it is the only path that gives a real, checkable
 * result without a human in the loop — which the self-correction loop requires
 * to run unattended. When root is NOT available, this tool does NOT silently
 * fall back to the ACTION_VIEW intent and pretend success: it returns a failure
 * result that says so explicitly, because a human-confirmation dialog cannot be
 * verified from an autonomous loop. This is a known, intentional limitation —
 * see CHANGES.md Phase 4 section.
 *
 * ── Crash detection (rule 4 of PROMPT_PHASE_4.md: must not be a TODO) ───────
 * Detection is logcat-based, not process-monitoring-based, because installed
 * user-project APKs run as a *different* Android process/UID than this app —
 * this app cannot attach a process watcher to another app's PID without root
 * (and even with root, a short-lived crash-restart cycle showing "not running"
 * is ambiguous: was it never running, or did it crash and get reaped already?).
 * Logcat is authoritative for this because a Java-level crash on any process
 * always writes a "FATAL EXCEPTION" / "AndroidRuntime" block to logcat with the
 * crashing package name in it — this is standard AndroidRuntime.uncaughtException
 * behavior on every Android version this project targets (minSdk verified via
 * BuiltInLibraryCompatibilityMatrix elsewhere in this codebase).
 *
 * Concretely: capture a logcat timestamp immediately before sending the launch
 * intent, wait the grace period, then run `logcat -d -v time -T <timestamp>` to
 * fetch only lines emitted after launch, and scan them for:
 *   - "FATAL EXCEPTION" (Java/Kotlin uncaught exception marker), AND
 *   - the target package name appearing in the same crash block
 * (mirrors the existing, working pattern in LogcatManager.getCrashLogs(), which
 * already filters on "AndroidRuntime" for the same purpose — reused/aligned
 * here rather than inventing a second crash-detection heuristic).
 */
public final class RunAndVerifyOnDeviceTool {

    private RunAndVerifyOnDeviceTool() {}

    /** Default grace period to watch logcat for a post-launch crash. Configurable per-call. */
    public static final int DEFAULT_WAIT_SECONDS = 6;
    private static final int MAX_WAIT_SECONDS = 30;

    private static final Pattern FATAL_EXCEPTION = Pattern.compile("FATAL EXCEPTION", Pattern.CASE_INSENSITIVE);

    public static class RunAndVerifyTool implements AgentTool {

        @Override public String getName() { return "run_and_verify_on_device"; }

        @Override public RiskLevel getRiskLevel() { return RiskLevel.CRITICAL; }

        @Override
        public String getDescription() {
            return "Installs the last successfully built debug APK and launches it, then watches "
                 + "logcat for a short grace period to check it did not crash on startup. "
                 + "SCOPE: crash-on-launch detection only — this does NOT test app behavior, UI, "
                 + "or functionality beyond 'did it stay alive after opening'. "
                 + "Requires build_project to have succeeded first (does not build). "
                 + "Requires root (via the same libsu path used by the app's own Run button) — "
                 + "without root this tool fails explicitly rather than silently no-op, because "
                 + "the non-root install path requires a human tap and cannot be verified "
                 + "unattended. Not tested on a real device in this session — no Android device "
                 + "was connected to the development environment that produced this tool.";
        }

        @Override public boolean requiresProject() { return true; }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();

            JsonObject scId = new JsonObject();
            scId.addProperty("type", "string");
            scId.addProperty("description", "The project SC ID. Must have a successful build_project result already.");
            props.add("sc_id", scId);

            JsonObject waitSeconds = new JsonObject();
            waitSeconds.addProperty("type", "integer");
            waitSeconds.addProperty("description",
                    "How many seconds to watch logcat after launch before declaring success. "
                    + "Default " + DEFAULT_WAIT_SECONDS + ", max " + MAX_WAIT_SECONDS + ".");
            props.add("wait_seconds", waitSeconds);

            schema.add("properties", props);
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
                return ToolResult.failure(null, "sc_id is required");
            }
            String scId = arguments.get("sc_id").getAsString();
            if (!context.isProjectAllowed(scId)) {
                return ToolResult.failure(null, "Access denied: project " + scId);
            }

            int waitSeconds = DEFAULT_WAIT_SECONDS;
            if (arguments.has("wait_seconds") && !arguments.get("wait_seconds").isJsonNull()) {
                waitSeconds = Math.max(1, Math.min(MAX_WAIT_SECONDS, arguments.get("wait_seconds").getAsInt()));
            }

            Context appContext = context.getAppContext();

            // ── Resolve the built APK path and package name from the project's own metadata,
            //    exactly like BuildTools.BuildProjectTool does — no new path-derivation logic. ──
            yq project;
            try {
                project = new yq(appContext, wq.d(scId), lC.b(scId));
            } catch (Exception e) {
                return ToolResult.failure(null, "Could not resolve project metadata for " + scId + ": " + e.getMessage());
            }

            File apk = new File(project.finalToInstallApkPath);
            if (!apk.exists()) {
                return ToolResult.failure(null,
                        "No built APK found at " + project.finalToInstallApkPath
                        + ". Run build_project successfully first.");
            }

            String packageName = project.packageName;
            if (packageName == null || packageName.isEmpty()) {
                return ToolResult.failure(null, "Project has no resolvable package name.");
            }

            // ── Root check (via the same shell mechanism DesignActivity uses) ──
            if (!isRootAvailable()) {
                return ToolResult.failure(null,
                        "Root access is required to install+verify unattended. "
                        + "The non-root install path opens the system installer UI and needs a "
                        + "human tap, which this autonomous loop cannot wait for or confirm. "
                        + "Install/verify manually, or grant root, then retry.");
            }

            context.reportProgress("Installing APK via root pm install…", 10);
            ToolResult installResult = installApkAsRoot(apk);
            if (!installResult.isSuccess()) {
                return installResult;
            }

            // Timestamp captured right before launch so the post-launch logcat read
            // only contains lines from this run, not stale crash logs from earlier attempts.
            String launchTimestamp = currentLogcatTimestamp();

            context.reportProgress("Launching " + packageName + "…", 40);
            ToolResult launchResult = launchApp(appContext, packageName);
            if (!launchResult.isSuccess()) {
                return launchResult;
            }

            context.reportProgress("Watching for crash for " + waitSeconds + "s…", 55);
            try {
                Thread.sleep(waitSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolResult.failure(null, "Verification interrupted.");
            }

            context.reportProgress("Checking logcat for crash…", 90);
            CrashCheckResult crashCheck = checkForCrash(packageName, launchTimestamp);

            JsonObject result = new JsonObject();
            result.addProperty("sc_id", scId);
            result.addProperty("package_name", packageName);
            result.addProperty("wait_seconds", waitSeconds);
            result.addProperty("crashed", crashCheck.crashed);

            if (crashCheck.checkFailed) {
                // The check itself broke -- this is NOT a verified "no crash" result.
                result.addProperty("status", "crash_check_failed");
                result.addProperty("detail", crashCheck.relevantLog);
                return ToolResult.failure(null, result.toString());
            }

            if (crashCheck.crashed) {
                result.addProperty("status", "crashed_on_launch");
                result.addProperty("crash_log", crashCheck.relevantLog);
                return ToolResult.failure(null, result.toString());
            }

            result.addProperty("status", "launched_no_crash_detected");
            result.addProperty("note",
                    "No crash observed within " + waitSeconds + "s of launch. This confirms the app "
                    + "did not crash on startup — it does NOT confirm any feature works correctly.");
            return ToolResult.success(null, result.toString());
        }

        // ── Root install (mirrors DesignActivity.installBuiltApk's root branch) ──

        private boolean isRootAvailable() {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                int exit = p.waitFor();
                return exit == 0;
            } catch (Exception e) {
                return false;
            }
        }

        private ToolResult installApkAsRoot(File apk) {
            try {
                long length = apk.length();
                Process p = Runtime.getRuntime().exec(new String[]{
                        "su", "-c", "cat '" + apk.getAbsolutePath() + "' | pm install -S " + length
                });
                String out = readAll(p.getInputStream());
                String err = readAll(p.getErrorStream());
                int exit = p.waitFor();
                if (exit != 0 || (out != null && out.toLowerCase().contains("failure"))) {
                    return ToolResult.failure(null, "APK install failed (exit=" + exit + "). stdout: " + out + " stderr: " + err);
                }
                return ToolResult.success(null, "Installed");
            } catch (Exception e) {
                return ToolResult.failure(null, "APK install threw: " + e.getMessage());
            }
        }

        // ── Launch ──

        private ToolResult launchApp(Context appContext, String packageName) {
            try {
                PackageManager pm = appContext.getPackageManager();
                Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                if (launchIntent == null) {
                    return ToolResult.failure(null,
                            "Package " + packageName + " has no launcher activity (or install did not "
                            + "actually complete despite exit code 0).");
                }
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(launchIntent);
                return ToolResult.success(null, "Launched");
            } catch (ActivityNotFoundException e) {
                return ToolResult.failure(null, "No activity found to launch " + packageName + ": " + e.getMessage());
            } catch (Exception e) {
                return ToolResult.failure(null, "Launch failed: " + e.getMessage());
            }
        }

        // ── Crash detection ──

        private static final class CrashCheckResult {
            final boolean crashed;
            final String relevantLog;
            /** True if the crash check itself threw and could not reliably determine crashed/not. */
            final boolean checkFailed;
            CrashCheckResult(boolean crashed, String relevantLog) {
                this(crashed, relevantLog, false);
            }
            CrashCheckResult(boolean crashed, String relevantLog, boolean checkFailed) {
                this.crashed = crashed;
                this.relevantLog = relevantLog;
                this.checkFailed = checkFailed;
            }
        }

        private String currentLogcatTimestamp() {
            // logcat -T accepts "MM-DD HH:MM:SS.mmm"; build it from the device clock so
            // it lines up with logcat's own -v time output format.
            return new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                    .format(new java.util.Date());
        }

        private CrashCheckResult checkForCrash(String packageName, String sinceTimestamp) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{
                        "sh", "-c", "logcat -d -v time -T '" + sinceTimestamp + "'"
                });
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder fullSinceLaunch = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    fullSinceLaunch.append(line).append('\n');
                }
                p.waitFor();

                String log = fullSinceLaunch.toString();
                if (log.isEmpty()) {
                    return new CrashCheckResult(false, null);
                }

                // A crash block is: a "FATAL EXCEPTION" line, in a stretch of log that also
                // mentions this package's name (process tag or stack trace package prefix).
                String[] lines = log.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (FATAL_EXCEPTION.matcher(lines[i]).find()) {
                        // Grab a window around the fatal marker for context, and check the
                        // window mentions our package — otherwise it's another app's crash.
                        int windowStart = Math.max(0, i - 2);
                        int windowEnd = Math.min(lines.length, i + 30);
                        StringBuilder window = new StringBuilder();
                        boolean mentionsPackage = false;
                        for (int j = windowStart; j < windowEnd; j++) {
                            window.append(lines[j]).append('\n');
                            if (lines[j].contains(packageName)) mentionsPackage = true;
                        }
                        if (mentionsPackage) {
                            return new CrashCheckResult(true, window.toString());
                        }
                    }
                }
                return new CrashCheckResult(false, null);
            } catch (Exception e) {
                // Detection failure is NOT the same as "no crash" — surface it distinctly
                // so the caller doesn't misreport a broken check as a clean run.
                return new CrashCheckResult(false, "crash-check itself failed: " + e.getMessage(), true);
            }
        }

        private String readAll(java.io.InputStream in) {
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(in));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }
    }
}
