package pro.sketchware.activities.projecttools;

import android.os.Environment;

import java.io.File;

public final class ProjectToolPaths {

    private ProjectToolPaths() {
    }

    public static File getSketchwareDir() {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware");
    }

    public static File getProjectDataDir(String scId) {
        return new File(getSketchwareDir(), "data" + File.separator + scId);
    }

    public static File getProjectGitMirrorDir(String scId) {
        return new File(getSketchwareDir(), "git" + File.separator + scId);
    }

    public static File getProjectBackupDir(String scId) {
        return new File(getSketchwareDir(), "backups" + File.separator + scId);
    }

    public static File getProjectExportDir(String scId) {
        return new File(getSketchwareDir(), "exports" + File.separator + scId);
    }

    public static File getProjectLibDexCacheDir(String scId) {
        return new File(getProjectDataDir(scId), "cache" + File.separator + "lib-dex");
    }

    public static File getProjectEditableRoot(String scId) {
        return new File(getProjectDataDir(scId), "files");
    }

    public static File getProjectEditableJavaDir(String scId) {
        return new File(getProjectEditableRoot(scId), "java");
    }

    public static File getProjectEditableResDir(String scId) {
        return new File(getProjectEditableRoot(scId), "resource");
    }

    public static File getProjectEditableAssetsDir(String scId) {
        return new File(getProjectEditableRoot(scId), "assets");
    }

    public static File getProjectInjectionDir(String scId) {
        return new File(getProjectDataDir(scId), "Injection");
    }

    public static File getProjectGradleInjectionDir(String scId) {
        return new File(getProjectInjectionDir(scId), "gradle");
    }

    public static File getProjectMyscDir(String scId) {
        return new File(getSketchwareDir(), "mysc" + File.separator + scId);
    }

    public static File getProjectGeneratedAppDir(String scId) {
        return new File(getProjectMyscDir(scId), "app");
    }

    public static File getProjectGeneratedJavaDir(String scId) {
        return new File(getProjectGeneratedAppDir(scId), "src/main/java");
    }

    public static File getProjectGeneratedResDir(String scId) {
        return new File(getProjectGeneratedAppDir(scId), "src/main/res");
    }

    public static File getProjectGeneratedAssetsDir(String scId) {
        return new File(getProjectGeneratedAppDir(scId), "src/main/assets");
    }

    public static File getProjectGeneratedManifestFile(String scId) {
        return new File(getProjectGeneratedAppDir(scId), "src/main/AndroidManifest.xml");
    }

    public static File getProjectGeneratedAppGradleFile(String scId) {
        return new File(getProjectGeneratedAppDir(scId), "build.gradle");
    }

    public static File getProjectGeneratedSettingsGradleFile(String scId) {
        return new File(getProjectMyscDir(scId), "settings.gradle");
    }

    public static File getProjectGeneratedProjectGradleFile(String scId) {
        return new File(getProjectMyscDir(scId), "build.gradle");
    }

    public static File getProjectGeneratedGradlePropertiesFile(String scId) {
        return new File(getProjectMyscDir(scId), "gradle.properties");
    }

    public static boolean isEditableFile(String scId, File file) {
        if (file == null) {
            return false;
        }
        return isUnder(file, getProjectEditableRoot(scId)) || isUnder(file, getProjectInjectionDir(scId));
    }

    public static boolean isGeneratedFile(String scId, File file) {
        return file != null && isUnder(file, getProjectMyscDir(scId));
    }

    public static boolean isUnder(File file, File root) {
        try {
            String filePath = file.getCanonicalPath();
            String rootPath = root.getCanonicalPath();
            return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String relativize(File root, File file) {
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.equals(rootPath)) {
                return "";
            }
            if (filePath.startsWith(rootPath + File.separator)) {
                return filePath.substring((rootPath + File.separator).length()).replace(File.separatorChar, '/');
            }
        } catch (Exception ignored) {
        }
        return file.getName();
    }

    public static File findGeneratedCounterpart(String scId, File editableFile) {
        if (editableFile == null || !editableFile.isFile()) {
            return null;
        }
        if (isUnder(editableFile, getProjectEditableJavaDir(scId))) {
            String rel = relativize(getProjectEditableJavaDir(scId), editableFile);
            File candidate = new File(getProjectGeneratedJavaDir(scId), rel);
            return candidate.isFile() ? candidate : null;
        }
        if (isUnder(editableFile, getProjectEditableResDir(scId))) {
            String rel = relativize(getProjectEditableResDir(scId), editableFile);
            File candidate = new File(getProjectGeneratedResDir(scId), rel);
            return candidate.isFile() ? candidate : null;
        }
        if (isUnder(editableFile, getProjectEditableAssetsDir(scId))) {
            String rel = relativize(getProjectEditableAssetsDir(scId), editableFile);
            File candidate = new File(getProjectGeneratedAssetsDir(scId), rel);
            return candidate.isFile() ? candidate : null;
        }
        return null;
    }
}
