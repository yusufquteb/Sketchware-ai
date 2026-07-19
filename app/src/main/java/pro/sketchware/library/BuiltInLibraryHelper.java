package pro.sketchware.library;

import dev.aldi.sayuti.editor.manage.LocalLibraryItem;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Defines all libraries that ship with DayDream (formerly "built-in libraries")
 * and handles their one-time migration into the standard
 * {@code local_libs/} directory on external storage.
 *
 * <p>After migration every built-in library has:
 * <ul>
 *   <li>A proper library folder under {@code .sketchware/libs/local_libs/{name}/}</li>
 *   <li>A {@code library.json} file with {@code isBuiltIn = true}</li>
 *   <li>A {@code classes.jar} copied from the app's internal assets</li>
 * </ul>
 *
 * <p>Migration is idempotent: already-migrated libraries are skipped.
 *
 * <p>In the NEXT phase built-in libraries will be DELETED from the app;
 * only the on-disk copies in local_libs will survive. This class is designed
 * to make that transition seamless.
 */
public class BuiltInLibraryHelper {

    private static final String TAG = "BuiltInLibHelper";

    /** SharedPreferences key to record that migration has been done */
    public static final String PREF_MIGRATION_DONE = "builtins_migrated_v1";

    // ─────────────────────────────────────────────────────────────────────────
    //  Built-in library definitions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Immutable descriptor for a built-in library.
     */
    public static class BuiltInLibDef {
        /** Folder name in local_libs (no version suffix here) */
        public final String name;
        /** Current bundled version */
        public final String version;
        /**
         * Path inside app assets where the JAR lives.
         * Can be null if the JAR is shipped as a plain JAR in app/libs.
         */
        public final String assetJarPath;
        /** Path inside app/libs (absolute at runtime, null if not there) */
        public final String internalJarName;
        /** Maven/Gradle dependency string */
        public final String dependency;
        /** Public download URL for the latest version (GitHub release or Maven) */
        public final String downloadUrl;

        public BuiltInLibDef(String name, String version, String assetJarPath,
                             String internalJarName, String dependency, String downloadUrl) {
            this.name            = name;
            this.version         = version;
            this.assetJarPath    = assetJarPath;
            this.internalJarName = internalJarName;
            this.dependency      = dependency;
            this.downloadUrl     = downloadUrl;
        }
    }

    /**
     * The canonical list of all DayDream built-in libraries.
     *
     * Add / remove entries here as the bundled library set changes.
     * Versions should be kept in sync with app/build.gradle.
     */
    public static final List<BuiltInLibDef> ALL_BUILT_INS = Arrays.asList(

        // ── Core Android / AndroidX ───────────────────────────────────────────
        new BuiltInLibDef(
            "appcompat", "1.7.1",
            null, "appcompat-1.7.1.aar",
            "androidx.appcompat:appcompat:1.7.1",
            "https://maven.google.com/web/index.html#androidx.appcompat:appcompat"
        ),
        new BuiltInLibDef(
            "recyclerview", "1.4.0",
            null, "recyclerview-1.4.0.aar",
            "androidx.recyclerview:recyclerview:1.4.0",
            "https://maven.google.com/web/index.html#androidx.recyclerview:recyclerview"
        ),
        new BuiltInLibDef(
            "cardview", "1.0.0",
            null, "cardview-1.0.0.aar",
            "androidx.cardview:cardview:1.0.0",
            "https://maven.google.com/web/index.html#androidx.cardview:cardview"
        ),
        new BuiltInLibDef(
            "constraintlayout", "2.2.1",
            null, "constraintlayout-2.2.1.aar",
            "androidx.constraintlayout:constraintlayout:2.2.1",
            "https://maven.google.com/web/index.html#androidx.constraintlayout:constraintlayout"
        ),

        // ── Material Design ───────────────────────────────────────────────────
        new BuiltInLibDef(
            "material", "1.13.0",
            null, "material-1.13.0.aar",
            "com.google.android.material:material:1.13.0",
            "https://maven.google.com/web/index.html#com.google.android.material:material"
        ),

        // ── Firebase ──────────────────────────────────────────────────────────
        new BuiltInLibDef(
            "firebase-auth", "23.2.0",
            null, "firebase-auth-23.2.0.aar",
            "com.google.firebase:firebase-auth:23.2.0",
            "https://firebase.google.com/docs/android/setup"
        ),
        new BuiltInLibDef(
            "firebase-database", "21.0.0",
            null, "firebase-database-21.0.0.aar",
            "com.google.firebase:firebase-database:21.0.0",
            "https://firebase.google.com/docs/database/android/start"
        ),
        new BuiltInLibDef(
            "firebase-storage", "21.0.1",
            null, "firebase-storage-21.0.1.aar",
            "com.google.firebase:firebase-storage:21.0.1",
            "https://firebase.google.com/docs/storage/android/start"
        ),

        // ── Google Play Services ──────────────────────────────────────────────
        new BuiltInLibDef(
            "play-services-maps", "19.2.0",
            null, "play-services-maps-19.2.0.aar",
            "com.google.android.gms:play-services-maps:19.2.0",
            "https://developers.google.com/maps/documentation/android-sdk/start"
        ),
        new BuiltInLibDef(
            "play-services-location", "21.3.0",
            null, "play-services-location-21.3.0.aar",
            "com.google.android.gms:play-services-location:21.3.0",
            "https://developers.google.com/android/reference/com/google/android/gms/location/package-summary"
        ),

        // ── Networking ────────────────────────────────────────────────────────
        new BuiltInLibDef(
            "okhttp", "4.12.0",
            null, "okhttp-4.12.0.jar",
            "com.squareup.okhttp3:okhttp:4.12.0",
            "https://github.com/square/okhttp/releases"
        ),
        new BuiltInLibDef(
            "retrofit", "2.11.0",
            null, "retrofit-2.11.0.jar",
            "com.squareup.retrofit2:retrofit:2.11.0",
            "https://github.com/square/retrofit/releases"
        ),
        new BuiltInLibDef(
            "gson", "2.11.0",
            null, "gson-2.11.0.jar",
            "com.google.code.gson:gson:2.11.0",
            "https://github.com/google/gson/releases"
        ),

        // ── Image loading ─────────────────────────────────────────────────────
        new BuiltInLibDef(
            "glide", "4.16.0",
            null, "glide-4.16.0.jar",
            "com.github.bumptech.glide:glide:4.16.0",
            "https://github.com/bumptech/glide/releases"
        ),

        // ── Sketchware internal ───────────────────────────────────────────────
        new BuiltInLibDef(
            "sketchware-core", "1.0.0",
            null, "base_libs.jar",
            "sketchware:sketchware-core:1.0.0",
            ""   // internal only
        )
    );

    // ─────────────────────────────────────────────────────────────────────────
    //  Queries
    // ─────────────────────────────────────────────────────────────────────────

    /** True if the given name matches any known built-in library definition */
    public static boolean isBuiltIn(String name) {
        if (name == null) return false;
        for (BuiltInLibDef def : ALL_BUILT_INS) {
            if (def.name.equals(name)) return true;
        }
        return false;
    }

    /** Returns the definition for the given name, or null */
    public static BuiltInLibDef getDefinition(String name) {
        for (BuiltInLibDef def : ALL_BUILT_INS) {
            if (def.name.equals(name)) return def;
        }
        return null;
    }

    /** Returns only those built-ins that have NOT yet been migrated */
    public static List<BuiltInLibDef> getPendingMigration() {
        List<BuiltInLibDef> pending = new ArrayList<>();
        for (BuiltInLibDef def : ALL_BUILT_INS) {
            if (!LocalLibraryManager.libraryExists(def.name)) {
                pending.add(def);
            }
        }
        return pending;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Migration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Callback interface for migration progress.
     */
    public interface MigrationCallback {
        void onProgress(String libName, int done, int total);
        void onComplete(int migratedCount, int failedCount);
    }

    /**
     * Migrates all built-in libraries to local_libs asynchronously.
     * Already-migrated libraries are skipped.
     *
     * Must be called from a background thread; calls back on the same thread.
     */
    public static void migrateAllBuiltIns(Context context, MigrationCallback cb) {
        LocalLibraryManager.ensureLocalLibsDirExists();
        List<BuiltInLibDef> all = ALL_BUILT_INS;
        int total     = all.size();
        int migrated  = 0;
        int failed    = 0;

        for (int i = 0; i < all.size(); i++) {
            BuiltInLibDef def = all.get(i);
            if (cb != null) cb.onProgress(def.name, i, total);

            try {
                boolean ok = migrateOneBuiltIn(context, def);
                if (ok) migrated++;
                else    failed++;
            } catch (Exception e) {
                Log.e(TAG, "Failed to migrate " + def.name, e);
                failed++;
            }
        }
        if (cb != null) cb.onComplete(migrated, failed);
    }

    /**
     * Migrates a single built-in library.
     * Returns true if either already migrated or successfully migrated now.
     */
    public static boolean migrateOneBuiltIn(Context context, BuiltInLibDef def) {
        // Already migrated?
        if (LocalLibraryManager.libraryExists(def.name)
                && LocalLibraryManager.libraryHasJar(def.name)) {
            // Ensure metadata is up-to-date
            LocalLibraryItem existing = LocalLibraryManager.loadLibrary(def.name);
            if (existing != null && !existing.isBuiltIn) {
                existing.isBuiltIn = true;
                LocalLibraryManager.saveMetadata(existing);
            }
            return true;
        }

        // Create folder
        LocalLibraryManager.createLibraryFolder(def.name);

        // Create metadata
        LocalLibraryItem item = new LocalLibraryItem();
        item.name        = def.name;
        item.version     = def.version;
        item.isBuiltIn   = true;
        item.dependency  = def.dependency;
        item.downloadUrl = def.downloadUrl;
        LocalLibraryManager.saveMetadata(item);

        // Copy JAR from app/libs (internal)
        if (def.internalJarName != null && !def.internalJarName.isEmpty()) {
            File appLibsDir = new File(context.getApplicationInfo().sourceDir)
                    .getParentFile();
            // Check app libs directory
            File[] possibleDirs = {
                new File(context.getFilesDir(), "libs"),
                new File(context.getApplicationInfo().nativeLibraryDir),
                appLibsDir
            };
            for (File dir : possibleDirs) {
                File jar = new File(dir, def.internalJarName);
                if (jar.exists()) {
                    try {
                        LocalLibraryManager.copyJarFromFile(jar,
                                def.name);
                        Log.i(TAG, "Migrated " + def.name + " from " + jar);
                        return true;
                    } catch (Exception e) {
                        Log.w(TAG, "Copy failed from " + jar, e);
                    }
                }
            }
        }

        // Copy from app assets
        if (def.assetJarPath != null && !def.assetJarPath.isEmpty()) {
            try {
                boolean ok = LocalLibraryManager.copyJarFromAssets(
                        context, def.assetJarPath, def.name);
                if (ok) {
                    Log.i(TAG, "Migrated " + def.name + " from assets");
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Asset copy failed for " + def.name, e);
            }
        }

        // JAR not available yet — folder and metadata are created,
        // user can download it later via LibraryUpdateManager.
        Log.w(TAG, "No JAR source found for " + def.name
                + "; folder created, download pending");
        return true; // Not a hard failure — metadata is in place
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Resolve library during project run
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Result of a library resolution attempt.
     */
    public static class ResolveResult {
        public enum Status { FOUND, FOUND_BY_BASENAME, NOT_FOUND, JAR_MISSING }
        public Status status;
        public LocalLibraryItem item;
        /** True if the JAR was auto-downloaded and is now ready */
        public boolean wasDownloaded = false;

        public ResolveResult(Status s, LocalLibraryItem i) {
            this.status = s;
            this.item   = i;
        }
    }

    /**
     * Attempts to resolve a library reference by exact name, then by base name.
     * Called just before a project run to verify all required libraries exist.
     *
     * @param requestedName the name as stored in the project (may include old version)
     * @return ResolveResult describing what was found
     */
    public static ResolveResult resolveLibraryForRun(String requestedName) {
        // 1. Exact match
        LocalLibraryItem exact = LocalLibraryManager.getLibraryByName(requestedName);
        if (exact != null && LocalLibraryManager.libraryHasJar(exact.name)) {
            return new ResolveResult(ResolveResult.Status.FOUND, exact);
        }

        // 2. Base-name fuzzy match (library was updated/renamed)
        String base = requestedName.replaceAll("-\\d+(\\.\\d+)*$", "");
        LocalLibraryItem fuzzy = LocalLibraryManager.findLibraryByBaseName(base);
        if (fuzzy != null && LocalLibraryManager.libraryHasJar(fuzzy.name)) {
            return new ResolveResult(ResolveResult.Status.FOUND_BY_BASENAME, fuzzy);
        }

        // 3. Found but JAR missing
        if (fuzzy != null) {
            return new ResolveResult(ResolveResult.Status.JAR_MISSING, fuzzy);
        }
        if (exact != null) {
            return new ResolveResult(ResolveResult.Status.JAR_MISSING, exact);
        }

        // 4. Completely absent
        return new ResolveResult(ResolveResult.Status.NOT_FOUND, null);
    }
}
