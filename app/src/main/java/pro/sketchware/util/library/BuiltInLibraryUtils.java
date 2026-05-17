package pro.sketchware.util.library;

import android.util.Log;

import mod.jbk.build.BuiltInLibraries;
import mod.jbk.build.BuiltInLibraries.BuiltInLibrary;

public class BuiltInLibraryUtils {
    private static final String TAG = "BuiltInLibUtils";

    /**
     * Returns the known dependencies for a given built-in library.
     * Returns an empty array if the library is unknown, which can happen when
     * opening projects created by another Sketchware variant or a future build.
     *
     * @apiNote This method won't return the dependencies' sub-dependencies!
     */
    public static String[] getKnownDependencies(String libraryName) {
        for (BuiltInLibrary library : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            if (library.getName().equals(libraryName)) {
                return library.getDependencyNames().toArray(new String[0]);
            }
        }

        Log.w(TAG, "Unknown built-in library '" + libraryName + "' — returning no dependencies");
        return new String[0];
    }

    /**
     * Returns the package name of a given built-in library, or an empty string
     * when the library is unknown or has no generated resources package.
     */
    public static String getPackageName(String libraryName) {
        for (BuiltInLibrary library : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            if (library.getName().equals(libraryName)) {
                return library.getPackageName().orElse("");
            }
        }

        Log.w(TAG, "getPackageName: unknown built-in library '" + libraryName + "'");
        return "";
    }

    /**
     * Returns whether a given built-in library has resources that need to be mapped to a R.java file
     * by a resource processor. Unknown libraries are treated as resource-less.
     */
    public static boolean hasResources(String libraryName) {
        for (BuiltInLibrary library : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            if (library.getName().equals(libraryName)) {
                return library.hasResources();
            }
        }

        Log.w(TAG, "hasResources: unknown built-in library '" + libraryName + "'");
        return false;
    }
}
