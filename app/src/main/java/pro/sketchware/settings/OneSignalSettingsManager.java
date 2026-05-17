package pro.sketchware.settings;

public final class OneSignalSettingsManager {
    private final ProjectSettingsStore store;
    public OneSignalSettingsManager(ProjectSettingsStore store) { this.store = store; }
    public void setAppId(String appId) { store.putString("onesignalAppId", appId == null ? "" : appId); }
    public String getAppId() { return store.getString("onesignalAppId", ""); }
}
