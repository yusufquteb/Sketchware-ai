package pro.sketchware.library;

import pro.sketchware.library.LocalLibraryMetadata;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity;
import mod.jbk.build.BuiltInLibraries;
import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mod.hey.studios.util.Helper;
import pro.sketchware.utility.FileUtil;

/**
 * Checks whether a project's required local libraries are present on disk.
 * When a library folder is missing (e.g. after an update), finds the best
 * available substitute and offers it to the user — never changes anything
 * automatically without explicit user confirmation.
 *
 * <p><b>Built-in libraries are auto-migrated silently:</b> if a project
 * references a known built-in (from {@link BuiltInLibraryHelper}) that is
 * missing, the best available version in local_libs is applied automatically
 * without showing a dialog, because the user never explicitly chose the
 * old name — it was assigned by the app.
 */
public class LibraryConflictChecker {

    private static final String TAG = "LibConflictChecker";

    public static class Conflict {
        /** The library entry from the project's local_library JSON */
        public final String requiredName;
        /** The required version parsed from the name */
        @Nullable public final String requiredVersion;
        /** Best available substitute on disk (null if nothing found) */
        @Nullable public final String availableName;
        @Nullable public final String availableVersion;
        /** Is this a major version jump? (potentially breaking) */
        public final boolean isMajorChange;

        Conflict(String reqName, String reqVer,
                 String avName, String avVer, boolean isMajor) {
            requiredName     = reqName;
            requiredVersion  = reqVer;
            availableName    = avName;
            availableVersion = avVer;
            isMajorChange    = isMajor;
        }

        public boolean hasSubstitute() { return availableName != null; }

        public String describeChange() {
            if (!hasSubstitute()) return "v" + requiredVersion + " — no substitute found";
            return "v" + requiredVersion + " → v" + availableVersion
                    + (isMajorChange ? "  ⚠️ major version change" : "");
        }
    }

    /**
     * Checks all libraries used by the given project.
     * Returns a list of Conflict objects — one per missing library.
     * Empty list = everything is fine.
     *
     * <p>Built-in libraries that are missing are <em>silently auto-migrated</em>
     * to the best available version in local_libs before returning — they will
     * NOT appear in the returned conflict list.
     */
    public static List<Conflict> checkProjectLibraries(String scId) {
        List<Conflict> conflicts = new ArrayList<>();
        String localLibsRoot = FileUtil.getExternalStorageDir()
                + "/.sketchware/libs/local_libs/";

        // Load the project's local_library JSON
        File libFile = LocalLibrariesUtil.getLocalLibFile(scId);
        if (!libFile.exists()) return conflicts;

        String content = FileUtil.readFile(libFile.getAbsolutePath());
        if (content == null || content.trim().isEmpty()) return conflicts;

        List<HashMap<String, Object>> projectLibs;
        try {
            projectLibs = new Gson().fromJson(content, Helper.TYPE_MAP_LIST);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse local_library for project " + scId, e);
            return conflicts;
        }

        // First pass: auto-migrate built-in libraries silently
        boolean anyAutoMigrated = autoMigrateBuiltIns(scId, projectLibs, localLibsRoot);
        if (anyAutoMigrated) {
            // Re-read after migration
            content = FileUtil.readFile(libFile.getAbsolutePath());
            try {
                projectLibs = new Gson().fromJson(content, Helper.TYPE_MAP_LIST);
            } catch (Exception ignored) {}
        }

        // Second pass: report remaining missing libraries as conflicts
        for (HashMap<String, Object> entry : projectLibs) {
            Object nameObj = entry.get("name");
            if (nameObj == null) continue;
            String libName = nameObj.toString();

            File libFolder = new File(localLibsRoot + libName);
            if (libFolder.exists() && libFolder.isDirectory()) continue;

            // Missing — try to find best substitute
            String reqVersion = LibraryVersionChecker.extractVersionFromName(libName);
            String[] best = findBestSubstitute(libName, reqVersion, localLibsRoot);

            boolean isMajor = false;
            if (best != null && reqVersion != null && best[1] != null) {
                isMajor = isMajorVersionChange(reqVersion, best[1]);
            }

            conflicts.add(new Conflict(
                    libName, reqVersion,
                    best != null ? best[0] : null,
                    best != null ? best[1] : null,
                    isMajor));
        }

        return conflicts;
    }

    /**
     * Silently fixes built-in library references that point to missing folders.
     * A "built-in" is any library whose base name matches a definition in
     * {@link BuiltInLibraryHelper#ALL_BUILT_INS} or
     * {@link pro.sketchware.compiler.BuiltInLibraries#KNOWN_BUILT_IN_LIBRARIES}.
     *
     * @return true if at least one entry was auto-migrated and the file was rewritten
     */
    private static boolean autoMigrateBuiltIns(String scId,
                                               List<HashMap<String, Object>> libs,
                                               String localLibsRoot) {
        boolean changed = false;
        for (HashMap<String, Object> lib : libs) {
            Object nameObj = lib.get("name");
            if (!(nameObj instanceof String)) continue;
            String name = (String) nameObj;

            // Already present on disk as a local lib → nothing to do
            if (new File(localLibsRoot + name).isDirectory()) continue;

            // Is this a known built-in?
            String baseName = name.replaceAll("[_-][Vv]?\\d+([._]\\d+)*$", "");
            boolean isBuiltIn = BuiltInLibraryHelper.isBuiltIn(baseName)
                    || BuiltInLibraryHelper.isBuiltIn(name)
                    || mod.jbk.build.BuiltInLibraries.BuiltInLibrary.ofName(baseName).isPresent()
                    || mod.jbk.build.BuiltInLibraries.BuiltInLibrary.ofName(name).isPresent();

            if (!isBuiltIn) continue;

            // Find the best available substitute in local_libs
            String reqVersion = LibraryVersionChecker.extractVersionFromName(name);
            String[] best = findBestSubstitute(name, reqVersion, localLibsRoot);
            if (best == null) continue;   // nothing available — let conflict dialog handle

            String newName = best[0];
            Log.i(TAG, "Auto-migrating built-in: " + name + " → " + newName);

            // Rebuild ALL path fields from scratch using the correct local_libs location.
            // Never trust the old paths — they point to the built-in directory.
            String dependency = (String) lib.get("dependency");
            HashMap<String, Object> freshEntry =
                    dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.createLibraryMap(
                            newName, dependency != null ? dependency : "");

            // Copy fresh paths into the existing entry (preserve any extra keys like packageName)
            for (String key : freshEntry.keySet()) {
                lib.put(key, freshEntry.get(key));
            }
            lib.put("name", newName);
            changed = true;
        }

        if (changed) {
            File libFile = LocalLibrariesUtil.getLocalLibFile(scId);
            try {
                FileUtil.writeFile(libFile.getAbsolutePath(),
                        new Gson().toJson(libs));
            } catch (Exception e) {
                Log.e(TAG, "Failed to save migrated local_library for " + scId, e);
            }

            // CRITICAL: exclude the old built-in names from the build classpath.
            // Without this, ProjectBuilder adds the old built-in JAR alongside the
            // new local_libs JAR → compiler uses wrong version → "Cannot find symbol".
            List<String> migratedNames = new ArrayList<>();
            for (HashMap<String, Object> lib : libs) {
                Object n = lib.get("name");
                if (n instanceof String) migratedNames.add((String) n);
            }
            excludeMigratedBuiltIns(scId, migratedNames);
        }
        return changed;
    }


    /**
     * Applies a single conflict resolution: updates the project's local_library
     * JSON to point to the substitute library.
     * Must be called from UI thread or with proper synchronization.
     */
    public static void applyResolution(String scId, Conflict conflict) {
        if (!conflict.hasSubstitute()) return;
        String localLibsRoot = FileUtil.getExternalStorageDir()
                + "/.sketchware/libs/local_libs/";
        // Read the project's dependency coordinate for the required library
        File reqDepFile = new File(localLibsRoot + conflict.requiredName + "/dependency");
        String oldCoord = null;
        // Try to read from the old folder (may not exist anymore)
        if (reqDepFile.exists()) {
            oldCoord = FileUtil.readFile(reqDepFile.getAbsolutePath()).trim();
        }
        // Read from the substitute's dependency file
        File subDepFile = new File(localLibsRoot + conflict.availableName + "/dependency");
        String newCoord = null;
        if (subDepFile.exists()) {
            newCoord = FileUtil.readFile(subDepFile.getAbsolutePath()).trim();
            if (newCoord.isEmpty()) newCoord = null;
        }
        ManageLocalLibraryActivity.updateLibraryInAllProjects(
                conflict.requiredName, conflict.availableName, oldCoord, newCoord);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Scans local_libs for a folder with the same base name (ignoring version suffix).
     * Returns [folderName, version] of the best match, or null if nothing found.
     * Prefers the highest version; if multiple exist, picks lowest major bump.
     */
    @Nullable
    private static String[] findBestSubstitute(
            String requiredName, @Nullable String requiredVersion, String localLibsRoot) {

        // Derive base name: strip version suffix
        String baseName = requiredName;
        if (requiredVersion != null) {
            baseName = requiredName
                    .replaceAll("[_-][Vv]?" + java.util.regex.Pattern.quote(requiredVersion) + "$", "")
                    .replaceAll("[_-]$", "");
        }
        if (baseName.isEmpty()) return null;

        final String searchBase = baseName;
        File root = new File(localLibsRoot);
        File[] candidates = root.listFiles(f -> f.isDirectory()
                && f.getName().toLowerCase().startsWith(searchBase.toLowerCase()));
        if (candidates == null || candidates.length == 0) return null;

        String bestName = null, bestVersion = null;
        for (File candidate : candidates) {
            String candidateVer = LibraryVersionChecker.extractVersionFromName(candidate.getName());
            if (bestVersion == null) {
                bestName = candidate.getName();
                bestVersion = candidateVer;
            } else if (candidateVer != null
                    && LibraryVersionChecker.isNewer(candidateVer, bestVersion)) {
                bestName = candidate.getName();
                bestVersion = candidateVer;
            }
        }

        return bestName != null ? new String[]{bestName, bestVersion} : null;
    }

    /** Returns true if the major version number changed (e.g. 2.x → 3.x). */
    private static boolean isMajorVersionChange(String v1, String v2) {
        try {
            int major1 = Integer.parseInt(v1.split("[._]")[0]);
            int major2 = Integer.parseInt(v2.split("[._]")[0]);
            return major2 > major1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Updates all path fields in a library map entry when its folder name changes. */
    private static void updatePathFields(HashMap<String, Object> lib,
                                         String oldName, String newName) {
        // The local_libs root on external storage
        String localLibsRoot = LocalLibraryManager.getLocalLibsPath(); // e.g. /storage/emulated/0/.sketchware/libs/local_libs/

        // Pre-compute the correct base paths for the new library in local_libs
        String newLibRoot   = localLibsRoot + newName + "/";
        String newJarPath   = newLibRoot + "classes.jar";
        String newResPath   = newLibRoot + "res";
        String newManifest  = newLibRoot + "AndroidManifest.xml";
        String newPgRules   = newLibRoot + "proguard.txt";
        String newAssetsPath= newLibRoot + "assets";
        String newJniPath   = newLibRoot + "jni";

        // DEX lives in the local_libs folder itself (sibling of classes.jar)
        String newDexPath   = newLibRoot + "classes.dex";

        // For each path field: if it currently points to the built-in dir
        // (or to a different version), replace with the correct local_libs path.
        String[] pathKeys = {"jarPath", "dexPath", "resPath", "pgRulesPath",
                             "manifestPath", "configPath", "assetsPath", "jniPath"};

        for (String key : pathKeys) {
            Object val = lib.get(key);
            if (!(val instanceof String)) continue;
            String current = (String) val;

            // Determine correct new value based on key
            String newVal;
            switch (key) {
                case "jarPath":      newVal = newJarPath;    break;
                case "dexPath":      newVal = newDexPath;    break;
                case "resPath":      newVal = newResPath;    break;
                case "pgRulesPath":  newVal = newPgRules;    break;
                case "manifestPath": newVal = newManifest;   break;
                case "assetsPath":   newVal = newAssetsPath; break;
                case "jniPath":      newVal = newJniPath;    break;
                default:
                    // configPath or unknown: just replace the name segment
                    newVal = current.replace("/" + oldName + "/", "/" + newName + "/");
                    // If it still points to built-in dir, redirect to local_libs
                    newVal = redirectToLocalLibs(newVal, newName, localLibsRoot);
                    break;
            }

            lib.put(key, newVal);
        }

        // Always update the name
        lib.put("name", newName);
    }

    /**
     * If a path still points to the built-in libs directory (jarBuiltInLibFolderDir
     * or dexBuiltInLibFolderDir), redirect it to the local_libs directory.
     */
    private static String redirectToLocalLibs(String path, String libName,
                                               String localLibsRoot) {
        if (path == null) return path;
        // Built-in paths contain "/libs/libs/" or "/libs/dexs/"
        if (path.contains("/libs/libs/") || path.contains("/libs/dexs/")) {
            // Extract just the filename
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            return localLibsRoot + libName + "/" + fileName;
        }
        return path;
    }

    // ── Auto-exclude helper ────────────────────────────────────────────────────

    /**
     * After migrating built-in libraries to local_libs, we must also exclude
     * them from the built-in classpath — otherwise the build system adds the
     * OLD built-in JAR to the classpath and the project compiles against the
     * wrong (old) version, causing "Cannot find symbol" for new APIs.
     *
     * This writes/merges the project's `excluded_library` config file.
     */
    public static void excludeMigratedBuiltIns(String scId,
                                               List<String> migratedBaseNames) {
        if (migratedBaseNames == null || migratedBaseNames.isEmpty()) return;

        File configFile = new File(android.os.Environment.getExternalStorageDirectory(),
                ".sketchware/data/" + scId + "/excluded_library");

        // Read existing config  { first: Boolean, second: List<String> }
        boolean excludingEnabled = true;
        List<String> excluded = new ArrayList<>();

        if (configFile.exists()) {
            try {
                String json = pro.sketchware.utility.FileUtil.readFile(configFile.getAbsolutePath());
                com.google.gson.reflect.TypeToken<android.util.Pair<Boolean, List<String>>> tt =
                        new com.google.gson.reflect.TypeToken<android.util.Pair<Boolean, List<String>>>(){};
                android.util.Pair<Boolean, List<String>> cfg = new com.google.gson.Gson().fromJson(json, tt.getType());
                if (cfg != null) {
                    excludingEnabled = cfg.first != null ? cfg.first : true;
                    if (cfg.second != null) excluded.addAll(cfg.second);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not read excluded_library for " + scId + ", will overwrite", e);
            }
        }

        boolean changed = false;
        for (String baseName : migratedBaseNames) {
            // Find the canonical BuiltInLibrary name for this baseName
            java.util.Optional<mod.jbk.build.BuiltInLibraries.BuiltInLibrary> opt =
                    mod.jbk.build.BuiltInLibraries.BuiltInLibrary.ofName(baseName);
            if (!opt.isPresent()) {
                // Try without version suffix
                String stripped = baseName.replaceAll("[_-][Vv]?\\d+([._]\\d+)*$", "");
                opt = mod.jbk.build.BuiltInLibraries.BuiltInLibrary.ofName(stripped);
            }
            if (opt.isPresent()) {
                String libName = opt.get().getName();
                if (!excluded.contains(libName)) {
                    excluded.add(libName);
                    changed = true;
                    Log.i(TAG, "Excluding built-in from classpath: " + libName
                            + " (replaced by local lib " + baseName + ")");
                }
            }
        }

        if (changed) {
            try {
                // Serialize in the same format ExcludeBuiltInLibrariesActivity uses:
                // Pair<Boolean, List<String>>
                android.util.Pair<Boolean, List<String>> newCfg =
                        new android.util.Pair<>(excludingEnabled, excluded);
                String json = new com.google.gson.Gson().toJson(newCfg);
                pro.sketchware.utility.FileUtil.writeFile(configFile.getAbsolutePath(), json);
                Log.i(TAG, "Saved excluded_library for project " + scId
                        + " (" + excluded.size() + " excluded)");
            } catch (Exception e) {
                Log.e(TAG, "Failed to save excluded_library for " + scId, e);
            }
        }
    }

    // ── Instance API ───────────────────────────────────────────────────────

    /**
     * Finds libraries with the same base name (ignoring version suffix).
     * Returns a list of human-readable conflict descriptions.
     */
    public List<String> findNameConflicts(java.util.Collection<LocalLibraryMetadata> metadata) {
        java.util.Map<String, java.util.List<String>> byBase = new java.util.LinkedHashMap<>();
        for (LocalLibraryMetadata m : metadata) {
            String base = stripVersion(m.name);
            byBase.computeIfAbsent(base, k -> new ArrayList<>()).add(m.name);
        }
        List<String> conflicts = new ArrayList<>();
        for (java.util.Map.Entry<String, java.util.List<String>> e : byBase.entrySet()) {
            if (e.getValue().size() > 1) {
                conflicts.add(e.getKey() + ": " + String.join(", ", e.getValue()));
            }
        }
        return conflicts;
    }

    private static String stripVersion(String name) {
        if (name == null) return "";
        return name.replaceAll("[-_]?\\d+(\\.\\d+)+$", "");
    }
}
