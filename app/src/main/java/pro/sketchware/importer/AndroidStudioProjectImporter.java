package pro.sketchware.importer;

import static mod.hey.studios.util.ProjectFile.COLOR_ACCENT;
import static mod.hey.studios.util.ProjectFile.COLOR_CONTROL_HIGHLIGHT;
import static mod.hey.studios.util.ProjectFile.COLOR_CONTROL_NORMAL;
import static mod.hey.studios.util.ProjectFile.COLOR_PRIMARY;
import static mod.hey.studios.util.ProjectFile.COLOR_PRIMARY_DARK;
import static mod.hey.studios.util.ProjectFile.getDefaultColor;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import a.a.a.GB;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.oB;
import a.a.a.wq;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.editor.manage.library.EnableBuiltInLibrariesActivity;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.manifest.ProjectManifestManager;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class AndroidStudioProjectImporter {
    private static final String TAG = "ASProjectImporter";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final Pattern STRING_ASSIGNMENT = Pattern.compile("(?m)^[\\t ]*([A-Za-z_][A-Za-z0-9_]*)[\\t ]*=[\\t ]*['\"]([^'\"]+)['\"]");
    private static final Pattern PROPERTY_ASSIGNMENT = Pattern.compile("(?m)^[\\t ]*([A-Za-z_][A-Za-z0-9_]*)[\\t ]*[=:][\\t ]*['\"]?([^\\n'\"]+)['\"]?");
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile("(?m)^[\\t ]*(implementation|api|compileOnly|runtimeOnly|kapt|ksp)\\s*(?:\\(|\\s)\\s*['\"]([^:'\"\\s]+):([^:'\"\\s]+):([^'\")\\s]+)['\"]");
    private static final Pattern LAYOUT_REFERENCE_PATTERN = Pattern.compile("R\\.layout\\.([A-Za-z0-9_]+)");
    private static final Pattern SET_CONTENT_VIEW_PATTERN = Pattern.compile("setContentView\\s*\\(\\s*R\\.layout\\.([A-Za-z0-9_]+)\\s*\\)");
    private static final Pattern INFLATE_PATTERN = Pattern.compile("inflate\\s*\\(\\s*R\\.layout\\.([A-Za-z0-9_]+)\\s*\\)");
    private static final Pattern JAVA_RESOURCE_REFERENCE_PATTERN = Pattern.compile("\\bR\\.([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z0-9_]+)\\b");
    private static final Pattern XML_RESOURCE_REFERENCE_PATTERN = Pattern.compile("@\\+?(?:(?:[A-Za-z0-9_.]+):)?([A-Za-z_][A-Za-z0-9_]*)/([A-Za-z0-9_]+)");
    private static final Pattern PACKAGE_DECL_PATTERN = Pattern.compile("(?m)^[\\t ]*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;?");
    private static final Pattern JAVA_CLASS_NAME_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern JAVA_ACTIVITY_EXTENDS_PATTERN = Pattern.compile("\\bclass\\s+[A-Za-z_][A-Za-z0-9_]*\\s+extends\\s+[A-Za-z0-9_$.]*Activity\\b");
    private static final Pattern KOTLIN_ACTIVITY_EXTENDS_PATTERN = Pattern.compile("\\bclass\\s+[A-Za-z_][A-Za-z0-9_]*\\s*:\\s*[A-Za-z0-9_$.]*Activity\\s*(?:\\(|\\{)");
    private static final Pattern DATABINDING_SET_CONTENT_VIEW_PATTERN = Pattern.compile("DataBindingUtil\\s*\\.\\s*setContentView\\s*\\(\\s*[^,]+,\\s*R\\.layout\\.([A-Za-z0-9_]+)\\s*\\)");
    private static final Pattern DATABINDING_INFLATE_PATTERN = Pattern.compile("DataBindingUtil\\s*\\.\\s*inflate\\s*\\([^,]+,\\s*R\\.layout\\.([A-Za-z0-9_]+)\\s*,");
    private static final Pattern VIEWBINDING_INFLATE_PATTERN = Pattern.compile("\\b([A-Za-z0-9_]+)Binding\\s*\\.\\s*inflate\\s*\\(");
    private static final Pattern ACTIVITY_THEME_PATTERN = Pattern.compile("Theme\\.(Material3|MaterialComponents|AppCompat)([^\\n]*)");
    private static final Pattern CATALOG_ACCESSOR_PATTERN = Pattern.compile(
            "(?m)^[\\t ]*(implementation|api|runtimeOnly|kapt|ksp)\\s*(?:\\(|\\s)\\s*libs\\.([A-Za-z0-9.]+)\\s*\\)?");
    private static final Pattern BOM_DEPENDENCY_PATTERN = Pattern.compile(
            "(?m)^[\\t ]*(implementation|api)\\s*(?:\\(|\\s)\\s*platform\\s*\\(\\s*['\"]([^:'\"\\s]+):([^:'\"\\s]+):([^'\")\\s]+)['\"]\\s*\\)");
    private static final Pattern GRADLE_VARIABLE_PATTERN = Pattern.compile(
            "(?m)^[\\t ]*(?:def|val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Map<List<String>, Pattern> NUMERIC_PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long MAX_EXTRACTED_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_EXTRACTED_FILES = 20000;
    private static final long DEPENDENCY_RESOLVE_TIMEOUT_MS = 120_000L;
    private static final long IMPORT_OVERALL_TIMEOUT_MS = 20 * 60 * 1000L;
    private static final Set<String> TEST_SOURCE_SET_NAMES = new HashSet<>(Arrays.asList(
            "test",
            "androidTest",
            "androidtest",
            "benchmark",
            "testFixtures"
    ));

    private final Context context;
    private final Gson gson = new Gson();
    private final FilePathUtil filePathUtil = new FilePathUtil();

    private ImportProgressListener progressListener;

    public AndroidStudioProjectImporter(Context context) {
        this.context = context.getApplicationContext();
    }

    public AndroidStudioProjectImporter setProgressListener(ImportProgressListener progressListener) {
        this.progressListener = progressListener;
        return this;
    }

    private void notifyProgress(String stage) {
        if (TextUtils.isEmpty(stage)) {
            return;
        }
        notifyProgress(new ImportProgress("Importing project", stage, 0, 0, true, null));
    }

    private void notifyProgress(String title, String detail, int currentStep, int totalSteps, boolean indeterminate, String statusLine) {
        notifyProgress(new ImportProgress(title, detail, currentStep, totalSteps, indeterminate, statusLine));
    }

    private void notifyProgress(ImportProgress progress) {
        if (progressListener != null && progress != null) {
            progressListener.onProgress(progress);
        }
    }


    public ImportResult importFromZipUri(Uri uri) throws Exception {
        File tempZip = new File(context.getCacheDir(), "import-" + System.currentTimeMillis() + ".zip");
        try {
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    throw new IOException("Unable to open selected file");
                }
                copyStreamToFile(inputStream, tempZip);
            }
            return importFromZipFile(tempZip, "android_studio_zip", null);
        } finally {
            deleteQuietly(tempZip);
        }
    }

    public ImportResult importFromGitHub(String repoUrl, String branch, String token) throws Exception {
        GitHubRepoSpec repoSpec = GitHubRepoSpec.parse(repoUrl, branch);
        notifyProgress("Resolving GitHub repository...");
        if (TextUtils.isEmpty(repoSpec.branch)) {
            repoSpec.branch = fetchDefaultBranch(repoSpec, token);
        }
        File archive = new File(context.getCacheDir(), repoSpec.repo + "-" + System.currentTimeMillis() + ".zip");
        try {
            notifyProgress("Downloading GitHub archive for " + repoSpec.owner + "/" + repoSpec.repo + "#" + repoSpec.branch + "...");
            downloadGitHubArchive(repoSpec, token, archive);
            ImportResult result = importFromZipFile(archive, "github", repoSpec.repo);
            result.sourceLabel = repoSpec.owner + "/" + repoSpec.repo + "#" + repoSpec.branch;
            return result;
        } finally {
            deleteQuietly(archive);
        }
    }

    public ImportResult importFromZipFile(File zipFile, String sourceType, String preferredProjectName) throws Exception {
        notifyProgress("Extracting archive...");
        File extractDir = new File(context.getCacheDir(), "import-extracted-" + System.currentTimeMillis());
        try {
            safeExtract(zipFile, extractDir);
            notifyProgress("Analyzing Gradle modules and manifests...");
            DetectedProject detectedProject = detectProject(extractDir);
            if (detectedProject != null) {
                detectedProject.archiveLabel = zipFile.getName();
            }
            if (detectedProject == null) {
                throw new IOException("No supported Android application module was found in the selected archive");
            }
            if (detectedProject.roundTripMetadataJson != null && detectedProject.roundTripMetadataJson.isFile()) {
                return restoreRoundTripProject(detectedProject, sourceType, preferredProjectName);
            }
            return importGenericAndroidProject(detectedProject, sourceType, preferredProjectName);
        } finally {
            deleteQuietly(extractDir);
        }
    }

    public ImportResult importFromFolder(File projectDir, String sourceType, String preferredProjectName) throws Exception {
        if (projectDir == null || !projectDir.isDirectory()) {
            throw new IOException("Selected folder does not exist or is not a directory");
        }
        notifyProgress("Analyzing project folder...");
        DetectedProject detectedProject = detectProject(projectDir);
        if (detectedProject != null) {
            detectedProject.archiveLabel = projectDir.getName();
        }
        if (detectedProject == null) {
            throw new IOException("No supported Android application module was found in the selected folder");
        }
        if (detectedProject.roundTripMetadataJson != null && detectedProject.roundTripMetadataJson.isFile()) {
            return restoreRoundTripProject(detectedProject, sourceType, preferredProjectName);
        }
        return importGenericAndroidProject(detectedProject, sourceType, preferredProjectName);
    }

    private void deleteQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            FileUtil.deleteFile(file.getAbsolutePath());
        } catch (Exception exception) {
            Log.w(TAG, "Failed to clean up temporary import file: " + file.getAbsolutePath(), exception);
        }
    }

    public static void writeRoundTripMetadata(String scId, HashMap<String, Object> metadata, String androidStudioProjectRoot) {
        try {
            String skproDir = androidStudioProjectRoot + File.separator + ".skpro";
            String snapshotDir = skproDir + File.separator + "data_snapshot";
            FileUtil.deleteFile(skproDir);
            FileUtil.makeDir(snapshotDir);
            File dataDir = new File(wq.b(scId));
            if (dataDir.exists()) {
                FileUtil.copyDirectory(dataDir, new File(snapshotDir, "data"));
            }
            FileUtil.writeFile(skproDir + File.separator + "project_metadata.json", new Gson().toJson(metadata));
        } catch (Exception e) {
            Log.e(TAG, "Failed to write round-trip metadata", e);
        }
    }

    private ImportResult restoreRoundTripProject(DetectedProject detectedProject, String sourceType, String preferredProjectName) throws Exception {
        String scId = lC.b();
        String metadataJson = FileUtil.readFile(detectedProject.roundTripMetadataJson.getAbsolutePath());
        HashMap<String, Object> metadata = gson.fromJson(metadataJson, new TypeToken<HashMap<String, Object>>() {
        }.getType());
        if (metadata == null) {
            throw new IOException("Invalid round-trip metadata");
        }
        metadata.put("sc_id", scId);
        if (!TextUtils.isEmpty(preferredProjectName)) {
            metadata.put("my_ws_name", sanitizeProjectName(preferredProjectName));
        }
        lC.a(scId, metadata);
        wq.a(context, scId);
        new oB().b(wq.b(scId));
        if (detectedProject.roundTripDataDir == null || !detectedProject.roundTripDataDir.isDirectory()) {
            throw new IOException("Round-trip data snapshot is missing");
        }
        FileUtil.copyDirectory(detectedProject.roundTripDataDir, new File(wq.b(scId)));
        ImportResult result = new ImportResult();
        result.scId = scId;
        result.projectName = String.valueOf(metadata.get("my_ws_name"));
        result.sourceType = sourceType;
        result.sourceLabel = sourceType.equals("android_studio_zip") ? detectedProject.archiveLabel : detectedProject.rootDirectory.getName();
        result.visualScreens.add("Restored Sketchware project snapshot");
        result.summary = "Round-trip import completed";
        writeImportMetadata(scId, sourceType, true);
        writeImportReport(result);
        return result;
    }

    private ImportResult importGenericAndroidProject(DetectedProject detectedProject, String sourceType, String preferredProjectName) throws Exception {
        GradleSummary gradle = parseGradle(detectedProject);
        notifyProgress("Parsing manifest and project settings...");
        ManifestSummary manifest = parseManifest(detectedProject);

        String projectName = chooseProjectName(preferredProjectName, manifest.applicationLabel, detectedProject.rootDirectory.getName());
        String applicationId = chooseApplicationId(gradle.applicationId, gradle.namespace, manifest.packageName, projectName);
        if (TextUtils.isEmpty(manifest.packageName)) {
            manifest.packageName = applicationId;
        }
        String versionCode = gradle.versionCode == null ? "1" : gradle.versionCode;
        String versionName = gradle.versionName == null ? "1.0" : gradle.versionName;
        int minSdk = gradle.minSdk > 0 ? gradle.minSdk : 21;
        int targetSdk = gradle.targetSdk > 0 ? gradle.targetSdk : 36;

        String scId = lC.b();
        HashMap<String, Object> metadata = createProjectMetadata(scId, projectName, applicationId, manifest.applicationLabel, versionCode, versionName);
        lC.a(scId, metadata);
        wq.a(context, scId);
        new oB().b(wq.b(scId));

        ProjectSettings settings = new ProjectSettings(scId);
        settings.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        if (gradle.viewBindingDetected) {
            settings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        }
        settings.setValue(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, String.valueOf(minSdk));
        settings.setValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(targetSdk));

        ensureProjectDirectories(scId);

        int dependencyWorkItems = Math.max(1, gradle.dependencies.size());
        int totalProgressSteps = dependencyWorkItems + 4;
        int normalizeStep = dependencyWorkItems + 2;
        int registerStep = dependencyWorkItems + 3;
        int finalizeStep = dependencyWorkItems + 4;

        notifyProgress("Copying project contents",
                "Copying source sets, resources, assets, native libraries, and checked-in artifacts into the Sketchware workspace.",
                1, totalProgressSteps, false, "Preparing imported project structure");
        copySourceTree(detectedProject.sourceRoots, new File(filePathUtil.getPathJava(scId)));
        copyResources(detectedProject.resDirectories, new File(filePathUtil.getPathResource(scId)));
        boolean expressiveDetected = detectExpressiveUsage(detectedProject.resDirectories);
        if (!expressiveDetected) {
            fixMaterial3ExpressiveStylesInDir(new File(filePathUtil.getPathResource(scId)));
        }
        copyDirectories(detectedProject.assetsDirectories, new File(filePathUtil.getPathAssets(scId)));
        copyDirectories(detectedProject.jniLibsDirectories, new File(filePathUtil.getPathNativelibs(scId)));
        importLocalJarsAndAars(detectedProject.libsDirectories, scId);

        List<String> allDependencies = new ArrayList<>(gradle.dependencies);
        detectImplicitResourceDependencies(detectedProject.resDirectories, allDependencies);
        DependencyResolutionReport dependencyReport = resolveAndRegisterDependencies(scId, allDependencies, totalProgressSteps, 2);

        if (!manifest.permissions.isEmpty()) {
            FileUtil.writeFile(filePathUtil.getPathPermission(scId), gson.toJson(manifest.permissions));
        }

        if (manifest.rawXml != null) {
            String normalizedRawManifest = ProjectManifestManager.ensureManifestPackageAttribute(manifest.rawXml, applicationId);
            ProjectManifestManager.ensureRawManifestSeeded(scId, normalizedRawManifest);
            ProjectManifestManager.setMode(scId, ProjectManifestManager.MODE_RAW);
        }

        ImportResult result = new ImportResult();
        result.scId = scId;
        result.projectName = projectName;
        result.sourceType = sourceType;
        result.sourceLabel = sourceType.equals("android_studio_zip") ? detectedProject.archiveLabel : detectedProject.rootDirectory.getName();
        result.importedDependencies.addAll(allDependencies);
        result.satisfiedByBundledDependencies.addAll(dependencyReport.satisfiedByBundled);
        result.reusedLocalDependencies.addAll(dependencyReport.reusedLocal);
        result.downloadedDependencies.addAll(dependencyReport.downloaded);
        result.manualDependencyActions.addAll(dependencyReport.unresolved);
        result.unsupportedFeatures.addAll(gradle.warnings);
        result.warnings.addAll(dependencyReport.warnings);

        if (new File(detectedProject.rootDirectory, ".gitmodules").isFile()) {
            result.warnings.add("Git submodules detected (.gitmodules). Submodule contents are not included in the downloaded archive and will be absent from the imported project. Clone the repository with \"git clone --recursive\" and import from the local folder instead.");
        }

        notifyProgress("Normalizing imported resources",
                "Reconciling launcher icons, resource qualifiers, and raw manifest preservation for the imported project.",
                normalizeStep, totalProgressSteps, false, "Optimizing imported resources");
        normalizeImportedProjectResources(scId, manifest, new File(filePathUtil.getPathJava(scId)),
                new File(filePathUtil.getPathResource(scId)), result);
        notifyProgress("Registering screens and custom views",
                "Linking imported activities, layouts, and custom views into Sketchware's project model.",
                registerStep, totalProgressSteps, false, "Registering visual surfaces");
        materializeActivitiesAndCustomViews(scId, manifest, detectedProject, result);
        notifyProgress("Finalizing imported project",
                "Applying detected library settings, writing import reports, and preparing the project for editing.",
                finalizeStep, totalProgressSteps, false, "Saving import report and library state");
        applyDetectedThemeAndLibraryState(scId, gradle, manifest, detectedProject, result, expressiveDetected);
        writeImportedComponentIndexes(scId, manifest);
        writeImportMetadata(scId, sourceType, false);
        writeImportReport(result);
        result.summary = buildSummary(result, manifest);
        return result;
    }

    private void materializeActivitiesAndCustomViews(String scId, ManifestSummary manifest, DetectedProject detectedProject, ImportResult result) {
        Set<String> usedScreenNames = new HashSet<>();
        Set<String> importedActivityLayouts = new HashSet<>();
        jC.b(scId);
        jC.a(scId);
        InjectRootLayoutManager rootLayoutManager = new InjectRootLayoutManager(scId);

        for (ManifestActivity manifestActivity : manifest.activities) {
            File sourceFile = findSourceFileForClass(detectedProject.sourceRoots, manifestActivity.fullyQualifiedName);
            String simpleClassName = manifestActivity.fullyQualifiedName.substring(manifestActivity.fullyQualifiedName.lastIndexOf('.') + 1);
            String screenName = uniquifyScreenName(usedScreenNames, toSketchwareScreenName(simpleClassName));
            ProjectFileBean fileBean = new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY, screenName);
            jC.b(scId).a(fileBean);
            if (manifestActivity.launcher) {
                result.visualScreens.add(screenName + " (launcher)");
            } else {
                result.visualScreens.add(screenName);
            }

            String layoutName = null;
            if (sourceFile != null) {
                String source = FileUtil.readFile(sourceFile.getAbsolutePath());
                layoutName = detectLayoutName(source);
            }
            if (layoutName == null) {
                layoutName = findBestMatchingLayoutName(detectedProject.layoutDirectories, screenName, simpleClassName);
            }

            if (!TextUtils.isEmpty(layoutName)) {
                File originalLayout = findLayoutFile(detectedProject.layoutDirectories, layoutName);
                if (originalLayout != null && originalLayout.exists()) {
                    importedActivityLayouts.add(layoutName);
                    importLayoutForScreen(scId, fileBean, layoutName, originalLayout, rootLayoutManager, result);
                    // Also track the visual companion name so materializeCustomViews won't re-register it
                    if (!fileBean.fileName.equals(layoutName)) {
                        importedActivityLayouts.add(fileBean.fileName);
                    }
                }
            } else {
                result.codeOnlyFiles.add(simpleClassName + " (no XML layout was detected)");
            }
        }

        Set<String> registeredActivityClasses = new LinkedHashSet<>();
        for (ManifestActivity manifestActivity : manifest.activities) {
            registeredActivityClasses.add(manifestActivity.fullyQualifiedName);
        }
        for (DiscoveredActivity discoveredActivity : discoverSourceActivities(detectedProject.sourceRoots)) {
            if (registeredActivityClasses.contains(discoveredActivity.fullyQualifiedName)) {
                continue;
            }
            String screenName = uniquifyScreenName(usedScreenNames, toSketchwareScreenName(discoveredActivity.simpleClassName));
            ProjectFileBean fileBean = new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY, screenName);
            jC.b(scId).a(fileBean);
            result.visualScreens.add(screenName + " (source-discovered)");
            if (!TextUtils.isEmpty(discoveredActivity.layoutName)) {
                File originalLayout = findLayoutFile(detectedProject.layoutDirectories, discoveredActivity.layoutName);
                if (originalLayout != null && originalLayout.exists()) {
                    importedActivityLayouts.add(discoveredActivity.layoutName);
                    importLayoutForScreen(scId, fileBean, discoveredActivity.layoutName, originalLayout, rootLayoutManager, result);
                    // Also track the visual companion name so materializeCustomViews won't re-register it
                    if (!fileBean.fileName.equals(discoveredActivity.layoutName)) {
                        importedActivityLayouts.add(fileBean.fileName);
                    }
                } else {
                    result.codeOnlyFiles.add(discoveredActivity.simpleClassName + " (layout @layout/" + discoveredActivity.layoutName + " was referenced but not found)");
                }
            } else {
                result.codeOnlyFiles.add(discoveredActivity.simpleClassName + " (no XML layout was detected)");
            }
        }

        materializeCustomViews(scId, detectedProject, usedScreenNames, importedActivityLayouts, rootLayoutManager, result);
        jC.b(scId).j();
        jC.b(scId).l();
    }

    private void importLayoutForScreen(String scId, ProjectFileBean fileBean, String originalLayoutName,
                                       File originalLayoutFile, InjectRootLayoutManager rootLayoutManager,
                                       ImportResult result) {
        String layoutRoot = filePathUtil.getPathResource(scId) + File.separator + "layout";
        String originalTarget = layoutRoot + File.separator + originalLayoutName + ".xml";
        FileUtil.copyFile(originalLayoutFile.getAbsolutePath(), originalTarget);

        String visualXmlName = fileBean.getXmlName();
        String visualTarget = layoutRoot + File.separator + visualXmlName;
        if (!visualXmlName.equals(originalLayoutName + ".xml")) {
            FileUtil.copyFile(originalLayoutFile.getAbsolutePath(), visualTarget);
            result.warnings.add(fileBean.fileName + ": created visual companion layout " + visualXmlName + " from " + originalLayoutName + ".xml");
        }

        try {
            String visualContent = FileUtil.readFile(visualTarget);
            ViewBeanParser parser = new ViewBeanParser(visualContent);
            parser.setSkipRoot(true);
            ArrayList<ViewBean> parsedLayout = parser.parse();
            if (parser.getRootAttributes() != null) {
                rootLayoutManager.set(visualXmlName, InjectRootLayoutManager.toRoot(parser.getRootAttributes()));
            }
            jC.a(scId).c.put(visualXmlName, parsedLayout);
        } catch (Exception e) {
            result.warnings.add(fileBean.fileName + ": layout imported as code-only because visual parsing failed (" + e.getMessage() + ")");
        }
    }

    private void materializeCustomViews(String scId, DetectedProject detectedProject, Set<String> usedScreenNames,
                                        Set<String> importedActivityLayouts, InjectRootLayoutManager rootLayoutManager,
                                        ImportResult result) {
        for (String layoutName : collectLayoutNames(detectedProject.layoutDirectories)) {
            if (usedScreenNames.contains(layoutName) || importedActivityLayouts.contains(layoutName)) {
                continue;
            }

            File layoutFile = findLayoutFile(detectedProject.layoutDirectories, layoutName);
            if (layoutFile == null || !layoutFile.isFile()) {
                continue;
            }

            ProjectFileBean fileBean = new ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW, layoutName);
            jC.b(scId).a(fileBean);
            result.visualCustomViews.add(layoutName);
            importLayoutForScreen(scId, fileBean, layoutName, layoutFile, rootLayoutManager, result);
        }
    }

    private Set<String> collectLayoutNames(List<File> layoutDirectories) {
        Set<String> layoutNames = new LinkedHashSet<>();
        for (File layoutDirectory : layoutDirectories) {
            if (layoutDirectory == null || !layoutDirectory.isDirectory()) {
                continue;
            }
            File[] layoutFiles = layoutDirectory.listFiles();
            if (layoutFiles == null) {
                continue;
            }
            for (File layoutFile : layoutFiles) {
                if (layoutFile.isFile() && layoutFile.getName().endsWith(".xml")) {
                    layoutNames.add(FileUtil.getFileNameNoExtension(layoutFile.getName()));
                }
            }
        }
        return layoutNames;
    }

    private File findLayoutFile(List<File> layoutDirectories, String layoutName) {
        for (int i = layoutDirectories.size() - 1; i >= 0; i--) {
            File layoutDirectory = layoutDirectories.get(i);
            if (layoutDirectory == null || !layoutDirectory.isDirectory()) {
                continue;
            }
            File candidate = new File(layoutDirectory, layoutName + ".xml");
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private void normalizeImportedProjectResources(String scId, ManifestSummary manifest, File javaRoot,
                                                   File resourceRoot, ImportResult result) throws IOException {
        Set<String> referencedResources = collectReferencedResources(javaRoot, resourceRoot, manifest);
        Map<String, List<File>> availableResources = collectResourcesByTypeAndName(resourceRoot);
        mirrorCompatibleResourceTypes(resourceRoot, referencedResources, availableResources, result);
        importAppIcon(scId, manifest, resourceRoot, availableResources, result);
    }

    private Set<String> collectReferencedResources(File javaRoot, File resourceRoot, ManifestSummary manifest) {
        Set<String> references = new LinkedHashSet<>();
        if (javaRoot != null && javaRoot.isDirectory()) {
            for (File sourceFile : collectFilesRecursively(javaRoot)) {
                if (!sourceFile.isFile()) {
                    continue;
                }
                String name = sourceFile.getName().toLowerCase(Locale.US);
                if (name.endsWith(".java") || name.endsWith(".kt")) {
                    collectResourceReferences(FileUtil.readFile(sourceFile.getAbsolutePath()), references);
                }
            }
        }
        if (resourceRoot != null && resourceRoot.isDirectory()) {
            for (File resFile : collectFilesRecursively(resourceRoot)) {
                if (resFile.isFile() && resFile.getName().toLowerCase(Locale.US).endsWith(".xml")) {
                    collectResourceReferences(FileUtil.readFile(resFile.getAbsolutePath()), references);
                }
            }
        }
        if (manifest != null) {
            if (!TextUtils.isEmpty(manifest.applicationIconRef)) {
                references.add(manifest.applicationIconRef);
            }
            if (!TextUtils.isEmpty(manifest.roundIconRef)) {
                references.add(manifest.roundIconRef);
            }
            if (!TextUtils.isEmpty(manifest.rawXml)) {
                collectResourceReferences(manifest.rawXml, references);
            }
        }
        return references;
    }

    private void collectResourceReferences(String content, Set<String> out) {
        if (TextUtils.isEmpty(content)) {
            return;
        }
        Matcher javaMatcher = JAVA_RESOURCE_REFERENCE_PATTERN.matcher(content);
        while (javaMatcher.find()) {
            out.add(javaMatcher.group(1) + ":" + javaMatcher.group(2));
        }
        Matcher xmlMatcher = XML_RESOURCE_REFERENCE_PATTERN.matcher(content);
        while (xmlMatcher.find()) {
            out.add(xmlMatcher.group(1) + ":" + xmlMatcher.group(2));
        }
    }

    private Map<String, List<File>> collectResourcesByTypeAndName(File resourceRoot) {
        Map<String, List<File>> resources = new LinkedHashMap<>();
        File[] resFolders = resourceRoot.listFiles();
        if (resFolders == null) {
            return resources;
        }

        for (File resFolder : resFolders) {
            if (!resFolder.isDirectory()) {
                continue;
            }
            String baseType = getBaseResourceType(resFolder.getName());
            File[] children = resFolder.listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (!child.isFile()) {
                    continue;
                }
                String resourceName = getResourceName(child.getName());
                if (resourceName == null) {
                    continue;
                }
                resources.computeIfAbsent(baseType + ":" + resourceName, unused -> new ArrayList<>()).add(child);
            }
        }
        return resources;
    }

    private void mirrorCompatibleResourceTypes(File resourceRoot, Set<String> referencedResources,
                                               Map<String, List<File>> availableResources,
                                               ImportResult result) throws IOException {
        Set<String> mirroredKeys = new LinkedHashSet<>();
        for (String referencedResource : referencedResources) {
            String[] parts = referencedResource.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String referenceType = parts[0];
            String referenceName = parts[1];
            String resourceKey = referenceType + ":" + referenceName;
            if (availableResources.containsKey(resourceKey)) {
                continue;
            }

            String compatibleType = getCompatibleResourceType(referenceType);
            if (compatibleType == null) {
                continue;
            }
            String compatibleKey = compatibleType + ":" + referenceName;
            List<File> compatibleFiles = availableResources.get(compatibleKey);
            if (compatibleFiles == null || compatibleFiles.isEmpty()) {
                continue;
            }

            for (File compatibleFile : compatibleFiles) {
                File parent = compatibleFile.getParentFile();
                if (parent == null) {
                    continue;
                }
                String aliasDirectoryName = compatibleFile.getParentFile().getName()
                        .replaceFirst("^" + Pattern.quote(compatibleType), referenceType);
                File aliasDirectory = new File(resourceRoot, aliasDirectoryName);
                File aliasFile = new File(aliasDirectory, compatibleFile.getName());
                if (!aliasFile.exists()) {
                    aliasDirectory.mkdirs();
                    FileUtil.copyFile(compatibleFile.getAbsolutePath(), aliasFile.getAbsolutePath());
                }
                availableResources.computeIfAbsent(resourceKey, unused -> new ArrayList<>()).add(aliasFile);
            }

            if (mirroredKeys.add(resourceKey)) {
                result.warnings.add("Mirrored @" + compatibleType + "/" + referenceName + " into @" + referenceType + "/" + referenceName + " for compatibility.");
            }
        }
    }

    private String getCompatibleResourceType(String referenceType) {
        if ("drawable".equals(referenceType)) {
            return "mipmap";
        }
        if ("mipmap".equals(referenceType)) {
            return "drawable";
        }
        return null;
    }

    private void importAppIcon(String scId, ManifestSummary manifest, File resourceRoot,
                               Map<String, List<File>> availableResources, ImportResult result) {
        String iconReference = !TextUtils.isEmpty(manifest.applicationIconRef) ? manifest.applicationIconRef : manifest.roundIconRef;
        if (TextUtils.isEmpty(iconReference)) {
            return;
        }

        String[] parts = iconReference.split(":", 2);
        if (parts.length != 2) {
            return;
        }

        List<File> iconFiles = availableResources.get(parts[0] + ":" + parts[1]);
        if (iconFiles == null || iconFiles.isEmpty()) {
            return;
        }

        String iconStoreRoot = wq.e() + File.separator + scId;
        FileUtil.makeDir(iconStoreRoot);

        boolean adaptive = false;
        for (File iconFile : iconFiles) {
            if (isAdaptiveIconFile(iconFile)) {
                adaptive = true;
                break;
            }
        }

        if (adaptive) {
            String targetMipmaps = iconStoreRoot + File.separator + "mipmaps";
            FileUtil.deleteFile(targetMipmaps);
            Set<File> filesToCopy = new LinkedHashSet<>(iconFiles);
            AdaptiveIconSpec previewSpec = null;
            for (File iconFile : iconFiles) {
                AdaptiveIconSpec spec = parseAdaptiveIconSpec(iconFile);
                if (previewSpec == null && spec != null) {
                    previewSpec = spec;
                }
                filesToCopy.addAll(findAdaptiveIconDependencies(iconFile, availableResources));
            }
            for (File iconFile : filesToCopy) {
                copyIconFileToProjectStore(iconFile, targetMipmaps);
            }
            for (File iconFile : iconFiles) {
                copyAdaptiveLauncherXml(iconFile, targetMipmaps);
            }
            File previewIcon = findPreviewIconForAdaptiveIcon(previewSpec, filesToCopy, availableResources);
            if (previewIcon != null) {
                if (isRasterIconFile(previewIcon)) {
                    FileUtil.copyFile(previewIcon.getAbsolutePath(), iconStoreRoot + File.separator + "icon.png");
                } else {
                    rasterizeVectorDrawable(previewIcon, new File(iconStoreRoot, "icon.png"), scId);
                }
            }
            if (!new File(iconStoreRoot, "icon.png").isFile()) {
                result.warnings.add("Imported adaptive icon resources, but Sketchware could not generate a preview icon automatically.");
            }
        } else {
            File bestIcon = chooseBestRasterIcon(iconFiles);
            if (bestIcon == null) {
                File vectorPreview = chooseBestVectorDrawable(iconFiles);
                if (vectorPreview != null && rasterizeVectorDrawable(vectorPreview, new File(iconStoreRoot, "icon.png"), scId)) {
                    bestIcon = new File(iconStoreRoot, "icon.png");
                }
            } else {
                FileUtil.copyFile(bestIcon.getAbsolutePath(), iconStoreRoot + File.separator + "icon.png");
            }
            if (bestIcon == null) {
                result.warnings.add("Imported icon reference " + iconReference.replace(':', '/') + " uses XML/vector resources that Sketchware's project-icon preview could not rasterize automatically.");
                return;
            }
        }

        HashMap<String, Object> metadata = lC.b(scId);
        metadata.put("custom_icon", true);
        metadata.put("isIconAdaptive", adaptive);
        lC.b(scId, metadata);
        result.warnings.add("Imported application icon from " + iconReference.replace(':', '/'));
    }

    private boolean isAdaptiveIconFile(File iconFile) {
        if (iconFile == null || !iconFile.isFile() || !iconFile.getName().toLowerCase(Locale.US).endsWith(".xml")) {
            return false;
        }
        return FileUtil.readFile(iconFile.getAbsolutePath()).contains("<adaptive-icon");
    }

    private AdaptiveIconSpec parseAdaptiveIconSpec(File iconFile) {
        if (!isAdaptiveIconFile(iconFile)) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(iconFile);
            NodeList backgrounds = document.getElementsByTagName("background");
            NodeList foregrounds = document.getElementsByTagName("foreground");
            NodeList monochromes = document.getElementsByTagName("monochrome");
            AdaptiveIconSpec spec = new AdaptiveIconSpec();
            if (backgrounds.getLength() > 0) {
                spec.backgroundRef = normalizeResourceReference(getAndroidAttribute((Element) backgrounds.item(0), "drawable"));
            }
            if (foregrounds.getLength() > 0) {
                spec.foregroundRef = normalizeResourceReference(getAndroidAttribute((Element) foregrounds.item(0), "drawable"));
            }
            if (monochromes.getLength() > 0) {
                spec.monochromeRef = normalizeResourceReference(getAndroidAttribute((Element) monochromes.item(0), "drawable"));
            }
            return spec;
        } catch (Exception e) {
            Log.e(TAG, "Failed to inspect adaptive icon " + iconFile.getAbsolutePath(), e);
            return null;
        }
    }

    private List<File> findAdaptiveIconDependencies(File iconFile, Map<String, List<File>> resourcesByTypeAndName) {
        ArrayList<File> dependencies = new ArrayList<>();
        AdaptiveIconSpec spec = parseAdaptiveIconSpec(iconFile);
        if (spec == null) {
            return dependencies;
        }
        for (String reference : spec.getReferences()) {
            List<File> files = resourcesByTypeAndName.get(reference);
            if (files != null) {
                dependencies.addAll(files);
            }
        }
        return dependencies;
    }

    private void copyAdaptiveLauncherXml(File iconFile, String targetMipmaps) {
        if (!isAdaptiveIconFile(iconFile)) {
            return;
        }
        copyIconFileToProjectStore(iconFile, targetMipmaps, "ic_launcher.xml");
    }

    private void copyIconFileToProjectStore(File iconFile, String targetMipmaps) {
        copyIconFileToProjectStore(iconFile, targetMipmaps, null);
    }

    private void copyIconFileToProjectStore(File iconFile, String targetMipmaps, String targetNameOverride) {
        if (iconFile == null || !iconFile.isFile()) {
            return;
        }
        File qualifierDirectory = iconFile.getParentFile();
        if (qualifierDirectory == null) {
            return;
        }
        File targetDirectory = new File(targetMipmaps, qualifierDirectory.getName());
        targetDirectory.mkdirs();
        String targetName = TextUtils.isEmpty(targetNameOverride) ? iconFile.getName() : targetNameOverride;
        FileUtil.copyFile(iconFile.getAbsolutePath(), new File(targetDirectory, targetName).getAbsolutePath());
    }

    private File chooseBestRasterIcon(List<File> iconFiles) {
        ArrayList<File> files = new ArrayList<>(iconFiles);
        files.sort(Comparator.comparingInt(this::getDrawableDensityScore).reversed());
        for (File file : files) {
            if (isRasterIconFile(file)) {
                return file;
            }
        }
        return null;
    }

    private boolean isRasterIconFile(File file) {
        if (file == null) {
            return false;
        }
        String lowerName = file.getName().toLowerCase(Locale.US);
        return lowerName.endsWith(".png") || lowerName.endsWith(".webp")
                || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
    }

    private File chooseBestVectorDrawable(List<File> iconFiles) {
        ArrayList<File> files = new ArrayList<>(iconFiles);
        files.sort(Comparator.comparingInt(this::getDrawableDensityScore).reversed());
        for (File file : files) {
            if (!file.getName().toLowerCase(Locale.US).endsWith(".xml")) {
                continue;
            }
            String content = FileUtil.readFile(file.getAbsolutePath());
            if (content.contains("<vector")) {
                return file;
            }
        }
        return null;
    }

    private boolean rasterizeVectorDrawable(File vectorFile, File targetFile, String scId) {
        String previousScId = com.besome.sketch.design.DesignActivity.sc_id;
        try {
            com.besome.sketch.design.DesignActivity.sc_id = scId;
            String svg = new mod.bobur.VectorDrawableParser(FileUtil.readFile(vectorFile.getAbsolutePath())).toSvg();
            com.bobur.androidsvg.SVG svgObject = com.bobur.androidsvg.SVG.getFromString(svg);
            android.graphics.Picture picture = svgObject.renderToPicture();
            int width = Math.max(picture.getWidth(), 432);
            int height = Math.max(picture.getHeight(), 432);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0);
            new android.graphics.Canvas(bitmap).drawPicture(picture);
            targetFile.getParentFile().mkdirs();
            try (FileOutputStream outputStream = new FileOutputStream(targetFile, false)) {
                return bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to rasterize vector icon " + vectorFile.getAbsolutePath(), throwable);
            return false;
        } finally {
            com.besome.sketch.design.DesignActivity.sc_id = previousScId;
        }
    }

    private File findPreviewIconForAdaptiveIcon(AdaptiveIconSpec spec, Set<File> copiedFiles,
                                                Map<String, List<File>> availableResources) {
        if (spec != null) {
            File preferred = chooseBestRasterIcon(resolveAdaptivePreviewCandidates(spec, availableResources));
            if (preferred != null) {
                return preferred;
            }
            File vectorPreview = chooseBestVectorDrawable(resolveAdaptivePreviewCandidates(spec, availableResources));
            if (vectorPreview != null) {
                return vectorPreview;
            }
        }
        File rasterFallback = chooseBestRasterIcon(new ArrayList<>(copiedFiles));
        if (rasterFallback != null) {
            return rasterFallback;
        }
        return chooseBestVectorDrawable(new ArrayList<>(copiedFiles));
    }

    private List<File> resolveAdaptivePreviewCandidates(AdaptiveIconSpec spec, Map<String, List<File>> availableResources) {
        ArrayList<File> candidates = new ArrayList<>();
        addPreviewCandidates(candidates, availableResources.get(spec.foregroundRef));
        addPreviewCandidates(candidates, availableResources.get(spec.monochromeRef));
        addPreviewCandidates(candidates, availableResources.get(spec.backgroundRef));
        return candidates;
    }

    private void addPreviewCandidates(List<File> out, List<File> candidates) {
        if (candidates == null) {
            return;
        }
        out.addAll(candidates);
    }

    private int getDrawableDensityScore(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return 0;
        }
        String qualifier = parent.getName();
        if (qualifier.contains("xxxhdpi")) return 6;
        if (qualifier.contains("xxhdpi")) return 5;
        if (qualifier.contains("xhdpi")) return 4;
        if (qualifier.contains("hdpi")) return 3;
        if (qualifier.contains("mdpi")) return 2;
        if (qualifier.contains("ldpi")) return 1;
        return 0;
    }

    private String getBaseResourceType(String folderName) {
        int qualifierIndex = folderName.indexOf('-');
        return qualifierIndex < 0 ? folderName : folderName.substring(0, qualifierIndex);
    }

    private String getResourceName(String filename) {
        if (filename.endsWith(".9.png")) {
            return filename.substring(0, filename.length() - ".9.png".length());
        }
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex <= 0) {
            return null;
        }
        return filename.substring(0, extensionIndex);
    }

    private DependencyResolutionReport resolveAndRegisterDependencies(String scId, List<String> dependencies,
                                                                     int totalProgressSteps, int dependencyStepStart) {
        DependencyResolutionReport report = new DependencyResolutionReport();
        ArrayList<HashMap<String, Object>> localLibraries = LocalLibrariesUtil.getLocalLibraries(scId);
        Set<String> existingDependencies = new LinkedHashSet<>();
        Set<String> existingLibraryNames = new LinkedHashSet<>();
        Set<String> availableGlobalLibraryNames = collectGlobalLocalLibraryNames();
        for (HashMap<String, Object> localLibrary : localLibraries) {
            Object dependency = localLibrary.get("dependency");
            if (dependency != null) {
                existingDependencies.add(String.valueOf(dependency));
            }
            Object name = localLibrary.get("name");
            if (name != null) {
                existingLibraryNames.add(String.valueOf(name));
            }
        }

        if (dependencies.isEmpty()) {
            notifyProgress("Checking dependencies",
                    "No direct Maven dependencies were declared. Sketchware will rely on built-in libraries only when the imported project configuration requires them.",
                    dependencyStepStart, totalProgressSteps, false, "No external dependency downloads required");
            return report;
        }

        BuiltInLibraries.maybeExtractAndroidJar();
        BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

        long resolveStartTime = System.currentTimeMillis();
        int dependencyIndex = 0;
        for (String dependency : dependencies) {
            dependencyIndex++;
            int progressStep = dependencyStepStart + dependencyIndex - 1;
            String stepLabel = "Dependency " + dependencyIndex + " of " + dependencies.size();

            long elapsedMs = System.currentTimeMillis() - resolveStartTime;
            if (elapsedMs > IMPORT_OVERALL_TIMEOUT_MS) {
                int remaining = dependencies.size() - dependencyIndex + 1;
                String msg = "Dependency resolution stopped after 20 minutes. "
                        + remaining + " dependenc" + (remaining == 1 ? "y was" : "ies were")
                        + " not resolved. Add them manually from Local Libraries.";
                report.warnings.add(msg);
                List<String> depList = new ArrayList<>(dependencies);
                for (int i = dependencyIndex - 1; i < depList.size(); i++) {
                    if (!existingDependencies.contains(depList.get(i))) {
                        report.unresolved.add(depList.get(i));
                    }
                }
                notifyProgress("Import timeout reached", msg, progressStep, totalProgressSteps, false, "Resolution stopped — time limit exceeded");
                break;
            }

            if (existingDependencies.contains(dependency)) {
                notifyProgress("Skipping duplicate dependency", dependency, progressStep, totalProgressSteps, false, stepLabel + " • already attached to project");
                continue;
            }

            String[] parts = dependency.split(":", 3);
            if (parts.length != 3) {
                String warning = "Dependency declaration '" + dependency + "' could not be parsed automatically. Review this dependency manually in the imported project.";
                report.warnings.add(warning);
                report.unresolved.add(dependency);
                notifyProgress("Dependency needs manual review", warning, progressStep, totalProgressSteps, false, stepLabel + " • unsupported declaration");
                continue;
            }

            BuiltInDependencyRegistry.Match bundledMatch = BuiltInDependencyRegistry.findBundled(parts[0], parts[1], parts[2]);
            if (bundledMatch != null && bundledMatch.isSatisfiedByBundled()) {
                existingDependencies.add(dependency);
                report.satisfiedByBundled.add(bundledMatch.toDisplayLine());
                notifyProgress("Using bundled library", bundledMatch.toDisplayLine(), progressStep, totalProgressSteps, false, stepLabel + " • satisfied by Sketchware");
                continue;
            }

            if (bundledMatch != null) {
                String warning = "Dependency '" + dependency + "' requests a newer version than Sketchware's bundled "
                        + bundledMatch.bundledLibraryName + ". The importer will resolve the external artifact instead of forcing the older bundled copy.";
                report.warnings.add(warning);
            }

            String rootLibraryName = sanitizeLibraryName(parts[1] + "-v" + parts[2]);
            if (availableGlobalLibraryNames.contains(rootLibraryName)) {
                if (!existingLibraryNames.contains(rootLibraryName)) {
                    localLibraries.add(LocalLibrariesUtil.createLibraryMap(rootLibraryName, dependency));
                    existingLibraryNames.add(rootLibraryName);
                }
                existingDependencies.add(dependency);
                report.reusedLocal.add(dependency + " -> existing local library " + rootLibraryName);
                notifyProgress("Reusing downloaded local library", dependency + " -> " + rootLibraryName, progressStep, totalProgressSteps, false, stepLabel + " • cached artifact reused");
                continue;
            }

            notifyProgress("Resolving external dependency", dependency, progressStep, totalProgressSteps, false, stepLabel + " • download and dex required");
            List<String> resolvedArtifacts;
            try {
                resolvedArtifacts = resolveDependencyArtifactsWithTimeout(scId, parts[0], parts[1], parts[2]);
            } catch (Exception exception) {
                Log.e(TAG, "Dependency resolution failed for " + dependency, exception);
                String warning = "Dependency '" + dependency + "' could not be resolved automatically: " + exception.getMessage();
                report.warnings.add(warning);
                report.unresolved.add(dependency);
                continue;
            }

            if (resolvedArtifacts.isEmpty()) {
                availableGlobalLibraryNames = collectGlobalLocalLibraryNames();
                if (availableGlobalLibraryNames.contains(rootLibraryName)) {
                    resolvedArtifacts = Collections.singletonList(rootLibraryName);
                } else {
                    String warning = "Dependency '" + dependency + "' finished without a usable local library output. Review this dependency manually from Local Libraries if the imported project fails to build.";
                    report.warnings.add(warning);
                    report.unresolved.add(dependency);
                    continue;
                }
            }

            for (String artifactName : resolvedArtifacts) {
                String safeArtifactName = sanitizeLibraryName(artifactName);
                if (existingLibraryNames.contains(safeArtifactName)) {
                    continue;
                }
                String rootDependency = safeArtifactName.equals(rootLibraryName) ? dependency : null;
                localLibraries.add(LocalLibrariesUtil.createLibraryMap(safeArtifactName, rootDependency));
                existingLibraryNames.add(safeArtifactName);
            }
            existingDependencies.add(dependency);
            availableGlobalLibraryNames = collectGlobalLocalLibraryNames();
            report.downloaded.add(dependency);
        }

        LocalLibrariesUtil.rewriteLocalLibFile(scId, gson.toJson(localLibraries));
        return report;
    }

    private Set<String> collectGlobalLocalLibraryNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (dev.aldi.sayuti.editor.manage.LocalLibrary library : LocalLibrariesUtil.getAllLocalLibraries()) {
            if (library != null && library.getName() != null) {
                names.add(library.getName());
            }
        }
        return names;
    }

    private List<String> resolveDependencyArtifactsWithTimeout(String scId, String groupId, String artifactId, String version) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> artifactsRef = new AtomicReference<>(new ArrayList<>());
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                List<String> resolvedArtifacts = new ArrayList<>();
                new DependencyResolver(groupId, artifactId, version, false, new BuildSettings(scId))
                        .resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                            @Override
                            public void onTaskCompleted(List<String> artifacts) {
                                if (artifacts != null) {
                                    resolvedArtifacts.addAll(artifacts);
                                }
                            }
                        });
                artifactsRef.set(resolvedArtifacts);
            } catch (Throwable throwable) {
                failureRef.set(throwable);
            } finally {
                latch.countDown();
            }
        }, "ImporterDependencyResolver-" + artifactId + '-' + version);
        worker.setDaemon(true);
        worker.start();

        if (!latch.await(DEPENDENCY_RESOLVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            worker.interrupt();
            throw new IOException("Timed out while resolving '" + groupId + ':' + artifactId + ':' + version + "'. The project was imported, but this dependency must be downloaded manually from the Local Libraries manager.");
        }

        Throwable failure = failureRef.get();
        if (failure != null) {
            if (failure instanceof Exception exception) {
                throw exception;
            }
            throw new IOException(failure.getMessage() == null ? failure.toString() : failure.getMessage(), failure);
        }

        return artifactsRef.get();
    }

    private void importLocalJarsAndAars(List<File> libraryDirectories, String scId) throws IOException {
        String classpathDir = wq.b(scId) + File.separator + "files" + File.separator + "classpath";
        FileUtil.makeDir(classpathDir);
        for (File libraryDirectory : libraryDirectories) {
            List<File> files = collectFilesRecursively(libraryDirectory);
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.US);
                if (name.endsWith(".jar")) {
                    try (java.nio.channels.FileChannel sourceChannel = new java.io.FileInputStream(file).getChannel();
                         java.nio.channels.FileChannel destChannel = new java.io.FileOutputStream(classpathDir + File.separator + file.getName()).getChannel()) {
                        long size = sourceChannel.size();
                        long position = 0;
                        while (position < size) {
                            position += destChannel.transferFrom(sourceChannel, position, size - position);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to copy local jar: " + file.getAbsolutePath(), e);
                    }
                } else if (name.endsWith(".aar")) {
                    importAarAsLocalLibrary(scId, file);
                }
            }
        }
    }

    private List<File> collectFilesRecursively(File directory) {
        List<File> files = new ArrayList<>();
        if (directory == null || !directory.exists()) {
            return files;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return files;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(collectFilesRecursively(child));
            } else {
                files.add(child);
            }
        }
        return files;
    }

    private void addUniqueFile(List<File> files, File candidate) {
        if (candidate == null) {
            return;
        }
        String candidatePath = candidate.getAbsolutePath();
        for (File existing : files) {
            if (candidatePath.equals(existing.getAbsolutePath())) {
                return;
            }
        }
        files.add(candidate);
    }

    private void importAarAsLocalLibrary(String scId, File aarFile) throws IOException {
        String libraryName = sanitizeLibraryName(FileUtil.getFileNameNoExtension(aarFile.getName()));
        String localLibraryRoot = FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs/" + libraryName;
        FileUtil.deleteFile(localLibraryRoot);
        FileUtil.makeDir(localLibraryRoot);
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(aarFile)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String safeName = sanitizeZipEntryName(entry.getName());
                if (safeName == null) {
                    zipInputStream.closeEntry();
                    continue;
                }
                File target = new File(localLibraryRoot, safeName);
                String canonicalRoot = new File(localLibraryRoot).getCanonicalPath() + File.separator;
                String canonicalTarget = target.getCanonicalPath();
                if (!canonicalTarget.startsWith(canonicalRoot) && !canonicalTarget.equals(new File(localLibraryRoot).getCanonicalPath())) {
                    throw new IOException("Unsafe AAR entry detected");
                }
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    target.getParentFile().mkdirs();
                    copyStreamToFile(zipInputStream, target);
                }
                zipInputStream.closeEntry();
            }
        }
        fixMaterial3ExpressiveStylesInDir(new File(localLibraryRoot, "res"));

        ArrayList<HashMap<String, Object>> localLibraries = LocalLibrariesUtil.getLocalLibraries(scId);
        boolean exists = false;
        for (HashMap<String, Object> library : localLibraries) {
            if (libraryName.equals(String.valueOf(library.get("name")))) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            localLibraries.add(LocalLibrariesUtil.createLibraryMap(libraryName, null));
            LocalLibrariesUtil.rewriteLocalLibFile(scId, gson.toJson(localLibraries));
        }
    }

    private void ensureProjectDirectories(String scId) {
        FileUtil.makeDir(wq.b(scId));
        FileUtil.makeDir(filePathUtil.getPathJava(scId));
        FileUtil.makeDir(filePathUtil.getPathResource(scId));
        FileUtil.makeDir(filePathUtil.getPathResource(scId) + File.separator + "layout");
        FileUtil.makeDir(filePathUtil.getPathResource(scId) + File.separator + "values");
        FileUtil.makeDir(filePathUtil.getPathAssets(scId));
        FileUtil.makeDir(filePathUtil.getPathNativelibs(scId));
        FileUtil.makeDir(wq.b(scId) + File.separator + "files" + File.separator + "classpath");
        FileUtil.makeDir(ProjectManifestManager.getManifestDirectory(scId));
    }

    private void copySourceTree(List<File> sourceRoots, File targetRoot) throws IOException {
        FileUtil.makeDir(targetRoot.getAbsolutePath());
        for (File sourceRoot : sourceRoots) {
            if (sourceRoot != null && sourceRoot.isDirectory()) {
                copySourceTree(sourceRoot, targetRoot, sourceRoot);
            }
        }
    }

    private void copySourceTree(File current, File targetRoot, File sourceRoot) throws IOException {
        if (current == null || !current.exists()) {
            return;
        }

        String relativePath = sourceRoot.toPath().relativize(current.toPath()).toString();
        if (shouldSkipImportedSource(current, relativePath)) {
            return;
        }

        if (current.isDirectory()) {
            File[] children = current.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                copySourceTree(child, targetRoot, sourceRoot);
            }
            return;
        }

        File destination = relativePath.isEmpty() ? new File(targetRoot, current.getName()) : new File(targetRoot, relativePath);
        File parent = destination.getParentFile();
        if (parent != null) {
            FileUtil.makeDir(parent.getAbsolutePath());
        }
        FileUtil.copyFile(current.getAbsolutePath(), destination.getAbsolutePath());
    }

    private boolean shouldSkipImportedSource(File file, String relativePath) {
        String normalizedRelativePath = relativePath == null ? "" : relativePath.replace("\\", "/");
        String lowerCasePath = normalizedRelativePath.toLowerCase(Locale.US);
        String fileName = file.getName();

        // Only skip top-level build output directories (build/, generated/, out/) to avoid
        // incorrectly filtering Java source files whose package path contains these words
        // (e.g. mod.jbk.build.* or com.example.generated.*).
        if ("build".equals(lowerCasePath)
                || "generated".equals(lowerCasePath)
                || "out".equals(lowerCasePath)
                || lowerCasePath.startsWith("build/")
                || lowerCasePath.startsWith("generated/")
                || lowerCasePath.startsWith("out/")) {
            return true;
        }

        if (file.isDirectory()) {
            return false;
        }

        if ("BuildConfig.java".equals(fileName)
                || "R.java".equals(fileName)
                || "BR.java".equals(fileName)
                || fileName.startsWith("R$")
                || fileName.startsWith("DataBinderMapper")) {
            return true;
        }

        // Sketchware auto-generates these files during build; importing them causes duplicates
        if ("FileUtil.java".equals(fileName)
                || "SketchwareUtil.java".equals(fileName)
                || "RequestNetwork.java".equals(fileName)
                || "RequestNetworkController.java".equals(fileName)) {
            return true;
        }

        if (fileName.endsWith("Binding.java") && lowerCasePath.contains("/databinding/")) {
            String content = FileUtil.readFile(file.getAbsolutePath());
            return content.contains("Generated file. Do not modify.")
                    || content.contains("inflate(LayoutInflater");
        }

        return false;
    }

    private void copyResources(List<File> resDirectories, File targetRoot) throws IOException {
        FileUtil.makeDir(targetRoot.getAbsolutePath());
        for (File resDirectory : resDirectories) {
            if (resDirectory != null && resDirectory.isDirectory()) {
                FileUtil.copyDirectory(resDirectory, targetRoot);
            }
        }
    }

    /**
     * Replaces Widget.Material3Expressive.* style references with their Material3 equivalents.
     * Material3Expressive styles require material:1.13.0+ which is newer than what Sketchware
     * bundles; the Material3 equivalents are functionally identical for build purposes.
     */
    private void fixMaterial3ExpressiveStylesInDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        for (File file : collectFilesRecursively(dir)) {
            if (!file.isFile() || !file.getName().endsWith(".xml")) {
                continue;
            }
            String content = FileUtil.readFile(file.getAbsolutePath());
            if (TextUtils.isEmpty(content) || !content.contains("Material3Expressive")) {
                continue;
            }
            String fixed = content.replace("Widget.Material3Expressive.", "Widget.Material3.");
            if (!fixed.equals(content)) {
                FileUtil.writeFile(file.getAbsolutePath(), fixed);
            }
        }
    }

    /**
     * Public version used by the build pipeline to fix styles before AAPT2 sees them.
     */
    public static void fixMaterial3ExpressiveStylesInDirStatic(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        fixMaterial3ExpressiveStylesInDirStaticRecursive(dir);
    }

    private static void fixMaterial3ExpressiveStylesInDirStaticRecursive(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File file : children) {
            if (file.isDirectory()) {
                fixMaterial3ExpressiveStylesInDirStaticRecursive(file);
            } else if (file.isFile() && file.getName().endsWith(".xml")) {
                String content = FileUtil.readFile(file.getAbsolutePath());
                if (!TextUtils.isEmpty(content) && content.contains("Material3Expressive")) {
                    String fixed = content.replace("Widget.Material3Expressive.", "Widget.Material3.");
                    if (!fixed.equals(content)) {
                        FileUtil.writeFile(file.getAbsolutePath(), fixed);
                    }
                }
            }
        }
    }

    /**
     * Scans res/xml/ directories for preference screen definitions and adds
     * androidx.preference:preference as an implicit dependency when found.
     * This fixes build errors for attribute key/summary/iconSpaceReserved.
     */
    private void detectImplicitResourceDependencies(List<File> resDirectories, List<String> dependencies) {
        boolean needsPreference = false;
        for (File resDir : resDirectories) {
            if (resDir == null || !resDir.isDirectory()) {
                continue;
            }
            File xmlDir = new File(resDir, "xml");
            if (!xmlDir.isDirectory()) {
                continue;
            }
            File[] xmlFiles = xmlDir.listFiles();
            if (xmlFiles == null) {
                continue;
            }
            for (File xmlFile : xmlFiles) {
                if (!xmlFile.isFile() || !xmlFile.getName().endsWith(".xml")) {
                    continue;
                }
                String content = FileUtil.readFile(xmlFile.getAbsolutePath());
                if (TextUtils.isEmpty(content)) {
                    continue;
                }
                if (content.contains("PreferenceScreen")
                        || content.contains("<SwitchPreference")
                        || content.contains("<CheckBoxPreference")
                        || content.contains("<ListPreference")
                        || content.contains("<EditTextPreference")
                        || content.contains("<Preference ")
                        || content.contains("iconSpaceReserved")) {
                    needsPreference = true;
                    break;
                }
            }
            if (needsPreference) {
                break;
            }
        }
        if (needsPreference) {
            String prefDep = "androidx.preference:preference:1.2.1";
            if (!dependencies.contains(prefDep)) {
                dependencies.add(prefDep);
                Log.i(TAG, "Auto-added " + prefDep + " (detected preference XML resources)");
            }
        }
    }

    private void copyDirectories(List<File> sources, File target) throws IOException {
        FileUtil.makeDir(target.getAbsolutePath());
        for (File source : sources) {
            if (source != null && source.isDirectory()) {
                FileUtil.copyDirectory(source, target);
            }
        }
    }

    private ImportResult emptyResult() {
        return new ImportResult();
    }

    private String buildSummary(ImportResult result, ManifestSummary manifest) {
        return "Imported " + result.visualScreens.size() + " screen(s), "
                + result.visualCustomViews.size() + " custom view(s), satisfied "
                + result.satisfiedByBundledDependencies.size() + " dependency declaration(s) with bundled libraries, reused "
                + result.reusedLocalDependencies.size() + " cached local librar" + (result.reusedLocalDependencies.size() == 1 ? "y" : "ies") + ", downloaded "
                + result.downloadedDependencies.size() + " external dependenc" + (result.downloadedDependencies.size() == 1 ? "y" : "ies") + ", and preserved "
                + manifest.permissions.size() + " manifest permission(s).";
    }

    private void writeImportMetadata(String scId, String sourceType, boolean roundTrip) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source_type", sourceType);
        metadata.put("java_layout", "full_source_tree");
        metadata.put("round_trip", roundTrip);
        metadata.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
        FileUtil.writeFile(wq.b(scId) + File.separator + "import_metadata.json", gson.toJson(metadata));
    }

    private void writeImportReport(ImportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(result.projectName).append('\n');
        sb.append("Sketchware ID: ").append(result.scId).append('\n');
        if (!TextUtils.isEmpty(result.sourceLabel)) {
            sb.append("Source: ").append(result.sourceLabel).append('\n');
        }
        sb.append("Type: ").append(result.sourceType).append('\n').append('\n');
        appendSection(sb, "Visual / registered screens", result.visualScreens);
        appendSection(sb, "Visual / registered custom views", result.visualCustomViews);
        appendSection(sb, "Code-only notes", result.codeOnlyFiles);
        appendSection(sb, "Dependency declarations", result.importedDependencies);
        appendSection(sb, "Satisfied by bundled libraries", result.satisfiedByBundledDependencies);
        appendSection(sb, "Reused cached local libraries", result.reusedLocalDependencies);
        appendSection(sb, "Downloaded external dependencies", result.downloadedDependencies);
        appendSection(sb, "Dependencies requiring manual review", result.manualDependencyActions);
        appendSection(sb, "Warnings", result.warnings);
        appendSection(sb, "Unsupported / degraded features", result.unsupportedFeatures);
        FileUtil.writeFile(wq.b(result.scId) + File.separator + "import_report.txt", sb.toString());
    }

    private void appendSection(StringBuilder sb, String title, List<String> lines) {
        sb.append(title).append(':').append('\n');
        if (lines == null || lines.isEmpty()) {
            sb.append("- none\n\n");
            return;
        }
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
        sb.append('\n');
    }

    private HashMap<String, Object> createProjectMetadata(String scId, String projectName, String packageName,
                                                          String appName, String versionCode, String versionName) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("sc_id", scId);
        data.put("proj_type", 1);
        data.put("my_sc_pkg_name", packageName);
        data.put("my_ws_name", sanitizeProjectName(projectName));
        data.put("my_app_name", TextUtils.isEmpty(appName) ? projectName : appName);
        data.put("my_sc_reg_dt", new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date()));
        data.put("custom_icon", false);
        data.put("isIconAdaptive", false);
        data.put("sc_ver_code", versionCode);
        data.put("sc_ver_name", versionName);
        data.put("sketchware_ver", GB.d(context));
        data.put(COLOR_ACCENT, getDefaultColor(COLOR_ACCENT));
        data.put(COLOR_PRIMARY, getDefaultColor(COLOR_PRIMARY));
        data.put(COLOR_PRIMARY_DARK, getDefaultColor(COLOR_PRIMARY_DARK));
        data.put(COLOR_CONTROL_HIGHLIGHT, getDefaultColor(COLOR_CONTROL_HIGHLIGHT));
        data.put(COLOR_CONTROL_NORMAL, getDefaultColor(COLOR_CONTROL_NORMAL));
        return data;
    }

    private String chooseProjectName(String preferredProjectName, String applicationLabel, String fallback) {
        if (!TextUtils.isEmpty(preferredProjectName)) {
            return sanitizeProjectName(preferredProjectName);
        }
        if (!TextUtils.isEmpty(applicationLabel)) {
            return sanitizeProjectName(applicationLabel);
        }
        return sanitizeProjectName(fallback);
    }

    private String chooseApplicationId(String applicationId, String namespace, String manifestPackage, String projectName) {
        if (!TextUtils.isEmpty(applicationId)) return applicationId;
        if (!TextUtils.isEmpty(namespace)) return namespace;
        if (!TextUtils.isEmpty(manifestPackage)) return manifestPackage;
        return "com.imported." + sanitizeProjectName(projectName).toLowerCase(Locale.US).replace(' ', '.');
    }

    private String sanitizeProjectName(String value) {
        if (TextUtils.isEmpty(value)) {
            return "ImportedProject";
        }
        value = value.replaceAll("[^A-Za-z0-9 _.-]", " ").trim();
        if (value.isEmpty()) {
            return "ImportedProject";
        }
        return value;
    }

    private String sanitizeLibraryName(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private String toSketchwareScreenName(String simpleClassName) {
        String base = simpleClassName;
        if (base.endsWith("Activity") && base.length() > "Activity".length()) {
            base = base.substring(0, base.length() - "Activity".length());
        }
        String snake = base.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.US);
        snake = snake.replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        snake = snake.replaceAll("^_+|_+$", "");
        return snake.isEmpty() ? "main" : snake;
    }

    private String uniquifyScreenName(Set<String> usedNames, String desired) {
        String candidate = desired;
        int index = 2;
        while (usedNames.contains(candidate)) {
            candidate = desired + "_" + index;
            index++;
        }
        usedNames.add(candidate);
        return candidate;
    }

    private String detectLayoutName(String source) {
        Matcher matcher = SET_CONTENT_VIEW_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = DATABINDING_SET_CONTENT_VIEW_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = DATABINDING_INFLATE_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = INFLATE_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = VIEWBINDING_INFLATE_PATTERN.matcher(source);
        if (matcher.find()) {
            return bindingClassNameToLayoutName(matcher.group(1));
        }
        matcher = LAYOUT_REFERENCE_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String bindingClassNameToLayoutName(String bindingClassName) {
        if (TextUtils.isEmpty(bindingClassName)) {
            return null;
        }
        String value = bindingClassName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.US);
        value = value.replaceAll("^_+|_+$", "");
        if (value.endsWith("_binding")) {
            value = value.substring(0, value.length() - "_binding".length());
        }
        return value;
    }

    private String findBestMatchingLayoutName(List<File> layoutDirectories, String screenName, String simpleClassName) {
        for (String candidate : buildLikelyLayoutNames(screenName, simpleClassName)) {
            File guessed = findLayoutFile(layoutDirectories, candidate);
            if (guessed != null && guessed.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private List<String> buildLikelyLayoutNames(String screenName, String simpleClassName) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(screenName)) {
            candidates.add(screenName);
            candidates.add("activity_" + screenName);
            candidates.add("fragment_" + screenName);
            candidates.add(screenName + "_activity");
            candidates.add(screenName + "_fragment");
            if (screenName.endsWith("_activity") && screenName.length() > 9) {
                String trimmed = screenName.substring(0, screenName.length() - 9);
                candidates.add(trimmed);
                candidates.add("activity_" + trimmed);
            }
        }
        if (!TextUtils.isEmpty(simpleClassName)) {
            String snake = toSketchwareScreenName(simpleClassName);
            candidates.add(snake);
            candidates.add("activity_" + snake);
            candidates.add("fragment_" + snake);
            String baseClassName = simpleClassName;
            if (baseClassName.endsWith("Activity") && baseClassName.length() > "Activity".length()) {
                baseClassName = baseClassName.substring(0, baseClassName.length() - "Activity".length());
                String baseSnake = toSketchwareScreenName(baseClassName);
                candidates.add(baseSnake);
                candidates.add("activity_" + baseSnake);
                candidates.add("fragment_" + baseSnake);
            }
            if (baseClassName.endsWith("Fragment") && baseClassName.length() > "Fragment".length()) {
                baseClassName = baseClassName.substring(0, baseClassName.length() - "Fragment".length());
                String baseSnake = toSketchwareScreenName(baseClassName);
                candidates.add(baseSnake);
                candidates.add("fragment_" + baseSnake);
                candidates.add("activity_" + baseSnake);
            }
        }
        return new ArrayList<>(candidates);
    }

    private ArrayList<DiscoveredActivity> discoverSourceActivities(List<File> sourceRoots) {
        LinkedHashMap<String, DiscoveredActivity> activities = new LinkedHashMap<>();
        for (File sourceRoot : sourceRoots) {
            if (sourceRoot == null || !sourceRoot.isDirectory()) {
                continue;
            }
            for (File sourceFile : collectFilesRecursively(sourceRoot)) {
                if (!sourceFile.isFile()) {
                    continue;
                }
                String lowerName = sourceFile.getName().toLowerCase(Locale.US);
                if (!(lowerName.endsWith(".java") || lowerName.endsWith(".kt"))) {
                    continue;
                }
                String source = FileUtil.readFile(sourceFile.getAbsolutePath());
                if (!looksLikeActivitySource(source)) {
                    continue;
                }
                String packageName = detectPackageName(source);
                String className = detectTopLevelClassName(source);
                if (TextUtils.isEmpty(className)) {
                    className = FileUtil.getFileNameNoExtension(sourceFile.getName());
                }
                String fqcn = TextUtils.isEmpty(packageName) ? className : packageName + "." + className;
                DiscoveredActivity discoveredActivity = new DiscoveredActivity();
                discoveredActivity.fullyQualifiedName = fqcn;
                discoveredActivity.simpleClassName = className;
                discoveredActivity.layoutName = detectLayoutName(source);
                activities.putIfAbsent(fqcn, discoveredActivity);
            }
        }
        return new ArrayList<>(activities.values());
    }

    private boolean looksLikeActivitySource(String source) {
        if (TextUtils.isEmpty(source)) {
            return false;
        }
        return JAVA_ACTIVITY_EXTENDS_PATTERN.matcher(source).find()
                || KOTLIN_ACTIVITY_EXTENDS_PATTERN.matcher(source).find();
    }

    private String detectPackageName(String source) {
        Matcher matcher = PACKAGE_DECL_PATTERN.matcher(source);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String detectTopLevelClassName(String source) {
        Matcher matcher = JAVA_CLASS_NAME_PATTERN.matcher(source);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private File findSourceFileForClass(List<File> sourceRoots, String fqcn) {
        String path = fqcn.replace('.', File.separatorChar);
        for (File sourceRoot : sourceRoots) {
            File javaFile = new File(sourceRoot, path + ".java");
            if (javaFile.exists()) return javaFile;
            File kotlinFile = new File(sourceRoot, path + ".kt");
            if (kotlinFile.exists()) return kotlinFile;
        }
        String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        for (File sourceRoot : sourceRoots) {
            List<File> javaCandidates = FileUtil.listFilesRecursively(sourceRoot, ".java");
            for (File candidate : javaCandidates) {
                if (candidate.getName().equals(simpleName + ".java")) return candidate;
            }
            List<File> ktCandidates = FileUtil.listFilesRecursively(sourceRoot, ".kt");
            for (File candidate : ktCandidates) {
                if (candidate.getName().equals(simpleName + ".kt")) return candidate;
            }
        }
        return null;
    }

    private DetectedProject detectProject(File extractedRoot) {
        Map<String, AndroidModule> modulesByPath = new LinkedHashMap<>();
        List<File> manifestFiles = FileUtil.listFilesRecursively(extractedRoot, "AndroidManifest.xml");
        for (File manifestFile : manifestFiles) {
            File sourceSetDir = manifestFile.getParentFile();
            if (sourceSetDir == null) {
                continue;
            }
            File srcDir = sourceSetDir.getParentFile();
            if (srcDir == null || !"src".equals(srcDir.getName())) {
                continue;
            }
            File moduleDir = srcDir.getParentFile();
            if (moduleDir == null) {
                continue;
            }
            AndroidModule module = modulesByPath.computeIfAbsent(moduleDir.getAbsolutePath(), unused -> createModule(moduleDir));
            module.manifestsBySourceSet.put(sourceSetDir.getName(), manifestFile);
        }

        if (modulesByPath.isEmpty()) {
            return null;
        }

        ArrayList<AndroidModule> modules = new ArrayList<>(modulesByPath.values());
        AndroidModule primaryModule = choosePrimaryModule(modules);
        if (primaryModule == null) {
            return null;
        }

        DetectedProject detectedProject = new DetectedProject();
        detectedProject.rootDirectory = determineProjectRoot(extractedRoot, modules);
        detectedProject.archiveLabel = detectedProject.rootDirectory.getName();
        detectedProject.appDirectory = primaryModule.moduleDirectory;
        detectedProject.primaryManifestFile = primaryModule.getMainManifest();
        detectedProject.primaryGradleFile = primaryModule.gradleFile;
        detectedProject.modules = new ArrayList<>();
        detectedProject.gradleFiles = new ArrayList<>();
        detectedProject.primaryManifestOverlayFiles = new ArrayList<>();
        detectedProject.sourceRoots = new ArrayList<>();
        detectedProject.resDirectories = new ArrayList<>();
        detectedProject.layoutDirectories = new ArrayList<>();
        detectedProject.assetsDirectories = new ArrayList<>();
        detectedProject.jniLibsDirectories = new ArrayList<>();
        detectedProject.libsDirectories = new ArrayList<>();
        detectedProject.libraryManifestFiles = new ArrayList<>();

        for (AndroidModule module : modules) {
            if (!(module == primaryModule || module.applicationModule || module.libraryModule || module.dynamicFeatureModule)) {
                continue;
            }
            detectedProject.modules.add(module);
            if (module.gradleFile != null && module.gradleFile.isFile()) {
                addUniqueFile(detectedProject.gradleFiles, module.gradleFile);
            }
            collectModuleSources(detectedProject, module);
            if (module != primaryModule) {
                for (File manifestFile : getOrderedManifestFiles(module)) {
                    if (manifestFile != null && manifestFile.isFile()) {
                        addUniqueFile(detectedProject.libraryManifestFiles, manifestFile);
                    }
                }
            }
        }

        for (File manifestFile : getOrderedManifestFiles(primaryModule)) {
            if (manifestFile != null && manifestFile.isFile() && !manifestFile.equals(detectedProject.primaryManifestFile)) {
                addUniqueFile(detectedProject.primaryManifestOverlayFiles, manifestFile);
            }
        }

        for (AndroidModule module : modules) {
            File moduleLibs = new File(module.moduleDirectory, "libs");
            if (moduleLibs.isDirectory()) {
                addUniqueFile(detectedProject.libsDirectories, moduleLibs);
            }
        }

        File rootLibs = new File(detectedProject.rootDirectory, "libs");
        if (rootLibs.isDirectory()) {
            addUniqueFile(detectedProject.libsDirectories, rootLibs);
        }

        File skproDir = new File(detectedProject.rootDirectory, ".skpro");
        File roundTripMetadata = new File(skproDir, "project_metadata.json");
        if (roundTripMetadata.isFile()) {
            detectedProject.roundTripMetadataJson = roundTripMetadata;
            File roundTripData = new File(skproDir, "data_snapshot/data");
            if (roundTripData.isDirectory()) {
                detectedProject.roundTripDataDir = roundTripData;
            }
        }
        return detectedProject;
    }

    private File determineProjectRoot(File extractedRoot, List<AndroidModule> modules) {
        File commonAncestor = null;
        for (AndroidModule module : modules) {
            commonAncestor = commonAncestor == null
                    ? module.moduleDirectory
                    : getCommonAncestor(commonAncestor, module.moduleDirectory);
        }
        if (commonAncestor == null) {
            return extractedRoot;
        }

        File settingsDir = commonAncestor;
        while (settingsDir != null && settingsDir.getAbsolutePath().startsWith(extractedRoot.getAbsolutePath())) {
            if (new File(settingsDir, "settings.gradle").isFile()
                    || new File(settingsDir, "settings.gradle.kts").isFile()) {
                return settingsDir;
            }
            settingsDir = settingsDir.getParentFile();
        }
        return commonAncestor;
    }

    private File getCommonAncestor(File left, File right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        File candidate = left;
        while (candidate != null) {
            String candidatePath = candidate.getAbsolutePath();
            String rightPath = right.getAbsolutePath();
            if (rightPath.equals(candidatePath) || rightPath.startsWith(candidatePath + File.separator)) {
                return candidate;
            }
            candidate = candidate.getParentFile();
        }
        return null;
    }

    private List<File> getOrderedManifestFiles(AndroidModule module) {
        LinkedHashSet<String> orderedSourceSets = new LinkedHashSet<>();
        orderedSourceSets.addAll(module.preferredSourceSetNames);
        ArrayList<String> sourceSetNames = new ArrayList<>(module.manifestsBySourceSet.keySet());
        sourceSetNames.sort(this::compareSourceSetNames);
        orderedSourceSets.addAll(sourceSetNames);
        ArrayList<File> files = new ArrayList<>();
        for (String sourceSetName : orderedSourceSets) {
            File manifestFile = module.manifestsBySourceSet.get(sourceSetName);
            if (manifestFile != null && manifestFile.isFile()) {
                files.add(manifestFile);
            }
        }
        return files;
    }

    private AndroidModule createModule(File moduleDir) {
        AndroidModule module = new AndroidModule();
        module.moduleDirectory = moduleDir;
        module.gradleFile = new File(moduleDir, "build.gradle");
        if (!module.gradleFile.isFile()) {
            module.gradleFile = new File(moduleDir, "build.gradle.kts");
        }
        String gradleContent = module.gradleFile.isFile() ? FileUtil.readFile(module.gradleFile.getAbsolutePath()) : "";
        module.applicationModule = gradleContent.contains("com.android.application");
        module.libraryModule = gradleContent.contains("com.android.library");
        module.dynamicFeatureModule = gradleContent.contains("com.android.dynamic-feature");
        module.availableBuildTypes.addAll(parseNamedGradleBlocks(gradleContent, "buildTypes"));
        module.availableProductFlavors.addAll(parseNamedGradleBlocks(gradleContent, "productFlavors"));
        module.preferredSourceSetNames.addAll(selectPreferredSourceSets(module.availableBuildTypes, module.availableProductFlavors));
        return module;
    }


    private LinkedHashSet<String> parseNamedGradleBlocks(String content, String blockName) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (TextUtils.isEmpty(content)) {
            return names;
        }
        int blockIndex = content.indexOf(blockName);
        if (blockIndex < 0) {
            return names;
        }
        int braceStart = content.indexOf('{', blockIndex);
        if (braceStart < 0) {
            return names;
        }
        int depth = 0;
        int blockEnd = -1;
        for (int i = braceStart; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    blockEnd = i;
                    break;
                }
            }
        }
        if (blockEnd <= braceStart) {
            return names;
        }
        String block = content.substring(braceStart + 1, blockEnd);
        int nestedDepth = 0;
        for (String rawLine : block.split("\n")) {
            String line = rawLine.trim();
            if (nestedDepth == 0) {
                if (line.startsWith("create(")) {
                    String createdName = extractQuotedIdentifier(line);
                    if (!TextUtils.isEmpty(createdName)) {
                        names.add(createdName);
                    }
                } else if (line.endsWith("{") || line.contains(" {")) {
                    String candidate = line.substring(0, line.indexOf('{')).trim();
                    if (candidate.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        names.add(candidate);
                    }
                }
            }
            nestedDepth += countOccurrences(rawLine, '{');
            nestedDepth -= countOccurrences(rawLine, '}');
            if (nestedDepth < 0) {
                nestedDepth = 0;
            }
        }
        return names;
    }

    private String extractQuotedIdentifier(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        int singleStart = value.indexOf(39);
        int doubleStart = value.indexOf('"');
        int start;
        char quote;
        if (singleStart >= 0 && (doubleStart < 0 || singleStart < doubleStart)) {
            start = singleStart;
            quote = (char) 39;
        } else if (doubleStart >= 0) {
            start = doubleStart;
            quote = '"';
        } else {
            return null;
        }
        int end = value.indexOf(quote, start + 1);
        if (end <= start + 1) {
            return null;
        }
        String candidate = value.substring(start + 1, end).trim();
        return candidate.matches("[A-Za-z_][A-Za-z0-9_]*") ? candidate : null;
    }

    private int countOccurrences(String value, char needle) {
        int count = 0;
        if (value == null) {
            return 0;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    private List<String> selectPreferredSourceSets(Set<String> buildTypes, Set<String> productFlavors) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        selected.add("main");
        String selectedFlavor = productFlavors.isEmpty() ? null : productFlavors.iterator().next();
        String selectedBuildType = null;
        if (buildTypes.contains("release")) {
            selectedBuildType = "release";
        } else if (buildTypes.contains("debug")) {
            selectedBuildType = "debug";
        } else if (!buildTypes.isEmpty()) {
            selectedBuildType = buildTypes.iterator().next();
        }
        if (!TextUtils.isEmpty(selectedFlavor)) {
            selected.add(selectedFlavor);
        }
        if (!TextUtils.isEmpty(selectedBuildType)) {
            selected.add(selectedBuildType);
        }
        if (!TextUtils.isEmpty(selectedFlavor) && !TextUtils.isEmpty(selectedBuildType)) {
            selected.add(selectedFlavor + Character.toUpperCase(selectedBuildType.charAt(0)) + selectedBuildType.substring(1));
            selected.add(selectedBuildType + Character.toUpperCase(selectedFlavor.charAt(0)) + selectedFlavor.substring(1));
        }
        return new ArrayList<>(selected);
    }

    private AndroidModule choosePrimaryModule(List<AndroidModule> modules) {
        for (AndroidModule module : modules) {
            if (module.applicationModule && hasLauncherManifest(module.getMainManifest())) {
                return module;
            }
        }
        for (AndroidModule module : modules) {
            if (module.applicationModule && "app".equals(module.moduleDirectory.getName())) {
                return module;
            }
        }
        for (AndroidModule module : modules) {
            if (module.applicationModule) {
                return module;
            }
        }
        for (AndroidModule module : modules) {
            if (hasLauncherManifest(module.getMainManifest())) {
                return module;
            }
        }
        return modules.isEmpty() ? null : modules.get(0);
    }

    private boolean hasLauncherManifest(File manifestFile) {
        return manifestFile != null && manifestFile.isFile()
                && FileUtil.readFile(manifestFile.getAbsolutePath()).contains("android.intent.category.LAUNCHER");
    }

    private void collectModuleSources(DetectedProject detectedProject, AndroidModule module) {
        File srcDirectory = new File(module.moduleDirectory, "src");
        File[] sourceSetDirectories = srcDirectory.listFiles(File::isDirectory);
        if (sourceSetDirectories == null) {
            return;
        }

        ArrayList<File> sortedSourceSets = new ArrayList<>(Arrays.asList(sourceSetDirectories));
        sortedSourceSets.sort(this::compareSourceSets);
        LinkedHashSet<String> allowedSourceSetNames = new LinkedHashSet<>(module.preferredSourceSetNames);
        boolean foundPreferredSourceSet = false;
        for (File sourceSetDirectory : sortedSourceSets) {
            if (allowedSourceSetNames.contains(sourceSetDirectory.getName())) {
                foundPreferredSourceSet = true;
                break;
            }
        }

        for (File sourceSetDirectory : sortedSourceSets) {
            if (isTestSourceSet(sourceSetDirectory.getName())) {
                continue;
            }
            if (foundPreferredSourceSet && !allowedSourceSetNames.contains(sourceSetDirectory.getName())) {
                continue;
            }

            File javaRoot = new File(sourceSetDirectory, "java");
            if (javaRoot.isDirectory()) {
                addUniqueFile(detectedProject.sourceRoots, javaRoot);
            }
            File kotlinRoot = new File(sourceSetDirectory, "kotlin");
            if (kotlinRoot.isDirectory()) {
                addUniqueFile(detectedProject.sourceRoots, kotlinRoot);
            }
            File resRoot = new File(sourceSetDirectory, "res");
            if (resRoot.isDirectory()) {
                addUniqueFile(detectedProject.resDirectories, resRoot);
                for (File layoutDirectory : findLayoutDirectories(resRoot)) {
                    addUniqueFile(detectedProject.layoutDirectories, layoutDirectory);
                }
            }
            File assetsRoot = new File(sourceSetDirectory, "assets");
            if (assetsRoot.isDirectory()) {
                addUniqueFile(detectedProject.assetsDirectories, assetsRoot);
            }
            File jniLibsRoot = new File(sourceSetDirectory, "jniLibs");
            if (jniLibsRoot.isDirectory()) {
                addUniqueFile(detectedProject.jniLibsDirectories, jniLibsRoot);
            }
        }
    }

    private int compareSourceSets(File left, File right) {
        int priorityCompare = compareSourceSetNames(left.getName(), right.getName());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return left.getAbsolutePath().compareTo(right.getAbsolutePath());
    }

    private int compareSourceSetNames(String left, String right) {
        int priorityCompare = Integer.compare(sourceSetPriority(left), sourceSetPriority(right));
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return left.compareTo(right);
    }

    private int sourceSetPriority(String name) {
        String lowerName = name.toLowerCase(Locale.US);
        if ("main".equals(lowerName)) {
            return 0;
        }
        if ("debug".equals(lowerName) || lowerName.endsWith("debug")) {
            return 2;
        }
        if ("release".equals(lowerName) || lowerName.endsWith("release")) {
            return 3;
        }
        return 1;
    }

    private boolean isTestSourceSet(String sourceSetName) {
        if (TEST_SOURCE_SET_NAMES.contains(sourceSetName)) {
            return true;
        }
        String normalized = sourceSetName.toLowerCase(Locale.US);
        return normalized.endsWith("test") || normalized.contains("androidtest");
    }

    private List<File> findLayoutDirectories(File resRoot) {
        File[] children = resRoot.listFiles(File::isDirectory);
        if (children == null) {
            return Collections.emptyList();
        }
        ArrayList<File> layoutDirectories = new ArrayList<>();
        for (File child : children) {
            if (child.getName().startsWith("layout")) {
                layoutDirectories.add(child);
            }
        }
        layoutDirectories.sort((left, right) -> {
            int qualifierCompare = Integer.compare(layoutDirectoryPreference(left), layoutDirectoryPreference(right));
            if (qualifierCompare != 0) {
                return qualifierCompare;
            }
            return left.getName().compareTo(right.getName());
        });
        return layoutDirectories;
    }

    private int layoutDirectoryPreference(File directory) {
        return "layout".equals(directory.getName()) ? 1 : 0;
    }

    private ManifestSummary parseManifest(DetectedProject detectedProject) throws Exception {
        ManifestSummary summary = new ManifestSummary();
        if (detectedProject.primaryManifestFile == null || !detectedProject.primaryManifestFile.isFile()) {
            throw new IOException("No AndroidManifest.xml was found for the selected project");
        }
        summary.rawXml = buildMergedManifestXml(detectedProject.primaryManifestFile,
                detectedProject.primaryManifestOverlayFiles, detectedProject.libraryManifestFiles);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(
                new java.io.ByteArrayInputStream(summary.rawXml.getBytes(StandardCharsets.UTF_8)));
        Element manifestElement = document.getDocumentElement();
        summary.packageName = manifestElement.getAttribute("package");

        collectManifestPermissions(summary.permissions, document, "uses-permission");
        collectManifestPermissions(summary.permissions, document, "uses-permission-sdk-23");
        collectManifestPermissions(summary.permissions, document, "uses-permission-sdk-m");

        NodeList applicationNodes = document.getElementsByTagName("application");
        if (applicationNodes.getLength() > 0) {
            Element applicationElement = (Element) applicationNodes.item(0);
            String label = getAndroidAttribute(applicationElement, "label");
            summary.applicationLabel = resolveManifestLabel(label, detectedProject.resDirectories);
            summary.applicationIconRef = normalizeResourceReference(getAndroidAttribute(applicationElement, "icon"));
            summary.roundIconRef = normalizeResourceReference(getAndroidAttribute(applicationElement, "roundIcon"));
            Set<String> seenActivityFqns = new HashSet<>();
            NodeList childNodes = applicationElement.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                if (!(child instanceof Element)) {
                    continue;
                }
                Element childElement = (Element) child;
                switch (childElement.getTagName()) {
                    case "activity" -> {
                        ManifestActivity activity = parseManifestActivity(childElement, summary.packageName);
                        if (seenActivityFqns.add(activity.fullyQualifiedName)) {
                            summary.activities.add(activity);
                        }
                    }
                    case "service" -> addComponentName(summary.services, childElement, summary.packageName);
                    case "receiver" -> addComponentName(summary.receivers, childElement, summary.packageName);
                    case "provider" -> addComponentName(summary.providers, childElement, summary.packageName);
                }
            }
        }
        return summary;
    }

    private void collectManifestPermissions(List<String> out, Document document, String tagName) {
        NodeList usesPermissions = document.getElementsByTagName(tagName);
        for (int i = 0; i < usesPermissions.getLength(); i++) {
            Element permissionElement = (Element) usesPermissions.item(i);
            String permissionName = getAndroidAttribute(permissionElement, "name");
            if (!TextUtils.isEmpty(permissionName) && !out.contains(permissionName)) {
                out.add(permissionName);
            }
        }
    }

    private String buildMergedManifestXml(File baseManifestFile, List<File> overlayManifestFiles,
                                          List<File> libraryManifestFiles) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document baseDocument = factory.newDocumentBuilder().parse(baseManifestFile);
            Element baseManifest = baseDocument.getDocumentElement();
            Element baseApplication = (Element) baseDocument.getElementsByTagName("application").item(0);
            for (File overlayManifest : overlayManifestFiles) {
                baseApplication = mergeManifestInto(baseDocument, baseManifest, baseApplication, overlayManifest, true);
            }
            for (File libraryManifest : libraryManifestFiles) {
                baseApplication = mergeManifestInto(baseDocument, baseManifest, baseApplication, libraryManifest, false);
            }
            return documentToString(baseDocument);
        } catch (Exception e) {
            Log.e(TAG, "Failed to merge manifests", e);
            return FileUtil.readFile(baseManifestFile.getAbsolutePath());
        }
    }

    private Element mergeManifestInto(Document baseDocument, Element baseManifest, Element baseApplication,
                                      File manifestFile, boolean overrideExisting) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document overlayDocument = factory.newDocumentBuilder().parse(manifestFile);
        Element overlayManifestElement = overlayDocument.getDocumentElement();
        mergeManifestAttributes(baseManifest, overlayManifestElement, overrideExisting);

        NodeList overlayChildren = overlayManifestElement.getChildNodes();
        for (int i = 0; i < overlayChildren.getLength(); i++) {
            Node node = overlayChildren.item(i);
            if (!(node instanceof Element overlayElement)) {
                continue;
            }
            if ("application".equals(overlayElement.getTagName())) {
                if (baseApplication == null) {
                    baseApplication = (Element) baseDocument.importNode(overlayElement, true);
                    baseManifest.appendChild(baseApplication);
                } else {
                    mergeApplicationElement(baseDocument, baseApplication, overlayElement, overrideExisting);
                }
                continue;
            }
            if ("queries".equals(overlayElement.getTagName())) {
                mergeContainerElement(baseDocument, baseManifest, overlayElement);
                continue;
            }
            Element existingElement = findManifestElement(baseManifest, overlayElement);
            if (existingElement != null) {
                mergeManifestElement(baseDocument, existingElement, overlayElement, overrideExisting);
            } else {
                baseManifest.appendChild(baseDocument.importNode(overlayElement, true));
            }
        }
        return baseApplication;
    }

    private void mergeManifestAttributes(Element baseManifest, Element overlayManifest, boolean overrideExisting) {
        NamedNodeMap attributes = overlayManifest.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if ("package".equals(attribute.getNodeName()) && baseManifest.hasAttribute("package")) {
                continue;
            }
            if (overrideExisting || !baseManifest.hasAttribute(attribute.getNodeName())) {
                baseManifest.setAttribute(attribute.getNodeName(), attribute.getNodeValue());
            }
        }
    }

    private void mergeApplicationElement(Document baseDocument, Element baseApplication, Element overlayApplication,
                                         boolean overrideExisting) {
        NamedNodeMap attributes = overlayApplication.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (overrideExisting || !baseApplication.hasAttribute(attribute.getNodeName())) {
                baseApplication.setAttribute(attribute.getNodeName(), attribute.getNodeValue());
            }
        }
        NodeList applicationChildren = overlayApplication.getChildNodes();
        for (int i = 0; i < applicationChildren.getLength(); i++) {
            Node node = applicationChildren.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            Element existingChild = findManifestElement(baseApplication, element);
            if (existingChild != null) {
                mergeManifestElement(baseDocument, existingChild, element, overrideExisting);
            } else {
                baseApplication.appendChild(baseDocument.importNode(element, true));
            }
        }
    }

    private Element findManifestElement(Element parent, Element candidate) {
        String candidateTag = candidate.getTagName();
        String candidateName = getAndroidAttribute(candidate, "name");
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element childElement)) {
                continue;
            }
            if (!candidateTag.equals(childElement.getTagName())) {
                continue;
            }
            String childName = getAndroidAttribute(childElement, "name");
            if (!TextUtils.isEmpty(candidateName) && candidateName.equals(childName)) {
                return childElement;
            }
            if (TextUtils.isEmpty(candidateName) && attributesSignature(candidate).equals(attributesSignature(childElement))) {
                return childElement;
            }
        }
        return null;
    }

    private void mergeManifestElement(Document baseDocument, Element baseElement, Element overlayElement,
                                      boolean overrideExisting) {
        NamedNodeMap attributes = overlayElement.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (overrideExisting || !baseElement.hasAttribute(attribute.getNodeName())) {
                baseElement.setAttribute(attribute.getNodeName(), attribute.getNodeValue());
            }
        }

        NodeList children = overlayElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element childElement)) {
                continue;
            }
            Element existingChild = findManifestElement(baseElement, childElement);
            if (existingChild != null) {
                mergeManifestElement(baseDocument, existingChild, childElement, overrideExisting);
            } else {
                baseElement.appendChild(baseDocument.importNode(childElement, true));
            }
        }
    }

    private void mergeContainerElement(Document baseDocument, Element parent, Element overlayContainer) {
        Element targetContainer = null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement && overlayContainer.getTagName().equals(childElement.getTagName())) {
                targetContainer = childElement;
                break;
            }
        }
        if (targetContainer == null) {
            parent.appendChild(baseDocument.importNode(overlayContainer, true));
            return;
        }
        NodeList containerChildren = overlayContainer.getChildNodes();
        for (int i = 0; i < containerChildren.getLength(); i++) {
            Node child = containerChildren.item(i);
            if (!(child instanceof Element childElement)) {
                continue;
            }
            Element existingChild = findManifestElement(targetContainer, childElement);
            if (existingChild != null) {
                mergeManifestElement(baseDocument, existingChild, childElement, true);
            } else {
                targetContainer.appendChild(baseDocument.importNode(childElement, true));
            }
        }
    }

    private boolean containsManifestElement(Element parent, Element candidate) {
        return findManifestElement(parent, candidate) != null;
    }

    private String attributesSignature(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        StringBuilder signature = new StringBuilder(element.getTagName());
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            signature.append('|').append(attribute.getNodeName()).append('=').append(attribute.getNodeValue());
        }
        return signature.toString();
    }

    private String documentToString(Document document) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private ManifestActivity parseManifestActivity(Element activityElement, String packageName) {
        ManifestActivity activity = new ManifestActivity();
        activity.fullyQualifiedName = resolveClassName(packageName, getAndroidAttribute(activityElement, "name"));
        activity.launcher = hasLauncherIntent(activityElement);
        return activity;
    }

    private boolean hasLauncherIntent(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) {
                continue;
            }
            Element intentFilter = (Element) child;
            if (!"intent-filter".equals(intentFilter.getTagName())) {
                continue;
            }
            boolean hasMain = false;
            boolean hasLauncher = false;
            NodeList filters = intentFilter.getChildNodes();
            for (int j = 0; j < filters.getLength(); j++) {
                Node filterNode = filters.item(j);
                if (!(filterNode instanceof Element)) {
                    continue;
                }
                Element filterElement = (Element) filterNode;
                if ("action".equals(filterElement.getTagName())
                        && "android.intent.action.MAIN".equals(getAndroidAttribute(filterElement, "name"))) {
                    hasMain = true;
                }
                if ("category".equals(filterElement.getTagName())
                        && "android.intent.category.LAUNCHER".equals(getAndroidAttribute(filterElement, "name"))) {
                    hasLauncher = true;
                }
            }
            if (hasMain && hasLauncher) {
                return true;
            }
        }
        return false;
    }

    private void addComponentName(List<String> out, Element element, String manifestPackage) {
        String name = resolveClassName(manifestPackage, getAndroidAttribute(element, "name"));
        if (!TextUtils.isEmpty(name)) {
            out.add(name);
        }
    }

    private String getAndroidAttribute(Element element, String attribute) {
        String value = element.getAttributeNS(ANDROID_NS, attribute);
        if (TextUtils.isEmpty(value)) {
            value = element.getAttribute("android:" + attribute);
        }
        if (TextUtils.isEmpty(value)) {
            value = element.getAttribute(attribute);
        }
        return value;
    }

    private String resolveManifestLabel(String labelValue, List<File> resDirectories) {
        if (TextUtils.isEmpty(labelValue)) {
            return null;
        }
        if (!labelValue.startsWith("@string/")) {
            return labelValue;
        }
        String key = labelValue.substring("@string/".length());
        for (int i = resDirectories.size() - 1; i >= 0; i--) {
            File stringsXml = new File(resDirectories.get(i), "values/strings.xml");
            if (!stringsXml.isFile()) {
                continue;
            }
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                Document document = factory.newDocumentBuilder().parse(stringsXml);
                NodeList strings = document.getElementsByTagName("string");
                for (int j = 0; j < strings.getLength(); j++) {
                    Element element = (Element) strings.item(j);
                    if (key.equals(element.getAttribute("name"))) {
                        return element.getTextContent();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return key;
    }

    private String normalizeResourceReference(String value) {
        if (TextUtils.isEmpty(value) || !value.startsWith("@")) {
            return null;
        }
        String normalized = value.substring(1);
        int packageSeparator = normalized.indexOf(':');
        if (packageSeparator >= 0) {
            normalized = normalized.substring(packageSeparator + 1);
        }
        int typeSeparator = normalized.indexOf('/');
        if (typeSeparator <= 0 || typeSeparator + 1 >= normalized.length()) {
            return null;
        }
        return normalized.substring(0, typeSeparator) + ":" + normalized.substring(typeSeparator + 1);
    }

    private String resolveClassName(String manifestPackage, String value) {
        if (TextUtils.isEmpty(value)) {
            return value;
        }
        if (value.startsWith(".")) {
            return manifestPackage + value;
        }
        if (!value.contains(".")) {
            return manifestPackage + "." + value;
        }
        return value;
    }

    private GradleSummary parseGradle(DetectedProject detectedProject) {
        GradleSummary summary = new GradleSummary();
        if (detectedProject.primaryGradleFile == null || !detectedProject.primaryGradleFile.isFile()) {
            summary.warnings.add("No module Gradle file was found; SDK versions and dependency import may be incomplete.");
            return summary;
        }
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();

        Map<String, String> gradleVariables = extractGradleVariables(detectedProject.gradleFiles);
        Map<String, String> versionCatalog = parseVersionCatalog(detectedProject.rootDirectory);

        for (File gradleFile : detectedProject.gradleFiles) {
            if (gradleFile == null || !gradleFile.isFile()) {
                continue;
            }
            String content = FileUtil.readFile(gradleFile.getAbsolutePath());
            if (summary.applicationId == null) {
                summary.applicationId = findFirstValue(content, Arrays.asList("applicationId"));
            }
            if (summary.namespace == null) {
                summary.namespace = findFirstValue(content, Arrays.asList("namespace"));
            }
            if ((summary.applicationId == null || summary.namespace == null) && content.contains("applicationIdSuffix")) {
                summary.warnings.add("applicationIdSuffix was detected; the imported project uses variant-specific application IDs. Sketchware imported the base namespace/applicationId only.");
            }
            if (summary.versionCode == null) {
                summary.versionCode = findFirstNumeric(content, Arrays.asList("versionCode"));
            }
            if (summary.versionName == null) {
                summary.versionName = findFirstValue(content, Arrays.asList("versionName"));
            }
            if (summary.minSdk <= 0) {
                summary.minSdk = parseInt(findFirstNumeric(content, Arrays.asList("minSdk", "minSdkVersion")), 0);
            }
            if (summary.targetSdk <= 0) {
                summary.targetSdk = parseInt(findFirstNumeric(content, Arrays.asList("targetSdk", "targetSdkVersion")), 0);
            }
            summary.material3Detected |= content.contains("com.google.android.material:material") || content.contains("Theme.Material3") || content.contains("ThemeOverlay.Material3");
            summary.appCompatDetected |= content.contains("androidx.appcompat") || content.contains("com.google.android.material:material") || content.contains("Theme.MaterialComponents") || content.contains("Theme.AppCompat") || summary.material3Detected;
            summary.viewBindingDetected |= content.contains("viewBinding = true") || content.contains("viewBinding.enabled = true") || content.contains("viewBinding true") || content.contains("buildFeatures.viewBinding");
            summary.gsonDetected |= content.contains("com.google.code.gson:gson");
            Matcher activityThemeMatcher = ACTIVITY_THEME_PATTERN.matcher(content);
            if (activityThemeMatcher.find()) {
                summary.detectedThemeFamily = activityThemeMatcher.group(1);
                String tail = activityThemeMatcher.group(2) == null ? "" : activityThemeMatcher.group(2);
                if (tail.contains("DayNight")) {
                    summary.detectedThemeMode = "DayNight";
                } else if (tail.contains("Light")) {
                    summary.detectedThemeMode = "Light";
                } else if (tail.contains("Dark")) {
                    summary.detectedThemeMode = "Dark";
                }
            }
            if ((content.contains("compose = true") || content.contains("buildFeatures.compose") || content.contains("androidx.compose"))
                    && !summary.warnings.contains("Jetpack Compose was detected. The project is imported in code mode; Compose is preserved but not reconstructed visually.")) {
                summary.warnings.add("Jetpack Compose was detected. The project is imported in code mode; Compose is preserved but not reconstructed visually.");
            }
            if ((content.contains("ksp(") || content.contains("com.google.devtools.ksp"))
                    && !summary.warnings.contains("KSP-generated sources were detected. Generated code may need regeneration outside Sketchware Pro.")) {
                summary.warnings.add("KSP-generated sources were detected. Generated code may need regeneration outside Sketchware Pro.");
            }
            if ((content.contains("dagger.hilt") || content.contains("com.google.dagger.hilt"))
                    && !summary.warnings.contains("Hilt was detected. Manifest and generated code are preserved, but project-specific Hilt tooling may still need review.")) {
                summary.warnings.add("Hilt was detected. Manifest and generated code are preserved, but project-specific Hilt tooling may still need review.");
            }
            if ((content.contains("externalNativeBuild") || content.contains("cmake") || content.contains("ndkBuild"))
                    && detectedProject.jniLibsDirectories.isEmpty()
                    && !summary.warnings.contains("Native source builds were detected. Checked-in/prebuilt jniLibs are imported, but compiling NDK/CMake sources still requires Android Studio or another external native build environment.")) {
                summary.warnings.add("Native source builds were detected. Checked-in/prebuilt jniLibs are imported, but compiling NDK/CMake sources still requires Android Studio or another external native build environment.");
            }

            // Match standard "group:artifact:version" dependencies (with variable version resolution)
            Matcher dependencyMatcher = DEPENDENCY_PATTERN.matcher(content);
            while (dependencyMatcher.find()) {
                String configuration = dependencyMatcher.group(1);
                if ("compileOnly".equals(configuration)) {
                    continue;
                }
                String version = resolveVersionVariable(dependencyMatcher.group(4), gradleVariables);
                dependencies.add(dependencyMatcher.group(2) + ":" + dependencyMatcher.group(3) + ":" + version);
            }

            // Match Version Catalog accessor references: implementation(libs.retrofit)
            if (!versionCatalog.isEmpty()) {
                Matcher catalogMatcher = CATALOG_ACCESSOR_PATTERN.matcher(content);
                while (catalogMatcher.find()) {
                    String configuration = catalogMatcher.group(1);
                    if ("compileOnly".equals(configuration)) continue;
                    String resolved = resolveCatalogAlias(catalogMatcher.group(2), versionCatalog);
                    if (resolved != null) {
                        dependencies.add(resolved);
                    }
                }
            }

            // Match platform/BOM dependencies: implementation(platform("group:artifact:version"))
            Matcher bomMatcher = BOM_DEPENDENCY_PATTERN.matcher(content);
            while (bomMatcher.find()) {
                dependencies.add(bomMatcher.group(2) + ":" + bomMatcher.group(3) + ":" + bomMatcher.group(4));
            }
        }
        scanVersionCatalogs(detectedProject.rootDirectory, summary);
        summary.appCompatDetected |= summary.material3Detected;
        summary.dependencies.addAll(dependencies);
        return summary;
    }

    private String findFirstValue(String content, List<String> keys) {
        for (String key : keys) {
            Matcher matcher = Pattern.compile("(?m)^[\\t ]*" + Pattern.quote(key) + "[\\t ]*=?[\\t ]*['\"]([^'\"]+)['\"]").matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        Matcher matcher = STRING_ASSIGNMENT.matcher(content);
        Map<String, String> values = new HashMap<>();
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2));
        }
        for (String key : keys) {
            String value = values.get(key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    private void scanVersionCatalogs(File projectRoot, GradleSummary summary) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return;
        }
        ArrayList<File> catalogs = new ArrayList<>();
        File gradleDirectory = new File(projectRoot, "gradle");
        if (gradleDirectory.isDirectory()) {
            File[] children = gradleDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isFile() && child.getName().endsWith(".toml")) {
                        catalogs.add(child);
                    }
                }
            }
        }
        for (File catalog : catalogs) {
            String content = FileUtil.readFile(catalog.getAbsolutePath());
            if (TextUtils.isEmpty(content)) {
                continue;
            }
            if (content.contains("androidx.appcompat") || content.contains("appcompat")) {
                summary.appCompatDetected = true;
            }
            if (content.contains("com.google.android.material") || content.contains("material3")) {
                summary.material3Detected = true;
                summary.appCompatDetected = true;
            }
        }
    }

    /**
     * Parses all .toml version catalog files under gradle/ and returns a map of
     * alias → "group:artifact:version" Maven coordinate.
     */
    private Map<String, String> parseVersionCatalog(File projectRoot) {
        Map<String, String> catalog = new LinkedHashMap<>();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return catalog;
        }
        File gradleDir = new File(projectRoot, "gradle");
        if (!gradleDir.isDirectory()) {
            return catalog;
        }
        File[] tomlFiles = gradleDir.listFiles((dir, name) -> name.endsWith(".toml"));
        if (tomlFiles == null) {
            return catalog;
        }
        for (File tomlFile : tomlFiles) {
            String content = FileUtil.readFile(tomlFile.getAbsolutePath());
            if (!TextUtils.isEmpty(content)) {
                parseTomlLibraries(content, catalog);
            }
        }
        return catalog;
    }

    /**
     * Parses a TOML version catalog, populating result with alias → "group:artifact:version".
     * Handles [versions] for version references and [libraries] with inline-table and string forms.
     */
    private void parseTomlLibraries(String content, Map<String, String> result) {
        Map<String, String> versions = new LinkedHashMap<>();
        String[] lines = content.split("\\n");
        String currentSection = "";

        // First pass: collect [versions]
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[")) {
                currentSection = line.replaceAll("[\\[\\]]", "").trim().toLowerCase(Locale.US);
                continue;
            }
            if ("versions".equals(currentSection)) {
                Matcher m = Pattern.compile("^([A-Za-z0-9_.\\-]+)\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(line);
                if (m.find()) {
                    versions.put(m.group(1), m.group(2).trim());
                }
            }
        }

        // Second pass: collect [libraries]
        currentSection = "";
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[")) {
                currentSection = line.replaceAll("[\\[\\]]", "").trim().toLowerCase(Locale.US);
                continue;
            }
            if (!"libraries".equals(currentSection)) continue;

            int eqIdx = line.indexOf("=");
            if (eqIdx < 0) continue;
            String alias = line.substring(0, eqIdx).trim();
            String valuePart = line.substring(eqIdx + 1).trim();

            // Form 1: alias = "group:artifact:version"
            Matcher shortForm = Pattern.compile("^['\"]([A-Za-z0-9._\\-]+\\.[A-Za-z0-9._\\-]+):([A-Za-z0-9._\\-]+):([^'\"]+)['\"]$").matcher(valuePart);
            if (shortForm.find()) {
                result.put(alias, shortForm.group(1) + ":" + shortForm.group(2) + ":" + shortForm.group(3).trim());
                continue;
            }

            // Form 2: inline table { ... }
            if (valuePart.startsWith("{")) {
                String group = null, name = null, ver = null;

                // module = "group:artifact" shorthand
                Matcher modM = Pattern.compile("module\\s*=\\s*['\"]([^:\"']+):([^\"']+)['\"]").matcher(valuePart);
                if (modM.find()) {
                    group = modM.group(1).trim();
                    name = modM.group(2).trim();
                } else {
                    Matcher gm = Pattern.compile("group\\s*=\\s*['\"]([^\"']+)['\"]").matcher(valuePart);
                    Matcher nm = Pattern.compile("\\bname\\s*=\\s*['\"]([^\"']+)['\"]").matcher(valuePart);
                    if (gm.find()) group = gm.group(1).trim();
                    if (nm.find()) name = nm.group(1).trim();
                }

                // version.ref = "alias" or version = "literal"
                Matcher vrm = Pattern.compile("version\\.ref\\s*=\\s*['\"]([^\"']+)['\"]").matcher(valuePart);
                if (vrm.find()) {
                    ver = versions.get(vrm.group(1));
                } else {
                    Matcher vdm = Pattern.compile("(?<![.])version\\s*=\\s*['\"]([^\"']+)['\"]").matcher(valuePart);
                    if (vdm.find()) {
                        ver = vdm.group(1).trim();
                    }
                }

                if (group != null && name != null && ver != null) {
                    result.put(alias, group + ":" + name + ":" + ver);
                }
            }
        }
    }

    /**
     * Resolves a Version Catalog accessor (e.g. "retrofit.converter.gson") to a Maven coordinate
     * by trying hyphens, dots, and underscores as separators.
     */
    private String resolveCatalogAlias(String accessor, Map<String, String> catalog) {
        if (accessor == null || catalog.isEmpty()) return null;
        // Most common: dots → hyphens (e.g. libs.retrofit.converter.gson → retrofit-converter-gson)
        String withHyphens = accessor.replace('.', '-');
        String resolved = catalog.get(withHyphens);
        if (resolved != null) return resolved;
        // Try literal (e.g. alias already uses dots in catalog key)
        resolved = catalog.get(accessor);
        if (resolved != null) return resolved;
        // Try underscores
        resolved = catalog.get(accessor.replace('.', '_'));
        return resolved;
    }

    /**
     * Collects variable declarations (def/val/var and ext-block assignments) from all Gradle files.
     * Used to resolve $variable references in dependency version strings.
     */
    private Map<String, String> extractGradleVariables(List<File> gradleFiles) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (File gradleFile : gradleFiles) {
            if (gradleFile == null || !gradleFile.isFile()) continue;
            String content = FileUtil.readFile(gradleFile.getAbsolutePath());
            if (TextUtils.isEmpty(content)) continue;

            // def/val/var declarations
            Matcher varMatcher = GRADLE_VARIABLE_PATTERN.matcher(content);
            while (varMatcher.find()) {
                variables.putIfAbsent(varMatcher.group(1), varMatcher.group(2));
            }

            // key = "value" assignments that look like version strings (contain a digit)
            Matcher assignMatcher = STRING_ASSIGNMENT.matcher(content);
            while (assignMatcher.find()) {
                String key = assignMatcher.group(1);
                String value = assignMatcher.group(2);
                if (!variables.containsKey(key) && value.matches(".*\\d.*")) {
                    variables.put(key, value);
                }
            }
        }
        return variables;
    }

    /**
     * Replaces $varName and ${varName} in a Gradle version string using the provided variables map.
     * Returns the original string unchanged if no substitution is possible.
     */
    private String resolveVersionVariable(String version, Map<String, String> variables) {
        if (version == null || !version.contains("$") || variables.isEmpty()) return version;

        // ${varName}
        Matcher bracesMatcher = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}").matcher(version);
        StringBuffer sb = new StringBuffer();
        while (bracesMatcher.find()) {
            String varValue = variables.get(bracesMatcher.group(1));
            bracesMatcher.appendReplacement(sb, varValue != null
                    ? Matcher.quoteReplacement(varValue) : Matcher.quoteReplacement(bracesMatcher.group(0)));
        }
        bracesMatcher.appendTail(sb);
        version = sb.toString();

        // $varName (no braces)
        Matcher dollarMatcher = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)").matcher(version);
        sb = new StringBuffer();
        while (dollarMatcher.find()) {
            String varValue = variables.get(dollarMatcher.group(1));
            dollarMatcher.appendReplacement(sb, varValue != null
                    ? Matcher.quoteReplacement(varValue) : Matcher.quoteReplacement(dollarMatcher.group(0)));
        }
        dollarMatcher.appendTail(sb);
        return sb.toString();
    }

    private String findFirstNumeric(String content, List<String> keys) {
        if (keys == null || keys.isEmpty()) return null;

        Pattern pattern = NUMERIC_PATTERN_CACHE.computeIfAbsent(keys, k -> {
            StringBuilder patternBuilder = new StringBuilder("(?m)^[\\t ]*(");
            for (int i = 0; i < k.size(); i++) {
                if (i > 0) patternBuilder.append("|");
                patternBuilder.append(Pattern.quote(k.get(i)));
            }
            patternBuilder.append(")[\\t ]*=?[\\t ]*([0-9]+)");
            return Pattern.compile(patternBuilder.toString());
        });

        Matcher matcher = pattern.matcher(content);

        String[] results = new String[keys.size()];
        int matches = 0;
        while (matcher.find()) {
            String matchKey = matcher.group(1);
            int idx = keys.indexOf(matchKey);
            if (idx != -1) {
                if (results[idx] == null) {
                    results[idx] = matcher.group(2).trim();
                    matches++;
                    if (idx == 0) return results[idx];
                }
            }
        }

        if (matches > 0) {
            for (String res : results) {
                if (res != null) return res;
            }
        }
        return null;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void writeImportedComponentIndexes(String scId, ManifestSummary manifest) {
        ArrayList<String> activityNames = new ArrayList<>();
        for (ManifestActivity activity : manifest.activities) {
            if (!TextUtils.isEmpty(activity.fullyQualifiedName)) {
                activityNames.add(activity.fullyQualifiedName);
            }
        }
        FileUtil.writeFile(filePathUtil.getManifestJava(scId), gson.toJson(activityNames));
        FileUtil.writeFile(filePathUtil.getManifestService(scId), gson.toJson(new ArrayList<>(manifest.services)));
        FileUtil.writeFile(filePathUtil.getManifestBroadcast(scId), gson.toJson(new ArrayList<>(manifest.receivers)));
    }

    private boolean detectViewBindingInSources(List<File> sourceRoots) {
        for (File sourceRoot : sourceRoots) {
            if (sourceRoot == null || !sourceRoot.isDirectory()) continue;
            for (File f : collectFilesRecursively(sourceRoot)) {
                if (!f.isFile()) continue;
                String name = f.getName().toLowerCase(Locale.US);
                if (!name.endsWith(".java") && !name.endsWith(".kt")) continue;
                String src = FileUtil.readFile(f.getAbsolutePath());
                if (src.contains("Binding.inflate(") || src.contains("Binding.bind(")
                        || src.contains("ActivityBinding") || src.contains("FragmentBinding")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void applyDetectedThemeAndLibraryState(String scId, GradleSummary gradle, ManifestSummary manifest,
                                                   DetectedProject detectedProject, ImportResult result,
                                                   boolean expressiveDetected) {
        boolean appCompatDetected = gradle.appCompatDetected;
        boolean material3Detected = gradle.material3Detected;
        String themeMode = gradle.detectedThemeMode;

        for (File resDirectory : detectedProject.resDirectories) {
            File[] valuesDirectories = resDirectory.listFiles(File::isDirectory);
            if (valuesDirectories == null) {
                continue;
            }
            for (File valuesDirectory : valuesDirectories) {
                if (!valuesDirectory.getName().startsWith("values")) {
                    continue;
                }
                File[] valueFiles = valuesDirectory.listFiles();
                if (valueFiles == null) {
                    continue;
                }
                for (File valueFile : valueFiles) {
                    if (valueFile == null || !valueFile.isFile() || !valueFile.getName().endsWith(".xml")) {
                        continue;
                    }
                    String stylesContent = FileUtil.readFile(valueFile.getAbsolutePath());
                    if (stylesContent.contains("Theme.Material3") || stylesContent.contains("ThemeOverlay.Material3")) {
                        material3Detected = true;
                        appCompatDetected = true;
                    }
                    if (stylesContent.contains("Theme.MaterialComponents") || stylesContent.contains("Theme.AppCompat")) {
                        appCompatDetected = true;
                    }
                    Matcher themeMatcher = ACTIVITY_THEME_PATTERN.matcher(stylesContent);
                    if (themeMatcher.find() && TextUtils.isEmpty(themeMode)) {
                        String tail = themeMatcher.group(2) == null ? "" : themeMatcher.group(2);
                        if (tail.contains("DayNight")) {
                            themeMode = "DayNight";
                        } else if (tail.contains("Light")) {
                            themeMode = "Light";
                        } else if (tail.contains("Dark")) {
                            themeMode = "Dark";
                        }
                    }
                }
            }
        }

        if (!(appCompatDetected || material3Detected)) {
            return;
        }

        try {
            a.a.a.iC libraryManager = jC.c(scId);
            com.besome.sketch.beans.ProjectLibraryBean compatBean = libraryManager.c();
            if (compatBean == null) {
                compatBean = new com.besome.sketch.beans.ProjectLibraryBean(com.besome.sketch.beans.ProjectLibraryBean.PROJECT_LIB_TYPE_COMPAT);
            }
            compatBean.useYn = com.besome.sketch.beans.ProjectLibraryBean.LIB_USE_Y;
            compatBean.configurations.put("material3", material3Detected);
            compatBean.configurations.put("dynamic_colors", material3Detected && detectDynamicColorsUsage(detectedProject.sourceRoots, detectedProject.resDirectories));
            compatBean.configurations.put("theme", TextUtils.isEmpty(themeMode) ? "DayNight" : themeMode);
            boolean enableExpressive = material3Detected && expressiveDetected;
            compatBean.configurations.put("material3_expressive", enableExpressive);
            libraryManager.a(compatBean);
            libraryManager.k();
            String libraryNote = enableExpressive
                    ? "Enabled AppCompat + Material3 + Material3 Expressive project library settings based on imported resources."
                    : material3Detected
                    ? "Enabled AppCompat + Material3 project library settings based on imported Gradle/theme resources."
                    : "Enabled AppCompat project library settings based on imported Gradle/theme resources.";
            result.warnings.add(libraryNote);
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to apply detected library state", throwable);
            result.warnings.add("Imported project themes suggest AppCompat/Material3, but Sketchware library settings could not be updated automatically.");
        }

        if (gradle.gsonDetected || detectGsonInSources(detectedProject.sourceRoots)) {
            try {
                EnableBuiltInLibrariesActivity.enableBuiltInLibrary(scId, mod.jbk.build.BuiltInLibraries.GSON);
                result.warnings.add("Enabled built-in gson library based on project dependencies.");
            } catch (Throwable throwable) {
                Log.e(TAG, "Failed to enable gson library", throwable);
            }
        }
    }

    private boolean detectGsonInSources(List<File> sourceRoots) {
        for (File sourceRoot : sourceRoots) {
            if (sourceRoot == null || !sourceRoot.isDirectory()) continue;
            for (File f : collectFilesRecursively(sourceRoot)) {
                if (!f.isFile()) continue;
                String name = f.getName().toLowerCase(Locale.US);
                if (!name.endsWith(".java") && !name.endsWith(".kt")) continue;
                String src = FileUtil.readFile(f.getAbsolutePath());
                if (src.contains("import com.google.gson") || src.contains("new Gson()")
                        || src.contains("GsonBuilder")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean detectDynamicColorsUsage(List<File> sourceRoots, List<File> resDirectories) {
        for (File sourceRoot : sourceRoots) {
            if (sourceRoot == null || !sourceRoot.isDirectory()) {
                continue;
            }
            for (File sourceFile : collectFilesRecursively(sourceRoot)) {
                if (!sourceFile.isFile()) {
                    continue;
                }
                String lowerName = sourceFile.getName().toLowerCase(Locale.US);
                if (!(lowerName.endsWith(".java") || lowerName.endsWith(".kt"))) {
                    continue;
                }
                String content = FileUtil.readFile(sourceFile.getAbsolutePath());
                if (content.contains("DynamicColors") || content.contains("dynamicColors")) {
                    return true;
                }
            }
        }
        for (File resDirectory : resDirectories) {
            File[] valuesDirectories = resDirectory.listFiles(File::isDirectory);
            if (valuesDirectories == null) {
                continue;
            }
            for (File valuesDirectory : valuesDirectories) {
                if (!valuesDirectory.getName().startsWith("values")) {
                    continue;
                }
                File[] children = valuesDirectory.listFiles();
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    if (child.isFile() && child.getName().endsWith(".xml")) {
                        String content = FileUtil.readFile(child.getAbsolutePath());
                        if (content.contains("material_dynamic") || content.contains("dynamicColor")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean detectExpressiveUsage(List<File> resDirectories) {
        for (File resDir : resDirectories) {
            if (resDir == null || !resDir.isDirectory()) continue;
            for (File file : collectFilesRecursively(resDir)) {
                if (!file.isFile() || !file.getName().endsWith(".xml")) continue;
                String content = FileUtil.readFile(file.getAbsolutePath());
                if (!TextUtils.isEmpty(content) && content.contains("Widget.Material3Expressive.")) {
                    return true;
                }
            }
        }
        return false;
    }

    private GitHubRepoSpec fetchRepoSpec(String repoUrl, String branch) {
        return GitHubRepoSpec.parse(repoUrl, branch);
    }

    private String fetchDefaultBranch(GitHubRepoSpec spec, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/" + spec.owner + "/" + spec.repo).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Sketchware-Pro-Importer");
        if (!TextUtils.isEmpty(token)) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("GitHub API request failed with HTTP " + responseCode);
        }
        String response = readFully(connection.getInputStream());
        JSONObject jsonObject = new JSONObject(response);
        return jsonObject.getString("default_branch");
    }

    private void downloadGitHubArchive(GitHubRepoSpec spec, String token, File targetZip) throws Exception {
        String archiveUrl = "https://codeload.github.com/" + spec.owner + "/" + spec.repo + "/zip/refs/heads/" + spec.branch;
        HttpURLConnection connection = (HttpURLConnection) new URL(archiveUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Sketchware-Pro-Importer");
        if (!TextUtils.isEmpty(token)) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("GitHub archive download failed with HTTP " + responseCode);
        }
        try (InputStream inputStream = connection.getInputStream()) {
            copyStreamToFileWithProgress(inputStream, targetZip, connection.getContentLengthLong(),
                    "Downloading GitHub archive", spec.owner + "/" + spec.repo + "#" + spec.branch);
        } finally {
            connection.disconnect();
        }
    }

    private void safeExtract(File zipFile, File outputDirectory) throws IOException {
        FileUtil.makeDir(outputDirectory.getAbsolutePath());
        String canonicalRoot = outputDirectory.getCanonicalPath() + File.separator;
        long totalExtractedBytes = 0L;
        int extractedFiles = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String safeName = sanitizeZipEntryName(entry.getName());
                if (safeName == null) {
                    zipInputStream.closeEntry();
                    continue;
                }
                File target = new File(outputDirectory, safeName);
                String canonicalTarget = target.getCanonicalPath();
                if (!canonicalTarget.startsWith(canonicalRoot) && !canonicalTarget.equals(outputDirectory.getCanonicalPath())) {
                    throw new IOException("Unsafe archive entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    extractedFiles++;
                    if (extractedFiles > MAX_EXTRACTED_FILES) {
                        throw new IOException("Archive contains too many files to import safely");
                    }
                    long declaredSize = entry.getSize();
                    if (declaredSize > 0) {
                        totalExtractedBytes += declaredSize;
                        if (totalExtractedBytes > MAX_EXTRACTED_BYTES) {
                            throw new IOException("Archive is too large to import safely");
                        }
                    }
                    target.getParentFile().mkdirs();
                    copyStreamToFile(zipInputStream, target);
                    if (declaredSize < 0) {
                        totalExtractedBytes += target.length();
                        if (totalExtractedBytes > MAX_EXTRACTED_BYTES) {
                            throw new IOException("Archive is too large to import safely");
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private String sanitizeZipEntryName(String entryName) {
        if (entryName == null) {
            return null;
        }
        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("../") || normalized.equals("..") || normalized.contains(":/")) {
            return null;
        }
        return normalized;
    }

    private void copyStreamToFileWithProgress(InputStream inputStream, File file, long expectedBytes, String title, String label) throws IOException {
        file.getParentFile().mkdirs();
        long totalRead = 0L;
        long lastPublishedBytes = 0L;
        long lastPublishTime = 0L;
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                totalRead += read;
                long now = System.currentTimeMillis();
                if (expectedBytes > 0 && (totalRead - lastPublishedBytes >= (256L * 1024L) || now - lastPublishTime >= 200L)) {
                    int percent = (int) Math.min(100L, (totalRead * 100L) / expectedBytes);
                    notifyProgress(title,
                            label + "\n" + humanReadableBytes(totalRead) + " / " + humanReadableBytes(expectedBytes),
                            0, 0, true, percent + "% downloaded");
                    lastPublishedBytes = totalRead;
                    lastPublishTime = now;
                }
            }
            outputStream.flush();
        }
    }

    private String humanReadableBytes(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = bytes;
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return String.format(Locale.US, unitIndex == 0 ? "%.0f %s" : "%.1f %s", value, units[unitIndex]);
    }

    private static void copyStreamToFile(InputStream inputStream, File file) throws IOException {
        file.getParentFile().mkdirs();
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private static String readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    public static class ImportResult {
        public String scId;
        public String projectName;
        public String sourceType;
        public String sourceLabel;
        public String summary;
        public final ArrayList<String> visualScreens = new ArrayList<>();
        public final ArrayList<String> visualCustomViews = new ArrayList<>();
        public final ArrayList<String> codeOnlyFiles = new ArrayList<>();
        public final ArrayList<String> importedDependencies = new ArrayList<>();
        public final ArrayList<String> satisfiedByBundledDependencies = new ArrayList<>();
        public final ArrayList<String> reusedLocalDependencies = new ArrayList<>();
        public final ArrayList<String> downloadedDependencies = new ArrayList<>();
        public final ArrayList<String> manualDependencyActions = new ArrayList<>();
        public final ArrayList<String> warnings = new ArrayList<>();
        public final ArrayList<String> unsupportedFeatures = new ArrayList<>();

        public String toDisplayText() {
            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(summary)) {
                sb.append(summary).append("\n\n");
            }
            if (!TextUtils.isEmpty(scId)) {
                sb.append("Sketchware ID: ").append(scId).append('\n');
            }
            if (!TextUtils.isEmpty(projectName)) {
                sb.append("Project: ").append(projectName).append('\n');
            }
            if (!TextUtils.isEmpty(sourceLabel)) {
                sb.append("Source: ").append(sourceLabel).append('\n');
            }
            if (!satisfiedByBundledDependencies.isEmpty()) {
                sb.append("\nSatisfied by bundled libraries:\n");
                for (String line : satisfiedByBundledDependencies) {
                    sb.append("• ").append(line).append('\n');
                }
            }
            if (!reusedLocalDependencies.isEmpty()) {
                sb.append("\nReused cached local libraries:\n");
                for (String line : reusedLocalDependencies) {
                    sb.append("• ").append(line).append('\n');
                }
            }
            if (!downloadedDependencies.isEmpty()) {
                sb.append("\nDownloaded external dependencies:\n");
                for (String line : downloadedDependencies) {
                    sb.append("• ").append(line).append('\n');
                }
            }
            if (!manualDependencyActions.isEmpty()) {
                sb.append("\nDependencies needing manual review:\n");
                for (String line : manualDependencyActions) {
                    sb.append("• ").append(line).append('\n');
                }
            }
            if (!visualScreens.isEmpty()) {
                sb.append("\nScreens:\n");
                for (String visualScreen : visualScreens) {
                    sb.append("• ").append(visualScreen).append('\n');
                }
            }
            if (!visualCustomViews.isEmpty()) {
                sb.append("\nCustom views:\n");
                for (String customView : visualCustomViews) {
                    sb.append("• ").append(customView).append('\n');
                }
            }
            if (!warnings.isEmpty()) {
                sb.append("\nWarnings:\n");
                for (String warning : warnings) {
                    sb.append("• ").append(warning).append('\n');
                }
            }
            if (!unsupportedFeatures.isEmpty()) {
                sb.append("\nUnsupported / degraded:\n");
                for (String item : unsupportedFeatures) {
                    sb.append("• ").append(item).append('\n');
                }
            }
            return sb.toString().trim();
        }
    }

    private static class DependencyResolutionReport {
        final ArrayList<String> satisfiedByBundled = new ArrayList<>();
        final ArrayList<String> reusedLocal = new ArrayList<>();
        final ArrayList<String> downloaded = new ArrayList<>();
        final ArrayList<String> unresolved = new ArrayList<>();
        final ArrayList<String> warnings = new ArrayList<>();
    }

    private static class DetectedProject {
        File rootDirectory;
        File appDirectory;
        File primaryGradleFile;
        File primaryManifestFile;
        ArrayList<File> gradleFiles;
        ArrayList<File> primaryManifestOverlayFiles;
        ArrayList<File> sourceRoots;
        ArrayList<File> resDirectories;
        ArrayList<File> layoutDirectories;
        ArrayList<File> assetsDirectories;
        ArrayList<File> jniLibsDirectories;
        ArrayList<File> libsDirectories;
        ArrayList<File> libraryManifestFiles;
        ArrayList<AndroidModule> modules;
        File roundTripMetadataJson;
        File roundTripDataDir;
        String archiveLabel;
    }

    private static class AndroidModule {
        File moduleDirectory;
        File gradleFile;
        boolean applicationModule;
        boolean libraryModule;
        boolean dynamicFeatureModule;
        final LinkedHashMap<String, File> manifestsBySourceSet = new LinkedHashMap<>();
        final LinkedHashSet<String> availableBuildTypes = new LinkedHashSet<>();
        final LinkedHashSet<String> availableProductFlavors = new LinkedHashSet<>();
        final LinkedHashSet<String> preferredSourceSetNames = new LinkedHashSet<>();

        File getMainManifest() {
            File mainManifest = manifestsBySourceSet.get("main");
            if (mainManifest != null) {
                return mainManifest;
            }
            return manifestsBySourceSet.isEmpty() ? null : manifestsBySourceSet.values().iterator().next();
        }
    }

    private static class GradleSummary {
        String applicationId;
        String namespace;
        String versionCode;
        String versionName;
        int minSdk;
        int targetSdk;
        boolean appCompatDetected;
        boolean material3Detected;
        boolean viewBindingDetected;
        boolean gsonDetected;
        String detectedThemeFamily;
        String detectedThemeMode;
        final ArrayList<String> dependencies = new ArrayList<>();
        final ArrayList<String> warnings = new ArrayList<>();
    }

    private static class ManifestSummary {
        String packageName;
        String applicationLabel;
        String applicationIconRef;
        String roundIconRef;
        String rawXml;
        final ArrayList<String> permissions = new ArrayList<>();
        final ArrayList<ManifestActivity> activities = new ArrayList<>();
        final ArrayList<String> services = new ArrayList<>();
        final ArrayList<String> receivers = new ArrayList<>();
        final ArrayList<String> providers = new ArrayList<>();
    }

    private static class ManifestActivity {
        String fullyQualifiedName;
        boolean launcher;
    }

    private static class DiscoveredActivity {
        String fullyQualifiedName;
        String simpleClassName;
        String layoutName;
    }

    public interface ImportProgressListener {
        void onProgress(ImportProgress progress);
    }

    public static class ImportProgress {
        public final String title;
        public final String detail;
        public final int currentStep;
        public final int totalSteps;
        public final boolean indeterminate;
        public final String statusLine;

        public ImportProgress(String title, String detail, int currentStep, int totalSteps, boolean indeterminate, String statusLine) {
            this.title = title;
            this.detail = detail;
            this.currentStep = currentStep;
            this.totalSteps = totalSteps;
            this.indeterminate = indeterminate || totalSteps <= 0;
            this.statusLine = statusLine;
        }

        public int getPercent() {
            if (indeterminate || totalSteps <= 0) {
                return 0;
            }
            int boundedStep = Math.max(0, Math.min(currentStep, totalSteps));
            return (boundedStep * 100) / totalSteps;
        }

        public String getStatusLineOrDefault() {
            if (!TextUtils.isEmpty(statusLine)) {
                return statusLine;
            }
            if (indeterminate || totalSteps <= 0) {
                return "Working...";
            }
            return "Step " + currentStep + " of " + totalSteps;
        }

        public String toDisplayText() {
            if (TextUtils.isEmpty(detail)) {
                return title;
            }
            return title + "\n" + detail;
        }
    }

    private static class AdaptiveIconSpec {
        String foregroundRef;
        String backgroundRef;
        String monochromeRef;

        List<String> getReferences() {
            ArrayList<String> references = new ArrayList<>();
            if (!TextUtils.isEmpty(foregroundRef)) {
                references.add(foregroundRef);
            }
            if (!TextUtils.isEmpty(backgroundRef)) {
                references.add(backgroundRef);
            }
            if (!TextUtils.isEmpty(monochromeRef)) {
                references.add(monochromeRef);
            }
            return references;
        }
    }

    private static class GitHubRepoSpec {
        String owner;
        String repo;
        String branch;

        static GitHubRepoSpec parse(String url, String branch) {
            String cleaned = url.trim();
            if (cleaned.endsWith("/")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            cleaned = cleaned.replace("https://github.com/", "");
            cleaned = cleaned.replace("http://github.com/", "");
            if (cleaned.startsWith("github.com/")) {
                cleaned = cleaned.substring("github.com/".length());
            }
            String[] parts = cleaned.split("/");
            if (parts.length < 2) {
                throw new IllegalArgumentException("GitHub repository URL is invalid");
            }
            GitHubRepoSpec spec = new GitHubRepoSpec();
            spec.owner = parts[0];
            spec.repo = parts[1].replaceAll("\\.git$", "");
            spec.branch = branch;
            if ((spec.branch == null || spec.branch.isEmpty()) && parts.length >= 4 && "tree".equals(parts[2])) {
                spec.branch = parts[3];
            }
            return spec;
        }
    }
}
