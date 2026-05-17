package pro.sketchware.project;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import pro.sketchware.utility.io.SafeFileOps;

public final class ProjectCleanupCore {
    private static final Set<String> DEFAULT_NAMES = new HashSet<>(Arrays.asList("build", ".gradle", "captures", "tmp", "temp"));
    private ProjectCleanupCore() {}

    public static int clean(File root) throws Exception { return clean(root, DEFAULT_NAMES); }

    public static int clean(File root, Set<String> removableNames) throws Exception {
        if (root == null || !root.exists()) return 0;
        int count = 0;
        File[] children = root.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            if (removableNames.contains(child.getName())) count += SafeFileOps.deleteRecursively(child);
            else if (child.isDirectory()) count += clean(child, removableNames);
        }
        return count;
    }
}
