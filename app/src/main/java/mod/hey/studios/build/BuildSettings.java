package mod.hey.studios.build;

import java.io.Serializable;

import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.utility.FileUtil;

public class BuildSettings extends ProjectSettings implements Serializable {

    public static final String SETTING_ANDROID_JAR_PATH = "android_jar";
    public static final String SETTING_CLASSPATH = "classpath";
    public static final String SETTING_DEXER = "dexer";
    public static final String SETTING_JAVA_VERSION = "java_ver";
    public static final String SETTING_NO_HTTP_LEGACY = "no_http_legacy";
    public static final String SETTING_NO_WARNINGS = "no_warn";
    public static final String SETTING_ENABLE_LOGCAT = "enable_logcat";
    public static final String SETTING_PARALLEL_ECJ = "parallel_ecj";
    public static final String SETTING_AUTO_CLEAN_AFTER_BUILD = "auto_clean_build";

    public static final String SETTING_BUILD_OUTPUT_FORMAT = "build_output_format";
    public static final String SETTING_BUILD_SIGNING_MODE = "build_signing_mode";
    public static final String SETTING_BUILD_KEYSTORE_PATH = "build_keystore_path";
    public static final String SETTING_BUILD_KEY_ALIAS = "build_key_alias";
    public static final String SETTING_BUILD_SIGN_ALGORITHM = "build_sign_algorithm";

    public static final String SETTING_BUILD_OUTPUT_FORMAT_DEBUG_APK = "debug_apk";
    public static final String SETTING_BUILD_OUTPUT_FORMAT_SIGNED_APK = "signed_apk";
    public static final String SETTING_BUILD_OUTPUT_FORMAT_AAB = "aab";

    public static final String SETTING_BUILD_SIGNING_MODE_DEBUG = "debug";
    public static final String SETTING_BUILD_SIGNING_MODE_KEYSTORE = "keystore";
    public static final String SETTING_BUILD_SIGNING_MODE_TESTKEY = "testkey";
    public static final String SETTING_BUILD_SIGNING_MODE_UNSIGNED = "unsigned";

    public static final String SETTING_DEXER_D8 = "D8";
    public static final String SETTING_DEXER_DX = "Dx";
    public static final String SETTING_DEXER_R8 = "R8";
    public static final String SETTING_JAVA_VERSION_1_7 = "1.7";
    public static final String SETTING_JAVA_VERSION_1_8 = "1.8";
    public static final String SETTING_JAVA_VERSION_11 = "11";
    public static final String SETTING_JAVA_VERSION_15 = "15";
    public static final String SETTING_JAVA_VERSION_16 = "16";
    public static final String SETTING_JAVA_VERSION_17 = "17";
    public static final String SETTING_JAVA_VERSION_20 = "20";

    public BuildSettings(String sc_id) {
        super(sc_id);
    }

    @Override
    public String getPath() {
        return FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/build_config";
    }
}
