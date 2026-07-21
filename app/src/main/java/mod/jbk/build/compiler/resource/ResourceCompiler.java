package mod.jbk.build.compiler.resource;

import static com.besome.sketch.Config.VAR_DEFAULT_TARGET_SDK_VERSION;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;

import a.a.a.Jp;
import a.a.a.ProjectBuilder;
import a.a.a.zy;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.diagnostic.MissingFileException;
import mod.jbk.util.LogUtil;
import pro.sketchware.SketchApplication;
import pro.sketchware.utility.BinaryExecutor;
import pro.sketchware.utility.FileUtil;

/**
 * A class responsible for compiling a Project's resources.
 * Supports AAPT2.
 */
public class ResourceCompiler {

    /**
     * About log tags: add ":" and the first letter of the function's name camelCase'd.
     * For example, in thisIsALongFunctionName, you should use this:
     * <pre>
     *     TAG + ":tIALFN"
     * </pre>
     */
    private static final String TAG = "AppBuilder";
    private final boolean willBuildAppBundle;
    private final File aaptFile;
    private final BuildProgressReceiver progressReceiver;
    private final ProjectBuilder builder;

    public ResourceCompiler(ProjectBuilder builder, File aapt, boolean willBuildAppBundle, BuildProgressReceiver receiver) {
        this.willBuildAppBundle = willBuildAppBundle;
        aaptFile = aapt;
        progressReceiver = receiver;
        this.builder = builder;
    }

    public void compile() throws IOException, zy, MissingFileException {
        Compiler resourceCompiler;
        resourceCompiler = new Aapt2Compiler(builder, aaptFile, willBuildAppBundle);

        resourceCompiler.setProgressListener(new Compiler.ProgressListener() {
            @Override
            void onProgressUpdate(String newProgress, int step) {
                if (progressReceiver != null) progressReceiver.onProgress(newProgress, step);
            }
        });
        resourceCompiler.compile();
    }

    /**
     * A base class of a resource compiler.
     */
    interface Compiler {

        /**
         * Compile a project's resources fully.
         */
        void compile() throws zy, MissingFileException;

        /**
         * Set a progress listener to compiling.
         *
         * @param listener The listener object
         */
        void setProgressListener(ProgressListener listener);

        /**
         * A listener for progress on compilation.
         */
        abstract class ProgressListener {
            /**
             * The compiler has reached a new phase the user should know about.
             *
             * @param newProgress A String provided by the resource compiler the user should see.
             */
            abstract void onProgressUpdate(String newProgress, int step);
        }
    }

    /**
     * A {@link Compiler} implementing AAPT2.
     */
    static class Aapt2Compiler implements Compiler {

        /**
         * Compatibility resource stubs injected into every project build.
         *
         * Provides definitions for styles and attributes that live in libraries not
         * bundled with Sketchware AI (core-splashscreen, preference) and for
         * Material3 Expressive tokens not present in material-1.13.0.  The stubs
         * are included first in the link step so that any later library (e.g. a
         * local copy of material-1.14.0) can legitimately override them.
         *
         * IMPORTANT: every style here must carry an explicit parent="" (or reference
         * another style defined in THIS file) — never leave parent absent for a
         * dotted style name.  AAPT2's implicit-parent rule would otherwise look for
         * "A.B" as the parent of "A.B.C" and fail the link step for ALL projects,
         * even those that do not use Material3 Expressive.
         */
        private static final String COMPAT_RES_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "    <!-- androidx.core:core-splashscreen stubs -->\n"
            + "    <attr name=\"windowSplashScreenBackground\" format=\"reference|color\" />\n"
            + "    <attr name=\"windowSplashScreenAnimatedIcon\" format=\"reference\" />\n"
            + "    <attr name=\"windowSplashScreenIconMaskingColor\" format=\"reference|color\" />\n"
            + "    <attr name=\"windowSplashScreenAnimationDuration\" format=\"integer\" />\n"
            + "    <attr name=\"postSplashScreenTheme\" format=\"reference\" />\n"
            + "    <style name=\"Theme.SplashScreen\" parent=\"android:Theme\" />\n"
            + "    <!-- androidx.preference stubs -->\n"
            + "    <attr name=\"widgetLayout\" format=\"reference\" />\n"
            + "    <attr name=\"switchPreferenceCompatStyle\" format=\"reference\" />\n"
            + "    <style name=\"Preference\" parent=\"\" />\n"
            + "    <style name=\"Preference.SwitchPreferenceCompat\" parent=\"Preference\" />\n"
            + "    <style name=\"Preference.SwitchPreferenceCompat.Material\""
            + " parent=\"Preference.SwitchPreferenceCompat\" />\n"
            + "</resources>\n";

        private final boolean buildAppBundle;

        private final File aapt2;
        private final ProjectBuilder buildHelper;
        private final File compiledBuiltInLibraryResourcesDirectory;
        private File compiledCompatResourcesZip;
        private ProgressListener progressListener;

        public Aapt2Compiler(ProjectBuilder buildHelper, File aapt2, boolean buildAppBundle) {
            this.buildHelper = buildHelper;
            this.aapt2 = aapt2;
            this.buildAppBundle = buildAppBundle;
            compiledBuiltInLibraryResourcesDirectory = new File(SketchApplication.getContext().getCacheDir(), "compiledLibs");
        }

        @Override
        public void compile() throws zy, MissingFileException {
            String fingerprint = computeResourceFingerprint();
            if (isResourceCacheValid(fingerprint)) {
                LogUtil.d(TAG, "Skipping resource compilation — no resource inputs changed");
                return;
            }

            String outputPath = buildHelper.yq.binDirectoryPath + File.separator + "res";
            emptyOrCreateDirectory(outputPath);

            long savedTimeMillis = System.currentTimeMillis();
            if (progressListener != null) {
                progressListener.onProgressUpdate("Compiling resources with AAPT2...", 9);
            }
            compileCompatibilityResources(outputPath);
            compileBuiltInLibraryResources();
            LogUtil.d(TAG + ":c", "Compiling built-in library resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            savedTimeMillis = System.currentTimeMillis();
            compileLocalLibraryResources(outputPath);
            LogUtil.d(TAG + ":c", "Compiling local library resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            savedTimeMillis = System.currentTimeMillis();
            compileProjectResources(outputPath);
            LogUtil.d(TAG + ":c", "Compiling project generated resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            savedTimeMillis = System.currentTimeMillis();
            compileImportedResources(outputPath);
            LogUtil.d(TAG + ":c", "Compiling project imported resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");

            savedTimeMillis = System.currentTimeMillis();
            link();
            LogUtil.d(TAG + ":c", "Linking resources took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");

            saveResourceCache(fingerprint);
        }

        // ── Resource cache helpers ────────────────────────────────────────────────

        /**
         * Computes a stable SHA-256 fingerprint of every input that affects AAPT2
         * compilation and linking: project resource files, manifest, assets, local
         * library resource directories, and all project settings that influence the
         * link step (minSdk, targetSdk, versionCode/Name, package, library list, etc.).
         */
        private String computeResourceFingerprint() {
            StringBuilder fileFp = new StringBuilder();
            appendNodeFingerprint(new File(buildHelper.yq.resDirectoryPath), fileFp);
            appendNodeFingerprint(new File(buildHelper.yq.androidManifestPath), fileFp);
            appendNodeFingerprint(new File(buildHelper.fpu.getPathResource(buildHelper.yq.sc_id)), fileFp);
            appendNodeFingerprint(new File(buildHelper.yq.assetsPath), fileFp);
            appendNodeFingerprint(new File(buildHelper.fpu.getPathAssets(buildHelper.yq.sc_id)), fileFp);
            for (String localLibRes : buildHelper.mll.getResLocalLibrary()) {
                appendNodeFingerprint(new File(localLibRes), fileFp);
            }

            StringBuilder envFp = new StringBuilder();
            envFp.append("minSdk=").append(buildHelper.settings.getMinSdkVersion()).append('\n');
            envFp.append("targetSdk=").append(buildHelper.settings.getValue(
                    ProjectSettings.SETTING_TARGET_SDK_VERSION,
                    String.valueOf(VAR_DEFAULT_TARGET_SDK_VERSION))).append('\n');
            envFp.append("versionCode=").append(buildHelper.yq.versionCode).append('\n');
            envFp.append("versionName=").append(buildHelper.yq.versionName).append('\n');
            envFp.append("packageName=").append(buildHelper.yq.packageName).append('\n');
            envFp.append("appBundle=").append(buildAppBundle).append('\n');
            envFp.append("androidJar=").append(
                    buildHelper.build_settings.getValue(BuildSettings.SETTING_ANDROID_JAR_PATH, "")).append('\n');
            envFp.append("extraPackages=").append(buildHelper.getLibraryPackageNames()).append('\n');
            for (Jp lib : buildHelper.builtInLibraryManager.getLibraries()) {
                envFp.append("lib=").append(lib.getName()).append('\n');
            }
            try {
                Context ctx = SketchApplication.getContext();
                envFp.append("appUpdateTime=").append(
                        ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).lastUpdateTime).append('\n');
            } catch (PackageManager.NameNotFoundException ignored) {}

            return sha256(envFp.toString() + "---\n" + fileFp.toString());
        }

        /**
         * Above this size, a file is fingerprinted by (length, lastModified) metadata
         * instead of a full content hash. This is a deliberate performance/correctness
         * trade-off: the false-cache-hit failure mode this fix addresses is specifically
         * about small, frequently-regenerated text resources (styles.xml, colors.xml,
         * AndroidManifest.xml, etc.) where two different contents can plausibly share a
         * byte length. Large binary assets (images, fonts, bundled jars) are extremely
         * unlikely to collide in both length AND semantic content, and hashing every byte
         * of every imported asset on every build would meaningfully slow down incremental
         * builds for projects with large asset directories, which is the scenario this fix
         * must not regress.
         */
        private static final long CONTENT_HASH_SIZE_LIMIT_BYTES = 2L * 1024 * 1024; // 2 MB

        /**
         * Recursively fingerprints a file or directory for cache-validity comparison.
         * <p>
         * IMPORTANT: leaf files at or below {@link #CONTENT_HASH_SIZE_LIMIT_BYTES} are
         * fingerprinted by SHA-256 content hash, not by (length, lastModified) metadata
         * alone. Metadata-only fingerprinting was found to be unsound: two genuinely
         * different file contents can share the same byte length (not exotic for small
         * generated XML files with similar structure, e.g. styles.xml before/after a
         * Material3 toggle), and on real Android storage (FAT32-formatted external
         * storage has 2-second mtime resolution; some I/O paths truncate timestamps
         * further) two rapid successive writes can easily land in the same mtime bucket.
         * Either condition alone means the old scheme could report a stale resource
         * cache as valid, silently reusing outdated compiled AAPT2 output/resources.apk
         * after a real content change. Content hashing removes this failure mode
         * entirely for the files where it matters, independent of filesystem timestamp
         * behavior. Larger files fall back to metadata fingerprinting; see
         * {@link #CONTENT_HASH_SIZE_LIMIT_BYTES}.
         */
        private static void appendNodeFingerprint(File file, StringBuilder sb) {
            if (!file.exists()) {
                sb.append(file.getAbsolutePath()).append("|missing\n");
                return;
            }
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    Arrays.sort(children, (a, b) -> a.getAbsolutePath().compareTo(b.getAbsolutePath()));
                    for (File child : children) {
                        appendNodeFingerprint(child, sb);
                    }
                }
            } else if (file.length() <= CONTENT_HASH_SIZE_LIMIT_BYTES) {
                sb.append(file.getAbsolutePath())
                  .append('|').append(file.length())
                  .append('|').append(hashFileContent(file))
                  .append('\n');
            } else {
                // Large file: metadata-only fingerprint is an accepted trade-off here (see
                // CONTENT_HASH_SIZE_LIMIT_BYTES doc above).
                sb.append(file.getAbsolutePath())
                  .append('|').append(file.length())
                  .append('|').append(file.lastModified())
                  .append('\n');
            }
        }

        /**
         * Streams a file's bytes through SHA-256 in fixed-size chunks (never loading the
         * whole file into memory at once) and returns the hex digest. Falls back to a
         * length/lastModified pair (prefixed distinctly so it can never collide with a
         * real hex digest) if the file cannot be read, so a transient I/O error forces a
         * cache miss (safe: triggers a rebuild) rather than crashing the build.
         */
        private static String hashFileContent(File file) {
            try (FileInputStream in = new FileInputStream(file)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
                byte[] hash = digest.digest();
                StringBuilder hex = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (Exception e) {
                LogUtil.w(TAG, "Failed to hash file for resource fingerprint, forcing cache miss: "
                        + file.getAbsolutePath() + " (" + e.getMessage() + ")");
                return "unreadable|" + file.length() + "|" + file.lastModified();
            }
        }

        private boolean isResourceCacheValid(String fingerprint) {
            File cacheFile = new File(buildHelper.yq.binDirectoryPath, ".res_cache");
            File resourcesApk = new File(buildHelper.yq.resourcesApkPath);
            File rJavaDir = new File(buildHelper.yq.rJavaDirectoryPath);

            if (!resourcesApk.exists() || resourcesApk.length() == 0) return false;
            String[] rJavaFiles = rJavaDir.list();
            if (rJavaFiles == null || rJavaFiles.length == 0) return false;
            if (!cacheFile.exists()) return false;

            String saved = FileUtil.readFile(cacheFile.getAbsolutePath());
            return fingerprint.equals(saved);
        }

        private void saveResourceCache(String fingerprint) {
            File cacheFile = new File(buildHelper.yq.binDirectoryPath, ".res_cache");
            FileUtil.writeFile(cacheFile.getAbsolutePath(), fingerprint);
        }

        private static String sha256(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(bytes.length * 2);
                for (byte b : bytes) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (Exception e) {
                return input;
            }
        }

        /**
         * Links the project's resources using AAPT2.
         *
         * @throws zy Thrown to be caught by DesignActivity to show an error Snackbar.
         */
        public void link() throws zy, MissingFileException {
            String resourcesPath = buildHelper.yq.binDirectoryPath + File.separator + "res";
            if (progressListener != null)
                progressListener.onProgressUpdate("Linking resources with AAPT2...", 10);

            ArrayList<String> args = new ArrayList<>();
            args.add(aapt2.getAbsolutePath());
            args.add("link");
            if (buildAppBundle) {
                args.add("--proto-format");
            }
            args.add("--allow-reserved-package-id");
            args.add("--auto-add-overlay");
            args.add("--no-version-vectors");
            args.add("--no-version-transitions");

            args.add("--min-sdk-version");
            args.add(String.valueOf(buildHelper.settings.getMinSdkVersion()));
            args.add("--target-sdk-version");
            args.add(buildHelper.settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(VAR_DEFAULT_TARGET_SDK_VERSION)));

            args.add("--version-code");
            String versionCode = buildHelper.yq.versionCode;
            args.add((versionCode == null || versionCode.isEmpty()) ? "1" : versionCode);
            args.add("--version-name");
            String versionName = buildHelper.yq.versionName;
            args.add((versionName == null || versionName.isEmpty()) ? "1.0" : versionName);

            args.add("-I");
            String customAndroidSdk = buildHelper.build_settings.getValue(BuildSettings.SETTING_ANDROID_JAR_PATH, "");
            if (customAndroidSdk.isEmpty()) {
                args.add(buildHelper.androidJarPath);
            } else {
                linkingAssertFileExists(customAndroidSdk);
                args.add(customAndroidSdk);
            }

            /* Add assets imported by vanilla method */
            linkingAssertDirectoryExists(buildHelper.yq.assetsPath);
            args.add("-A");
            args.add(buildHelper.yq.assetsPath);

            /* Add imported assets */
            String importedAssetsPath = buildHelper.fpu.getPathAssets(buildHelper.yq.sc_id);
            if (FileUtil.isExistFile(importedAssetsPath)) {
                args.add("-A");
                args.add(importedAssetsPath);
            }

            /* Add built-in libraries' assets */
            for (Jp library : buildHelper.builtInLibraryManager.getLibraries()) {
                if (library.hasAssets()) {
                    String assetsPath = BuiltInLibraries.getLibraryAssetsPath(library.getName());

                    linkingAssertDirectoryExists(assetsPath);
                    args.add("-A");
                    args.add(assetsPath);
                }
            }

            /* Add local libraries' assets */
            for (String localLibraryAssetsDirectory : new ManageLocalLibrary(buildHelper.yq.sc_id).getAssets()) {
                linkingAssertDirectoryExists(localLibraryAssetsDirectory);
                args.add("-A");
                args.add(localLibraryAssetsDirectory);
            }

            /* Include compat stubs first so built-in libraries can override them */
            if (compiledCompatResourcesZip != null && compiledCompatResourcesZip.exists()) {
                args.add("-R");
                args.add(compiledCompatResourcesZip.getAbsolutePath());
            }

            /* Include compiled built-in library resources */
            for (Jp library : buildHelper.builtInLibraryManager.getLibraries()) {
                if (library.hasResources()) {
                    File compiledLibResources = new File(compiledBuiltInLibraryResourcesDirectory, library.getName() + ".zip");
                    // Unlike local libraries' resources (listed straight from disk) and the
                    // compat zip (explicitly null-checked above), this file lives under
                    // getCacheDir()/compiledLibs — a directory the OS may clear at any time,
                    // independent of and possibly *after* compileBuiltInLibraryResources()
                    // verified it existed. AAPT2 does not hard-fail on a missing -R argument;
                    // it silently drops that resource set and fails later at link time with a
                    // confusing "resource not found" for styles/attrs that library defines.
                    // If it's missing here, try to recompile it on the spot rather than
                    // letting AAPT2 fail vaguely downstream — this is the same compile
                    // logic compileBuiltInLibraryResources() already ran earlier in this
                    // same build, just re-triggered for the one library the cache lied
                    // about. Only throw if that recompile also fails to produce the file.
                    if (!compiledLibResources.exists()) {
                        LogUtil.w(TAG + ":l", "Missing compiled resources for built-in library "
                                + library.getName() + " at link time; recompiling now");
                        recompileBuiltInLibraryResources(library);
                        linkingAssertFileExists(compiledLibResources.getAbsolutePath());
                    }
                    args.add("-R");
                    args.add(compiledLibResources.getAbsolutePath());
                }
            }

            /* Include compiled local libraries' resources */
            File[] filesInCompiledResourcesPath = new File(resourcesPath).listFiles();
            if (filesInCompiledResourcesPath != null) {
                for (File file : filesInCompiledResourcesPath) {
                    if (file.isFile()) {
                        if (!file.getName().equals("project.zip") && !file.getName().equals("project-imported.zip")) {
                            args.add("-R");
                            args.add(file.getAbsolutePath());
                        }
                    }
                }
            }

            /* Include compiled project resources */
            File projectArchive = new File(resourcesPath, "project.zip");
            if (projectArchive.exists()) {
                args.add("-R");
                args.add(projectArchive.getAbsolutePath());
            }

            /* Include compiled imported project resources */
            File projectImportedArchive = new File(resourcesPath, "project-imported.zip");
            if (projectImportedArchive.exists()) {
                args.add("-R");
                args.add(projectImportedArchive.getAbsolutePath());
            }

            /* Add R.java */
            linkingAssertDirectoryExists(buildHelper.yq.rJavaDirectoryPath);
            args.add("--java");
            args.add(buildHelper.yq.rJavaDirectoryPath);

            /* Output AAPT2's generated ProGuard rules to a.a.a.yq.aapt_rules */
            args.add("--proguard");
            args.add(buildHelper.yq.proguardAaptRules);

            /* Add AndroidManifest.xml */
            linkingAssertFileExists(buildHelper.yq.androidManifestPath);
            args.add("--manifest");
            args.add(buildHelper.yq.androidManifestPath);

            /* Use the generated R.java for used libraries */
            String extraPackages = buildHelper.getLibraryPackageNames();
            if (!extraPackages.isEmpty()) {
                args.add("--extra-packages");
                args.add(extraPackages);
            }

            /* Output the APK only with resources to a.a.a.yq.C */
            args.add("-o");
            args.add(buildHelper.yq.resourcesApkPath);

            LogUtil.d(TAG + ":l", args.toString());
            BinaryExecutor executor = new BinaryExecutor();
            executor.setCommands(args);
            if (!executor.execute().isEmpty()) {
                LogUtil.e(TAG + ":l", executor.getLog());
                throw new zy(executor.getLog());
            }
        }

        private void compileProjectResources(String outputPath) throws zy, MissingFileException {
            compilingAssertDirectoryExists(buildHelper.yq.resDirectoryPath);

            ArrayList<String> commands = new ArrayList<>();
            commands.add(aapt2.getAbsolutePath());
            commands.add("compile");
            commands.add("--no-crunch"); // skip PNG re-encoding for faster debug builds
            commands.add("--dir");
            commands.add(buildHelper.yq.resDirectoryPath);
            commands.add("-o");
            commands.add(outputPath + File.separator + "project.zip");
            LogUtil.d(TAG + ":cPR", "Now executing: " + commands);
            BinaryExecutor executor = new BinaryExecutor();
            executor.setCommands(commands);
            if (!executor.execute().isEmpty()) {
                LogUtil.e(TAG, executor.getLog());
                throw new zy(executor.getLog());
            }
        }

        private void emptyOrCreateDirectory(String path) {
            if (FileUtil.isExistFile(path)) {
                FileUtil.deleteFile(path);
            }
            FileUtil.makeDir(path);
        }

        private void compileLocalLibraryResources(String outputPath) throws zy, MissingFileException {
            int localLibrariesCount = buildHelper.mll.getResLocalLibrary().size();
            LogUtil.d(TAG + ":cLLR", "About to compile " + localLibrariesCount
                    + " local " + (localLibrariesCount == 1 ? "library" : "libraries"));
            for (String localLibraryResDirectory : buildHelper.mll.getResLocalLibrary()) {
                File localLibraryDirectory = new File(localLibraryResDirectory).getParentFile();
                if (localLibraryDirectory != null) {
                    compilingAssertDirectoryExists(localLibraryResDirectory);

                    // Sanitize values/ and xml/ subdirectories before feeding to aapt2.
                    // aapt2 rejects "50%" (raw dimension) and "auto" (unknown enum) that some
                    // Material library alpha releases embed. We strip those attribute lines so
                    // linking does not fail with "expected dimension / expected enum" errors.
                    sanitizeAapt2IncompatibleValues(localLibraryResDirectory);

                    ArrayList<String> commands = new ArrayList<>();
                    commands.add(aapt2.getAbsolutePath());
                    commands.add("compile");
                    commands.add("--dir");
                    commands.add(localLibraryResDirectory);
                    commands.add("-o");
                    commands.add(outputPath + File.separator + localLibraryDirectory.getName() + ".zip");

                    LogUtil.d(TAG + ":cLLR", "Now executing: " + commands);
                    BinaryExecutor executor = new BinaryExecutor();
                    executor.setCommands(commands);
                    if (!executor.execute().isEmpty()) {
                        LogUtil.e(TAG, executor.getLog());
                        throw new zy(executor.getLog());
                    }
                }
            }
        }

        /**
         * Removes attribute lines that aapt2 cannot parse from all XML files inside
         * the {@code values/} and {@code xml/} subdirectories of a library res directory.
         *
         * <p>Known incompatible patterns (as of Material 1.14.0-alpha01):
         * <ul>
         *   <li>Dimension attributes set to the raw string {@code "50%"} — aapt2 requires
         *       a unit suffix (dp/px/sp/…) and does not accept percentage literals.</li>
         *   <li>Enum-typed attributes set to the raw string {@code "auto"} — aapt2 rejects
         *       values that are not declared in the enum list for that attr.</li>
         * </ul>
         *
         * <p>Lines are removed rather than replaced because we cannot safely substitute a
         * value (we do not know the semantic intent), and a missing attribute is harmless
         * at runtime — the view falls back to its declared default.
         */
        private void sanitizeAapt2IncompatibleValues(String resDirectory) {
            String[] subDirs = {"values", "xml"};
            for (String sub : subDirs) {
                File dir = new File(resDirectory, sub);
                if (!dir.exists() || !dir.isDirectory()) continue;
                File[] xmlFiles = dir.listFiles(f -> f.getName().endsWith(".xml"));
                if (xmlFiles == null) continue;
                for (File xmlFile : xmlFiles) {
                    try {
                        sanitizeXmlFile(xmlFile);
                    } catch (IOException e) {
                        LogUtil.w(TAG + ":sAIV", "Could not sanitize " + xmlFile.getPath() + ": " + e.getMessage());
                    }
                }
            }
        }

        private void sanitizeXmlFile(File xmlFile) throws IOException {
            File tmpFile = new File(xmlFile.getParent(), xmlFile.getName() + ".tmp");
            boolean modified = false;
            try (BufferedReader reader = new BufferedReader(new FileReader(xmlFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(tmpFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Drop lines whose attribute value is the literal string "50%" or "auto".
                    // These patterns match the exact aapt2 error messages reported:
                    //   error: expected dimension but got (raw string) 50%
                    //   error: expected enum but got (raw string) auto
                    boolean isIncompatible =
                            line.contains(">50%<") ||
                            line.contains("\"50%\"") ||
                            line.contains(">auto<") ||
                            line.contains("\"auto\"");
                    if (isIncompatible) {
                        LogUtil.w(TAG + ":sXF",
                                "Stripped aapt2-incompatible line from " + xmlFile.getName() + ": " + line.trim());
                        modified = true;
                    } else {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
            if (modified) {
                if (!xmlFile.delete() || !tmpFile.renameTo(xmlFile)) {
                    LogUtil.e(TAG + ":sXF", "Failed to replace sanitized file: " + xmlFile.getPath());
                }
            } else {
                tmpFile.delete();
            }
        }

        private void compileCompatibilityResources(String outputPath) {
            File compatDir = new File(SketchApplication.getContext().getCacheDir(), "sketchware_compat_res");
            File compatValuesDir = new File(compatDir, "values");
            compatValuesDir.mkdirs();
            compiledCompatResourcesZip = new File(compiledBuiltInLibraryResourcesDirectory, "sketchware-compat.zip");

            // Cache key is a content hash of the compat XML constant itself, rather than
            // the app's PackageInfo.lastUpdateTime. lastUpdateTime only reflects when the
            // APK was installed/updated, not whether COMPAT_RES_XML actually changed, so
            // it could both miss real changes (same install, code hot-swapped some other
            // way) and force unnecessary recompiles (any app update, even unrelated ones).
            File compatCacheMarker = new File(compiledBuiltInLibraryResourcesDirectory, ".compat_res_marker");
            String compatFingerprint = sha256(COMPAT_RES_XML);

            boolean needsRecompile = !compiledCompatResourcesZip.exists();
            if (!needsRecompile) {
                if (!compatCacheMarker.exists()) {
                    needsRecompile = true;
                } else {
                    String savedFingerprint = FileUtil.readFile(compatCacheMarker.getAbsolutePath());
                    needsRecompile = !compatFingerprint.equals(savedFingerprint);
                }
            }

            if (!needsRecompile) {
                LogUtil.d(TAG + ":cCR", "Skipped compat resource recompilation");
                return;
            }

            File compatXml = new File(compatValuesDir, "sketchware_compat.xml");
            try (java.io.FileWriter writer = new java.io.FileWriter(compatXml)) {
                writer.write(COMPAT_RES_XML);
            } catch (IOException e) {
                LogUtil.e(TAG + ":cCR", "Failed to write compat XML: " + e.getMessage());
                compiledCompatResourcesZip = null;
                compatCacheMarker.delete();
                return;
            }

            ArrayList<String> commands = new ArrayList<>();
            commands.add(aapt2.getAbsolutePath());
            commands.add("compile");
            commands.add("--dir");
            commands.add(compatDir.getAbsolutePath());
            commands.add("-o");
            commands.add(compiledCompatResourcesZip.getAbsolutePath());

            LogUtil.d(TAG + ":cCR", "Compiling compat resources: " + commands);
            BinaryExecutor executor = new BinaryExecutor();
            executor.setCommands(commands);
            if (!executor.execute().isEmpty()) {
                LogUtil.w(TAG + ":cCR", "Compat resource compilation warnings: " + executor.getLog());
            }
            FileUtil.writeFile(compatCacheMarker.getAbsolutePath(), compatFingerprint);
        }

        private void compileBuiltInLibraryResources() throws zy, MissingFileException {
            compiledBuiltInLibraryResourcesDirectory.mkdirs();
            for (Jp builtInLibrary : buildHelper.builtInLibraryManager.getLibraries()) {
                if (builtInLibrary.hasResources()) {
                    compileSingleBuiltInLibraryResources(builtInLibrary, false);
                }
            }
        }

        /**
         * Forces a recompile of one built-in library's resources, bypassing the normal
         * cache-validity check. Used by {@link #link()} as a last resort when a
         * previously-verified compiled zip has gone missing by link time (e.g. evicted
         * from the cache directory between compile() and link() within the same build) —
         * see the call site in link() for why this can happen despite
         * compileBuiltInLibraryResources() having already run successfully this build.
         */
        private void recompileBuiltInLibraryResources(Jp builtInLibrary) throws zy, MissingFileException {
            compiledBuiltInLibraryResourcesDirectory.mkdirs();
            compileSingleBuiltInLibraryResources(builtInLibrary, true);
        }

        private void compileSingleBuiltInLibraryResources(Jp builtInLibrary, boolean forceRecompile) throws zy, MissingFileException {
            File cachedCompiledResources = new File(compiledBuiltInLibraryResourcesDirectory, builtInLibrary.getName() + ".zip");
            String libraryResources = BuiltInLibraries.getLibraryResourcesPath(builtInLibrary.getName());

            compilingAssertDirectoryExists(libraryResources);

            File libResMarker = new File(compiledBuiltInLibraryResourcesDirectory, builtInLibrary.getName() + ".marker");
            if (forceRecompile || isBuiltInLibraryRecompilingNeeded(cachedCompiledResources, libResMarker, libraryResources)) {
                // Sanitize aapt2-incompatible values (e.g. "50%", "auto") from
                // built-in library resources before compilation, exactly as we do
                // for local libraries. Needed for material-1.13.0+ alpha releases.
                sanitizeAapt2IncompatibleValues(libraryResources);

                ArrayList<String> commands = new ArrayList<>();
                commands.add(aapt2.getAbsolutePath());
                commands.add("compile");
                commands.add("--dir");
                commands.add(libraryResources);
                commands.add("-o");
                commands.add(cachedCompiledResources.getAbsolutePath());

                LogUtil.d(TAG + ":cBILR", "Now executing: " + commands);
                BinaryExecutor executor = new BinaryExecutor();
                executor.setCommands(commands);
                if (!executor.execute().isEmpty()) {
                    LogUtil.e(TAG + ":cBILR", executor.getLog());
                    libResMarker.delete();
                    throw new zy(executor.getLog());
                }
                StringBuilder libFp = new StringBuilder();
                appendNodeFingerprint(new File(libraryResources), libFp);
                FileUtil.writeFile(libResMarker.getAbsolutePath(), sha256(libFp.toString()));
            } else {
                LogUtil.d(TAG + ":cBILR", "Skipped resource recompilation for built-in library " + builtInLibrary.getName());
            }
        }

        /**
         * Determines whether a built-in library's resources need recompiling, based on a
         * content fingerprint of its resource directory rather than the app's
         * PackageInfo.lastUpdateTime. lastUpdateTime only tracks when the host APK was
         * installed/updated — it doesn't reflect whether this specific library's bundled
         * resources actually changed, so it can both miss real changes and force
         * unnecessary recompiles on unrelated app updates.
         */
        private boolean isBuiltInLibraryRecompilingNeeded(File cachedCompiledResources, File marker, String libraryResources) {
            if (!cachedCompiledResources.exists()) {
                LogUtil.d(TAG + ":iBILRN", "File " + cachedCompiledResources.getAbsolutePath()
                        + " doesn't exist, forcing compilation");
                return true;
            }
            if (!marker.exists()) {
                return true;
            }
            StringBuilder libFp = new StringBuilder();
            appendNodeFingerprint(new File(libraryResources), libFp);
            String currentFingerprint = sha256(libFp.toString());
            String savedFingerprint = FileUtil.readFile(marker.getAbsolutePath());
            return !currentFingerprint.equals(savedFingerprint);
        }

        private void compileImportedResources(String outputPath) throws zy {
            if (FileUtil.isExistFile(buildHelper.fpu.getPathResource(buildHelper.yq.sc_id))
                    && new File(buildHelper.fpu.getPathResource(buildHelper.yq.sc_id)).length() != 0) {
                ArrayList<String> commands = new ArrayList<>();
                commands.add(aapt2.getAbsolutePath());
                commands.add("compile");
                commands.add("--no-crunch"); // skip PNG re-encoding for faster debug builds
                commands.add("--dir");
                commands.add(buildHelper.fpu.getPathResource(buildHelper.yq.sc_id));
                commands.add("-o");
                commands.add(outputPath + File.separator + "project-imported.zip");
                LogUtil.d(TAG + ":cIR", "Now executing: " + commands);
                BinaryExecutor executor = new BinaryExecutor();
                executor.setCommands(commands);
                if (!executor.execute().isEmpty()) {
                    LogUtil.e(TAG, executor.getLog());
                    throw new zy(executor.getLog());
                }
            }
        }

        private void compilingAssertDirectoryExists(String directoryPath) throws MissingFileException {
            File directory = new File(directoryPath);
            if (!directory.exists()) {
                throw new MissingFileException(directory, MissingFileException.STEP_RESOURCE_COMPILING, true);
            }
        }

        public void linkingAssertFileExists(String filePath) throws MissingFileException {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new MissingFileException(file, MissingFileException.STEP_RESOURCE_LINKING, false);
            }
        }

        public void linkingAssertDirectoryExists(String filePath) throws MissingFileException {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new MissingFileException(file, MissingFileException.STEP_RESOURCE_LINKING, true);
            }
        }

        @Override
        public void setProgressListener(ProgressListener listener) {
            progressListener = listener;
        }
    }
}
