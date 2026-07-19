package pro.sketchware.ai.tools.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;

import com.besome.sketch.beans.ProjectFileBean;

import a.a.a.jC;

import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.util.SketchwareFileDecryptor;
import pro.sketchware.util.SketchwareFileEncryptor;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.FilePathUtil;

/**
 * ActivityContentSeedingTools — Phase 3, item 1 ("create a project from
 * scratch, not just from a template").
 *
 * WHAT WAS ACTUALLY WRONG WITH THE AUDIT REPORT'S PREMISE (verified, not
 * assumed): the report framed this as "only CreateFromTemplateTool exists, no
 * from-scratch path." That is inaccurate. Both {@code create_project}
 * (ProjectTools.CreateProjectTool) and {@code create_activity}
 * (ActivityTools.CreateActivityTool) already exist, are already registered,
 * and already perform real from-scratch creation — {@code create_activity} in
 * particular already does the correct {@link ProjectFileBean} registration
 * via {@code jC.b(scId).a(bean)} → {@code .j()} → {@code .l()}, the same
 * sequence the app's own (non-AI) ActivityManagerActivity.cloneActivity()
 * uses.
 *
 * The REAL, narrower gap (confirmed by reading create_activity's full
 * execute() body): it registers the bean but writes NOTHING to the encrypted
 * "view"/"logic" project files and creates NO layout XML resource file. A
 * freshly create_activity'd activity therefore has no "@ActivityName.java.xml"
 * section for the design editor and no "@ActivityName.java_onCreate" section
 * for the logic editor — both BlockLogicReader's own class-level documentation
 * and ProjectTools.seedDefaultResources() (which create_project uses to seed
 * MainActivity's main.xml, but ONLY the layout resource — not view/logic
 * sections either) confirm this section-header format and confirm no other
 * code path seeds it automatically.
 *
 * This tool is the missing third step of the real from-scratch sequence:
 *   1. create_project        (existing — project + MainActivity's main.xml)
 *   2. create_activity        (existing — registers a NEW activity's ProjectFileBean)
 *   3. seed_blank_activity_content   (NEW, this file — fills in the empty
 *      view/logic sections + layout XML so the activity is actually editable)
 *
 * A plan step sequence for "build me an app from scratch" should call
 * create_project once, then create_activity + seed_blank_activity_content as
 * a pair for every activity — including MainActivity itself, since
 * create_project seeds its layout XML but not its view/logic sections.
 */
public final class ActivityContentSeedingTools {

    private ActivityContentSeedingTools() {}

    private static ToolResult ok(String output) { return ToolResult.success(null, output); }
    private static ToolResult err(String msg) { return ToolResult.failure(null, msg); }

    public static class SeedBlankActivityContentTool implements AgentTool {

        @Override public String getName() { return "seed_blank_activity_content"; }

        @Override public RiskLevel getRiskLevel() { return RiskLevel.MEDIUM; }

        @Override public boolean requiresProject() { return true; }

        @Override public String getDescription() {
            return "Fills in the blank view/logic sections and layout XML resource for an activity "
                 + "that create_project or create_activity registered but left empty. Call this "
                 + "immediately after create_activity for any NEW activity (including MainActivity, "
                 + "whose layout create_project seeds but whose view/logic sections it does NOT). "
                 + "Idempotent-safe: if a section already exists for this activity, it is left "
                 + "untouched and reported, not overwritten. Required: sc_id, activity_name (as "
                 + "registered — matches the ProjectFileBean fileName, e.g. 'MainActivity' or "
                 + "whatever create_activity returned as java_name minus '.java'). Optional: "
                 + "layout_root ('LinearLayout' default, 'RelativeLayout', or 'ScrollView').";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addProp(props, "sc_id", "string", "Project ID");
            addProp(props, "activity_name", "string", "Registered activity name, e.g. 'MainActivity' (matches ProjectFileBean.fileName)");
            addProp(props, "layout_root", "string", "Root layout tag: LinearLayout (default), RelativeLayout, or ScrollView");
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            if (!args.has("sc_id") || !args.has("activity_name")) {
                return err("sc_id and activity_name are required");
            }
            String scId = args.get("sc_id").getAsString().trim();
            String activityName = args.get("activity_name").getAsString().trim();
            String layoutRoot = args.has("layout_root") && !args.get("layout_root").isJsonNull()
                    ? args.get("layout_root").getAsString().trim() : "LinearLayout";
            if (!layoutRoot.equals("LinearLayout") && !layoutRoot.equals("RelativeLayout")
                    && !layoutRoot.equals("ScrollView")) {
                return err("layout_root must be LinearLayout, RelativeLayout, or ScrollView");
            }
            if (!ctx.isProjectAllowed(scId)) return err("Access denied: project " + scId);

            String javaFileName = activityName.endsWith(".java") ? activityName : activityName + ".java";
            String baseName = javaFileName.substring(0, javaFileName.length() - ".java".length());

            try {
                // ── Confirm the activity is actually registered — this tool seeds
                //    content for an EXISTING bean, it does not create one itself
                //    (that stays create_activity's job, per this phase's rule 3:
                //    follow the existing tool pattern rather than merge responsibilities). ──
                ArrayList<ProjectFileBean> beans = jC.b(scId).b();
                boolean registered = false;
                if (beans != null) {
                    for (ProjectFileBean b : beans) {
                        if (javaFileName.equals(b.fileName)) { registered = true; break; }
                    }
                }
                if (!registered) {
                    return err("No registered activity named '" + baseName + "' found in project " + scId
                            + ". Call create_activity first.");
                }

                StringBuilder report = new StringBuilder();

                // ── View section ──────────────────────────────────────────────
                String viewSectionHeader = "@" + javaFileName + ".xml";
                String viewContent = SketchwareFileDecryptor.decryptFile(scId, "view");
                if (viewContent == null) viewContent = "";
                if (viewContent.contains(viewSectionHeader)) {
                    report.append("view section already exists, left untouched; ");
                } else {
                    ctx.reportProgress("Seeding blank view section…", -1, true);
                    String newViewContent = (viewContent.trim().isEmpty() ? "" : viewContent.trim() + "\n")
                            + viewSectionHeader + "\n";
                    SketchwareFileEncryptor.encryptAndSaveFile(scId, "view", newViewContent);
                    report.append("view section created; ");
                }

                // ── Logic section (one empty onCreate event, per BlockLogicReader's
                //    documented "@ActivityName.java_eventName" header format) ──────
                String logicSectionHeader = "@" + javaFileName + "_onCreate";
                String logicContent = SketchwareFileDecryptor.decryptFile(scId, "logic");
                if (logicContent == null) logicContent = "";
                if (logicContent.contains(logicSectionHeader)) {
                    report.append("logic section already exists, left untouched; ");
                } else {
                    ctx.reportProgress("Seeding blank logic section…", -1, true);
                    String newLogicContent = (logicContent.trim().isEmpty() ? "" : logicContent.trim() + "\n")
                            + logicSectionHeader + "\n";
                    SketchwareFileEncryptor.encryptAndSaveFile(scId, "logic", newLogicContent);
                    report.append("logic (onCreate) section created; ");
                }

                // ── Layout XML resource — same format ProjectTools.seedDefaultResources()
                //    uses for MainActivity's main.xml. ──────────────────────────────
                FilePathUtil filePathUtil = new FilePathUtil();
                File layoutFile = new File(filePathUtil.getPathResource(scId)
                        + File.separator + "layout" + File.separator + baseName + ".xml");
                if (layoutFile.exists()) {
                    report.append("layout XML already exists, left untouched.");
                } else {
                    ctx.reportProgress("Writing layout XML…", -1, true);
                    String layoutXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<" + layoutRoot + " xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"match_parent\"\n"
                            + "    android:layout_height=\"match_parent\">\n\n"
                            + "</" + layoutRoot + ">\n";
                    FileUtil.writeFile(layoutFile.getAbsolutePath(), layoutXml);
                    report.append("layout XML created.");
                }

                return ok("Content seeded for activity '" + baseName + "' in project " + scId + ": "
                        + report + " NOT YET TESTED by opening the design editor on a real device — "
                        + "verify the activity renders correctly in the design canvas before relying on this.");
            } catch (Exception e) {
                return err("Failed to seed activity content: " + e.getMessage());
            }
        }

        private static void addProp(JsonObject props, String key, String type, String desc) {
            JsonObject p = new JsonObject();
            p.addProperty("type", type);
            p.addProperty("description", desc);
            props.add(key, p);
        }
    }
}
