package dev.aldi.sayuti.editor.manage;

import static pro.sketchware.utility.FileUtil.formatFileSize;
import static pro.sketchware.utility.FileUtil.getFileSize;

import java.io.File;

public class LocalLibrary {
    private final String name;
    private final String size;
    private boolean isSelected;

    // ── Extended fields added for version/dependency tracking ──────────────
    private String dependency      = null;
    private String latestVersion   = null;
    private int    usageCount      = 0;
    private boolean versionChecked = false;

    private LocalLibrary(String name, String size) {
        this.name = name;
        this.size = size;
    }

    public static LocalLibrary fromFile(File file) {
        return new LocalLibrary(file.getName(), formatFileSize(getFileSize(file)));
    }

    // ── Core ───────────────────────────────────────────────────────────────
    public String getName()  { return name; }
    public String getSize()  { return size; }

    public boolean isSelected()                { return isSelected; }
    public void    setSelected(boolean v)      { isSelected = v; }

    // ── Dependency ─────────────────────────────────────────────────────────
    /** Maven coordinate, e.g. "com.squareup.retrofit2:retrofit:2.9.0". Null if unknown. */
    public String getDependency()           { return dependency; }
    public void   setDependency(String dep) { this.dependency = dep; }

    // ── Version info ───────────────────────────────────────────────────────
    /**
     * The "current" version is extracted from the folder name.
     * Returns the part after the last '-' that looks like a version, or null.
     */
    public String getCurrentVersion() {
        if (name == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)+)$").matcher(name);
        return m.find() ? m.group(1) : null;
    }

    /** Latest version fetched remotely; null until checked. */
    public String getLatestVersion()            { return latestVersion; }
    public void   setLatestVersion(String v)    { this.latestVersion = v; }

    // ── Usage count ────────────────────────────────────────────────────────
    public int  getUsageCount()          { return usageCount; }
    public void setUsageCount(int count) { this.usageCount = count; }

    // ── Version-check state ────────────────────────────────────────────────
    public boolean isVersionChecked()  { return versionChecked; }
    public void    markVersionChecked() { this.versionChecked = true; }

    // ── Search match ───────────────────────────────────────────────────────
    /**
     * Returns true if this library's name matches the given query
     * (case-insensitive substring or equality check).
     */
    public boolean matches(String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase().trim();
        if (name != null && name.toLowerCase().contains(q)) return true;
        if (dependency != null && dependency.toLowerCase().contains(q)) return true;
        return false;
    }
}
