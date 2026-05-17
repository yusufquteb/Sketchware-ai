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
        logicEditor.a("b", "getBooleanSharedPreferences");
        logicEditor.a(" ", "putBooleanSharedPreferences");
        logicEditor.a("d", "getIntSharedPreferences");
        logicEditor.a(" ", "putIntSharedPreferences");
    }

    public static void addBasicComponentBlocks(LogicEditorActivity logicEditor) {
        logicEditor.a("b", "intentGetBoolean");
        logicEditor.a("d", "intentGetDouble");
    }

    public static void addIntentPutExtraBlocks(LogicEditorActivity logicEditor) {
        logicEditor.a(" ", "intentPutExtraBoolean");
        logicEditor.a(" ", "intentPutExtraDouble");
    }
}
