package pro.sketchware.library;

import pro.sketchware.library.LocalLibraryMetadata;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import dev.aldi.sayuti.editor.manage.LocalLibraryItem;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans all Sketchware project data folders to discover which projects
 * reference which local libraries.
 *
 * Project library lists are stored as JSON in:
 *   .sketchware/data/{sc_id}/local_library
 * Format: [{"name":"retrofit","jarPath":"...","dependency":"..."}, ...]
 */
public class LibraryProjectLinker {

    private static final String TAG = "LibProjectLinker";
    private static final Gson GSON = new Gson();
    private static final Type LIST_MAP_TYPE =
            new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType();

    // ─────────────────────────────────────────────────────────────────────────
    //  Scanning
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scans all projects and builds a map of {@code libName → [sc_id, …]}.
     */
    public static Map<String, List<String>> buildLibraryToProjectsMap() {
        Map<String, List<String>> map = new HashMap<>();
        File dataRoot = new File(LocalLibraryManager.getProjectsDataPath());
        if (!dataRoot.exists() || !dataRoot.isDirectory()) return map;

        File[] projectDirs = dataRoot.listFiles(File::isDirectory);
        if (projectDirs == null) return map;

        for (File projectDir : projectDirs) {
            String scId = projectDir.getName();
            // local_library (JSON) — not "library" (binary)
            File localLibFile = new File(projectDir, "local_library");
            if (!localLibFile.exists()) continue;

            try {
                List<String> libsInProject = readLibraryNamesFromJson(localLibFile);
                for (String libName : libsInProject) {
                    map.computeIfAbsent(libName, k -> new ArrayList<>()).add(scId);
                }
            } catch (Exception e) {
                Log.w(TAG, "Cannot read local_library for project " + scId, e);
            }
        }
        return map;
    }

    /**
     * Returns the list of project sc_ids that use the given library name.
     */
    public static List<String> getProjectsUsingLibrary(String libName) {
        List<String> result = new ArrayList<>();
        if (libName == null || libName.trim().isEmpty()) return result;

        File dataRoot = new File(LocalLibraryManager.getProjectsDataPath());
        if (!dataRoot.exists()) return result;

        File[] projectDirs = dataRoot.listFiles(File::isDirectory);
        if (projectDirs == null) return result;

        String baseName = libName.replaceAll("-\\d+(\\.\\d+)*$", "").toLowerCase();

        for (File projectDir : projectDirs) {
            File localLibFile = new File(projectDir, "local_library");
            if (!localLibFile.exists()) continue;
            try {
                List<String> names = readLibraryNamesFromJson(localLibFile);
                for (String name : names) {
                    if (name.equals(libName)) {
                        result.add(projectDir.getName());
                        break;
                    }
                    // Fuzzy: base name match
                    String base = name.replaceAll("-\\d+(\\.\\d+)*$", "").toLowerCase();
                    if (base.equals(baseName)) {
                        result.add(projectDir.getName());
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    public static int countProjectsUsingLibrary(String libName) {
        return getProjectsUsingLibrary(libName).size();
    }

    /**
     * P16 — Returns human-readable project names (not sc_ids) for all projects
     * that reference the given library. Falls back to sc_id if name not available.
     */
    public static List<String> getProjectNamesUsingLibrary(String libName) {
        List<String> scIds = getProjectsUsingLibrary(libName);
        List<String> names = new ArrayList<>();
        for (String scId : scIds) {
            // Try to read the project's name from its my_sc_local_lib or _data file
            String displayName = readProjectDisplayName(scId);
            names.add(displayName != null && !displayName.isEmpty() ? displayName : "Project " + scId);
        }
        return names;
    }

    private static String readProjectDisplayName(String scId) {
        // Projects store their name in .sketchware/data/{sc_id}/_data or my_sc_local_lib
        File myDataFile = new File(LocalLibraryManager.getProjectsDataPath() + scId + "/_data");
        if (!myDataFile.exists()) {
            myDataFile = new File(LocalLibraryManager.getProjectsDataPath() + scId + "/my_sc_local_lib");
        }
        if (!myDataFile.exists()) return null;
        try {
            String content = AtomicFileWriter.read(myDataFile);
            // Try JSON field "sc_name" or "my_sc_name"
            for (String key : new String[]{"sc_name", "my_sc_name", "project_name"}) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(content);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Updating project references
    // ─────────────────────────────────────────────────────────────────────────

    public static class UpdateResult {
        public int updatedProjects = 0;
        public int skippedProjects = 0;
        public int failedProjects  = 0;
        public List<String> updatedIds = new ArrayList<>();
        public List<String> failedIds  = new ArrayList<>();
    }

    /**
     * Updates {@code oldLibName} to {@code newLibName} in all linked project
     * local_library JSON files.
     */
    public static UpdateResult updateLibraryNameInProjects(
            String oldLibName, String newLibName, List<String> projectIds) {

        UpdateResult result = new UpdateResult();
        if (projectIds == null || projectIds.isEmpty()) return result;
        if (oldLibName == null || oldLibName.equals(newLibName)) return result;

        for (String scId : projectIds) {
            File projectDir   = new File(LocalLibraryManager.getProjectsDataPath() + scId);
            File localLibFile = new File(projectDir, "local_library");

            if (!localLibFile.exists()) {
                result.skippedProjects++;
                continue;
            }

            try {
                String json = readFileString(localLibFile);
                ArrayList<HashMap<String, Object>> libs = GSON.fromJson(json, LIST_MAP_TYPE);
                if (libs == null) libs = new ArrayList<>();

                boolean changed = false;
                for (HashMap<String, Object> lib : libs) {
                    Object nameObj = lib.get("name");
                    if (nameObj instanceof String && oldLibName.equals(nameObj)) {
                        lib.put("name", newLibName);
                        updatePathFields(lib, oldLibName, newLibName);
                        changed = true;
                    }
                }

                if (changed) {
                    writeFileString(localLibFile, GSON.toJson(libs));
                    result.updatedProjects++;
                    result.updatedIds.add(scId);
                } else {
                    result.skippedProjects++;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update project " + scId, e);
                result.failedProjects++;
                result.failedIds.add(scId);
            }
        }
        return result;
    }

    /**
     * Adds a library reference to a project's local_library JSON file.
     */
    public static boolean addLibraryToProject(String scId, LocalLibraryItem item) {
        File projectDir  = new File(LocalLibraryManager.getProjectsDataPath() + scId);
        if (!projectDir.exists()) return false;
        File localLibFile = new File(projectDir, "local_library");

        try {
            ArrayList<HashMap<String, Object>> libs = new ArrayList<>();
            if (localLibFile.exists()) {
                String json = readFileString(localLibFile);
                ArrayList<HashMap<String, Object>> existing = GSON.fromJson(json, LIST_MAP_TYPE);
                if (existing != null) libs = existing;
            }
            // Check if already present
            for (HashMap<String, Object> lib : libs) {
                Object n = lib.get("name");
                if (n instanceof String && item.name.equals(n)) return true;
            }
            // Add new entry using LocalLibrariesUtil to build the correct map
            HashMap<String, Object> newLib = LocalLibrariesUtil.createLibraryMap(
                    item.name, item.dependency);
            libs.add(newLib);
            writeFileString(localLibFile, GSON.toJson(libs));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "addLibraryToProject failed for " + scId, e);
            return false;
        }
    }

    /**
     * Removes a library reference from a project's local_library JSON file.
     */
    public static boolean removeLibraryFromProject(String scId, String libName) {
        File projectDir  = new File(LocalLibraryManager.getProjectsDataPath() + scId);
        if (!projectDir.exists()) return false;
        File localLibFile = new File(projectDir, "local_library");
        if (!localLibFile.exists()) return false;

        try {
            String json = readFileString(localLibFile);
            ArrayList<HashMap<String, Object>> libs = GSON.fromJson(json, LIST_MAP_TYPE);
            if (libs == null) return false;

            String baseName = libName.replaceAll("-\\d+(\\.\\d+)*$", "").toLowerCase();
            boolean removed = libs.removeIf(lib -> {
                Object n = lib.get("name");
                if (!(n instanceof String)) return false;
                String nameStr = (String) n;
                return nameStr.equals(libName) ||
                       nameStr.replaceAll("-\\d+(\\.\\d+)*$", "")
                               .equalsIgnoreCase(baseName);
            });
            if (removed) writeFileString(localLibFile, GSON.toJson(libs));
            return removed;
        } catch (Exception e) {
            Log.e(TAG, "removeLibraryFromProject failed", e);
            return false;
        }
    }

    /**
     * Checks if a library is referenced in a project's local_library file.
     */
    public static boolean isLibraryInProject(String scId, String libName) {
        if (scId == null || scId.isEmpty() || libName == null) return false;
        File localLibFile = new File(
                LocalLibraryManager.getProjectsDataPath() + scId, "local_library");
        if (!localLibFile.exists()) return false;
        try {
            String baseName = libName.replaceAll("-\\d+(\\.\\d+)*$", "").toLowerCase();
            List<String> names = readLibraryNamesFromJson(localLibFile);
            for (String n : names) {
                if (n.equals(libName)) return true;
                if (n.replaceAll("-\\d+(\\.\\d+)*$", "").equalsIgnoreCase(baseName)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Populates projectCount and linkedProjectIds for all items in the list.
     */
    public static void populateProjectCounts(List<LocalLibraryItem> items) {
        if (items == null || items.isEmpty()) return;
        Map<String, List<String>> map = buildLibraryToProjectsMap();
        for (LocalLibraryItem item : items) {
            List<String> projects = map.get(item.name);
            if (projects == null) {
                String base = item.name.replaceAll("-\\d+(\\.\\d+)*$", "").toLowerCase();
                for (Map.Entry<String, List<String>> e : map.entrySet()) {
                    if (e.getKey().replaceAll("-\\d+(\\.\\d+)*$", "").equalsIgnoreCase(base)) {
                        projects = e.getValue();
                        break;
                    }
                }
            }
            if (projects != null) {
                item.linkedProjectIds = new ArrayList<>(projects);
                item.projectCount     = projects.size();
            } else {
                item.linkedProjectIds = new ArrayList<>();
                item.projectCount     = 0;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static List<String> readLibraryNamesFromJson(File localLibFile) throws Exception {
        List<String> names = new ArrayList<>();
        String json = readFileString(localLibFile);
        if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) return names;

        ArrayList<HashMap<String, Object>> libs = GSON.fromJson(json, LIST_MAP_TYPE);
        if (libs == null) return names;

        for (HashMap<String, Object> lib : libs) {
            Object nameObj = lib.get("name");
            if (nameObj instanceof String && !((String) nameObj).isEmpty()) {
                names.add((String) nameObj);
            }
        }
        return names;
    }

    private static void updatePathFields(HashMap<String, Object> lib,
                                         String oldName, String newName) {
        // Delegate to the full path-correction logic (same as LibraryConflictChecker)
        String localLibsRoot = LocalLibraryManager.getLocalLibsPath();
        String newLibRoot    = localLibsRoot + newName + "/";

        String[] pathKeys = {"jarPath", "dexPath", "resPath", "pgRulesPath",
                             "manifestPath", "configPath", "assetsPath", "jniPath"};
        for (String key : pathKeys) {
            Object val = lib.get(key);
            if (!(val instanceof String)) continue;
            String current = (String) val;
            String newVal;
            switch (key) {
                case "jarPath":      newVal = newLibRoot + "classes.jar";         break;
                case "dexPath":      newVal = newLibRoot + "classes.dex";         break;
                case "resPath":      newVal = newLibRoot + "res";                 break;
                case "pgRulesPath":  newVal = newLibRoot + "proguard.txt";        break;
                case "manifestPath": newVal = newLibRoot + "AndroidManifest.xml"; break;
                case "assetsPath":   newVal = newLibRoot + "assets";              break;
                case "jniPath":      newVal = newLibRoot + "jni";                 break;
                default:
                    newVal = current.replace("/" + oldName + "/", "/" + newName + "/");
                    if (newVal.contains("/libs/libs/") || newVal.contains("/libs/dexs/")) {
                        String fileName = newVal.substring(newVal.lastIndexOf('/') + 1);
                        newVal = newLibRoot + fileName;
                    }
                    break;
            }
            lib.put(key, newVal);
        }
    }

    private static String readFileString(File f) throws IOException {
        return AtomicFileWriter.read(f); }
    // ── Proposal 10: Fix absolute paths from imported projects ────────────────

    /**
     * Scans every project's local_library file and replaces absolute
     * storage paths (e.g. /storage/emulated/0/.sketchware/…) with the
     * current device's base path. Safe to call after importing a project
     * that was exported from a different device.
     *
     * @return number of project files that were updated
     */
    public static int fixAbsolutePathsInAllProjects() {
        File dataRoot = new File(LocalLibraryManager.getProjectsDataPath());
        if (!dataRoot.exists()) return 0;
        File[] dirs = dataRoot.listFiles(File::isDirectory);
        if (dirs == null) return 0;

        String correctBase = LocalLibraryManager.getLocalLibsRoot(); // e.g. /storage/emulated/0/.sketchware/libs/local_libs/
        // Pattern: any /storage/... or /sdcard/... prefix before .sketchware/libs/local_libs/
        java.util.regex.Pattern absPattern = java.util.regex.Pattern.compile(
                "(/storage/[^/]+|/sdcard)/.sketchware/libs/local_libs/");

        int fixed = 0;
        for (File dir : dirs) {
            File libFile = new File(dir, "local_library");
            if (!libFile.exists()) continue;
            String content = AtomicFileWriter.read(libFile);
            if (content.isEmpty()) continue;

            // Check if any absolute path mismatch exists
            java.util.regex.Matcher m = absPattern.matcher(content);
            if (!m.find()) continue;

            // Replace all occurrences with the current device's path
            String fixed_content = m.replaceAll(
                    java.util.regex.Matcher.quoteReplacement(correctBase));
            if (!fixed_content.equals(content)) {
                try {
                    AtomicFileWriter.write(libFile, fixed_content);
                    fixed++;
                    Log.i(TAG, "Fixed absolute paths in project " + dir.getName());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to fix paths in project " + dir.getName(), e);
                }
            }
        }
        return fixed;
    }

    private static void writeFileString(File f, String content) throws IOException {
        // Proposal 19: atomic write via AtomicFileWriter (legacy FileOutputStream path removed — unreachable)
        AtomicFileWriter.write(f, content);
    }

    // ── Instance API for ProjectLibraryDiagnosticsActivity ────────────────
    private final java.util.List<LocalLibraryMetadata> addedLibs = new java.util.ArrayList<>();

    /** Registers a library metadata entry for classpath resolution. */
    public void add(LocalLibraryMetadata lib) {
        if (lib != null) addedLibs.add(lib);
    }

    /** Returns the list of artifact Files that make up the resolved classpath. */
    public java.util.List<java.io.File> classpath() {
        java.util.List<java.io.File> cp = new java.util.ArrayList<>();
        for (LocalLibraryMetadata m : addedLibs) {
            if (m.artifact != null && m.artifact.isFile()) cp.add(m.artifact);
        }
        return cp;
    }
}
