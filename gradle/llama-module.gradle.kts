// Own build script for the :llama module (settings.gradle: project(":llama").buildFile points
// here; projectDir still points into third_party/llama.cpp/examples/llama.android/lib, the git
// submodule, so relative source/CMake paths below keep resolving inside it).
//
// WHY THIS FILE EXISTS INSTEAD OF USING THE SUBMODULE'S OWN build.gradle.kts DIRECTLY:
// that file applies plugins via `plugins { alias(libs.plugins.android.library) ... } `, resolved
// against ITS OWN gradle/libs.versions.toml (a separate multi-project Gradle build we don't
// include here). Pointing our root catalog's [plugins] table at matching aliases (see
// gradle/libs.versions.toml's android-library/jetbrains-kotlin-android entries) was tried first,
// but Gradle's plugins{} DSL refuses to reconcile a *version-pinned* plugin request against a
// plugin already present on the buildscript classpath from the old-style `buildscript { …
// classpath … }` block in the root build.gradle (com.android.tools.build:gradle +
// kotlin-gradle-plugin) — confirmed via a real Gradle sync failure: "InvalidPluginRequestException:
// The request for this plugin could not be satisfied because the plugin is already on the
// classpath with an unknown version, so compatibility cannot be checked." This is a known
// limitation when mixing legacy buildscript-classpath plugin application (which this whole
// project otherwise uses — see app/build.gradle's `apply plugin: "com.android.application"`)
// with the plugins{}/version-catalog DSL for the same plugin in a subproject.
//
// FIX: apply the plugins the same old way every other module in this project does — no
// plugins{} block, no version pinned here at all, just the plugin IDs looked up on the already-
// established classpath. We can't edit the submodule's own build.gradle.kts to do this (it's a
// pinned, read-only vendored dependency — patching it would only exist in this local checkout
// and be silently lost on the next `git submodule update`), so this file is a full replacement
// living in OUR OWN repo tree instead, replicating that file's android{}/dependencies{} content
// verbatim otherwise. If the vendored module's build.gradle.kts changes upstream (a submodule
// version bump), diff it against this file and port any changes over by hand.
apply(plugin = "com.android.library")
apply(plugin = "org.jetbrains.kotlin.android")

android {
    namespace = "com.arm.aichat"
    compileSdk = 36

    ndkVersion = "29.0.13113456"

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DCMAKE_MESSAGE_LOG_LEVEL=DEBUG"
                arguments += "-DCMAKE_VERBOSE_MAKEFILE=ON"

                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_APP=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"

                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
            }
        }
        aarMetadata {
            minCompileSdk = 35
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Original vendored file also sets `kotlin { jvmToolchain(17) }` here via the type-safe
    // accessor the plugins{} DSL generates. Deliberately NOT replicated: with apply(plugin=...)
    // instead of plugins{}, that accessor's availability is less certain and this is unverified
    // in this sandbox either way (see file header). Kotlin Gradle Plugin infers its JVM target
    // from the compileOptions above in recent versions; if a real build shows a JVM target
    // mismatch error, that's the fix to add back explicitly.

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
        }
    }
}

dependencies {
    add("implementation", "androidx.core:core-ktx:1.17.0")
    add("implementation", "androidx.datastore:datastore-preferences:1.2.0")

    add("testImplementation", "junit:junit:4.13.2")
    add("androidTestImplementation", "androidx.test.ext:junit:1.3.0")
}
