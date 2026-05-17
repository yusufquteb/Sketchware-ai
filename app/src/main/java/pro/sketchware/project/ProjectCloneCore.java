package pro.sketchware.project;

import java.io.File;

import pro.sketchware.utility.io.SafeFileOps;

public final class ProjectCloneCore {
    private ProjectCloneCore() {}

    public static void cloneProject(File source, File target) throws Exception {
        if (target.exists()) throw new IllegalStateException("Target already exists: " + target);
        SafeFileOps.copyTree(source, target);
        ProjectCleanupCore.clean(target);
    }
}
