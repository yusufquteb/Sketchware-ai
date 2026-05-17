package pro.sketchware.tools;

import java.io.File;

import pro.sketchware.project.ProjectBackupCore;
import pro.sketchware.project.ProjectCleanupCore;
import pro.sketchware.project.ProjectCloneCore;

public final class ProjectToolbox {
    private ProjectToolbox() {}
    public static File backup(File projectRoot, File backupDir, String projectId) throws Exception { return ProjectBackupCore.backup(projectRoot, backupDir, projectId); }
    public static int clean(File projectRoot) throws Exception { return ProjectCleanupCore.clean(projectRoot); }
    public static void cloneProject(File source, File target) throws Exception { ProjectCloneCore.cloneProject(source, target); }
}
