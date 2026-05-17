package pro.sketchware.settings;

public final class GradleSettingsManager {
    private final ProjectSettingsStore store;
    public GradleSettingsManager(ProjectSettingsStore store) { this.store = store; }
    public void setGradleInjection(String script) { store.putString("gradleInjection", script == null ? "" : script); }
    public String getGradleInjection() { return store.getString("gradleInjection", ""); }
}
