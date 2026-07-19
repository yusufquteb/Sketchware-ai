package mod.hey.studios.compiler.kotlin

import a.a.a.ProjectBuilder
import mod.hey.studios.build.BuildSettings
import mod.hey.studios.compiler.kotlin.KotlinCompilerUtil.*
import mod.jbk.util.LogUtil
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File

/**
 * Partly adapted from:
 * [https://github.com/tyron12233/CodeAssist/blob/main/build-logic/src/main/java/com/tyron/builder/compiler/incremental/kotlin/IncrementalKotlinCompiler.java]
 *
 * A huge thank you to [tyron][https://github.com/tyron12233] for porting `kotlinc` to Android.
 */
class KotlinCompiler(
    private val builder: ProjectBuilder
) {
    private val workspace = builder.yq

    /**
     * Invokes `kotlinc`.
     *
     * Always performs a full Kotlin compilation of every source file found.
     * This used to skip compilation via IncrementalCompileCache when it
     * believed no sources had changed; that cache had a confirmed staleness
     * bug on the Java side (see ProjectBuilder.compileJavaCode()) and has
     * been removed project-wide rather than patched again. kotlinc itself is
     * naturally slower than ECJ, but correctness takes priority here, and a
     * project with few/no .kt files (the common case, since this only runs
     * at all when Kotlin sources are present) pays a small, predictable cost.
     */
    @Throws(Throwable::class)
    fun compile() {
        val timeMillis = System.currentTimeMillis()

        val filesToCompile = getFilesToCompile(workspace).apply {
            if (!areAnyKtFilesPresent(workspace)) {
                LogUtil.d(TAG, "No kotlin source files found, skipping kotlinc")
                return
            }
        }

        val plugins = getCompilerPlugins(workspace).map(File::getAbsolutePath).toTypedArray()

        val mKotlinHome = File(KotlinCompilerBridge.getKotlinHome(workspace)).apply { mkdirs() }
        // Output in the same place as ecj, makes everything easier.
        // Wiped and recreated on every compile (rather than reused) since
        // this is now always a full recompile of every .kt source: without
        // this, a .class file for a .kt source that the user later deletes
        // would silently survive here and keep getting merged/packaged.
        val mClassOutput = File(workspace.compiledKotlinClassesPath).apply {
            deleteRecursively()
            mkdirs()
        }

        val arguments = mutableListOf<String>().apply {
            // Classpath
            add("-cp")
            add(builder.getClasspath())

            // Sources (.java & .kt)
            addAll(filesToCompile.map { it.absolutePath })
        }

        val compiler = K2JVMCompiler()
        val collector = DiagnosticCollector()

        val args = K2JVMCompilerArguments().apply {
            compileJava = false
            includeRuntime = false
            noJdk = true
            noReflect = true
            noStdlib = true

            kotlinHome = mKotlinHome.absolutePath
            destination = mClassOutput.absolutePath
            pluginClasspaths = plugins
        }

        LogUtil.d(TAG, "Running kotlinc with these arguments: $arguments")

        compiler.parseArguments(arguments.toTypedArray(), args)
        compiler.exec(collector, Services.EMPTY, args)

        // Log all diagnostics
        LogUtil.d(TAG, "kotlinc MessageCollector: $collector")

        // kotlinc generates some .kotlin_module files that make D8 fail,
        // delete them for now (?) TODO
        File(mClassOutput, "META-INF").deleteRecursively()

        if (collector.hasErrors()) {
            LogUtil.e(TAG, "Failed to compile Kotlin files")
            throw Exception(collector.getDiagnostics(areWarningsEnabled()))
        } else {
            builder.rebuildMergedCompiledClassesDirectory()
            LogUtil.d(
                TAG,
                "Compiling Kotlin files took ${System.currentTimeMillis() - timeMillis} ms"
            )
        }
    }

    private fun areWarningsEnabled(): Boolean {
        return builder.build_settings.getValue(
            BuildSettings.SETTING_NO_WARNINGS,
            BuildSettings.SETTING_GENERIC_VALUE_TRUE
        ) != BuildSettings.SETTING_GENERIC_VALUE_TRUE
    }

    companion object {
        const val TAG = "KotlinCompiler"
    }
}
