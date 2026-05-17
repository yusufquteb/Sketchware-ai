package mod.pranav.dependency.resolver

import android.os.Environment
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import mod.hey.studios.build.BuildSettings
import mod.hey.studios.util.Helper
import mod.jbk.build.BuiltInLibraries
import org.cosmic.ide.dependency.resolver.api.Artifact
import org.cosmic.ide.dependency.resolver.api.EventReciever
import org.cosmic.ide.dependency.resolver.api.Repository
import org.cosmic.ide.dependency.resolver.eventReciever
import org.cosmic.ide.dependency.resolver.getArtifact
import org.cosmic.ide.dependency.resolver.repositories
import pro.sketchware.importer.BuiltInDependencyRegistry
import pro.sketchware.utility.FileUtil
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import java.util.regex.Pattern
import java.util.zip.ZipFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DependencyResolver(
    private val groupId: String,
    private val artifactId: String,
    private val version: String,
    private val skipDependencies: Boolean,
    private val buildSettings: BuildSettings
) {
    companion object {
        private val DEFAULT_REPOS = """
          |[
          |    {"url": "https://dl.google.com/dl/android/maven2", "name": "Google Maven"},
          |    {"url": "https://repo.maven.apache.org/maven2", "name": "Maven Central"},
          |    {"url": "https://oss.sonatype.org/content/repositories/releases", "name": "Sonatype Releases"},
          |    {"url": "https://jitpack.io", "name": "JitPack"}
          |]
        """.trimMargin()

        private const val METADATA_FILE = "resolver-metadata.json"
        private const val DEX_METADATA_FILE = "dex-input-fingerprint.txt"
    }

    private val localLibsRoot: Path = Paths.get(
        FileUtil.getExternalStorageDir(), ".sketchware", "libs", "local_libs"
    )

    private val cacheRoot: Path = Paths.get(
        FileUtil.getExternalStorageDir(), ".sketchware", "cache", "maven"
    )

    private val repositoriesJson = Paths.get(
        Environment.getExternalStorageDirectory().absolutePath,
        ".sketchware",
        "libs",
        "repositories.json"
    )

    init {
        if (Files.notExists(repositoriesJson)) {
            Files.createDirectories(repositoriesJson.parent)
            repositoriesJson.writeText(DEFAULT_REPOS)
        }
        Gson().fromJson(repositoriesJson.readText(), Helper.TYPE_MAP_LIST).forEach {
            val url: String? = it["url"] as String?
            if (url != null) {
                repositories.add(object : Repository {
                    override fun getName(): String {
                        return it["name"] as String
                    }

                    override fun getURL(): String {
                        return if (url.endsWith("/")) {
                            url.substringBeforeLast("/")
                        } else {
                            url
                        }
                    }
                })
            }
        }
    }

    open class DependencyResolverCallback : EventReciever() {
        override fun artifactFound(artifact: Artifact) {}
        override fun onArtifactNotFound(artifact: Artifact) {}
        override fun onFetchingLatestVersion(artifact: Artifact) {}
        override fun onFetchedLatestVersion(artifact: Artifact, version: String) {}
        override fun onResolving(artifact: Artifact, dependency: Artifact) {}
        override fun onResolutionComplete(artifact: Artifact) {}
        override fun onSkippingResolution(artifact: Artifact) {}
        override fun onVersionNotFound(artifact: Artifact) {}
        override fun onDependenciesNotFound(artifact: Artifact) {}
        override fun onInvalidScope(artifact: Artifact, scope: String) {}
        override fun onInvalidPOM(artifact: Artifact) {}
        override fun onDownloadStart(artifact: Artifact) {}
        override fun onDownloadEnd(artifact: Artifact) {}
        override fun onDownloadError(artifact: Artifact, error: Throwable) {}
        open fun unzipping(artifact: Artifact) {}
        open fun dexing(artifact: Artifact) {}
        open fun onTaskCompleted(artifacts: List<String>) {}
        open fun dexingFailed(artifact: Artifact, e: Exception) {}
        open fun invalidPackaging(artifact: Artifact) {}
    }

    fun resolveDependency(callback: DependencyResolverCallback) = runBlocking {
        eventReciever = callback
        val dependency = getArtifact(groupId, artifactId, version) ?: return@runBlocking

        if (dependency.extension != "jar" && dependency.extension != "aar") {
            callback.invalidPackaging(dependency)
            return@runBlocking
        }

        val libraryJars = mutableListOf<Path>()
        libraryJars.add(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath().resolve("core-lambda-stubs.jar"))
        libraryJars.add(Paths.get(
            buildSettings.getValue(
                BuildSettings.SETTING_ANDROID_JAR_PATH,
                BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.resolve("android.jar").absolutePath
            )
        ))

        val baseClasspath = linkedSetOf<Path>()
        buildSettings.getValue(BuildSettings.SETTING_CLASSPATH, "")
            .split(":")
            .filter { it.isNotEmpty() }
            .mapTo(baseClasspath) { Paths.get(it) }

        val resolvedArtifactNames = linkedSetOf<String>()
        val downloadedArtifactJars = linkedSetOf<Path>()

        val rootJar = prepareArtifact(dependency, callback)
        if (rootJar == null) {
            callback.onDependenciesNotFound(dependency)
            return@runBlocking
        }
        downloadedArtifactJars.add(rootJar)
        resolvedArtifactNames.add("${dependency.artifactId}-v${dependency.version}")

        if (!skipDependencies) {
            dependency.resolveDependencyTree()
            dependency.getAllDependencies().forEach { dep ->
                println("Resolving dependency: ${dep.artifactId} v${dep.version}")
                if (dep.extension != "jar" && dep.extension != "aar") {
                    callback.invalidPackaging(dep)
                    return@forEach
                }
                if (dep.version.isEmpty()) {
                    callback.onVersionNotFound(dep)
                    return@forEach
                }

                val bundledMatch = BuiltInDependencyRegistry.findSatisfiedBuiltIn(dep.groupId, dep.artifactId, dep.version)
                if (bundledMatch != null) {
                    val bundledJar = BuiltInLibraries.getLibraryClassesJarPath(bundledMatch.bundledLibraryName).toPath()
                    if (Files.exists(bundledJar)) {
                        downloadedArtifactJars.add(bundledJar)
                        callback.onSkippingResolution(dep)
                        return@forEach
                    }
                }

                val jar = prepareArtifact(dep, callback)
                if (jar == null) {
                    callback.onDependenciesNotFound(dep)
                    return@forEach
                }

                downloadedArtifactJars.add(jar)
                resolvedArtifactNames.add("${dep.artifactId}-v${dep.version}")
            }
        }

        val compileTargets = linkedMapOf<Artifact, Path>()
        compileTargets[dependency] = rootJar
        if (!skipDependencies) {
            dependency.getAllDependencies().forEach { dep ->
                val jar = getCanonicalArtifactDirectory(dep).resolve("classes.jar")
                if (Files.exists(jar)) {
                    compileTargets[dep] = jar
                }
            }
        }

        compileTargets.forEach { (artifact, jar) ->
            callback.dexing(artifact)
            try {
                val compileClasspath = linkedSetOf<Path>()
                compileClasspath.addAll(baseClasspath)
                compileClasspath.addAll(downloadedArtifactJars.filter { it != jar })
                compileJar(jar, compileClasspath.toList(), libraryJars)
                callback.onResolutionComplete(artifact)
            } catch (e: Exception) {
                callback.dexingFailed(artifact, e)
            }
        }

        if (skipDependencies) {
            callback.onSkippingResolution(dependency)
        }
        callback.onTaskCompleted(resolvedArtifactNames.toList())
    }

    private fun prepareArtifact(artifact: Artifact, callback: DependencyResolverCallback): Path? {
        val canonicalDirectory = getCanonicalArtifactDirectory(artifact)
        val compatibilityDirectory = getCompatibilityArtifactDirectory(artifact)
        val archivePath = canonicalDirectory.resolve("classes.${artifact.extension}")
        Files.createDirectories(canonicalDirectory)
        Files.createDirectories(compatibilityDirectory)

        val jar = canonicalDirectory.resolve("classes.jar")
        val cachedMetadata = readStringMap(canonicalDirectory.resolve(METADATA_FILE))
        if (canReusePreparedArtifact(artifact, canonicalDirectory, jar, cachedMetadata, null)) {
            publishCompatibilityView(canonicalDirectory, compatibilityDirectory)
            callback.artifactFound(artifact)
            return jar
        }
        val remoteChecksum = fetchRemoteChecksum(artifact)
        if (canReusePreparedArtifact(artifact, canonicalDirectory, jar, cachedMetadata, remoteChecksum)) {
            publishCompatibilityView(canonicalDirectory, compatibilityDirectory)
            callback.artifactFound(artifact)
            return jar
        }
        if (migrateLegacyPreparedArtifact(artifact, compatibilityDirectory, canonicalDirectory, remoteChecksum)) {
            callback.artifactFound(artifact)
            return jar
        }

        clearArtifactDirectory(canonicalDirectory)
        Files.createDirectories(canonicalDirectory)
        artifact.downloadTo(archivePath.toFile())

        if (artifact.extension == "aar") {
            callback.unzipping(artifact)
            unzip(archivePath)
            val packageName = findPackageName(canonicalDirectory.toAbsolutePath().toString(), artifact.groupId)
            canonicalDirectory.resolve("config").writeText(packageName)
        } else if (Files.exists(archivePath) && !Files.exists(jar)) {
            Files.copy(archivePath, jar)
        }

        if (Files.exists(archivePath)) {
            canonicalDirectory.resolve("archive.sha256").writeText(sha256(archivePath))
            if (artifact.extension == "aar") {
                Files.deleteIfExists(archivePath)
            }
        }

        if (Files.exists(jar)) {
            writeArtifactMetadata(artifact, canonicalDirectory, remoteChecksum)
            publishCompatibilityView(canonicalDirectory, compatibilityDirectory)
            return jar
        }
        return null
    }

    private fun canReusePreparedArtifact(
        artifact: Artifact,
        artifactDirectory: Path,
        jar: Path,
        metadata: Map<String, String>?,
        remoteChecksum: String?
    ): Boolean {
        if (!Files.exists(jar)) {
            return false
        }
        val resolvedMetadata = metadata ?: readStringMap(artifactDirectory.resolve(METADATA_FILE)) ?: return false
        if (resolvedMetadata["groupId"] != artifact.groupId
            || resolvedMetadata["artifactId"] != artifact.artifactId
            || resolvedMetadata["version"] != artifact.version
            || resolvedMetadata["extension"] != artifact.extension) {
            return false
        }

        val storedRemoteChecksum = resolvedMetadata["remoteChecksum"]
        if (!remoteChecksum.isNullOrBlank() && !storedRemoteChecksum.isNullOrBlank()) {
            return remoteChecksum.equals(storedRemoteChecksum, ignoreCase = true)
        }
        return true
    }

    private fun writeArtifactMetadata(artifact: Artifact, artifactDirectory: Path, remoteChecksum: String?) {
        val metadata = linkedMapOf(
            "groupId" to artifact.groupId,
            "artifactId" to artifact.artifactId,
            "version" to artifact.version,
            "extension" to artifact.extension,
            "remoteChecksum" to (remoteChecksum ?: ""),
            "publishedAt" to System.currentTimeMillis().toString()
        )
        artifactDirectory.resolve(METADATA_FILE).writeText(Gson().toJson(metadata))
    }

    private fun clearArtifactDirectory(artifactDirectory: Path) {
        val dir = artifactDirectory.toFile()
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
    }

    private fun getCanonicalArtifactDirectory(artifact: Artifact): Path {
        val safeVersion = sanitizePathSegment(artifact.version)
        return cacheRoot
            .resolve(artifact.groupId.replace('.', File.separatorChar))
            .resolve(sanitizePathSegment(artifact.artifactId))
            .resolve(safeVersion)
    }

    private fun getCompatibilityArtifactDirectory(artifact: Artifact): Path {
        return localLibsRoot.resolve("${artifact.artifactId}-v${artifact.version}")
    }

    private fun publishCompatibilityView(canonicalDirectory: Path, compatibilityDirectory: Path) {
        clearArtifactDirectory(compatibilityDirectory)
        Files.createDirectories(compatibilityDirectory)
        canonicalDirectory.toFile().listFiles()?.forEach { file ->
            val target = compatibilityDirectory.resolve(file.name)
            if (file.isDirectory) {
                file.copyRecursively(target.toFile(), overwrite = true)
            } else {
                Files.copy(file.toPath(), target)
            }
        }
    }


    private fun migrateLegacyPreparedArtifact(artifact: Artifact, compatibilityDirectory: Path, canonicalDirectory: Path, remoteChecksum: String?): Boolean {
        val legacyJar = compatibilityDirectory.resolve("classes.jar")
        if (!canReusePreparedArtifact(artifact, compatibilityDirectory, legacyJar, null, remoteChecksum)) {
            return false
        }
        clearArtifactDirectory(canonicalDirectory)
        Files.createDirectories(canonicalDirectory)
        compatibilityDirectory.toFile().listFiles()?.forEach { file ->
            val target = canonicalDirectory.resolve(file.name)
            if (file.isDirectory) {
                file.copyRecursively(target.toFile(), overwrite = true)
            } else {
                Files.copy(file.toPath(), target)
            }
        }
        writeArtifactMetadata(artifact, canonicalDirectory, remoteChecksum)
        publishCompatibilityView(canonicalDirectory, compatibilityDirectory)
        return true
    }

    private fun fetchRemoteChecksum(artifact: Artifact): String? {
        val coordinatePath = artifact.groupId.replace('.', '/') + "/" + artifact.artifactId + "/" + artifact.version
        val fileName = "${artifact.artifactId}-${artifact.version}.${artifact.extension}"
        for (repository in repositories) {
            val baseUrl = repository.getURL().trimEnd('/')
            val artifactUrl = "$baseUrl/$coordinatePath/$fileName"
            val sha1 = fetchTextIfAvailable("$artifactUrl.sha1")?.substringBefore(' ')
            if (!sha1.isNullOrBlank()) {
                return sha1.lowercase(Locale.ENGLISH)
            }
            val md5 = fetchTextIfAvailable("$artifactUrl.md5")?.substringBefore(' ')
            if (!md5.isNullOrBlank()) {
                return md5.lowercase(Locale.ENGLISH)
            }
        }
        return null
    }

    private fun fetchTextIfAvailable(urlString: String): String? {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true
            connection.inputStream.bufferedReader().use { reader ->
                if (connection.responseCode in 200..299) {
                    reader.readText().trim().ifEmpty { null }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sanitizePathSegment(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun readStringMap(path: Path): Map<String, String>? {
        return try {
            if (Files.notExists(path)) {
                null
            } else {
                Gson().fromJson(path.readText(), Helper.TYPE_STRING_MAP)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findPackageName(path: String, defaultValue: String): String {
        val manifest =
            File(path).walk().filter { it.isFile && it.name == "AndroidManifest.xml" }.firstOrNull()
        val content = manifest?.readText() ?: return defaultValue
        val p = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL)
        val m = p.matcher(content)
        if (m.find()) {
            return m.group(1)!!
        }

        return defaultValue
    }

    private fun unzip(path: Path) {
        val zipFile = ZipFile(path.toFile())
        zipFile.use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val entryDestination = path.parent.resolve(entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(entryDestination)
                } else {
                    Files.createDirectories(entryDestination.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(entryDestination).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun compileJar(jarFile: Path, jars: List<Path>, libraryJars: List<Path>) {
        Files.createDirectories(jarFile.parent)
        val fingerprint = buildDexFingerprint(jarFile, jars, libraryJars)
        val dexMetadata = jarFile.parent.resolve(DEX_METADATA_FILE)
        val classesDex = jarFile.parent.resolve("classes.dex")

        if (Files.exists(classesDex) && Files.exists(dexMetadata) && dexMetadata.readText() == fingerprint) {
            return
        }

        jarFile.parent.toFile().listFiles()?.forEach { file ->
            if (file.name.matches(Regex("""classes(\d+)?\.dex"""))) {
                file.delete()
            }
        }

        D8.run(
            D8Command.builder()
                .setMode(CompilationMode.RELEASE)
                .setMinApiLevel(buildSettings.minSdkVersion)
                .addProgramFiles(jarFile)
                .addLibraryFiles(libraryJars)
                .addClasspathFiles(jars)
                .setOutput(jarFile.parent, OutputMode.DexIndexed)
                .build()
        )

        dexMetadata.writeText(fingerprint)
    }

    private fun buildDexFingerprint(jarFile: Path, jars: List<Path>, libraryJars: List<Path>): String {
        val fingerprint = StringBuilder()
        fingerprint.append("minSdk=").append(buildSettings.minSdkVersion).append('\n')
        appendFingerprintEntry(fingerprint, jarFile)
        jars.sortedBy { it.toAbsolutePath().toString() }.forEach { appendFingerprintEntry(fingerprint, it) }
        libraryJars.sortedBy { it.toAbsolutePath().toString() }.forEach { appendFingerprintEntry(fingerprint, it) }
        return fingerprint.toString()
    }

    private fun appendFingerprintEntry(builder: StringBuilder, path: Path) {
        val file = path.toFile()
        builder.append(path.toAbsolutePath()).append('|')
        if (file.exists()) {
            builder.append(file.length()).append('|').append(file.lastModified())
        } else {
            builder.append("missing")
        }
        builder.append('\n')
    }
}
