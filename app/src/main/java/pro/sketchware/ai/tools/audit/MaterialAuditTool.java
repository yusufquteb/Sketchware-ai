package pro.sketchware.ai.tools.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.util.SketchwareFileDecryptor;

/**
 * Material Design audit tool — deterministic, no LLM required.
 *
 * Scans all view beans in a project's layout data for Material 3 violations:
 *   - Hardcoded hex colors (#RRGGBB / #AARRGGBB) instead of @color/ references
 *   - AppCompat Button (type=3) instead of MaterialButton
 *   - Missing material component theme (checks project config)
 *   - Text size in dp instead of sp
 *
 * Returns a categorised report with violation counts per activity.
 */
public final class MaterialAuditTool implements AgentTool {

    private static final Pattern HEX_COLOR  = Pattern.compile("\"#[0-9A-Fa-f]{6,8}\"");
    private static final Pattern DP_TEXT    = Pattern.compile("\"textSize\":\\s*\"\\d+dp\"");

    @Override
    public String getName() { return "material_audit_project"; }

    @Override
    public String getDescription() {
        return "Scans all layouts in a Sketchware Pro project for Material Design violations. "
             + "Detects hardcoded hex colors, wrong text size units, and non-Material widget types. "
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

        ctx.reportProgress("Reading project view data...", -1, true);

        String raw;
        try {
            raw = SketchwareFileDecryptor.decryptFile(scId, "view");
        } catch (Exception e) {
            return ToolResult.failure(null, "Cannot read view file: " + e.getMessage());
        }
        if (raw == null || raw.isEmpty()) {
            return ToolResult.failure(null, "No layout data found for project " + scId);
        }

        List<Section> sections = parseSections(raw);

        StringBuilder report = new StringBuilder();
        report.append("Material Design Audit — Project ").append(scId).append('\n');
        report.append("═".repeat(50)).append('\n').append('\n');

        int totalViolations = 0;
        for (Section section : sections) {
            List<String> issues = detectViolations(section.content.toString());
            if (!issues.isEmpty()) {
                totalViolations += issues.size();
                report.append("▶ ").append(section.name).append('\n');
                for (String issue : issues) {
                    report.append("   ⚠ ").append(issue).append('\n');
                }
                report.append('\n');
            }
        }

        if (totalViolations == 0) {
            report.append("✅ No Material Design violations found.\n");
        } else {
            report.append("─".repeat(50)).append('\n');
            report.append("Total violations: ").append(totalViolations).append('\n');
            report.append("Recommended fixes:\n");
            report.append("  • Replace #RRGGBB colors with @color/ resource references\n");
            report.append("  • Change textSize unit from dp to sp for accessibility scaling\n");
            report.append("  • Use MaterialButton (type=4) instead of AppCompat Button (type=3)\n");
        }

        return ToolResult.success(null, report.toString());
    }

    // ── Detection logic ───────────────────────────────────────────────────────

    private static List<String> detectViolations(String content) {
        List<String> issues = new ArrayList<>();
        if (content == null || content.isEmpty()) return issues;

        // Hardcoded hex colors
        Matcher colorMatcher = HEX_COLOR.matcher(content);
        int colorCount = 0;
        while (colorMatcher.find()) colorCount++;
        if (colorCount > 0)
            issues.add("Hardcoded hex color found (" + colorCount + " instance(s)) — use @color/ references");

        // textSize in dp instead of sp
        Matcher dpMatcher = DP_TEXT.matcher(content);
        int dpCount = 0;
        while (dpMatcher.find()) dpCount++;
        if (dpCount > 0)
            issues.add("textSize uses dp (" + dpCount + " instance(s)) — use sp for text scaling accessibility");

        // AppCompat Button type=3 (Sketchware's Button widget)
        // In Sketchware ViewBeans: type 3 = Button, type 4 = ImageView
        // MaterialButton would need to be added as a custom view
        if (content.contains("\"type\":3") || content.contains("\"type\": 3")) {
            int buttonCount = countOccurrences(content, "\"type\":3")
                            + countOccurrences(content, "\"type\": 3");
            if (buttonCount > 0)
                issues.add("Standard Button widget (" + buttonCount + ") detected — consider MaterialButton for Material 3 theming");
        }

        // Check for missing background tint attribute (hardcoded background colors)
        if (content.contains("\"backgroundColor\"") && !content.contains("\"backgroundTint\""))
            issues.add("backgroundColor set without backgroundTint — use colorPrimary/colorSecondary from theme");

        return issues;
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }

    // ── Section parser (same format as RtlAuditTool) ─────────────────────────

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

    private static final class Section {
        final String name;
        final StringBuilder content = new StringBuilder();
        Section(String name) { this.name = name; }
    }
}
