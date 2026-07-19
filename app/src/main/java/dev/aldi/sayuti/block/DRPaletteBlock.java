package dev.aldi.sayuti.block;

import com.besome.sketch.editor.LogicEditorActivity;

public class DRPaletteBlock {

    public static void addViewBlocks(LogicEditorActivity logicEditor) {
        logicEditor.a(" ", "setBackgroundResource");
    }

    public static void addStringBlocks(LogicEditorActivity logicEditor) {
        logicEditor.a(" ", "concatenateVarString");
    }

    public static void addStringOperatorBlocks(LogicEditorActivity logicEditor) {
        // placeholder - no active blocks currently
    }

    public static void addSharedPreferencesBlocks(LogicEditorActivity logicEditor) {
        // original blocks
        logicEditor.a("b", "getBooleanSharedPreferences");
        logicEditor.a(" ", "putBooleanSharedPreferences");
        logicEditor.a("d", "getIntSharedPreferences");
        logicEditor.a(" ", "putIntSharedPreferences");
        // added: missing SharedPreferences suite
        logicEditor.a("b", "containsSharedPreferences");
        logicEditor.a("s", "getDataSharedPreferences");
        logicEditor.a(" ", "setDataSharedPreferences");
        logicEditor.a(" ", "removeDataSharedPreferences");
    }

    public static void addBasicComponentBlocks(LogicEditorActivity logicEditor) {
        // original blocks
        logicEditor.a("b", "intentGetBoolean");
        logicEditor.a("d", "intentGetDouble");
        // added: missing getExtra String overload
        logicEditor.a("s", "intentGetString");
    }

    public static void addIntentPutExtraBlocks(LogicEditorActivity logicEditor) {
        // original blocks
        logicEditor.a(" ", "intentPutExtraBoolean");
        logicEditor.a(" ", "intentPutExtraDouble");
        // added: missing putExtra String overload
        logicEditor.a(" ", "intentPutExtraString");
    }
}
