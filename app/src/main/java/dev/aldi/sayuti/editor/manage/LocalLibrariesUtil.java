package dev.aldi.sayuti.editor.manage;

import pro.sketchware.library.LibraryVersionChecker;

import static pro.sketchware.utility.FileUtil.deleteFile;
import static pro.sketchware.utility.FileUtil.getExternalStorageDir;
import static pro.sketchware.utility.FileUtil.isExistFile;
import static pro.sketchware.utility.FileUtil.listDirAsFile;
import static pro.sketchware.utility.FileUtil.readFile;
import static pro.sketchware.utility.FileUtil.writeFile;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import mod.hey.studios.util.Helper;

public class LocalLibrariesUtil {
    private static final String localLibsPath = getExternalStorageDir().concat("/.sketchware/libs/local_libs/");

    public static List<LocalLibrary> getAllLocalLibraries() {
        ArrayList<File> localLibraryFiles = new ArrayList<>();
        listDirAsFile(localLibsPath, localLibraryFiles);
        localLibraryFiles.sort(new LocalLibrariesComparator());

        List<LocalLibrary> localLibraries = new LinkedList<>();
        for (File libraryFile : localLibraryFiles) {
            if (libraryFile.isDirectory()) {
                LocalLibrary lib = LocalLibrary.fromFile(libraryFile);
                // Load dependency string from the library's "dependency" file if it exists
                File depFile = new File(libraryFile, "dependency");
                if (depFile.exists()) {
                    String dep = readFile(depFile.getAbsolutePath()).trim();
                    if (!dep.isEmpty()) lib.setDependency(dep);
                }
                localLibraries.add(lib);
            }
        }

        return localLibraries;
    }

    public static ArrayList<HashMap<String, Object>> getLocalLibraries(String scId) {
        File localLibFile = getLocalLibFile(scId);
        String fileContent;
        if (!localLibFile.exists() || (fileContent = readFile(localLibFile.getAbsolutePath())).isEmpty()) {
            writeFile(localLibFile.getAbsolutePath(), "[]");
            return new ArrayList<>();
        }
        return new Gson().fromJson(fileContent, Helper.TYPE_MAP_LIST);
    }

    public static void deleteSelectedLocalLibraries(String scId, List<LocalLibrary> localLibraries, ArrayList<HashMap<String, Object>> projectUsedLibs) {
        localLibraries.removeIf(library -> {
            if (library.isSelected()) {
                deleteFile(localLibsPath.concat(library.getName()));
                if (projectUsedLibs != null) {
                    int indexToRemove = -1;
                    for (int i = 0; i < projectUsedLibs.size(); i++) {
                        Map<String, Object> libraryMap = projectUsedLibs.get(i);
                        // Match by stable id (preferred) or by name (legacy fallback)
                Object mapId   = libraryMap.get("id");
                Object mapName = libraryMap.get("name");
                boolean idMatch   = mapId   instanceof String && library.matches((String) mapId);
                boolean nameMatch = mapName instanceof String && library.matches((String) mapName);
                if (idMatch || nameMatch) {
                            indexToRemove = i;
                            break;
                        }
                    }
                    if (indexToRemove != -1) {
                        projectUsedLibs.remove(indexToRemove);
                    }
                }
                return true;
            }
            return false;
        });
        if (projectUsedLibs != null)
            rewriteLocalLibFile(scId, new Gson().toJson(projectUsedLibs));
    }

    public static File getLocalLibFile(String scId) {
        return new File(getExternalStorageDir().concat("/.sketchware/data/").concat(scId.concat("/local_library")));
    }

    public static void rewriteLocalLibFile(String scId, String newContent) {
        writeFile(getLocalLibFile(scId).getAbsolutePath(), newContent);
    }

    public static HashMap<String, Object> createLibraryMap(String name, String dependency) {
        String configPath = localLibsPath + name + "/config";
        String resPath = localLibsPath + name + "/res";
        String jarPath = localLibsPath + name + "/classes.jar";
        String dexPath = localLibsPath + name + "/classes.dex";
        String manifestPath = localLibsPath + name + "/AndroidManifest.xml";
        String pgRulesPath = localLibsPath + name + "/proguard.txt";
        String assetsPath = localLibsPath + name + "/assets";

        // Compute stable id: strip version from folder name
        String libId = LibraryVersionChecker.extractBaseNameFromFolder(name);
        // Try to read from existing "id" file in library folder
        File idFile = new File(localLibsPath + name + "/id");
        if (idFile.exists()) {
            try {
                String stored = readFile(idFile.getAbsolutePath()).trim();
                if (!stored.isEmpty()) libId = stored;
            } catch (Exception ignored) {}
        } else {
            // Write id file for future lookups (write-once, never overwrite)
            try { writeFile(idFile.getAbsolutePath(), libId); } catch (Exception ignored) {}
        }

        HashMap<String, Object> localLibrary = new HashMap<>();
        localLibrary.put("id", libId);    // stable identifier (no version)
        localLibrary.put("name", name);   // current folder name (may include version)
        if (dependency != null) {
            localLibrary.put("dependency", dependency);
        }
        if (isExistFile(configPath)) {
            localLibrary.put("packageName", readFile(configPath));
        }
        if (isExistFile(resPath)) {
            localLibrary.put("resPath", resPath);
        }
        if (isExistFile(jarPath)) {
            localLibrary.put("jarPath", jarPath);
        }
        if (isExistFile(dexPath)) {
            localLibrary.put("dexPath", dexPath);
        }
        if (isExistFile(manifestPath)) {
            localLibrary.put("manifestPath", manifestPath);
        }
        if (isExistFile(pgRulesPath)) {
            localLibrary.put("pgRulesPath", pgRulesPath);
        }
        if (isExistFile(assetsPath)) {
            localLibrary.put("assetsPath", assetsPath);
        }

        // ── Ported from ManageLocalLibrary (legacy): JNI native libs ──────
        String jniPath = localLibsPath + name + "/jni";
        if (isExistFile(jniPath)) {
            localLibrary.put("jniPath", jniPath);
        }

        return localLibrary;
    }

    // ── Utility methods ported from ManageLocalLibrary (legacy) ───────────

    /**
     * Returns paths to extra DEX files (classes2.dex, classes3.dex, …) found
     * alongside the main classes.dex for the given library list.
     */
    public static ArrayList<String> getExtraDexPaths(ArrayList<HashMap<String, Object>> libList) {
        ArrayList<String> extraDexes = new ArrayList<>();
        for (HashMap<String, Object> lib : libList) {
            Object dexPath = lib.get("dexPath");
            if (!(dexPath instanceof String)) continue;
            File dexFile = new File((String) dexPath);
            File parent = dexFile.getParentFile();
            if (parent == null) continue;
            File[] files = parent.listFiles();
            if (files == null) continue;
            for (File f : files) {
                String n = f.getName();
                if (!n.equals("classes.dex") && n.startsWith("classes") && n.endsWith(".dex")) {
                    extraDexes.add(f.getAbsolutePath());
                }
            }
        }
        return extraDexes;
    }

    /**
     * Returns paths to JNI native library folders for the given library list.
     * Ported from {@code ManageLocalLibrary#getNativeLibs()}.
     */
    public static ArrayList<String> getNativeLibPaths(ArrayList<HashMap<String, Object>> libList) {
        ArrayList<String> nativeLibDirs = new ArrayList<>();
        for (HashMap<String, Object> lib : libList) {
            // Try explicit jniPath first (set by createLibraryMap)
            Object jniPath = lib.get("jniPath");
            if (jniPath instanceof String && new File((String) jniPath).isDirectory()) {
                nativeLibDirs.add((String) jniPath);
                continue;
            }
            // Fallback: derive from dexPath parent
            Object dexPath = lib.get("dexPath");
            if (dexPath instanceof String) {
                File jniFolder = new File(new File((String) dexPath).getParentFile(), "jni");
                if (jniFolder.isDirectory()) {
                    nativeLibDirs.add(jniFolder.getAbsolutePath());
                }
            }
        }
        return nativeLibDirs;
    }

    /**
     * Returns ProGuard rule file paths for all enabled libraries.
     * Ported from {@code ManageLocalLibrary#getPgRules()}.
     */
    public static ArrayList<String> getProguardRulePaths(ArrayList<HashMap<String, Object>> libList) {
        ArrayList<String> pgPaths = new ArrayList<>();
        for (HashMap<String, Object> lib : libList) {
            Object pgPath = lib.get("pgRulesPath");
            if (pgPath instanceof String) pgPaths.add((String) pgPath);
        }
        return pgPaths;
    }

    /**
     * Returns assets folder paths for all enabled libraries.
     * Ported from {@code ManageLocalLibrary#getAssets()}.
     */
    public static ArrayList<String> getAssetsPaths(ArrayList<HashMap<String, Object>> libList) {
        ArrayList<String> assets = new ArrayList<>();
        for (HashMap<String, Object> lib : libList) {
            Object assetsPath = lib.get("assetsPath");
            if (assetsPath instanceof String) assets.add((String) assetsPath);
        }
        return assets;
    }
}
