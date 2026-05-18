package pro.sketchware.features;

import android.content.Context;

import java.io.File;

import pro.sketchware.git.GitConfig;
import pro.sketchware.git.GitConfigStore;
import pro.sketchware.git.GitProjectWorkflow;
import pro.sketchware.settings.ProjectSettingsStore;

public final class FeatureServices {
    private FeatureServices() {}

    public static GitProjectWorkflow git(Context context, File repositoryRoot, String projectId) {
        GitConfig config = GitConfigStore.load(context, projectId);
        return new GitProjectWorkflow(repositoryRoot, config);
    }

    public static ProjectSettingsStore settings(Context context, String projectId) {
        return new ProjectSettingsStore(context, projectId);
    }
}
