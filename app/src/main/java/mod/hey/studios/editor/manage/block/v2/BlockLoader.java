package mod.hey.studios.editor.manage.block.v2;

import static com.google.android.material.color.MaterialColors.harmonizeWithPrimary;

import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.view.ContextThemeWrapper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import dev.aldi.sayuti.block.ExtraBlockFile;
import mod.agus.jcoderz.editor.manage.block.palette.PaletteSelector;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import mod.jbk.util.LogUtil;
import pro.sketchware.R;
import pro.sketchware.SketchApplication;
import pro.sketchware.compiler.CustomBlockDefinitionValidator;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

/**
 * An optimized Custom Blocks loader.
 *
 * @since v6.3.0
 */
public class BlockLoader {

    private static ArrayList<ExtraBlockInfo> blocks;

    static {
        loadCustomBlocks();
    }

    public static ExtraBlockInfo getBlockInfo(String block_name) {
        if (blocks == null) {
            loadCustomBlocks();
        }

        for (ExtraBlockInfo info : blocks) {
            if (info.getName().equals(block_name)) {
                return info;
            }
        }

        ExtraBlockInfo in = new ExtraBlockInfo();
        in.setName(block_name);
        in.isMissing = true;
        return in;
    }

    public static ExtraBlockInfo getBlockFromProject(String sc_id, String block_name) {
        for (ExtraBlockInfo info : loadProjectBlocks(sc_id)) {
            if (block_name.equals(info.getName())) {
                return info;
            }
        }

        ExtraBlockInfo in = new ExtraBlockInfo();
        in.setName(block_name);
        in.isMissing = true;
        return in;
    }

    public static ArrayList<ExtraBlockInfo> loadProjectBlocks(String sc_id) {
        ArrayList<ExtraBlockInfo> result = new ArrayList<>();
        File customBlocksConfig = new File(Environment.getExternalStorageDirectory(),
                ".sketchware/data/" + sc_id + "/custom_blocks");
        if (!customBlocksConfig.exists()) {
            return result;
        }

        try {
            ArrayList<ExtraBlockInfo> extraBlocks = new Gson().fromJson(
                    FileUtil.readFile(customBlocksConfig.getAbsolutePath()),
                    new TypeToken<ArrayList<ExtraBlockInfo>>() {
                    }.getType());
            if (extraBlocks == null) {
                return result;
            }

            for (ExtraBlockInfo info : extraBlocks) {
                if (info == null) {
                    continue;
                }
                applyValidation(info);
                result.add(info);
            }
        } catch (Exception e) {
            SketchwareUtil.toastError("Failed to get Custom Blocks for project " + sc_id + ": " + e.getMessage());
        }

        return result;
    }

    private static void loadCustomBlocks() {
        ArrayList<HashMap<String, Object>> palettes = new PaletteSelector().getPaletteSelector();

        blocks = new ArrayList<>();

        ArrayList<HashMap<String, Object>> arrList = ExtraBlockFile.getExtraBlockData();

        for (int i = 0; i < arrList.size(); i++) {
            HashMap<String, Object> map = arrList.get(i);

            if (!map.containsKey("name")) {
                continue;
            }

            ExtraBlockInfo info = new ExtraBlockInfo();

            Object name = map.get("name");

            if (name instanceof String) {
                info.setName((String) name);
            } else {
                info.setName("");
                SketchwareUtil.toastError("Invalid name entry in Custom Block #" + (i + 1));
                continue;
            }

            if (map.containsKey("spec")) {
                Object spec = map.get("spec");

                if (spec instanceof String) {
                    info.setSpec((String) spec);
                }
            }

            if (map.containsKey("spec2")) {
                Object spec2 = map.get("spec2");

                if (spec2 instanceof String) {
                    info.setSpec2((String) spec2);
                }
            }

            if (map.containsKey("type")) {
                Object type = map.get("type");
                if (type instanceof String) {
                    info.setType((String) type);
                }
            }

            if (map.containsKey("typeName")) {
                Object typeName = map.get("typeName");
                if (typeName instanceof String) {
                    info.setTypeName((String) typeName);
                }
            }

            if (map.containsKey("code")) {
                Object code = map.get("code");

                if (code instanceof String) {
                    info.setCode((String) code);
                }
            }

            if (map.containsKey("color")) {
                Object color = map.get("color");

                if (color instanceof String) {
                    try {
                        Context context = new ContextThemeWrapper(SketchApplication.getContext(), R.style.Theme_SketchwarePro);
                        int harmonizedColor = harmonizeWithPrimary(context, Color.parseColor((String) color));
                        info.setColor(harmonizedColor);
                    } catch (IllegalArgumentException e) {
                        SketchwareUtil.toastError("Invalid color in Custom Block #" + (i + 1));
                        continue;
                    }
                }
            } else {
                if (!map.containsKey("palette")) {
                    continue;
                } else {
                    Object mapPalette = map.get("palette");
                    if (mapPalette instanceof String) {
                        try {
                            int mapPaletteNumber = Integer.parseInt((String) mapPalette);

                            for (int j = 0, palettesSize = palettes.size(); j < palettesSize; j++) {
                                HashMap<String, Object> palette = palettes.get(j);
                                Object paletteIndex = palette.get("index");

                                if (paletteIndex instanceof Integer) {
                                    int indexInt = (Integer) paletteIndex;

                                    if (mapPaletteNumber == indexInt) {
                                        Object paletteColor = palette.get("color");

                                        if (paletteColor instanceof Integer) {
                                            try {
                                                info.setPaletteColor((Integer) paletteColor);
                                            } catch (IllegalArgumentException e) {
                                                SketchwareUtil.toastError("Invalid color in Custom Block palette #" + (j + 1));
                                            }
                                        } else {
                                            SketchwareUtil.toastError("Invalid color value type in Custom Block palette #" + (j + 1));
                                        }
                                    }
                                } else {
                                    SketchwareUtil.toastError("Invalid palette index value type in Custom Block palette #" + (j + 1));
                                }
                            }
                        } catch (NumberFormatException e) {
                            SketchwareUtil.toastError("Invalid palette number in Custom Block #" + (i + 1));
                            continue;
                        }
                    } else {
                        SketchwareUtil.toastError("Invalid palette number value type in Custom Block #" + (i + 1));
                        continue;
                    }
                }
            }

            applyValidation(info);
            blocks.add(info);
        }
    }

    private static void applyValidation(ExtraBlockInfo info) {
        info.setType(CustomBlockDefinitionValidator.normalizeType(info.getType()));
        info.setValidationError(CustomBlockDefinitionValidator.validate(
                info.getName(),
                info.getSpec(),
                info.getCode(),
                info.getType()
        ));
    }

    /**
     * Still used in {@link a.a.a.Rs}, so it must exist (for now).
     */
    public static void log(String message) {
        LogUtil.d("BlockLoader", message);
    }

    public static void refresh() {
        loadCustomBlocks();
    }
}
