package pro.sketchware.util.library;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import a.a.a.Jp;
import a.a.a.ProjectBuilder;
import a.a.a.yq;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.editor.manage.library.EnableBuiltInLibrariesActivity;
import mod.jbk.editor.manage.library.ExcludeBuiltInLibrariesActivity;
import pro.sketchware.SketchApplication;

/**
 * A class to keep track of a project's built-in libraries.
 */

public class BuiltInLibraryManager {

    private static final String TAG = ProjectBuilder.TAG;

    private final ArrayList<String> libraryNames = new ArrayList<>();
    private final ArrayList<Jp> libraries = new ArrayList<>();
    private final List<BuiltInLibraries.BuiltInLibrary> excludedLibraries;
    private final List<BuiltInLibraries.BuiltInLibrary> manuallyEnabledLibraries;
    /** Tracks processed library names to avoid duplicate work and dependency cycles. */
    private final Set<String> processedNames = new HashSet<>();

    public BuiltInLibraryManager(String projectId) {
        excludedLibraries = ExcludeBuiltInLibrariesActivity.getExcludedLibraries(projectId);
        manuallyEnabledLibraries = EnableBuiltInLibrariesActivity.getEnabledLibraries(projectId);
        for (BuiltInLibraries.BuiltInLibrary library : manuallyEnabledLibraries) {
            addLibrary(library.getName());
        }
    }

    /**
     * Add a built-in library to the project libraries list.
     * Won't add a library if it's in the list already,
     * or it got excluded with {@link ExcludeBuiltInLibrariesActivity}.
     *
     * <p>Unknown libraries are accepted without dependency expansion so projects
     * created by another Sketchware variant do not crash the builder.</p>
     *
     * @param libraryName The built-in library's name, e.g. material-1.0.0
     */
    public void addLibrary(String libraryName) {
        if (libraryName == null || libraryName.isEmpty()) {
            return;
        }
        if (!processedNames.add(libraryName)) {
            Log.v(TAG, "Didn't reprocess built-in library \"" + libraryName + "\"");
            return;
        }

        Optional<BuiltInLibraries.BuiltInLibrary> library = BuiltInLibraries.BuiltInLibrary.ofName(libraryName);
        //noinspection SimplifyOptionalCallChains because #isEmpty() isn't available on Android.
        boolean isExcluded = library.isPresent() && excludedLibraries.contains(library.get());
        if (!isExcluded) {
            if (!libraryNames.contains(libraryName)) {
                Log.d(TAG, "Added built-in library \"" + libraryName + "\" to project's dependencies");
                libraryNames.add(libraryName);
                libraries.add(new Jp(libraryName));
            } else {
                Log.v(TAG, "Didn't add built-in library \"" + libraryName + "\" to project's dependencies again");
            }
        } else {
            Log.v(TAG, "Didn't add built-in library \"" + libraryName + "\" to project's dependencies as it's excluded");
            Log.v(TAG, "Adding its dependencies though");
        }

        addDependencies(libraryName);
    }

    private void addDependencies(String libraryName) {
        for (String libraryDependency : BuiltInLibraryUtils.getKnownDependencies(libraryName)) {
            addLibrary(libraryDependency);
        }
    }

    public boolean containsLibrary(String libraryName) {
        Optional<BuiltInLibraries.BuiltInLibrary> library = BuiltInLibraries.BuiltInLibrary.ofName(libraryName);
        //noinspection SimplifyOptionalCallChains because #isEmpty() isn't available on Android.
        if (!library.isPresent()) {
            return libraryNames.contains(libraryName);
        }
        return libraries.contains(new Jp(library.get().getName()));
    }

    /**
     * @return {@link BuiltInLibraryManager#libraries}
     */
    public ArrayList<Jp> getLibraries() {
        return libraries;
    }

    /**
     * Validates that each registered built-in library has an extracted classes.jar.
     * This is useful for diagnosing stale/corrupt compile assets before build steps fail.
     *
     * @return names of libraries whose classes.jar is missing or empty
     */
    public List<String> validateClasspath() {
        List<String> missing = new ArrayList<>();
        for (String name : libraryNames) {
            if (!BuiltInLibraries.isLibraryClassesJarAvailable(name)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            Log.e(TAG, "Built-in libraries with missing classes.jar: " + missing);
        }
        return missing;
    }

    public static List<BuiltInLibraries.BuiltInLibrary> getEffectiveEnabledLibraries(String projectId) {
        try {
            yq workspace = new yq(SketchApplication.getContext(), projectId);
            ProjectBuilder projectBuilder = new ProjectBuilder(SketchApplication.getContext(), workspace);
            projectBuilder.buildBuiltInLibraryInformation();
            List<BuiltInLibraries.BuiltInLibrary> libraries = new ArrayList<>();
            for (Jp library : projectBuilder.getBuiltInLibraryManager().getLibraries()) {
                BuiltInLibraries.BuiltInLibrary.ofName(library.getName()).ifPresent(libraries::add);
            }
            libraries.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return libraries;
        } catch (Throwable ignored) {
            return new ArrayList<>(EnableBuiltInLibrariesActivity.getEnabledLibraries(projectId));
        }
    }

}
