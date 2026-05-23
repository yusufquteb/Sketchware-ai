package pro.sketchware.ai.tools.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

/**
 * BlockApiTools — Phase 4 Block Logic API
 *
 * Provides 8 AI agent tools for reading and writing Sketchware Pro .logic files:
 *
 *   1. get_activity_events   — list all events/functions for an activity
 *   2. get_event_blocks      — get all blocks in a specific event
 *   3. add_block             — add a new block to an event
 *   4. modify_block          — update fields of an existing block
 *   5. delete_block          — remove a block and repair the chain
 *   6. get_moreblocks        — list all custom moreblock definitions
 *   7. create_moreblock      — create a new moreblock definition
 *   8. delete_moreblock      — delete a moreblock definition
 */
public final class BlockApiTools {

    private BlockApiTools() {}

    // ── Shared helpers ────────────────────────────────────────────────────

    private static ToolResult success(String output) {
        return ToolResult.success(null, output);
    }

    private static ToolResult error(String message) {
        return ToolResult.failure(null, message);
    }

    private static String requireString(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        return args.get(key).getAsString().trim();
    }

    private static File logicFile(ToolContext ctx, String scId) {
        return new File(ctx.getProjectDataDir(scId), "logic");
    }

    /**
     * Flushes Sketchware's in-memory logic cache after a BlockLogicWriter write.
     * Without this, the Logic Editor still shows the old blocks until manual reload.
     */
    private static void flushLogicCache(String scId) {
        try {
            a.a.a.jC.a(scId, true);   // reload logic from disk into jC's in-memory map
        } catch (Throwable ignored) {}
    }

    private static JsonObject blockSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();

        JsonObject scIdP = new JsonObject(); scIdP.addProperty("type", "string");
        scIdP.addProperty("description", "Sketchware project ID (sc_id)");
        props.add("sc_id", scIdP);

        JsonObject actP = new JsonObject(); actP.addProperty("type", "string");
        actP.addProperty("description", "Activity name without .java, e.g. 'MainActivity'");
        props.add("activity_name", actP);

        schema.add("properties", props);
        JsonArray req = new JsonArray();
        req.add("sc_id"); req.add("activity_name");
        schema.add("required", req);
        return schema;
    }

    // ── Tool 1: get_activity_events ───────────────────────────────────────

    public static class GetActivityEventsTool implements AgentTool {
        @Override public String getName() { return "get_activity_events"; }

        @Override public String getDescription() {
            return "Lists all logic events (onCreate, onClick, custom moreblocks, etc.) defined "
                 + "for a specific activity in a Sketchware Pro project. "
                 + "Returns event names, block counts, and whether each is a moreblock.";
        }

        @Override public JsonObject getParametersSchema() { return blockSchema(); }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = requireString(args, "sc_id");
            String activityName = requireString(args, "activity_name");
            if (scId == null)          return error("sc_id is required");
            if (activityName == null)  return error("activity_name is required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Reading logic file…", -1, true);
            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not parse logic file for project " + scId);

            List<BlockLogicReader.EventEntry> events = lf.eventsForActivity(activityName);
            if (events.isEmpty()) {
                return success("No events found for activity '" + activityName + "' in project " + scId
                        + ". Available activities: " + lf.activityNames());
            }

            JsonArray result = new JsonArray();
            for (BlockLogicReader.EventEntry ev : events) {
                JsonObject obj = new JsonObject();
                obj.addProperty("event_name",   ev.eventName);
                obj.addProperty("full_name",    ev.name);
                obj.addProperty("block_count",  ev.blocksById.size());
                obj.addProperty("is_moreblock", ev.isMoreBlock);
                result.add(obj);
            }

            JsonObject out = new JsonObject();
            out.addProperty("activity",    activityName);
            out.addProperty("event_count", events.size());
            out.add("events", result);
            return success(out.toString());
        }
    }

    // ── Tool 2: get_event_blocks ──────────────────────────────────────────

    public static class GetEventBlocksTool implements AgentTool {
        @Override public String getName() { return "get_event_blocks"; }

        @Override public String getDescription() {
            return "Returns all blocks inside a specific event of a Sketchware Pro activity, "
                 + "in execution order. Each block shows its id, opCode, spec, parameters, "
                 + "and chain links (nextBlock, subStack1, subStack2). "
                 + "Use this before add_block or modify_block to understand the current state.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = blockSchema();
            JsonObject props = schema.getAsJsonObject("properties");
            JsonObject evP = new JsonObject();
            evP.addProperty("type", "string");
            evP.addProperty("description", "Event name, e.g. 'onCreate', 'onClick_button1', or a moreblock name");
            props.add("event_name", evP);
            JsonArray req = schema.getAsJsonArray("required");
            req.add("event_name");
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId        = requireString(args, "sc_id");
            String actName     = requireString(args, "activity_name");
            String eventName   = requireString(args, "event_name");
            if (scId == null)      return error("sc_id is required");
            if (actName == null)   return error("activity_name is required");
            if (eventName == null) return error("event_name is required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Reading blocks…", -1, true);
            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not parse logic file");

            String fullName = actName + ".java_" + eventName;
            BlockLogicReader.EventEntry ev = lf.findEvent(fullName);
            if (ev == null) return error("Event not found: " + fullName
                    + ". Available: " + lf.eventsForActivity(actName).stream()
                        .map(e -> e.eventName).collect(java.util.stream.Collectors.joining(", ")));

            JsonArray blocks = new JsonArray();
            for (BlockLogicReader.BlockEntry b : ev.orderedBlocks()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id",        b.id);
                obj.addProperty("opCode",    b.opCode);
                obj.addProperty("spec",      b.spec);
                obj.addProperty("type",      b.type);
                obj.addProperty("nextBlock", b.nextBlock);
                obj.addProperty("subStack1", b.subStack1);
                obj.addProperty("subStack2", b.subStack2);
                JsonArray params = new JsonArray();
                for (String p : b.parameters) params.add(p);
                obj.add("parameters", params);
                blocks.add(obj);
            }

            JsonObject out = new JsonObject();
            out.addProperty("event",       fullName);
            out.addProperty("block_count", ev.blocksById.size());
            out.add("blocks", blocks);
            return success(out.toString());
        }
    }

    // ── Tool 3: add_block ─────────────────────────────────────────────────

    public static class AddBlockTool implements AgentTool {
        @Override public String getName() { return "add_block"; }

        @Override public String getDescription() {
            return "Adds ONE block to an event. "
                 + "⚠ For multiple blocks use set_event_logic instead — it is faster and more reliable. "
                 + "Required: sc_id, activity_name, event_name, opCode, spec, type. "
                 + "Optional: parameters (array of strings), after_block_id (-1 to prepend, omit to append). "
                 + "For addSourceDirectly: opCode='addSourceDirectly', "
                 + "spec='add source directly %s.inputOnly', type=' ', parameters=['your java code;']. "
                 + "Common opCodes: addSourceDirectly (raw Java), ifElse, doWhile, "
                 + "initializeVariable, setBoolean, setInt, setString, callFunc, "
                 + "showToast, showAlertDialog, startActivity, finish.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();

            addProp(props, "sc_id",          "string", "Project ID");
            addProp(props, "activity_name",  "string", "Activity name without .java");
            addProp(props, "event_name",     "string", "Event name, e.g. 'onCreate'");
            addProp(props, "opCode",         "string", "Block opCode, e.g. 'addSourceDirectly'");
            addProp(props, "spec",           "string", "Block spec string");
            addProp(props, "type",           "string", "Block type: ' '=statement, 'b'=boolean, 'd'=number, 's'=string, 'c'=condition, 'e'=end");
            addProp(props, "after_block_id", "integer", "Insert after this block id, -1 to prepend (default: append at end)");

            JsonObject paramsP = new JsonObject();
            paramsP.addProperty("type", "array");
            JsonObject items = new JsonObject(); items.addProperty("type", "string");
            paramsP.add("items", items);
            paramsP.addProperty("description", "Block parameter values");
            props.add("parameters", paramsP);

            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("event_name");
            req.add("opCode"); req.add("spec"); req.add("type");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId      = requireString(args, "sc_id");
            String actName   = requireString(args, "activity_name");
            String eventName = requireString(args, "event_name");
            String opCode    = requireString(args, "opCode");
            String spec      = requireString(args, "spec");
            String type      = requireString(args, "type");
            if (scId == null || actName == null || eventName == null
                    || opCode == null || spec == null || type == null)
                return error("sc_id, activity_name, event_name, opCode, spec and type are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            int afterBlockId = args.has("after_block_id")
                    ? args.get("after_block_id").getAsInt() : Integer.MAX_VALUE;

            ctx.reportProgress("Adding block…", -1, true);
            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not parse logic file");

            String fullName = actName + ".java_" + eventName;
            // Auto-create event if it doesn't exist
            if (lf.findEvent(fullName) == null) {
                BlockLogicReader.EventEntry newEv =
                        new BlockLogicReader.EventEntry(fullName, new JsonArray());
                lf.events.add(newEv);
            }

            // If afterBlockId is MAX_VALUE, append at end
            if (afterBlockId == Integer.MAX_VALUE) {
                BlockLogicReader.EventEntry ev = lf.findEvent(fullName);
                List<BlockLogicReader.BlockEntry> ordered = ev.orderedBlocks();
                afterBlockId = ordered.isEmpty() ? -1 : ordered.get(ordered.size() - 1).id;
            }

            JsonObject blockJson = new JsonObject();
            blockJson.addProperty("opCode",   opCode);
            blockJson.addProperty("spec",     spec);
            blockJson.addProperty("type",     type);
            blockJson.addProperty("typeName", "");
            blockJson.addProperty("color",    -10701022);

            if (args.has("parameters") && args.get("parameters").isJsonArray()) {
                blockJson.add("parameters", args.getAsJsonArray("parameters"));
            } else {
                blockJson.add("parameters", new JsonArray());
            }

            BlockLogicWriter writer = new BlockLogicWriter(logicFile(ctx, scId), scId);
            String err = writer.addBlock(lf, fullName, blockJson, afterBlockId);
            if (err != null) return error(err);
            return success("Block added successfully to event '" + fullName + "' in project " + scId);
        }
    }

    // ── Tool 4: modify_block ──────────────────────────────────────────────

    public static class ModifyBlockTool implements AgentTool {
        @Override public String getName() { return "modify_block"; }

        @Override public String getDescription() {
            return "Modifies fields of an existing block in a Sketchware Pro event. "
                 + "Only specified fields are updated; others remain unchanged. "
                 + "Commonly modified fields: spec, opCode, parameters, type. "
                 + "Use get_event_blocks first to get the block id.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addProp(props, "sc_id",         "string",  "Project ID");
            addProp(props, "activity_name", "string",  "Activity name without .java");
            addProp(props, "event_name",    "string",  "Event name");
            addProp(props, "block_id",      "integer", "ID of the block to modify");
            addProp(props, "opCode",        "string",  "New opCode value (optional)");
            addProp(props, "spec",          "string",  "New spec value (optional)");
            addProp(props, "type",          "string",  "New type value (optional)");
            JsonObject paramsP = new JsonObject();
            paramsP.addProperty("type", "array");
            JsonObject items = new JsonObject(); items.addProperty("type", "string");
            paramsP.add("items", items);
            paramsP.addProperty("description", "New parameters array (optional)");
            props.add("parameters", paramsP);
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("event_name"); req.add("block_id");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId      = requireString(args, "sc_id");
            String actName   = requireString(args, "activity_name");
            String eventName = requireString(args, "event_name");
            if (scId == null || actName == null || eventName == null)
                return error("sc_id, activity_name, event_name are required");
            if (!args.has("block_id")) return error("block_id is required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            int blockId = args.get("block_id").getAsInt();
            ctx.reportProgress("Modifying block " + blockId + "…", -1, true);

            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not parse logic file");

            JsonObject patch = new JsonObject();
            if (args.has("opCode"))      patch.addProperty("opCode", args.get("opCode").getAsString());
            if (args.has("spec"))        patch.addProperty("spec",   args.get("spec").getAsString());
            if (args.has("type"))        patch.addProperty("type",   args.get("type").getAsString());
            if (args.has("parameters"))  patch.add("parameters",     args.get("parameters"));

            if (patch.size() == 0) return error("No fields to modify specified");

            String fullName = actName + ".java_" + eventName;
            BlockLogicWriter writer = new BlockLogicWriter(logicFile(ctx, scId), scId);
            String err = writer.modifyBlock(lf, fullName, blockId, patch);
            if (err != null) return error(err);
            return success("Block " + blockId + " modified in event '" + fullName + "'");
        }
    }

    // ── Tool 5: delete_block ──────────────────────────────────────────────

    public static class DeleteBlockTool implements AgentTool {
        @Override public String getName() { return "delete_block"; }

        @Override public String getDescription() {
            return "Deletes a block from a Sketchware Pro event and repairs the execution chain. "
                 + "The successor block (nextBlock) is automatically linked to the predecessor. "
                 + "Use get_event_blocks first to identify the block id.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addProp(props, "sc_id",         "string",  "Project ID");
            addProp(props, "activity_name", "string",  "Activity name without .java");
            addProp(props, "event_name",    "string",  "Event name");
            addProp(props, "block_id",      "integer", "ID of the block to delete");
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("event_name"); req.add("block_id");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId      = requireString(args, "sc_id");
            String actName   = requireString(args, "activity_name");
            String eventName = requireString(args, "event_name");
            if (scId == null || actName == null || eventName == null)
                return error("sc_id, activity_name, event_name are required");
            if (!args.has("block_id")) return error("block_id is required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            int blockId = args.get("block_id").getAsInt();
            ctx.reportProgress("Deleting block " + blockId + "…", -1, true);

            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not parse logic file");

            String fullName = actName + ".java_" + eventName;
            BlockLogicWriter writer = new BlockLogicWriter(logicFile(ctx, scId), scId);
            String err = writer.deleteBlock(lf, fullName, blockId);
            if (err != null) return error(err);
            return success("Block " + blockId + " deleted from event '" + fullName + "'");
        }
    }

    // ── Tool 6: get_moreblocks ────────────────────────────────────────────

    public static class GetMoreBlocksTool implements AgentTool {
        @Override public String getName() { return "get_moreblocks"; }

        @Override public String getDescription() {
            return "Lists all custom moreblock (function) definitions for an activity "
                 + "in a Sketchware Pro project. Returns each moreblock's name, spec, "
                 + "and parameter types.";
        }

        @Override public JsonObject getParametersSchema() { return blockSchema(); }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            if (scId == null || actName == null)
                return error("sc_id and activity_name are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Reading moreblocks…", -1, true);
            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not parse logic file");

            List<BlockLogicReader.EventEntry> events = lf.eventsForActivity(actName);
            JsonArray moreblocks = new JsonArray();
            for (BlockLogicReader.EventEntry ev : events) {
                if (ev.isMoreBlock) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("name",        ev.eventName);
                    obj.addProperty("full_name",   ev.name);
                    obj.addProperty("block_count", ev.blocksById.size());
                    moreblocks.add(obj);
                }
            }
            JsonObject out = new JsonObject();
            out.addProperty("activity",        actName);
            out.addProperty("moreblock_count", moreblocks.size());
            out.add("moreblocks", moreblocks);
            return success(out.toString());
        }
    }

    // ── Tool 7: create_moreblock ──────────────────────────────────────────

    public static class CreateMoreBlockTool implements AgentTool {
        @Override public String getName() { return "create_moreblock"; }

        @Override public String getDescription() {
            return "Creates a new custom moreblock (function) definition for an activity. "
                 + "The moreblock name must be a valid Java identifier. "
                 + "After creation, use add_block to add logic inside the moreblock.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addProp(props, "sc_id",            "string", "Project ID");
            addProp(props, "activity_name",    "string", "Activity name without .java");
            addProp(props, "moreblock_name",   "string", "Name for the moreblock (valid Java identifier)");
            addProp(props, "moreblock_spec",   "string", "Display spec string shown in logic editor");
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("moreblock_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId     = requireString(args, "sc_id");
            String actName  = requireString(args, "activity_name");
            String mbName   = requireString(args, "moreblock_name");
            if (scId == null || actName == null || mbName == null)
                return error("sc_id, activity_name, moreblock_name are required");
            if (!mbName.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
                return error("moreblock_name must be a valid Java identifier: " + mbName);
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Creating moreblock " + mbName + "…", -1, true);
            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not parse logic file");

            String fullName = actName + ".java_onMoreBlock_" + mbName;
            BlockLogicWriter writer = new BlockLogicWriter(logicFile(ctx, scId), scId);
            String err = writer.createEvent(lf, fullName);
            if (err != null) return error(err);
            return success("Moreblock '" + mbName + "' created in activity '" + actName + "'.\n"
                    + "Use add_block with event_name='onMoreBlock_" + mbName + "' to add blocks.");
        }
    }

    // ── Tool 8: delete_moreblock ──────────────────────────────────────────

    public static class DeleteMoreBlockTool implements AgentTool {
        @Override public String getName() { return "delete_moreblock"; }

        @Override public String getDescription() {
            return "Deletes a custom moreblock (function) definition and all its blocks "
                 + "from a Sketchware Pro activity's logic.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addProp(props, "sc_id",           "string", "Project ID");
            addProp(props, "activity_name",   "string", "Activity name without .java");
            addProp(props, "moreblock_name",  "string", "Name of the moreblock to delete");
            schema.add("properties", props);
            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("moreblock_name");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId    = requireString(args, "sc_id");
            String actName = requireString(args, "activity_name");
            String mbName  = requireString(args, "moreblock_name");
            if (scId == null || actName == null || mbName == null)
                return error("sc_id, activity_name, moreblock_name are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Deleting moreblock " + mbName + "…", -1, true);
            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not parse logic file");

            String fullName = actName + ".java_onMoreBlock_" + mbName;
            BlockLogicWriter writer = new BlockLogicWriter(logicFile(ctx, scId), scId);
            String err = writer.deleteEvent(lf, fullName);
            if (err != null) return error(err);
            return success("Moreblock '" + mbName + "' deleted from activity '" + actName + "'");
        }
    }

    // ── Phase 3: describe_block_logic ─────────────────────────────────────────

    /**
     * Converts a raw block chain into human-readable pseudocode the AI can reason about.
     * Much easier to understand than raw block JSON with nextBlock/subStack1 link IDs.
     *
     * Output example:
     *   [onCreate]
     *   1: setVariable(count, 0)
     *   2: if (count > 0) {
     *   3:   showToast("Hello")
     *      } else {
     *   4:   showToast("World")
     *      }
     *   5: button1.setText("Click me")
     */
    public static class DescribeBlockLogicTool implements AgentTool {
        @Override public String getName() { return "describe_block_logic"; }

        @Override public String getDescription() {
            return "Converts a Sketchware event's block chain into readable pseudocode. "
                 + "Shows the logical structure with if/else branches and subStacks clearly. "
                 + "Far easier to understand than the raw block JSON returned by get_event_blocks. "
                 + "Use this to audit or plan modifications to existing logic before using add_block/modify_block. "
                 + "Parameters: sc_id, activity_name, event_name. "
                 + "Set event_name to '*' to describe ALL events in the activity.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = blockSchema();
            JsonObject props = schema.getAsJsonObject("properties");
            JsonObject evP = new JsonObject();
            evP.addProperty("type", "string");
            evP.addProperty("description",
                "Event name (e.g. 'onCreate', 'onClick_button1') or '*' for all events");
            props.add("event_name", evP);
            JsonArray req = schema.getAsJsonArray("required");
            req.add("event_name");
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId      = requireString(args, "sc_id");
            String actName   = requireString(args, "activity_name");
            String eventName = requireString(args, "event_name");
            if (scId == null || actName == null || eventName == null)
                return error("sc_id, activity_name, and event_name are required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            ctx.reportProgress("Describing block logic…", -1, true);
            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not parse logic file");

            StringBuilder sb = new StringBuilder();
            sb.append("# Block Logic: ").append(actName).append("\n\n");

            List<BlockLogicReader.EventEntry> targets;
            if ("*".equals(eventName)) {
                targets = lf.eventsForActivity(actName);
                if (targets.isEmpty())
                    return error("No events found for activity: " + actName);
            } else {
                String fullName = actName + ".java_" + eventName;
                BlockLogicReader.EventEntry ev = lf.findEvent(fullName);
                if (ev == null)
                    return error("Event not found: " + fullName
                            + ". Available: " + lf.eventsForActivity(actName).stream()
                                .map(e -> e.eventName)
                                .collect(java.util.stream.Collectors.joining(", ")));
                targets = java.util.Arrays.asList(ev);
            }

            for (BlockLogicReader.EventEntry ev : targets) {
                sb.append("## [").append(ev.eventName).append("]\n");
                if (ev.blocksById.isEmpty()) {
                    sb.append("  (empty — no blocks)\n\n");
                    continue;
                }
                renderChain(ev.orderedBlocks(), sb, "  ", ev.blocksById);
                sb.append("\n");
            }

            return success(sb.toString().trim());
        }

        private void renderChain(List<BlockLogicReader.BlockEntry> chain,
                                  StringBuilder sb, String indent,
                                  java.util.Map<Integer, BlockLogicReader.BlockEntry> byId) {
            for (BlockLogicReader.BlockEntry b : chain) {
                String line = fillSpec(b);
                sb.append(indent).append("[").append(b.id).append("] ").append(line).append("\n");

                if (b.subStack1 != -1 && byId.containsKey(b.subStack1)) {
                    sb.append(indent).append("  {\n");
                    renderChain(collectChain(b.subStack1, byId), sb, indent + "    ", byId);
                    sb.append(indent).append("  }\n");
                }

                if (b.subStack2 != -1 && byId.containsKey(b.subStack2)) {
                    sb.append(indent).append("  else {\n");
                    renderChain(collectChain(b.subStack2, byId), sb, indent + "    ", byId);
                    sb.append(indent).append("  }\n");
                }
            }
        }

        /** Follows nextBlock links starting from startId, returns the ordered chain. */
        private List<BlockLogicReader.BlockEntry> collectChain(
                int startId, java.util.Map<Integer, BlockLogicReader.BlockEntry> byId) {
            List<BlockLogicReader.BlockEntry> chain = new ArrayList<>();
            java.util.Set<Integer> visited = new java.util.HashSet<>();
            int cur = startId;
            while (cur != -1 && byId.containsKey(cur) && visited.add(cur)) {
                BlockLogicReader.BlockEntry b = byId.get(cur);
                chain.add(b);
                cur = b.nextBlock;
            }
            return chain;
        }

        /** Substitutes %s / %d / %b placeholders in the block spec with actual parameters. */
        private String fillSpec(BlockLogicReader.BlockEntry b) {
            String spec = b.spec;
            if (spec == null || spec.isEmpty()) spec = b.opCode;
            // Remove Sketchware type suffixes like %s.inputOnly, %d.inputOnly
            spec = spec.replaceAll("%[sdb]\\.\\S+", "%p");
            spec = spec.replaceAll("%[sdb]", "%p");
            List<String> params = b.parameters;
            if (params.isEmpty()) return spec;
            StringBuilder res = new StringBuilder();
            int pi = 0;
            int i = 0;
            while (i < spec.length()) {
                if (i + 1 < spec.length() && spec.charAt(i) == '%' && spec.charAt(i + 1) == 'p') {
                    res.append(pi < params.size() ? params.get(pi++) : "?");
                    i += 2;
                } else {
                    res.append(spec.charAt(i++));
                }
            }
            // Append remaining params if any
            while (pi < params.size()) res.append(" ").append(params.get(pi++));
            return res.toString();
        }
    }

    // ── Tool 10: set_event_logic ──────────────────────────────────────────

    /**
     * Replaces ALL blocks in an event with a fresh ordered list.
     *
     * The AI provides blocks WITHOUT ids or nextBlock links — they are
     * auto-assigned by this tool.  Nested blocks (if/loop conditions) use
     * "then_blocks" / "else_blocks" arrays that are recursively wired.
     *
     * Example blocks array:
     *   [
     *     {"opCode":"addSourceDirectly","spec":"add source directly %s.inputOnly",
     *      "type":" ","parameters":["setTitle(\"Hello\");"]},
     *     {"opCode":"ifElse","spec":"if %b then","type":"c",
     *      "parameters":["count > 0"],
     *      "then_blocks":[
     *        {"opCode":"addSourceDirectly","type":" ","parameters":["doSomething();"]}
     *      ],
     *      "else_blocks":[
     *        {"opCode":"addSourceDirectly","type":" ","parameters":["doElse();"]}
     *      ]}
     *   ]
     */
    public static class SetEventLogicTool implements AgentTool {
        @Override public String getName() { return "set_event_logic"; }

        @Override public String getDescription() {
            return "Replaces ALL blocks in a Sketchware event with a new ordered list in one call. "
                 + "Much faster than calling add_block repeatedly. "
                 + "Provide blocks in execution order WITHOUT ids or nextBlock — they are auto-assigned. "
                 + "Nested branches use 'then_blocks' and 'else_blocks' arrays. "
                 + "Common opCodes: addSourceDirectly (raw Java), ifElse, doWhile, "
                 + "setInt, setString, setBoolean, callFunc, showToast, startActivity, finish. "
                 + "For addSourceDirectly: type=' ', spec='add source directly %s.inputOnly', "
                 + "parameters=[\"your java code here;\"]";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addProp(props, "sc_id",         "string",  "Project ID");
            addProp(props, "activity_name", "string",  "Activity name without .java");
            addProp(props, "event_name",    "string",  "Event name, e.g. 'onCreate', 'onClick_button1'");

            JsonObject blocksP = new JsonObject();
            blocksP.addProperty("type", "array");
            blocksP.addProperty("description",
                    "Ordered list of block objects. Fields: opCode, spec, type, parameters, "
                  + "color (optional), then_blocks (array, for if-true branch), "
                  + "else_blocks (array, for else branch). "
                  + "Do NOT include id, nextBlock, subStack1, subStack2 — auto-assigned.");
            JsonObject items = new JsonObject(); items.addProperty("type", "object");
            blocksP.add("items", items);
            props.add("blocks", blocksP);
            schema.add("properties", props);

            JsonArray req = new JsonArray();
            req.add("sc_id"); req.add("activity_name"); req.add("event_name"); req.add("blocks");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId      = requireString(args, "sc_id");
            String actName   = requireString(args, "activity_name");
            String eventName = requireString(args, "event_name");
            if (scId == null || actName == null || eventName == null)
                return error("sc_id, activity_name, and event_name are required");
            if (!args.has("blocks") || !args.get("blocks").isJsonArray())
                return error("'blocks' must be a JSON array");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            JsonArray blocksInput = args.getAsJsonArray("blocks");
            String fullName = actName + ".java_" + eventName;

            ctx.reportProgress("Writing " + blocksInput.size() + " blocks to " + fullName + "…");

            BlockLogicReader.LogicFile lf = BlockLogicReader.read(logicFile(ctx, scId), scId);
            if (lf == null) return error("Could not read logic file for project " + scId);

            BlockLogicReader.EventEntry existing = lf.findEvent(fullName);
            if (existing != null) lf.events.remove(existing);

            JsonArray newContent = new JsonArray();
            int[] idCounter = {0};
            BlockLogicReader.EventEntry newEv = new BlockLogicReader.EventEntry(fullName, newContent);
            buildBlocks(blocksInput, newContent, newEv.blocksById, idCounter, -1);
            lf.events.add(newEv);

            BlockLogicWriter writer = new BlockLogicWriter(logicFile(ctx, scId), scId);
            String err = writer.write(lf);
            if (err != null) return error(err);
            return success("Set " + newContent.size() + " block(s) in event '" + fullName
                    + "' of project " + scId + ".");
        }

        /**
         * Recursively builds block JSON, assigns sequential IDs, wires nextBlock/subStack links,
         * and populates blocksById. Parent blocks are written before their nested children.
         *
         * @return id of the first block in this chain, or -1 if empty
         */
        private int buildBlocks(JsonArray inputBlocks, JsonArray output,
                                 java.util.Map<Integer, BlockLogicReader.BlockEntry> byId,
                                 int[] idCounter, int nextOverride) {
            if (inputBlocks == null || inputBlocks.size() == 0) return -1;

            int[] ids = new int[inputBlocks.size()];
            for (int i = 0; i < inputBlocks.size(); i++) ids[i] = ++idCounter[0];

            for (int i = 0; i < inputBlocks.size(); i++) {
                JsonElement el = inputBlocks.get(i);
                if (!el.isJsonObject()) continue;
                JsonObject src = el.getAsJsonObject().deepCopy();

                int nextBlock = (i < inputBlocks.size() - 1) ? ids[i + 1] : nextOverride;

                JsonArray nested = new JsonArray();
                int subStack1 = -1;
                if (src.has("then_blocks") && src.get("then_blocks").isJsonArray()) {
                    subStack1 = buildBlocks(src.getAsJsonArray("then_blocks"), nested, byId, idCounter, -1);
                    src.remove("then_blocks");
                }
                int subStack2 = -1;
                if (src.has("else_blocks") && src.get("else_blocks").isJsonArray()) {
                    subStack2 = buildBlocks(src.getAsJsonArray("else_blocks"), nested, byId, idCounter, -1);
                    src.remove("else_blocks");
                }

                src.addProperty("id",        ids[i]);
                src.addProperty("nextBlock",  nextBlock);
                src.addProperty("subStack1",  subStack1);
                src.addProperty("subStack2",  subStack2);
                if (!src.has("color"))      src.addProperty("color",    -10701022);
                if (!src.has("typeName"))   src.addProperty("typeName", "");
                if (!src.has("parameters")) src.add("parameters", new JsonArray());

                output.add(src);
                BlockLogicReader.BlockEntry entry = new BlockLogicReader.BlockEntry(src);
                byId.put(entry.id, entry);
                for (JsonElement ne : nested) output.add(ne);
            }
            return ids[0];
        }
    }

    // ── Tool 11: undo_blocks ──────────────────────────────────────────────

    /**
     * Restores the logic file to its state before the last AI modification.
     * Keeps a backup at {dataDir}/logic.bak created just before each write.
     */
    public static class UndoBlocksTool implements AgentTool {
        @Override public String getName() { return "undo_blocks"; }

        @Override public String getDescription() {
            return "Reverts the last change made to a Sketchware project's block logic. "
                 + "Restores the logic file to its state before the previous set_event_logic, "
                 + "add_block, modify_block, or delete_block call. "
                 + "Each project has one undo level — call immediately after a mistake.";
        }

        @Override public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            JsonObject props = new JsonObject();
            addProp(props, "sc_id", "string", "Project ID");
            schema.add("properties", props);
            JsonArray req = new JsonArray(); req.add("sc_id");
            schema.add("required", req);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = requireString(args, "sc_id");
            if (scId == null) return error("sc_id is required");
            if (!ctx.isProjectAllowed(scId)) return error("Access denied: project " + scId);

            File logic = logicFile(ctx, scId);
            File backup = new File(logic.getParentFile(), "logic.bak");
            if (!backup.exists()) return error("No undo backup found for project " + scId
                    + ". The undo backup is created just before the first AI block modification.");

            try {
                // Copy backup → logic
                byte[] bak = java.nio.file.Files.readAllBytes(backup.toPath());
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logic, "rw")) {
                    raf.setLength(0);
                    raf.write(bak);
                }
                flushLogicCache(scId);
                backup.delete();
                return success("Undo successful. Block logic for project " + scId + " has been restored.");
            } catch (Exception e) {
                return error("Undo failed: " + e.getMessage());
            }
        }
    }

    // ── Schema helpers ────────────────────────────────────────────────────

    private static void addProp(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", desc);
        props.add(key, p);
    }
}
