package pro.sketchware.settings;

public final class ThemeUiSettings {
    private final ProjectSettingsStore store;
    public ThemeUiSettings(ProjectSettingsStore store) { this.store = store; }
    public void setThemeName(String themeName) { store.putString("themeName", themeName == null ? "system" : themeName); }
    public String getThemeName() { return store.getString("themeName", "system"); }
    public void setCompactMode(boolean compact) { store.putBoolean("compactMode", compact); }
    public boolean isCompactMode() { return store.getBoolean("compactMode", false); }
}
