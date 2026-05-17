package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.ai.models.ToolResult;

/**
 * LibraryDiscoveryTools — أدوات اكتشاف المكتبات والبحث عن التبعيات.
 *
 * الأدوات المتاحة:
 *   search_maven       — البحث في كتالوج المكتبات المعروفة وإرجاع إحداثيات Maven
 *   scan_dependencies  — فحص ملفات Java للكشف عن imports غير مغطاة بمكتبات
 *
 * تم نقل search_maven من Phase3Tools وscan_dependencies من DevTools
 * لتجميع أدوات اكتشاف المكتبات في ملف واحد منظم.
 */
public final class LibraryDiscoveryTools {

    private LibraryDiscoveryTools() {}

    // ── مساعدات مشتركة ────────────────────────────────────────────────────────

    static ToolResult ok(String output)  { return ToolResult.success(null, output); }
    static ToolResult err(String msg)    { return ToolResult.failure(null, msg); }

    static String req(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        return args.get(key).getAsString().trim();
    }

    static void addP(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", desc);
        props.add(key, p);
    }

    private static List<File> collectJavaFiles(File dir) {
        List<File> out = new ArrayList<>();
        if (dir == null || !dir.exists()) return out;
        File[] kids = dir.listFiles();
        if (kids == null) return out;
        for (File f : kids) {
            if (f.isDirectory()) out.addAll(collectJavaFiles(f));
            else if (f.getName().endsWith(".java")) out.add(f);
        }
        return out;
    }

    private static String readFile(File f) {
        try { return new String(java.nio.file.Files.readAllBytes(f.toPath())); }
        catch (Exception e) { return ""; }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. SEARCH MAVEN TOOL
    // ════════════════════════════════════════════════════════════════════════

    public static class SearchMavenTool implements AgentTool {
        @Override public String getName() { return "search_maven"; }

        @Override public String getDescription() {
            return "Looks up a well-known Android library and returns its Gradle dependency string. "
                 + "Supports Retrofit, OkHttp, Glide, Picasso, Room, Lifecycle, Hilt, "
                 + "Gson, Moshi, Coil, Volley, Lottie, ExoPlayer, Firebase, Material, and more. "
                 + "Use scan_dependencies first to find what's missing, then search_maven to get coordinates.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "library_name", "string", "Library name, e.g. 'Retrofit', 'Glide', 'Room'");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("library_name");
            s.add("required", r);
            return s;
        }

        private static final String[][] CATALOG = {
            {"retrofit",        "com.squareup.retrofit2:retrofit:2.11.0",           "Type-safe HTTP client."},
            {"okhttp",          "com.squareup.okhttp3:okhttp:4.12.0",               "HTTP client. Add logging interceptor too."},
            {"glide",           "com.github.bumptech.glide:glide:4.16.0",           "Image loading. Add compiler annotation processor."},
            {"picasso",         "com.squareup.picasso:picasso:2.8",                 "Simple image loading by Square."},
            {"coil",            "io.coil-kt:coil:2.6.0",                            "Kotlin-first image loading."},
            {"room",            "androidx.room:room-runtime:2.6.1",                 "SQLite ORM. Add room-compiler annotation processor."},
            {"lifecycle",       "androidx.lifecycle:lifecycle-viewmodel:2.8.0",     "ViewModel + LiveData."},
            {"hilt",            "com.google.dagger:hilt-android:2.51",              "Dependency injection. Requires Hilt plugin."},
            {"gson",            "com.google.code.gson:gson:2.10.1",                 "JSON serialization. No setup needed."},
            {"moshi",           "com.squareup.moshi:moshi:1.15.1",                 "Modern JSON library."},
            {"volley",          "com.android.volley:volley:1.2.1",                 "HTTP networking by Google."},
            {"lottie",          "com.airbnb.android:lottie:6.4.0",                 "After Effects animations."},
            {"exoplayer",       "androidx.media3:media3-exoplayer:1.3.1",           "Media playback. Add media3-ui for UI."},
            {"mpandroidchart",  "com.github.PhilJay:MPAndroidChart:v3.1.0",        "Charts. Add JitPack repo."},
            {"material",        "com.google.android.material:material:1.12.0",      "Material Design components."},
            {"firebase_auth",   "com.google.firebase:firebase-auth:23.0.0",         "Firebase Authentication."},
            {"firebase_db",     "com.google.firebase:firebase-database:21.0.0",     "Firebase Realtime Database."},
            {"rxjava",          "io.reactivex.rxjava3:rxjava:3.1.8",               "Reactive extensions."},
            {"workmanager",     "androidx.work:work-runtime:2.9.0",                "Background task scheduling."},
            {"datastore",       "androidx.datastore:datastore-preferences:1.1.1",   "Modern SharedPreferences replacement."},
            {"navigation",      "androidx.navigation:navigation-fragment:2.7.7",    "Jetpack Navigation component."},
            {"paging",          "androidx.paging:paging-runtime:3.3.0",            "Paged list loading."},
            {"markwon",         "io.noties.markwon:core:4.6.2",                    "Markdown rendering."},
            {"jsoup",           "org.jsoup:jsoup:1.17.2",                          "HTML parsing."},
            {"eventbus",        "org.greenrobot:eventbus:3.3.1",                   "Event bus for Android."},
            {"preference",      "androidx.preference:preference:1.2.1",            "Settings/Preference screens."},
            {"viewpager2",      "androidx.viewpager2:viewpager2:1.1.0",            "Swipeable views."},
            {"swiperefresh",    "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0", "Pull-to-refresh layout."},
        };

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String query = req(args, "library_name");
            if (query == null) return err("library_name is required");
            ctx.reportProgress("Searching library catalog...", -1, true);

            String q = query.toLowerCase().replace(" ", "").replace("-", "");
            List<String[]> matches = new ArrayList<>();
            for (String[] entry : CATALOG) {
                if (entry[0].replace("-", "").contains(q) || q.contains(entry[0].replace("-", ""))
                        || entry[1].toLowerCase().contains(q)) {
                    matches.add(entry);
                }
            }

            if (matches.isEmpty()) {
                return ok("No library found for: " + query + "\n\n"
                        + "Use download_dependency with full Maven coordinate:\n"
                        + "  group:artifact:version (e.g. com.squareup.retrofit2:retrofit:2.11.0)\n"
                        + "Search at: https://mvnrepository.com");
            }
            StringBuilder sb = new StringBuilder("Library Search: " + query + "\n" + "=".repeat(40) + "\n\n");
            for (String[] m : matches) {
                sb.append("Dependency: ").append(m[1]).append("\n");
                sb.append("Notes:      ").append(m[2]).append("\n");
                sb.append("Install:    Use download_dependency tool\n\n");
            }
            return ok(sb.toString());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. DEPENDENCY SCAN TOOL
    // ════════════════════════════════════════════════════════════════════════

    public static class DependencyScanTool implements AgentTool {
        @Override public String getName() { return "scan_dependencies"; }

        @Override public String getDescription() {
            return "Scans all Java files in a project for import statements and identifies "
                 + "packages that are NOT covered by any attached library. Returns a list of "
                 + "potentially missing libraries with suggested Maven coordinates. "
                 + "Run this before build_project to catch missing dependencies early.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type", "string"); p.add("sc_id", sc);
            JsonObject s = new JsonObject(); s.addProperty("type", "object"); s.add("properties", p);
            JsonArray req = new JsonArray(); req.add("sc_id"); s.add("required", req);
            return s;
        }

        @Override public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = args.has("sc_id") ? args.get("sc_id").getAsString() : null;
            if (scId == null) return err("sc_id is required");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            File javaDir = ctx.getProjectJavaDir(scId);
            List<File> javaFiles = collectJavaFiles(javaDir);

            File genDir = new File(ctx.getProjectMyscDir(scId), "bin/normalized_sources/generated");
            javaFiles.addAll(collectJavaFiles(genDir));

            Set<String> allImports = new HashSet<>();
            Pattern importPat = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);
            for (File f : javaFiles) {
                Matcher m = importPat.matcher(readFile(f));
                while (m.find()) allImports.add(m.group(1));
            }

            Set<String> knownPrefixes = new HashSet<>();
            knownPrefixes.add("android."); knownPrefixes.add("androidx.");
            knownPrefixes.add("java."); knownPrefixes.add("javax.");
            knownPrefixes.add("com.google.android."); knownPrefixes.add("kotlin.");
            knownPrefixes.add("pro.sketchware."); knownPrefixes.add("com.besome.");
            knownPrefixes.add("a.a.a."); knownPrefixes.add("mod.");

            Map<String, String> suggestions = new HashMap<>();
            suggestions.put("okhttp3.",             "com.squareup.okhttp3:okhttp:4.12.0");
            suggestions.put("retrofit2.",            "com.squareup.retrofit2:retrofit:2.9.0");
            suggestions.put("com.google.gson.",      "com.google.code.gson:gson:2.10.1");
            suggestions.put("io.reactivex.",         "io.reactivex.rxjava2:rxjava:2.2.21");
            suggestions.put("org.greenrobot.",       "org.greenrobot:eventbus:3.3.1");
            suggestions.put("com.github.bumptech.",  "com.github.bumptech.glide:glide:4.16.0");
            suggestions.put("com.squareup.picasso.", "com.squareup.picasso:picasso:2.8");
            suggestions.put("io.noties.markwon.",    "io.noties.markwon:core:4.6.2");
            suggestions.put("org.jsoup.",            "org.jsoup:jsoup:1.17.2");
            suggestions.put("com.google.firebase.",  "com.google.firebase:firebase-bom:32.7.0");
            suggestions.put("com.airbnb.lottie.",    "com.airbnb.android:lottie:6.4.0");
            suggestions.put("androidx.room.",        "androidx.room:room-runtime:2.6.1");
            suggestions.put("androidx.work.",        "androidx.work:work-runtime:2.9.0");
            suggestions.put("coil.",                 "io.coil-kt:coil:2.6.0");

            List<String> missing = new ArrayList<>();
            for (String imp : allImports) {
                boolean covered = false;
                for (String prefix : knownPrefixes) {
                    if (imp.startsWith(prefix)) { covered = true; break; }
                }
                if (!covered) {
                    String suggestion = "";
                    for (Map.Entry<String, String> e : suggestions.entrySet()) {
                        if (imp.startsWith(e.getKey())) { suggestion = " → " + e.getValue(); break; }
                    }
                    missing.add(imp + suggestion);
                }
            }

            if (missing.isEmpty())
                return ok("✅ No missing dependencies detected. All imports appear to be covered.");
            StringBuilder out = new StringBuilder();
            out.append("⚠️ Potentially missing libraries (").append(missing.size()).append("):\n\n");
            for (String m : missing) out.append("  • ").append(m).append("\n");
            out.append("\nUse search_maven to find Maven coordinates, then download_dependency to install.");
            return ok(out.toString());
        }
    }
}
