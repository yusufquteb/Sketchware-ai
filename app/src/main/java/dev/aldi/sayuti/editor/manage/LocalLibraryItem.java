package dev.aldi.sayuti.editor.manage;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime data model for a single local (or built-in) library entry.
 * Persisted subset lives in {@link LibraryMetadata} / library.json.
 */
public class LocalLibraryItem {

    // ── Core identity ──────────────────────────────────────────────────────────
    public String name    = "";
    public String version = "1.0.0";
    public String latestVersion = "";
    public boolean isBuiltIn    = false;

    // ── File paths ────────────────────────────────────────────────────────────
    public String jarPath       = "";
    public String resFolderPath = "";
    public String pgRulesPath   = "";
    public String dexPath       = "";

    // ── Remote info ───────────────────────────────────────────────────────────
    public String downloadUrl  = "";
    public String dependency   = "";   // e.g. "com.squareup.retrofit2:retrofit:2.9.0"
    public String changelog    = "";
    public String changelogUrl = "";   // P13: link to release notes

    // ── P15: Pin version ──────────────────────────────────────────────────────
    /** When true: excluded from "Check Updates" and "Update All" */
    public boolean pinned = false;

    // ── P8: minSdk requirement ────────────────────────────────────────────────
    /** Minimum Android API level required (0 = unknown) */
    public int minSdkRequired = 0;

    // ── P18: JAR integrity ────────────────────────────────────────────────────
    public String jarSha256   = "";
    public long   jarSizeBytes = 0L;

    // ── P12: Update history ───────────────────────────────────────────────────
    public List<String> versionHistory = new ArrayList<>();

    // ── P21: Library groups ───────────────────────────────────────────────────
    /** User-defined group label, e.g. "Firebase", "Square", "" = ungrouped */
    public String groupName = "";

    // ── P20: Kotlin dependency ────────────────────────────────────────────────
    /** Minimum Kotlin stdlib version required by this library ("" = none) */
    public String kotlinRequired = "";

    // ── Project linkage ───────────────────────────────────────────────────────
    public int          projectCount     = 0;
    public List<String> linkedProjectIds = new ArrayList<>();
    /** Resolved project display names (loaded on demand for P16 delete dialog) */
    public List<String> linkedProjectNames = new ArrayList<>();

    // ── UI state (not persisted) ──────────────────────────────────────────────
    public boolean isUpdateAvailable  = false;
    public boolean isUpdating         = false;
    public boolean isEnabledInProject = false;
    /** P17: cached health issues count (0 = healthy) */
    public int healthIssueCount = 0;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public String getDisplayName() {
        if (version != null && !version.trim().isEmpty() && !version.equals("1.0.0")) {
            return name + "  " + version;
        }
        return name;
    }

    public boolean hasUpdate() {
        if (pinned) return false;
        return latestVersion != null
                && !latestVersion.trim().isEmpty()
                && !latestVersion.equals(version)
                && pro.sketchware.library.LocalLibraryManager.compareVersions(latestVersion, version) > 0;
    }

    public String getBaseName() {
        if (name == null) return "";
        return name.replaceAll("-\\d+(\\.\\d+)*$", "");
    }

    /** P14: Returns true if query matches name, dependency, or package name */
    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase().trim();
        if (name.toLowerCase().contains(q)) return true;
        if (dependency != null && dependency.toLowerCase().contains(q)) return true;
        // Package name is the groupId part of dependency, e.g. "com.squareup.retrofit2"
        if (dependency != null && dependency.contains(":")) {
            String pkg = dependency.split(":")[0].toLowerCase();
            if (pkg.contains(q)) return true;
        }
        if (groupName != null && groupName.toLowerCase().contains(q)) return true;
        return false;
    }

    @Override
    public String toString() {
        return "LocalLibraryItem{name='" + name + "', version='" + version
                + "', pinned=" + pinned + ", isBuiltIn=" + isBuiltIn
                + ", projects=" + projectCount + "}";
    }
}
