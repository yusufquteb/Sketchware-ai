package pro.sketchware.ai.tools.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.util.SketchwareFileDecryptor;

/**
 * Full-project RTL audit — deterministic, no LLM call required.
 *
 * Reads the project's encrypted view file (all @section blocks) and checks every
 * view bean for directional attributes that break RTL layouts:
 *   - marginLeft / marginRight  → must have marginStart / marginEnd counterpart
 *   - paddingLeft / paddingRight → must have paddingStart / paddingEnd counterpart
 *   - gravity=LEFT (3) or gravity=RIGHT (5) hardcoded values
 *   - layoutDirection absent on any section (likely missing on root views)
 *   - AndroidManifest missing android:supportsRtl="true"
 */
public final class RtlAuditTool implements AgentTool {

    @Override
    public String getName() { return "rtl_audit_project"; }

    @Override
    public String getDescription() {
        return "Scans all layouts in a Sketchware Pro project for RTL compatibility issues. "
             + "Detects hardcoded left/right margins, padding, gravity, and missing layoutDirection. "
             + "Returns a full per-activity report. No LLM needed — purely deterministic.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject scIdProp = new JsonObject();
        scIdProp.addProperty("type", "string");
        scIdProp.addProperty("description", "Sketchware project ID to audit");
        props.add("sc_id", scIdProp);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("sc_id");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject args, ToolContext ctx) {
        if (!args.has("sc_id")) return ToolResult.failure(null, "sc_id is required");
        String scId = args.get("sc_id").getAsString().trim();
        if (!ctx.isProjectAllowed(scId)) return ToolResult.failure(null, "Access denied: project " + scId);

        ctx.reportProgress("Reading project layouts...", -1, true);

        String raw;
        try {
            raw = SketchwareFileDecryptor.decryptFile(scId, "view");
        } catch (Exception e) {
            return ToolResult.failure(null, "Cannot read view file: " + e.getMessage());
        }
        if (raw == null || raw.isEmpty()) {
            return ToolResult.failure(null, "No layout data found for project " + scId);
        }

        // Parse @section blocks into named segments
        List<Section> sections = parseSections(raw);

        StringBuilder report = new StringBuilder();
        report.append("RTL Audit Report — Project ").append(scId).append('\n');
        report.append("═".repeat(50)).append('\n').append('\n');

        int totalIssues = 0;
        for (Section section : sections) {
            List<String> issues = detectIssues(section.content);
            if (!issues.isEmpty()) {
                totalIssues += issues.size();
                report.append("▶ ").append(section.name).append('\n');
                for (String issue : issues) {
                    report.append("   ⚠ ").append(issue).append('\n');
                }
                report.append('\n');
            }
        }

        // Manifest check (best-effort — Sketchware regenerates manifest on build)
        if (!manifestSupportsRtl(ctx, scId)) {
            totalIssues++;
            report.append("▶ AndroidManifest.xml\n");
            report.append("   ⚠ Missing android:supportsRtl=\"true\" in <application> tag\n\n");
        }

        if (totalIssues == 0) {
            report.append("✅ No RTL issues found. Project appears RTL-compatible.\n");
        } else {
            report.append("─".repeat(50)).append('\n');
            report.append("Total issues: ").append(totalIssues).append('\n');
            report.append("Fix: use modify_view to replace marginLeft→marginStart, ");
            report.append("paddingLeft→paddingStart, gravity=LEFT(3)→gravity=START(8388611).\n");
        }

        return ToolResult.success(null, report.toString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<Section> parseSections(String raw) {
        List<Section> result = new ArrayList<>();
        Section current = null;
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("@")) {
                if (current != null) result.add(current);
                current = new Section(t.substring(1).trim());
            } else if (current != null && !t.isEmpty()) {
                current.content.append(t).append('\n');
            }
        }
        if (current != null) result.add(current);
        return result;
    }

    private static List<String> detectIssues(StringBuilder contentBuilder) {
        List<String> issues = new ArrayList<>();
        String content = contentBuilder.toString();
        if (content.isEmpty()) return issues;

        if (content.contains("\"marginLeft\"") && !content.contains("\"marginStart\""))
            issues.add("marginLeft used without marginStart — replace with marginStart");
        if (content.contains("\"marginRight\"") && !content.contains("\"marginEnd\""))
            issues.add("marginRight used without marginEnd — replace with marginEnd");
        if (content.contains("\"paddingLeft\"") && !content.contains("\"paddingStart\""))
            issues.add("paddingLeft used without paddingStart — replace with paddingStart");
        if (content.contains("\"paddingRight\"") && !content.contains("\"paddingEnd\""))
            issues.add("paddingRight used without paddingEnd — replace with paddingEnd");
        if (content.contains("\"gravity\":3") || content.contains("\"gravity\": 3"))
            issues.add("gravity=LEFT (3) detected — use gravity=START (8388611)");
        if (content.contains("\"gravity\":5") || content.contains("\"gravity\": 5"))
            issues.add("gravity=RIGHT (5) detected — use gravity=END (8388613)");
        if (content.length() > 50 && !content.contains("\"layoutDirection\""))
            issues.add("layoutDirection not set on any view — add layoutDirection=locale on root views");

        return issues;
    }

    private static boolean manifestSupportsRtl(ToolContext ctx, String scId) {
        try {
            File manifestFile = new File(ctx.getProjectDataDir(scId), "AndroidManifest.xml");
            if (!manifestFile.exists()) return true;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(manifestFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString().contains("supportsRtl=\"true\"");
        } catch (Exception e) {
            return true;
        }
    }

    private static final class Section {
        final String name;
        final StringBuilder content = new StringBuilder();
        Section(String name) { this.name = name; }
    }
}
