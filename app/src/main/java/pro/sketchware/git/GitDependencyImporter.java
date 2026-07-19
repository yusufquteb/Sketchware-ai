package pro.sketchware.git;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class GitDependencyImporter {
    private GitDependencyImporter() {}

    public static List<File> collectDependencyFiles(File repositoryRoot) {
        List<File> files = new ArrayList<>();
        collectIfExists(files, repositoryRoot, "build.gradle");
        collectIfExists(files, repositoryRoot, "settings.gradle");
        collectIfExists(files, repositoryRoot, "gradle/libs.versions.toml");
        collectIfExists(files, repositoryRoot, "app/build.gradle");
        return files;
    }

    private static void collectIfExists(List<File> files, File root, String relative) {
        File file = new File(root, relative);
        if (file.isFile()) files.add(file);
    }
}
