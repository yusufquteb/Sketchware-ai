package mod.hey.studios.editor.manage.block.v2;

import static com.google.android.material.color.MaterialColors.harmonizeWithPrimary;

import android.content.Context;
import android.graphics.Color;
import android.view.ContextThemeWrapper;

import pro.sketchware.R;
import pro.sketchware.SketchApplication;

/**
 * Utility for resolving block color/palette values from Gson-deserialized data.
 *
 * <p>Gson deserializes all JSON numbers into {@code Double} when the target type is
 * {@code Object} (e.g., inside a {@code HashMap<String, Object>}). This means a JSON
 * integer color like {@code -16777216} arrives as {@code Double(-16777216.0)}, not
 * {@code Integer}. Similarly, palette indices like {@code 7} arrive as {@code Double(7.0)}.
 *
 * <p>All parsing methods in this class accept every numeric type that Gson may produce
 * ({@code Double}, {@code Long}, {@code Integer}) as well as {@code String} (hex notation
 * like {@code "#4A6CD4"} for colors, or numeric strings like {@code "7"} for indices).
 *
 * @since v6.3.1
 */
public final class BlockColorUtil {

    private BlockColorUtil() {
    }

    /**
     * Parses a raw ARGB color from a Gson-deserialized block-map value.
     *
     * <p>Accepted input types:
     * <ul>
     *   <li>{@code String}  — hex notation, e.g. {@code "#4A6CD4"} or {@code "#FF4A6CD4"}</li>
     *   <li>{@code Double}  — numeric ARGB int stored as a JSON number (Gson default for Object)</li>
     *   <li>{@code Long}    — numeric ARGB int on platforms that widen to Long</li>
     *   <li>{@code Integer} — numeric ARGB int already narrowed correctly</li>
     * </ul>
     *
     * @param colorObj the raw value from the deserialized map
     * @return the ARGB int color
     * @throws IllegalArgumentException if {@code colorObj} is {@code null}, an unknown type,
     *                                  or a String that {@link Color#parseColor} cannot parse
     */
    public static int parseRawColor(Object colorObj) throws IllegalArgumentException {
        if (colorObj instanceof String) {
            // May throw IllegalArgumentException for malformed hex — let the caller handle it.
            return Color.parseColor((String) colorObj);
        } else if (colorObj instanceof Double) {
            return ((Double) colorObj).intValue();
        } else if (colorObj instanceof Long) {
            return ((Long) colorObj).intValue();
        } else if (colorObj instanceof Integer) {
            return (Integer) colorObj;
        }
        throw new IllegalArgumentException(
                "Unsupported color value type: "
                        + (colorObj == null ? "null" : colorObj.getClass().getSimpleName()));
    }

    /**
     * Parses a palette or palette-index number from a Gson-deserialized block-map value.
     *
     * <p>Accepted input types: {@code String} (e.g. {@code "7"}), {@code Double}, {@code Long},
     * {@code Integer}.
     *
     * @param paletteObj the raw value from the deserialized map
     * @return the palette/index int
     * @throws IllegalArgumentException if {@code paletteObj} is {@code null} or an unknown type
     * @throws NumberFormatException    if {@code paletteObj} is a String that is not a valid int
     */
    public static int parsePaletteNumber(Object paletteObj)
            throws IllegalArgumentException, NumberFormatException {
        if (paletteObj instanceof String) {
            return Integer.parseInt((String) paletteObj);
        } else if (paletteObj instanceof Double) {
            return ((Double) paletteObj).intValue();
        } else if (paletteObj instanceof Long) {
            return ((Long) paletteObj).intValue();
        } else if (paletteObj instanceof Integer) {
            return (Integer) paletteObj;
        }
        throw new IllegalArgumentException(
                "Unsupported palette value type: "
                        + (paletteObj == null ? "null" : paletteObj.getClass().getSimpleName()));
    }

    /**
     * Applies Material Color Harmonization against the app's primary color.
     *
     * @param rawColor the ARGB int to harmonize
     * @return the harmonized ARGB int
     */
    public static int harmonize(int rawColor) {
        Context context = new ContextThemeWrapper(
                SketchApplication.getContext(), R.style.Theme_SketchwarePro);
        return harmonizeWithPrimary(context, rawColor);
    }
}
