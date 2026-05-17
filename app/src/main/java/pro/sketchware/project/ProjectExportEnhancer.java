package pro.sketchware.project;

import java.io.File;

import pro.sketchware.utility.io.SafeFileOps;

public final class ProjectExportEnhancer {
    private ProjectExportEnhancer() {}

    public static File exportZip(File projectRoot, File outputDirectory, String name) throws Exception {
        SafeFileOps.ensureDirectory(outputDirectory);
        String fileName = (name == null || name.trim().isEmpty() ? "project" : name.trim().replaceAll("[^A-Za-z0-9._-]", "_")) + ".zip";
        File out = new File(outputDirectory, fileName);
        SafeFileOps.zipDirectory(projectRoot, out);
        return out;
    }
}
