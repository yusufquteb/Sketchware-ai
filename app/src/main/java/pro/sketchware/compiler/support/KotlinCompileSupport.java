package pro.sketchware.compiler.support;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class KotlinCompileSupport {
    private KotlinCompileSupport() {}

    public static boolean hasKotlinSources(File root) {
        File[] files = root == null ? null : root.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory() && hasKotlinSources(f)) return true;
            if (f.isFile() && f.getName().endsWith(".kt")) return true;
        }
        return false;
    }

    public static List<String> sourcePaths(File root) {
        List<String> out = new ArrayList<>(); collect(root, out); return out;
    }
    private static void collect(File file, List<String> out) {
        File[] files = file == null ? null : file.listFiles(); if (files == null) return;
        for (File f : files) { if (f.isDirectory()) collect(f, out); else if (f.getName().endsWith(".kt")) out.add(f.getAbsolutePath()); }
    }
}
