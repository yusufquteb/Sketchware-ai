package pro.sketchware.git;

import android.content.Context;
import android.content.SharedPreferences;

public final class GitConfigStore {
    private static final String PREFS = "project_git_configs";

    private GitConfigStore() {}

    public static void save(Context context, GitConfig config) {
        if (context == null || config == null || config.projectId == null) return;
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        String prefix = config.projectId + ":";
        editor.putString(prefix + "remoteUrl", config.remoteUrl == null ? "" : config.remoteUrl);
        editor.putString(prefix + "token", config.token == null ? "" : config.token);
        editor.putString(prefix + "branch", config.branch == null || config.branch.isEmpty() ? "main" : config.branch);
        editor.apply();
    }

    public static GitConfig load(Context context, String projectId) {
        GitConfig config = new GitConfig();
        config.projectId = projectId == null ? "" : projectId;
        if (context == null) return config;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String prefix = config.projectId + ":";
        config.remoteUrl = prefs.getString(prefix + "remoteUrl", "");
        config.token = prefs.getString(prefix + "token", "");
        config.branch = prefs.getString(prefix + "branch", "main");
        return config;
    }

    public static void delete(Context context, String projectId) {
        if (context == null || projectId == null) return;
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        String prefix = projectId + ":";
        editor.remove(prefix + "remoteUrl").remove(prefix + "token").remove(prefix + "branch").apply();
    }
}
