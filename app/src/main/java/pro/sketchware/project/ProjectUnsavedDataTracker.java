package pro.sketchware.project;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import pro.sketchware.utility.io.SafeFileOps;

public final class ProjectUnsavedDataTracker {
    private final Map<File, String> baselines = new HashMap<>();

    public void markSaved(File file) throws Exception { baselines.put(file.getCanonicalFile(), SafeFileOps.sha256(file)); }
    public boolean hasUnsavedChanges(File file) throws Exception {
        String oldHash = baselines.get(file.getCanonicalFile());
        return oldHash != null && !oldHash.equals(SafeFileOps.sha256(file));
    }
    public void clear(File file) throws Exception { baselines.remove(file.getCanonicalFile()); }
}
