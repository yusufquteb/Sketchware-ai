// nikit overhaul — crash fix — 2026-05
// Fix: StringIndexOutOfBoundsException in getVariableBlock when spec has only one dot
//   Crash: begin 3, end 2, length 7
//   Root cause: spec.substring(indexOf(".")+1, lastIndexOf(".")) with a single-dot spec
//   produces begin > end (e.g. %m.abcde → substring(3,2)).
package mod.jbk.util;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import a.a.a.FB;
import a.a.a.Rs;
import a.a.a.Ts;
import a.a.a.kq;
import mod.hey.studios.moreblock.ReturnMoreblockManager;

public class BlockUtil {
    public static void loadMoreblockPreview(ViewGroup blockArea, String spec) {
        var moreblock = new Rs(blockArea.getContext(), 0, ReturnMoreblockManager.getMbName(spec), ReturnMoreblockManager.getMoreblockType(spec), "definedFunc");
        blockArea.addView(moreblock);

        loadPreviewBlockVariables(blockArea, moreblock, spec);
        moreblock.k();
    }

    /**
     * Loads the Variable Blocks of a Block that's for preview only.
     */
    public static void loadPreviewBlockVariables(ViewGroup blockArea, Rs previewBlock, String spec) {
        int id = 0;
        for (var specPart : FB.c(spec)) {
            if (specPart.charAt(0) != '%') {
                continue;
            }

            var variable = getVariableBlock(blockArea.getContext(), id + 1, specPart, "getVar");
            if (variable != null) {
                blockArea.addView(variable);
                previewBlock.a((Ts) previewBlock.V.get(id), variable);
                id++;
            }
        }
    }

    /**
     * @param opCode Block op code like <code>"getArg"</code> (used in Events' heading/start Block)
     *               or <code>"getVar"</code> (type of Blocks in the Palette)
     * @return The Variable Block that's part of for example a MoreBlock or an Event,
     * or <code>null</code> if its spec wasn't recognized.
     */
    @Nullable
    public static Rs getVariableBlock(Context context, int id, String spec, String opCode) {
        if (spec == null || spec.length() < 2) return null;

        var type = spec.charAt(1);
        return switch (type) {
            case 'b', 'd', 's' -> {
                // Guard: spec must be at least 4 chars to safely call substring(3)
                // Format: %X<space><content> — minimum valid spec is "%b x" (4 chars)
                String label = spec.length() > 3 ? spec.substring(3) : "";
                yield new Rs(context, id, label, Character.toString(type), opCode);
            }
            case 'm' -> {
                // Format: %m.<TypeClass>.<subtype>  (two dots)
                // Crash when only one dot: indexOf(".") == lastIndexOf(".")
                // → substring(indexOf(".")+1, lastIndexOf(".")) becomes substring(N+1, N) → crash
                int firstDot = spec.indexOf(".");
                int lastDot  = spec.lastIndexOf(".");

                if (firstDot < 0) {
                    // No dot at all — malformed spec, skip gracefully
                    yield null;
                }

                String specLast  = spec.substring(lastDot + 1);
                // Only produce a non-empty specFirst when there are TWO distinct dots
                String specFirst = (firstDot < lastDot)
                        ? spec.substring(firstDot + 1, lastDot)
                        : "";   // ← single-dot spec: no middle segment, use empty string
                yield new Rs(context, id, specLast, kq.a(specFirst), kq.b(specFirst), opCode);
            }
            default -> null;
        };
    }
}
