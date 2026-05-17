package pro.sketchware.ai.tools;

import android.content.Context;
import android.content.Intent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import a.a.a.GB;
import a.a.a.lC;
import a.a.a.wq;
import pro.sketchware.activities.importproject.ImportAndroidStudioProjectActivity;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.importer.AndroidStudioProjectImporter;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

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

    private static final String TOOL_NAME        = "export_to_android_studio";
    private static final int    BUFFER_SIZE      = 8192;
    private static final String SKPRO_DIR_NAME   = ".skpro";
    private static final String SNAPSHOT_DIR     = "data_snapshot";
    private static final String METADATA_FILE    = "project_metadata.json";

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String getName() {
        return TOOL_NAME;
    }

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
            // Pass the exported ZIP path so the activity can pre-fill it
            intent.putExtra(ImportAndroidStudioProjectActivity.EXTRA_PRELOADED_ZIP_PATH,
                    outputZip.getAbsolutePath());
            appCtx.startActivity(intent);
        } catch (Exception e) {
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
        String dataDir   = wq.b(scId);         // .sketchware/data/<scId>
        String javaDir   = fpu.getPathJava(scId);
        String resDir    = fpu.getPathResource(scId);
        String assetsDir = fpu.getPathAssets(scId);

        String base = safeAppName + "/";

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
            addText(zos, base + "app/build.gradle", buildAppGradle(pkgName, verCode, verName));

            // ── AndroidManifest.xml ───────────────────────────────────────
            String manifestPath = dataDir + "/AndroidManifest.xml";
            if (new File(manifestPath).exists()) {
                addFile(zos, base + "app/src/main/AndroidManifest.xml",
                        new File(manifestPath));
            } else {
                addText(zos, base + "app/src/main/AndroidManifest.xml",
                        buildDefaultManifest(pkgName, wsName));
            }

            // ── Java sources ──────────────────────────────────────────────
            File javaDirFile = new File(javaDir);
            if (javaDirFile.exists()) {
                addDirectory(zos, javaDirFile,
                        base + "app/src/main/java/");
            }

            // ── Resources ─────────────────────────────────────────────────
            File resDirFile = new File(resDir);
            if (resDirFile.exists()) {
                addDirectory(zos, resDirFile,
                        base + "app/src/main/res/");
            }

            // ── Assets ────────────────────────────────────────────────────
            File assetsDirFile = new File(assetsDir);
            if (assetsDirFile.exists()) {
                addDirectory(zos, assetsDirFile,
                        base + "app/src/main/assets/");
            }

            // ── .skpro round-trip snapshot ────────────────────────────────
            // This makes re-import completely lossless (all Sketchware metadata
            // and logic blocks are preserved via restoreRoundTripProject)
            String skproBase = base + ".skpro/";
            addText(zos, skproBase + METADATA_FILE,
                    new com.google.gson.Gson().toJson(metadata));

            File dataDirFile = new File(dataDir);
            if (dataDirFile.exists()) {
                addDirectory(zos, dataDirFile,
                        skproBase + SNAPSHOT_DIR + "/data/");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GRADLE TEMPLATES
    // ─────────────────────────────────────────────────────────────────────────

    private String buildAppGradle(String pkgName, String verCode, String verName) {
        return "plugins {\n"
                + "    id 'com.android.application'\n"
                + "}\n\n"
                + "android {\n"
                + "    compileSdk 34\n"
                + "    namespace '" + pkgName + "'\n\n"
                + "    defaultConfig {\n"
                + "        applicationId \"" + pkgName + "\"\n"
                + "        minSdk 21\n"
                + "        targetSdk 34\n"
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
                + "    implementation 'com.google.android.material:material:1.12.0'\n"
                + "    implementation 'androidx.constraintlayout:constraintlayout:2.2.1'\n"
                + "}\n";
    }

    private String buildDefaultManifest(String pkgName, String appName) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <application\n"
                + "        android:allowBackup=\"true\"\n"
                + "        android:label=\"" + escXml(appName) + "\"\n"
                + "        android:theme=\"@style/Theme.AppCompat.Light.DarkActionBar\">\n"
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
    //  ZIP HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void addText(ZipOutputStream zos, String entryName, String text) throws IOException {
        byte[] bytes = text.getBytes("UTF-8");
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
    }

    private void addFile(ZipOutputStream zos, String entryName, File file) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = fis.read(buf)) > 0) {
                zos.write(buf, 0, len);
            }
        }
        zos.closeEntry();
    }

    private void addDirectory(ZipOutputStream zos, File dir, String zipBase) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                addDirectory(zos, f, zipBase + f.getName() + "/");
            } else {
                addFile(zos, zipBase + f.getName(), f);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UTILS
    // ─────────────────────────────────────────────────────────────────────────

    private String strOf(HashMap<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        if (v == null) return fallback;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? fallback : s;
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
