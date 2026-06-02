package pro.sketchware.ai.tools.snapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.engine.snapshot.ProjectSnapshotManager;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

/**
 * Restores a project to a previously created snapshot.
 * Risk: CRITICAL — overwrites all current project data with snapshot contents.
 *
 * Always requires explicit user approval before execution.
 */
public final class RestoreSnapshotTool implements AgentTool {

    @Override public String getName() { return "restore_snapshot"; }

    @Override
    public String getDescription() {
        return "Restores a Sketchware project to a previously saved snapshot. "
             + "WARNING: This overwrites ALL current project data with the snapshot contents. "
             + "Use list_snapshots to find available snapshot IDs. "
             + "Requires explicit user approval before execution.";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.CRITICAL;
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject scIdProp = new JsonObject();
        scIdProp.addProperty("type", "string");
        scIdProp.addProperty("description", "Project ID to restore");
        props.add("sc_id", scIdProp);

        JsonObject snapIdProp = new JsonObject();
        snapIdProp.addProperty("type", "string");
        snapIdProp.addProperty("description", "Snapshot ID from list_snapshots");
        props.add("snapshot_id", snapIdProp);

        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("sc_id");
        required.add("snapshot_id");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject args, ToolContext ctx) {
        if (!args.has("sc_id"))        return ToolResult.failure(null, "sc_id is required");
        if (!args.has("snapshot_id"))  return ToolResult.failure(null, "snapshot_id is required");

        String scId       = args.get("sc_id").getAsString().trim();
        String snapshotId = args.get("snapshot_id").getAsString().trim();

        if (!ctx.isProjectAllowed(scId)) return ToolResult.failure(null, "Access denied: project " + scId);

        ctx.reportProgress("Restoring snapshot " + snapshotId + "...", -1, true);
        ProjectSnapshotManager manager = new ProjectSnapshotManager(ctx);
        boolean success = manager.restoreSnapshot(scId, snapshotId);

        if (!success) {
            return ToolResult.failure(null,
                    "Failed to restore snapshot '" + snapshotId + "' for project " + scId
                    + ". The snapshot may not exist or storage is unavailable.");
        }

        return ToolResult.success(null,
                "Project " + scId + " restored to snapshot '" + snapshotId + "'.\n"
                + "All project data has been replaced with the snapshot contents.\n"
                + "Reload the project in Sketchware to see the restored state.");
    }
}
