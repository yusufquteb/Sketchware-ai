package a.a.a;

/**
 * Backward-compatible alias for project/build scripts that still reference the
 * legacy a.a.a package name used by other Sketchware variants.
 */
@Deprecated
public class BuiltInLibraryManager extends pro.sketchware.util.library.BuiltInLibraryManager {
    public BuiltInLibraryManager(String projectId) {
        super(projectId);
    }
}
