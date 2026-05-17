# ═══════════════════════════════════════════════════════════════════
#  Sketchware Pro – ProGuard Rules
#  minifyEnabled = true (release only)
#  shrinkResources = true
# ═══════════════════════════════════════════════════════════════════

# ── Stack trace readability ───────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Keep all Sketchware Pro Activities, Services, etc. ────────────
# (Anything registered in AndroidManifest must keep its class name)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference

# ── Keep View-based bindings & custom Views ───────────────────────
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ── Keep all Fragment classes ─────────────────────────────────────
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Fragment

# ── Gson: keep data-model classes used with Gson serialization ────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep all HashMap-serialized models (Sketchware stores projects as JSON)
-keep class java.util.HashMap { *; }
-keep class java.util.ArrayList { *; }

# ── Sketchware core packages: keep class names (obfuscate internals) ─
-keep class a.a.a.** { *; }
-keep class pro.sketchware.** { *; }
-keep class mod.** { *; }
-keep class dev.aldi.sayuti.** { *; }
-keep class extensions.** { *; }

# ── Builder / compiler pipeline ──────────────────────────────────
# These use reflection heavily; keep them intact
-keep class a.a.a.ProjectBuilder { *; }
-keep class a.a.a.Ix { *; }
-keep class a.a.a.Jx { *; }
-keep class a.a.a.Lx { *; }
-keep class a.a.a.Ox { *; }
-keep class a.a.a.yq { *; }

# ── Local & built-in library managers ────────────────────────────
-keep class dev.aldi.sayuti.editor.manage.** { *; }
-keep class mod.jbk.build.** { *; }
-keep class mod.jbk.editor.** { *; }

# ── ProGuard integration classes ──────────────────────────────────
-keep class mod.hey.studios.project.proguard.** { *; }

# ── AI Agent subsystem ───────────────────────────────────────────
-keep class pro.sketchware.ai.** { *; }

# ── Kotlin & Coroutines ───────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontnote kotlin.**

# ── OkHttp / Okio ─────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Jackson / JSON processing ─────────────────────────────────────
-dontwarn com.fasterxml.jackson.**

# ── Apache Commons / Logging ─────────────────────────────────────
-dontwarn org.apache.**
-dontwarn org.slf4j.**

# ── Bouncycastle (used by ProGuard-core) ─────────────────────────
-dontwarn org.bouncycastle.**

# ── R8 / ProGuard itself (used as a library) ─────────────────────
-keep class com.android.tools.r8.** { *; }
-dontwarn com.android.tools.r8.**
-keep class com.guardsquare.proguard.** { *; }
-dontwarn com.guardsquare.**

# ── Enum classes: required for proper serialization ───────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable ───────────────────────────────────────────────────
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ── Serializable ─────────────────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── Suppress common warnings from 3rd-party libs ─────────────────
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
