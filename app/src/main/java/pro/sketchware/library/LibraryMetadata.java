package pro.sketchware.library;

import dev.aldi.sayuti.editor.manage.LocalLibraryItem;
/**
 * Gson-serialisable model written as "library.json" inside every
 * local_libs/{libName}/ folder.
 *
 * Keeping this separate from LocalLibraryItem lets us persist only
 * stable data and compute transient fields (projectCount, isUpdating…)
 * at runtime.
 */
public class LibraryMetadata {

    /** Must match the folder name */
    public String name         = "";
    public String version      = "1.0.0";
    public String latestVersion = "";
    public boolean isBuiltIn   = false;
    public String downloadUrl  = "";
    public String dependency   = "";
    public String changelog    = "";

    /** Epoch-ms of last remote version-check */
    public long lastChecked    = 0L;

    // ── Proposal 5: minSdk requirement ────────────────────────────────────────
    /** Minimum Android API level this library requires (0 = unknown) */
    public int minSdkRequired  = 0;

    // ── Proposal 10: path portability ─────────────────────────────────────────
    /**
     * True if this library's paths have been verified to be relative
     * (no absolute /storage/emulated/0/... paths in local_library references).
     */
    public boolean pathsRelative = false;

    // ── Proposal 12: update history ───────────────────────────────────────────
    /** Ordered list of versions this library was previously on (newest first) */
    public java.util.List<String> versionHistory = new java.util.ArrayList<>();

    // ── Proposal 13: changelog URL ────────────────────────────────────────────
    /** URL to release notes / changelog page */
    public String changelogUrl  = "";

    // ── Proposal 15: pin version ──────────────────────────────────────────────
    /**
     * When true, this library is excluded from "Check Updates" and
     * "Update All" operations. The user has pinned it at the current version.
     */
    public boolean pinned      = false;

    // ── Proposal 18: JAR integrity ────────────────────────────────────────────
    /** SHA-256 hex of classes.jar after last successful install/update */
    public String jarSha256    = "";

    // ── Proposal 11: size info ────────────────────────────────────────────────
    /** Approximate JAR size in bytes (filled at install time) */
    public long jarSizeBytes   = 0L;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static LibraryMetadata fromItem(LocalLibraryItem item) {
        LibraryMetadata m = new LibraryMetadata();
        m.name           = item.name;
        m.version        = item.version;
        m.latestVersion  = item.latestVersion;
        m.isBuiltIn      = item.isBuiltIn;
        m.downloadUrl    = item.downloadUrl;
        m.dependency     = item.dependency;
        m.changelog      = item.changelog;
        m.changelogUrl   = item.changelogUrl;
        m.pinned         = item.pinned;
        m.minSdkRequired = item.minSdkRequired;
        m.jarSha256      = item.jarSha256;
        m.jarSizeBytes   = item.jarSizeBytes;
        m.versionHistory = item.versionHistory;
        m.lastChecked    = System.currentTimeMillis();
        return m;
    }

    public LocalLibraryItem toItem() {
        LocalLibraryItem item = new LocalLibraryItem();
        item.name           = name;
        item.version        = version;
        item.latestVersion  = latestVersion;
        item.isBuiltIn      = isBuiltIn;
        item.downloadUrl    = downloadUrl;
        item.dependency     = dependency;
        item.changelog      = changelog;
        item.changelogUrl   = changelogUrl;
        item.pinned         = pinned;
        item.minSdkRequired = minSdkRequired;
        item.jarSha256      = jarSha256;
        item.jarSizeBytes   = jarSizeBytes;
        item.versionHistory = versionHistory != null ? versionHistory : new java.util.ArrayList<>();
        item.isUpdateAvailable = !pinned && item.hasUpdate();
        return item;
    }
}
