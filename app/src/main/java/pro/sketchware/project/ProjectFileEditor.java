package pro.sketchware.project;

import java.io.File;

import pro.sketchware.utility.io.SafeFileOps;

public final class ProjectFileEditor {
    private ProjectFileEditor() {}

    public static String read(File file) throws Exception { return SafeFileOps.readUtf8(file); }
    public static void save(File file, String content) throws Exception { SafeFileOps.writeUtf8Atomic(file, content); }
    public static void create(File file, String content) throws Exception { if (file.exists()) throw new IllegalStateException("File already exists: " + file); save(file, content); }
    public static boolean delete(File file) throws Exception { return SafeFileOps.deleteRecursively(file) > 0; }
}
