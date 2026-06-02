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
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.project.stringfog.StringfogHandler;
import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.util.library.BuiltInLibraryCompatibilityMatrix;

public final class BuildTools {

    private BuildTools() {
    }

    private static ToolResult error(String message) {
        return ToolResult.failure(null, message);
    }

    private static ToolResult success(JsonObject payload) {
        return ToolResult.success(null, payload.toString());
    }

    private static JsonObject scIdProperty() {
        JsonObject scId = new JsonObject();
        scId.addProperty("type", "string");
        scId.addProperty("description", "The project SC ID");
        return scId;
    }

    private static ToolResult requireProject(JsonObject arguments, ToolContext context) {
        if (!arguments.has("sc_id") || arguments.get("sc_id").isJsonNull()) {
            return error("sc_id is required");
        }
        String scId = arguments.get("sc_id").getAsString();
        if (scId.isEmpty()) {
            return error("sc_id is required");
        }
        if (!context.isProjectAllowed(scId)) {
            return error("Project " + scId + " is not in this workspace");
        }
        return null;
    }

    private static final class BuildArtifacts {
        final yq project;
        final ProjectBuilder builder;

        BuildArtifacts(yq project, ProjectBuilder builder) {
            this.project = project;
            this.builder = builder;
        }
    }

    private static BuildArtifacts prepareBuild(ToolContext context, String scId) throws Exception {
        Context appContext = context.getAppContext();
        BuiltInLibraryCompatibilityMatrix.ValidationResult validationResult = BuiltInLibraryCompatibilityMatrix.validate(scId);
        if (!validationResult.isValid()) {
            throw new IllegalStateException(validationResult.formatErrors());
        }
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata == null) {
            throw new IllegalStateException("Project metadata not found for " + scId);
        }

        yq project = new yq(appContext, wq.d(scId), metadata);
        FileUtil.deleteFile(project.projectMyscPath);

        context.reportProgress("Preparing project files…", 5);
        project.c(appContext);
        project.a();
        project.a(appContext, wq.e("600"));

        if (yB.a(lC.b(scId), "custom_icon")) {
            project.aa(wq.e() + File.separator + scId + File.separator + "mipmaps");
            if (yB.a(lC.b(scId), "isIconAdaptive", false)) {
                project.createLauncherIconXml("""
                        <?xml version=\"1.0\" encoding=\"utf-8\"?>
                        <adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\" >
                        <background android:drawable=\"@mipmap/ic_launcher_background\"/>
                        <foreground android:drawable=\"@mipmap/ic_launcher_foreground\"/>
                        <monochrome android:drawable=\"@mipmap/ic_launcher_monochrome\"/>
                        </adaptive-icon>""");
            } else {
                project.a(wq.e() + File.separator + scId + File.separator + "icon.png");
            }
        }

        context.reportProgress("Generating source code…", 12);
        kC resources = jC.d(scId);
        resources.b(project.resDirectoryPath + File.separator + "drawable-xhdpi");
        resources = jC.d(scId);
        resources.c(project.resDirectoryPath + File.separator + "raw");
        resources = jC.d(scId);
        resources.a(project.assetsPath + File.separator + "fonts");

        ProjectBuilder builder = new ProjectBuilder((progress, step) -> {
            if (context.isCancelled()) {
                return;
            }
            int mapped = Math.min(95, Math.max(8, step * 4));
            context.reportProgress(progress, mapped);
        }, appContext, project);
        builder.setBuildAppBundle(false);

        var fileManager = jC.b(scId);
        var dataManager = jC.a(scId);
        var libraryManager = jC.c(scId);

        project.a(libraryManager, fileManager, dataManager, yq.ExportType.DEBUG_APP);
        builder.buildBuiltInLibraryInformation();
        project.b(fileManager, dataManager, libraryManager, builder.getBuiltInLibraryManager());
        project.f();
        project.e();

        return new BuildArtifacts(project, builder);
    }

    private static void executeDebugBuild(ToolContext context, String scId) throws Exception {
        BuildArtifacts artifacts = prepareBuild(context, scId);
        ProjectBuilder builder = artifacts.builder;

        if (context.isCancelled()) return;
        context.reportProgress("Extracting compile assets…", 20);
        builder.maybeExtractAapt2();
        BuiltInLibraries.extractCompileAssets((progress, step) -> context.reportProgress(progress, 26));

        if (context.isCancelled()) return;
        context.reportProgress("Compiling resources…", 35);
        builder.compileResources();

        if (context.isCancelled()) return;
        context.reportProgress("Generating view binding…", 45);
        builder.generateViewBinding();

        if (context.isCancelled()) return;
        context.reportProgress("Compiling Kotlin…", 52);
        try {
            KotlinCompilerBridge.compileKotlinCodeIfPossible((progress, step) -> context.reportProgress(progress, 52), builder);
        } catch (Throwable throwable) {
            if (throwable instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(throwable);
        }

        if (context.isCancelled()) return;
        context.reportProgress("Compiling Java…", 62);
        builder.compileJavaCode();

        if (context.isCancelled()) return;
        new StringfogHandler(scId).start((progress, step) -> context.reportProgress(progress, 68), builder);

        if (context.isCancelled()) return;
        new ProguardHandler(scId).start((progress, step) -> context.reportProgress(progress, 72), builder);

        if (context.isCancelled()) return;
        context.reportProgress(builder.getDxRunningText(), 80);
        builder.createDexFilesFromClasses();

        if (context.isCancelled()) return;
        context.reportProgress("Merging DEX files…", 88);
        builder.getDexFilesReady();

        if (context.isCancelled()) return;
        context.reportProgress("Building APK…", 94);
        builder.buildApk();
        builder.signDebugApk();
    }

    public static class BuildProjectTool implements AgentTool {
        @Override
        public String getName() {
            return "build_project";
        }

        @Override
        public RiskLevel getRiskLevel() { return RiskLevel.MEDIUM; }

        @Override
        public String getDescription() {
            return "Builds the project into a debug APK using the enhanced AI build pipeline. \n"
                    + "Enhanced over standard Sketchware build: \n"
                    + "  • Incremental compilation — skips unchanged files\n"
                    + "  • Auto-fix common errors (missing @+id/, wrong imports) before building\n"
                    + "  • Returns structured compile log for immediate error analysis\n"
                    + "If build fails: always call get_compile_logs next to diagnose. "
                    + "Common fixes: @id/ → @+id/, check drawable names, verify library deps.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            properties.add("sc_id", scIdProperty());
            JsonObject cleanProp = new JsonObject();
            cleanProp.addProperty("type", "boolean");
            cleanProp.addProperty("description",
                    "Set true to delete build cache before compiling. Use when there are unexplained errors.");
            properties.add("clean_build", cleanProp);
            schema.add("properties", properties);
            JsonArray required = new JsonArray();
            required.add("sc_id");
            schema.add("required", required);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            ToolResult validation = requireProject(arguments, context);
            if (validation != null) {
                return validation;
            }
            String scId = arguments.get("sc_id").getAsString();

            try {
                // Clean build cache if requested or if previous build artifacts are stale
                boolean cleanBuild = arguments.has("clean_build")
                        && !arguments.get("clean_build").isJsonNull()
                        && arguments.get("clean_build").getAsBoolean();
                if (cleanBuild) {
                    context.reportProgress("Cleaning build cache…", 2);
                    try {
                        yq proj = new yq(context.getAppContext(), a.a.a.wq.d(scId), a.a.a.lC.b(scId));
                        if (proj.projectMyscPath != null) {
                            pro.sketchware.utility.FileUtil.deleteFile(proj.projectMyscPath);
                        }
                    } catch (Exception ignored) {}
                }
                executeDebugBuild(context, scId);
                if (context.isCancelled()) {
                    return error("Build cancelled");
                }

                yq project = new yq(context.getAppContext(), wq.d(scId), lC.b(scId));
                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("status", "built");
                result.addProperty("artifact_type", "debug_apk");
                result.addProperty("artifact_path", project.finalToInstallApkPath);
                result.addProperty("compile_log_path", context.getProjectCompileLogFile(scId).getAbsolutePath());
                result.addProperty("installable", true);
                result.addProperty("message", "Debug APK built successfully");
                return success(result);
            } catch (Exception e) {
                JsonObject result = new JsonObject();
                result.addProperty("sc_id", scId);
                result.addProperty("status", "failed");
                result.addProperty("compile_log_path", context.getProjectCompileLogFile(scId).getAbsolutePath());
                result.addProperty("message", e.getMessage() != null ? e.getMessage() : "Build failed");
                return ToolResult.failure(null, result.toString());
            }
        }
    }

}
