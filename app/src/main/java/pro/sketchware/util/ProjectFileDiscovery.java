package pro.sketchware.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers files only inside the currently selected Sketchware project.
 */
public class ProjectFileDiscovery {

    private static final int MAX_DISCOVERY_DEPTH = 10;

    public static class FileInfo {
        public final String path;
        public final String name;
        public final boolean isDirectory;
        public final boolean isEncrypted;
        public final long size;

        public FileInfo(String path, String name, boolean isDirectory, boolean isEncrypted, long size) {
            this.path = path;
            this.name = name;
            this.isDirectory = isDirectory;
            this.isEncrypted = isEncrypted;
            this.size = size;
        }
    }

    public static List<FileInfo> discoverFiles(String scId, String relativePath) {
        List<FileInfo> files = new ArrayList<>();

        try {
            File sketchwareRoot = ProjectPathResolver.getSketchwareRoot();

            if (relativePath == null || relativePath.isEmpty()) {
                for (File root : ProjectPathResolver.getReadableRoots(scId)) {
                    if (root.exists()) {
                        listFilesRecursive(root, sketchwareRoot, files, 0);
                    }
                }
                return files;
            }

            ProjectPathResolver.ResolvedPath resolvedPath = ProjectPathResolver.resolveForRead(scId, relativePath);
            if (resolvedPath == null) {
                return files;
            }

            File target = resolvedPath.getFile();
            if (!target.exists()) {
                return files;
            }

            if (target.isDirectory()) {
                listFilesRecursive(target, sketchwareRoot, files, 0);
            } else {
                files.add(toFileInfo(target, sketchwareRoot));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return files;
    }

    private static void listFilesRecursive(File dir, File sketchwareRoot, List<FileInfo> files, int depth) {
        try {
            File[] children = dir.listFiles();
            if (children == null) {
                return;
            }

            for (File child : children) {
                files.add(toFileInfo(child, sketchwareRoot));

                if (child.isDirectory() && depth < MAX_DISCOVERY_DEPTH) {
                    listFilesRecursive(child, sketchwareRoot, files, depth + 1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static FileInfo toFileInfo(File file, File sketchwareRoot) {
        String relativePath = sketchwareRoot.toPath()
                .relativize(file.toPath())
                .toString()
                .replace(File.separator, "/");

        return new FileInfo(
                relativePath,
                file.getName(),
                file.isDirectory(),
                isEncryptedFile(file),
                file.isFile() ? file.length() : 0
        );
    }

    private static boolean isEncryptedFile(File file) {
        if (file.isDirectory()) {
            return false;
        }

        String name = file.getName();
        if (!name.contains(".")) {
            return true;
        }

        String ext = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
        return !ext.equals("xml")
                && !ext.equals("json")
                && !ext.equals("txt")
                && !ext.equals("java")
                && !ext.equals("kt")
                && !ext.equals("gradle")
                && !ext.equals("properties")
                && !ext.equals("html")
                && !ext.equals("md")
                && !ext.equals("pro");
    }

    public static List<FileInfo> searchFiles(String scId, String pattern) {
        List<FileInfo> allFiles = discoverFiles(scId, null);
        List<FileInfo> matches = new ArrayList<>();

        String lowerPattern = pattern.toLowerCase();
        for (FileInfo file : allFiles) {
            if (file.name.toLowerCase().contains(lowerPattern)
                    || file.path.toLowerCase().contains(lowerPattern)) {
                matches.add(file);
            }
        }

        return matches;
    }

    public static FileInfo getFileInfo(String scId, String relativePath) {
        try {
            ProjectPathResolver.ResolvedPath resolvedPath = ProjectPathResolver.resolveForRead(scId, relativePath);
            if (resolvedPath == null || !resolvedPath.getFile().exists()) {
                return null;
            }

            return new FileInfo(
                    resolvedPath.getRelativePath(),
                    resolvedPath.getFile().getName(),
                    resolvedPath.getFile().isDirectory(),
                    isEncryptedFile(resolvedPath.getFile()),
                    resolvedPath.getFile().isFile() ? resolvedPath.getFile().length() : 0
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
