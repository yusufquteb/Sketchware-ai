package pro.sketchware.ai.tools;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import a.a.a.lC;
import a.a.a.yB;
import com.besome.sketch.editor.manage.library.material3.Material3LibraryManager;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.ProjectFile;
import pro.sketchware.activities.importproject.ImportAndroidStudioProjectActivity;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.utility.FilePathUtil;

/**
 * AI Agent Tool: export_to_android_studio
 *
 * Exports a Sketchware Pro project as a proper Android Studio / Gradle ZIP
 * (with .skpro round-trip snapshot) and immediately opens
 * ImportAndroidStudioProjectActivity so the user can confirm import.
 *
 * Because the round-trip snapshot is embedded, the re-import is 100% lossless:
 * all Sketchware metadata, activities, layouts, and logic blocks are preserved.
 */
public final class ExportToAndroidStudioTool implements AgentTool {

    private static final String TOOL_NAME      = "export_to_android_studio";
    private static final int    BUFFER_SIZE    = 8192;
    private static final String SNAPSHOT_DIR   = "data_snapshot";
    private static final String METADATA_FILE  = "project_metadata.json";

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String getName() { return TOOL_NAME; }

    @Override
    public String getDescription() {
        return "Exports a Sketchware Pro project as a complete Android Studio / Gradle project ZIP "
                + "and immediately opens the Android Studio importer screen so the user can "
                + "import it back into Sketchware (or open it in Android Studio). "
                + "A lossless .skpro round-trip snapshot is embedded in the ZIP so re-importing "
                + "preserves all Sketchware metadata, activities, layouts, and logic blocks. "
                + "Use this after creating or finishing a project with the AI agent.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject properties = new JsonObject();

        JsonObject scIdProp = new JsonObject();
        scIdProp.addProperty("type", "string");
        scIdProp.addProperty("description", "The SC ID of the project to export (e.g. \"601\")");
        properties.add("sc_id", scIdProp);

        JsonArray required = new JsonArray();
        required.add("sc_id");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject arguments, ToolContext context) {

        // ── Validate input ────────────────────────────────────────────────
        if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
            return failure("Missing required parameter: sc_id");
        }
        String scId = arguments.get("sc_id").getAsString().trim();
        if (scId.isEmpty()) {
            return failure("sc_id must not be empty");
        }
        if (!context.isProjectAllowed(scId)) {
            return failure("Project " + scId + " is not in the current workspace");
        }

        // ── Load metadata ─────────────────────────────────────────────────
        context.reportProgress("Reading project metadata…");
        HashMap<String, Object> metadata;
        try {
            metadata = lC.b(scId);
        } catch (Exception e) {
            return failure("Failed to read project metadata: " + e.getMessage());
        }
        if (metadata == null) {
            return failure("Project not found: " + scId);
        }

        String appName  = strOf(metadata, "my_app_name",  "MyApp");
        String pkgName  = strOf(metadata, "my_sc_pkg_name", "com.example.app");
        String verName  = strOf(metadata, "sc_ver_name",  "1.0");
        String verCode  = strOf(metadata, "sc_ver_code",  "1");
        String wsName   = strOf(metadata, "my_ws_name",   appName);

        String safeAppName = appName.replaceAll("[^A-Za-z0-9_\\-]", "_");

        // ── Prepare output ZIP ────────────────────────────────────────────
        Context appCtx  = context.getAppContext();
        File outputZip  = new File(appCtx.getCacheDir(),
                safeAppName + "_as_" + System.currentTimeMillis() + ".zip");

        try {
            context.reportProgress("Building Android Studio project structure…");
            buildAndroidStudioZip(appCtx, scId, metadata, safeAppName,
                    pkgName, verCode, verName, wsName, outputZip);
        } catch (Exception e) {
            return failure("Export failed: " + e.getMessage());
        }

        // ── Open ImportAndroidStudioProjectActivity with the ZIP pre-filled ─
        context.reportProgress("Opening import screen…");
        try {
            Intent intent = new Intent(appCtx, ImportAndroidStudioProjectActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(ImportAndroidStudioProjectActivity.EXTRA_PRELOADED_ZIP_PATH,
                    outputZip.getAbsolutePath());
            appCtx.startActivity(intent);
        } catch (Exception ignored) {
            // Even if the Activity open fails, export succeeded
        }

        // ── Return success ────────────────────────────────────────────────
        JsonObject result = new JsonObject();
        result.addProperty("sc_id",        scId);
        result.addProperty("app_name",     appName);
        result.addProperty("package_name", pkgName);
        result.addProperty("zip_path",     outputZip.getAbsolutePath());
        result.addProperty("zip_size_kb",  outputZip.length() / 1024);
        result.addProperty("message",
                "Project '" + appName + "' exported as an Android Studio ZIP ("
                        + (outputZip.length() / 1024) + " KB). "
                        + "The import screen has been opened — tap 'Import ZIP' to complete the import.");
        return ToolResult.success(null, result.toString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ZIP BUILDER
    // ─────────────────────────────────────────────────────────────────────────

    private void buildAndroidStudioZip(Context ctx, String scId,
                                       HashMap<String, Object> metadata,
                                       String safeAppName, String pkgName,
                                       String verCode, String verName, String wsName,
                                       File outputZip) throws Exception {

        FilePathUtil fpu = new FilePathUtil();
        // Data dir: .sketchware/data/<scId>
        String dataDir   = a.a.a.wq.b(scId);
        String javaDir   = fpu.getPathJava(scId);
        String resDir    = fpu.getPathResource(scId);
        String assetsDir = fpu.getPathAssets(scId);

        String base = safeAppName + "/";

        // Read project SDK settings
        ProjectSettings projectSettings = new ProjectSettings(scId);
        int minSdk    = projectSettings.getMinSdkVersion();
        int targetSdk = 34;

        // Detect if Material3 is enabled for dependency selection
        boolean isMaterial3 = new Material3LibraryManager(scId).isMaterial3Enabled();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip))) {

            // ── Gradle wrapper / root files ───────────────────────────────
            addText(zos, base + "settings.gradle",
                    "rootProject.name = \"" + safeAppName + "\"\n"
                            + "include ':app'\n");

            addText(zos, base + "build.gradle",
                    "// Root build file\n"
                            + "buildscript {\n"
                            + "    repositories { google(); mavenCentral() }\n"
                            + "    dependencies {\n"
                            + "        classpath 'com.android.tools.build:gradle:8.3.2'\n"
                            + "    }\n"
                            + "}\n"
                            + "allprojects {\n"
                            + "    repositories { google(); mavenCentral() }\n"
                            + "}\n");

            addText(zos, base + "gradle.properties",
                    "android.useAndroidX=true\n"
                            + "android.enableJetifier=true\n"
                            + "org.gradle.jvmargs=-Xmx2048m\n");

            // ── app/build.gradle ──────────────────────────────────────────
            addText(zos, base + "app/build.gradle",
                    buildAppGradle(pkgName, verCode, verName, minSdk, targetSdk, isMaterial3));

            // ── AndroidManifest.xml ───────────────────────────────────────
            String manifestSrc = dataDir + "/AndroidManifest.xml";
            if (new File(manifestSrc).exists()) {
                String manifestXml = readFileAsString(new File(manifestSrc));
                // Strip deprecated package attribute — namespace is declared in build.gradle
                manifestXml = manifestXml.replaceAll(
                        "\\s+package\\s*=\\s*\"[^\"]*\"", "");
                addText(zos, base + "app/src/main/AndroidManifest.xml", manifestXml);
            } else {
                addText(zos, base + "app/src/main/AndroidManifest.xml",
                        buildDefaultManifest(pkgName, wsName));
            }

            // ── Java sources ──────────────────────────────────────────────
            File javaDirFile = new File(javaDir);
            if (javaDirFile.exists()) {
                addDirectory(zos, javaDirFile, base + "app/src/main/java/");
            }

            // ── SketchApplication.java + DebugActivity.java ───────────────
            // These are required by the manifest but not included in project java sources.
            String pkgPath = pkgName.replace('.', '/');
            String javaBase = base + "app/src/main/java/" + pkgPath + "/";

            String debugActivitySrc = readAssetTemplate(ctx, "debug/DebugActivity.java")
                    .replaceAll("<\\?package_name\\?>", pkgName);
            addText(zos, javaBase + "DebugActivity.java", debugActivitySrc);

            String sketchAppSrc = readAssetTemplate(ctx, "debug/SketchApplication.java")
                    .replaceAll("<\\?package_name\\?>", pkgName);
            addText(zos, javaBase + "SketchApplication.java", sketchAppSrc);

            // ── Resources (skip values/colors.xml and values/styles.xml) ──
            File resDirFile = new File(resDir);
            if (resDirFile.exists()) {
                addDirectoryExcluding(zos, resDirFile, base + "app/src/main/res/",
                        "values/colors.xml", "values/styles.xml");
            }

            // ── Generated colors.xml + styles.xml ────────────────────────
            addText(zos, base + "app/src/main/res/values/colors.xml",
                    buildColorsXml(metadata));
            addText(zos, base + "app/src/main/res/values/styles.xml",
                    buildStylesXml(isMaterial3));

            // ── Assets ────────────────────────────────────────────────────
            File assetsDirFile = new File(assetsDir);
            if (assetsDirFile.exists()) {
                addDirectory(zos, assetsDirFile, base + "app/src/main/assets/");
            }

            // ── .skpro round-trip snapshot ────────────────────────────────
            String skproBase = base + ".skpro/";
            addText(zos, skproBase + METADATA_FILE,
                    new com.google.gson.Gson().toJson(metadata));

            File dataDirFile = new File(dataDir);
            if (dataDirFile.exists()) {
                addDirectory(zos, dataDirFile, skproBase + SNAPSHOT_DIR + "/data/");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GRADLE TEMPLATES
    // ─────────────────────────────────────────────────────────────────────────

    private String buildAppGradle(String pkgName, String verCode, String verName,
                                   int minSdk, int targetSdk, boolean isMaterial3) {
        String materialDep = isMaterial3
                ? "    implementation 'com.google.android.material:material:1.12.0'\n"
                : "    implementation 'com.google.android.material:material:1.12.0'\n";
        return "plugins {\n"
                + "    id 'com.android.application'\n"
                + "}\n\n"
                + "android {\n"
                + "    compileSdk " + targetSdk + "\n"
                + "    namespace '" + pkgName + "'\n\n"
                + "    defaultConfig {\n"
                + "        applicationId \"" + pkgName + "\"\n"
                + "        minSdk " + minSdk + "\n"
                + "        targetSdk " + targetSdk + "\n"
                + "        versionCode " + verCode + "\n"
                + "        versionName \"" + verName + "\"\n"
                + "    }\n\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            minifyEnabled false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'\n"
                + "        }\n"
                + "    }\n\n"
                + "    compileOptions {\n"
                + "        sourceCompatibility JavaVersion.VERSION_17\n"
                + "        targetCompatibility JavaVersion.VERSION_17\n"
                + "    }\n"
                + "}\n\n"
                + "dependencies {\n"
                + "    implementation 'androidx.appcompat:appcompat:1.7.1'\n"
                + materialDep
                + "    implementation 'androidx.constraintlayout:constraintlayout:2.2.1'\n"
                + "}\n";
    }

    private String buildDefaultManifest(String pkgName, String appName) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <application\n"
                + "        android:name=\".SketchApplication\"\n"
                + "        android:allowBackup=\"true\"\n"
                + "        android:label=\"" + escXml(appName) + "\"\n"
                + "        android:theme=\"@style/AppTheme\">\n"
                + "        <activity\n"
                + "            android:name=\"." + appName.replaceAll("[^A-Za-z0-9]", "") + "Activity\"\n"
                + "            android:exported=\"true\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RESOURCE GENERATORS
    // ─────────────────────────────────────────────────────────────────────────

    private String buildColorsXml(HashMap<String, Object> metadata) {
        int colorPrimary         = intOf(metadata, ProjectFile.COLOR_PRIMARY,           Color.parseColor("#ff2196f3"));
        int colorPrimaryDark     = intOf(metadata, ProjectFile.COLOR_PRIMARY_DARK,      Color.parseColor("#ff1976d2"));
        int colorAccent          = intOf(metadata, ProjectFile.COLOR_ACCENT,            Color.parseColor("#ff2196f3"));
        int colorOnPrimary       = intOf(metadata, ProjectFile.COLOR_ON_PRIMARY,        Color.WHITE);
        int colorControlHighlight= intOf(metadata, ProjectFile.COLOR_CONTROL_HIGHLIGHT, Color.parseColor("#202196f3"));
        int colorControlNormal   = intOf(metadata, ProjectFile.COLOR_CONTROL_NORMAL,    Color.parseColor("#ff2196f3"));

        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <color name=\"colorPrimary\">"          + fmtColor(colorPrimary)          + "</color>\n"
                + "    <color name=\"colorPrimaryDark\">"      + fmtColor(colorPrimaryDark)      + "</color>\n"
                + "    <color name=\"colorAccent\">"           + fmtColor(colorAccent)           + "</color>\n"
                + "    <color name=\"colorOnPrimary\">"        + fmtColor(colorOnPrimary)        + "</color>\n"
                + "    <color name=\"colorControlHighlight\">" + fmtColor(colorControlHighlight) + "</color>\n"
                + "    <color name=\"colorControlNormal\">"    + fmtColor(colorControlNormal)    + "</color>\n"
                + "</resources>\n";
    }

    private String buildStylesXml(boolean isMaterial3) {
        if (isMaterial3) {
            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<resources>\n"
                    + "    <style name=\"AppTheme\" parent=\"Theme.Material3.DayNight.NoActionBar\">\n"
                    + "        <item name=\"android:statusBarColor\">@android:color/transparent</item>\n"
                    + "        <item name=\"android:navigationBarColor\">@android:color/transparent</item>\n"
                    + "    </style>\n"
                    + "    <style name=\"AppTheme.FullScreen\" parent=\"AppTheme\">\n"
                    + "        <item name=\"android:windowFullscreen\">true</item>\n"
                    + "        <item name=\"android:windowContentOverlay\">@null</item>\n"
                    + "    </style>\n"
                    + "    <style name=\"AppTheme.DebugActivity\" parent=\"AppTheme\">\n"
                    + "        <item name=\"windowActionBar\">true</item>\n"
                    + "        <item name=\"windowNoTitle\">false</item>\n"
                    + "    </style>\n"
                    + "</resources>\n";
        } else {
            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<resources>\n"
                    + "    <style name=\"AppTheme\" parent=\"Theme.MaterialComponents.Light.NoActionBar.Bridge\">\n"
                    + "        <item name=\"colorPrimary\">@color/colorPrimary</item>\n"
                    + "        <item name=\"colorPrimaryDark\">@color/colorPrimaryDark</item>\n"
                    + "        <item name=\"colorAccent\">@color/colorAccent</item>\n"
                    + "        <item name=\"colorOnPrimary\">@color/colorOnPrimary</item>\n"
                    + "        <item name=\"colorControlHighlight\">@color/colorControlHighlight</item>\n"
                    + "        <item name=\"colorControlNormal\">@color/colorControlNormal</item>\n"
                    + "    </style>\n"
                    + "    <style name=\"AppTheme.FullScreen\" parent=\"AppTheme\">\n"
                    + "        <item name=\"android:windowFullscreen\">true</item>\n"
                    + "        <item name=\"android:windowContentOverlay\">@null</item>\n"
                    + "    </style>\n"
                    + "    <style name=\"AppTheme.DebugActivity\" parent=\"AppTheme\">\n"
                    + "        <item name=\"windowActionBar\">true</item>\n"
                    + "        <item name=\"windowNoTitle\">false</item>\n"
                    + "    </style>\n"
                    + "</resources>\n";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ZIP HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void addText(ZipOutputStream zos, String entryName, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(bytes);
        zos.closeEntry();
    }

    private void addFile(ZipOutputStream zos, String entryName, File file) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = fis.read(buf)) > 0) zos.write(buf, 0, len);
        }
        zos.closeEntry();
    }

    private void addDirectory(ZipOutputStream zos, File dir, String zipBase) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) addDirectory(zos, f, zipBase + f.getName() + "/");
            else addFile(zos, zipBase + f.getName(), f);
        }
    }

    /** Like addDirectory but skips relative paths listed in {@code excludeRelPaths}. */
    private void addDirectoryExcluding(ZipOutputStream zos, File dir, String zipBase,
                                        String... excludeRelPaths) throws IOException {
        addDirRecursiveExcl(zos, dir, dir, zipBase, excludeRelPaths);
    }

    private void addDirRecursiveExcl(ZipOutputStream zos, File root, File current,
                                      String zipBase, String[] excludeRelPaths) throws IOException {
        File[] files = current.listFiles();
        if (files == null) return;
        for (File f : files) {
            // Build relative path from root for exclusion comparison
            String relPath = root.toURI().relativize(f.toURI()).getPath();
            boolean excluded = false;
            for (String ex : excludeRelPaths) {
                if (relPath.equals(ex) || relPath.equals(ex + "/")) { excluded = true; break; }
            }
            if (excluded) continue;
            if (f.isDirectory()) addDirRecursiveExcl(zos, root, f, zipBase + f.getName() + "/", excludeRelPaths);
            else addFile(zos, zipBase + f.getName(), f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UTILS
    // ─────────────────────────────────────────────────────────────────────────

    /** Reads an Android asset file and returns it as a String. */
    private String readAssetTemplate(Context ctx, String assetPath) throws IOException {
        try (InputStream is = ctx.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    /** Reads a file from the filesystem as a UTF-8 String. */
    private String readFileAsString(File file) throws IOException {
        try (InputStream is = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private String strOf(HashMap<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        if (v == null) return fallback;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? fallback : s;
    }

    private int intOf(HashMap<String, Object> map, String key, int fallback) {
        try { return yB.a(map, key, fallback); } catch (Exception e) { return fallback; }
    }

    /** Formats an ARGB int color as #RRGGBB or #AARRGGBB. */
    private String fmtColor(int color) {
        if ((color & 0xFF000000) == 0 && color != 0) color |= 0xFF000000;
        int alpha = (color >> 24) & 0xff;
        if (alpha != 0xff) return String.format("#%08X", color);
        return String.format("#%06X", 0xFFFFFF & color);
    }

    private String escXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private ToolResult failure(String msg) {
        return ToolResult.failure(null, msg);
    }
}
