package pro.sketchware.importer;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import mod.jbk.build.BuiltInLibraries;

/**
 * Production-safe mapping between Maven coordinates and Sketchware Pro's bundled libraries.
 *
 * <p>This registry is intentionally conservative: it only maps coordinates that are known to be
 * bundled by the host app. The importer uses it to avoid downloading duplicate copies of built-in
 * AndroidX/Material/common libraries when the bundled version is equal to or newer than what the
 * imported project requests.</p>
 */
public final class BuiltInDependencyRegistry {

    private static final Map<String, String> COORDINATE_TO_BUNDLED_LIBRARY;

    static {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        map.put("androidx.activity:activity", BuiltInLibraries.ANDROIDX_ACTIVITY);
        map.put("androidx.annotation:annotation-experimental", BuiltInLibraries.ANDROIDX_ANNOTATION_EXPERIMENTAL);
        map.put("androidx.annotation:annotation-jvm", BuiltInLibraries.ANDROIDX_ANNOTATION_JVM);
        map.put("androidx.appcompat:appcompat", BuiltInLibraries.ANDROIDX_APPCOMPAT);
        map.put("androidx.appcompat:appcompat-resources", BuiltInLibraries.ANDROIDX_APPCOMPAT_RESOURCES);
        map.put("androidx.asynclayoutinflater:asynclayoutinflater", BuiltInLibraries.ANDROIDX_ASYNCLAYOUTINFLATER);
        map.put("androidx.browser:browser", BuiltInLibraries.ANDROIDX_BROWSER);
        map.put("androidx.cardview:cardview", BuiltInLibraries.ANDROIDX_CARDVIEW);
        map.put("androidx.collection:collection-jvm", BuiltInLibraries.ANDROIDX_COLLECTION_JVM);
        map.put("androidx.concurrent:concurrent-futures", BuiltInLibraries.ANDROIDX_CONCURRENT_FUTURES);
        map.put("androidx.constraintlayout:constraintlayout", BuiltInLibraries.ANDROIDX_CONSTRAINTLAYOUT);
        map.put("androidx.constraintlayout:constraintlayout-core", BuiltInLibraries.ANDROIDX_CONSTRAINTLAYOUT_CORE);
        map.put("androidx.coordinatorlayout:coordinatorlayout", BuiltInLibraries.ANDROIDX_COORDINATORLAYOUT);
        map.put("androidx.core:core", BuiltInLibraries.ANDROIDX_CORE);
        map.put("androidx.core:core-ktx", BuiltInLibraries.ANDROIDX_CORE_KTX);
        map.put("androidx.core:core-viewtree", BuiltInLibraries.ANDROIDX_CORE_VIEWTREE);
        map.put("androidx.cursoradapter:cursoradapter", BuiltInLibraries.ANDROIDX_CURSORADAPTER);
        map.put("androidx.customview:customview", BuiltInLibraries.ANDROIDX_CUSTOMVIEW);
        map.put("androidx.documentfile:documentfile", BuiltInLibraries.ANDROIDX_DOCUMENTFILE);
        map.put("androidx.drawerlayout:drawerlayout", BuiltInLibraries.ANDROIDX_DRAWERLAYOUT);
        map.put("androidx.exifinterface:exifinterface", BuiltInLibraries.ANDROIDX_EXIFINTERFACE);
        map.put("androidx.fragment:fragment", BuiltInLibraries.ANDROIDX_FRAGMENT);
        map.put("androidx.interpolator:interpolator", BuiltInLibraries.ANDROIDX_INTERPOLATOR);
        map.put("androidx.legacy:legacy-support-core-ui", BuiltInLibraries.ANDROIDX_LEGACY_SUPPORT_CORE_UI);
        map.put("androidx.legacy:legacy-support-core-utils", BuiltInLibraries.ANDROIDX_LEGACY_SUPPORT_CORE_UTILS);
        map.put("androidx.legacy:legacy-support-v13", BuiltInLibraries.ANDROIDX_LEGACY_SUPPORT_V13);
        map.put("androidx.legacy:legacy-support-v4", BuiltInLibraries.ANDROIDX_LEGACY_SUPPORT_V4);
        map.put("androidx.lifecycle:lifecycle-common", BuiltInLibraries.ANDROIDX_LIFECYCLE_COMMON);
        map.put("androidx.lifecycle:lifecycle-livedata", BuiltInLibraries.ANDROIDX_LIFECYCLE_LIVEDATA);
        map.put("androidx.lifecycle:lifecycle-livedata-core", BuiltInLibraries.ANDROIDX_LIFECYCLE_LIVEDATA_CORE);
        map.put("androidx.lifecycle:lifecycle-process", BuiltInLibraries.ANDROIDX_LIFECYCLE_PROCESS);
        map.put("androidx.lifecycle:lifecycle-runtime", BuiltInLibraries.ANDROIDX_LIFECYCLE_RUNTIME);
        map.put("androidx.lifecycle:lifecycle-service", BuiltInLibraries.ANDROIDX_LIFECYCLE_SERVICE);
        map.put("androidx.lifecycle:lifecycle-viewmodel", BuiltInLibraries.ANDROIDX_LIFECYCLE_VIEWMODEL);
        map.put("androidx.lifecycle:lifecycle-viewmodel-savedstate", BuiltInLibraries.ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE);
        map.put("androidx.loader:loader", BuiltInLibraries.ANDROIDX_LOADER);
        map.put("androidx.localbroadcastmanager:localbroadcastmanager", BuiltInLibraries.ANDROIDX_LOCALBROADCASTMANAGER);
        map.put("androidx.media:media", BuiltInLibraries.ANDROIDX_MEDIA);
        map.put("androidx.multidex:multidex", BuiltInLibraries.ANDROIDX_MULTIDEX);
        map.put("androidx.recyclerview:recyclerview", BuiltInLibraries.ANDROIDX_RECYCLERVIEW);
        map.put("androidx.room:room-common", BuiltInLibraries.ANDROIDX_ROOM_COMMON);
        map.put("androidx.room:room-runtime", BuiltInLibraries.ANDROIDX_ROOM_RUNTIME);
        map.put("androidx.savedstate:savedstate", BuiltInLibraries.ANDROIDX_SAVEDSTATE);
        map.put("androidx.slidingpanelayout:slidingpanelayout", BuiltInLibraries.ANDROIDX_SLIDINGPANELAYOUT);
        map.put("androidx.sqlite:sqlite", BuiltInLibraries.ANDROIDX_SQLITE);
        map.put("androidx.sqlite:sqlite-framework", BuiltInLibraries.ANDROIDX_SQLITE_FRAMEWORK);
        map.put("androidx.startup:startup-runtime", BuiltInLibraries.ANDROIDX_STARTUP_RUNTIME);
        map.put("androidx.swiperefreshlayout:swiperefreshlayout", BuiltInLibraries.ANDROIDX_SWIPEREFRESHLAYOUT);
        map.put("androidx.tracing:tracing", BuiltInLibraries.ANDROIDX_TRACING);
        map.put("androidx.transition:transition", BuiltInLibraries.ANDROIDX_TRANSITION);
        map.put("androidx.vectordrawable:vectordrawable", BuiltInLibraries.ANDROIDX_VECTORDRAWABLE);
        map.put("androidx.vectordrawable:vectordrawable-animated", BuiltInLibraries.ANDROIDX_VECTORDRAWABLE_ANIMATED);
        map.put("androidx.versionedparcelable:versionedparcelable", BuiltInLibraries.ANDROIDX_VERSIONEDPARCELABLE);
        map.put("androidx.viewpager:viewpager", BuiltInLibraries.ANDROIDX_VIEWPAGER);
        map.put("androidx.viewpager2:viewpager2", BuiltInLibraries.ANDROIDX_VIEWPAGER2);
        map.put("androidx.work:work-runtime", BuiltInLibraries.ANDROIDX_WORK_RUNTIME);
        map.put("com.airbnb.android:lottie", BuiltInLibraries.LOTTIE);
        map.put("com.github.bumptech.glide:glide", BuiltInLibraries.GLIDE);
        map.put("com.google.android.material:material", BuiltInLibraries.MATERIAL);
        map.put("com.google.code.gson:gson", BuiltInLibraries.GSON);
        map.put("com.squareup.okhttp3:okhttp", BuiltInLibraries.OKHTTP_ANDROID);
        map.put("com.squareup.okio:okio", BuiltInLibraries.OKIO_JVM);
        map.put("org.jetbrains.kotlin:kotlin-stdlib", BuiltInLibraries.JETBRAINS_KOTLIN_STDLIB);
        map.put("org.jetbrains.kotlinx:kotlinx-coroutines-android", BuiltInLibraries.JETBRAINS_KOTLINX_COROUTINES_ANDROID);
        map.put("org.jetbrains.kotlinx:kotlinx-coroutines-core", BuiltInLibraries.JETBRAINS_KOTLINX_COROUTINES_CORE_JVM);

        COORDINATE_TO_BUNDLED_LIBRARY = Collections.unmodifiableMap(map);
    }

    private BuiltInDependencyRegistry() {
    }

    @Nullable
    public static Match findSatisfiedBuiltIn(String groupId, String artifactId, @Nullable String requestedVersion) {
        Match match = findBundled(groupId, artifactId, requestedVersion);
        if (match == null || !match.isSatisfiedByBundled()) {
            return null;
        }
        return match;
    }

    @Nullable
    public static Match findBundled(String groupId, String artifactId, @Nullable String requestedVersion) {
        String bundledLibrary = COORDINATE_TO_BUNDLED_LIBRARY.get(normalizeCoordinate(groupId, artifactId));
        if (bundledLibrary == null) {
            return null;
        }
        String bundledVersion = extractVersionFromBundledName(bundledLibrary);
        boolean satisfies = requestedVersion == null || requestedVersion.isEmpty()
                || isVersionAtLeast(bundledVersion, requestedVersion);
        return new Match(groupId, artifactId, requestedVersion, bundledLibrary, bundledVersion, satisfies);
    }

    public static boolean isBundledCoordinate(String groupId, String artifactId) {
        return COORDINATE_TO_BUNDLED_LIBRARY.containsKey(normalizeCoordinate(groupId, artifactId));
    }

    public static String normalizeCoordinate(String groupId, String artifactId) {
        return (groupId == null ? "" : groupId.trim().toLowerCase(Locale.US)) + ":"
                + (artifactId == null ? "" : artifactId.trim().toLowerCase(Locale.US));
    }

    @Nullable
    public static String extractVersionFromBundledName(@Nullable String bundledLibraryName) {
        if (bundledLibraryName == null || bundledLibraryName.isEmpty()) {
            return null;
        }
        int dashIndex = bundledLibraryName.lastIndexOf('-');
        if (dashIndex < 0 || dashIndex + 1 >= bundledLibraryName.length()) {
            return null;
        }
        String tail = bundledLibraryName.substring(dashIndex + 1);
        if (!Character.isDigit(tail.charAt(0))) {
            return null;
        }
        return tail;
    }

    public static boolean isVersionAtLeast(@Nullable String bundledVersion, @Nullable String requestedVersion) {
        if (requestedVersion == null || requestedVersion.isEmpty()) {
            return true;
        }
        if (bundledVersion == null || bundledVersion.isEmpty()) {
            return false;
        }
        int[] bundledParts = parseVersion(bundledVersion);
        int[] requestedParts = parseVersion(requestedVersion);
        int length = Math.max(bundledParts.length, requestedParts.length);
        for (int i = 0; i < length; i++) {
            int bundled = i < bundledParts.length ? bundledParts[i] : 0;
            int requested = i < requestedParts.length ? requestedParts[i] : 0;
            if (bundled > requested) {
                return true;
            }
            if (bundled < requested) {
                return false;
            }
        }
        return true;
    }

    private static int[] parseVersion(String value) {
        String sanitized = value == null ? "" : value.trim();
        if (sanitized.startsWith("v") || sanitized.startsWith("V")) {
            sanitized = sanitized.substring(1);
        }
        sanitized = sanitized.replace('_', '.');
        int dashIndex = sanitized.indexOf('-');
        if (dashIndex >= 0) {
            sanitized = sanitized.substring(0, dashIndex);
        }
        String[] rawParts = sanitized.split("\\.");
        int[] parsed = new int[rawParts.length];
        for (int i = 0; i < rawParts.length; i++) {
            StringBuilder digits = new StringBuilder();
            for (int j = 0; j < rawParts[i].length(); j++) {
                char ch = rawParts[i].charAt(j);
                if (Character.isDigit(ch)) {
                    digits.append(ch);
                } else {
                    break;
                }
            }
            if (digits.length() == 0) {
                parsed[i] = 0;
            } else {
                try {
                    parsed[i] = Integer.parseInt(digits.toString());
                } catch (NumberFormatException ignored) {
                    parsed[i] = 0;
                }
            }
        }
        return parsed;
    }

    public static final class Match {
        public final String groupId;
        public final String artifactId;
        public final String requestedVersion;
        public final String bundledLibraryName;
        public final String bundledVersion;
        public final boolean satisfiedByBundled;

        Match(String groupId, String artifactId, String requestedVersion,
              String bundledLibraryName, String bundledVersion, boolean satisfiedByBundled) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.requestedVersion = requestedVersion;
            this.bundledLibraryName = bundledLibraryName;
            this.bundledVersion = bundledVersion;
            this.satisfiedByBundled = satisfiedByBundled;
        }

        public boolean isSatisfiedByBundled() {
            return satisfiedByBundled;
        }

        public String toDisplayLine() {
            String requested = requestedVersion == null || requestedVersion.isEmpty() ? "unspecified" : requestedVersion;
            String bundled = bundledVersion == null || bundledVersion.isEmpty() ? bundledLibraryName : bundledVersion;
            return groupId + ":" + artifactId + ":" + requested + " -> bundled " + bundled;
        }
    }
}
