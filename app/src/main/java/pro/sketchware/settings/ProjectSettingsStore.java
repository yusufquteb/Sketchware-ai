package pro.sketchware.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProjectSettingsStore {
    private final SharedPreferences preferences;
    private final String projectId;

    public ProjectSettingsStore(Context context, String projectId) {
        this.preferences = context.getSharedPreferences("project_settings", Context.MODE_PRIVATE);
        this.projectId = projectId == null ? "" : projectId;
    }

    public void putString(String key, String value) { preferences.edit().putString(projectId + ":" + key, value).apply(); }
    public String getString(String key, String fallback) { return preferences.getString(projectId + ":" + key, fallback); }
    public void putBoolean(String key, boolean value) { preferences.edit().putBoolean(projectId + ":" + key, value).apply(); }
    public boolean getBoolean(String key, boolean fallback) { return preferences.getBoolean(projectId + ":" + key, fallback); }
}
