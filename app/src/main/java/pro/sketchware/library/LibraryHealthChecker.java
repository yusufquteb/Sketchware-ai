package pro.sketchware.library;

import pro.sketchware.library.LocalLibraryMetadata;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.build.BuildProgressReceiver;
import dev.aldi.sayuti.editor.manage.LocalLibraryItem;
import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Proposals 1, 2, 3, 4, 17, 18 — Library health validation.
 *
 * Runs a comprehensive check on all (or selected) libraries and returns
 * a structured report. The report drives:
 *   - The "Library Health" screen (17)
 *   - Pre-build validation in LibraryRunValidator
 *   - Post-update JAR integrity check (18)
 */
public class LibraryHealthChecker {

    private static final String TAG = "LibHealthChecker";

    // ── Issue types ───────────────────────────────────────────────────────────

    public enum IssueType {
        /** Proposal 1: folder exists but classes.jar is missing */
        JAR_MISSING,
        /** Proposal 1: only classes.dex exists, no JAR */
        DEX_ONLY,
        /** Proposal 2: another library with same base name exists */
        DUPLICATE_BASE_NAME,
        /** Proposal 3: folder was deleted externally */
        FOLDER_MISSING,
        /** Proposal 4: local_library JSON for a project is corrupt/empty */
        CORRUPT_PROJECT_JSON,
        /** Proposal 18: JAR exists but fails ZIP magic-number check */
        JAR_CORRUPT,
        /** Proposal 18: JAR SHA-256 doesn't match recorded checksum */
        JAR_CHECKSUM_MISMATCH,
        /** Library folder exists but is completely empty */
        EMPTY_FOLDER,
        /** library.json is missing or unreadable */
        MISSING_METADATA,
    }

    public static class Issue {
        public final IssueType type;
        /** Library name (or project sc_id for CORRUPT_PROJECT_JSON) */
        public final String subject;
        public final String detail;
        /** True if the issue can be auto-fixed */
        public final boolean autoFixable;

        /** Severity alias: equals type.name() */
        public final String severity;
        /** Message alias: equals detail */
        public final String message;
        /** Optional file reference */
        public java.io.File file;


        Issue(IssueType type, String subject, String detail, boolean autoFixable) {
            this.type        = type;
            this.subject     = subject;
            this.detail      = detail;
            this.autoFixable = autoFixable;
            this.severity    = type.name();
            this.message     = detail;
        }

        @Override public String toString() {
            return "[" + type + "] " + subject + ": " + detail;
        }
    }

    public static class HealthReport {
        public final List<Issue> issues = new ArrayList<>();
        public int checkedLibraries  = 0;
        public int checkedProjects   = 0;

        public boolean isHealthy() { return issues.isEmpty(); }

        public List<Issue> getByType(IssueType type) {
            List<Issue> result = new ArrayList<>();
            for (Issue i : issues) if (i.type == type) result.add(i);
            return result;
        }

        public int countAutoFixable() {
            int n = 0;
            for (Issue i : issues) if (i.autoFixable) n++;
            return n;
        }
    }

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface ProgressCallback {
        /** Called on background thread with current progress */
        void onProgress(int done, int total, String currentLib);
    }

    // ── Main entry points ─────────────────────────────────────────────────────

    /**
     * Full health check: libraries + project JSON files.
     * Safe to call from a background thread.
     */
    public static HealthReport checkAll(ProgressCallback progress) {
        HealthReport report = new HealthReport();
        List<LocalLibraryItem> libs = LocalLibraryManager.getAllLibraries(true);
        report.checkedLibraries = libs.size();

        // Phase 1 — per-library checks
        checkLibraries(libs, report, progress);

        // Phase 2 — duplicate base-name check (proposal 2)
        checkDuplicates(libs, report);

        // Phase 3 — project JSON checks (proposal 4)
        checkProjectJsonFiles(report);

        return report;
    }

    /**
     * Checks integrity of a single JAR file.
     * Used by LibraryUpdateManager after every download (proposal 18).
     *
     * @param jar        the downloaded JAR/AAR file
     * @param expectedSha256 hex-encoded SHA-256 from remote metadata, or null to skip hash check
     * @return null if OK, or an Issue describing the problem
     */
    public static Issue checkJar(File jar, String expectedSha256) {
        if (!jar.exists() || jar.length() == 0) {
            return new Issue(IssueType.JAR_MISSING, jar.getName(),
                    "File does not exist or is empty", false);
        }
        if (!isValidZip(jar)) {
            return new Issue(IssueType.JAR_CORRUPT, jar.getName(),
                    "Not a valid ZIP/JAR (bad magic number)", false);
        }
        if (expectedSha256 != null && !expectedSha256.isEmpty()) {
            String actual = sha256Hex(jar);
            if (actual != null && !actual.equalsIgnoreCase(expectedSha256)) {
                return new Issue(IssueType.JAR_CHECKSUM_MISMATCH, jar.getName(),
                        "Expected " + expectedSha256.substring(0, 8) + "…"
                        + " got " + actual.substring(0, 8) + "…", false);
            }
        }
        return null;  // healthy
    }

    // ── Auto-fix ──────────────────────────────────────────────────────────────

    /**
     * Attempts to fix all auto-fixable issues in the report.
     * Returns the number of issues fixed.
     *
     * Strategies:
     *   JAR_MISSING / DEX_ONLY / JAR_CORRUPT → re-extract built-in from APK
     *   FOLDER_MISSING → re-copy from internal path or remove dangling refs
     *   CORRUPT_PROJECT_JSON → reset to "[]"
     *   EMPTY_FOLDER / MISSING_METADATA → create stub metadata
     *   JAR_CHECKSUM_MISMATCH → recompute and store hash
     */
    public static int autoFix(HealthReport report) {
        int fixed = 0;
        for (Issue issue : report.issues) {
            if (!issue.autoFixable) continue;
            try {
                switch (issue.type) {
                    case JAR_MISSING:
                    case DEX_ONLY:
                    case JAR_CORRUPT: {
                        String logicalId = issue.subject.replaceAll("-\\d[\\d.]*.*$", "");
                        boolean isBuiltIn = mod.jbk.build.BuiltInLibraries.BUNDLED_VERSIONS
                                .containsKey(logicalId);
                        if (isBuiltIn) {
                            File internalJar = new File(
                                    mod.jbk.build.BuiltInLibraries.EXTRACTED_BUILT_IN_LIBRARIES_PATH,
                                    mod.jbk.build.BuiltInLibraries.resolveFolderName(logicalId)
                                    + "/classes.jar");
                            File targetJar = new File(LocalLibraryManager.getJarPath(issue.subject));
                            if (internalJar.exists()) {
                                copyFileSafe(internalJar, targetJar);
                                Log.i(TAG, "Re-extracted built-in JAR for: " + issue.subject);
                                fixed++;
                            } else {
                                try {
                                    mod.jbk.build.BuiltInLibraries.extractCompileAssets(
                                            new mod.jbk.build.BuildProgressReceiver[0]);
                                    if (internalJar.exists()) {
                                        copyFileSafe(internalJar, targetJar);
                                        Log.i(TAG, "Re-extracted (full) JAR: " + issue.subject);
                                        fixed++;
                                    }
                                } catch (Exception ex) {
                                    Log.w(TAG, "Cannot re-extract: " + ex.getMessage());
                                }
                            }
                        }
                        break;
                    }

                    case FOLDER_MISSING: {
                        String logicalId = issue.subject.replaceAll("-\\d[\\d.]*.*$", "");
                        boolean isBuiltIn = mod.jbk.build.BuiltInLibraries.BUNDLED_VERSIONS
                                .containsKey(logicalId);
                        if (isBuiltIn) {
                            File internal = new File(
                                    mod.jbk.build.BuiltInLibraries.EXTRACTED_BUILT_IN_LIBRARIES_PATH,
                                    mod.jbk.build.BuiltInLibraries.resolveFolderName(logicalId));
                            File external = new File(LocalLibraryManager.getLibraryPath(issue.subject));
                            if (internal.exists() && internal.isDirectory()) {
                                copyDirSafe(internal, external);
                                Log.i(TAG, "Re-copied built-in folder: " + issue.subject);
                                fixed++;
                            }
                        } else {
                            removeDanglingReferences(issue.subject);
                            Log.w(TAG, "Removed dangling refs for: " + issue.subject);
                            fixed++;
                        }
                        break;
                    }

                    case CORRUPT_PROJECT_JSON: {
                        File f = LocalLibrariesUtil.getLocalLibFile(issue.subject);
                        AtomicFileWriter.write(f, "[]");
                        Log.i(TAG, "Reset corrupt local_library for project " + issue.subject);
                        fixed++;
                        break;
                    }

                    case EMPTY_FOLDER: {
                        LocalLibraryItem stub = new LocalLibraryItem();
                        stub.name = issue.subject;
                        LocalLibraryManager.saveMetadata(stub);
                        fixed++;
                        break;
                    }

                    case MISSING_METADATA: {
                        LocalLibraryItem meta = LocalLibraryManager.loadLibrary(issue.subject);
                        if (meta != null) {
                            LocalLibraryManager.saveMetadata(meta);
                            fixed++;
                        }
                        break;
                    }

                    case JAR_CHECKSUM_MISMATCH: {
                        File jar = new File(LocalLibraryManager.getJarPath(issue.subject));
                        if (jar.exists()) {
                            String newHash = sha256Hex(jar);
                            if (newHash != null) {
                                LocalLibraryItem item = LocalLibraryManager.loadLibrary(issue.subject);
                                if (item != null) {
                                    item.jarSha256 = newHash;
                                    LocalLibraryManager.saveMetadata(item);
                                    fixed++;
                                }
                            }
                        }
                        break;
                    }

                    default:
                        break;
                }
            } catch (Exception e) {
                Log.w(TAG, "autoFix failed for " + issue, e);
            }
        }
        return fixed;
    }

    private static void removeDanglingReferences(String libName) {
        try {
            File dataRoot = new File(LocalLibraryManager.getProjectsDataPath());
            if (!dataRoot.exists()) return;
            File[] dirs = dataRoot.listFiles(File::isDirectory);
            if (dirs == null) return;
            for (File dir : dirs) {
                LibraryProjectLinker.removeLibraryFromProject(dir.getName(), libName);
            }
        } catch (Exception e) {
            Log.w(TAG, "removeDanglingReferences failed: " + libName, e);
        }
    }

    private static void copyFileSafe(File src, File dst) {
        try {
            if (dst.getParentFile() != null && !dst.getParentFile().exists())
                dst.getParentFile().mkdirs();
            try (java.io.FileInputStream in = new java.io.FileInputStream(src);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
        } catch (Exception e) {
            Log.w(TAG, "copyFileSafe failed: " + src + " → " + dst, e);
        }
    }

    private static void copyDirSafe(File src, File dst) {
        try {
            if (src.isDirectory()) {
                if (!dst.exists()) dst.mkdirs();
                File[] ch = src.listFiles();
                if (ch != null) for (File c : ch) copyDirSafe(c, new File(dst, c.getName()));
            } else {
                copyFileSafe(src, dst);
            }
        } catch (Exception e) {
            Log.w(TAG, "copyDirSafe failed", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void checkLibraries(List<LocalLibraryItem> libs,
                                        HealthReport report,
                                        ProgressCallback progress) {
        int total = libs.size();
        for (int i = 0; i < total; i++) {
            LocalLibraryItem lib = libs.get(i);
            if (progress != null) progress.onProgress(i + 1, total, lib.name);

            File libDir = new File(LocalLibraryManager.getLibraryPath(lib.name));

            // Proposal 3: folder deleted externally
            if (!libDir.exists()) {
                report.issues.add(new Issue(IssueType.FOLDER_MISSING, lib.name,
                        "Library folder does not exist on disk", true));
                continue;
            }

            // Empty folder
            File[] contents = libDir.listFiles();
            if (contents == null || contents.length == 0) {
                report.issues.add(new Issue(IssueType.EMPTY_FOLDER, lib.name,
                        "Library folder is empty", true));
                continue;
            }

            // Proposal 1: JAR check
            File jar = new File(LocalLibraryManager.getJarPath(lib.name));
            File dex = new File(lib.dexPath != null ? lib.dexPath :
                    LocalLibraryManager.getLibraryPath(lib.name) + "classes.dex");

            if (!jar.exists()) {
                if (dex.exists()) {
                    report.issues.add(new Issue(IssueType.DEX_ONLY, lib.name,
                            "Only classes.dex found — JAR is missing. "
                            + "Build from source will fail.", true));
                } else {
                    report.issues.add(new Issue(IssueType.JAR_MISSING, lib.name,
                            "classes.jar not found in library folder", true));
                }
            } else {
                // Proposal 18: JAR integrity
                if (!isValidZip(jar)) {
                    report.issues.add(new Issue(IssueType.JAR_CORRUPT, lib.name,
                            "classes.jar is not a valid ZIP file (may be truncated)", true));
                }
            }

            // Missing metadata
            File metaFile = new File(LocalLibraryManager.getMetadataPath(lib.name));
            if (!metaFile.exists()) {
                report.issues.add(new Issue(IssueType.MISSING_METADATA, lib.name,
                        "library.json is missing", true));
            }
        }
    }

    /** Proposal 2: finds libraries sharing the same base name */
    private static void checkDuplicates(List<LocalLibraryItem> libs, HealthReport report) {
        java.util.Map<String, List<String>> byBase = new java.util.HashMap<>();
        for (LocalLibraryItem lib : libs) {
            String base = lib.name.replaceAll("-\\d+(\\.\\d+)*$", "").toLowerCase();
            byBase.computeIfAbsent(base, k -> new ArrayList<>()).add(lib.name);
        }
        for (java.util.Map.Entry<String, List<String>> e : byBase.entrySet()) {
            if (e.getValue().size() > 1) {
                String names = String.join(", ", e.getValue());
                for (String name : e.getValue()) {
                    report.issues.add(new Issue(IssueType.DUPLICATE_BASE_NAME, name,
                            "Duplicate: " + names + " — only highest version is used at build time",
                            false));
                }
            }
        }
    }

    /** Proposal 4: scans all project local_library files for corruption */
    private static void checkProjectJsonFiles(HealthReport report) {
        File dataRoot = new File(LocalLibraryManager.getProjectsDataPath());
        if (!dataRoot.exists()) return;
        File[] dirs = dataRoot.listFiles(File::isDirectory);
        if (dirs == null) return;
        report.checkedProjects = dirs.length;
        for (File dir : dirs) {
            String scId = dir.getName();
            File libFile = new File(dir, "local_library");
            if (!libFile.exists()) continue;
            String content = AtomicFileWriter.read(libFile).trim();
            if (content.isEmpty() || content.equals("[]")) continue;
            // Must be a JSON array
            if (!content.startsWith("[") || !content.endsWith("]")) {
                report.issues.add(new Issue(IssueType.CORRUPT_PROJECT_JSON, scId,
                        "local_library file is corrupt (not a JSON array)", true));
            }
        }
    }

    // ── Zip / SHA-256 ─────────────────────────────────────────────────────────

    static boolean isValidZip(File f) {
        if (!f.exists() || f.length() < 4) return false;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            int magic = raf.readInt();
            return magic == 0x504B0304 || magic == 0x504B0506 || magic == 0x504B0708;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns lowercase hex SHA-256 of file, or null on error */
    public static String sha256Hex(File f) {
        if (!f.exists()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = fis.read(buf)) != -1) md.update(buf, 0, n);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "SHA-256 failed for " + f.getName(), e);
            return null;
        }
    }

    // ── Proposal 5: AndroidManifest.xml conflict check ────────────────────────

    public static class ManifestConflict {
        public final String libName;
        public final String permission;
        public final String conflictsWithLib; // empty = conflicts with project manifest
        ManifestConflict(String lib, String perm, String other) {
            libName = lib; permission = perm; conflictsWithLib = other;
        }
        @Override public String toString() {
            return libName + " declares " + permission
                    + (conflictsWithLib.isEmpty() ? "" : " (also in " + conflictsWithLib + ")");
        }
    }

    /**
     * Scans AndroidManifest.xml files for all given libraries and returns
     * a list of permissions declared by more than one library (or by the
     * project manifest if projectManifestFile is provided).
     */
    public static List<ManifestConflict> checkManifestConflicts(
            List<LocalLibraryItem> libs, File projectManifestFile) {

        List<ManifestConflict> conflicts = new ArrayList<>();
        // permission → first library that declared it
        java.util.Map<String, String> seen = new java.util.LinkedHashMap<>();

        // Seed with project manifest permissions
        if (projectManifestFile != null && projectManifestFile.exists()) {
            for (String perm : extractPermissions(AtomicFileWriter.read(projectManifestFile))) {
                seen.put(perm, "project");
            }
        }

        for (LocalLibraryItem lib : libs) {
            String manifestPath = LocalLibraryManager.getLibraryPath(lib.name) + "AndroidManifest.xml";
            File manifest = new File(manifestPath);
            if (!manifest.exists()) continue;
            String xml = AtomicFileWriter.read(manifest);
            for (String perm : extractPermissions(xml)) {
                if (seen.containsKey(perm)) {
                    conflicts.add(new ManifestConflict(lib.name, perm, seen.get(perm)));
                } else {
                    seen.put(perm, lib.name);
                }
            }
        }
        return conflicts;
    }

    private static List<String> extractPermissions(String manifestXml) {
        List<String> perms = new ArrayList<>();
        if (manifestXml == null || manifestXml.isEmpty()) return perms;
        // Match <uses-permission android:name="..." />
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("uses-permission[^>]+android:name=\"([^\"]+)\"")
                .matcher(manifestXml);
        while (m.find()) perms.add(m.group(1));
        return perms;
    }

    // ── Proposal 20: Kotlin runtime check ────────────────────────────────────

    /**
     * Returns libraries that require a Kotlin stdlib version newer than
     * what is bundled in DayDream (from BuiltInLibraries.BUNDLED_VERSIONS).
     */
    public static List<Issue> checkKotlinRuntime(List<LocalLibraryItem> libs) {
        List<Issue> issues = new ArrayList<>();
        String bundled = mod.jbk.build.BuiltInLibraries.BUNDLED_VERSIONS
                .getOrDefault("kotlin-stdlib", "0");

        for (LocalLibraryItem lib : libs) {
            if (lib.kotlinRequired == null || lib.kotlinRequired.isEmpty()) continue;
            // If the library needs a newer Kotlin than what's bundled
            try {
                if (LibraryVersionChecker.isNewer(lib.kotlinRequired, bundled)) {
                    issues.add(new Issue(IssueType.MISSING_METADATA, lib.name,
                            "Requires kotlin-stdlib ≥ " + lib.kotlinRequired
                            + " but bundled is " + bundled, false));
                }
            } catch (Exception ignored) {}
        }
        return issues;
    }

    /**
     * Instance wrapper: checks the given library metadata list for health issues.
     * Returns a flat list of issues found.
     */
    public List<Issue> check(java.util.Collection<LocalLibraryMetadata> metadata) {
        // Build a synthetic list of LocalLibraryItem from metadata for the existing static checks
        List<dev.aldi.sayuti.editor.manage.LocalLibraryItem> items = new ArrayList<>();
        for (LocalLibraryMetadata m : metadata) {
            dev.aldi.sayuti.editor.manage.LocalLibraryItem item =
                    new dev.aldi.sayuti.editor.manage.LocalLibraryItem();
            item.name    = m.name;
            item.version = m.version != null ? m.version : "1.0.0";
            items.add(item);
        }
        HealthReport report = new HealthReport();
        // Run duplicate check
        checkDuplicates(items, report);
        return report.issues;
    }
}
