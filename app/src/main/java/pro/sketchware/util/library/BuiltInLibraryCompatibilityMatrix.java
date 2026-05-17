package pro.sketchware.util.library;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.material3.Material3LibraryManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import a.a.a.jC;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.editor.manage.library.ExcludeBuiltInLibrariesActivity;

public final class BuiltInLibraryCompatibilityMatrix {

    public static final class ValidationResult {
        private final ArrayList<String> errors = new ArrayList<>();
        private final LinkedHashSet<String> requiredLibraries = new LinkedHashSet<>();

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public Set<String> getRequiredLibraries() {
            return requiredLibraries;
        }

        public String formatErrors() {
            return String.join("\n\n", errors);
        }
    }

    private BuiltInLibraryCompatibilityMatrix() {
    }

    @NonNull
    public static ValidationResult validate(String scId) {
        ProjectLibraryBean compat = jC.c(scId).c();
        ProjectLibraryBean firebase = jC.c(scId).d();
        ProjectLibraryBean admob = jC.c(scId).b();
        ProjectLibraryBean googleMap = jC.c(scId).e();
        Pair<Boolean, List<BuiltInLibraries.BuiltInLibrary>> exclusionConfig = ExcludeBuiltInLibrariesActivity.readConfigCompat(scId);
        return validate(scId, compat, firebase, admob, googleMap, exclusionConfig.first, exclusionConfig.second);
    }

    @NonNull
    public static ValidationResult validate(String scId, ProjectLibraryBean compat, ProjectLibraryBean firebase,
                                            ProjectLibraryBean admob, ProjectLibraryBean googleMap,
                                            boolean excludingEnabled, List<BuiltInLibraries.BuiltInLibrary> excludedLibraries) {
        ValidationResult result = new ValidationResult();
        boolean compatEnabled = compat != null && compat.isEnabled();
        boolean firebaseEnabled = firebase != null && firebase.isEnabled();
        boolean admobEnabled = admob != null && admob.isEnabled();
        boolean mapsEnabled = googleMap != null && googleMap.isEnabled();
        boolean material3Enabled = compat != null && new Material3LibraryManager(compat).isMaterial3Enabled();

        if (firebaseEnabled && !compatEnabled) {
            result.errors.add("Firebase is enabled, but AppCompat/Design is disabled. Enable AppCompat/Design before saving this library configuration.");
        }

        if (material3Enabled && !compatEnabled) {
            result.errors.add("Material 3 is enabled, but AppCompat/Design is disabled. Enable AppCompat/Design before saving this library configuration.");
        }

        if (compatEnabled) {
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_APPCOMPAT);
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_APPCOMPAT_RESOURCES);
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_ACTIVITY);
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_FRAGMENT);
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_CORE);
            addRequiredLibrary(result, BuiltInLibraries.MATERIAL);
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_COORDINATORLAYOUT);
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_DRAWERLAYOUT);
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_RECYCLERVIEW);
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_CONSTRAINTLAYOUT);
        }

        if (material3Enabled) {
            addRequiredLibrary(result, BuiltInLibraries.MATERIAL);
            addRequiredLibrary(result, BuiltInLibraries.ANDROIDX_GRAPHICS_SHAPES_ANDROID);
        }

        if (firebaseEnabled) {
            addRequiredLibrary(result, BuiltInLibraries.FIREBASE_AUTH);
            addRequiredLibrary(result, BuiltInLibraries.FIREBASE_DATABASE);
            addRequiredLibrary(result, BuiltInLibraries.FIREBASE_STORAGE);
            addRequiredLibrary(result, BuiltInLibraries.FIREBASE_MESSAGING);
        }

        if (admobEnabled) {
            addRequiredLibrary(result, BuiltInLibraries.PLAY_SERVICES_ADS);
            addRequiredLibrary(result, BuiltInLibraries.USER_MESSAGING_PLATFORM);
            addRequiredLibrary(result, BuiltInLibraries.PLAY_SERVICES_APPSET);
        }

        if (mapsEnabled) {
            addRequiredLibrary(result, BuiltInLibraries.PLAY_SERVICES_MAPS);
            addRequiredLibrary(result, BuiltInLibraries.PLAY_SERVICES_LOCATION);
        }

        if (excludingEnabled) {
            LinkedHashSet<String> excludedNames = new LinkedHashSet<>();
            for (BuiltInLibraries.BuiltInLibrary library : excludedLibraries) {
                excludedNames.add(library.getName());
            }
            for (String requiredLibrary : result.requiredLibraries) {
                if (excludedNames.contains(requiredLibrary)) {
                    result.errors.add("The excluded built-in libraries list removes required dependency '" + requiredLibrary + "'. Remove it from the exclusion list or disable the feature that depends on it.");
                }
            }
        }

        return result;
    }

    private static void addRequiredLibrary(ValidationResult result, String rootLibraryName) {
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(rootLibraryName);
        while (!pending.isEmpty()) {
            String libraryName = pending.removeFirst();
            if (!result.requiredLibraries.add(libraryName)) {
                continue;
            }
            Optional<BuiltInLibraries.BuiltInLibrary> library = BuiltInLibraries.BuiltInLibrary.ofName(libraryName);
            if (library.isPresent()) {
                pending.addAll(library.get().getDependencyNames());
            }
        }
    }
}
