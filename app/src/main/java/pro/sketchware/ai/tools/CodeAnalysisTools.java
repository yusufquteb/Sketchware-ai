package pro.sketchware.ai.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.util.SketchwareFileDecryptor;

/**
 * CodeAnalysisTools — أدوات تحليل جودة الكود وفحص المشاكل البرمجية.
 *
 * الأدوات المتاحة:
 *   analyze_code         — تحليل ثابت لملف Java (imports غير مستخدمة، أخطاء setText، إلخ)
 *   review_source_code   — مراجعة أفضل الممارسات (تسريب الذاكرة، APIs مهجورة، إلخ)
 *   validate_rtl_layout  — فحص توافق RTL في تخطيطات الواجهة
 *
 * تم نقل هذه الأدوات من Phase3Tools لتجميع الأدوات المتشابهة في ملف واحد.
 */
public final class CodeAnalysisTools {

    private CodeAnalysisTools() {}

    // ── مساعدات مشتركة ────────────────────────────────────────────────────────

    static ToolResult ok(String output)  { return ToolResult.success(null, output); }
    static ToolResult err(String msg)    { return ToolResult.failure(null, msg); }

    static String req(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        return args.get(key).getAsString().trim();
    }

    static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            char[] buf = new char[4096]; int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    static void writeFile(File f, String content) throws IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
    }

    static void addP(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", desc);
        props.add(key, p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. ANALYZE CODE TOOL
    // ════════════════════════════════════════════════════════════════════════

    public static class AnalyzeCodeTool implements AgentTool {
        @Override public String getName() { return "analyze_code"; }

        @Override public String getDescription() {
            return "Performs static analysis on a Java source file in a Sketchware Pro project. "
                 + "Detects unused imports, setText(int) bugs, empty catch blocks, "
                 + "Thread.sleep without try-catch, and suggests best practices. "
                 + "Use before build_project to catch errors early.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",     "string", "Project ID");
            addP(p, "file_path", "string", "Path, e.g. 'java/com/example/MainActivity.java'");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("file_path");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = req(args, "sc_id");
            String path = req(args, "file_path");
            if (scId == null || path == null) return err("sc_id and file_path are required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);
            ctx.reportProgress("Analysing source code...", -1, true);

            String relative = path;
            if (relative.startsWith("java/")) relative = relative.substring(5);
            else if (relative.startsWith("app/src/main/java/")) relative = relative.substring(18);
            File file = new File(ctx.getProjectJavaDir(scId), relative);
            if (!file.exists()) file = new File(ctx.getProjectDataDir(scId), path);
            if (!file.exists()) return err("File not found: " + path);

            String source;
            try { source = readFile(file); }
            catch (IOException e) { return err("Could not read file: " + e.getMessage()); }

            StringBuilder report = new StringBuilder();
            report.append("Code Analysis: ").append(file.getName()).append("\n");
            report.append("=".repeat(50)).append("\n\n");

            List<String> issues = new ArrayList<>();
            List<String> suggestions = new ArrayList<>();
            String[] lines = source.split("\n");
            int lineNum = 0;

            for (String line : lines) {
                lineNum++;
                String t = line.trim();

                if (t.startsWith("import ") && !t.startsWith("import static")) {
                    String cls = t.replace("import ", "").replace(";", "").trim();
                    String simple = cls.contains(".") ? cls.substring(cls.lastIndexOf('.') + 1) : cls;
                    if (!simple.isEmpty() && !simple.equals("*")) {
                        long uses = java.util.Arrays.stream(lines)
                                .filter(l -> !l.trim().startsWith("import"))
                                .filter(l -> l.contains(simple)).count();
                        if (uses == 0)
                            issues.add("L" + lineNum + ": Possibly unused import: " + cls);
                    }
                }
                if (t.matches(".*\\.setText\\(\\d+\\).*"))
                    issues.add("L" + lineNum + ": setText(int) sets resource ID. Use setText(String.valueOf(n)).");
                if ((t.equals("} catch (Exception e) {") || t.equals("catch (Exception e) {"))
                        && lineNum < lines.length && lines[lineNum].trim().equals("}"))
                    issues.add("L" + lineNum + ": Empty catch block silently swallows exceptions.");
                if (t.contains("Thread.sleep(")) {
                    boolean hasTry = false;
                    for (int i = Math.max(0, lineNum - 3); i < lineNum - 1; i++) {
                        if (lines[i].contains("try {") || lines[i].contains("try{")) { hasTry = true; break; }
                    }
                    if (!hasTry) issues.add("L" + lineNum + ": Thread.sleep() needs try-catch (InterruptedException).");
                }
                if (t.contains("AsyncTask"))
                    suggestions.add("L" + lineNum + ": AsyncTask is deprecated. Use ExecutorService + Handler.");
                if (t.contains("System.out.println"))
                    suggestions.add("L" + lineNum + ": Replace System.out.println with Log.d(TAG, ...).");
            }

            if (issues.isEmpty() && suggestions.isEmpty()) {
                report.append("No obvious issues detected. Code looks good.\n");
            } else {
                if (!issues.isEmpty()) {
                    report.append("ISSUES (").append(issues.size()).append("):\n");
                    for (String i : issues) report.append("  Warning: ").append(i).append("\n");
                    report.append("\n");
                }
                if (!suggestions.isEmpty()) {
                    report.append("SUGGESTIONS (").append(suggestions.size()).append("):\n");
                    for (String s : suggestions) report.append("  Tip: ").append(s).append("\n");
                }
            }
            report.append("\nTotal lines analysed: ").append(lineNum);
            return ok(report.toString());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. REVIEW SOURCE CODE TOOL
    // ════════════════════════════════════════════════════════════════════════

    public static class ReviewSourceCodeTool implements AgentTool {
        @Override public String getName() { return "review_source_code"; }

        @Override public String getDescription() {
            return "Reviews Java source code for Android best practice issues: "
                 + "memory leaks (static Context, Handler without Looper), deprecated APIs, "
                 + "missing error handling, logging issues. "
                 + "Pass the source code as a string. Returns findings with fix suggestions.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",         "string", "Project ID");
            addP(p, "source_code",   "string", "Full Java source code to review");
            addP(p, "activity_name", "string", "Activity name for context (optional)");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("source_code");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = req(args, "sc_id");
            String code = req(args, "source_code");
            String act  = req(args, "activity_name");
            if (scId == null || code == null) return err("sc_id and source_code are required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);
            ctx.reportProgress("Reviewing source code...", -1, true);

            List<String> findings = new ArrayList<>();
            if (code.contains("new Handler()") && !code.contains("Looper.getMainLooper()"))
                findings.add("MEMORY LEAK: Use new Handler(Looper.getMainLooper()) in API 30+.");
            if (code.contains("AsyncTask"))
                findings.add("DEPRECATED: AsyncTask removed in API 30. Use ExecutorService + Handler.");
            if (code.contains("static Context") || code.contains("static Activity"))
                findings.add("MEMORY LEAK: Static Context/Activity reference. Use WeakReference<Activity>.");
            if (code.contains("e.printStackTrace()"))
                findings.add("BAD PRACTICE: Replace e.printStackTrace() with Log.e(TAG, message, e).");
            if (code.contains("System.out.println"))
                findings.add("BAD PRACTICE: Replace System.out.println with Log.d(TAG, ...).");
            if (code.contains("getApplicationContext()") && code.contains("AlertDialog"))
                findings.add("CRASH RISK: Do not use getApplicationContext() for dialogs. Use Activity context.");
            if (!code.contains("TAG") && code.contains("Log."))
                findings.add("STYLE: Add: private static final String TAG = \""
                        + (act != null ? act : "MyActivity") + "\";");

            StringBuilder sb = new StringBuilder("Code Review");
            if (act != null) sb.append(": ").append(act);
            sb.append("\n").append("=".repeat(40)).append("\n\n");

            if (findings.isEmpty()) {
                sb.append("No major issues found. Code looks good for Sketchware Pro.\n");
                sb.append("\nGeneral tips:\n");
                sb.append("  - Add null checks before using intent extras\n");
                sb.append("  - Use try-with-resources for streams\n");
            } else {
                sb.append("FINDINGS (").append(findings.size()).append("):\n\n");
                for (int i = 0; i < findings.size(); i++)
                    sb.append(i + 1).append(". ").append(findings.get(i)).append("\n\n");
            }
            sb.append("\nTo apply: use write_file with corrected source, then build_project.");
            return ok(sb.toString());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. VALIDATE RTL LAYOUT TOOL
    // ════════════════════════════════════════════════════════════════════════

    public static class ValidateRtlLayoutTool implements AgentTool {
        @Override public String getName() { return "validate_rtl_layout"; }

        @Override public String getDescription() {
            return "Validates a Sketchware Pro activity layout for RTL compatibility. "
                 + "Detects hardcoded left/right margins (use Start/End), "
                 + "missing layoutDirection, and gravity issues for Arabic/Hebrew/Urdu apps.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject s = new JsonObject(); s.addProperty("type", "object");
            JsonObject p = new JsonObject();
            addP(p, "sc_id",         "string", "Project ID");
            addP(p, "activity_name", "string", "Activity name without .java");
            s.add("properties", p);
            JsonArray r = new JsonArray(); r.add("sc_id"); r.add("activity_name");
            s.add("required", r);
            return s;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = req(args, "sc_id");
            String actName = req(args, "activity_name");
            if (scId == null || actName == null) return err("sc_id and activity_name are required");
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);
            String xmlName = actName.endsWith(".xml") ? actName.replace(".xml","") : actName;
            ctx.reportProgress("Validating RTL compatibility for " + xmlName + "...", -1, true);

            // Read from jC in-memory first (most accurate — reflects live canvas state)
            String raw = null;
            try {
                java.util.ArrayList<com.besome.sketch.beans.ViewBean> liveBeans =
                        a.a.a.jC.a(scId).d(xmlName + ".xml");
                if (liveBeans != null && !liveBeans.isEmpty()) {
                    StringBuilder sb2 = new StringBuilder();
                    for (com.besome.sketch.beans.ViewBean b : liveBeans) {
                        if (b.layout != null) {
                            sb2.append("\"marginLeft\":").append(b.layout.marginLeft)
                               .append(",\"marginRight\":").append(b.layout.marginRight)
                               .append(",\"paddingLeft\":").append(b.layout.paddingLeft)
                               .append(",\"paddingRight\":").append(b.layout.paddingRight)
                               .append(",\"gravity\":").append(b.layout.gravity)
                               .append(",");
                        }
                    }
                    raw = sb2.toString();
                }
            } catch (Exception ignored) {}

            // Fallback: read encrypted disk file
            if (raw == null || raw.isEmpty()) {
                try {
                    raw = SketchwareFileDecryptor.decryptFile(scId, "view");
                } catch (Exception e) {
                    raw = null;
                }
            }
            if (raw == null || raw.isEmpty()) return err("Cannot read layout for project " + scId);

            List<String> issues = new ArrayList<>();
            if (raw.contains("\"marginLeft\"") && !raw.contains("\"marginStart\""))
                issues.add("marginLeft without marginStart — use marginStart for RTL");
            if (raw.contains("\"marginRight\"") && !raw.contains("\"marginEnd\""))
                issues.add("marginRight without marginEnd — use marginEnd for RTL");
            if (raw.contains("\"paddingLeft\"") && !raw.contains("\"paddingStart\""))
                issues.add("paddingLeft without paddingStart — use paddingStart for RTL");
            if (raw.contains("\"gravity\":3") || raw.contains("\"gravity\": 3"))
                issues.add("gravity=LEFT (3) — use END (5) for RTL-safe alignment");
            if (!raw.contains("\"layoutDirection\"") && raw.length() > 100)
                issues.add("No layoutDirection — add layoutDirection=locale on root view");

            StringBuilder sb = new StringBuilder("RTL Validation: " + actName + "\n" + "=".repeat(40) + "\n\n");
            if (issues.isEmpty()) {
                sb.append("No RTL issues detected.\n");
                sb.append("Tip: Ensure AndroidManifest has android:supportsRtl=\"true\".\n");
            } else {
                sb.append("RTL ISSUES (").append(issues.size()).append("):\n");
                for (String i : issues) sb.append("  Warning: ").append(i).append("\n");
                sb.append("\nFixes: use modify_view to update marginStart/End instead of Left/Right.\n");
            }
            return ok(sb.toString());
        }
    }
}
