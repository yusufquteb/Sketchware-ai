package mod.hey.studios.project.proguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public final class R8Profiles {

    public static final String PROFILE_SAFE = "safe";
    public static final String PROFILE_STANDARD = "standard";
    public static final String PROFILE_FIREBASE_HEAVY = "firebase_heavy";
    public static final String PROFILE_GAME = "game";
    public static final String PROFILE_MINIMAL = "minimal";

    private static final LinkedHashMap<String, Profile> PROFILES = new LinkedHashMap<>();

    static {
        register(new Profile(
                PROFILE_SAFE,
                "Safe",
                "Most conservative keep rules. Best for reflection-heavy apps and first-time shrinking.",
                List.of(
                        "-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses",
                        "-keep class kotlin.Metadata { *; }",
                        "-keep class * implements java.io.Serializable { *; }",
                        "-keep class * implements android.os.Parcelable { *; }"
                )
        ));

        register(new Profile(
                PROFILE_STANDARD,
                "Standard",
                "Balanced profile for most apps. Keeps common metadata and strips verbose logging.",
                List.of(
                        "-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses",
                        "-keep class kotlin.Metadata { *; }",
                        "-assumenosideeffects class android.util.Log {",
                        "    public static *** d(...);",
                        "    public static *** i(...);",
                        "    public static *** v(...);",
                        "}"
                )
        ));

        register(new Profile(
                PROFILE_FIREBASE_HEAVY,
                "Firebase-heavy",
                "Adds keep rules for common Firebase runtime entry points and messaging services.",
                List.of(
                        "-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses",
                        "-keep class kotlin.Metadata { *; }",
                        "-keep class com.google.firebase.provider.FirebaseInitProvider { *; }",
                        "-keep class * extends com.google.firebase.messaging.FirebaseMessagingService { *; }",
                        "-keep class * extends com.google.firebase.components.ComponentRegistrar { *; }",
                        "-keep class com.google.firebase.** { *; }",
                        "-keep class com.google.android.gms.** { *; }",
                        "-dontwarn com.google.firebase.**",
                        "-dontwarn com.google.android.gms.**"
                )
        ));

        register(new Profile(
                PROFILE_GAME,
                "Game",
                "Preserves native and renderer entry points while still allowing aggressive shrinking elsewhere.",
                List.of(
                        "-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses",
                        "-keepclasseswithmembernames class * { native <methods>; }",
                        "-keep class * implements android.opengl.GLSurfaceView$Renderer { *; }",
                        "-keep class * extends android.view.SurfaceView { *; }",
                        "-keep class * extends android.app.Service { *; }"
                )
        ));

        register(new Profile(
                PROFILE_MINIMAL,
                "Minimal APK",
                "Smallest output focus. Use only when the app has minimal reflection and no fragile runtime discovery.",
                List.of(
                        "-keepattributes *Annotation*",
                        "-keep class kotlin.Metadata { *; }",
                        "-assumenosideeffects class android.util.Log {",
                        "    public static *** d(...);",
                        "    public static *** i(...);",
                        "    public static *** v(...);",
                        "    public static *** w(...);",
                        "}",
                        "-repackageclasses"
                )
        ));
    }

    private R8Profiles() {
    }

    private static void register(Profile profile) {
        PROFILES.put(profile.getId(), profile);
    }

    public static Profile get(String id) {
        Profile profile = PROFILES.get(id);
        return profile == null ? PROFILES.get(PROFILE_STANDARD) : profile;
    }

    public static boolean isValid(String id) {
        return PROFILES.containsKey(id);
    }

    public static List<Profile> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(PROFILES.values()));
    }

    public static final class Profile {
        private final String id;
        private final String displayName;
        private final String description;
        private final List<String> rules;

        private Profile(String id, String displayName, String description, List<String> rules) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.rules = rules;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getRules() {
            return rules;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
