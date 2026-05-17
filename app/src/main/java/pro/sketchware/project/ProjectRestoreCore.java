package pro.sketchware.project;

import java.io.File;

import pro.sketchware.utility.io.SafeFileOps;

public final class ProjectRestoreCore {
    private ProjectRestoreCore() {}

    public static void restore(File backupZip, File destination, boolean replace) throws Exception {
        if (destination.exists() && replace) SafeFileOps.deleteRecursively(destination);
        SafeFileOps.ensureDirectory(destination);
        SafeFileOps.unzip(backupZip, destination);
    }
}
