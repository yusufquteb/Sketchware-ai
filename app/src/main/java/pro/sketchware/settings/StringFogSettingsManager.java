package pro.sketchware.settings;

public final class StringFogSettingsManager {
    private final ProjectSettingsStore store;
    public StringFogSettingsManager(ProjectSettingsStore store) { this.store = store; }
    public void setEnabled(boolean enabled) { store.putBoolean("stringFogEnabled", enabled); }
    public boolean isEnabled() { return store.getBoolean("stringFogEnabled", false); }
}
