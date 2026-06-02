package pro.sketchware.ai.tools.snapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import pro.sketchware.ai.engine.snapshot.ProjectSnapshotManager;
import pro.sketchware.ai.engine.snapshot.SnapshotMetadata;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

/**
 * Creates a manual snapshot of a project before making major changes.
 * Risk: LOW — adds a backup, does not modify the project.
 */
public final class CreateSnapshotTool implements AgentTool {

    @Override public String getName() { return "create_snapshot"; }

    @Override
    public String getDescription() {
        return "Creates a snapshot (backup) of a Sketchware project before making changes. "
             + "Snapshots are stored automatically before MEDIUM/CRITICAL operations, "
             + "but you can also create one manually with a descriptive label. "
             + "Use list_snapshots to see existing ones, restore_snapshot to roll back.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject scIdProp = new JsonObject();
        scIdProp.addProperty("type", "string");
        scIdProp.addProperty("description", "Project ID to snapshot");
        props.add("sc_id", scIdProp);

        JsonObject labelProp = new JsonObject();
        labelProp.addProperty("type", "string");
        labelProp.addProperty("description", "Short description of why this snapshot is being created");
        props.add("label", labelProp);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("sc_id");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject args, ToolContext ctx) {
        if (!args.has("sc_id")) return ToolResult.failure(null, "sc_id is required");
        String scId  = args.get("sc_id").getAsString().trim();
        String label = args.has("label") ? args.get("label").getAsString().trim() : "manual snapshot";

        if (!ctx.isProjectAllowed(scId)) return ToolResult.failure(null, "Access denied: project " + scId);

        ctx.reportProgress("Creating snapshot...", -1, true);
        ProjectSnapshotManager manager = new ProjectSnapshotManager(ctx);
        SnapshotMetadata meta = manager.createSnapshot(scId, label, "create_snapshot");

        if (meta == null) {
            return ToolResult.failure(null,
                    "Failed to create snapshot for project " + scId
                    + ". Check storage permissions and available space.");
        }

        return ToolResult.success(null,
                "Snapshot created successfully.\n"
                + "ID:    " + meta.snapshotId + "\n"
                + "Label: " + meta.label + "\n"
                + "Size:  " + meta.sizeBytes + " bytes\n"
                + "Use restore_snapshot with this ID to roll back if needed.");
    }
}
