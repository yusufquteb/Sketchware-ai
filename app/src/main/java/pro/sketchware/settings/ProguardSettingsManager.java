package pro.sketchware.settings;

public final class ProguardSettingsManager {
    private final ProjectSettingsStore store;
    public ProguardSettingsManager(ProjectSettingsStore store) { this.store = store; }
    public void setEnabled(boolean enabled) { store.putBoolean("proguardEnabled", enabled); }
    public boolean isEnabled() { return store.getBoolean("proguardEnabled", false); }
    public void setRules(String rules) { store.putString("proguardRules", rules == null ? "" : rules); }
    public String getRules() { return store.getString("proguardRules", ""); }
}
