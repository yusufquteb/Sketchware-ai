package mod.jbk.build.compiler.dex;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;

import a.a.a.ProjectBuilder;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.SketchApplication;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

/**
 * NOTE (audit report / user-project NoSuchMethodError fix — resolved):
 * This D8 pipeline dexes a *user's* Sketchware-ai project and is completely
 * separate from the Gradle build that produces this app's own APK. The fix for
 * "NoSuchMethodError: InputStream.readAllBytes()" applied to this app's own
 * code (app/build.gradle: coreLibraryDesugaring switched from
 * desugar_jdk_libs_nio to the full desugar_jdk_libs) does NOT automatically
 * extend to user projects built here, since this class calls D8 directly with
 * its own separate D8Command — Gradle's desugaring only rewrites this app's own
 * compiled bytecode, not bytecode this app dexes at runtime for someone else's
 * project.
 *
 * Root cause, confirmed by reading the full pipeline: this class dexes the
 * user's own ECJ-compiled .class files (programFiles below). Nothing in this
 * codebase's bundled jars or assets contains a readAllBytes() call — the crash
 * comes from whatever the user's own project (or a library they linked into
 * it) references, which D8 previously dexed completely unmodified since no
 * desugared-library configuration was ever attached to the D8Command. On a
 * device below the API level a referenced method was added at (readAllBytes()
 * needs API 33), that produces exactly the reported NoSuchMethodError at
 * runtime — the .dex file is valid, it just calls a method absent from that
 * device's libcore.
 *
 * FIX: attach the same desugared-library configuration this app's own Gradle
 * build uses (com.android.tools:desugar_jdk_libs_configuration, matching the
 * desugar_jdk_libs:2.1.5 version in app/build.gradle) to the user-project
 * D8Command too, via D8Command.Builder#addDesugaredLibraryConfiguration(String).
 * That overload is confirmed public API on BaseCompilerCommand.Builder (which
 * D8Command.Builder extends) in the r8 source for the exact versions this
 * project depends on (8.11.18 / 8.13.17) — verified by reading
 * BaseCompilerCommand.java directly, not assumed from documentation. Taking the
 * String overload specifically means no reference to com.android.tools.r8.
 * StringResource is needed anywhere in this file; StringResource not being
 * resolvable was the reason an earlier attempt at this fix failed to compile.
 *
 * The JSON configuration content itself is staged as a build asset
 * (app/src/main/assets/desugar_jdk_libs_configuration.json) by a Gradle task
 * (see app/build.gradle: stageDesugaredLibraryConfigAsset) that resolves it
 * from the same Maven artifact at build time, rather than being hand-copied
 * into this repo — so it always matches the desugar_jdk_libs version in use
 * and updates automatically if that version is ever bumped.
 *
 * Desugared-library configuration is only meaningful (and only valid — R8
 * enforces this) when compiling for a minApiLevel below what the referenced
 * APIs need natively, so it is attached conditionally: only when the user
 * project's own configured minSdk is below 26, the API level
 * desugar_jdk_libs's supported API surface targets. Above that, the user's
 * device already has the real platform implementations and no desugaring is
 * needed or valid to request.
 *
 * If the asset is ever missing (e.g. a build ran without the Gradle task, or
 * someone deleted it) this fails soft: the build proceeds exactly as before
 * this fix (no desugared-library configuration attached, same behavior as the
 * original bug), with a warning surfaced via SketchwareUtil.toastError so a
 * developer notices, rather than aborting a user's build over an infra
 * problem unrelated to their project.
 */
public class DexCompiler {

    private static final String DESUGAR_CONFIG_ASSET = "desugar_jdk_libs_configuration.json";

    /**
     * API level at or above which every API backported by desugar_jdk_libs 2.x is available
     * natively — anything below this may be missing APIs the library covers. Set conservatively to
     * 34 (Android 14) since desugar_jdk_libs 2.x backports APIs introduced as late as Java 17,
     * some of which arrived in platform API 33/34. D8/R8 ignores the config for any individual
     * API that is already present at the given minApiLevel, so attaching it for minApiLevel < 34
     * is always safe and causes no overhead for APIs that don't need backporting.
     *
     * NOTE: the previous value was 26, which meant desugaring was skipped for projects with
     * minSdk 26–33. InputStream.readAllBytes() (added at API 33) was then absent at runtime on
     * devices below API 33, causing the NoSuchMethodError reported on Pixel 4 API 29.
     */
    private static final int DESUGARING_RELEVANT_BELOW_API = 34;

    public static void compileDexFiles(ProjectBuilder builder) throws CompilationFailedException {
        int minApiLevel;

        try {
            minApiLevel = Integer.parseInt(builder.settings.getValue(
                    ProjectSettings.SETTING_MINIMUM_SDK_VERSION, "21"));
        } catch (NumberFormatException e) {
            throw new CompilationFailedException("Invalid minSdkVersion specified in Project Settings" + e.getMessage());
        }

        Collection<Path> programFiles = new LinkedList<>();
        if (builder.proguard.isShrinkingEnabled()) {
            programFiles.add(Paths.get(builder.yq.proguardClassesPath));
        } else {
            for (File file : FileUtil.listFilesRecursively(new File(builder.yq.compiledClassesPath), ".class")) {
                programFiles.add(file.toPath());
            }
        }

        File dexOutputDir = new File(builder.yq.binDirectoryPath, "dex");
        File dexMarker = new File(builder.yq.binDirectoryPath, ".dex_marker");

        // Skip D8 entirely if no class file is newer than the dex marker.
        if (dexMarker.exists() && dexOutputDir.exists()) {
            long markerTime = dexMarker.lastModified();
            boolean anyNewer = false;
            for (Path p : programFiles) {
                if (p.toFile().lastModified() > markerTime) {
                    anyNewer = true;
                    break;
                }
            }
            if (!anyNewer) {
                return;
            }
        }

        Collection<Path> libraryFiles = new LinkedList<>();
        for (String jarPath : builder.getClasspath().split(":")) {
            libraryFiles.add(Paths.get(jarPath));
        }

        CompilationMode mode = builder.isReleaseBuildMode() ? CompilationMode.RELEASE : CompilationMode.DEBUG;

        D8Command.Builder command = D8Command.builder()
                .setMode(mode)
                .setMinApiLevel(minApiLevel)
                .addLibraryFiles(libraryFiles)
                .setOutput(dexOutputDir.toPath(), OutputMode.DexIndexed)
                .addProgramFiles(programFiles);

        if (minApiLevel < DESUGARING_RELEVANT_BELOW_API) {
            String desugarConfig = readDesugaredLibraryConfigOrNull();
            if (desugarConfig != null) {
                command.addDesugaredLibraryConfiguration(desugarConfig);
            }
        }

        D8.run(command.build());

        try {
            dexMarker.createNewFile();
            dexMarker.setLastModified(System.currentTimeMillis());
        } catch (IOException ignored) {
        }
    }

    /**
     * Reads the desugared-library JSON configuration staged as a build asset by
     * app/build.gradle's stageDesugaredLibraryConfigAsset task. Returns null
     * (never throws) if the asset is missing or unreadable, so a packaging
     * problem degrades to "no desugaring for user projects" — the pre-existing
     * behavior — instead of breaking every user build.
     */
    private static String readDesugaredLibraryConfigOrNull() {
        try (InputStream in = SketchApplication.getContext().getAssets().open(DESUGAR_CONFIG_ASSET)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            SketchwareUtil.toastError("Desugared-library config missing (" + DESUGAR_CONFIG_ASSET
                    + ") — user projects targeting API < " + DESUGARING_RELEVANT_BELOW_API
                    + " may crash at runtime if they reference newer java.* APIs. "
                    + "Rebuild this app to regenerate the asset.");
            return null;
        }
    }
}

