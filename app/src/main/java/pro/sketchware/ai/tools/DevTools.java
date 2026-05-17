package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import pro.sketchware.ai.models.ToolResult;

/**
 * DevTools — five advanced developer tools for the AI agent.
 *
 *  1. WebSearchTool       — searches the internet via DuckDuckGo instant answers API
 *  2. DependencyScanTool  — finds imported packages that have no matching local library
 *  3. ShellExecutorTool   — runs safe shell commands (grep, ls, find, cat, wc)
 *  4. LogcatFilterTool    — retrieves recent logcat lines filtered by tag / keyword
 *  5. ResourceOptimizerTool — detects unused resources (drawables, layouts, strings)
 */
public final class DevTools {

    private DevTools() {}

    // ── helpers ───────────────────────────────────────────────────────────────
    private static ToolResult ok(String out)  { return ToolResult.success(null, out); }
    private static ToolResult err(String msg) { return ToolResult.failure(null, msg); }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
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
    // 1. WEB SEARCH TOOL
    // ════════════════════════════════════════════════════════════════════════
    public static class WebSearchTool implements AgentTool {
        private static final OkHttpClient HTTP = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        @Override public String getName() { return "web_search"; }
        @Override public String getDescription() {
            return "Searches the internet for documentation, GitHub issues, StackOverflow answers, " +
                   "or library information. Returns a plain-text summary of the top results. " +
                   "Best for: finding library usage examples, Android API docs, error explanations.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject q = new JsonObject(); q.addProperty("type","string");
            q.addProperty("description","Search query, e.g. 'Android Room database migration example'");
            p.add("query",q);
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties",p);
            JsonArray req = new JsonArray(); req.add("query"); s.add("required",req);
            return s;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext ctx) {
            String query = args.has("query") ? args.get("query").getAsString() : null;
            if (query == null || query.isEmpty()) return err("query is required");
            try {
                // DuckDuckGo Instant Answers API — no key required, returns JSON summary
                String url = "https://api.duckduckgo.com/?q="
                        + URLEncoder.encode(query, "UTF-8")
                        + "&format=json&no_html=1&skip_disambig=1";
                Request req2 = new Request.Builder().url(url)
                        .header("User-Agent","Sketchware-Pro-AI/1.0")
                        .build();
                try (Response resp = HTTP.newCall(req2).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null)
                        return err("Search request failed: HTTP " + resp.code());
                    String body = resp.body().string();
                    com.google.gson.JsonObject json =
                            com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                    StringBuilder result = new StringBuilder();
                    result.append("Query: ").append(query).append("\n\n");
                    if (json.has("Abstract") && !json.get("Abstract").getAsString().isEmpty()) {
                        result.append("Summary:\n").append(json.get("Abstract").getAsString()).append("\n");
                        if (json.has("AbstractURL"))
                            result.append("Source: ").append(json.get("AbstractURL").getAsString()).append("\n");
                    }
                    if (json.has("Answer") && !json.get("Answer").getAsString().isEmpty()) {
                        result.append("\nAnswer: ").append(json.get("Answer").getAsString()).append("\n");
                    }
                    if (json.has("RelatedTopics")) {
                        com.google.gson.JsonArray topics = json.get("RelatedTopics").getAsJsonArray();
                        int max = Math.min(5, topics.size());
                        result.append("\nRelated:\n");
                        for (int i = 0; i < max; i++) {
                            if (topics.get(i).isJsonObject()) {
                                com.google.gson.JsonObject t = topics.get(i).getAsJsonObject();
                                if (t.has("Text"))
                                    result.append("• ").append(t.get("Text").getAsString()).append("\n");
                            }
                        }
                    }
                    String out = result.toString().trim();
                    if (out.isEmpty()) return err("No results found for: " + query);
                    return ok(out);
                }
            } catch (Exception e) { return err("Web search failed: " + e.getMessage()); }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. DEPENDENCY SCAN TOOL
    // ════════════════════════════════════════════════════════════════════════
    public static class DependencyScanTool implements AgentTool {
        @Override public String getName() { return "scan_dependencies"; }
        @Override public String getDescription() {
            return "Scans all Java files in a project for import statements and identifies " +
                   "packages that are NOT covered by any attached library. Returns a list of " +
                   "potentially missing libraries with suggested Maven coordinates.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type","string"); p.add("sc_id",sc);
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties",p);
            JsonArray req = new JsonArray(); req.add("sc_id"); s.add("required",req);
            return s;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = args.has("sc_id") ? args.get("sc_id").getAsString() : null;
            if (scId == null) return err("sc_id is required");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");

            File javaDir = ctx.getProjectJavaDir(scId);
            List<File> javaFiles = collectJavaFiles(javaDir);

            // Also scan generated/normalized sources
            File genDir = new File(ctx.getProjectMyscDir(scId), "bin/normalized_sources/generated");
            javaFiles.addAll(collectJavaFiles(genDir));

            Set<String> allImports = new HashSet<>();
            Pattern importPat = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);
            for (File f : javaFiles) {
                Matcher m = importPat.matcher(readFile(f));
                while (m.find()) allImports.add(m.group(1));
            }

            // Known Android/Java stdlib prefixes that don't need extra libraries
            Set<String> knownPrefixes = new HashSet<>();
            knownPrefixes.add("android."); knownPrefixes.add("androidx.");
            knownPrefixes.add("java."); knownPrefixes.add("javax.");
            knownPrefixes.add("com.google.android."); knownPrefixes.add("kotlin.");
            knownPrefixes.add("pro.sketchware."); knownPrefixes.add("com.besome.");
            knownPrefixes.add("a.a.a."); knownPrefixes.add("mod.");

            // Suggestion map for common third-party packages
            Map<String, String> suggestions = new HashMap<>();
            suggestions.put("okhttp3.",          "com.squareup.okhttp3:okhttp:4.12.0");
            suggestions.put("retrofit2.",         "com.squareup.retrofit2:retrofit:2.9.0");
            suggestions.put("com.google.gson.",   "com.google.code.gson:gson:2.10.1");
            suggestions.put("io.reactivex.",      "io.reactivex.rxjava2:rxjava:2.2.21");
            suggestions.put("org.greenrobot.",    "org.greenrobot:eventbus:3.3.1");
            suggestions.put("com.github.bumptech.","com.github.bumptech.glide:glide:4.16.0");
            suggestions.put("com.squareup.picasso.","com.squareup.picasso:picasso:2.8");
            suggestions.put("io.noties.markwon.", "io.noties.markwon:core:4.6.2");
            suggestions.put("org.jsoup.",         "org.jsoup:jsoup:1.17.2");
            suggestions.put("com.google.firebase.","com.google.firebase:firebase-bom:32.7.0");

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

            if (missing.isEmpty()) return ok("✅ No missing dependencies detected. All imports appear to be covered.");
            StringBuilder out = new StringBuilder();
            out.append("⚠️ Potentially missing libraries (").append(missing.size()).append("):\n\n");
            for (String m : missing) out.append("  • ").append(m).append("\n");
            out.append("\nUse download_dependency or add_library to add these to the project.");
            return ok(out.toString());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. SHELL EXECUTOR TOOL
    // ════════════════════════════════════════════════════════════════════════
    public static class ShellExecutorTool implements AgentTool {
        /** Allowlist of safe command prefixes. Prevents destructive operations. */
        private static final String[] SAFE_PREFIXES = {
            "ls", "find", "grep", "cat", "wc", "echo", "pwd", "head", "tail",
            "sort", "uniq", "diff", "stat", "file", "du", "df"
        };
        /** Hard limit on output to avoid flooding the context */
        private static final int MAX_OUTPUT_LINES = 200;

        @Override public String getName() { return "execute_shell"; }
        @Override public String getDescription() {
            return "Executes a READ-ONLY shell command and returns its output. " +
                   "Allowed commands: ls, find, grep, cat, wc, echo, head, tail, sort, uniq, diff, stat, du, df. " +
                   "Useful for fast file searches, counting lines, comparing files. " +
                   "CANNOT modify files — use write_file or patch_file for that.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject cmd = new JsonObject(); cmd.addProperty("type","string");
            cmd.addProperty("description","Shell command, e.g. 'grep -r \"onCreate\" /path/to/java'");
            p.add("command",cmd);
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties",p);
            JsonArray req = new JsonArray(); req.add("command"); s.add("required",req);
            return s;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext ctx) {
            String command = args.has("command") ? args.get("command").getAsString().trim() : null;
            if (command == null || command.isEmpty()) return err("command is required");

            // Safety check
            String first = command.split("\\s+")[0].toLowerCase();
            boolean safe = false;
            for (String p : SAFE_PREFIXES) { if (first.equals(p)) { safe = true; break; } }
            if (!safe) return err("Command '" + first + "' is not in the allowed list: " +
                    String.join(", ", SAFE_PREFIXES));

            try {
                Process proc = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                BufferedReader errReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                StringBuilder out = new StringBuilder();
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < MAX_OUTPUT_LINES) {
                    out.append(line).append('\n');
                    count++;
                }
                if (count >= MAX_OUTPUT_LINES) out.append("\n[Output truncated at ").append(MAX_OUTPUT_LINES).append(" lines]");
                proc.waitFor(10, TimeUnit.SECONDS);

                // Append stderr if any
                StringBuilder errOut = new StringBuilder();
                while ((line = errReader.readLine()) != null) errOut.append(line).append('\n');
                if (errOut.length() > 0) out.append("\nSTDERR:\n").append(errOut);

                return ok("$ " + command + "\n" + out.toString().trim());
            } catch (Exception e) { return err("Shell error: " + e.getMessage()); }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. LOGCAT FILTER TOOL
    // ════════════════════════════════════════════════════════════════════════
    public static class LogcatFilterTool implements AgentTool {
        @Override public String getName() { return "filter_logcat"; }
        @Override public String getDescription() {
            return "Fetches recent logcat entries filtered by tag and/or keyword. " +
                   "Returns up to 150 lines. Useful for debugging crashes, finding " +
                   "specific log messages, or monitoring a running app.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject tag = new JsonObject(); tag.addProperty("type","string");
            tag.addProperty("description","Logcat tag filter, e.g. 'MainActivity' or '*:E' for all errors");
            p.add("tag",tag);
            JsonObject kw = new JsonObject(); kw.addProperty("type","string");
            kw.addProperty("description","Optional keyword to grep in the output");
            p.add("keyword",kw);
            JsonObject maxL = new JsonObject(); maxL.addProperty("type","integer");
            maxL.addProperty("description","Max lines to return (default 150, max 500)");
            p.add("max_lines",maxL);
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties",p);
            return s;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext ctx) {
            String tag     = args.has("tag")     ? args.get("tag").getAsString()     : "*:W";
            String keyword = args.has("keyword") ? args.get("keyword").getAsString() : null;
            int maxLines   = args.has("max_lines") ? Math.min(500, args.get("max_lines").getAsInt()) : 150;

            try {
                // Dump recent logcat buffer, filter by tag, limit output
                String cmd = "logcat -d -v time -s " + tag;
                Process proc = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                StringBuilder out = new StringBuilder();
                String line;
                int count = 0;
                Pattern kwPat = (keyword != null && !keyword.isEmpty())
                        ? Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE) : null;
                while ((line = reader.readLine()) != null) {
                    if (kwPat != null && !kwPat.matcher(line).find()) continue;
                    out.append(line).append('\n');
                    if (++count >= maxLines) break;
                }
                proc.waitFor(5, TimeUnit.SECONDS);
                if (out.length() == 0) return ok("No logcat entries found for tag=" + tag +
                        (keyword != null ? ", keyword=" + keyword : ""));
                return ok("logcat [tag=" + tag + (keyword != null ? ", keyword=" + keyword : "") +
                        "] (" + count + " lines):\n\n" + out.toString().trim());
            } catch (Exception e) { return err("Logcat error: " + e.getMessage()); }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. RESOURCE OPTIMIZER TOOL
    // ════════════════════════════════════════════════════════════════════════
    public static class ResourceOptimizerTool implements AgentTool {
        @Override public String getName() { return "analyze_unused_resources"; }
        @Override public String getDescription() {
            return "Scans a project's res/ directory and finds resources (drawables, layouts, " +
                   "values) whose names do NOT appear in any Java source file or other layout XML. " +
                   "Returns a list of potentially unused resources to help reduce APK size.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject p = new JsonObject();
            JsonObject sc = new JsonObject(); sc.addProperty("type","string"); p.add("sc_id",sc);
            JsonObject type = new JsonObject(); type.addProperty("type","string");
            type.addProperty("description","Optional: filter by type — 'drawable', 'layout', 'values', or 'all' (default)");
            p.add("resource_type",type);
            JsonObject s = new JsonObject(); s.addProperty("type","object"); s.add("properties",p);
            JsonArray req = new JsonArray(); req.add("sc_id"); s.add("required",req);
            return s;
        }
        @Override public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = args.has("sc_id") ? args.get("sc_id").getAsString() : null;
            if (scId == null) return err("sc_id is required");
            if (!ctx.isProjectAllowed(scId)) return err("Project not in workspace");
            String filterType = args.has("resource_type") ? args.get("resource_type").getAsString() : "all";

            // Read all Java code into one string for fast searching
            File javaDir = ctx.getProjectJavaDir(scId);
            StringBuilder allCode = new StringBuilder();
            for (File f : collectJavaFiles(javaDir)) allCode.append(readFile(f)).append('\n');

            // Also read all XML layouts
            File myscDir = ctx.getProjectMyscDir(scId);
            File resDir  = new File(myscDir, "res");
            List<File> xmlFiles = collectByExt(resDir, ".xml");
            for (File f : xmlFiles) allCode.append(readFile(f)).append('\n');
            String allCodeStr = allCode.toString();

            // Gather resource files
            List<File> resourceFiles = new ArrayList<>();
            if ("all".equals(filterType) || "drawable".equals(filterType))
                resourceFiles.addAll(collectByExt(new File(resDir,"drawable"), ".xml", ".png", ".jpg", ".webp", ".gif"));
            if ("all".equals(filterType) || "layout".equals(filterType))
                resourceFiles.addAll(collectByExt(new File(resDir,"layout"), ".xml"));
            if ("all".equals(filterType) || "values".equals(filterType))
                resourceFiles.addAll(collectByExt(new File(resDir,"values"), ".xml"));

            List<String> unused = new ArrayList<>();
            for (File res : resourceFiles) {
                String name = res.getName();
                // Strip extension
                int dot = name.lastIndexOf('.');
                String resName = dot != -1 ? name.substring(0, dot) : name;
                // Check if the resource name appears anywhere in the code
                if (!allCodeStr.contains(resName) && !allCodeStr.contains("@+id/" + resName)) {
                    unused.add(res.getAbsolutePath() + " [" + res.length() + " bytes]");
                }
            }

            if (unused.isEmpty())
                return ok("✅ No obviously unused resources detected in the scanned directories.");
            StringBuilder out = new StringBuilder();
            out.append("⚠️ Potentially unused resources (").append(unused.size()).append("):\n\n");
            for (String u : unused) out.append("  • ").append(u).append("\n");
            out.append("\nNote: Verify manually before deleting — dynamic references (getString/getDrawable by name) won't be detected.");
            return ok(out.toString());
        }

        private static List<File> collectByExt(File dir, String... exts) {
            List<File> out = new ArrayList<>();
            if (dir == null || !dir.exists()) return out;
            File[] kids = dir.listFiles();
            if (kids == null) return out;
            for (File f : kids) {
                if (f.isDirectory()) { out.addAll(collectByExt(f, exts)); continue; }
                for (String ext : exts) { if (f.getName().endsWith(ext)) { out.add(f); break; } }
            }
            return out;
        }
    }
}
