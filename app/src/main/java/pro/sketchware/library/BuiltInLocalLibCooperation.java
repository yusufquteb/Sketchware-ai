package pro.sketchware.library;

import dev.aldi.sayuti.editor.manage.LibraryDownloaderDialogFragment;
import dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity;
import android.app.Activity;
import android.content.Intent;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Cooperation between Built-in Libraries and Local Library Manager.
 *
 * Built-in libs (e.g. firebase-database-22.0.0) are bundled with Sketchware.
 * Local libs can hold NEWER versions (e.g. firebase-database-23.0.0).
 *
 * Rules:
 * 1. When a built-in lib is ACTIVATED → check if local_libs has a newer version.
 *    If yes → warn user to prefer the local version.
 * 2. When user requests "add to local_library" via built-in update → download
 *    the newer version into local_libs (uses LibraryDownloaderDialogFragment).
 * 3. Corrupt check: verify each excluded/built-in lib folder exists and is non-empty.
 */
public class BuiltInLocalLibCooperation {

    private static final String LOCAL_LIBS_ROOT =
            FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs/";

    // Built-in lib base-name → Maven coordinate
    private static final Map<String, String> BUILTIN_MAVEN = new LinkedHashMap<>();
    static {
        // Firebase
        BUILTIN_MAVEN.put("firebase-auth",      "com.google.firebase:firebase-auth");
        BUILTIN_MAVEN.put("firebase-database",  "com.google.firebase:firebase-database");
        BUILTIN_MAVEN.put("firebase-storage",   "com.google.firebase:firebase-storage");
        BUILTIN_MAVEN.put("firebase-messaging", "com.google.firebase:firebase-messaging");
        BUILTIN_MAVEN.put("firebase-common",    "com.google.firebase:firebase-common");
        BUILTIN_MAVEN.put("firebase-analytics", "com.google.firebase:firebase-analytics");
        BUILTIN_MAVEN.put("firebase-firestore", "com.google.firebase:firebase-firestore");
        // AdMob / Play Services
        BUILTIN_MAVEN.put("play-services-ads",      "com.google.android.gms:play-services-ads");
        BUILTIN_MAVEN.put("play-services-maps",     "com.google.android.gms:play-services-maps");
        BUILTIN_MAVEN.put("play-services-auth",     "com.google.android.gms:play-services-auth");
        BUILTIN_MAVEN.put("play-services-location", "com.google.android.gms:play-services-location");
        BUILTIN_MAVEN.put("play-services-tasks",    "com.google.android.gms:play-services-tasks");
        BUILTIN_MAVEN.put("play-services-base",     "com.google.android.gms:play-services-base");
        // AndroidX / Material
        BUILTIN_MAVEN.put("appcompat",          "androidx.appcompat:appcompat");
        BUILTIN_MAVEN.put("recyclerview",       "androidx.recyclerview:recyclerview");
        BUILTIN_MAVEN.put("material",           "com.google.android.material:material");
        BUILTIN_MAVEN.put("constraintlayout",   "androidx.constraintlayout:constraintlayout");
    }

    // ── Version extraction ─────────────────────────────────────────────────
    private static final Pattern VER_PATTERN =
            Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)*)$");

    public static String extractVersion(String libFolderName) {
        Matcher m = VER_PATTERN.matcher(libFolderName);
        return m.find() ? m.group(1) : null;
    }

    public static String extractBaseName(String libFolderName) {
        Matcher m = VER_PATTERN.matcher(libFolderName);
        if (!m.find()) return libFolderName;
        String stripped = libFolderName.substring(0, libFolderName.length() - m.group(1).length());
        return stripped.replaceAll("[-_]$", "");
    }

    /** Returns 1 if a > b, -1 if a < b, 0 if equal. Semver comparison. */
    public static int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int ia = i < pa.length ? safeInt(pa[i]) : 0;
            int ib = i < pb.length ? safeInt(pb[i]) : 0;
            if (ia != ib) return Integer.compare(ia, ib);
        }
        return 0;
    }

    private static int safeInt(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }

    // ── Newer local version check ──────────────────────────────────────────
    /**
     * Finds local_lib folders that are NEWER than a built-in lib.
     * @param builtInLibName e.g. "firebase-database-22.0.0"
     * @return list of local folder names with newer versions, empty if none
     */
    public static List<String> findNewerLocalVersions(String builtInLibName) {
        String baseName = extractBaseName(builtInLibName);
        String builtInVer = extractVersion(builtInLibName);
        File localDir = new File(LOCAL_LIBS_ROOT);
        File[] folders = localDir.listFiles(File::isDirectory);
        List<String> newer = new ArrayList<>();
        if (folders == null || baseName == null) return newer;
        for (File folder : folders) {
            String fn = folder.getName();
            String fb = extractBaseName(fn);
            if (!fb.equalsIgnoreCase(baseName)) continue;
            String fv = extractVersion(fn);
            if (compareVersions(fv, builtInVer) > 0) newer.add(fn);
        }
        newer.sort((a, b) -> compareVersions(
                extractVersion(b), extractVersion(a))); // newest first
        return newer;
    }

    // ── Corrupt check ─────────────────────────────────────────────────────
    public static class CorruptResult {
        public final String libName;
        public final String reason;
        CorruptResult(String n, String r) { libName = n; reason = r; }
    }

    /** Checks all KNOWN_BUILT_IN_LIBRARIES for missing/empty folders */
    public static List<CorruptResult> checkBuiltInLibrariesIntegrity() {
        File builtInPath = BuiltInLibraries.EXTRACTED_BUILT_IN_LIBRARIES_PATH;
        List<CorruptResult> corrupted = new ArrayList<>();
        for (BuiltInLibraries.BuiltInLibrary lib : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            String name = lib.getName();
            File jar = new File(builtInPath, name + "/classes.jar");
            File dex = new File(BuiltInLibraries.EXTRACTED_BUILT_IN_LIBRARY_DEX_FILES_PATH,
                    name + ".dex");
            if (!jar.exists() && !dex.exists()) {
                corrupted.add(new CorruptResult(name, "Missing JAR and DEX"));
            } else if (jar.exists() && jar.length() == 0) {
                corrupted.add(new CorruptResult(name, "Empty JAR file (0 bytes)"));
            }
        }
        return corrupted;
    }

    // ── Show cooperation dialog when built-in is activated ─────────────────
    /**
     * Call this when a built-in library switch is turned ON.
     * If a newer version exists in local_libs → inform the user.
     * @param activity current activity
     * @param builtInLibName e.g. "firebase-database-22.0.0"
     * @param scId project id (to open local lib manager if user wants)
     */
    public static void checkAndWarnNewerLocal(Activity activity,
                                               String builtInLibName, String scId) {
        List<String> newer = findNewerLocalVersions(builtInLibName);
        if (newer.isEmpty()) return;

        String builtInVer = extractVersion(builtInLibName);
        String newestLocal = newer.get(0);
        String localVer    = extractVersion(newestLocal);

        new MaterialAlertDialogBuilder(activity)
            .setTitle("⚡ Newer version in Local Libraries")
            .setMessage(
                "You activated: " + builtInLibName + " (v" + builtInVer + ")\n\n"
                + "A newer version exists in your Local Libraries:\n"
                + "📦 " + newestLocal + " (v" + localVer + ")\n\n"
                + "Using the local version gives you the latest features and bug fixes.\n\n"
                + "Tip: In Library Manager, disable this built-in library and enable "
                + "\"" + newestLocal + "\" from Local Libraries instead.")
            .setPositiveButton("Open Local Libraries", (d, w) -> {
                Intent intent = new Intent(activity,
                        dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity.class);
                intent.putExtra("sc_id", scId);
                activity.startActivity(intent);
            })
            .setNeutralButton("Keep Built-in", null)
            .show();
    }

    /**
     * Call this to show a "Download newer version to local_libs" dialog.
     * Used when user presses Update in a built-in library context.
     * Does NOT modify the built-in library itself.
     */
    public static void offerDownloadToLocal(Activity activity,
                                             String builtInLibName, String scId) {
        String baseName = extractBaseName(builtInLibName);
        String maven = BUILTIN_MAVEN.get(baseName);
        if (maven == null) {
            SketchwareUtil.toast("No Maven coordinate found for " + baseName);
            return;
        }

        new MaterialAlertDialogBuilder(activity)
            .setTitle("📥 Download Newer Version")
            .setMessage(
                "This will download the latest version of:\n\n"
                + "📦 " + maven + "\n\n"
                + "It will be added to your Local Libraries.\n"
                + "The built-in library remains unchanged.\n\n"
                + "After download, you can exclude the built-in version and\n"
                + "enable the newer local version instead.")
            .setPositiveButton("Download to Local Library", (d, w) -> {
                // Open LibraryDownloaderDialogFragment with prefilled coord
                android.os.Bundle bundle = new android.os.Bundle();
                bundle.putBoolean("notAssociatedWithProject", scId == null);
                bundle.putSerializable("buildSettings",
                        new mod.hey.studios.build.BuildSettings(
                                scId != null ? scId : "system"));
                bundle.putString("localLibFile",
                        FileUtil.getExternalStorageDir()
                        + "/.sketchware/data/" + (scId != null ? scId : "system")
                        + "/local_library");
                bundle.putString("prefillDependency", maven);

                LibraryDownloaderDialogFragment fragment = new LibraryDownloaderDialogFragment();
                fragment.setArguments(bundle);
                fragment.show(
                    ((androidx.appcompat.app.AppCompatActivity) activity)
                        .getSupportFragmentManager(),
                    "builtin_update_downloader");
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Show corrupt check results in a dialog.
     */
    public static void showCorruptCheckDialog(Activity activity) {
        List<CorruptResult> corrupted = checkBuiltInLibrariesIntegrity();
        if (corrupted.isEmpty()) {
            new MaterialAlertDialogBuilder(activity)
                .setTitle("✅ All Built-in Libraries OK")
                .setMessage("All " + BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES.length
                        + " built-in libraries passed integrity check.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(corrupted.size()).append(" corrupted librar")
          .append(corrupted.size() == 1 ? "y" : "ies").append(":\n\n");
        for (CorruptResult r : corrupted) {
            sb.append("❌ ").append(r.libName).append("\n   ").append(r.reason).append("\n\n");
        }
        sb.append("Fix: Go to Settings → Re-extract built-in libraries.");
        new MaterialAlertDialogBuilder(activity)
            .setTitle("⚠️ Corrupted Built-in Libraries")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }
}
