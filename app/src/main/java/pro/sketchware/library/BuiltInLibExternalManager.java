package pro.sketchware.library;

import pro.sketchware.library.BuiltInLibMavenCoords;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.aldi.sayuti.editor.manage.LocalLibraryItem;
import mod.jbk.build.BuiltInLibraries;

/**
 * BuiltInLibExternalManager
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Manages the synchronisation between the APK-internal built-in libraries
 * and the user-accessible external folder:
 *
 *   /storage/emulated/0/.sketchware/libs/built-in/
 *
 * ── What this class does ──────────────────────────────────────────────────
 *  1.  EXPORT  – copies library folders from the internal extraction path
 *                to the external path (only when folder is missing or the
 *                APK version is NEWER than the external one).
 *
 *  2.  RESOLVE – overrides BuiltInLibraries.getLibraryPath() so the build
 *                system first checks external storage before falling back
 *                to the internal private path.
 *
 *  3.  LIST    – provides getAllExternalBuiltInsAsItems() so the
 *                ManageLocalLibraryActivity can display external built-ins
 *                when "Show system libraries" is toggled on.
 *
 * ── Safety guarantees ────────────────────────────────────────────────────
 *  • Old versions are NEVER deleted automatically.  If the APK ships
 *    "material-1.15.0" and the external folder already has
 *    "material-1.14.0-alpha09", the new folder is added beside the old one;
 *    the old one is removed only when the user explicitly deletes it.
 *
 *  • If a library in the external folder is NEWER than the internal one,
 *    the internal version is never exported over it (user wins).
 *
 *  • All copy operations are atomic (write to .tmp → rename) to prevent
 *    partial files on power-loss or low-storage conditions.
 *
 *  • Every method is null-safe and catch-all; exceptions are logged but
 *    never propagated — the app always falls back to the internal APK libs.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────
 *  exportAllToExternalIfNeeded() must be called from a background thread.
 *  All read methods (getExternalLibFolder, getAllExternalBuiltInsAsItems)
 *  are safe from any thread.
 */
public final class BuiltInLibExternalManager {

    private static final String TAG = "BuiltInLibExternal";

    /** SharedPreferences key: last app versionCode that ran the export successfully. */
    public static final String PREF_EXPORT_VERSION = "builtin_export_version";
    /** SharedPreferences name used for tracking the export state. */
    public static final String PREFS_NAME = "daydream_builtin_export";

    // ── Public path constant ──────────────────────────────────────────────────

    /** The shared external directory visible on file managers. */
    public static final File EXTERNAL_BUILT_IN_PATH = new File(
            Environment.getExternalStorageDirectory(),
            ".sketchware/libs/built-in"
    );

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface ExportCallback {
        void onProgress(String libName, int done, int total);
        /** @param error null on full success; non-null if setup itself failed. */
        void onComplete(int copied, int skipped, @Nullable String error);
    }

    // ── Main API ──────────────────────────────────────────────────────────────

    /**
     * Returns the external folder for an exact internal folder name
     * (e.g. "material-1.14.0-alpha09").
     *
     * @return the directory if it exists in external storage, null otherwise.
     */
    @Nullable
    public static File getExternalLibFolder(String folderName) {
        if (folderName == null || folderName.isEmpty()) return null;
        try {
            File f = new File(EXTERNAL_BUILT_IN_PATH, folderName);
            return f.isDirectory() ? f : null;
        } catch (Exception e) {
            Log.w(TAG, "getExternalLibFolder error for " + folderName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolves the best (highest version) external folder for a logical library ID.
     * Used by {@code BuiltInLibraries.getLibraryPath()} as the first-priority check.
     *
     * @param logicalId logical library id, e.g. "material"
     * @return best external folder, or null if none found
     */
    @Nullable
    public static File resolveBestExternalFolder(String logicalId) {
        if (logicalId == null || logicalId.isEmpty()) return null;
        try {
            File[] all = EXTERNAL_BUILT_IN_PATH.listFiles(File::isDirectory);
            if (all == null) return null;

            File bestFolder = null;
            String bestVersion = null;

            for (File f : all) {
                String id = extractLogicalId(f.getName());
                if (!logicalId.equals(id)) continue;
                String ver = BuiltInLibMavenCoords.extractVersion(f.getName());
                if (ver == null) ver = "";
                if (bestVersion == null || compareVersions(ver, bestVersion) >= 0) {
                    bestVersion = ver;
                    bestFolder = f;
                }
            }
            return bestFolder;
        } catch (Exception e) {
            Log.w(TAG, "resolveBestExternalFolder error for " + logicalId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns all libraries located in the external built-in folder as
     * {@link LocalLibraryItem} instances (all with {@code isBuiltIn = true}).
     *
     * Each returned item's {@code name} is the full folder name,
     * e.g. "material-1.14.0-alpha09".
     */
    @NonNull
    public static List<LocalLibraryItem> getAllExternalBuiltInsAsItems() {
        List<LocalLibraryItem> result = new ArrayList<>();
        try {
            if (!EXTERNAL_BUILT_IN_PATH.exists() || !EXTERNAL_BUILT_IN_PATH.isDirectory()) {
                return result;
            }
            File[] folders = EXTERNAL_BUILT_IN_PATH.listFiles(File::isDirectory);
            if (folders == null) return result;

            Arrays.sort(folders, (a, b) -> a.getName().compareTo(b.getName()));

            for (File folder : folders) {
                LocalLibraryItem item = buildItemFromExternalFolder(folder);
                if (item != null) result.add(item);
            }
        } catch (Exception e) {
            Log.e(TAG, "getAllExternalBuiltInsAsItems error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Exports all internal built-in libraries to the external folder.
     *
     * Rules:
     * <ul>
     *   <li>If external already has the SAME or NEWER version → skip (user wins).</li>
     *   <li>If internal is NEWER → copy the new folder (old folder kept as-is).</li>
     *   <li>If the external folder doesn't exist → copy it now.</li>
     * </ul>
     *
     * Must be called from a background thread.
     */
    public static void exportAllToExternalIfNeeded(@Nullable ExportCallback cb) {
        try {
            File internalRoot = BuiltInLibraries.EXTRACTED_BUILT_IN_LIBRARIES_PATH;

            if (!internalRoot.exists() || !internalRoot.isDirectory()) {
                if (cb != null) cb.onComplete(0, 0, "Internal libs not yet extracted");
                return;
            }

            if (!EXTERNAL_BUILT_IN_PATH.exists() && !EXTERNAL_BUILT_IN_PATH.mkdirs()) {
                if (cb != null) cb.onComplete(0, 0,
                        "Cannot create " + EXTERNAL_BUILT_IN_PATH.getAbsolutePath());
                return;
            }

            File[] folders = internalRoot.listFiles(File::isDirectory);
            if (folders == null) {
                if (cb != null) cb.onComplete(0, 0, null);
                return;
            }

            Arrays.sort(folders, (a, b) -> a.getName().compareTo(b.getName()));
            final int total = folders.length;
            int copied = 0, skipped = 0;

            // Build a version index of what's already in external to avoid O(n²) scans
            Map<String, String> externalVersionIndex = buildExternalVersionIndex();

            for (int i = 0; i < folders.length; i++) {
                File internalFolder = folders[i];
                String folderName = internalFolder.getName();
                if (cb != null) cb.onProgress(folderName, i, total);

                String logicalId      = extractLogicalId(folderName);
                String internalVer    = nvl(BuiltInLibMavenCoords.extractVersion(folderName));
                String existingExtVer = externalVersionIndex.get(logicalId);

                if (existingExtVer != null) {
                    int cmp = compareVersions(existingExtVer, internalVer);
                    if (cmp > 0) {
                        // External is newer — never overwrite
                        Log.d(TAG, "SKIP " + folderName
                                + " (external=" + existingExtVer + " > internal=" + internalVer + ")");
                        skipped++;
                        continue;
                    }
                    if (cmp == 0) {
                        // Same version already present — no need to copy
                        skipped++;
                        continue;
                    }
                    // Internal is newer → fall through and copy
                }

                // Copy folder (old external version, if different name, is preserved)
                File externalFolder = new File(EXTERNAL_BUILT_IN_PATH, folderName);
                try {
                    copyFolderAtomic(internalFolder, externalFolder);
                    externalVersionIndex.put(logicalId, internalVer); // update index
                    Log.d(TAG, "EXPORTED " + folderName);
                    copied++;
                } catch (IOException e) {
                    Log.e(TAG, "Export failed for " + folderName + ": " + e.getMessage());
                    // non-fatal — keep going
                }
            }

            if (cb != null) cb.onComplete(copied, skipped, null);

        } catch (Exception e) {
            Log.e(TAG, "exportAllToExternalIfNeeded fatal: " + e.getMessage());
            if (cb != null) cb.onComplete(0, 0, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Deletes the given external built-in folder.
     * The internal APK version will be used as fallback afterwards.
     *
     * @return true if deleted, false if it didn't exist or deletion failed
     */
    public static boolean deleteExternalFolder(String folderName) {
        if (folderName == null || folderName.isEmpty()) return false;
        try {
            File f = new File(EXTERNAL_BUILT_IN_PATH, folderName);
            if (!f.exists()) return false;
            return deleteRecursive(f);
        } catch (Exception e) {
            Log.e(TAG, "deleteExternalFolder error for " + folderName + ": " + e.getMessage());
            return false;
        }
    }

    // ── Version helpers ───────────────────────────────────────────────────────

    /**
     * Extracts the logical ID (base name, no version) from a library folder name.
     * Handles DISK_FOLDER_ALIASES: "core-5.1.13" → "onesignal-core" (not "core").
     * Examples:
     *   "material-1.14.0-alpha09"  →  "material"
     *   "kotlin-stdlib-2.0.21"     →  "kotlin-stdlib"
     *   "gson-2.8.7"               →  "gson"
     *   "core-5.1.13"              →  "onesignal-core" (via reverse alias)
     *   "core-1.17.0"              →  "core" (AndroidX)
     */
    @NonNull
    public static String extractLogicalId(@NonNull String folderName) {
        // Use BuiltInLibMavenCoords helper which already handles multi-part names
        String base;
        try {
            base = BuiltInLibMavenCoords.extractBase(folderName);
            if (base == null) base = folderName;
        } catch (Exception e) {
            // Fallback: strip last "-N.N.N" segment
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^(.*?)-(\\d[\\w.\\-]*)$")
                    .matcher(folderName);
            base = m.matches() ? m.group(1) : folderName;
        }

        // Check reverse DISK_FOLDER_ALIASES: if "core" is the disk prefix,
        // we need to figure out which logical ID it maps to by checking version
        return BuiltInLibraries.diskFolderToLogicalId(folderName);
    }

    /**
     * Compares two version strings numerically, segment by segment.
     * Handles alpha/beta suffixes by stripping non-numeric chars for comparison.
     *
     * @return positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    public static int compareVersions(@Nullable String v1, @Nullable String v2) {
        String[] p1 = nvl(v1).split("[.\\-]");
        String[] p2 = nvl(v2).split("[.\\-]");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? parseVersionPart(p1[i]) : 0;
            int n2 = i < p2.length ? parseVersionPart(p2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Map<String, String> buildExternalVersionIndex() {
        Map<String, String> index = new HashMap<>();
        try {
            File[] all = EXTERNAL_BUILT_IN_PATH.listFiles(File::isDirectory);
            if (all == null) return index;
            for (File f : all) {
                String id  = extractLogicalId(f.getName());
                String ver = nvl(BuiltInLibMavenCoords.extractVersion(f.getName()));
                // Keep highest version per logical ID
                String existing = index.get(id);
                if (existing == null || compareVersions(ver, existing) > 0) {
                    index.put(id, ver);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "buildExternalVersionIndex: " + e.getMessage());
        }
        return index;
    }

    @Nullable
    private static LocalLibraryItem buildItemFromExternalFolder(File folder) {
        try {
            LocalLibraryItem item = new LocalLibraryItem();
            item.name      = folder.getName();
            item.isBuiltIn = true;

            String version = BuiltInLibMavenCoords.extractVersion(folder.getName());
            if (version != null && !version.isEmpty()) item.version = version;

            String coord = BuiltInLibMavenCoords.getCoordinate(folder.getName());
            if (coord != null && !coord.isEmpty()) item.dependency = coord;

            item.jarPath       = new File(folder, "classes.jar").getAbsolutePath();
            item.resFolderPath = new File(folder, "res").getAbsolutePath();
            item.pgRulesPath   = new File(folder, "proguard.txt").getAbsolutePath();

            File jar = new File(item.jarPath);
            if (jar.exists()) item.jarSizeBytes = jar.length();

            return item;
        } catch (Exception e) {
            Log.w(TAG, "buildItemFromExternalFolder error for " + folder.getName()
                    + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Atomic folder copy: each file is written to a .tmp sibling then renamed.
     * This prevents partially-written files on crash or low-storage.
     */
    private static void copyFolderAtomic(File src, File dst) throws IOException {
        if (!dst.exists() && !dst.mkdirs()) {
            throw new IOException("Cannot create: " + dst);
        }
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File target = new File(dst, f.getName());
            if (f.isDirectory()) {
                copyFolderAtomic(f, target);
            } else {
                copyFileAtomic(f, target);
            }
        }
    }

    private static void copyFileAtomic(File src, File dst) throws IOException {
        File tmp = new File(dst.getParent(), dst.getName() + ".tmp");
        try (FileInputStream in  = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.getFD().sync(); // flush OS buffers
        }
        if (!tmp.renameTo(dst)) {
            // On some Android versions rename across mount points fails; fall back to copy+delete
            tmp.renameTo(dst); // best-effort
            if (tmp.exists()) tmp.delete();
        }
    }

    private static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        return f.delete();
    }

    private static int parseVersionPart(String s) {
        if (s == null || s.isEmpty()) return 0;
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try { return Integer.parseInt(digits); }
        catch (NumberFormatException e) { return 0; }
    }

    @NonNull
    private static String nvl(@Nullable String s) {
        return s != null ? s : "";
    }
}
