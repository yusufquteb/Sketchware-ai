package pro.sketchware.ai.tools.snapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.ai.engine.snapshot.ProjectSnapshotManager;
import pro.sketchware.ai.engine.snapshot.SnapshotMetadata;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

/**
 * Lists all available snapshots for a project, sorted newest first.
 * Risk: LOW — read-only.
 */
public final class ListSnapshotsTool implements AgentTool {

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    @Override public String getName() { return "list_snapshots"; }

    @Override
    public String getDescription() {
        return "Lists all available snapshots for a Sketchware project, sorted newest first. "
             + "Each snapshot was created before a risky operation. "
             + "Use restore_snapshot to roll back to a previous state.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject scIdProp = new JsonObject();
        scIdProp.addProperty("type", "string");
        scIdProp.addProperty("description", "Project ID to list snapshots for");
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

        ProjectSnapshotManager manager = new ProjectSnapshotManager(ctx);
        List<SnapshotMetadata> snapshots = manager.listSnapshots(scId);

        if (snapshots.isEmpty()) {
            return ToolResult.success(null, "No snapshots found for project " + scId + ".\n"
                    + "Snapshots are created automatically before MEDIUM/CRITICAL operations.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Snapshots for project ").append(scId)
          .append(" (").append(snapshots.size()).append(" total):\n\n");

        for (SnapshotMetadata snap : snapshots) {
            sb.append("ID:      ").append(snap.snapshotId).append('\n');
            sb.append("Label:   ").append(snap.label).append('\n');
            sb.append("Tool:    ").append(snap.triggerTool != null ? snap.triggerTool : "manual").append('\n');
            sb.append("Created: ").append(DATE_FMT.format(new Date(snap.createdAt))).append('\n');
            sb.append("Size:    ").append(humanSize(snap.sizeBytes)).append('\n');
            sb.append('\n');
        }
        sb.append("Use restore_snapshot with the desired snapshot ID to roll back.");

        return ToolResult.success(null, sb.toString());
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
    }
}
