package pro.sketchware.library;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps built-in library folder names to their Maven coordinates.
 * Delegates version/base-name extraction to BuiltInLocalLibCooperation.
 */
public final class BuiltInLibMavenCoords {

    private static final Pattern VER_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)+)$");

    // Known built-in library base name -> Maven coordinate (groupId:artifactId)
    private static final Map<String, String> COORDS = new LinkedHashMap<>();
    static {
        COORDS.put("appcompat",           "androidx.appcompat:appcompat");
        COORDS.put("recyclerview",        "androidx.recyclerview:recyclerview");
        COORDS.put("material",            "com.google.android.material:material");
        COORDS.put("constraintlayout",    "androidx.constraintlayout:constraintlayout");
        COORDS.put("cardview",            "androidx.cardview:cardview");
        COORDS.put("browser",             "androidx.browser:browser");
        COORDS.put("firebase-auth",       "com.google.firebase:firebase-auth");
        COORDS.put("firebase-database",   "com.google.firebase:firebase-database");
        COORDS.put("firebase-storage",    "com.google.firebase:firebase-storage");
        COORDS.put("firebase-messaging",  "com.google.firebase:firebase-messaging");
        COORDS.put("firebase-analytics",  "com.google.firebase:firebase-analytics");
        COORDS.put("firebase-firestore",  "com.google.firebase:firebase-firestore");
        COORDS.put("play-services-ads",   "com.google.android.gms:play-services-ads");
        COORDS.put("play-services-maps",  "com.google.android.gms:play-services-maps");
        COORDS.put("play-services-auth",  "com.google.android.gms:play-services-auth");
        COORDS.put("play-services-location", "com.google.android.gms:play-services-location");
        COORDS.put("gson",                "com.google.code.gson:gson");
        COORDS.put("retrofit",            "com.squareup.retrofit2:retrofit");
        COORDS.put("okhttp",              "com.squareup.okhttp3:okhttp");
        COORDS.put("glide",               "com.github.bumptech.glide:glide");
        COORDS.put("picasso",             "com.squareup.picasso:picasso");
        COORDS.put("room-runtime",        "androidx.room:room-runtime");
        COORDS.put("room-ktx",            "androidx.room:room-ktx");
        COORDS.put("lifecycle-viewmodel", "androidx.lifecycle:lifecycle-viewmodel");
        COORDS.put("lifecycle-livedata",  "androidx.lifecycle:lifecycle-livedata");
        COORDS.put("navigation-fragment", "androidx.navigation:navigation-fragment");
        COORDS.put("navigation-ui",       "androidx.navigation:navigation-ui");
    }

    private BuiltInLibMavenCoords() {}

    /** Extracts the version suffix from a folder name, e.g. "retrofit-2.9.0" → "2.9.0". */
    public static String extractVersion(String folderName) {
        if (folderName == null) return "";
        Matcher m = VER_PATTERN.matcher(folderName);
        return m.find() ? m.group(1) : "";
    }

    /** Strips the version suffix from a folder name, e.g. "retrofit-2.9.0" → "retrofit". */
    public static String extractBase(String folderName) {
        if (folderName == null) return "";
        Matcher m = VER_PATTERN.matcher(folderName);
        if (!m.find()) return folderName;
        String stripped = folderName.substring(0, folderName.length() - m.group(1).length());
        return stripped.replaceAll("[-_]$", "");
    }

    /**
     * Returns the full Maven coordinate for a folder name (with or without version),
     * e.g. "retrofit-2.9.0" → "com.squareup.retrofit2:retrofit:2.9.0".
     * Returns null if unknown.
     */
    public static String getCoordinate(String folderName) {
        if (folderName == null) return null;
        String base    = extractBase(folderName);
        String version = extractVersion(folderName);
        String groupArtifact = COORDS.get(base.toLowerCase());
        if (groupArtifact == null) return null;
        return version.isEmpty() ? groupArtifact : (groupArtifact + ":" + version);
    }
}
