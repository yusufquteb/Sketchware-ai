package pro.sketchware.library;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the latest stable version of an Android library.
 * Strategy: Google Maven first (androidx/firebase/google), then Maven Central.
 * Has a built-in lookup table for ~100 common libraries to avoid guessing groupId.
 */
public class LibraryVersionChecker {

    private static final String TAG = "LibVersionChecker";

    private static final String GOOGLE_MAVEN_URL =
            "https://dl.google.com/dl/android/maven2/%s/%s/maven-metadata.xml";

    private static final String MAVEN_CENTRAL_URL =
            "https://search.maven.org/solrsearch/select?q=g:%%22%s%%22+AND+a:%%22%s%%22&rows=1&wt=json";

    private static final String[] GOOGLE_MAVEN_PREFIXES = {
        "androidx.", "com.google.android.", "com.google.firebase.",
        "com.google.gms.", "com.android.", "com.google.ar.",
        "com.google.mlkit.", "com.google.accompanist."
    };

    /**
     * Static lookup: normalized library base-name -> "groupId:artifactId"
     * Covers the most common Sketchware local libraries.
     * Key is lowercase, with hyphens/underscores normalized to hyphen.
     */
    private static final Map<String, String> KNOWN_COORDS = new HashMap<>();
    static {
        // Apache Commons
        KNOWN_COORDS.put("commons-codec",      "commons-codec:commons-codec");
        KNOWN_COORDS.put("commons-compress",   "org.apache.commons:commons-compress");
        KNOWN_COORDS.put("commons-io",         "commons-io:commons-io");
        KNOWN_COORDS.put("commons-lang3",      "org.apache.commons:commons-lang3");
        KNOWN_COORDS.put("commons-math3",      "org.apache.commons:commons-math3");
        KNOWN_COORDS.put("commons-text",       "org.apache.commons:commons-text");
        KNOWN_COORDS.put("commons-collections4","org.apache.commons:commons-collections4");

        // Square
        KNOWN_COORDS.put("okhttp",             "com.squareup.okhttp3:okhttp");
        KNOWN_COORDS.put("okio",               "com.squareup.okio:okio");
        KNOWN_COORDS.put("retrofit",           "com.squareup.retrofit2:retrofit");
        KNOWN_COORDS.put("retrofit2",          "com.squareup.retrofit2:retrofit");
        KNOWN_COORDS.put("picasso",            "com.squareup.picasso:picasso");
        KNOWN_COORDS.put("moshi",              "com.squareup.moshi:moshi");

        // Google / AndroidX
        KNOWN_COORDS.put("appcompat",          "androidx.appcompat:appcompat");
        KNOWN_COORDS.put("compat",             "androidx.appcompat:appcompat");
        KNOWN_COORDS.put("material",           "com.google.android.material:material");
        KNOWN_COORDS.put("recyclerview",       "androidx.recyclerview:recyclerview");
        KNOWN_COORDS.put("cardview",           "androidx.cardview:cardview");
        KNOWN_COORDS.put("constraintlayout",   "androidx.constraintlayout:constraintlayout");
        KNOWN_COORDS.put("constraint-layout",  "androidx.constraintlayout:constraintlayout");
        KNOWN_COORDS.put("viewpager2",         "androidx.viewpager2:viewpager2");
        KNOWN_COORDS.put("viewpager",          "androidx.viewpager:viewpager");
        KNOWN_COORDS.put("fragment",           "androidx.fragment:fragment");
        KNOWN_COORDS.put("lifecycle",          "androidx.lifecycle:lifecycle-runtime");
        KNOWN_COORDS.put("room",               "androidx.room:room-runtime");
        KNOWN_COORDS.put("navigation",         "androidx.navigation:navigation-fragment");
        KNOWN_COORDS.put("work",               "androidx.work:work-runtime");
        KNOWN_COORDS.put("paging",             "androidx.paging:paging-runtime");
        KNOWN_COORDS.put("browser",            "androidx.browser:browser");
        KNOWN_COORDS.put("concurrent-futures", "androidx.concurrent:concurrent-futures");
        KNOWN_COORDS.put("annotation",         "androidx.annotation:annotation");
        KNOWN_COORDS.put("core",               "androidx.core:core");
        KNOWN_COORDS.put("core-ktx",           "androidx.core:core-ktx");

        // Firebase
        KNOWN_COORDS.put("firebase-analytics", "com.google.firebase:firebase-analytics");
        KNOWN_COORDS.put("firebase-firestore",  "com.google.firebase:firebase-firestore");
        KNOWN_COORDS.put("firebase-auth",       "com.google.firebase:firebase-auth");
        KNOWN_COORDS.put("firebase-database",   "com.google.firebase:firebase-database");
        KNOWN_COORDS.put("firebase-storage",    "com.google.firebase:firebase-storage");
        KNOWN_COORDS.put("firebase-messaging",  "com.google.firebase:firebase-messaging");

        // OneSignal
        KNOWN_COORDS.put("onesignal-core",            "com.onesignal:core");
        KNOWN_COORDS.put("onesignal-notifications",   "com.onesignal:notifications");
        KNOWN_COORDS.put("onesignal-in-app-messages", "com.onesignal:in-app-messages");
        KNOWN_COORDS.put("onesignal-location",        "com.onesignal:location");

        // Image loading
        KNOWN_COORDS.put("glide",              "com.github.bumptech.glide:glide");
        KNOWN_COORDS.put("coil",               "io.coil-kt:coil");
        KNOWN_COORDS.put("fresco",             "com.facebook.fresco:fresco");
        KNOWN_COORDS.put("circleimageview",    "de.hdodenhof:circleimageview");
        KNOWN_COORDS.put("circleimageview-v",  "de.hdodenhof:circleimageview");
        KNOWN_COORDS.put("universal-image-loader","com.nostra13.universalimageloader:universal-image-loader");

        // Networking
        KNOWN_COORDS.put("volley",             "com.android.volley:volley");
        KNOWN_COORDS.put("gson",               "com.google.code.gson:gson");
        KNOWN_COORDS.put("jackson",            "com.fasterxml.jackson.core:jackson-databind");
        KNOWN_COORDS.put("jackson-databind",   "com.fasterxml.jackson.core:jackson-databind");
        KNOWN_COORDS.put("jackson-core",       "com.fasterxml.jackson.core:jackson-core");
        KNOWN_COORDS.put("jsoup",              "org.jsoup:jsoup");
        KNOWN_COORDS.put("logging-interceptor","com.squareup.okhttp3:logging-interceptor");
        KNOWN_COORDS.put("converter-gson",     "com.squareup.retrofit2:converter-gson");
        KNOWN_COORDS.put("adapter-rxjava2",    "com.squareup.retrofit2:adapter-rxjava2");

        // RxJava
        KNOWN_COORDS.put("rxjava",             "io.reactivex.rxjava3:rxjava");
        KNOWN_COORDS.put("rxjava2",            "io.reactivex.rxjava2:rxjava");
        KNOWN_COORDS.put("rxandroid",          "io.reactivex.rxjava3:rxandroid");
        KNOWN_COORDS.put("rxandroid2",         "io.reactivex.rxjava2:rxandroid");

        // DB
        KNOWN_COORDS.put("realm",              "io.realm:realm-android-library");
        KNOWN_COORDS.put("realm-android",      "io.realm:realm-android-library");
        KNOWN_COORDS.put("sqlite",             "androidx.sqlite:sqlite");
        KNOWN_COORDS.put("sqlcipher",          "net.zetetic:android-database-sqlcipher");
        KNOWN_COORDS.put("greenrobot-eventbus","org.greenrobot:eventbus");
        KNOWN_COORDS.put("eventbus",           "org.greenrobot:eventbus");

        // Kotlin
        KNOWN_COORDS.put("kotlin-stdlib",      "org.jetbrains.kotlin:kotlin-stdlib");
        KNOWN_COORDS.put("kotlin",             "org.jetbrains.kotlin:kotlin-stdlib");
        KNOWN_COORDS.put("kotlinx-coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-android");
        KNOWN_COORDS.put("coroutines",         "org.jetbrains.kotlinx:kotlinx-coroutines-android");

        // UI / Animations
        KNOWN_COORDS.put("lottie",             "com.airbnb.android:lottie");
        KNOWN_COORDS.put("mpandroidchart",     "com.github.PhilJay:MPAndroidChart");
        KNOWN_COORDS.put("chart",              "com.github.PhilJay:MPAndroidChart");
        KNOWN_COORDS.put("achartengine",       "org.achartengine:achartengine");
        KNOWN_COORDS.put("shimmer",            "com.facebook.shimmer:shimmer");
        KNOWN_COORDS.put("expandablelayout",   "net.cachapa.expandablelayout:expandablelayout");
        KNOWN_COORDS.put("swipereveal",        "com.chauthai.swipereveallayout:swipe-reveal-layout");
        KNOWN_COORDS.put("photoview",          "com.github.chrisbanes:PhotoView");
        KNOWN_COORDS.put("subsampling",        "com.davemorrissey.labs:subsampling-scale-image-view");

        // Navigation / Menu
        KNOWN_COORDS.put("bubblenavigation",   "com.gauravk.bubblenavigation:bubblenavigation");
        KNOWN_COORDS.put("bubble-navigation",  "com.gauravk.bubblenavigation:bubblenavigation");

        // Annotations / DI
        KNOWN_COORDS.put("dagger",             "com.google.dagger:dagger");
        KNOWN_COORDS.put("hilt",               "com.google.dagger:hilt-android");
        KNOWN_COORDS.put("koin",               "io.insert-koin:koin-android");
        KNOWN_COORDS.put("butterknife",        "com.jakewharton:butterknife");

        // Misc
        KNOWN_COORDS.put("timber",             "com.jakewharton.timber:timber");
        KNOWN_COORDS.put("dexter",             "com.karumi:dexter");
        KNOWN_COORDS.put("stetho",             "com.facebook.stetho:stetho");
        KNOWN_COORDS.put("leakcanary",         "com.squareup.leakcanary:leakcanary-android");
        KNOWN_COORDS.put("multidex",           "androidx.multidex:multidex");
        KNOWN_COORDS.put("compiler",           "io.realm:realm-android-library"); // generic fallback
        KNOWN_COORDS.put("startup",            "androidx.startup:startup-runtime");
        KNOWN_COORDS.put("tracing",            "androidx.tracing:tracing");
        KNOWN_COORDS.put("collection",         "androidx.collection:collection");
    }

    /** Only successful results are cached — failures are retried */
    private static final Map<String, String> sVersionCache = new HashMap<>();
    private static final ExecutorService sExecutor = Executors.newCachedThreadPool();

    // =========================================================================
    // Public API
    // =========================================================================

    public interface VersionCallback {
        void onResult(String currentVersion, @Nullable String latestVersion);
    }

    public static void checkLatestVersion(@Nullable String coord, VersionCallback callback) {
        checkInternal(coord, callback, false);
    }

    public static void checkLatestVersionForce(@Nullable String coord, VersionCallback callback) {
        checkInternal(coord, callback, true);
    }

    /**
     * Smart check: if coord is null, tries to resolve it from the library folder name.
     * Returns the resolved coordinate (or null) via resolvedCoordCallback before the version result.
     */
    public interface SmartCheckCallback {
        void onCoordResolved(@Nullable String coord);
        void onResult(String currentVersion, @Nullable String latestVersion);
    }

    public static void smartCheck(String libFolderName,
                                  @Nullable String storedCoord,
                                  SmartCheckCallback callback) {
        sExecutor.execute(() -> {
            String coord = storedCoord;
            if (coord == null || coord.isEmpty()) {
                coord = resolveCoordinate(libFolderName);
                if (coord != null) callback.onCoordResolved(coord);
            }
            if (coord == null) {
                callback.onResult("?", null);
                return;
            }

            // Now do the version check with the resolved coord
            String[] parts = coord.split(":");
            if (parts.length < 3) { callback.onResult(coord, null); return; }
            String groupId = parts[0], artifactId = parts[1], currentVer = parts[2];
            String cacheKey = groupId + ":" + artifactId;

            String latest = null;
            synchronized (sVersionCache) {
                if (sVersionCache.containsKey(cacheKey)) latest = sVersionCache.get(cacheKey);
            }
            if (latest == null) {
                latest = fetchLatestStable(groupId, artifactId);
                if (latest != null) {
                    synchronized (sVersionCache) { sVersionCache.put(cacheKey, latest); }
                }
            }
            boolean hasUpdate = latest != null && isNewer(latest, currentVer);
            callback.onResult(currentVer, hasUpdate ? latest : null);
        });
    }

    // =========================================================================
    // Coordinate Resolution
    // =========================================================================

    /**
     * Given a library folder name like "commons-codec-v1.17.1",
     * returns "commons-codec:commons-codec:1.17.1" or null if unknown.
     */
    @Nullable
    public static String resolveCoordinate(String libFolderName) {
        String version = extractVersionFromName(libFolderName);
        String base = libFolderName;
        if (version != null) {
            int idx = libFolderName.toLowerCase().lastIndexOf(version.replace('.', '.'));
            // strip trailing separator + version
            String stripped = libFolderName.substring(0, libFolderName.length() - version.length());
            // remove trailing separator chars: -v, _V_, -, _, etc.
            stripped = stripped.replaceAll("[-_][Vv]?$", "").replaceAll("[-_]$","");
            base = stripped;
        }

        // Normalize: lowercase, replace underscores with hyphens
        String key = base.toLowerCase().replace('_', '-').trim();

        // Direct lookup
        if (KNOWN_COORDS.containsKey(key)) {
            String groupArtifact = KNOWN_COORDS.get(key);
            String[] ga = groupArtifact.split(":");
            String v = version != null ? version : "0";
            return ga[0] + ":" + ga[1] + ":" + v;
        }

        // Prefix-based lookup: try progressively shorter keys
        for (Map.Entry<String, String> entry : KNOWN_COORDS.entrySet()) {
            if (key.startsWith(entry.getKey()) || entry.getKey().startsWith(key)) {
                String groupArtifact = entry.getValue();
                String[] ga = groupArtifact.split(":");
                String v = version != null ? version : "0";
                return ga[0] + ":" + ga[1] + ":" + v;
            }
        }

        return null; // unknown
    }

    // =========================================================================
    // Internal check
    // =========================================================================

    private static void checkInternal(@Nullable String coord, VersionCallback callback, boolean force) {
        if (coord == null || coord.isEmpty()) { sExecutor.execute(() -> callback.onResult("?", null)); return; }
        String[] parts = coord.trim().split(":");
        if (parts.length < 3) { sExecutor.execute(() -> callback.onResult(coord, null)); return; }

        String groupId = parts[0], artifactId = parts[1], currentVer = parts[2];
        String cacheKey = groupId + ":" + artifactId;

        if (force) {
            synchronized (sVersionCache) { sVersionCache.remove(cacheKey); }
        } else {
            // Cache hit: still dispatch via executor so callback is NEVER called
            // synchronously during RecyclerView layout/scroll (prevents IllegalStateException)
            synchronized (sVersionCache) {
                if (sVersionCache.containsKey(cacheKey)) {
                    String cached = sVersionCache.get(cacheKey);
                    final String result = (cached != null && isNewer(cached, currentVer)) ? cached : null;
                    sExecutor.execute(() -> callback.onResult(currentVer, result));
                    return;
                }
            }
        }

        sExecutor.execute(() -> {
            String latest = fetchLatestStable(groupId, artifactId);
            if (latest != null) {
                synchronized (sVersionCache) { sVersionCache.put(cacheKey, latest); }
            }
            callback.onResult(currentVer, (latest != null && isNewer(latest, currentVer)) ? latest : null);
        });
    }

    // =========================================================================
    // Artifact Search
    // =========================================================================

    public interface ArtifactSearchCallback {
        void onResult(@Nullable String groupId, @Nullable String artifactId, @Nullable String version);
    }

    public static void searchMavenForArtifact(String libBaseName, ArtifactSearchCallback callback) {
        sExecutor.execute(() -> {
            String[] r = fetchArtifactSearch(libBaseName);
            callback.onResult(
                r != null && r.length > 0 ? r[0] : null,
                r != null && r.length > 1 ? r[1] : null,
                r != null && r.length > 2 ? r[2] : null);
        });
    }

    // =========================================================================
    // Fetch Logic
    // =========================================================================

    @Nullable
    private static String fetchLatestStable(String groupId, String artifactId) {
        if (isGoogleGroup(groupId)) return fetchFromGoogleMaven(groupId, artifactId);
        return fetchFromMavenCentral(groupId, artifactId);
    }

    private static boolean isGoogleGroup(String groupId) {
        for (String p : GOOGLE_MAVEN_PREFIXES) if (groupId.startsWith(p)) return true;
        return false;
    }

    @Nullable
    private static String fetchFromGoogleMaven(String groupId, String artifactId) {
        try {
            String url = String.format(GOOGLE_MAVEN_URL, groupId.replace('.', '/'), artifactId);
            String xml = httpGet(url);
            if (xml == null) return null;
            List<String> versions = new ArrayList<>();
            Matcher m = Pattern.compile("<version>(.*?)</version>").matcher(xml);
            while (m.find()) {
                String v = m.group(1);
                if (!isPreRelease(v)) versions.add(v);
            }
            if (versions.isEmpty()) return null;
            String best = versions.get(0);
            for (String v : versions) if (isNewer(v, best)) best = v;
            return best;
        } catch (Exception e) {
            Log.w(TAG, "Google Maven failed: " + groupId + ":" + artifactId, e);
            return null;
        }
    }

    @Nullable
    private static String fetchFromMavenCentral(String groupId, String artifactId) {
        try {
            String json = httpGet(String.format(MAVEN_CENTRAL_URL, groupId, artifactId));
            if (json == null) return null;
            JSONArray docs = new JSONObject(json).getJSONObject("response").getJSONArray("docs");
            if (docs.length() == 0) return null;
            String latest = docs.getJSONObject(0).optString("latestVersion", null);
            if (latest != null && !isPreRelease(latest)) return latest;
            return fetchVersionListFallback(groupId, artifactId);
        } catch (Exception e) {
            Log.w(TAG, "Maven Central failed: " + groupId + ":" + artifactId, e);
            return null;
        }
    }

    @Nullable
    private static String fetchVersionListFallback(String groupId, String artifactId) {
        try {
            String url = "https://search.maven.org/solrsearch/select?q=g:%22"
                    + groupId + "%22+AND+a:%22" + artifactId + "%22&core=gav&rows=50&wt=json";
            String json = httpGet(url);
            if (json == null) return null;
            JSONArray docs = new JSONObject(json).getJSONObject("response").getJSONArray("docs");
            String best = null;
            for (int i = 0; i < docs.length(); i++) {
                String v = docs.getJSONObject(i).optString("v", null);
                if (v == null || isPreRelease(v)) continue;
                if (best == null || isNewer(v, best)) best = v;
            }
            return best;
        } catch (Exception e) { return null; }
    }

    @Nullable
    private static String[] fetchArtifactSearch(String query) {
        try {
            String q = query.replaceAll("[_-]", " ").trim();
            String url = "https://search.maven.org/solrsearch/select?q="
                    + java.net.URLEncoder.encode(q, "UTF-8") + "&rows=1&wt=json";
            String json = httpGet(url);
            if (json == null) return null;
            JSONArray docs = new JSONObject(json).getJSONObject("response").getJSONArray("docs");
            if (docs.length() == 0) return null;
            JSONObject doc = docs.getJSONObject(0);
            String g = doc.optString("g", null), a = doc.optString("a", null);
            String v = doc.optString("latestVersion", null);
            if (g == null || a == null) return null;
            return new String[]{g, a, (v != null && !isPreRelease(v)) ? v : ""};
        } catch (Exception e) { return null; }
    }

    // =========================================================================
    // HTTP
    // =========================================================================

    @Nullable
    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("User-Agent", "DayDream-LibChecker/1.0");
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) { return null; }
        finally { if (conn != null) conn.disconnect(); }
    }

    // =========================================================================
    // Version Helpers
    // =========================================================================

    private static boolean isPreRelease(String v) {
        if (v == null) return false;
        String l = v.toLowerCase();
        return l.contains("alpha") || l.contains("beta") || l.contains("-rc") ||
               l.contains(".rc")   || l.contains("snapshot") || l.contains("dev") ||
               l.contains("eap");
    }

    static boolean isNewer(String candidate, String base) {
        int[] c = parseVersion(candidate), b = parseVersion(base);
        for (int i = 0; i < Math.max(c.length, b.length); i++) {
            int cv = i < c.length ? c[i] : 0, bv = i < b.length ? b[i] : 0;
            if (cv > bv) return true; if (cv < bv) return false;
        }
        return false;
    }

    private static int[] parseVersion(String v) {
        if (v == null || v.isEmpty()) return new int[]{0};
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        String[] segs = v.split("-", 2)[0].split("\\.");
        int[] r = new int[segs.length];
        for (int i = 0; i < segs.length; i++) {
            try { r[i] = Integer.parseInt(segs[i]); } catch (NumberFormatException e) { r[i] = 0; }
        }
        return r;
    }

    @Nullable
    public static String extractVersionFromName(String name) {
        Matcher m = Pattern.compile("[_-][Vv]?(\\d+(?:[._]\\d+)*)$").matcher(name);
        if (m.find()) return m.group(1).replace('_', '.');
        // Try without separator: name ends directly with version digits
        m = Pattern.compile("-(\\d+\\.\\d+(?:\\.\\d+)*)$").matcher(name);
        if (m.find()) return m.group(1);
        return null;
    }
    /**
     * استخرج الـ base name (بدون إصدار) من اسم مجلد مكتبة.
     *
     * مثال:
     *   "luaj-jse-3.0.2"         → "luaj-jse"
     *   "material-1.14.0-alpha09" → "material"
     *   "mylib"                   → "mylib"  (مفيش إصدار)
     *
     * ده هو الـ stable id للمكتبة.
     */
    @NonNull
    public static String extractBaseNameFromFolder(@NonNull String folderName) {
        // حاول تجرّد الإصدار
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(.+?)-([Vv]?\\d+(?:[._\\-]\\w+)*)$")
                .matcher(folderName);
        if (m.matches()) return m.group(1);
        return folderName;
    }


    /**
     * Compares two semantic version strings.
     * @return positive if a > b, negative if a < b, 0 if equal.
     */
    public static int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int ia = i < pa.length ? safeParseInt(pa[i]) : 0;
            int ib = i < pb.length ? safeParseInt(pb[i]) : 0;
            if (ia != ib) return Integer.compare(ia, ib);
        }
        return 0;
    }

    private static int safeParseInt(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }
}
