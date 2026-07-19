package dev.aldi.sayuti.block;

import android.os.Environment;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import mod.hey.studios.util.Helper;
import mod.hilal.saif.blocks.BlocksHandler;
import pro.sketchware.utility.FileUtil;

public class ExtraBlockFile {

    public static final File EXTRA_BLOCKS_DATA_FILE = new File(Environment.getExternalStorageDirectory(),
            ".sketchware/resources/block/My Block/block.json");
    public static final File EXTRA_BLOCKS_PALETTE_FILE = new File(Environment.getExternalStorageDirectory(),
            ".sketchware/resources/block/My Block/palette.json");

    public static ArrayList<HashMap<String, Object>> buildInBlocks = new ArrayList<>();

    // ── Cache fields ─────────────────────────────────────────────────────────
    private static ArrayList<HashMap<String, Object>> cachedExtraBlockData = null;
    private static long cachedFileLastModified = -1L;

    /**
     * Returns merged list of user-defined extra blocks + built-in blocks.
     *
     * Results are cached and only rebuilt when block.json changes on disk,
     * so repeated calls (e.g. every palette switch) are instant.
     */
    public static ArrayList<HashMap<String, Object>> getExtraBlockData() {
        long lastModified = EXTRA_BLOCKS_DATA_FILE.exists()
                ? EXTRA_BLOCKS_DATA_FILE.lastModified()
                : 0L;

        if (cachedExtraBlockData != null && lastModified == cachedFileLastModified) {
            return cachedExtraBlockData;
        }

        // File changed or first run — rebuild
        ArrayList<HashMap<String, Object>> extraBlocks =
                new Gson().fromJson(getExtraBlockFile(), Helper.TYPE_MAP_LIST);

        if (buildInBlocks.isEmpty()) {
            // Build built-in blocks once (they never change at runtime)
            BlocksHandler.builtInBlocks(buildInBlocks);
        }
        extraBlocks.addAll(buildInBlocks);

        cachedExtraBlockData = extraBlocks;
        cachedFileLastModified = lastModified;
        return cachedExtraBlockData;
    }

    /**
     * Call this after the user saves/deletes custom blocks so the cache
     * is invalidated and rebuilt on the next palette open.
     */
    public static void invalidateCache() {
        cachedExtraBlockData = null;
        cachedFileLastModified = -1L;
    }

    /**
     * @return Non-empty content of {@link ExtraBlockFile#EXTRA_BLOCKS_DATA_FILE},
     * as cases of {@code ""} as file content return {@code "[]"}
     */
    public static String getExtraBlockFile() {
        String fileContent;
        if (EXTRA_BLOCKS_DATA_FILE.exists()
                && !(fileContent = FileUtil.readFile(EXTRA_BLOCKS_DATA_FILE.getAbsolutePath())).isEmpty()) {
            return fileContent;
        }
        return "[]";
    }

    public static String getPaletteBlockFile() {
        return FileUtil.readFile(EXTRA_BLOCKS_PALETTE_FILE.getAbsolutePath());
    }

    public static String getExtraBlockJson() {
        return "[]";
    }
}
