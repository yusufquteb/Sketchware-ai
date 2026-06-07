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
import pro.sketchware.compiler.IncrementalCompileCache;
import pro.sketchware.compiler.ECJCompilerClient;
import pro.sketchware.compiler.JavaCompileGraph;
import pro.sketchware.compiler.SourceOutputTracker;
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
    private ArrayList<File> dexesToAddButNotMerge = new ArrayList<>();

    private boolean forceFullJavaCompilation = false;
    private boolean compiledClassesPreparedForJointRebuild = false;

    /**
     * Timestamp keeping track of when compiling the project's resources started, needed for stats of how long compiling took.
     */
    private long timestampResourceCompilationStarted;

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
     * The files' sizes are compared, not content.
     *
     * @param fileInAssets The file in assets relative to assets/ in the APK
     * @param targetFile   The file on local storage
     * @return If the file in assets has been extracted
     */
    public static boolean hasFileChanged(String fileInAssets, String targetFile) {
        long length;
        File compareToFile = new File(targetFile);
        oB fileUtil = new oB();
        long lengthOfFileInAssets = fileUtil.a(SketchApplication.getContext(), fileInAssets);
        if (compareToFile.exists()) {
            length = compareToFile.length();
        } else {
            length = 0;
        }
        if (lengthOfFileInAssets == length) {
            return false;
        }

        /* Delete the file */
        fileUtil.a(compareToFile);
        /* Copy the file from assets to local storage */
        fileUtil.a(SketchApplication.getContext(), fileInAssets, targetFile);
        return true;
    }

    /**
     * Compile resources and log time needed.
     *
     * @throws Exception Thrown when anything goes wrong while compiling resources
     */
    public void compileResources() throws Exception {
        timestampResourceCompilationStarted = System.currentTimeMillis();
        // If the project has NOT enabled Material3 Expressive, normalise any
        // Widget.Material3Expressive.* style references before AAPT2 sees them.
        // Material3Expressive requires material:1.13.0+ which is not bundled; the
        // Material3 equivalents are functionally identical for layout/build purposes.
        com.besome.sketch.editor.manage.library.material3.Material3LibraryManager m3 =
                new com.besome.sketch.editor.manage.library.material3.Material3LibraryManager(yq.sc_id);
        if (!m3.isMaterial3ExpressiveEnabled()) {
            pro.sketchware.importer.AndroidStudioProjectImporter
                    .fixMaterial3ExpressiveStylesInDirStatic(new java.io.File(yq.resDirectoryPath));
        }
        ResourceCompiler compiler = new ResourceCompiler(
                this,
                aapt2Binary,
                buildAppBundle,
                progressReceiver);
        compiler.compile();
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

    public synchronized void prepareJointKotlinJavaFullRebuild(String reason) {
        cleanCompiledClassesDirectory();
        forceFullJavaCompilation = true;
        compiledClassesPreparedForJointRebuild = true;
        LogUtil.d(TAG, "Prepared compiled class outputs for a joint Kotlin/Java full rebuild. Reason: " + reason);
    }

    private synchronized boolean consumeForceFullJavaCompilationFlag() {
        boolean value = forceFullJavaCompilation;
        forceFullJavaCompilation = false;
        return value;
    }

    private synchronized boolean consumeCompiledClassesPreparedFlag() {
        boolean value = compiledClassesPreparedForJointRebuild;
        compiledClassesPreparedForJointRebuild = false;
        return value;
    }

    public String buildJavaCompilationEnvironmentFingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append("javaVersion=")
                .append(build_settings.getValue(BuildSettings.SETTING_JAVA_VERSION,
                        BuildSettings.SETTING_JAVA_VERSION_1_8))
                .append('\n');
        fingerprint.append("warnings=")
                .append(build_settings.getValue(BuildSettings.SETTING_NO_WARNINGS,
                        BuildSettings.SETTING_GENERIC_VALUE_TRUE))
                .append('\n');
        fingerprint.append("parallelEcj=")
                .append(build_settings.getValue(BuildSettings.SETTING_PARALLEL_ECJ,
                        ProjectSettings.SETTING_GENERIC_VALUE_FALSE))
                .append('\n');

        String classpath = getClasspath();
        if (!TextUtils.isEmpty(classpath)) {
            for (String part : classpath.split(":")) {
                if (TextUtils.isEmpty(part)
                        || yq.compiledClassesPath.equals(part)
                        || yq.compiledJavaClassesPath.equals(part)
                        || yq.compiledKotlinClassesPath.equals(part)) {
                    continue;
                }
                appendPathFingerprint(fingerprint, part);
            }
        }

        return fingerprint.toString();
    }

    private void appendPathFingerprint(StringBuilder fingerprint, String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        File file = new File(path);
        fingerprint.append(path);
        fingerprint.append('|');
        if (file.exists()) {
            fingerprint.append(file.length())
                    .append('|')
                    .append(file.lastModified());
        } else {
            fingerprint.append("missing");
        }
        fingerprint.append('\n');
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
        if (isModernJavaEnabled()) {
            return true;
        }
        String dexer = build_settings.getValue(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_DX);
        return dexer.equals(BuildSettings.SETTING_DEXER_D8) || dexer.equals(BuildSettings.SETTING_DEXER_R8);
    }

    public boolean isR8DexerEnabled() {
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
            long savedTimeMillis = System.currentTimeMillis();
            try {
                runR8Dexer();
                LogUtil.d(TAG, "R8 (dexer mode) took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            } catch (Exception e) {
                LogUtil.e(TAG, "R8 (dexer mode) failed to process .class files", e);
                throw e;
            }
        } else if (isD8Enabled()) {
            long savedTimeMillis = System.currentTimeMillis();
            try {
                DexCompiler.compileDexFiles(this);
                LogUtil.d(TAG, "D8 took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            } catch (Exception e) {
                LogUtil.e(TAG, "D8 failed to process .class files", e);
                throw e;
            }
        } else {
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
     */
    public void compileJavaCode() throws zy, IOException {
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

        List<String> fullCompileSources = collectExistingSourceInputs(
                normalizedGeneratedPath,
                yq.rJavaDirectoryPath,
                normalizedPathJava,
                normalizedPathBroadcast,
                normalizedPathService
        );

        IncrementalCompileCache compileCache = new IncrementalCompileCache(yq.sc_id, "java");
        IncrementalCompileCache.ChangeSet changeSet = compileCache.getChangeSetWithEnvironment(
                buildJavaCompilationEnvironmentFingerprint(),
                normalizedGeneratedPath,
                yq.rJavaDirectoryPath,
                normalizedPathJava,
                normalizedPathBroadcast,
                normalizedPathService
        );

        List<String> allJavaSourceFiles = changeSet.getCurrentSnapshot().keySet().stream()
                .filter(path -> path.endsWith(".java"))
                .sorted()
                .collect(Collectors.toList());
        File javaClassesDirectory = new File(yq.compiledJavaClassesPath);
        boolean hasCompiledClasses = javaClassesDirectory.exists() && javaClassesDirectory.isDirectory();
        boolean fullJavaCompilationRequested = consumeForceFullJavaCompilationFlag();
        boolean compiledClassesPrepared = consumeCompiledClassesPreparedFlag();

        if (changeSet.isEnvironmentChanged()) {
            LogUtil.d(TAG, "Forcing a full Java compilation because the compilation environment changed");
        }
        if (fullJavaCompilationRequested) {
            LogUtil.d(TAG, "Forcing a full Java compilation because another compiler stage requested a joint rebuild");
        }

        if (!changeSet.hasChanges() && hasCompiledClasses && !fullJavaCompilationRequested) {
            LogUtil.d(TAG, "Skipping Java compilation because no Java sources or compilation inputs changed");
            rebuildMergedCompiledClassesDirectory();
            return;
        }

        List<String> changedJavaSources = changeSet.getChangedOrAddedFilesWithExtension(".java");
        List<String> removedJavaSources = changeSet.getRemovedFilesWithExtension(".java");
        boolean hasRemovedJavaSources = !removedJavaSources.isEmpty();

        if (changedJavaSources.isEmpty()
                && !hasRemovedJavaSources
                && hasCompiledClasses
                && !changeSet.isEnvironmentChanged()
                && !fullJavaCompilationRequested) {
            LogUtil.d(TAG, "Skipping Java compilation because only non-Java sources changed");
            compileCache.save(changeSet);
            rebuildMergedCompiledClassesDirectory();
            return;
        }

        JavaCompileGraph compileGraph = new JavaCompileGraph(allJavaSourceFiles);
        List<String> impactedJavaSources = compileGraph.collectImpactedSources(changedJavaSources);
        if (impactedJavaSources.isEmpty()) {
            impactedJavaSources = new ArrayList<>(changedJavaSources);
        }
        Collections.sort(impactedJavaSources);

        boolean incrementalCompile = hasCompiledClasses
                && !compiledClassesPrepared
                && !fullJavaCompilationRequested
                && !changeSet.isEnvironmentChanged()
                && !hasRemovedJavaSources
                && !impactedJavaSources.isEmpty()
                && impactedJavaSources.size() < allJavaSourceFiles.size();

        File rJavaFileWithoutPackage = new File(yq.rJavaDirectoryPath, "R.java");
        if (rJavaFileWithoutPackage.exists() && !rJavaFileWithoutPackage.delete()) {
            LogUtil.w(TAG, "Failed to delete file " + rJavaFileWithoutPackage.getAbsolutePath());
        }

        SourceOutputTracker javaOutputTracker = new SourceOutputTracker(yq.sc_id, "java");
        javaOutputTracker.deleteOutputsForSources(removedJavaSources, javaClassesDirectory);
        javaOutputTracker.removeSources(removedJavaSources);

        EcjCompileResult result;
        List<String> successfullyCompiledJavaSources;
        if (incrementalCompile) {
            successfullyCompiledJavaSources = new ArrayList<>(impactedJavaSources);
            javaOutputTracker.deleteOutputsForSources(impactedJavaSources, javaClassesDirectory);

            List<List<String>> compileGroups = compileGraph.partitionIndependentSourceGroups(impactedJavaSources);
            boolean canRunInParallel = isParallelEcjEnabled() && compileGroups.size() > 1;
            LogUtil.d(TAG, (canRunInParallel ? "Running safe parallel incremental Java compilation" : "Running incremental Java compilation")
                    + " for " + impactedJavaSources.size() + " impacted source file(s): " + impactedJavaSources);
            result = canRunInParallel
                    ? runParallelEcjCompile(compileGroups, javaClassesDirectory)
                    : runEcjCompile(buildEcjArguments(impactedJavaSources, yq.compiledJavaClassesPath));
            if (!result.success) {
                LogUtil.w(TAG, "Incremental Java compilation failed. Falling back to a clean full recompilation. Error: "
                        + result.getBestErrorMessage());
            }
        } else {
            result = null;
            successfullyCompiledJavaSources = new ArrayList<>(allJavaSourceFiles);
        }

        if (result == null || !result.success) {
            cleanJavaAndMergedOutputs();
            if (compiledClassesPrepared) {
                FileUtil.makeDir(yq.compiledKotlinClassesPath);
                LogUtil.d(TAG, "Reusing Kotlin classes directory that was already prepared by Kotlin compilation");
            }
            List<List<String>> fullCompileGroups = compileGraph.partitionIndependentSourceGroups(allJavaSourceFiles);
            boolean canRunFullInParallel = isParallelEcjEnabled() && fullCompileGroups.size() > 1;
            if (canRunFullInParallel) {
                LogUtil.d(TAG, "Running safe parallel full Java compilation with " + fullCompileGroups.size()
                        + " groups for " + allJavaSourceFiles.size() + " source file(s)");
                result = runParallelEcjCompile(fullCompileGroups, javaClassesDirectory);
            } else {
                LogUtil.d(TAG, "Running full Java compilation for " + fullCompileSources.size() + " source roots/files");
                result = runEcjCompile(buildEcjArguments(fullCompileSources, yq.compiledJavaClassesPath));
            }
            successfullyCompiledJavaSources = new ArrayList<>(allJavaSourceFiles);
        }

        LogUtil.d(TAG, "System.out of Eclipse compiler: " + result.output);
        if (result.success) {
            LogUtil.d(TAG, "System.err of Eclipse compiler: " + result.errors);
            compileCache.save(changeSet);
            javaOutputTracker.refreshOutputsForSources(successfullyCompiledJavaSources, javaClassesDirectory);
            rebuildMergedCompiledClassesDirectory();
            LogUtil.d(TAG, "Compiling Java files took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        } else {
            LogUtil.e(TAG, "Failed to compile Java files");
            throw new zy(result.getBestErrorMessage());
        }
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

    private void cleanCompiledClassesDirectory() {
        FileUtil.deleteFile(yq.compiledClassesPath);
        FileUtil.deleteFile(yq.compiledJavaClassesPath);
        FileUtil.deleteFile(yq.compiledKotlinClassesPath);
        FileUtil.makeDir(yq.compiledClassesPath);
        FileUtil.makeDir(yq.compiledJavaClassesPath);
        FileUtil.makeDir(yq.compiledKotlinClassesPath);
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
     * Either merges DEX files to as few as possible, or adds list of DEX files to add to the APK to
     * {@link #dexesToAddButNotMerge}.
     * <p>
     * Will merge DEX files if either the project's minSdkVersion is lower than 21, or if {@link jq#isDebugBuild}
     * of {@link yq#N} in {@link #yq} is false.
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

        if (settings.getMinSdkVersion() < 21 || !yq.N.isDebugBuild) {
            dexLibraries(new File(yq.binDirectoryPath), dexes);
            LogUtil.d(TAG, "Merging DEX files took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        } else {
            dexesToAddButNotMerge = dexes;
            LogUtil.d(TAG, "Skipped merging DEX files due to debug build with minSdkVersion >= 21");
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
            // OneSignal - requires Firebase + AppCompat
            if (yq.N.g && extraSettings.isUseOneSignal()) {
                builtInLibraryManager.addLibrary(BuiltInLibraries.ONESIGNAL_CORE);
                builtInLibraryManager.addLibrary(BuiltInLibraries.ONESIGNAL_NOTIFICATIONS);
                builtInLibraryManager.addLibrary(BuiltInLibraries.ONESIGNAL_IN_APP_MESSAGES);
                builtInLibraryManager.addLibrary(BuiltInLibraries.ONESIGNAL_LOCATION);
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

        LogUtil.d(TAG, "zipalign took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
    }

    public void setBuildAppBundle(boolean buildAppBundle) {
        this.buildAppBundle = buildAppBundle;
    }
}
