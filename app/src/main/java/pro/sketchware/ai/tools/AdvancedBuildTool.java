package pro.sketchware.ai.tools;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.HashMap;

import a.a.a.ProjectBuilder;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import a.a.a.yq;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.project.stringfog.StringfogHandler;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.util.library.BuiltInLibraryCompatibilityMatrix;

/**
 * AdvancedBuildTool — R8, parallel ECJ, and D8/Dx build configuration tools.
 *
 * Tools registered:
 *   set_build_compiler  — switch dexer (R8 / D8 / Dx), parallel ECJ, Java version
 *   build_with_r8       — full build pipeline with R8 shrinking enabled
 */
public final class AdvancedBuildTool {

    private AdvancedBuildTool() {}

    // ── Tool 1: set_build_compiler ────────────────────────────────────────────

    public static class SetBuildCompilerTool implements AgentTool {

        @Override public String getName() { return "set_build_compiler"; }

        @Override
        public String getDescription() {
            return "Configures the dexer (bytecode compiler) and Java version for a project.\n"
                    + "  dexer 'R8'  → enables ProGuard + R8 (smallest APK, best for large projects)\n"
                    + "  dexer 'D8'  → modern dexer, no shrinking (default for Java 1.8+)\n"
                    + "  dexer 'Dx'  → legacy dexer (only for very old projects)\n"
                    + "  parallel_ecj true → compile Java in parallel threads (faster builds)\n"
                    + "  java_version: '1.7' | '1.8' | '11' | '15' | '16' | '17' | '20'\n"
                    + "After calling this, run build_project or build_with_r8.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addStr(props, "sc_id", "Project ID (sc_id)");

            JsonObject dexer = new JsonObject();
            dexer.addProperty("type", "string");
            dexer.addProperty("description", "Dexer: 'R8', 'D8', or 'Dx'");
            JsonArray dexerEnum = new JsonArray();
            dexerEnum.add("R8"); dexerEnum.add("D8"); dexerEnum.add("Dx");
            dexer.add("enum", dexerEnum);
            props.add("dexer", dexer);

            JsonObject parallelEcj = new JsonObject();
            parallelEcj.addProperty("type", "boolean");
            parallelEcj.addProperty("description",
                    "Enable parallel ECJ for faster Java compilation on multi-core devices");
            props.add("parallel_ecj", parallelEcj);

            JsonObject javaVer = new JsonObject();
            javaVer.addProperty("type", "string");
            javaVer.addProperty("description",
                    "Java source/target version: '1.7', '1.8', '11', '15', '16', '17', '20'");
            props.add("java_version", javaVer);

            schema.add("properties", props);
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            if (scId == null || scId.isEmpty())
                return ToolResult.failure(null, "sc_id is required");
            if (!ctx.isProjectAllowed(scId))
                return ToolResult.failure(null, "Project " + scId + " is not in this workspace");

            // BuildSettings.setValue() saves to disk automatically (no separate save() call needed)
            BuildSettings settings = new BuildSettings(scId);
            StringBuilder changes = new StringBuilder();

            if (args.has("dexer") && !args.get("dexer").isJsonNull()) {
                String dexer = args.get("dexer").getAsString().trim();
                switch (dexer) {
                    case "R8":
                        // R8 uses D8 as the dexer but runs via ProguardHandler with R8 enabled
                        settings.setValue(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_D8);
                        // setProguardEnabled / setR8Enabled each write directly to disk
                        new ProguardHandler(scId).setProguardEnabled(true);
                        new ProguardHandler(scId).setR8Enabled(true);
                        changes.append("Dexer: R8 (ProGuard shrinking + R8 minification enabled)\n");
                        break;
                    case "D8":
                        settings.setValue(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_D8);
                        new ProguardHandler(scId).setR8Enabled(false);
                        changes.append("Dexer: D8 (modern, no shrinking)\n");
                        break;
                    case "Dx":
                        settings.setValue(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_DX);
                        changes.append("Dexer: Dx (legacy)\n");
                        break;
                    default:
                        return ToolResult.failure(null,
                                "Invalid dexer '" + dexer + "'. Use 'R8', 'D8', or 'Dx'");
                }
            }

            if (args.has("parallel_ecj") && !args.get("parallel_ecj").isJsonNull()) {
                boolean parallel = args.get("parallel_ecj").getAsBoolean();
                settings.setValue(BuildSettings.SETTING_PARALLEL_ECJ,
                        parallel ? ProjectSettings.SETTING_GENERIC_VALUE_TRUE
                                 : ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
                changes.append("Parallel ECJ: ").append(parallel ? "enabled" : "disabled").append("\n");
            }

            if (args.has("java_version") && !args.get("java_version").isJsonNull()) {
                String jv = args.get("java_version").getAsString().trim();
                String settingValue;
                switch (jv) {
                    case "1.7": settingValue = BuildSettings.SETTING_JAVA_VERSION_1_7; break;
                    case "1.8": settingValue = BuildSettings.SETTING_JAVA_VERSION_1_8; break;
                    case "11":  settingValue = BuildSettings.SETTING_JAVA_VERSION_11;  break;
                    case "15":  settingValue = BuildSettings.SETTING_JAVA_VERSION_15;  break;
                    case "16":  settingValue = BuildSettings.SETTING_JAVA_VERSION_16;  break;
                    case "17":  settingValue = BuildSettings.SETTING_JAVA_VERSION_17;  break;
                    case "20":  settingValue = BuildSettings.SETTING_JAVA_VERSION_20;  break;
                    default:
                        return ToolResult.failure(null,
                                "Invalid java_version '" + jv + "'. Use: 1.7 / 1.8 / 11 / 15 / 16 / 17 / 20");
                }
                settings.setValue(BuildSettings.SETTING_JAVA_VERSION, settingValue);
                changes.append("Java version: ").append(jv).append("\n");
            }

            if (changes.length() == 0)
                return ToolResult.success(null,
                        "No changes applied. Specify at least one of: dexer, parallel_ecj, java_version.");

            return ToolResult.success(null,
                    "Build settings updated for project " + scId + ":\n" + changes
                    + "\nRun build_with_r8 or build_project to compile with the new settings.");
        }
    }

    // ── Tool 2: build_with_r8 ─────────────────────────────────────────────────

    public static class BuildWithR8Tool implements AgentTool {

        @Override public String getName() { return "build_with_r8"; }

        @Override
        public String getDescription() {
            return "Runs a full build pipeline with R8 shrinking and minification enabled. "
                    + "Produces smaller, faster APKs and handles large projects that time out with Dx. "
                    + "Pipeline: extract assets → compile resources → view binding → "
                    + "Kotlin → Java → StringFog → R8/ProGuard → DEX → package → sign. "
                    + "Returns the APK path on success, or the compile error on failure.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addStr(props, "sc_id", "Project ID (sc_id)");

            JsonObject parallelEcj = new JsonObject();
            parallelEcj.addProperty("type", "boolean");
            parallelEcj.addProperty("description",
                    "Enable parallel ECJ for faster Java compilation (default: true)");
            props.add("parallel_ecj", parallelEcj);

            schema.add("properties", props);
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            if (scId == null || scId.isEmpty())
                return ToolResult.failure(null, "sc_id is required");
            if (!ctx.isProjectAllowed(scId))
                return ToolResult.failure(null, "Project " + scId + " is not in this workspace");

            boolean parallelEcj = !args.has("parallel_ecj")
                    || args.get("parallel_ecj").isJsonNull()
                    || args.get("parallel_ecj").getAsBoolean();

            ctx.reportProgress("Preparing R8 build…", 2, true);
            try {
                Context appCtx = ctx.getAppContext();

                BuiltInLibraryCompatibilityMatrix.ValidationResult validation =
                        BuiltInLibraryCompatibilityMatrix.validate(scId);
                if (!validation.isValid())
                    return ToolResult.failure(null,
                            "Library compatibility error:\n" + validation.formatErrors());

                HashMap<String, Object> metadata = lC.b(scId);
                if (metadata == null)
                    return ToolResult.failure(null, "Project metadata not found for " + scId);

                yq project = new yq(appCtx, wq.d(scId), metadata);
                FileUtil.deleteFile(project.projectMyscPath);

                ctx.reportProgress("Generating source files…", 8, true);
                project.c(appCtx);
                project.a();
                project.a(appCtx, wq.e("600"));

                if (yB.a(lC.b(scId), "custom_icon")) {
                    project.aa(wq.e() + File.separator + scId + File.separator + "mipmaps");
                    if (yB.a(lC.b(scId), "isIconAdaptive", false)) {
                        project.createLauncherIconXml("""
                                <?xml version="1.0" encoding="utf-8"?>
                                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                                <background android:drawable="@mipmap/ic_launcher_background"/>
                                <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                                <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                                </adaptive-icon>""");
                    } else {
                        project.a(wq.e() + File.separator + scId + File.separator + "icon.png");
                    }
                }

                ctx.reportProgress("Processing resources…", 12, true);
                kC resources = jC.d(scId);
                resources.b(project.resDirectoryPath + File.separator + "drawable-xhdpi");
                resources = jC.d(scId);
                resources.c(project.resDirectoryPath + File.separator + "raw");
                resources = jC.d(scId);
                resources.a(project.assetsPath + File.separator + "fonts");

                // Apply build settings
                BuildSettings buildSettings = new BuildSettings(scId);
                buildSettings.setValue(BuildSettings.SETTING_PARALLEL_ECJ,
                        parallelEcj ? ProjectSettings.SETTING_GENERIC_VALUE_TRUE
                                    : ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
                buildSettings.setValue(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_D8);

                // Enable R8 via ProguardHandler (writes directly to disk, no save() needed)
                new ProguardHandler(scId).setProguardEnabled(true);
                new ProguardHandler(scId).setR8Enabled(true);

                // Same builder setup as BuildTools.prepareBuild()
                ProjectBuilder builder = new ProjectBuilder((progress, step) -> {
                    if (ctx.isCancelled()) return;
                    int mapped = Math.min(95, Math.max(8, step * 4));
                    ctx.reportProgress(progress, mapped, false);
                }, appCtx, project);
                builder.setBuildAppBundle(false);

                var fileManager    = jC.b(scId);
                var dataManager    = jC.a(scId);
                var libraryManager = jC.c(scId);
                project.a(libraryManager, fileManager, dataManager, yq.ExportType.DEBUG_APP);
                builder.buildBuiltInLibraryInformation();
                project.b(fileManager, dataManager, libraryManager,
                        builder.getBuiltInLibraryManager());
                project.f();
                project.e();

                // Execute the same steps as BuildTools.executeDebugBuild()
                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Extracting compile assets…", 20, true);
                builder.maybeExtractAapt2();
                BuiltInLibraries.extractCompileAssets(
                        (progress, step) -> ctx.reportProgress(progress, 26, false));

                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Compiling resources (AAPT2)…", 35, true);
                builder.compileResources();

                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Generating view binding…", 45, true);
                builder.generateViewBinding();

                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Compiling Kotlin…", 52, true);
                try {
                    KotlinCompilerBridge.compileKotlinCodeIfPossible(
                            (progress, step) -> ctx.reportProgress(progress, 52, false), builder);
                } catch (Throwable t) {
                    if (t instanceof Exception e) throw e;
                    throw new RuntimeException(t);
                }

                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Compiling Java…", 62, true);
                builder.compileJavaCode();

                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Running StringFog…", 68, true);
                new StringfogHandler(scId).start(
                        (progress, step) -> ctx.reportProgress(progress, 68, false), builder);

                // ProguardHandler.start() detects R8 is enabled and runs R8 automatically
                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Running R8 (shrink + minify + dex)…", 72, true);
                new ProguardHandler(scId).start(
                        (progress, step) -> ctx.reportProgress(progress, 72, false), builder);

                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Creating DEX files…", 80, true);
                builder.createDexFilesFromClasses();

                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Merging DEX files…", 88, true);
                builder.getDexFilesReady();

                if (ctx.isCancelled()) return ToolResult.failure(null, "Build cancelled");
                ctx.reportProgress("Packaging APK…", 94, true);
                builder.buildApk();
                builder.signDebugApk();

                ctx.reportProgress("Build complete!", 100, false);

                // project.finalToInstallApkPath is the signed APK path
                String apkPath = project.finalToInstallApkPath;
                long   apkSize = new File(apkPath).length();

                JsonObject result = new JsonObject();
                result.addProperty("status",         "success");
                result.addProperty("dexer",          "R8");
                result.addProperty("parallel_ecj",   parallelEcj);
                result.addProperty("artifact_path",  apkPath);
                result.addProperty("apk_size_bytes", apkSize);
                result.addProperty("installable",    true);
                result.addProperty("message",
                        "R8 build completed successfully.\n"
                        + "APK size: " + (apkSize / 1024) + " KB\n"
                        + "Path: " + apkPath);
                return ToolResult.success(null, result.toString());

            } catch (Exception e) {
                String errMsg = e.getMessage() != null
                        ? e.getMessage() : e.getClass().getSimpleName();
                try {
                    new mod.jbk.diagnostic.CompileErrorSaver(scId).writeLogsToFile(errMsg);
                } catch (Exception ignored) {}
                return ToolResult.failure(null,
                        "R8 build failed:\n" + errMsg
                        + "\n\nUse get_compile_logs to read the full error output.");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String str(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        return args.get(key).getAsString().trim();
    }

    private static void addStr(JsonObject props, String key, String description) {
        JsonObject p = new JsonObject();
        p.addProperty("type",        "string");
        p.addProperty("description", description);
        props.add(key, p);
    }
}
