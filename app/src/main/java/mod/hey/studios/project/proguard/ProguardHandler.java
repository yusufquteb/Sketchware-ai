package mod.hey.studios.project.proguard;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.ProjectBuilder;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuildProgressReceiver;
import pro.sketchware.utility.FileUtil;

public class ProguardHandler {
    public static String ANDROID_PROGUARD_RULES_PATH = createAndroidRules();
    public static String DEFAULT_PROGUARD_RULES_PATH = "";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DEBUG = "debug";
    private static final String KEY_R8 = "r8";
    private static final String KEY_R8_PROFILE = "r8_profile";

    private final String config_path;
    private final String fm_config_path;
    private final String r8_profile_rules_path;

    public ProguardHandler(String sc_id) {
        DEFAULT_PROGUARD_RULES_PATH = createDefaultRules(sc_id);
        config_path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/proguard";
        fm_config_path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/proguard_fm";
        r8_profile_rules_path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/r8-profile-rules.pro";

        if (!FileUtil.isExistFile(config_path)) {
            FileUtil.writeFile(config_path, getDefaultConfig());
        }
    }

    private static String createAndroidRules() {
        String rulePath = FileUtil.getExternalStorageDir() + "/.sketchware/libs/android-proguard-rules.pro";

        if (!FileUtil.isExistFile(rulePath)) {
            FileUtil.writeFile(rulePath, """
                    -dontusemixedcaseclassnames
                    -dontskipnonpubliclibraryclasses
                    -verbose
                    
                    -dontoptimize
                    -dontpreverify
                    
                    -keepattributes *Annotation*
                    -keep public class com.google.vending.licensing.ILicensingService
                    -keep public class com.android.vending.licensing.ILicensingService
                    
                    -keepclasseswithmembernames class * {
                        native <methods>;
                    }
                    
                    -keepclassmembers public class * extends android.view.View {
                       void set*(***);
                       *** get*();
                    }
                    
                    -keepclassmembers class * extends android.app.Activity {
                       public void *(android.view.View);
                    }
                    
                    -keepclassmembers enum * {
                        public static **[] values();
                        public static ** valueOf(java.lang.String);
                    }
                    
                    -keepclassmembers class * implements android.os.Parcelable {
                      public static final android.os.Parcelable$Creator CREATOR;
                    }
                    
                    -keepclassmembers class **.R$* {
                        public static <fields>;
                    }
                    
                    -dontwarn android.support.**
                    
                    -keep class android.support.annotation.Keep
                    
                    -keep @android.support.annotation.Keep class * {*;}
                    
                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <methods>;
                    }
                    
                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <fields>;
                    }
                    
                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <init>(...);
                    }
                    
                    -keepclassmembers class * {
                        @android.webkit.JavascriptInterface <methods>;\
                    }
                    
                    -dontwarn android.arch.**
                    -dontwarn android.lifecycle.**
                    -keep class android.arch.** { *; }
                    -keep class android.lifecycle.** { *; }
                    
                    -dontwarn androidx.arch.**
                    -dontwarn androidx.lifecycle.**
                    -keep class androidx.arch.** { *; }
                    -keep class androidx.lifecycle.** { *; }
                    """);
        }

        return rulePath;
    }

    private static String createDefaultRules(String sc_id) {
        String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/proguard-rules.pro";

        if (!FileUtil.isExistFile(path)) {
            FileUtil.writeFile(path, """
                    -repackageclasses
                    -ignorewarnings
                    -dontwarn
                    -dontnote
                    """);
        }

        return path;
    }

    private String getDefaultConfig() {
        HashMap<String, String> defaultConfig = new HashMap<>();

        defaultConfig.put(KEY_ENABLED, "false");
        defaultConfig.put(KEY_DEBUG, "false");
        defaultConfig.put(KEY_R8, "false");
        defaultConfig.put(KEY_R8_PROFILE, R8Profiles.PROFILE_STANDARD);

        return new Gson().toJson(defaultConfig);
    }

    public String getCustomProguardRules() {
        return DEFAULT_PROGUARD_RULES_PATH;
    }

    public boolean isDebugFilesEnabled() {
        boolean debugFiles = true;
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                if (!config.containsKey(KEY_DEBUG)) return false;

                String debug = config.get(KEY_DEBUG);
                if (debug != null) {
                    debugFiles = debug.equals("true");
                }

            } catch (Exception e) {
                debugFiles = false;
            }
        }

        return debugFiles;
    }

    public boolean isShrinkingEnabled() {
        boolean proguardEnabled = true;
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                String enabled = config.get(KEY_ENABLED);
                if (enabled == null) {
                    proguardEnabled = false;
                } else {
                    proguardEnabled = enabled.equals("true");
                }

            } catch (Exception e) {
                proguardEnabled = false;
            }
        }

        return proguardEnabled;
    }

    public void setProguardEnabled(boolean proguardEnabled) {
        HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put(KEY_ENABLED, String.valueOf(proguardEnabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public boolean isR8Enabled() {
        boolean r8Enabled = true;
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                String enabled = config.get(KEY_R8);
                if (enabled == null) {
                    r8Enabled = false;
                } else {
                    r8Enabled = enabled.equals("true");
                }

            } catch (Exception e) {
                r8Enabled = false;
            }
        }

        return r8Enabled;
    }

    public void setR8Enabled(boolean r8Enabled) {
        HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put(KEY_R8, String.valueOf(r8Enabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }


    public String getR8ProfileId() {
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
                String profile = config.get(KEY_R8_PROFILE);
                if (R8Profiles.isValid(profile)) {
                    return profile;
                }
            } catch (Exception ignored) {
            }
        }
        return R8Profiles.PROFILE_STANDARD;
    }

    public void setR8ProfileId(String profileId) {
        String safeProfile = R8Profiles.isValid(profileId) ? profileId : R8Profiles.PROFILE_STANDARD;
        HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put(KEY_R8_PROFILE, safeProfile);
        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public R8Profiles.Profile getR8Profile() {
        return R8Profiles.get(getR8ProfileId());
    }

    public String getR8ProfileRulesPath() {
        R8Profiles.Profile profile = getR8Profile();
        FileUtil.writeFile(r8_profile_rules_path, String.join("\n", profile.getRules()).trim());
        return r8_profile_rules_path;
    }

    public boolean libIsProguardFMEnabled(String library) {
        boolean enabled;
        if (isShrinkingEnabled() && FileUtil.isExistFile(fm_config_path)) {
            String configContent = FileUtil.readFile(fm_config_path);

            if (configContent.isEmpty()) {
                return false;
            }

            try {
                ArrayList<String> config = new Gson().fromJson(configContent, Helper.TYPE_STRING);
                enabled = config.contains(library);
                return enabled;
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put(KEY_DEBUG, String.valueOf(debugEnabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public void setProguardFMLibs(ArrayList<String> fullModeLibs) {
        FileUtil.writeFile(fm_config_path, new Gson().toJson(fullModeLibs));
    }

    public void start(BuildProgressReceiver progressReceiver, ProjectBuilder builder) throws IOException {
        if (isShrinkingEnabled()) {
            if (isR8Enabled()) {
                progressReceiver.onProgress("Running R8 on classes...", 15);
                builder.runR8();
            } else {
                progressReceiver.onProgress("ProGuarding classes...", 16);
                builder.runProguard();
            }
        }
    }
}
