package a.a.a;

import static android.system.OsConstants.S_IRUSR;
import static android.system.OsConstants.S_IWUSR;
import static android.system.OsConstants.S_IXUSR;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.system.Os;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import com.android.sdklib.build.ApkBuilder;
import com.android.sdklib.build.ApkCreationException;
import com.android.sdklib.build.DuplicateFileException;
import com.android.sdklib.build.SealedApkException;
import com.github.megatronking.stringfog.plugin.StringFogClassInjector;
import com.github.megatronking.stringfog.plugin.StringFogMappingPrinter;
import com.iyxan23.zipalignjava.InvalidZipException;
import com.iyxan23.zipalignjava.ZipAlign;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import pro.sketchware.compiler.LegacyJavaSourceNormalizer;
import pro.sketchware.compiler.ECJCompilerClient;
import pro.sketchware.compiler.JavaCompileGraph;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.agus.jcoderz.dex.Dex;
import mod.agus.jcoderz.dex.FieldId;
import mod.agus.jcoderz.dex.MethodId;
import mod.agus.jcoderz.dex.ProtoId;
import mod.agus.jcoderz.dx.command.dexer.DxContext;
import mod.agus.jcoderz.dx.command.dexer.Main;
import mod.agus.jcoderz.dx.merge.CollisionPolicy;
import mod.agus.jcoderz.dx.merge.DexMerger;
import mod.agus.jcoderz.editor.library.ExtLibSelected;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.util.SystemLogPrinter;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.build.compiler.dex.DexCompiler;
import mod.jbk.build.compiler.resource.ResourceCompiler;
import mod.jbk.util.LogUtil;
import mod.jbk.util.TestkeySignBridge;
import mod.pranav.build.JarBuilder;
import mod.pranav.build.R8Compiler;
import mod.pranav.viewbinding.ViewBindingBuilder;
import pro.sketchware.settings.LibraryExtrasSettings;
import pro.sketchware.settings.ProjectSettingsStore;
import pro.sketchware.SketchApplication;
import pro.sketchware.util.library.BuiltInLibraryManager;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import proguard.Configuration;
import proguard.ConfigurationParser;
import proguard.ParseException;
import proguard.ProGuard;

public class ProjectBuilder {
    public static final String TAG = "AppBuilder";

    private final File aapt2Binary;
    private final Context context;
    public BuildSettings build_settings;
    public yq yq;
    public FilePathUtil fpu;
    public ManageLocalLibrary mll;
    public BuiltInLibraryManager builtInLibraryManager;
    public String androidJarPath;
    public ProguardHandler proguard;
    public ProjectSettings settings;
    private BuildProgressReceiver progressReceiver;
    private boolean buildAppBundle = false;
    private boolean releaseBuildMode = false;
    // Kept for buildApk() compatibility only. As of the unconditional-merge fix,
    // getDexFilesReady() always merges via dexLibraries(...) and never populates
    // this list; it is left in place (always empty) rather than restructuring
    // buildApk()'s packaging branches, which is out of scope for this fix.
    private ArrayList<File> dexesToAddButNotMerge = new ArrayList<>();

    /**
     * Timestamp keeping track of when compiling the project's resources started, needed for stats of how long compiling took.
     */
    private long timestampResourceCompilationStarted;

    private final pro.sketchware.compiler.BuildProfiler buildProfiler =
            new pro.sketchware.compiler.BuildProfiler();

    public ProjectBuilder(Context context, yq yqVar) {
        /* Detect some bad behaviour of the app */
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        );

        SystemLogPrinter.start();

        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            LogUtil.d(TAG, "Running Sketchware Pro " + info.versionName + " (" + info.versionCode + ")");

            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);

            long fileSizeInBytes = new File(applicationInfo.sourceDir).length();
            LogUtil.d(TAG, "base.apk's size is " + Formatter.formatFileSize(context, fileSizeInBytes) + " (" + fileSizeInBytes + " B)");
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(TAG, "Somehow failed to get package info about us!", e);
        }

        aapt2Binary = new File(context.getCacheDir(), "aapt2");
        build_settings = new BuildSettings(yqVar.sc_id);
        this.context = context;
        yq = yqVar;
        fpu = new FilePathUtil();
        mll = new ManageLocalLibrary(yqVar.sc_id);
        builtInLibraryManager = new BuiltInLibraryManager(yqVar.sc_id);
        File defaultAndroidJar = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android.jar");
        androidJarPath = build_settings.getValue(BuildSettings.SETTING_ANDROID_JAR_PATH, defaultAndroidJar.getAbsolutePath());
        proguard = new ProguardHandler(yqVar.sc_id);
        settings = new ProjectSettings(yqVar.sc_id);
    }

    public ProjectBuilder(BuildProgressReceiver buildAsyncTask, Context context, yq yqVar) {
        this(context, yqVar);
        progressReceiver = buildAsyncTask;
    }

    /**
     * Checks if a file on local storage differs from a file in assets, and if so,
     * replaces the file on local storage with the one in assets.
     * <p/>
     * IMPORTANT: this compares file SIZE and a CRC32 checksum of the asset's content,
     * not size alone. Size-only comparison was found to be unsound: if the extracted
     * archive on disk (e.g. libs.zip, containing all built-in libraries including
     * shizuku-shared) happened to match the new asset's byte length — for example
     * after a library was swapped for a different one of similar total size, or if a
     * prior extraction was interrupted partway and left a same-sized but incomplete
     * file — this method would report "unchanged" and skip re-extraction entirely,
     * silently leaving stale or incomplete library data on disk. This was observed to
     * cause D8 to fail with NoSuchFileException for individual library JARs (e.g.
     * shizuku-shared-13.1.5/classes.jar) that were declared as dependencies but never
     * actually extracted to disk. CRC32 is used instead of a full hash (e.g. SHA-256)
     * because these archives can be tens of megabytes (libs.zip specifically is around
     * 27 MB) and this check runs on every build; CRC32 is fast, catches the failure
     * mode above, and only requires a single streaming pass, same as the size check.
     *
     * @param fileInAssets The file in assets relative to assets/ in the APK
     * @param targetFile   The file on local storage
     * @return If the file in assets has been extracted
     */
    public static boolean hasFileChanged(String fileInAssets, String targetFile) {
        File compareToFile = new File(targetFile);
        oB fileUtil = new oB();
        long lengthOfFileInAssets = fileUtil.a(SketchApplication.getContext(), fileInAssets);
        long length = compareToFile.exists() ? compareToFile.length() : 0;

        boolean unchanged = false;
        if (lengthOfFileInAssets == length && length > 0) {
            try {
                long assetCrc = computeAssetCrc32(fileInAssets);
                long diskCrc = computeFileCrc32(compareToFile);
                unchanged = (assetCrc == diskCrc);
            } catch (IOException e) {
                LogUtil.w(TAG, "hasFileChanged: CRC32 check failed for " + fileInAssets
                        + ", forcing re-extraction to be safe: " + e.getMessage());
                unchanged = false;
            }
        }

        if (unchanged) {
            return false;
        }

        /* Delete the file */
        fileUtil.a(compareToFile);
        /* Copy the file from assets to local storage */
        fileUtil.a(SketchApplication.getContext(), fileInAssets, targetFile);
        return true;
    }

    private static long computeAssetCrc32(String assetPath) throws IOException {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        try (java.io.InputStream in = SketchApplication.getContext().getAssets().open(assetPath)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
            }
        }
        return crc.getValue();
    }

    private static long computeFileCrc32(File file) throws IOException {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
            }
        }
        return crc.getValue();
    }

    /**
     * Compile resources and log time needed.
     *
     * @throws Exception Thrown when anything goes wrong while compiling resources
     */
    public void compileResources() throws Exception {
        buildProfiler.start("Resources");
        timestampResourceCompilationStarted = System.currentTimeMillis();
        // material-1.14.0-beta01 ships the full Widget/Theme/ThemeOverlay.
        // Material3Expressive.* style family natively, so Widget.Material3Expressive.*
        // references no longer need to be rewritten or backed by a separate
        // compat library before AAPT2 sees them.
        ResourceCompiler compiler = new ResourceCompiler(
                this,
                aapt2Binary,
                buildAppBundle,
                progressReceiver);
        compiler.compile();
        buildProfiler.stop("Resources");
        LogUtil.d(TAG, "Compiling resources took " + (System.currentTimeMillis() - timestampResourceCompilationStarted) + " ms");
    }

    public void generateViewBinding() throws IOException, SAXException {
        if (settings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(ProjectSettings.SETTING_GENERIC_VALUE_FALSE)) {
            return;
        }

        pruneGeneratedSourceConflicts();

        File outputDirectory = new File(yq.javaFilesPath + File.separator + yq.packageName.replace(".", File.separator) + File.separator + "databinding");
        if (outputDirectory.exists()) {
            FileUtil.deleteFile(outputDirectory.getAbsolutePath());
        }
        outputDirectory.mkdirs();

        List<File> layouts = FileUtil.listFiles(yq.layoutFilesPath, "xml").stream()
                .map(File::new)
                .collect(Collectors.toList());

        ViewBindingBuilder builder = new ViewBindingBuilder(layouts, outputDirectory, yq.packageName + ".databinding");
        builder.generateBindings();
    }

    private void pruneGeneratedSourceConflicts() {
        File customSourceRoot = new File(fpu.getPathJava(yq.sc_id));
        if (!customSourceRoot.exists()) {
            return;
        }
        pruneGeneratedSourceConflicts(customSourceRoot);
    }

    private void pruneGeneratedSourceConflicts(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                pruneGeneratedSourceConflicts(child);
            }
            return;
        }

        String name = file.getName();
        if (!name.endsWith(".java")) {
            return;
        }

        boolean suspiciousGeneratedFile =
                name.endsWith("Binding.java")
                        || name.equals("BuildConfig.java")
                        || name.equals("R.java")
                        || name.startsWith("R$")
                        || name.equals("BR.java")
                        || name.startsWith("DataBinderMapper");

        if (!suspiciousGeneratedFile) {
            return;
        }

        String content = FileUtil.readFile(file.getAbsolutePath());
        if (looksLikeGeneratedSource(content)) {
            FileUtil.deleteFile(file.getAbsolutePath());
        }
    }

    private boolean looksLikeGeneratedSource(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        return content.contains("Generated file. Do not modify.")
                || (content.contains(".databinding") && content.contains("inflate(LayoutInflater"))
                || content.contains("public final class BuildConfig")
                || content.contains("public final class R")
                || content.contains("public class R")
                || content.contains("class BR")
                || content.contains("DataBinderMapper");
    }

    public boolean isModernJavaEnabled() {
        return !build_settings.getValue(
                BuildSettings.SETTING_JAVA_VERSION,
                BuildSettings.SETTING_JAVA_VERSION_1_8
        ).equals(BuildSettings.SETTING_JAVA_VERSION_1_7);
    }

    public void setReleaseBuildMode(boolean release) {
        this.releaseBuildMode = release;
    }

    public boolean isReleaseBuildMode() {
        return releaseBuildMode;
    }

    public boolean isD8Enabled() {
        // D8/R8 (com.android.tools:r8:8.11.18) uses Java 9+ APIs (InputStream.readAllBytes etc.)
        // in its own implementation bytecode. AGP's coreLibraryDesugaring doesn't reliably
        // desugar R8 itself (circular toolchain dependency), so calling D8.run() on API < 33
        // throws NoSuchMethodError at runtime. Fall back to the bundled DX dexer on older
        // devices — DX is pure Java 7 bytecode and runs safely on any API level.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        if (isModernJavaEnabled()) {
            return true;
        }
        String dexer = build_settings.getValue(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_DX);
        return dexer.equals(BuildSettings.SETTING_DEXER_D8) || dexer.equals(BuildSettings.SETTING_DEXER_R8);
    }

    public boolean isR8DexerEnabled() {
        // See isD8Enabled() — same limitation applies to R8.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        return build_settings.getValue(
                BuildSettings.SETTING_DEXER,
                BuildSettings.SETTING_DEXER_DX
        ).equals(BuildSettings.SETTING_DEXER_R8);
    }

    public boolean isParallelEcjEnabled() {
        return build_settings.getValue(
                BuildSettings.SETTING_PARALLEL_ECJ,
                ProjectSettings.SETTING_GENERIC_VALUE_TRUE
        ).equals(ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
    }

    public String getDxRunningText() {
        if (isR8DexerEnabled()) return "R8 is running...";
        return (isD8Enabled() ? "D8" : "Dx") + " is running...";
    }

    /**
     * Compile Java classes into DEX file(s)
     *
     * @throws Exception Thrown if the compiler had any problems compiling
     */
    public void createDexFilesFromClasses() throws Exception {
        FileUtil.makeDir(yq.binDirectoryPath + File.separator + "dex");
        if (proguard.isShrinkingEnabled() && proguard.isR8Enabled()) return;

        if (isR8DexerEnabled()) {
            buildProfiler.start("Dex");
            long savedTimeMillis = System.currentTimeMillis();
            try {
                runR8Dexer();
                buildProfiler.stop("Dex");
                LogUtil.d(TAG, "R8 (dexer mode) took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            } catch (Exception e) {
                LogUtil.e(TAG, "R8 (dexer mode) failed to process .class files", e);
                throw e;
            }
        } else if (isD8Enabled()) {
            buildProfiler.start("Dex");
            long savedTimeMillis = System.currentTimeMillis();
            try {
                DexCompiler.compileDexFiles(this);
                buildProfiler.stop("Dex");
                LogUtil.d(TAG, "D8 took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            } catch (Exception e) {
                LogUtil.e(TAG, "D8 failed to process .class files", e);
                throw e;
            }
        } else {
            buildProfiler.start("Dex");
            long savedTimeMillis = System.currentTimeMillis();
            List<String> args = Arrays.asList(
                    "--debug",
                    "--verbose",
                    "--multi-dex",
                    "--output=" + yq.binDirectoryPath + File.separator + "dex",
                    proguard.isShrinkingEnabled() ? yq.proguardClassesPath : yq.compiledClassesPath
            );

            try {
                LogUtil.d(TAG, "Running Dx with these arguments: " + args);

                Main.clearInternTables();
                Main.Arguments arguments = new Main.Arguments();
                Method parseMethod = Main.Arguments.class.getDeclaredMethod("parse", String[].class);
                parseMethod.setAccessible(true);
                parseMethod.invoke(arguments, (Object) args.toArray(new String[0]));

                Main.run(arguments);
                buildProfiler.stop("Dex");
                LogUtil.d(TAG, "Dx took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            } catch (Exception e) {
                LogUtil.e(TAG, "Dx failed to process .class files", e);
                throw e;
            }
        }
    }

    public String getClasspath() {
        StringBuilder classpath = new StringBuilder();

        appendProjectClassOutput(classpath, yq.compiledJavaClassesPath);
        appendProjectClassOutput(classpath, yq.compiledKotlinClassesPath);

        if (classpath.length() > 0) {
            classpath.append(':');
        }

        /* Add android.jar */
        classpath.append(androidJarPath);

        /* Add HTTP legacy files if wanted */
        if (!build_settings.getValue(BuildSettings.SETTING_NO_HTTP_LEGACY,
                BuildSettings.SETTING_GENERIC_VALUE_FALSE).equals(BuildSettings.SETTING_GENERIC_VALUE_TRUE)) {
            classpath.append(":").append(BuiltInLibraries.getLibraryClassesJarPathString(BuiltInLibraries.HTTP_LEGACY_ANDROID));
        }

        /* Include MultiDex library if needed */
        if (settings.getMinSdkVersion() < 21) {
            classpath.append(":").append(BuiltInLibraries.getLibraryClassesJarPathString(BuiltInLibraries.ANDROIDX_MULTIDEX));
        }

        /*
         * Add lambda helper classes
         * Since all versions above java 7 supports lambdas, this should work
         */
        if (!build_settings.getValue(BuildSettings.SETTING_JAVA_VERSION,
                        BuildSettings.SETTING_JAVA_VERSION_1_8)
                .equals(BuildSettings.SETTING_JAVA_VERSION_1_7)) {
            classpath.append(":").append(new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "core-lambda-stubs.jar").getAbsolutePath());
        }

        /* Add Sketchware compile-time API stubs (BuildSettings, BuiltInLibraries, coil, R8) */
        File sketchwareStubs = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "sketchware-compile-stubs.jar");
        if (sketchwareStubs.exists()) {
            classpath.append(":").append(sketchwareStubs.getAbsolutePath());
        }

        /* Add used built-in libraries to the classpath */
        for (Jp library : builtInLibraryManager.getLibraries()) {
            classpath.append(":").append(BuiltInLibraries.getLibraryClassesJarPathString(library.getName()));
        }

        /* Add local libraries to the classpath */
        classpath.append(mll.getJarLocalLibrary());

        /* Append user's custom classpath */
        if (!build_settings.getValue(BuildSettings.SETTING_CLASSPATH, "").isEmpty()) {
            classpath.append(":").append(build_settings.getValue(BuildSettings.SETTING_CLASSPATH, ""));
        }

        /* Add JARs from project's classpath */
        String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + yq.sc_id + "/files/classpath/";
        ArrayList<String> jars = FileUtil.listFiles(path, "jar");
        classpath.append(":").append(TextUtils.join(":", jars));

        return classpath.toString();
    }

    private void appendProjectClassOutput(StringBuilder classpath, String outputPath) {
        if (!TextUtils.isEmpty(outputPath) && FileUtil.isExistFile(outputPath)) {
            // Ensure each path entry is separated by ":" so the classpath is valid.
            // Without this, compiledJavaClassesPath and compiledKotlinClassesPath
            // would be concatenated into a single malformed path, causing D8/R8 to
            // report "Unsupported source file type" when both directories exist.
            if (classpath.length() > 0) {
                classpath.append(':');
            }
            classpath.append(outputPath);
        }
    }

    /**
     * @return Similar to {@link ProjectBuilder#getClasspath()}, but doesn't return some local libraries' JARs if ProGuard full mode is enabled
     */
    public String getProguardClasspath() {
        Collection<String> localLibraryJarsWithFullModeOn = new LinkedList<>();

        for (HashMap<String, Object> localLibrary : mll.list) {
            Object nameObject = localLibrary.get("name");
            Object jarPathObject = localLibrary.get("jarPath");

            if (nameObject instanceof String name && jarPathObject instanceof String jarPath) {

                if (localLibrary.containsKey("jarPath") && proguard.libIsProguardFMEnabled(name)) {
                    localLibraryJarsWithFullModeOn.add(jarPath);
                }
            }
        }

        String normalClasspath = getClasspath();
        StringBuilder classpath = new StringBuilder();
        normalClasspathLoop:
        for (String classpathPart : normalClasspath.split(":")) {
            for (String jarPathToExclude : localLibraryJarsWithFullModeOn) {
                if (classpathPart.equals(jarPathToExclude)) {
                    localLibraryJarsWithFullModeOn.remove(jarPathToExclude);
                    continue normalClasspathLoop;
                }
            }

            if (!classpathPart.equals(yq.compiledClassesPath)
                    && !classpathPart.equals(yq.compiledJavaClassesPath)
                    && !classpathPart.equals(yq.compiledKotlinClassesPath)) {
                classpath.append(classpathPart).append(':');
            }
        }

        if (classpath.length() > 0) {
            classpath.deleteCharAt(classpath.length() - 1);
        }

        return classpath.toString();
    }

    /**
     * Dexes libraries.
     *
     * @return List of result DEX files which were merged or couldn't be merged with others.
     * @throws Exception Thrown if merging had problems
     */
    private Collection<File> dexLibraries(File outputDirectory, List<File> dexes) throws Exception {
        int lastDexNumber = 1;
        String nextMergedDexFilename;
        Collection<File> resultDexFiles = new LinkedList<>();
        LinkedList<Dex> dexObjects = new LinkedList<>();
        Iterator<File> toMergeIterator = dexes.iterator();

        List<FieldId> mergedDexFields;
        List<MethodId> mergedDexMethods;
        List<ProtoId> mergedDexProtos;
        List<Integer> mergedDexTypes;

        {
            // Closable gets closed automatically
            Dex firstDex = new Dex(new FileInputStream(toMergeIterator.next()));
            dexObjects.add(firstDex);
            mergedDexFields = new LinkedList<>(firstDex.fieldIds());
            mergedDexMethods = new LinkedList<>(firstDex.methodIds());
            mergedDexProtos = new LinkedList<>(firstDex.protoIds());
            mergedDexTypes = new LinkedList<>(firstDex.typeIds());
        }

        while (toMergeIterator.hasNext()) {
            File dexFile = toMergeIterator.next();
            nextMergedDexFilename = lastDexNumber == 1 ? "classes.dex" : "classes" + lastDexNumber + ".dex";

            // Closable gets closed automatically
            Dex dex = new Dex(new FileInputStream(dexFile));

            boolean canMerge = true;
            List<FieldId> newDexFieldIds = new LinkedList<>();
            List<MethodId> newDexMethodIds = new LinkedList<>();
            List<ProtoId> newDexProtoIds = new LinkedList<>();
            List<Integer> newDexTypeIds = new LinkedList<>();

            bruh:
            {
                for (FieldId fieldId : dex.fieldIds()) {
                    if (!mergedDexFields.contains(fieldId)) {
                        if (mergedDexFields.size() + newDexFieldIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new field IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexFields.size() + " field IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexFieldIds.add(fieldId);
                        }
                    }
                }

                for (MethodId methodId : dex.methodIds()) {
                    if (!newDexMethodIds.contains(methodId)) {
                        if (mergedDexMethods.size() + newDexMethodIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new method IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexMethods.size() + " method IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexMethodIds.add(methodId);
                        }
                    }
                }

                for (ProtoId protoId : dex.protoIds()) {
                    if (!newDexProtoIds.contains(protoId)) {
                        if (mergedDexProtos.size() + newDexProtoIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new proto IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexProtos.size() + " proto IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexProtoIds.add(protoId);
                        }
                    }
                }

                for (Integer typeId : dex.typeIds()) {
                    if (!newDexTypeIds.contains(typeId)) {
                        if (mergedDexTypes.size() + newDexProtoIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new type IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexTypes.size() + " type IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexTypeIds.add(typeId);
                        }
                    }
                }
            }

            if (canMerge) {
                LogUtil.d(TAG, "Merging DEX #" + dexes.indexOf(dexFile) + " as well to " + nextMergedDexFilename);
                dexObjects.add(dex);
                mergedDexFields.addAll(newDexFieldIds);
                mergedDexMethods.addAll(newDexMethodIds);
                mergedDexProtos.addAll(newDexProtoIds);
                mergedDexTypes.addAll(newDexTypeIds);
            } else {
                File target = new File(outputDirectory, nextMergedDexFilename);
                mergeDexes(target, dexObjects);
                resultDexFiles.add(target);
                dexObjects.clear();
                dexObjects.add(dex);

                mergedDexFields = new ArrayList<>(dex.fieldIds());
                mergedDexMethods = new ArrayList<>(dex.methodIds());
                mergedDexProtos = new ArrayList<>(dex.protoIds());
                mergedDexTypes = new ArrayList<>(dex.typeIds());
                lastDexNumber++;
            }
        }
        if (!dexObjects.isEmpty()) {
            File file = new File(outputDirectory, lastDexNumber == 1 ? "classes.dex" : "classes" + lastDexNumber + ".dex");
            mergeDexes(file, dexObjects);
            resultDexFiles.add(file);
        }

        return resultDexFiles;
    }

    /**
     * Get package names of in-use libraries which have resources, separated by <code>:</code>.
     */
    public String getLibraryPackageNames() {
        StringBuilder extraPackages = new StringBuilder();
        for (Jp library : builtInLibraryManager.getLibraries()) {
            if (library.hasResources()) {
                extraPackages.append(library.getPackageName()).append(":");
            }
        }
        return extraPackages + mll.getPackageNameLocalLibrary();
    }

    /**
     * Run Eclipse Compiler to compile Java files.
     *
     * This always performs a full recompilation of every generated Java
     * source. A previous version of this method tried to skip/incrementally
     * recompile using IncrementalCompileCache, deciding "nothing changed" by
     * comparing content fingerprints of a normalized_sources temp tree. That
     * system had a confirmed, reproducible bug: toggling Material3 Manager
     * off then on again left stale generated sources in place (both in the
     * normalized temp copy and in generator-owned files like
     * SketchwareUtil.java that were only regenerated when missing), so the
     * skip decision would fire even though the real compiled output no
     * longer matched the project's actual configuration -> APK shipped with
     * mismatched bytecode/resources -> instant crash on launch that did not
     * self-heal by re-toggling the setting.
     *
     * Given how cheap a full ECJ compile is for typical Sketchware projects
     * (seconds, not minutes) compared to the cost of a silent, hard-to-
     * diagnose stale-cache crash, the incremental skip/partial-recompile
     * logic has been removed rather than patched a third time. Compiling
     * everything, every time, is simple enough to reason about that this
     * class of bug can't recur. The independent-source-group parallelism in
     * runFullCompilation() is unrelated to that bug (it only groups files
     * for the compiler, it never skips any of them) and is kept as-is.
     */
    public void compileJavaCode() throws zy, IOException {
        buildProfiler.start("ECJ");
        long savedTimeMillis = System.currentTimeMillis();

        String pathJava = fpu.getPathJava(yq.sc_id);
        String pathBroadcast = fpu.getPathBroadcast(yq.sc_id);
        String pathService = fpu.getPathService(yq.sc_id);
        String normalizedRoot = yq.binDirectoryPath + File.separator + "normalized_sources";
        String normalizedPathJava = FileUtil.isExistFile(pathJava)
                ? LegacyJavaSourceNormalizer.normalizeDirectoryToTemp(pathJava, normalizedRoot + File.separator + "java")
                : null;
        String normalizedPathBroadcast = FileUtil.isExistFile(pathBroadcast)
                ? LegacyJavaSourceNormalizer.normalizeDirectoryToTemp(pathBroadcast, normalizedRoot + File.separator + "broadcast")
                : null;
        String normalizedPathService = FileUtil.isExistFile(pathService)
                ? LegacyJavaSourceNormalizer.normalizeDirectoryToTemp(pathService, normalizedRoot + File.separator + "service")
                : null;
        String normalizedGeneratedPath = LegacyJavaSourceNormalizer.normalizeDirectoryToTemp(
                yq.javaFilesPath,
                normalizedRoot + File.separator + "generated");

        // AAPT2 writes the real, correctly-packaged R.java into a package-name
        // subdirectory under rJavaDirectoryPath (e.g. gen/com/example/app/R.java).
        // On some builds a stray, package-less R.java is left directly at the
        // root of rJavaDirectoryPath from an earlier stage/older run. If left
        // in place, collectJavaSourceFiles() below picks it up alongside the
        // real one, and ECJ fails immediately with "Syntax error on token
        // 'package', Name expected after this token" because that root-level
        // copy has an empty/missing package declaration. This must be deleted
        // before collecting source files on every build, not just once, since
        // AAPT2 (a separate build stage) can recreate it.
        File rJavaFileWithoutPackage = new File(yq.rJavaDirectoryPath, "R.java");
        if (rJavaFileWithoutPackage.exists() && !rJavaFileWithoutPackage.delete()) {
            LogUtil.w(TAG, "Failed to delete file " + rJavaFileWithoutPackage.getAbsolutePath());
        }

        List<String> fullCompileSources = collectExistingSourceInputs(
                normalizedGeneratedPath,
                yq.rJavaDirectoryPath,
                normalizedPathJava,
                normalizedPathBroadcast,
                normalizedPathService
        );

        List<String> allJavaSourceFiles = collectJavaSourceFiles(fullCompileSources);
        File javaClassesDirectory = new File(yq.compiledJavaClassesPath);

        cleanJavaAndMergedOutputs();
        EcjCompileResult result = runFullCompilation(allJavaSourceFiles, fullCompileSources, javaClassesDirectory);

        LogUtil.d(TAG, "System.out of Eclipse compiler: " + result.output);
        if (result.success) {
            LogUtil.d(TAG, "System.err of Eclipse compiler: " + result.errors);
            rebuildMergedCompiledClassesDirectory();
            buildProfiler.stop("ECJ");
            LogUtil.d(TAG, "Compiling Java files took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        } else {
            LogUtil.e(TAG, "Failed to compile Java files");
            throw new zy(result.getBestErrorMessage());
        }
    }

    /**
     * Recursively lists every .java file under the given source roots
     * (directories or single files), matching what IncrementalCompileCache's
     * directory walk used to collect but without any caching involved.
     */
    private List<String> collectJavaSourceFiles(List<String> sourceRoots) {
        List<String> result = new ArrayList<>();
        for (String root : sourceRoots) {
            if (root == null || root.isEmpty()) {
                continue;
            }
            collectJavaSourceFilesInto(new File(root), result);
        }
        Collections.sort(result);
        return result;
    }

    private void collectJavaSourceFilesInto(File file, List<String> out) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                collectJavaSourceFilesInto(child, out);
            }
        } else if (file.getName().endsWith(".java")) {
            out.add(file.getAbsolutePath());
        }
    }

    /**
     * Full compilation strategy: pre-compile R.java first so it becomes available on the
     * classpath, then compile the remaining files in parallel using an independent-component
     * graph.  Without this split, R.java is a hub that every Activity imports, which forces
     * all source files into a single dependency component and makes parallel ECJ useless.
     */
    private EcjCompileResult runFullCompilation(
            List<String> allJavaSourceFiles,
            List<String> fullCompileSources,
            File javaClassesDirectory) throws zy {

        if (!isParallelEcjEnabled() || allJavaSourceFiles.isEmpty()) {
            LogUtil.d(TAG, "Running sequential full Java compilation for "
                    + fullCompileSources.size() + " source roots/files");
            return runEcjCompile(buildEcjArguments(fullCompileSources, yq.compiledJavaClassesPath));
        }

        // Separate R.java files (resource-ID hubs) from the rest of the source files.
        List<String> rJavaFiles = new ArrayList<>();
        List<String> otherJavaFiles = new ArrayList<>();
        for (String f : allJavaSourceFiles) {
            if (f != null && (f.endsWith("/R.java") || f.endsWith(File.separator + "R.java"))) {
                rJavaFiles.add(f);
            } else {
                otherJavaFiles.add(f);
            }
        }

        // Phase 1: compile R.java alone (takes only seconds).
        if (!rJavaFiles.isEmpty()) {
            LogUtil.d(TAG, "Phase 1: compiling " + rJavaFiles.size() + " R.java file(s) first");
            EcjCompileResult rResult = runEcjCompile(
                    buildEcjArguments(rJavaFiles, yq.compiledJavaClassesPath));
            if (!rResult.success) {
                LogUtil.w(TAG, "R.java pre-compilation failed; falling back to full sequential compilation");
                return runEcjCompile(buildEcjArguments(fullCompileSources, yq.compiledJavaClassesPath));
            }
        }

        // Phase 2: build a fresh graph without R.java and partition into independent groups.
        // R.class is now on the classpath (compiledJavaClassesPath), so each Activity can
        // resolve 'R' without importing from the source tree.
        if (otherJavaFiles.isEmpty()) {
            LogUtil.d(TAG, "No non-R.java sources remain after phase 1; compilation complete");
            return new EcjCompileResult(true, "", "", "");
        }

        JavaCompileGraph nonRGraph = new JavaCompileGraph(otherJavaFiles);
        List<List<String>> groups = nonRGraph.partitionIndependentSourceGroups(otherJavaFiles);
        if (groups.size() > 1) {
            LogUtil.d(TAG, "Phase 2: parallel full Java compilation — "
                    + groups.size() + " independent group(s) for "
                    + otherJavaFiles.size() + " source file(s)");
            return runParallelEcjCompile(groups, javaClassesDirectory);
        }

        LogUtil.d(TAG, "Phase 2: sources form a single dependency component ("
                + otherJavaFiles.size() + " files); running sequential full compilation");
        return runEcjCompile(buildEcjArguments(fullCompileSources, yq.compiledJavaClassesPath));
    }

    private ArrayList<String> buildEcjArguments(List<String> sourceInputs, String outputDirectory) {
        ArrayList<String> args = new ArrayList<>();
        // Enforce a minimum effective Java compliance of 1.8.
        // Sketchware-generated code always uses lambda expressions for event listeners,
        // which require Java 8+. Allowing 1.7 here causes "Syntax error on token" failures
        // on ALL projects regardless of the user's choice in Build Settings.
        String javaVersion = build_settings.getValue(BuildSettings.SETTING_JAVA_VERSION,
                BuildSettings.SETTING_JAVA_VERSION_1_8);
        if (BuildSettings.SETTING_JAVA_VERSION_1_7.equals(javaVersion)) {
            javaVersion = BuildSettings.SETTING_JAVA_VERSION_1_8;
        }
        args.add("-" + javaVersion);
        args.add("-nowarn");
        if (!build_settings.getValue(BuildSettings.SETTING_NO_WARNINGS,
                BuildSettings.SETTING_GENERIC_VALUE_TRUE).equals(BuildSettings.SETTING_GENERIC_VALUE_TRUE)) {
            args.add("-deprecation");
        }
        args.add("-d");
        args.add(outputDirectory);
        args.add("-cp");
        args.add(getClasspath());
        args.add("-proc:none");
        args.addAll(sourceInputs);
        return args;
    }

    private List<String> collectExistingSourceInputs(String... paths) {
        ArrayList<String> sourceInputs = new ArrayList<>();
        if (paths == null) {
            return sourceInputs;
        }
        for (String path : paths) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            File file = new File(path);
            if (file.exists()) {
                sourceInputs.add(path);
            }
        }
        return sourceInputs;
    }

    private void cleanJavaAndMergedOutputs() {
        FileUtil.deleteFile(yq.compiledJavaClassesPath);
        FileUtil.deleteFile(yq.compiledClassesPath);
        FileUtil.makeDir(yq.compiledJavaClassesPath);
        FileUtil.makeDir(yq.compiledClassesPath);
    }

    public void rebuildMergedCompiledClassesDirectory() {
        File mergedOutput = new File(yq.compiledClassesPath);
        File javaOutput = new File(yq.compiledJavaClassesPath);
        File kotlinOutput = new File(yq.compiledKotlinClassesPath);
        File marker = new File(yq.binDirectoryPath, ".merged_classes_marker");

        long markerTime = marker.exists() ? marker.lastModified() : 0L;
        if (markerTime > 0 && mergedOutput.exists()) {
            long javaModified = javaOutput.exists() ? newestModified(javaOutput) : 0L;
            long kotlinModified = kotlinOutput.exists() ? newestModified(kotlinOutput) : 0L;
            if (javaModified <= markerTime && kotlinModified <= markerTime) {
                return;
            }
        }

        FileUtil.deleteFile(mergedOutput.getAbsolutePath());
        FileUtil.makeDir(mergedOutput.getAbsolutePath());

        try {
            if (javaOutput.exists()) {
                FileUtil.copyDirectory(javaOutput, mergedOutput);
            }
            if (kotlinOutput.exists()) {
                FileUtil.copyDirectory(kotlinOutput, mergedOutput);
            }
            marker.createNewFile();
            marker.setLastModified(System.currentTimeMillis());
        } catch (IOException e) {
            LogUtil.e(TAG, "Failed to rebuild merged compiled classes directory", e);
        }
    }

    private static long newestModified(File dir) {
        long newest = dir.lastModified();
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                long t = child.isDirectory() ? newestModified(child) : child.lastModified();
                if (t > newest) newest = t;
            }
        }
        return newest;
    }

    private EcjCompileResult runParallelEcjCompile(List<List<String>> compileGroups, File targetOutputDirectory) throws zy {
        File parallelRoot = new File(yq.binDirectoryPath, "ecj_parallel");
        FileUtil.deleteFile(parallelRoot.getAbsolutePath());
        FileUtil.makeDir(parallelRoot.getAbsolutePath());
        FileUtil.makeDir(targetOutputDirectory.getAbsolutePath());

        int threadCount = Math.min(compileGroups.size(), Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(compileGroups.size());
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicReference<String> bestError = new AtomicReference<>("");
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        List<File> tempOutputs = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < compileGroups.size(); i++) {
            final int groupIndex = i;
            final List<String> group = compileGroups.get(i);
            executorService.submit(() -> {
                try {
                    if (failed.get()) {
                        return;
                    }
                    File tempOutput = new File(parallelRoot, "group_" + groupIndex);
                    FileUtil.makeDir(tempOutput.getAbsolutePath());
                    EcjCompileResult groupResult = runEcjCompile(buildEcjArguments(group, tempOutput.getAbsolutePath()));
                    synchronized (stdout) {
                        if (!groupResult.output.isEmpty()) {
                            stdout.append(groupResult.output).append('\n');
                        }
                        if (!groupResult.errors.isEmpty()) {
                            stderr.append(groupResult.errors).append('\n');
                        }
                    }
                    if (!groupResult.success) {
                        failed.set(true);
                        bestError.compareAndSet("", groupResult.getBestErrorMessage());
                    } else {
                        tempOutputs.add(tempOutput);
                    }
                } catch (Exception e) {
                    failed.set(true);
                    bestError.compareAndSet("", e.getMessage() == null ? "Parallel Java compilation failed" : e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            if (!latch.await(3600, TimeUnit.SECONDS)) {
                failed.set(true);
                bestError.compareAndSet("", "Timed out while waiting for parallel Java compilation");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new zy("Parallel Java compilation interrupted");
        } finally {
            executorService.shutdownNow();
        }

        if (!failed.get()) {
            for (File tempOutput : tempOutputs) {
                try {
                    FileUtil.copyDirectory(tempOutput, targetOutputDirectory);
                } catch (IOException e) {
                    failed.set(true);
                    bestError.compareAndSet("", e.getMessage() == null ? "Failed to merge parallel Java compiler outputs" : e.getMessage());
                    break;
                }
            }
        }

        FileUtil.deleteFile(parallelRoot.getAbsolutePath());
        return new EcjCompileResult(!failed.get(), stdout.toString().trim(), stderr.toString().trim(), bestError.get());
    }

    private EcjCompileResult runEcjCompile(ArrayList<String> args) throws zy {
        LogUtil.d(TAG, "Running Eclipse compiler with these arguments: " + args);

        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> compilerError = new AtomicReference<>("");
        AtomicReference<String> compilerOutput = new AtomicReference<>("");
        AtomicReference<String> compilerStderr = new AtomicReference<>("");
        CountDownLatch latch = new CountDownLatch(1);

        ECJCompilerClient.compile(context, args.toArray(new String[0]), new ECJCompilerClient.Listener() {
            @Override
            public void onProgress(String message) {
                LogUtil.d(TAG, message);
            }

            @Override
            public void onSuccess(String output) {
                compilerOutput.set(output == null ? "" : output);
                success.set(true);
                completed.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String errors, String output) {
                compilerStderr.set(errors == null ? "" : errors);
                compilerOutput.set(output == null ? "" : output);
                compilerError.set(errors == null ? "" : errors);
                completed.set(true);
                latch.countDown();
            }

            @Override
            public void onOOM() {
                compilerError.set("Java compilation ran out of memory in the isolated compiler process.");
                completed.set(true);
                latch.countDown();
            }
        });

        try {
            latch.await(3600, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new zy("Java compilation interrupted");
        }

        if (!completed.get()) {
            throw new zy("Timed out while waiting for isolated Java compilation");
        }

        return new EcjCompileResult(
                success.get(),
                compilerOutput.get(),
                compilerStderr.get(),
                compilerError.get()
        );
    }

    private static final class EcjCompileResult {
        private final boolean success;
        private final String output;
        private final String errors;
        private final String compilerError;

        private EcjCompileResult(boolean success, String output, String errors, String compilerError) {
            this.success = success;
            this.output = output == null ? "" : output;
            this.errors = errors == null ? "" : errors;
            this.compilerError = compilerError == null ? "" : compilerError;
        }

        private String getBestErrorMessage() {
            if (!compilerError.trim().isEmpty()) {
                return compilerError;
            }
            if (!errors.trim().isEmpty()) {
                return errors;
            }
            if (!output.trim().isEmpty()) {
                return output;
            }
            return "Unknown Java compilation failure";
        }
    }

    public void buildApk() throws By {
        // dexesToAddButNotMerge is always empty now that getDexFilesReady() merges
        // unconditionally; the branches below that reference it are effectively dead
        // but left intact for compatibility (see field declaration for details).
        String firstDexPath = dexesToAddButNotMerge.isEmpty() ? yq.classesDexPath : dexesToAddButNotMerge.remove(0).getAbsolutePath();
        try {
            ApkBuilder apkBuilder = new ApkBuilder(new File(yq.unsignedUnalignedApkPath), new File(yq.resourcesApkPath), new File(firstDexPath), null, null, System.out);

            for (Jp library : builtInLibraryManager.getLibraries()) {
                apkBuilder.addResourcesFromJar(BuiltInLibraries.getLibraryClassesJarPath(library.getName()));
            }

            for (String jarPath : mll.getJarLocalLibrary().split(":")) {
                if (!jarPath.trim().isEmpty()) {
                    apkBuilder.addResourcesFromJar(new File(jarPath));
                }
            }

            /* Add project's native libraries */
            File nativeLibrariesDirectory = new File(fpu.getPathNativelibs(yq.sc_id));
            if (nativeLibrariesDirectory.exists()) {
                apkBuilder.addNativeLibraries(nativeLibrariesDirectory);
            }

            /* Add Local libraries' native libraries */
            for (String nativeLibraryDirectory : mll.getNativeLibs()) {
                apkBuilder.addNativeLibraries(new File(nativeLibraryDirectory));
            }

            if (dexesToAddButNotMerge.isEmpty()) {
                List<String> dexFiles = FileUtil.listFiles(yq.binDirectoryPath, "dex");
                for (String dexFile : dexFiles) {
                    if (!Uri.fromFile(new File(dexFile)).getLastPathSegment().equals("classes.dex")) {
                        apkBuilder.addFile(new File(dexFile), Uri.parse(dexFile).getLastPathSegment());
                    }
                }
            } else {
                int dexNumber = 2;

                for (File dexFile : dexesToAddButNotMerge) {
                    apkBuilder.addFile(dexFile, "classes" + dexNumber + ".dex");
                    dexNumber++;
                }
            }

            apkBuilder.setDebugMode(false);
            apkBuilder.sealApk();
        } catch (ApkCreationException | SealedApkException e) {
            throw new By(e.getMessage());
        } catch (DuplicateFileException e) {
            String message = "Duplicate files from two libraries detected \r\n";
            message += "File1: " + e.getFile1() + " \r\n";
            message += "File2: " + e.getFile2() + " \r\n";
            message += "Archive path: " + e.getArchivePath();
            throw new By(message);
        }
        LogUtil.d(TAG, "Time passed since starting to compile resources until building the unsigned APK: " +
                (System.currentTimeMillis() - timestampResourceCompilationStarted) + " ms");
    }

    /**
     * Merges all built-in/local library DEX files (plus the project's own compiled DEX)
     * into as few output DEX files as possible, for every build regardless of minSdk or
     * debug/release mode. This is unconditional: unmerged raw multidex packaging was
     * removed after confirming it allowed duplicate class definitions (e.g. between
     * emoji2 and lifecycle-common) to reach the ART verifier and cause silent crashes.
     * A content fingerprint is used to skip re-merging when inputs haven't changed.
     *
     * @throws Exception Thrown if merging failed
     */
    public void getDexFilesReady() throws Exception {
        long savedTimeMillis = System.currentTimeMillis();
        ArrayList<File> dexes = new ArrayList<>();

        /* Add AndroidX MultiDex library if needed */
        if (settings.getMinSdkVersion() < 21) {
            dexes.add(BuiltInLibraries.getLibraryDexFile(BuiltInLibraries.ANDROIDX_MULTIDEX));
        }

        /* Add HTTP legacy files if wanted */
        if (!build_settings.getValue(BuildSettings.SETTING_NO_HTTP_LEGACY, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(ProjectSettings.SETTING_GENERIC_VALUE_TRUE)) {
            dexes.add(BuiltInLibraries.getLibraryDexFile(BuiltInLibraries.HTTP_LEGACY_ANDROID));
        }

        /* Add used built-in libraries' DEX files */
        for (Jp builtInLibrary : builtInLibraryManager.getLibraries()) {
            dexes.add(BuiltInLibraries.getLibraryDexFile(builtInLibrary.getName()));
        }

        /* Add local libraries' main DEX files */
        ArrayList<HashMap<String, Object>> list = mll.list;
        for (int i1 = 0, listSize = list.size(); i1 < listSize; i1++) {
            HashMap<String, Object> localLibrary = list.get(i1);
            Object localLibraryName = localLibrary.get("name");

            if (localLibraryName instanceof String) {
                Object localLibraryDexPath = localLibrary.get("dexPath");

                if (localLibraryDexPath instanceof String) {
                    if (!proguard.libIsProguardFMEnabled((String) localLibraryName)) {
                        dexes.add(new File((String) localLibraryDexPath));
                        /* Add library's extra DEX files */
                        File localLibraryDirectory = new File((String) localLibraryDexPath).getParentFile();

                        if (localLibraryDirectory != null) {
                            File[] localLibraryFiles = localLibraryDirectory.listFiles();

                            if (localLibraryFiles != null) {
                                for (File localLibraryFile : localLibraryFiles) {
                                    String filename = localLibraryFile.getName();

                                    if (!filename.equals("classes.dex")
                                            && filename.startsWith("classes") && filename.endsWith(".dex")) {
                                        dexes.add(localLibraryFile);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    SketchwareUtil.toastError("Invalid DEX file path of enabled Local library #" + i1, Toast.LENGTH_LONG);
                }
            } else {
                SketchwareUtil.toastError("Invalid name of enabled Local library #" + i1, Toast.LENGTH_LONG);
            }
        }

        for (String file : FileUtil.listFiles(yq.binDirectoryPath + File.separator + "dex", "dex")) {
            dexes.add(new File(file));
        }

        LogUtil.d(TAG, "Will merge these " + dexes.size() + " DEX files to classes.dex: " + dexes);

        // NOTE: DEX merging is now unconditional for ALL builds (regardless of minSdk
        // or debug/release). Built-in library DEX files (e.g. emoji2, lifecycle-common)
        // have been confirmed to bundle overlapping transitive classes; the previous
        // raw-multidex path (used for debug builds with minSdk >= 21) added these DEX
        // files to the APK unmerged, allowing duplicate class definitions to reach the
        // ART verifier and causing silent startup crashes. DexMerger's KEEP_FIRST
        // collision policy resolves duplicates safely, so merging is always performed.
        // `dexesToAddButNotMerge` is intentionally left unpopulated (see buildApk()).
        {
            // Build a fingerprint of all input DEX files so we can skip the expensive
            // merge when nothing has changed (libraries and project DEX are identical).
            StringBuilder dexFpBuilder = new StringBuilder();
            for (File dex : dexes) {
                dexFpBuilder.append(dex.getAbsolutePath())
                        .append('|').append(dex.exists() ? dex.length() : -1L)
                        .append('|').append(dex.exists() ? dex.lastModified() : 0L)
                        .append('\n');
            }
            String dexMergeFingerprint = dexFpBuilder.toString();
            File dexMergeMarker = new File(yq.binDirectoryPath, ".dex_merge_marker");
            File classesDex = new File(yq.binDirectoryPath, "classes.dex");

            if (classesDex.exists() && FileUtil.isExistFile(dexMergeMarker.getAbsolutePath())) {
                String savedFingerprint = FileUtil.readFile(dexMergeMarker.getAbsolutePath());
                if (dexMergeFingerprint.equals(savedFingerprint)) {
                    LogUtil.d(TAG, "Skipping DEX library merge — inputs unchanged");
                    return;
                }
            }

            warnOnDuplicateClassDescriptors(dexes);

            dexLibraries(new File(yq.binDirectoryPath), dexes);
            FileUtil.writeFile(dexMergeMarker.getAbsolutePath(), dexMergeFingerprint);
            LogUtil.d(TAG, "Merging DEX files took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        }
    }

    /**
     * Lightweight, descriptor-level duplicate-class scan across the DEX files about to
     * be merged. This is NOT a full semantic/bytecode comparison — it only flags class
     * descriptors (e.g. "Landroidx/lifecycle/Lifecycle;") that appear in more than one
     * input DEX, which is the exact pattern found between emoji2 and lifecycle-common.
     * Findings are logged only; DexMerger's KEEP_FIRST policy already resolves the
     * collision safely, so this does not fail the build. Intended to help catch new
     * cases of the same contamination pattern in future library additions.
     */
    private void warnOnDuplicateClassDescriptors(List<File> dexes) {
        try {
            java.util.HashMap<String, File> seenIn = new java.util.HashMap<>();
            for (File dexFile : dexes) {
                if (!dexFile.exists() || !dexFile.getName().endsWith(".dex")) {
                    continue;
                }
                Dex dex;
                try {
                    dex = new Dex(dexFile);
                } catch (Exception e) {
                    // Not every input is guaranteed to be a standalone parseable DEX
                    // at this stage; skip silently rather than risk breaking the build.
                    continue;
                }
                List<String> typeNames = dex.typeNames();
                for (mod.agus.jcoderz.dex.ClassDef classDef : dex.classDefs()) {
                    String descriptor = typeNames.get(classDef.getTypeIndex());
                    File previous = seenIn.putIfAbsent(descriptor, dexFile);
                    if (previous != null && !previous.equals(dexFile)) {
                        LogUtil.w(TAG, "Duplicate class descriptor " + descriptor + " found in both "
                                + previous.getName() + " and " + dexFile.getName()
                                + " — will be resolved by DexMerger (KEEP_FIRST).");
                    }
                }
            }
        } catch (Exception e) {
            // This is a best-effort diagnostic pass; never let it block a build.
            LogUtil.w(TAG, "Duplicate-class scan failed (non-fatal): " + e.getMessage());
        }
    }

    /**
     * Extracts AAPT2 binaries (if they need to be extracted).
     *
     * @throws By If anything goes wrong while extracting
     */
    public void maybeExtractAapt2() throws By {
        var abi = Build.SUPPORTED_ABIS[0];
        try {
            if (hasFileChanged("aapt/aapt2-" + abi, aapt2Binary.getAbsolutePath())) {
                Os.chmod(aapt2Binary.getAbsolutePath(), S_IRUSR | S_IWUSR | S_IXUSR);
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to extract AAPT2 binaries", e);
            // noinspection ConstantValue: the bytecode's lying
            throw new By(
                    e instanceof FileNotFoundException fileNotFoundException ?
                            "Looks like the device's architecture (" + abi + ") isn't supported.\n"
                                    + Log.getStackTraceString(fileNotFoundException)
                            : "Couldn't extract AAPT2 binaries! Message: " + e.getMessage()
            );
        }
    }

    /**
     * Checks if we need to extract any library/dependency from assets to filesDir,
     * and extracts them, if needed. Also initializes used built-in libraries.
     */
    public void buildBuiltInLibraryInformation() {
        if (yq.N.g) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_APPCOMPAT);
            builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_COORDINATORLAYOUT);
            builtInLibraryManager.addLibrary(BuiltInLibraries.MATERIAL);
        }
        if (yq.N.isFirebaseEnabled) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_COMMON);
        }
        if (yq.N.isFirebaseAuthUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_AUTH);
        }
        if (yq.N.isFirebaseDatabaseUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_DATABASE);
        }
        if (yq.N.isFirebaseStorageUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_STORAGE);
        }
        if (yq.N.isMapUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.PLAY_SERVICES_MAPS);
        }
        if (yq.N.isAdMobEnabled) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.PLAY_SERVICES_ADS);
        }
        if (yq.N.isGsonUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.GSON);
        }
        if (yq.N.isGlideUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.GLIDE);
        }
        if (yq.N.isHttp3Used) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.OKHTTP_ANDROID);
        }

        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(this, builtInLibraryManager);

        // ── Extra Libraries from Library Manager settings ──────────────────
        LibraryExtrasSettings extraSettings = new LibraryExtrasSettings(
                new ProjectSettingsStore(SketchApplication.getContext(), yq.N.sc_id));

        if (yq.N.g) {
            // EdgeToEdge — requires androidx.activity:activity >= 1.8.0
            // AppCompat pulls it transitively, but we add it explicitly so the
            // class is guaranteed to be present in the project's libs even when
            // the transitive resolution is skipped during on-device build.
            if (extraSettings.isEdgeToEdge()) {
                builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_ACTIVITY);
            }
            // WorkManager - requires AppCompat
            if (extraSettings.isForceWorkManager()) {
                builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_WORK_RUNTIME);
            }
            // Media3 / ExoPlayer - requires AppCompat + minSdk 24+
            if (extraSettings.isUseMedia3()) {
                builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_MEDIA3_EXOPLAYER);
                builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_MEDIA3_EXOPLAYER_HLS);
                builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_MEDIA3_UI);
            }
            // Browser - requires AppCompat
            if (extraSettings.isUseBrowser()) {
                builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_BROWSER);
            }
            // Credential Manager - requires AppCompat + minSdk 24+
            if (extraSettings.isUseCredentialManager()) {
                builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_CREDENTIALS);
            }
            // Shizuku
            if (extraSettings.isUseShizuku()) {
                builtInLibraryManager.addLibrary(BuiltInLibraries.DEV_RIKKA_SHIZUKU_PROVIDER);
            }
        }
        // Firebase-dependent extras
        if (yq.N.isFirebaseEnabled) {
            // Google Analytics
            if (extraSettings.isUseGoogleAnalytics()) {
                builtInLibraryManager.addLibrary(BuiltInLibraries.PLAY_SERVICES_MEASUREMENT_API);
            }
            // Android Billing - requires Firebase + AppCompat
            if (yq.N.g && extraSettings.isUseAndroidBilling()) {
                builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROID_BILLING);
            }
        }
        // GlideTransformations (depends on Glide being used)
        if (yq.N.isGlideUsed && extraSettings.isUseGlideTransformations()) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.GLIDE_TRANSFORMATIONS);
        }

        ExtLibSelected.addUsedDependencies(yq.N.x, builtInLibraryManager);
    }

    public BuiltInLibraryManager getBuiltInLibraryManager() {
        return builtInLibraryManager;
    }

    /**
     * Sign the debug APK file with testkey.
     * <p>
     * This method uses apksigner, but kellinwood's zipsigner as fallback.
     */
    public void signDebugApk() throws GeneralSecurityException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        TestkeySignBridge.signWithTestkey(yq.unsignedUnalignedApkPath, yq.finalToInstallApkPath);
    }

    private void mergeDexes(File target, List<Dex> dexes) throws IOException {
        DexMerger merger = new DexMerger(dexes.toArray(new Dex[0]), CollisionPolicy.KEEP_FIRST, new DxContext());
        merger.merge().writeTo(target);
    }

    /**
     * Adds all built-in libraries' ProGuard rules to {@code args}, if any.
     *
     * @param args List of arguments to add built-in libraries' ProGuard roles to.
     */
    private void proguardAddLibConfigs(List<String> args) {
        for (Jp library : builtInLibraryManager.getLibraries()) {
            File config = BuiltInLibraries.getLibraryProguardConfiguration(library.getName());
            if (config.exists()) {
                args.add("-include");
                args.add(config.getAbsolutePath());
            }
        }
    }

    /**
     * Generates default ProGuard R.java rules and adds them to {@code args}.
     *
     * @param args List of arguments to add R.java rules to.
     */
    private void proguardAddRjavaRules(List<String> args) {
        FileUtil.writeFile(yq.proguardAutoGeneratedExclusions, getRJavaRules());
        args.add("-include");
        args.add(yq.proguardAutoGeneratedExclusions);
    }

    private String getRJavaRules() {
        StringBuilder sb = new StringBuilder("# R.java rules");
        for (Jp jp : builtInLibraryManager.getLibraries()) {
            if (jp.hasResources() && !jp.getPackageName().isEmpty()) {
                sb.append("\n");
                sb.append("-keep class ");
                sb.append(jp.getPackageName());
                sb.append(".** { *; }");
            }
        }
        for (HashMap<String, Object> hashMap : mll.list) {
            String obj = hashMap.get("name").toString();
            if (hashMap.containsKey("packageName") && !proguard.libIsProguardFMEnabled(obj)) {
                sb.append("\n");
                sb.append("-keep class ");
                sb.append(hashMap.get("packageName").toString());
                sb.append(".** { *; }");
            }
        }
        sb.append("\n");
        sb.append("-keep class ").append(yq.packageName).append(".R { *; }").append('\n');
        return sb.toString();
    }

    private void runR8Dexer() throws IOException {
        ArrayList<String> rules = new ArrayList<>();
        rules.add("-dontshrink");
        rules.add("-dontoptimize");
        rules.add("-dontobfuscate");

        try {
            JarBuilder.INSTANCE.generateJar(new File(yq.compiledClassesPath));
            new R8Compiler(
                    rules,
                    new String[]{ProguardHandler.ANDROID_PROGUARD_RULES_PATH, yq.proguardAaptRules},
                    getProguardClasspath().split(":"),
                    new String[]{yq.compiledClassesPath + ".jar"},
                    settings.getMinSdkVersion(),
                    yq
            ).compile();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public void runR8() throws IOException {
        buildProfiler.start("R8");
        long savedTimeMillis = System.currentTimeMillis();
        long inputJarSize = new File(yq.compiledClassesPath + ".jar").exists()
                ? new File(yq.compiledClassesPath + ".jar").length()
                : 0L;

        ArrayList<String> config = new ArrayList<>();
        config.add(ProguardHandler.ANDROID_PROGUARD_RULES_PATH);
        config.add(yq.proguardAaptRules);
        config.add(proguard.getCustomProguardRules());
        config.add(proguard.getR8ProfileRulesPath());
        var rules = new ArrayList<>(Arrays.asList(getRJavaRules().split("\n")));
        for (Jp library : builtInLibraryManager.getLibraries()) {
            File f = BuiltInLibraries.getLibraryProguardConfiguration(library.getName());
            if (f.exists()) {
                config.add(f.getAbsolutePath());
            }
        }
        config.addAll(mll.getPgRules());
        ArrayList<String> jars = new ArrayList<>();
        jars.add(yq.compiledClassesPath + ".jar");

        for (HashMap<String, Object> hashMap : mll.list) {
            String obj = hashMap.get("name").toString();
            if (hashMap.containsKey("jarPath") && proguard.libIsProguardFMEnabled(obj)) {
                jars.add(hashMap.get("jarPath").toString());
            }
        }
        try {
            JarBuilder.INSTANCE.generateJar(new File(yq.compiledClassesPath));
            new R8Compiler(rules, config.toArray(new String[0]), getProguardClasspath().split(":"), jars.toArray(new String[0]), settings.getMinSdkVersion(), yq).compile();
        } catch (Exception e) {
            throw new IOException(e);
        }
        long outputDexSize = 0L;
        for (String dexPath : FileUtil.listFiles(yq.binDirectoryPath + File.separator + "dex", "dex")) {
            outputDexSize += new File(dexPath).length();
        }
        buildProfiler.stop("R8");
        LogUtil.d(TAG, "R8 took " + (System.currentTimeMillis() - savedTimeMillis) + " ms using profile " + proguard.getR8Profile().getDisplayName());
        LogUtil.d(TAG, "R8 output stats: input JAR=" + inputJarSize + " B, output DEX=" + outputDexSize + " B");
    }

    public void runProguard() throws IOException {
        long savedTimeMillis = System.currentTimeMillis();

        ArrayList<String> args = new ArrayList<>();

        /* Include global ProGuard rules */
        args.add("-include");
        args.add(ProguardHandler.ANDROID_PROGUARD_RULES_PATH);

        /* Include ProGuard rules generated by AAPT2 */
        args.add("-include");
        args.add(yq.proguardAaptRules);

        /* Include custom ProGuard rules */
        args.add("-include");
        args.add(proguard.getCustomProguardRules());

        proguardAddLibConfigs(args);
        proguardAddRjavaRules(args);

        /* Include local libraries' ProGuard rules */
        for (String rule : mll.getPgRules()) {
            args.add("-include");
            args.add(rule);
        }

        /* Include compiled Java classes (?) IT SAYS -in*jar*s, so why include .class es? */
        args.add("-injars");
        args.add(yq.compiledClassesPath);

        for (HashMap<String, Object> hashMap : mll.list) {
            String obj = hashMap.get("name").toString();
            if (hashMap.containsKey("jarPath") && proguard.libIsProguardFMEnabled(obj)) {
                args.add("-injars");
                args.add(hashMap.get("jarPath").toString());
            }
        }
        args.add("-libraryjars");
        args.add(getProguardClasspath());
        args.add("-outjars");
        args.add(yq.proguardClassesPath);
        if (proguard.isDebugFilesEnabled()) {
            args.add("-printseeds");
            args.add(yq.proguardSeedsPath);
            args.add("-printusage");
            args.add(yq.proguardUsagePath);
            args.add("-printmapping");
            args.add(yq.proguardMappingPath);
        }
        LogUtil.d(TAG, "About to run ProGuard with these arguments: " + args);

        Configuration configuration = new Configuration();

        try {
            ConfigurationParser parser = new ConfigurationParser(args.toArray(new String[0]), System.getProperties());
            try {
                parser.parse(configuration);
            } finally {
                parser.close();
            }
        } catch (ParseException e) {
            throw new IOException(e);
        }

        try {
            new ProGuard(configuration).execute();
        } catch (Exception e) {
            throw new IOException(e);
        }

        LogUtil.d(TAG, "ProGuard took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
    }

    public void runStringfog() {
        try {
            StringFogMappingPrinter stringFogMappingPrinter = new StringFogMappingPrinter(new File(yq.binDirectoryPath,
                    "stringFogMapping.txt"));
            StringFogClassInjector stringFogClassInjector = new StringFogClassInjector(new String[0],
                    "UTF-8",
                    "com.github.megatronking.stringfog.xor.StringFogImpl",
                    "com.github.megatronking.stringfog.xor.StringFogImpl",
                    stringFogMappingPrinter);
            stringFogMappingPrinter.startMappingOutput();
            stringFogMappingPrinter.ouputInfo("UTF-8", "com.github.megatronking.stringfog.xor.StringFogImpl");
            stringFogClassInjector.doFog2ClassInDir(new File(yq.compiledClassesPath));
            KB.a(context, "stringfog/stringfog.zip", yq.compiledClassesPath);
        } catch (Exception e) {
            LogUtil.e("StringFog", "Failed to run StringFog", e);
        }
    }

    public void runZipalign(String inPath, String outPath) throws By {
        buildProfiler.start("Packaging");
        LogUtil.d(TAG, "About to zipalign " + inPath + " to " + outPath);
        long savedTimeMillis = System.currentTimeMillis();

        try (RandomAccessFile in = new RandomAccessFile(inPath, "r");
             FileOutputStream out = new FileOutputStream(outPath)) {
            ZipAlign.alignZip(in, out);
        } catch (IOException e) {
            throw new By("Couldn't run zipalign on " + inPath + " with output path " + outPath + ": " + Log.getStackTraceString(e));
        } catch (InvalidZipException e) {
            throw new By("Failed to zipalign due to the given zip being invalid: " + Log.getStackTraceString(e));
        }

        buildProfiler.stop("Packaging");
        LogUtil.d(TAG, "zipalign took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        buildProfiler.log("Project " + yq.sc_id);
    }

    public void setBuildAppBundle(boolean buildAppBundle) {
        this.buildAppBundle = buildAppBundle;
    }
}
