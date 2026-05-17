package pro.sketchware.project;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public final class ProjectFileScanner {
    private ProjectFileScanner() {}

    public static ProjectFileNode scan(File root) {
        ProjectFileNode node = new ProjectFileNode(root);
        File[] children = root == null ? null : root.listFiles(file -> !file.getName().equals(".git") && !file.getName().equals("build"));
        if (children == null) return node;
        Arrays.sort(children, Comparator.comparing(File::isFile).thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) node.children.add(scan(child));
        return node;
    }
}
