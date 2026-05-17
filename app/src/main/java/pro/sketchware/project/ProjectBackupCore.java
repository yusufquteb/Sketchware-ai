package pro.sketchware.project;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pro.sketchware.utility.io.SafeFileOps;

public final class ProjectBackupCore {
    private ProjectBackupCore() {}

    public static File backup(File projectRoot, File backupDirectory, String projectId) throws Exception {
        SafeFileOps.ensureDirectory(backupDirectory);
        String safeId = projectId == null || projectId.isEmpty() ? "project" : projectId.replaceAll("[^A-Za-z0-9._-]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File out = new File(backupDirectory, safeId + "_" + stamp + ".zip");
        SafeFileOps.zipDirectory(projectRoot, out);
        return out;
    }
}
