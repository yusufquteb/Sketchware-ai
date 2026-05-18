package pro.sketchware.ai.tools;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.utility.FileUtil;

/**
 * UnusedResourcesTool — scans entire Sketchware Pro project for unused resources.
 *
 * Tools:
 *   scan_unused_resources  — find drawables/strings/fonts/colors not referenced anywhere
 *   delete_unused_resources — delete a confirmed list (user must confirm first)
 */
public final class UnusedResourcesTool {

    private UnusedResourcesTool() {}

    // ── Tool 1: scan_unused_resources ─────────────────────────────────────────

    public static class ScanUnusedResourcesTool implements AgentTool {

        @Override public String getName() { return "scan_unused_resources"; }

        @Override
        public String getDescription() {
            return "Scans the entire project for unused resources (drawables, strings, colors, " +
                    "fonts, raw files, layouts) that are not referenced anywhere in the project " +
                    "(Java code, XML layouts, event blocks, resource files). " +
                    "Returns a categorized list of unused resources with their file paths. " +
                    "ALWAYS show this list to the user and ask for confirmation before calling " +
                    "delete_unused_resources. Never delete without explicit user approval.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id", "string", "Project ID");
            JsonObject types = new JsonObject(); types.addProperty("type", "string");
            types.addProperty("description",
                    "Comma-separated resource types to scan. " +
                    "Options: drawables,strings,colors,fonts,raw. Default: all.");
            p.add("resource_types", types);
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            if (scId == null) return err("sc_id is required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied");

            String typesArg = str(args, "resource_types");
            Set<String> scanTypes = typesArg != null
                    ? new HashSet<>(Arrays.asList(typesArg.toLowerCase().split(",")))
                    : new HashSet<>(Arrays.asList("drawables", "strings", "colors", "fonts", "raw"));

            ctx.reportProgress("Scanning project files for references…", 10, true);

            // Step 1: Collect all references from source files
            Set<String> allReferences = collectAllReferences(ctx.getAppContext(), scId, ctx);

            ctx.reportProgress("Analyzing resources…", 60, true);

            // Step 2: Find resource files
            JsonObject result = new JsonObject();
            JsonArray unusedList = new JsonArray();
            int totalUnused = 0;

            String basePath = new File(android.os.Environment.getExternalStorageDirectory(),
                    ".sketchware/mysc/" + scId + "/app/src/main/res").getAbsolutePath();

            if (scanTypes.contains("drawables")) {
                File drawDir = new File(basePath, "drawable");
                JsonArray items = scanDir(drawDir, allReferences, ".png", ".jpg", ".jpeg", ".xml", ".webp");
                if (items.size() > 0) { result.add("drawables", items); totalUnused += items.size(); }
            }
            if (scanTypes.contains("raw")) {
                File rawDir = new File(basePath, "raw");
                JsonArray items = scanDir(rawDir, allReferences);
                if (items.size() > 0) { result.add("raw", items); totalUnused += items.size(); }
            }
            if (scanTypes.contains("fonts")) {
                File fontDir = new File(new File(android.os.Environment.getExternalStorageDirectory(),
                        ".sketchware/mysc/" + scId + "/app/src/main/assets"), "fonts");
                JsonArray items = scanDir(fontDir, allReferences, ".ttf", ".otf");
                if (items.size() > 0) { result.add("fonts", items); totalUnused += items.size(); }
            }

            // Strings and colors are in the data files — scan those
            if (scanTypes.contains("strings") || scanTypes.contains("colors")) {
                try {
                    String strRaw = pro.sketchware.util.SketchwareFileDecryptor.decryptFile(scId, "resource");
                    if (strRaw != null) {
                        scanDataResources(strRaw, allReferences, scanTypes, result);
                    }
                } catch (Exception ignored) {}
            }

            ctx.reportProgress("Scan complete", 100, false);

            if (totalUnused == 0) {
                return ok("✅ No unused resources found. The project is clean.");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("⚠ Found ").append(totalUnused).append(" unused resources:\n\n");
            for (String key : new String[]{"drawables", "raw", "fonts", "strings_unused", "colors_unused"}) {
                if (result.has(key)) {
                    sb.append("📁 ").append(key.toUpperCase()).append(":\n");
                    for (com.google.gson.JsonElement el : result.getAsJsonArray(key)) {
                        sb.append("   • ").append(el.getAsString()).append("\n");
                    }
                    sb.append("\n");
                }
            }
            sb.append("To delete these, call delete_unused_resources with the confirmed list.\n");
            sb.append("⚠ IMPORTANT: Always confirm with user before deleting.");
            result.addProperty("total_unused", totalUnused);
            result.addProperty("summary", sb.toString());
            return ok(sb.toString() + "\n\n[RAW JSON for delete_unused_resources]\n" + result.toString());
        }

        /** Scans a directory for files NOT referenced in the project. */
        private JsonArray scanDir(File dir, Set<String> refs, String... extensions) {
            JsonArray unused = new JsonArray();
            if (dir == null || !dir.exists()) return unused;
            File[] files = dir.listFiles();
            if (files == null) return unused;
            for (File f : files) {
                if (!f.isFile()) continue;
                if (extensions.length > 0) {
                    boolean matchExt = false;
                    for (String ext : extensions) if (f.getName().endsWith(ext)) { matchExt = true; break; }
                    if (!matchExt) continue;
                }
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                String baseName = dot >= 0 ? name.substring(0, dot) : name;
                // Check if baseName is referenced anywhere
                boolean used = refs.contains(baseName) || refs.contains(name)
                        || refs.contains("@drawable/" + baseName)
                        || refs.contains("@raw/" + baseName)
                        || refs.contains("@font/" + baseName);
                if (!used) {
                    unused.add(f.getAbsolutePath());
                }
            }
            return unused;
        }

        /** Scans resource data file for unused string/color resources. */
        private void scanDataResources(String raw, Set<String> refs, Set<String> scanTypes,
                                       JsonObject result) {
            try {
                com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(raw.trim()).getAsJsonArray();
                JsonArray unusedStrings = new JsonArray();
                JsonArray unusedColors = new JsonArray();
                for (com.google.gson.JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject obj = el.getAsJsonObject();
                    String resName = obj.has("resName") ? obj.get("resName").getAsString() : null;
                    String resType = obj.has("resType") ? obj.get("resType").getAsString() : null;
                    if (resName == null) continue;
                    boolean used = refs.contains(resName) || refs.contains("@string/" + resName)
                            || refs.contains("@color/" + resName) || refs.contains(resName);
                    if (!used) {
                        if ("string".equals(resType) && scanTypes.contains("strings"))
                            unusedStrings.add(resName);
                        else if ("color".equals(resType) && scanTypes.contains("colors"))
                            unusedColors.add(resName);
                    }
                }
                if (unusedStrings.size() > 0) result.add("strings_unused", unusedStrings);
                if (unusedColors.size() > 0) result.add("colors_unused", unusedColors);
            } catch (Exception ignored) {}
        }

        /** Collects all resource references from Java, XML, and data files. */
        private Set<String> collectAllReferences(Context appCtx, String scId, ToolContext ctx) {
            Set<String> refs = new HashSet<>();
            String mysc = new File(android.os.Environment.getExternalStorageDirectory(),
                    ".sketchware/mysc/" + scId).getAbsolutePath();
            // Scan all Java + XML files
            scanFileTreeForRefs(new File(mysc, "app/src/main/java"), refs);
            scanFileTreeForRefs(new File(mysc, "app/src/main/res"), refs);
            // Also scan Sketchware data files (blocks, events reference resources)
            try {
                String[] dataFiles = {"logic", "resource", "view", "file"};
                for (String df : dataFiles) {
                    try {
                        String raw = pro.sketchware.util.SketchwareFileDecryptor.decryptFile(scId, df);
                        if (raw != null) extractRefsFromText(raw, refs);
                    } catch (Exception ignored2) {}
                }
            } catch (Exception ignored) {}
            return refs;
        }

        private void scanFileTreeForRefs(File dir, Set<String> refs) {
            if (dir == null || !dir.exists()) return;
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                if (f.isDirectory()) { scanFileTreeForRefs(f, refs); continue; }
                try { extractRefsFromText(readFilePlain(f), refs); } catch (Exception ignored) {}
            }
        }

        private void extractRefsFromText(String text, Set<String> refs) {
            if (text == null || text.isEmpty()) return;
            // Match @drawable/name, @string/name, @color/name, @raw/name, R.drawable.name etc.
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "@(?:drawable|string|color|raw|font)/([A-Za-z0-9_]+)|" +
                "R\\.(?:drawable|string|color|raw|font)\\.([A-Za-z0-9_]+)|" +
                "\"([A-Za-z0-9_]{3,60})\"" // bare string references in data files
            ).matcher(text);
            while (m.find()) {
                for (int i = 1; i <= 3; i++) {
                    if (m.group(i) != null) refs.add(m.group(i));
                }
            }
        }

        private String readFilePlain(File f) throws Exception {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                char[] buf = new char[8192]; int n;
                while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }

    // ── Tool 2: delete_unused_resources ──────────────────────────────────────

    public static class DeleteUnusedResourcesTool implements AgentTool {

        @Override public String getName() { return "delete_unused_resources"; }

        @Override
        public String getDescription() {
            return "Deletes a list of confirmed unused resource files from the project. " +
                    "ONLY call this after scan_unused_resources AND explicit user confirmation. " +
                    "Pass the exact file paths returned by scan_unused_resources. " +
                    "Returns a summary of deleted files and any errors.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id", "string", "Project ID");
            JsonObject paths = new JsonObject(); paths.addProperty("type", "array");
            JsonObject items = new JsonObject(); items.addProperty("type", "string");
            paths.add("items", items);
            paths.addProperty("description", "Absolute file paths to delete (from scan_unused_resources output)");
            p.add("file_paths", paths);
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("file_paths"); s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = str(args, "sc_id");
            if (scId == null) return err("sc_id is required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied");
            if (!args.has("file_paths") || !args.get("file_paths").isJsonArray())
                return err("file_paths array is required");

            JsonArray paths = args.getAsJsonArray("file_paths");
            if (paths.size() == 0) return ok("No files to delete.");

            // Safety: only allow deletion within this project's directory
            String safeBase = new File(android.os.Environment.getExternalStorageDirectory(),
                    ".sketchware/mysc/" + scId).getAbsolutePath();

            List<String> deleted = new ArrayList<>();
            List<String> errors  = new ArrayList<>();

            for (com.google.gson.JsonElement el : paths) {
                String path = el.getAsString();
                if (!path.startsWith(safeBase)) {
                    errors.add("SKIPPED (outside project): " + path);
                    continue;
                }
                File f = new File(path);
                if (!f.exists()) { errors.add("NOT FOUND: " + path); continue; }
                if (f.delete()) {
                    deleted.add(f.getName());
                } else {
                    errors.add("DELETE FAILED: " + path);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("✅ Deleted ").append(deleted.size()).append(" resource(s):\n");
            for (String d : deleted) sb.append("   🗑 ").append(d).append("\n");
            if (!errors.isEmpty()) {
                sb.append("\n⚠ Errors (").append(errors.size()).append("):\n");
                for (String e : errors) sb.append("   • ").append(e).append("\n");
            }
            return ok(sb.toString());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString().trim() : null;
    }

    private static void addP(JsonObject p, String k, String t, String d) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", t);
        obj.addProperty("description", d);
        p.add(k, obj);
    }

    private static ToolResult ok(String s)  { return ToolResult.success(null, s); }
    private static ToolResult err(String s) { return ToolResult.failure(null, s); }
}
