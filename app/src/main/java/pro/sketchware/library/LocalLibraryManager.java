package pro.sketchware.library;

import pro.sketchware.library.BuiltInLibExternalManager;

import mod.jbk.build.BuiltInLibraries;

import dev.aldi.sayuti.editor.manage.LocalLibraryItem;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Single source of truth for all local library file operations.
 *
 * Directory layout (on external storage):
 * <pre>
 *   .sketchware/libs/local_libs/
 *     {libraryName}/
 *       library.json      ← LibraryMetadata (new format)
 *       dependency        ← Maven coordinate (legacy v1 format)
 *       id                ← base name without version (legacy v1 format)
 *       classes.jar
 *       res/
 *       pgRules.pro
 * </pre>
 *
 * Built-in libraries use the SAME directory, but their library.json
 * has isBuiltIn = true.
 *
 * MIGRATION: When only the legacy "dependency" file exists (no library.json),
 * this manager reads the coordinate and extracts the version automatically.
 */
public class LocalLibraryManager {

    private static final String TAG = "LocalLibraryManager";

    // ── Paths ─────────────────────────────────────────────────────────────────

    public static String getSketchwarePath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.sketchware";
    }

    public static String getLocalLibsPath() {
        return getSketchwarePath() + "/libs/local_libs/";
    }

    /** Alias used by LibraryProjectLinker for absolute-path fixing (P10) */
    public static String getLocalLibsRoot() {
        return getLocalLibsPath();
    }

    public static String getProjectsDataPath() {
        return getSketchwarePath() + "/data/";
    }

    public static String getLibraryPath(String libName) {
        return getLocalLibsPath() + libName + "/";
    }

    public static String getMetadataPath(String libName) {
        return getLibraryPath(libName) + "library.json";
    }

    public static String getJarPath(String libName) {
        return getLibraryPath(libName) + "classes.jar";
    }

    public static String getResFolderPath(String libName) {
        return getLibraryPath(libName) + "res/";
    }

    public static String getPgRulesPath(String libName) {
        return getLibraryPath(libName) + "pgRules.pro";
    }

    // ── GSON ──────────────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns all libraries stored in local_libs, plus (when includeBuiltIn=true)
     * all libraries found in the external built-in folder:
     *   /storage/emulated/0/.sketchware/libs/built-in/
     *
     * Deduplication rule: if a library appears in BOTH local_libs AND the external
     * built-in folder (matched by logical base-name), the local_libs entry wins
     * (it is more user-controllable) and the external duplicate is omitted.
     *
     * @param includeBuiltIn when false built-in libraries are skipped entirely
     */
    public static List<LocalLibraryItem> getAllLibraries(boolean includeBuiltIn) {
        List<LocalLibraryItem> result = new ArrayList<>();

        // ── 1. Scan local_libs (existing behaviour) ───────────────────────────
        java.util.Set<String> seenLogicalIds = new java.util.HashSet<>();

        File root = new File(getLocalLibsPath());
        if (root.exists() && root.isDirectory()) {
            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs != null) {
                Arrays.sort(dirs, Comparator.comparing(File::getName));
                for (File dir : dirs) {
                    LocalLibraryItem item = loadLibrary(dir.getName());
                    if (item == null) continue;
                    if (!includeBuiltIn && item.isBuiltIn) continue;
                    result.add(item);
                    // Track logical ID to deduplicate against external built-ins
                    String logicalId = pro.sketchware.library.BuiltInLibExternalManager
                            .extractLogicalId(item.name);
                    seenLogicalIds.add(logicalId);
                    seenLogicalIds.add(item.name); // also track exact name
                }
            }
        }

        // ── 2. Append external built-ins (when requested) ─────────────────────
        if (includeBuiltIn) {
            try {
                List<LocalLibraryItem> externalBuiltIns =
                        pro.sketchware.library.BuiltInLibExternalManager
                                .getAllExternalBuiltInsAsItems();

                for (LocalLibraryItem extItem : externalBuiltIns) {
                    String logicalId = pro.sketchware.library.BuiltInLibExternalManager
                            .extractLogicalId(extItem.name);

                    // Skip if local_libs already has an entry for this logical lib
                    if (seenLogicalIds.contains(logicalId)
                            || seenLogicalIds.contains(extItem.name)) {
                        continue;
                    }

                    // Populate project counts (project JSON stores logical IDs)
                    extItem.jarPath       = new File(
                            pro.sketchware.library.BuiltInLibExternalManager.EXTERNAL_BUILT_IN_PATH,
                            extItem.name + "/classes.jar").getAbsolutePath();
                    extItem.resFolderPath = new File(
                            pro.sketchware.library.BuiltInLibExternalManager.EXTERNAL_BUILT_IN_PATH,
                            extItem.name + "/res").getAbsolutePath();
                    extItem.pgRulesPath   = new File(
                            pro.sketchware.library.BuiltInLibExternalManager.EXTERNAL_BUILT_IN_PATH,
                            extItem.name + "/proguard.txt").getAbsolutePath();

                    result.add(extItem);
                    seenLogicalIds.add(logicalId);
                    seenLogicalIds.add(extItem.name);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not load external built-in libs: " + e.getMessage());
                // non-fatal — local_libs items are already collected
            }
        }

        return result;
    }

    /**
     * Loads a single library by its folder name.
     *
     * Resolution order:
     * 1. library.json (new format — v2)
     * 2. dependency + id files (legacy format — v1)
     * 3. Infer version from folder name suffix  (e.g. "retrofit-2.9.0")
     *
     * Returns null if the folder does not exist.
     */
    public static LocalLibraryItem loadLibrary(String libName) {
        if (libName == null || libName.trim().isEmpty()) return null;
        File dir = new File(getLibraryPath(libName));
        if (!dir.exists() || !dir.isDirectory()) return null;

        LocalLibraryItem item;

        // ── 1. Try library.json ───────────────────────────────────────────────
        File metaFile = new File(getMetadataPath(libName));
        if (metaFile.exists()) {
            try {
                String json = readFile(metaFile);
                LibraryMetadata meta = GSON.fromJson(json, LibraryMetadata.class);
                item = (meta != null) ? meta.toItem() : new LocalLibraryItem();
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse library.json for " + libName, e);
                item = new LocalLibraryItem();
            }
        } else {
            item = new LocalLibraryItem();
        }

        item.name = libName;

        // ── Mark as built-in if any known source says so ──────────────────────
        // Priority: library.json flag > BuiltInLibraryHelper (15 migrated) > BuiltInLibraries.KNOWN (~200+)
        if (!item.isBuiltIn) {
            item.isBuiltIn = BuiltInLibraryHelper.isBuiltIn(libName)
                    || mod.jbk.build.BuiltInLibraries.BuiltInLibrary.ofName(libName).isPresent();
        }

        // ── 2. Read legacy "dependency" file if version still unknown ─────────
        if (isDefaultVersion(item.version)) {
            File depFile = new File(getLibraryPath(libName) + "dependency");
            if (depFile.exists()) {
                try {
                    String dep = readFile(depFile).trim();
                    if (!dep.isEmpty()) {
                        if (item.dependency == null || item.dependency.isEmpty()) {
                            item.dependency = dep;
                        }
                        // Extract version from "group:artifact:version" coordinate
                        String[] parts = dep.split(":");
                        if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                            item.version = parts[2].trim();
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Cannot read dependency file for " + libName, e);
                }
            }
        }

        // ── 3. Infer version from folder name (e.g. "retrofit-2.9.0") ─────────
        if (isDefaultVersion(item.version)) {
            String inferred = inferVersionFromName(libName);
            if (inferred != null) item.version = inferred;
        }

        // ── 4. Populate file paths ─────────────────────────────────────────────
        item.jarPath       = getJarPath(libName);
        item.resFolderPath = getResFolderPath(libName);
        item.pgRulesPath   = getPgRulesPath(libName);

        // ── 5. Persist back as library.json if we didn't have one ─────────────
        if (!metaFile.exists() && !isDefaultVersion(item.version)) {
            try {
                saveMetadata(item);
            } catch (Exception ignored) {}
        }

        item.isUpdateAvailable = item.hasUpdate();
        return item;
    }

    /**
     * Find the first library whose folder name equals {@code name} exactly.
     */
    public static LocalLibraryItem getLibraryByName(String name) {
        return loadLibrary(name);
    }

    /**
     * Find a library by base name (ignoring trailing "-X.Y.Z" version suffix).
     * Returns the highest-versioned match, or null.
     */
    public static LocalLibraryItem findLibraryByBaseName(String baseName) {
        if (baseName == null || baseName.trim().isEmpty()) return null;
        File root = new File(getLocalLibsPath());
        if (!root.exists()) return null;
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return null;

        // 1. Exact match
        for (File d : dirs) {
            if (d.getName().equalsIgnoreCase(baseName)) return loadLibrary(d.getName());
        }

        // 2. Find all matching folders and return the highest version
        List<File> candidates = new ArrayList<>();
        for (File d : dirs) {
            String folderBase = d.getName().replaceAll("-\\d+(\\.\\d+)*$", "");
            if (folderBase.equalsIgnoreCase(baseName)) candidates.add(d);
        }
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return loadLibrary(candidates.get(0).getName());

        // Return highest version
        candidates.sort((a, b) -> compareVersionSuffix(b.getName(), a.getName()));
        return loadLibrary(candidates.get(0).getName());
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public static void saveMetadata(LocalLibraryItem item) {
        if (item == null || item.name == null || item.name.trim().isEmpty()) return;
        File dir = new File(getLibraryPath(item.name));
        if (!dir.exists()) dir.mkdirs();

        LibraryMetadata meta = LibraryMetadata.fromItem(item);
        String json = GSON.toJson(meta);
        try {
            writeFile(new File(getMetadataPath(item.name)), json);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write library.json for " + item.name, e);
        }
    }

    public static void createLibraryFolder(String libName) {
        File dir = new File(getLibraryPath(libName));
        dir.mkdirs();
        new File(dir, "res").mkdirs();
    }

    public static boolean deleteLibrary(String libName) {
        // Proposal 6: refuse to delete a library that is currently being updated
        if (LibraryUpdateManager.ACTIVE_UPDATES.contains(libName)) {
            Log.w(TAG, "Cannot delete " + libName + " — update in progress");
            return false;
        }

        // If this is an external built-in (folder name like "material-1.14.0-alpha09"),
        // delete from the external built-in folder — NOT from local_libs.
        // The internal APK version will be used as fallback after deletion.
        File extFolder = pro.sketchware.library.BuiltInLibExternalManager
                .getExternalLibFolder(libName);
        if (extFolder != null) {
            // Backup before deleting external built-in
            backupLibraryToTrash(libName, extFolder);
            boolean deleted = pro.sketchware.library.BuiltInLibExternalManager
                    .deleteExternalFolder(libName);
            Log.i(TAG, "Deleted external built-in: " + libName + " → " + deleted);
            return deleted;
        }

        // Standard local_libs deletion — backup first!
        File dir = new File(getLibraryPath(libName));
        if (dir.exists()) {
            backupLibraryToTrash(libName, dir);
        }
        return deleteRecursive(dir);
    }

    /**
     * Auto-backup a library folder to .sketchware/libs/.trash/ before deletion.
     * Keeps up to 3 most recent backups per library name.
     * Backup is timestamped so accidental deletes can be recovered.
     */
    private static void backupLibraryToTrash(String libName, File sourceDir) {
        try {
            File trashRoot = new File(Environment.getExternalStorageDirectory(),
                    ".sketchware/libs/.trash");
            if (!trashRoot.exists()) trashRoot.mkdirs();

            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                    java.util.Locale.US).format(new java.util.Date());
            String safeName = libName.replaceAll("[^a-zA-Z0-9._-]", "_");
            File backupDir = new File(trashRoot, safeName + "_" + timestamp);

            // Copy the entire library folder
            copyDirectoryRecursive(sourceDir, backupDir);
            Log.i(TAG, "Backed up library \"" + libName + "\" to: " + backupDir.getAbsolutePath());

            // Prune old backups: keep only the 3 most recent
            pruneOldBackups(trashRoot, safeName, 3);
        } catch (Exception e) {
            Log.w(TAG, "Failed to backup library \"" + libName + "\" before deletion: " + e.getMessage());
            // Don't block deletion — backup is best-effort
        }
    }

    /**
     * Keep only the N most recent backups matching the given prefix.
     */
    private static void pruneOldBackups(File trashRoot, String prefix, int keepCount) {
        File[] existing = trashRoot.listFiles((d, n) -> n.startsWith(prefix + "_"));
        if (existing == null || existing.length <= keepCount) return;
        Arrays.sort(existing, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < existing.length - keepCount; i++) {
            deleteRecursive(existing[i]);
            Log.d(TAG, "Pruned old backup: " + existing[i].getName());
        }
    }

    /**
     * Recursively copy a directory.
     */
    private static void copyDirectoryRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyDirectoryRecursive(child, new File(dst, child.getName()));
                }
            }
        } else {
            copyFile(src, dst);
        }
    }

    /**
     * Restores a library from the most recent backup in .trash/ .
     *
     * @return true if restoration succeeded
     */
    public static boolean restoreFromTrash(String libName) {
        try {
            File trashRoot = new File(Environment.getExternalStorageDirectory(),
                    ".sketchware/libs/.trash");
            if (!trashRoot.exists()) return false;

            String safeName = libName.replaceAll("[^a-zA-Z0-9._-]", "_");
            File[] backups = trashRoot.listFiles((d, n) -> n.startsWith(safeName + "_"));
            if (backups == null || backups.length == 0) return false;

            // Find most recent
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            File latest = backups[backups.length - 1];

            File targetDir = new File(getLibraryPath(libName));
            if (targetDir.exists()) {
                Log.w(TAG, "Library folder already exists, not overwriting: " + libName);
                return false;
            }
            copyDirectoryRecursive(latest, targetDir);
            Log.i(TAG, "Restored library \"" + libName + "\" from: " + latest.getName());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore library from trash: " + libName, e);
            return false;
        }
    }

    /**
     * Explicitly deletes only the external built-in copy of a library,
     * leaving the local_libs entry (if any) intact.
     *
     * @return true if the external copy was removed (or didn't exist)
     */
    public static boolean deleteExternalBuiltInOnly(String folderName) {
        return pro.sketchware.library.BuiltInLibExternalManager
                .deleteExternalFolder(folderName);
    }

    public static LocalLibraryItem renameLibrary(String oldName, String newName) {
        File oldDir = new File(getLibraryPath(oldName));
        File newDir = new File(getLibraryPath(newName));
        if (!oldDir.exists()) return null;
        if (newDir.exists()) {
            Log.w(TAG, "Target folder already exists: " + newName);
            return null;
        }
        if (!oldDir.renameTo(newDir)) {
            Log.e(TAG, "Failed to rename " + oldName + " → " + newName);
            return null;
        }
        LocalLibraryItem item = loadLibrary(newName);
        if (item != null) {
            item.name = newName;
            saveMetadata(item);
        }
        return item;
    }

    public static boolean copyJarFromAssets(Context ctx, String assetPath, String libName)
            throws IOException {
        File outJar = new File(getJarPath(libName));
        File outDir = outJar.getParentFile();
        if (outDir != null && !outDir.exists()) outDir.mkdirs();

        try (InputStream in = ctx.getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(outJar)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        return outJar.exists();
    }

    public static boolean copyJarFromFile(File sourceJar, String libName) throws IOException {
        File outJar = new File(getJarPath(libName));
        File outDir = outJar.getParentFile();
        if (outDir != null && !outDir.exists()) outDir.mkdirs();
        copyFile(sourceJar, outJar);
        return outJar.exists();
    }

    // ── Existence checks ──────────────────────────────────────────────────────

    public static boolean libraryExists(String libName) {
        return new File(getLibraryPath(libName)).isDirectory();
    }

    public static boolean libraryHasJar(String libName) {
        return new File(getJarPath(libName)).isFile();
    }

    public static void ensureLocalLibsDirExists() {
        File dir = new File(getLocalLibsPath());
        if (!dir.exists()) dir.mkdirs();
    }

    /**
     * Returns all libraries that have no projects using them.
     */
    public static List<LocalLibraryItem> getUnusedLibraries() {
        List<LocalLibraryItem> all = getAllLibraries(true);
        LibraryProjectLinker.populateProjectCounts(all);
        List<LocalLibraryItem> unused = new ArrayList<>();
        for (LocalLibraryItem item : all) {
            if (item.projectCount == 0) unused.add(item);
        }
        return unused;
    }

    /**
     * Deletes libraries that are used in more than one copy (same base name,
     * different version numbers), keeping only the highest version.
     *
     * @return count of deleted duplicates
     */
    public static int deleteDuplicates() {
        List<LocalLibraryItem> all = getAllLibraries(true);
        java.util.Map<String, List<LocalLibraryItem>> byBase = new java.util.LinkedHashMap<>();
        for (LocalLibraryItem item : all) {
            String base = item.name.replaceAll("-\\d+(\\.\\d+)*$", "").toLowerCase();
            byBase.computeIfAbsent(base, k -> new ArrayList<>()).add(item);
        }
        int deleted = 0;
        for (List<LocalLibraryItem> group : byBase.values()) {
            if (group.size() <= 1) continue;
            // Sort by version descending (highest first)
            group.sort((a, b) -> compareVersions(b.version, a.version));
            // Delete all but the first (highest version)
            for (int i = 1; i < group.size(); i++) {
                if (deleteLibrary(group.get(i).name)) deleted++;
            }
        }
        return deleted;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** True if version is still the default placeholder */
    private static boolean isDefaultVersion(String v) {
        return v == null || v.isEmpty() || v.equals("1.0.0") || v.equals("1.0");
    }

    /** Extracts version from folder name suffix, e.g. "retrofit-2.9.0" → "2.9.0" */
    private static String inferVersionFromName(String folderName) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("-(\\d+(?:\\.\\d+)*)$")
                .matcher(folderName);
        return m.find() ? m.group(1) : null;
    }

    /** Compares two version strings (simple numeric comparison) */
    public static int compareVersions(String a, String b) {
        if (a == null) a = "0";
        if (b == null) b = "0";
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int ia = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int ib = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (ia != ib) return Integer.compare(ia, ib);
        }
        return 0;
    }

    private static int compareVersionSuffix(String folderA, String folderB) {
        String va = inferVersionFromName(folderA);
        String vb = inferVersionFromName(folderB);
        return compareVersions(va, vb);
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }

    private static String readFile(File f) throws IOException {
        return AtomicFileWriter.read(f);
    }

    private static void writeFile(File f, String content) throws IOException {
        AtomicFileWriter.write(f, content);  // Proposal 19: atomic write
    }

    private static void copyFile(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
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
}
