package pro.sketchware.library;

import dev.aldi.sayuti.editor.manage.LocalLibraryItem;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Handles asynchronous version checking and downloading for local libraries.
 *
 * <p>Version check strategy:
 * <ol>
 *   <li>For libraries with a Maven dependency string → query Maven Central / Google Maven</li>
 *   <li>For libraries with a GitHub download URL → check GitHub releases API</li>
 *   <li>Built-in libraries without a URL → mark as "up-to-date" (no remote check)</li>
 * </ol>
 *
 * <p>Download strategy:
 * <ul>
 *   <li>Download JAR/AAR to a temp file inside the library folder</li>
 *   <li>Verify the download is a valid ZIP (JARs are ZIP files)</li>
 *   <li>Atomically replace the old classes.jar</li>
 *   <li>Update library.json with the new version</li>
 *   <li>Optionally propagate the name change to linked projects via
 *       {@link LibraryProjectLinker}</li>
 * </ul>
 */
public class LibraryUpdateManager {

    private static final String TAG = "LibUpdateManager";

    /**
     * Proposal 7 — Sequential download queue.
     * Max 2 concurrent downloads to avoid racing writes to the same Maven endpoint.
     * Uses a bounded ThreadPoolExecutor so we never queue infinitely.
     */
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            2, 2, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(128),
            r -> { Thread t = new Thread(r, "LibUpdate"); t.setDaemon(true); return t; });

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * Proposal 6 — Guard against delete-during-update.
     * Tracks library names currently being downloaded/updated.
     * LibraryManager.deleteLibrary() checks this set before proceeding.
     */
    public static final Set<String> ACTIVE_UPDATES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    // ─────────────────────────────────────────────────────────────────────────
    //  Callbacks
    // ─────────────────────────────────────────────────────────────────────────

    public interface CheckCallback {
        /** Called on the main thread after checking one library */
        void onChecked(LocalLibraryItem item, boolean updateAvailable, String latestVersion);
        /** Called on the main thread when the batch check is complete */
        void onAllChecked(List<LocalLibraryItem> updatedItems);
    }

    public interface DownloadCallback {
        /** 0–100 */
        void onProgress(int percent);
        /** Called on the main thread on success */
        void onSuccess(LocalLibraryItem updatedItem);
        /** Called on the main thread on failure */
        void onFailure(String error);
    }

    public interface MissingLibraryCallback {
        /** Called when a missing library has been downloaded and installed */
        void onInstalled(String libName, String version);
        /** Called when the download fails */
        void onFailed(String libName, String error);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Version check
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks all libraries in the list for available updates, asynchronously.
     * Results are delivered via {@code callback} on the main thread.
     */
    public static void checkForUpdates(List<LocalLibraryItem> items, CheckCallback callback) {
        if (items == null || items.isEmpty()) {
            if (callback != null) callback.onAllChecked(new ArrayList<>());
            return;
        }

        EXECUTOR.execute(() -> {
            List<LocalLibraryItem> results = new ArrayList<>();
            for (LocalLibraryItem item : items) {
                // P15: skip pinned libraries entirely
                if (item.pinned) {
                    results.add(item);
                    MAIN_HANDLER.post(() -> {
                        if (callback != null)
                            callback.onChecked(item, false, item.version);
                    });
                    continue;
                }

                String latest = null;
                boolean fromCache = false;

                // P23: try network first; fall back to cached latestVersion if offline
                try {
                    latest = fetchLatestVersion(item);
                } catch (Exception networkError) {
                    Log.w(TAG, "Network check failed for " + item.name
                            + " — using cached version", networkError);
                    if (item.latestVersion != null && !item.latestVersion.isEmpty()) {
                        latest = item.latestVersion;
                        fromCache = true;
                    }
                }

                // P9: if network returned nothing and dependency is known, check for
                //     renamed coordinates (e.g. android-support → androidx)
                if ((latest == null || latest.isEmpty()) && !fromCache) {
                    String redirected = checkDependencyRedirect(item.dependency);
                    if (redirected != null) {
                        Log.i(TAG, "Dependency redirect detected for " + item.name
                                + ": " + redirected);
                        item.changelog = "⚠️ This library may have been renamed to: " + redirected;
                        LocalLibraryManager.saveMetadata(item);
                    }
                }

                final String finalLatest = latest;
                if (finalLatest != null && !finalLatest.isEmpty() && !finalLatest.equals(item.version)) {
                    item.latestVersion     = finalLatest;
                    item.isUpdateAvailable = true;
                    if (!fromCache) LocalLibraryManager.saveMetadata(item);
                } else if (finalLatest != null && !finalLatest.isEmpty()) {
                    item.latestVersion     = finalLatest;
                    item.isUpdateAvailable = false;
                    if (!fromCache) LocalLibraryManager.saveMetadata(item);
                }
                results.add(item);
                MAIN_HANDLER.post(() -> {
                    if (callback != null) {
                        callback.onChecked(item,
                                item.isUpdateAvailable,
                                finalLatest != null ? finalLatest : "");
                    }
                });
            }
            MAIN_HANDLER.post(() -> {
                if (callback != null) callback.onAllChecked(results);
            });
        });
    }


    /**
     * Synchronously fetches the latest version string for a library.
     * Returns null on network error or if no remote info is configured.
     *
     * Called from background thread only.
     */
    private static String fetchLatestVersion(LocalLibraryItem item) {
        // Built-in with no download URL → skip
        if ((item.downloadUrl == null || item.downloadUrl.trim().isEmpty())
                && (item.dependency == null || item.dependency.trim().isEmpty())) {
            return null;
        }

        // Try Maven Central metadata API
        if (item.dependency != null && item.dependency.contains(":")) {
            String latest = fetchFromMavenMetadata(item.dependency);
            if (latest != null) return latest;
        }

        // Try GitHub releases API
        if (item.downloadUrl != null && item.downloadUrl.contains("github.com")) {
            String latest = fetchFromGitHub(item.downloadUrl);
            if (latest != null) return latest;
        }

        return null;
    }

    /** Queries Maven Central metadata.xml for the latest release version */
    private static String fetchFromMavenMetadata(String dependency) {
        // dependency format: "group:artifact:version"
        String[] parts = dependency.split(":");
        if (parts.length < 2) return null;

        String group    = parts[0].replace('.', '/');
        String artifact = parts[1];

        // Try Maven Central
        String url = "https://repo1.maven.org/maven2/"
                + group + "/" + artifact + "/maven-metadata.xml";
        try {
            String xml = httpGet(url);
            if (xml == null) {
                // Try Google Maven
                url = "https://dl.google.com/dl/android/maven2/"
                        + group + "/" + artifact + "/maven-metadata.xml";
                xml = httpGet(url);
            }
            if (xml == null) return null;
            // Extract <release> or last <version>
            String release = extractXmlValue(xml, "release");
            if (release != null && !release.isEmpty()) return release;
            // Fall back to last <version>
            String lastVersion = extractLastXmlValue(xml, "version");
            return lastVersion;
        } catch (Exception e) {
            Log.w(TAG, "Maven check failed for " + dependency, e);
            return null;
        }
    }

    /** Queries GitHub releases API for the latest tag */
    private static String fetchFromGitHub(String repoUrl) {
        // Convert "https://github.com/owner/repo/..." to API URL
        try {
            // Extract owner/repo
            String path = repoUrl.replace("https://github.com/", "");
            String[] segments = path.split("/");
            if (segments.length < 2) return null;
            String owner = segments[0];
            String repo  = segments[1].split("[/#?]")[0];

            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo
                    + "/releases/latest";
            String json = httpGet(apiUrl);
            if (json == null) return null;

            // Extract "tag_name" from JSON (simple regex, no full JSON parser needed)
            String tag = extractJsonString(json, "tag_name");
            if (tag == null) return null;
            // Strip leading "v" prefix common in GitHub tags
            return tag.startsWith("v") ? tag.substring(1) : tag;
        } catch (Exception e) {
            Log.w(TAG, "GitHub check failed for " + repoUrl, e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Download / install update
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Downloads and installs the update for {@code item}, then optionally
     * propagates the new name/version to linked projects.
     *
     * @param item             the library to update
     * @param newVersion       the version string to install
     * @param propagate        true → update library references in linked projects
     * @param callback         delivery callback (main thread)
     */
    public static void applyUpdate(LocalLibraryItem item, String newVersion,
                                   boolean propagate, DownloadCallback callback) {
        if (item == null || newVersion == null || newVersion.trim().isEmpty()) {
            if (callback != null) callback.onFailure("Invalid parameters");
            return;
        }

        // Proposal 6: mark as active so delete is blocked during update
        ACTIVE_UPDATES.add(item.name);

        EXECUTOR.execute(() -> {
            try {
                String downloadUrl = resolveDownloadUrl(item, newVersion);
                if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
                    ACTIVE_UPDATES.remove(item.name);
                    postFailure(callback, "No download URL for " + item.name);
                    return;
                }

                // Download to temp file
                File libDir  = new File(LocalLibraryManager.getLibraryPath(item.name));
                File tmpFile = new File(libDir, "update_tmp_" + Thread.currentThread().getId() + ".jar");
                downloadFile(downloadUrl, tmpFile, percent ->
                        MAIN_HANDLER.post(() -> {
                            if (callback != null) callback.onProgress(percent);
                        }));

                // Proposal 18: JAR integrity check (ZIP magic + optional checksum)
                LibraryHealthChecker.Issue integrityIssue =
                        LibraryHealthChecker.checkJar(tmpFile, null);
                if (integrityIssue != null) {
                    tmpFile.delete();
                    ACTIVE_UPDATES.remove(item.name);
                    postFailure(callback, "Downloaded JAR is corrupt: " + integrityIssue.detail);
                    return;
                }

                // Compute and persist SHA-256 of new JAR for future checks
                String sha256 = LibraryHealthChecker.sha256Hex(tmpFile);

                // Atomically replace old JAR (renameTo is atomic on same filesystem)
                File jarFile = new File(LocalLibraryManager.getJarPath(item.name));
                if (jarFile.exists()) jarFile.delete();
                if (!tmpFile.renameTo(jarFile)) {
                    // Fallback: copy
                    try (java.io.FileInputStream in  = new java.io.FileInputStream(tmpFile);
                         FileOutputStream        out = new FileOutputStream(jarFile)) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    }
                    tmpFile.delete();
                }

                // Update metadata (including sha256)
                item.version           = newVersion;
                item.latestVersion     = newVersion;
                item.isUpdateAvailable = false;
                LocalLibraryManager.saveMetadata(item);

                // Persist checksum alongside library.json
                if (sha256 != null) {
                    try {
                        AtomicFileWriter.write(
                                new File(LocalLibraryManager.getLibraryPath(item.name), "classes.jar.sha256"),
                                sha256);
                    } catch (Exception ignored) {}
                }

                // Propagate to projects if requested
                if (propagate && !item.linkedProjectIds.isEmpty()) {
                    // If library name doesn't change (version-less naming) just save.
                    // If the dependency string changes, update project files.
                    String oldDep = item.dependency;
                    String newDep = rebuildDependency(item.dependency, newVersion);
                    if (!oldDep.equals(newDep)) {
                        LibraryProjectLinker.UpdateResult r =
                                LibraryProjectLinker.updateLibraryNameInProjects(
                                        item.name, item.name,
                                        item.linkedProjectIds);
                        Log.i(TAG, "Propagated update to " + r.updatedProjects + " projects");
                    }
                }

                final LocalLibraryItem finalItem = item;
                ACTIVE_UPDATES.remove(item.name);   // Proposal 6: release lock
                MAIN_HANDLER.post(() -> {
                    if (callback != null) callback.onSuccess(finalItem);
                });

            } catch (Exception e) {
                Log.e(TAG, "Update failed for " + item.name, e);
                ACTIVE_UPDATES.remove(item.name);   // Proposal 6: release lock on failure
                postFailure(callback, e.getMessage());
            }
        });
    }

    /**
     * Downloads a missing library (identified by base name) and installs it.
     * Used when a project run detects a missing library.
     *
     * @param baseName      library name without version (e.g. "retrofit")
     * @param version       desired version, or null to use latest
     * @param callback      result callback
     */
    public static void downloadMissingLibrary(String baseName, String version,
                                              MissingLibraryCallback callback) {
        if (baseName == null || baseName.trim().isEmpty()) {
            if (callback != null) callback.onFailed(baseName, "Empty library name");
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                // Check if we have a definition for this built-in
                BuiltInLibraryHelper.BuiltInLibDef def =
                        BuiltInLibraryHelper.getDefinition(baseName);

                if (def == null) {
                    // Unknown library — can't auto-download without a URL
                    postMissingFailure(callback, baseName, "Unknown library. Please add it manually.");
                    return;
                }

                String targetVersion = (version != null && !version.isEmpty())
                        ? version : def.version;
                String url = def.downloadUrl;

                if (url == null || url.trim().isEmpty()) {
                    postMissingFailure(callback, baseName, "No download URL available.");
                    return;
                }

                // Create library folder
                LocalLibraryManager.createLibraryFolder(baseName);

                // Create/update metadata
                LocalLibraryItem item = new LocalLibraryItem();
                item.name        = baseName;
                item.version     = targetVersion;
                item.isBuiltIn   = true;
                item.dependency  = def.dependency;
                item.downloadUrl = url;
                LocalLibraryManager.saveMetadata(item);

                // Download JAR
                File jarFile = new File(LocalLibraryManager.getJarPath(baseName));
                downloadFile(url, jarFile, null);

                if (!jarFile.exists() || jarFile.length() == 0) {
                    postMissingFailure(callback, baseName, "Download produced an empty file.");
                    return;
                }

                final String finalVersion = targetVersion;
                MAIN_HANDLER.post(() -> {
                    if (callback != null) callback.onInstalled(baseName, finalVersion);
                });

            } catch (Exception e) {
                Log.e(TAG, "Download missing library failed: " + baseName, e);
                postMissingFailure(callback, baseName, e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String resolveDownloadUrl(LocalLibraryItem item, String newVersion) {
        if (item.dependency != null && item.dependency.contains(":")) {
            String[] p = item.dependency.split(":");
            if (p.length >= 2) {
                String group    = p[0];
                String artifact = p[1];
                // Maven Central direct JAR URL
                String groupPath = group.replace('.', '/');
                return "https://repo1.maven.org/maven2/"
                        + groupPath + "/" + artifact + "/" + newVersion
                        + "/" + artifact + "-" + newVersion + ".jar";
            }
        }
        return item.downloadUrl;
    }

    private static String rebuildDependency(String dep, String newVersion) {
        if (dep == null) return "";
        String[] parts = dep.split(":");
        if (parts.length >= 3) {
            return parts[0] + ":" + parts[1] + ":" + newVersion;
        }
        return dep;
    }

    /** Simple HTTP GET, returns body as String, or null on error */
    private static String httpGet(String url) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                return response.body().string();
            }
        } catch (Exception e) {
            Log.w(TAG, "GET failed: " + url, e);
            return null;
        }
    }

    /** Downloads a file, reporting progress via {@code progressCallback} (0-100) */
    private static void downloadFile(String url, File dest,
                                     ProgressConsumer progressCallback) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                .build();
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            long contentLength = response.body().contentLength();
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            try (InputStream in     = response.body().byteStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                long downloaded = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    downloaded += n;
                    if (progressCallback != null && contentLength > 0) {
                        int pct = (int) (downloaded * 100L / contentLength);
                        progressCallback.accept(pct);
                    }
                }
            }
        }
    }

    /** Validates that a file is a ZIP (JAR/AAR are ZIP files) */
    private static boolean isValidZip(File f) {
        if (!f.exists() || f.length() < 4) return false;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
            int magic = raf.readInt();
            return magic == 0x504B0304 || magic == 0x504B0506 || magic == 0x504B0708;
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractXmlValue(String xml, String tag) {
        String open  = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        if (start < 0) return null;
        start += open.length();
        int end = xml.indexOf(close, start);
        if (end < 0) return null;
        return xml.substring(start, end).trim();
    }

    private static String extractLastXmlValue(String xml, String tag) {
        String open  = "<" + tag + ">";
        String close = "</" + tag + ">";
        String last  = null;
        int idx = 0;
        while (true) {
            int start = xml.indexOf(open, idx);
            if (start < 0) break;
            start += open.length();
            int end = xml.indexOf(close, start);
            if (end < 0) break;
            last = xml.substring(start, end).trim();
            idx = end + close.length();
        }
        return last;
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        idx += pattern.length();
        idx = json.indexOf("\"", idx);
        if (idx < 0) return null;
        idx++;
        int end = json.indexOf("\"", idx);
        if (end < 0) return null;
        return json.substring(idx, end);
    }

    private static void postFailure(DownloadCallback cb, String msg) {
        MAIN_HANDLER.post(() -> { if (cb != null) cb.onFailure(msg); });
    }

    private static void postMissingFailure(MissingLibraryCallback cb,
                                           String name, String msg) {
        MAIN_HANDLER.post(() -> { if (cb != null) cb.onFailed(name, msg); });
    }

    // ── Proposal 9: Dependency rename / redirect detection ────────────────────

    /**
     * Known Maven coordinate renames. When a library returns no results
     * on checkForUpdates, we check if its groupId:artifactId has a well-known
     * successor and surface a warning to the user.
     *
     * Add more entries here as the ecosystem evolves.
     */
    private static final java.util.Map<String, String> DEPENDENCY_REDIRECTS;
    static {
        DEPENDENCY_REDIRECTS = new java.util.LinkedHashMap<>();
        // Android Support → AndroidX
        DEPENDENCY_REDIRECTS.put("com.android.support:appcompat-v7",
                "androidx.appcompat:appcompat");
        DEPENDENCY_REDIRECTS.put("com.android.support:recyclerview-v7",
                "androidx.recyclerview:recyclerview");
        DEPENDENCY_REDIRECTS.put("com.android.support:design",
                "com.google.android.material:material");
        DEPENDENCY_REDIRECTS.put("com.android.support:support-v4",
                "androidx.legacy:legacy-support-v4");
        DEPENDENCY_REDIRECTS.put("com.android.support.constraint:constraint-layout",
                "androidx.constraintlayout:constraintlayout");
        // Firebase
        DEPENDENCY_REDIRECTS.put("com.google.firebase:firebase-core",
                "com.google.firebase:firebase-analytics");
        // Misc
        DEPENDENCY_REDIRECTS.put("com.squareup.picasso:picasso",
                "com.squareup.picasso:picasso");  // same, just verify
        DEPENDENCY_REDIRECTS.put("io.reactivex:rxjava",
                "io.reactivex.rxjava3:rxjava");
        DEPENDENCY_REDIRECTS.put("io.reactivex:rxandroid",
                "io.reactivex.rxjava3:rxandroid");
    }

    /**
     * Returns the suggested new coordinate if the given dependency string
     * is known to have been renamed, or null if no redirect is known.
     */
    static String checkDependencyRedirect(String dependency) {
        if (dependency == null || !dependency.contains(":")) return null;
        String[] parts = dependency.split(":");
        if (parts.length < 2) return null;
        String coord = parts[0] + ":" + parts[1];
        String redirect = DEPENDENCY_REDIRECTS.get(coord);
        // Only return if it actually changed
        return (redirect != null && !redirect.startsWith(coord)) ? redirect : null;
    }

    @FunctionalInterface
    private interface ProgressConsumer {
        void accept(int percent);
    }

    // ── Instance API for ProjectLibraryDiagnosticsActivity ────────────────
    private final java.util.Deque<java.io.File[]> undoStack = new java.util.ArrayDeque<>();

    /**
     * Replaces {@code current} library folder with {@code replacement}, saving
     * a backup in {@code backupDir} for potential undo.
     */
    public void replace(java.io.File current, java.io.File replacement, java.io.File backupDir) {
        if (current == null || replacement == null) return;
        try {
            java.io.File backup = null;
            if (backupDir != null) {
                backupDir.mkdirs();
                backup = new java.io.File(backupDir, current.getName() + ".bak");
                copyDir(current, backup);
            }
            deleteDir(current);
            copyDir(replacement, current);
            undoStack.push(new java.io.File[]{ current, backup });
        } catch (Exception e) {
            android.util.Log.e("LibUpdateManager", "replace failed", e);
        }
    }

    /**
     * Reverts the most recent replace() operation.
     * @return true if undo succeeded, false if nothing to undo.
     */
    public boolean undoLast() {
        java.io.File[] entry = undoStack.poll();
        if (entry == null) return false;
        java.io.File original = entry[0];
        java.io.File backup   = entry[1];
        if (backup == null || !backup.exists()) return false;
        try {
            deleteDir(original);
            copyDir(backup, original);
            deleteDir(backup);
            return true;
        } catch (Exception e) {
            android.util.Log.e("LibUpdateManager", "undoLast failed", e);
            return false;
        }
    }

    private static void copyDir(java.io.File src, java.io.File dst) throws java.io.IOException {
        if (!src.exists()) return;
        dst.mkdirs();
        for (java.io.File f : src.listFiles() != null ? src.listFiles() : new java.io.File[0]) {
            java.io.File target = new java.io.File(dst, f.getName());
            if (f.isDirectory()) copyDir(f, target);
            else {
                java.nio.file.Files.copy(f.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteDir(java.io.File dir) {
        if (dir == null || !dir.exists()) return;
        java.io.File[] files = dir.listFiles();
        if (files != null) for (java.io.File f : files) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
        }
        dir.delete();
    }
}
