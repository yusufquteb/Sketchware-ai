package pro.sketchware.ai.tools.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * BlockLogicWriter — Phase 4 Block Logic API
 *
 * Provides safe write, add, modify, and delete operations on Sketchware Pro
 * .logic files. Every mutation goes through:
 *   1. Validation — checks structural integrity before writing
 *   2. Backup   — saves previous state to the undo stack
 *   3. Write    — serialises updated LogicFile to disk
 *
 * Undo stack is per-instance and scoped to one writing session.
 */
public final class BlockLogicWriter {

    /** Max undo levels kept in memory. */
    private static final int MAX_UNDO = 20;

    private final File logicFile;
    private final String scId;
    private final Deque<String> undoStack = new ArrayDeque<>();

    public BlockLogicWriter(File logicFile, String scId) {
        this.logicFile = logicFile;
        this.scId      = scId;
    }

    // ── Public write API ──────────────────────────────────────────────────

    /**
     * Writes the entire LogicFile back to disk after validation.
     * Pushes the previous state onto the undo stack.
     *
     * @return null on success, or an error message string on failure
     */
    public String write(BlockLogicReader.LogicFile lf) {
        String validationError = validate(lf);
        if (validationError != null) return "Validation error: " + validationError;
        try {
            pushUndo();
            String serialised = BlockLogicReader.serialise(lf);
            writeRaw(serialised);
            return null;
        } catch (IOException e) {
            return "Write error: " + e.getMessage();
        }
    }

    /**
     * Adds a new block to an event's block chain.
     *
     * @param lf          the loaded LogicFile to modify in-place
     * @param eventName   full event name, e.g. "MainActivity.java_onCreate"
     * @param blockJson   JSON object describing the block (opCode, spec, type, parameters…)
     * @param afterBlockId insert after this block id, or -1 to prepend
     * @return null on success, error message on failure
     */
    public String addBlock(BlockLogicReader.LogicFile lf, String eventName,
                           JsonObject blockJson, int afterBlockId) {
        BlockLogicReader.EventEntry ev = lf.findEvent(eventName);
        if (ev == null) return "Event not found: " + eventName;

        // Assign a new unique id
        int newId = nextId(ev);
        blockJson.addProperty("id",        newId);
        blockJson.addProperty("nextBlock", -1);
        if (!blockJson.has("subStack1"))  blockJson.addProperty("subStack1", -1);
        if (!blockJson.has("subStack2"))  blockJson.addProperty("subStack2", -1);
        if (!blockJson.has("parameters")) blockJson.add("parameters", new JsonArray());
        if (!blockJson.has("color"))      blockJson.addProperty("color", -10701022);
        if (!blockJson.has("typeName"))   blockJson.addProperty("typeName", "");

        if (afterBlockId == -1) {
            // Prepend: find current first block and set its id as new block's nextBlock
            List<BlockLogicReader.BlockEntry> ordered = ev.orderedBlocks();
            if (!ordered.isEmpty()) {
                blockJson.addProperty("nextBlock", ordered.get(0).id);
            }
        } else {
            // Insert after afterBlockId
            BlockLogicReader.BlockEntry after = ev.blocksById.get(afterBlockId);
            if (after == null) return "afterBlockId not found: " + afterBlockId;
            int oldNext = after.nextBlock;
            blockJson.addProperty("nextBlock", oldNext);
            // Patch after.rawJson to point to newId
            after.rawJson.addProperty("nextBlock", newId);
        }

        ev.rawContent.add(blockJson);
        // Keep blocksById in sync so validate() and subsequent orderedBlocks() see the new block.
        BlockLogicReader.BlockEntry newEntry = new BlockLogicReader.BlockEntry(blockJson);
        ev.blocksById.put(newEntry.id, newEntry);
        return write(lf);
    }

    /**
     * Modifies specific fields of an existing block.
     *
     * @param lf        the loaded LogicFile
     * @param eventName full event name
     * @param blockId   id of the block to modify
     * @param patch     JSON object with fields to update (e.g. {"spec": "...", "parameters": [...]})
     * @return null on success, error message on failure
     */
    public String modifyBlock(BlockLogicReader.LogicFile lf, String eventName,
                              int blockId, JsonObject patch) {
        BlockLogicReader.EventEntry ev = lf.findEvent(eventName);
        if (ev == null) return "Event not found: " + eventName;
        BlockLogicReader.BlockEntry block = ev.blocksById.get(blockId);
        if (block == null) return "Block not found: id=" + blockId;

        // Apply patch to rawJson
        for (String key : new ArrayList<>(patch.keySet())) {
            block.rawJson.add(key, patch.get(key));
        }
        return write(lf);
    }

    /**
     * Deletes a block and repairs the chain (previous block's nextBlock is updated).
     *
     * @param lf        the loaded LogicFile
     * @param eventName full event name
     * @param blockId   id of the block to delete
     * @return null on success, error message on failure
     */
    public String deleteBlock(BlockLogicReader.LogicFile lf, String eventName, int blockId) {
        BlockLogicReader.EventEntry ev = lf.findEvent(eventName);
        if (ev == null) return "Event not found: " + eventName;
        BlockLogicReader.BlockEntry toDelete = ev.blocksById.get(blockId);
        if (toDelete == null) return "Block not found: id=" + blockId;

        int successor = toDelete.nextBlock;

        // Find any block whose nextBlock, subStack1, or subStack2 points to this block
        for (BlockLogicReader.BlockEntry b : ev.blocksById.values()) {
            if (b.id == blockId) continue;
            if (b.nextBlock == blockId)  b.rawJson.addProperty("nextBlock",  successor);
            if (b.subStack1 == blockId)  b.rawJson.addProperty("subStack1",  successor);
            if (b.subStack2 == blockId)  b.rawJson.addProperty("subStack2",  successor);
        }

        // Remove from rawContent
        for (int i = 0; i < ev.rawContent.size(); i++) {
            JsonElement el = ev.rawContent.get(i);
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("id") && obj.get("id").getAsInt() == blockId) {
                    ev.rawContent.remove(i);
                    break;
                }
            }
        }
        ev.blocksById.remove(blockId);
        return write(lf);
    }

    /**
     * Creates a new event entry in the logic file (e.g. a new moreblock definition).
     *
     * @param lf        the loaded LogicFile
     * @param eventName full event name, e.g. "MainActivity.java_myFunction"
     * @return null on success, error message on failure
     */
    public String createEvent(BlockLogicReader.LogicFile lf, String eventName) {
        if (lf.findEvent(eventName) != null) return "Event already exists: " + eventName;
        BlockLogicReader.EventEntry ev = new BlockLogicReader.EventEntry(eventName, new JsonArray());
        lf.events.add(ev);
        return write(lf);
    }

    /**
     * Deletes an entire event entry and all its blocks.
     *
     * @param lf        the loaded LogicFile
     * @param eventName full event name to delete
     * @return null on success, error message on failure
     */
    public String deleteEvent(BlockLogicReader.LogicFile lf, String eventName) {
        BlockLogicReader.EventEntry ev = lf.findEvent(eventName);
        if (ev == null) return "Event not found: " + eventName;
        lf.events.remove(ev);
        return write(lf);
    }

    // ── Undo ─────────────────────────────────────────────────────────────

    /**
     * Reverts the logic file to the previous state.
     *
     * @return null on success, error message on failure
     */
    public String undo() {
        if (undoStack.isEmpty()) return "Nothing to undo.";
        String previous = undoStack.pop();
        try {
            writeRaw(previous);
            return null;
        } catch (IOException e) {
            return "Undo write error: " + e.getMessage();
        }
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }

    // ── Validation ────────────────────────────────────────────────────────

    /**
     * Validates the structural integrity of a LogicFile before writing.
     *
     * Checks:
     * - No duplicate event names
     * - No duplicate block ids within an event
     * - No dangling nextBlock / subStack references
     * - Block id >= 1
     *
     * @return null if valid, or a description of the first problem found
     */
    public static String validate(BlockLogicReader.LogicFile lf) {
        if (lf == null) return "LogicFile is null";
        java.util.Set<String> eventNames = new java.util.HashSet<>();
        for (BlockLogicReader.EventEntry ev : lf.events) {
            if (!eventNames.add(ev.name)) return "Duplicate event name: " + ev.name;
            java.util.Set<Integer> ids = new java.util.HashSet<>();
            for (BlockLogicReader.BlockEntry b : ev.blocksById.values()) {
                if (b.id < 1) return "Invalid block id " + b.id + " in event " + ev.name;
                if (!ids.add(b.id)) return "Duplicate block id " + b.id + " in event " + ev.name;
            }
            // Check references
            for (BlockLogicReader.BlockEntry b : ev.blocksById.values()) {
                if (b.nextBlock != -1 && !ev.blocksById.containsKey(b.nextBlock))
                    return "Block " + b.id + " nextBlock=" + b.nextBlock + " not found in " + ev.name;
                if (b.subStack1 != -1 && !ev.blocksById.containsKey(b.subStack1))
                    return "Block " + b.id + " subStack1=" + b.subStack1 + " not found in " + ev.name;
                if (b.subStack2 != -1 && !ev.blocksById.containsKey(b.subStack2))
                    return "Block " + b.id + " subStack2=" + b.subStack2 + " not found in " + ev.name;
            }
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private int nextId(BlockLogicReader.EventEntry ev) {
        int max = 0;
        for (int id : ev.blocksById.keySet()) if (id > max) max = id;
        return max + 1;
    }

    private void pushUndo() throws IOException {
        if (logicFile.exists()) {
            // Create a binary backup file alongside the logic file for UndoBlocksTool.
            // The backup stores the raw encrypted bytes, so restore is trivial.
            File backup = new File(logicFile.getParentFile(), "logic.bak");
            if (!backup.exists()) {
                // Only create backup once (before the first write in this session).
                byte[] raw = java.nio.file.Files.readAllBytes(logicFile.toPath());
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(backup, "rw")) {
                    raf.setLength(0);
                    raf.write(raw);
                }
            }
            // Also push decrypted snapshot to the in-memory undo stack
            String snapshot = "";
            try {
                snapshot = BlockLogicReader.readDecrypted(logicFile);
            } catch (IOException ignored) {}
            if (snapshot == null) snapshot = "";
            undoStack.push(snapshot);
            if (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        }
    }

    private void writeRaw(String content) throws IOException {
        try {
            byte[] key = "sketchwaresecure".getBytes(StandardCharsets.UTF_8);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.IvParameterSpec(key));
            byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            File parent = logicFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logicFile, "rw")) {
                raf.setLength(0);
                raf.write(encrypted);
            }
            // jC.a() reloads Sketchware's in-memory project cache so the Design
            // editor reflects the change immediately. It must run on the main thread.
            new Handler(Looper.getMainLooper()).post(() -> {
                try { a.a.a.jC.a(scId, true); } catch (Throwable ignored) {}
            });
        } catch (Exception e) {
            throw new IOException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reads the logic file for undo snapshot using IA's proven decryptor.
     */
    String readRaw() throws IOException {
        return BlockLogicReader.read(logicFile, scId) != null
                ? BlockLogicReader.serialise(BlockLogicReader.read(logicFile, scId))
                : "[]";
    }
}
