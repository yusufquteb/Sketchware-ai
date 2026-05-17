package pro.sketchware.ai.tools.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import pro.sketchware.util.SketchwareFileDecryptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BlockLogicReader — Phase 4 Block Logic API
 *
 * Reads Sketchware Pro's .logic file format and converts it into a structured
 * object model the AI agent can understand and manipulate.
 *
 * .logic file structure:
 *   JSON array of event entries, each:
 *   {
 *     "name": "MainActivity.java_onCreate",   // {activity}.java_{eventName}
 *     "content": [                             // ordered list of blocks
 *       {
 *         "id": 1,
 *         "opCode": "addSourceDirectly",
 *         "spec": "add source directly %s.inputOnly",
 *         "type": " ",
 *         "typeName": "",
 *         "color": -10701022,
 *         "nextBlock": 2,          // id of next block (-1 = end)
 *         "subStack1": -1,         // id of first child block (-1 = none)
 *         "subStack2": -1,
 *         "parameters": ["Toast.makeText(this,\"Hello\",0).show();"]
 *       }, ...
 *     ]
 *   }
 *
 * Special entries:
 *   name ending with ".java_onMoreBlock"  → custom moreblock definitions
 *   name containing "@" separator         → component events
 */
public final class BlockLogicReader {

    private BlockLogicReader() {}

    // ── Public data model ─────────────────────────────────────────────────

    public static class LogicFile {
        public final String scId;
        public final List<EventEntry> events = new ArrayList<>();

        public LogicFile(String scId) { this.scId = scId; }

        /** Returns the event entry with the given name, or null. */
        public EventEntry findEvent(String name) {
            for (EventEntry e : events) {
                if (e.name.equals(name)) return e;
            }
            return null;
        }

        /** Returns all events for the given activity (e.g. "MainActivity"). */
        public List<EventEntry> eventsForActivity(String activityName) {
            String prefix = activityName + ".java_";
            List<EventEntry> result = new ArrayList<>();
            for (EventEntry e : events) {
                if (e.name.startsWith(prefix)) result.add(e);
            }
            return result;
        }

        /** Returns all distinct activity names present in this logic file. */
        public List<String> activityNames() {
            List<String> names = new ArrayList<>();
            for (EventEntry e : events) {
                int dot = e.name.indexOf(".java_");
                if (dot > 0) {
                    String act = e.name.substring(0, dot);
                    if (!names.contains(act)) names.add(act);
                }
            }
            return names;
        }
    }

    public static class EventEntry {
        /** Full entry name: e.g. "MainActivity.java_onCreate" */
        public final String name;
        /** Activity name extracted from name (e.g. "MainActivity") */
        public final String activityName;
        /** Event name extracted from name (e.g. "onCreate") */
        public final String eventName;
        /** Whether this is a moreblock definition */
        public final boolean isMoreBlock;
        /** Ordered list of blocks. Key = block id, value = block. */
        public final Map<Integer, BlockEntry> blocksById = new LinkedHashMap<>();
        /** Raw JSON content array (preserved for write-back) */
        public final JsonArray rawContent;

        public EventEntry(String name, JsonArray rawContent) {
            this.name = name;
            this.rawContent = rawContent;
            int dot = name.indexOf(".java_");
            if (dot > 0) {
                activityName = name.substring(0, dot);
                eventName    = name.substring(dot + 6);
            } else {
                activityName = name;
                eventName    = "";
            }
            isMoreBlock = eventName.equals("onMoreBlock");
        }

        /** Returns blocks in logical execution order (following nextBlock links). */
        public List<BlockEntry> orderedBlocks() {
            List<BlockEntry> ordered = new ArrayList<>();
            // Find root block (not referenced as nextBlock, subStack1, or subStack2 by any other)
            java.util.Set<Integer> referenced = new java.util.HashSet<>();
            for (BlockEntry b : blocksById.values()) {
                if (b.nextBlock  != -1) referenced.add(b.nextBlock);
                if (b.subStack1  != -1) referenced.add(b.subStack1);
                if (b.subStack2  != -1) referenced.add(b.subStack2);
            }
            BlockEntry root = null;
            for (BlockEntry b : blocksById.values()) {
                if (!referenced.contains(b.id)) { root = b; break; }
            }
            if (root == null && !blocksById.isEmpty()) {
                root = blocksById.values().iterator().next();
            }
            // Follow nextBlock chain
            BlockEntry cur = root;
            java.util.Set<Integer> visited = new java.util.HashSet<>();
            while (cur != null && !visited.contains(cur.id)) {
                ordered.add(cur);
                visited.add(cur.id);
                cur = cur.nextBlock != -1 ? blocksById.get(cur.nextBlock) : null;
            }
            return ordered;
        }
    }

    public static class BlockEntry {
        public final int    id;
        public final String opCode;
        public final String spec;
        public final String type;
        public final String typeName;
        public final int    color;
        public final int    nextBlock;
        public final int    subStack1;
        public final int    subStack2;
        public final List<String> parameters;
        /** Reference to original JSON for patch-write */
        public final JsonObject rawJson;

        public BlockEntry(JsonObject json) {
            this.rawJson    = json;
            this.id         = getInt(json, "id", 0);
            this.opCode     = getString(json, "opCode");
            this.spec       = getString(json, "spec");
            this.type       = getString(json, "type");
            this.typeName   = getString(json, "typeName");
            this.color      = getInt(json, "color", 0);
            this.nextBlock  = getInt(json, "nextBlock", -1);
            this.subStack1  = getInt(json, "subStack1", -1);
            this.subStack2  = getInt(json, "subStack2", -1);
            this.parameters = new ArrayList<>();
            if (json.has("parameters") && json.get("parameters").isJsonArray()) {
                for (JsonElement p : json.getAsJsonArray("parameters")) {
                    parameters.add(p.isJsonNull() ? "" : p.getAsString());
                }
            }
        }

        private static String getString(JsonObject j, String key) {
            return j.has(key) && !j.get(key).isJsonNull() ? j.get(key).getAsString() : "";
        }
        private static int getInt(JsonObject j, String key, int def) {
            return j.has(key) && j.get(key).isJsonPrimitive() ? j.get(key).getAsInt() : def;
        }

        /** Human-readable description of this block. */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("[id=").append(id).append("] ");
            sb.append(opCode.isEmpty() ? spec : opCode);
            if (!parameters.isEmpty()) {
                sb.append(" params=").append(parameters);
            }
            if (nextBlock != -1)  sb.append(" →").append(nextBlock);
            if (subStack1 != -1)  sb.append(" sub1→").append(subStack1);
            if (subStack2 != -1)  sb.append(" sub2→").append(subStack2);
            return sb.toString();
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Reads and parses the logic file for a project.
     *
     * @param logicFile  File pointing to .sketchware/data/{scId}/logic
     * @param scId       project id (for reference in returned model)
     * @return parsed LogicFile, or null if file doesn't exist or can't be parsed
     */
    public static LogicFile read(File logicFile, String scId) {
        if (!logicFile.exists()) return new LogicFile(scId);
        try {
            String raw = readFile(logicFile);
            if (raw == null || raw.trim().isEmpty()) return new LogicFile(scId);
            JsonArray array = JsonParser.parseString(raw.trim()).getAsJsonArray();
            return parse(array, scId);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }

    /**
     * Converts the logic file to a compact human-readable summary for the AI model.
     * Groups by activity and lists events with block counts.
     */
    public static String summarize(LogicFile lf) {
        if (lf == null) return "Error: could not read logic file.";
        if (lf.events.isEmpty()) return "Logic file is empty — no events defined yet.";

        StringBuilder sb = new StringBuilder();
        sb.append("Project ").append(lf.scId).append(" — ")
          .append(lf.events.size()).append(" event(s):\n");

        String currentActivity = null;
        for (EventEntry ev : lf.events) {
            if (!ev.activityName.equals(currentActivity)) {
                currentActivity = ev.activityName;
                sb.append("\n### ").append(currentActivity).append("\n");
            }
            sb.append("  event: ").append(ev.eventName)
              .append(" (").append(ev.blocksById.size()).append(" block(s)");
            if (ev.isMoreBlock) sb.append(", moreblock");
            sb.append(")\n");
            for (BlockEntry b : ev.orderedBlocks()) {
                sb.append("    • ").append(b.describe()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Serialises a LogicFile back to the JSON string for writing to disk.
     */
    public static String serialise(LogicFile lf) {
        JsonArray out = new JsonArray();
        for (EventEntry ev : lf.events) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", ev.name);
            entry.add("content", ev.rawContent);
            out.add(entry);
        }
        return out.toString();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static LogicFile parse(JsonArray array, String scId) {
        LogicFile lf = new LogicFile(scId);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            JsonArray content = obj.has("content") && obj.get("content").isJsonArray()
                    ? obj.getAsJsonArray("content") : new JsonArray();

            EventEntry ev = new EventEntry(name, content);
            for (JsonElement blockEl : content) {
                if (!blockEl.isJsonObject()) continue;
                BlockEntry block = new BlockEntry(blockEl.getAsJsonObject());
                ev.blocksById.put(block.id, block);
            }
            lf.events.add(ev);
        }
        return lf;
    }

    /**
     * Reads and decrypts a Sketchware logic file using IA's proven decryptor.
     * The logic file uses AES/CBC with KEY=IV="sketchwaresecure".
     * Falls back to plain text for new/unencrypted files.
     */
    private static String readFile(File f) throws IOException {
        if (!f.exists() || f.length() == 0) return "[]";
        // Extract scId from path: .../.sketchware/data/{scId}/logic
        String absPath = f.getAbsolutePath().replace("\\", "/");
        String[] parts = absPath.split("/");
        String scId = null;
        String relPath = null;
        for (int i = 0; i < parts.length - 1; i++) {
            if ("data".equals(parts[i]) && i + 1 < parts.length) {
                scId = parts[i + 1];
                relPath = parts[parts.length - 1]; // "logic"
                break;
            }
        }
        if (scId != null && relPath != null) {
            String decrypted = SketchwareFileDecryptor.decryptFile(scId, relPath);
            if (decrypted != null && !decrypted.isEmpty()) return decrypted;
        }
        // Fallback: plain text
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            char[] buf = new char[4096]; int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
