package pro.sketchware.ai.tools.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.util.SketchwareFileDecryptor;

/**
 * BlockLogicReader — reads Sketchware Pro's native .logic file format.
 *
 * Native format (AES-encrypted, key/IV = "sketchwaresecure"):
 *
 *   @ActivityName.java_eventName
 *   {"id":1,"opCode":"...","spec":"...","type":" ","nextBlock":2,...,"parameters":["..."]}
 *   {"id":2,"opCode":"...","nextBlock":-1,...}
 *   @ActivityName.java_onMoreBlock_myFunc
 *   {"id":1,...}
 *
 * Each section starts with "@{activityName}.java_{eventName}" and contains
 * one BlockBean JSON object per line.
 *
 * Block chain navigation:
 *   nextBlock  — id of the next statement in sequence  (-1 = end)
 *   subStack1  — id of the first child block (if-true / loop body)  (-1 = none)
 *   subStack2  — id of the second child block (else branch)         (-1 = none)
 */
public final class BlockLogicReader {

    private BlockLogicReader() {}

    // ── Public data model ─────────────────────────────────────────────────

    public static class LogicFile {
        public final String scId;
        public final List<EventEntry> events = new ArrayList<>();

        public LogicFile(String scId) { this.scId = scId; }

        public EventEntry findEvent(String name) {
            for (EventEntry e : events) { if (e.name.equals(name)) return e; }
            return null;
        }

        public List<EventEntry> eventsForActivity(String activityName) {
            String prefix = activityName + ".java_";
            List<EventEntry> result = new ArrayList<>();
            for (EventEntry e : events) { if (e.name.startsWith(prefix)) result.add(e); }
            return result;
        }

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
        /** Full entry name, e.g. "MainActivity.java_onCreate" */
        public final String name;
        public final String activityName;
        public final String eventName;
        public final boolean isMoreBlock;
        /** Ordered map: block id → block. */
        public final Map<Integer, BlockEntry> blocksById = new LinkedHashMap<>();
        /** Mutable JSON content array — mutated in-place by BlockLogicWriter. */
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
            isMoreBlock = eventName.startsWith("onMoreBlock");
        }

        /** Returns blocks in logical execution order (root → nextBlock chain). */
        public List<BlockEntry> orderedBlocks() {
            if (blocksById.isEmpty()) return new ArrayList<>();
            java.util.Set<Integer> referenced = new java.util.HashSet<>();
            for (BlockEntry b : blocksById.values()) {
                if (b.nextBlock != -1) referenced.add(b.nextBlock);
                if (b.subStack1 != -1) referenced.add(b.subStack1);
                if (b.subStack2 != -1) referenced.add(b.subStack2);
            }
            BlockEntry root = null;
            for (BlockEntry b : blocksById.values()) {
                if (!referenced.contains(b.id)) { root = b; break; }
            }
            if (root == null) root = blocksById.values().iterator().next();

            List<BlockEntry> ordered = new ArrayList<>();
            java.util.Set<Integer> visited = new java.util.HashSet<>();
            BlockEntry cur = root;
            while (cur != null && visited.add(cur.id)) {
                ordered.add(cur);
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
        /** Reference back to the JsonObject for in-place mutation. */
        public final JsonObject rawJson;

        public BlockEntry(JsonObject json) {
            this.rawJson    = json;
            this.id         = getInt(json, "id",        0);
            this.opCode     = getString(json, "opCode");
            this.spec       = getString(json, "spec");
            this.type       = getString(json, "type");
            this.typeName   = getString(json, "typeName");
            this.color      = getInt(json, "color",     0);
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

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("[id=").append(id).append("] ");
            sb.append(opCode.isEmpty() ? spec : opCode);
            if (!parameters.isEmpty()) sb.append(" params=").append(parameters);
            if (nextBlock != -1) sb.append(" →").append(nextBlock);
            if (subStack1 != -1) sb.append(" sub1→").append(subStack1);
            if (subStack2 != -1) sb.append(" sub2→").append(subStack2);
            return sb.toString();
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Reads and parses the logic file for a project.
     * Handles both the native Sketchware "@Section\n{JSON}" format and
     * a fallback JSON-array format.
     */
    public static LogicFile read(File logicFile, String scId) {
        if (!logicFile.exists()) return new LogicFile(scId);
        try {
            String raw = readDecrypted(logicFile);
            if (raw == null || raw.trim().isEmpty()) return new LogicFile(scId);
            raw = raw.trim();

            if (raw.startsWith("@")) return parseAtFormat(raw, scId);  // native format
            if (raw.startsWith("[")) {                                  // legacy JSON array
                try {
                    return parse(JsonParser.parseString(raw).getAsJsonArray(), scId);
                } catch (JsonSyntaxException | IllegalStateException ignored) {}
            }
            return new LogicFile(scId);
        } catch (IOException e) {
            return null;
        }
    }

    /** Human-readable summary of all events grouped by activity. */
    public static String summarize(LogicFile lf) {
        if (lf == null)           return "Error: could not read logic file.";
        if (lf.events.isEmpty())  return "Logic file is empty — no events defined yet.";

        StringBuilder sb = new StringBuilder();
        sb.append("Project ").append(lf.scId).append(" — ").append(lf.events.size()).append(" event(s):\n");

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
     * Serialises a LogicFile to the native "@Section\n{JSON}" format.
     * This is the format Sketchware reads at runtime.
     */
    public static String serialise(LogicFile lf) {
        StringBuilder sb = new StringBuilder();
        for (EventEntry ev : lf.events) {
            sb.append("@").append(ev.name).append("\n");
            for (JsonElement el : ev.rawContent) {
                sb.append(el.toString()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    // ── Parsing ───────────────────────────────────────────────────────────

    /** Parses the native "@Section\n{JSON}" format. */
    private static LogicFile parseAtFormat(String raw, String scId) {
        LogicFile lf = new LogicFile(scId);
        // Split on lines that begin with '@', keeping the delimiter
        String[] lines = raw.split("\r?\n");
        EventEntry current = null;

        for (String line : lines) {
            if (line.startsWith("@")) {
                // Start of a new section
                String sectionName = line.substring(1).trim();
                if (!sectionName.contains(".java_")) continue; // skip non-logic sections
                JsonArray contentArray = new JsonArray();
                current = new EventEntry(sectionName, contentArray);
                lf.events.add(current);
            } else if (current != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || !trimmed.startsWith("{")) continue;
                try {
                    JsonObject blockJson = JsonParser.parseString(trimmed).getAsJsonObject();
                    current.rawContent.add(blockJson);
                    BlockEntry block = new BlockEntry(blockJson);
                    current.blocksById.put(block.id, block);
                } catch (JsonSyntaxException | IllegalStateException ignored) {}
            }
        }
        return lf;
    }

    /** Parses the legacy JSON array format. */
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
     * Public wrapper around {@link #readDecrypted(File)} for callers outside
     * this package. Added Phase 3 for {@code pro.sketchware.ai.engine.diff.BlockDiffSupport},
     * which needs the raw pre/post-mutation logic file text to compute a diff —
     * it cannot call the package-private {@link #readDecrypted(File)} directly.
     * Behaviour is identical to {@link #readDecrypted(File)}; this is a
     * visibility-only wrapper, no logic duplicated or changed.
     */
    public static String readDecryptedPublic(File f) throws IOException {
        return readDecrypted(f);
    }

    /**
     * Decrypts the logic file using Sketchware's AES/CBC/PKCS5 scheme
     * (key = IV = "sketchwaresecure").
     */
    static String readDecrypted(File f) throws IOException {
        if (!f.exists() || f.length() == 0) return "";
        // Extract scId from path: .../.sketchware/data/{scId}/logic
        String absPath = f.getAbsolutePath().replace("\\", "/");
        String[] parts = absPath.split("/");
        String scId = null;
        String relPath = null;
        for (int i = 0; i < parts.length - 1; i++) {
            if ("data".equals(parts[i]) && i + 1 < parts.length) {
                scId    = parts[i + 1];
                relPath = parts[parts.length - 1];
                break;
            }
        }
        if (scId != null && relPath != null) {
            String decrypted = SketchwareFileDecryptor.decryptFile(scId, relPath);
            if (decrypted != null && !decrypted.isEmpty()) return decrypted;
        }
        // Fallback: plain text
        byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
