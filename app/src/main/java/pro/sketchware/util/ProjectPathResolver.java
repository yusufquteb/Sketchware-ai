package pro.sketchware.util;

import android.os.Environment;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves project-scoped paths for chat tools so they never escape the
 * currently selected Sketchware project.
 */
public final class ProjectPathResolver {

    private static final String[] DATA_ALIASES = {
            "file",
            "logic",
            "view",
            "library",
            "resource",
            "permission",
            "import",
            "files",
            "compile_log",
            "local_library",
            "custom_blocks",
            "project_config",
            "build_config",
            "proguard",
            "proguard_fm",
            "proguard-rules.pro",
            "stringfog",
            "injection",
            "Injection",
            "converted-vectors"
    };

    private static final String[] MYSC_ALIASES = {
            "app",
            "bin",
            "gen"
    };

    private ProjectPathResolver() {
    }

    public static final class ResolvedPath {
        private final File file;
        private final String relativePath;

        public ResolvedPath(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }

        public File getFile() {
            return file;
        }

        public String getRelativePath() {
            return relativePath;
        }
    }

    public static File getSketchwareRoot() {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware");
    }

    public static List<File> getReadableRoots(String scId) {
        List<File> roots = new ArrayList<>();
        roots.add(new File(getSketchwareRoot(), "data/" + scId));
        roots.add(new File(getSketchwareRoot(), "mysc/list/" + scId));
        roots.add(new File(getSketchwareRoot(), "mysc/" + scId));
        return roots;
    }

    public static List<File> getWritableRoots(String scId) {
        List<File> roots = new ArrayList<>();
        roots.add(new File(getSketchwareRoot(), "data/" + scId));
        roots.add(new File(getSketchwareRoot(), "mysc/list/" + scId));
        return roots;
    }

    @Nullable
    public static ResolvedPath resolveForRead(String scId, String requestedPath) {
        return resolve(scId, requestedPath, true);
    }

    @Nullable
    public static ResolvedPath resolveForWrite(String scId, String requestedPath) {
        return resolve(scId, requestedPath, false);
    }

    private static ResolvedPath resolve(String scId, String requestedPath, boolean readOnlyScope) {
        if (scId == null || scId.trim().isEmpty() || requestedPath == null) {
            return null;
        }

        String normalizedPath = normalize(requestedPath);
        if (normalizedPath.isEmpty()) {
            return null;
        }

        String mappedRelativePath = mapToProjectScope(scId, normalizedPath);
        if (mappedRelativePath == null) {
            return null;
        }

        if (!readOnlyScope && !isWritableProjectPath(scId, mappedRelativePath)) {
            return null;
        }

        File root = getSketchwareRoot();
        File candidate = new File(root, mappedRelativePath.replace("/", File.separator));
        if (!isInsideAllowedRoots(scId, candidate, readOnlyScope)) {
            return null;
        }

        return new ResolvedPath(candidate, mappedRelativePath);
    }

    private static String normalize(String requestedPath) {
        String normalizedPath = requestedPath.trim().replace("\\", "/");
        normalizedPath = normalizedPath.replaceAll("/{2,}", "/");

        int sketchwareIndex = normalizedPath.indexOf(".sketchware/");
        if (sketchwareIndex >= 0) {
            normalizedPath = normalizedPath.substring(sketchwareIndex + ".sketchware/".length());
        }

        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        if (normalizedPath.contains("../") || normalizedPath.contains("..\\")) {
            return "";
        }

        return normalizedPath;
    }

    @Nullable
    private static String mapToProjectScope(String scId, String normalizedPath) {
        if (normalizedPath.equals("project") || normalizedPath.equals("project.json")) {
            return "mysc/list/" + scId + "/project";
        }

        String scopedDataPrefix = "data/" + scId;
        String scopedListPrefix = "mysc/list/" + scId;
        String scopedMyscPrefix = "mysc/" + scId;

        if (normalizedPath.equals(scopedDataPrefix)
                || normalizedPath.startsWith(scopedDataPrefix + "/")
                || normalizedPath.equals(scopedListPrefix)
                || normalizedPath.startsWith(scopedListPrefix + "/")
                || normalizedPath.equals(scopedMyscPrefix)
                || normalizedPath.startsWith(scopedMyscPrefix + "/")) {
            return normalizedPath;
        }

        if (normalizedPath.startsWith("data/") || normalizedPath.startsWith("mysc/")) {
            return null;
        }

        if (startsWithAny(normalizedPath, DATA_ALIASES)) {
            return scopedDataPrefix + "/" + normalizedPath;
        }

        if (startsWithAny(normalizedPath, MYSC_ALIASES)) {
            return scopedMyscPrefix + "/" + normalizedPath;
        }

        return null;
    }

    private static boolean startsWithAny(String path, String[] prefixes) {
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWritableProjectPath(String scId, String mappedRelativePath) {
        String dataPrefix = "data/" + scId + "/";
        if (mappedRelativePath.startsWith(dataPrefix)) {
            return true;
        }
        return mappedRelativePath.equals("mysc/list/" + scId + "/project");
    }

    private static boolean isInsideAllowedRoots(String scId, File candidate, boolean readOnlyScope) {
        try {
            String candidatePath = candidate.getCanonicalPath();
            for (File root : readOnlyScope ? getReadableRoots(scId) : getWritableRoots(scId)) {
                String allowedPath = root.getCanonicalPath();
                if (candidatePath.equals(allowedPath) || candidatePath.startsWith(allowedPath + File.separator)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }
}
