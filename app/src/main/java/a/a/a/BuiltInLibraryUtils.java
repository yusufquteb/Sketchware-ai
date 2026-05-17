package a.a.a;

/**
 * Backward-compatible delegate for project/build scripts that still reference
 * the legacy a.a.a package name used by other Sketchware variants.
 */
@Deprecated
public class BuiltInLibraryUtils {

    public static String[] getKnownDependencies(String libraryName) {
        return pro.sketchware.util.library.BuiltInLibraryUtils.getKnownDependencies(libraryName);
    }

    public static String getPackageName(String libraryName) {
        return pro.sketchware.util.library.BuiltInLibraryUtils.getPackageName(libraryName);
    }

    public static boolean hasResources(String libraryName) {
        return pro.sketchware.util.library.BuiltInLibraryUtils.hasResources(libraryName);
    }
}
