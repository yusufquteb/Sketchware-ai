package a.a.a;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Block opCode ↔ resource name and parameter spec mapper.
 * Replaces the original obfuscated lq.class from a.a.a-important-classes.jar.
 *
 * lq.a(opCode)    → ArrayList<String> param type specs for a block  e.g. ["%m.view", "%s"]
 * lq.b(eventName) → ArrayList<String> param type specs for an event root spec
 * lq.c(eventName) → snake_case suffix for root_spec_* string resources
 * lq.d(opCode)    → snake_case suffix for block_* string resources
 *
 * Usage by xB: context.getString(R.string."block_" + lq.d(opCode))
 */
public class lq {

    private static final HashMap<String, String[]> BLOCK_PARAMS  = new HashMap<>();
    private static final HashMap<String, String[]> EVENT_PARAMS  = new HashMap<>();
    private static final HashMap<String, String>   BLOCK_SNAKE   = new HashMap<>();
    private static final HashMap<String, String>   EVENT_SNAKE   = new HashMap<>();

    static {
        initBlockParams();
        initEventParams();
        initBlockSnake();
        initEventSnake();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void initBlockParams() {
        // Lists
        BLOCK_PARAMS.put("addListInt",        new String[]{ "%m.listInt", "%d" });
        BLOCK_PARAMS.put("addListStr",        new String[]{ "%m.listStr", "%s" });
        BLOCK_PARAMS.put("addListMap",        new String[]{ "%s", "%m.varMap", "%m.listMap" });
        BLOCK_PARAMS.put("addMapToList",      new String[]{ "%m.varMap", "%m.listMap" });
        BLOCK_PARAMS.put("clearList",         new String[]{ "%m.list" });
        BLOCK_PARAMS.put("deleteList",        new String[]{ "%d", "%m.list" });
        BLOCK_PARAMS.put("lengthList",        new String[]{ "%m.list" });
        BLOCK_PARAMS.put("getAtListInt",      new String[]{ "%d", "%m.listInt" });
        BLOCK_PARAMS.put("getAtListStr",      new String[]{ "%d", "%m.listStr" });
        BLOCK_PARAMS.put("getAtListMap",      new String[]{ "%d", "%s", "%m.listMap" });
        BLOCK_PARAMS.put("getMapInList",      new String[]{ "%d", "%m.listMap", "%m.varMap" });
        BLOCK_PARAMS.put("setAtListMap",      new String[]{ "%s", "%m.varMap", "%d", "%m.listMap" });
        BLOCK_PARAMS.put("indexofListInt",    new String[]{ "%d", "%m.listInt" });
        BLOCK_PARAMS.put("indexofListStr",    new String[]{ "%s", "%m.listStr" });
        BLOCK_PARAMS.put("indexListInt",      new String[]{ "%d", "%m.listInt" });
        BLOCK_PARAMS.put("indexListStr",      new String[]{ "%s", "%m.listStr" });
        BLOCK_PARAMS.put("insertListInt",     new String[]{ "%d", "%d", "%m.listInt" });
        BLOCK_PARAMS.put("insertListStr",     new String[]{ "%s", "%d", "%m.listStr" });
        BLOCK_PARAMS.put("insertListMap",     new String[]{ "%s", "%m.varMap", "%d", "%m.listMap" });
        BLOCK_PARAMS.put("insertMapToList",   new String[]{ "%m.varMap", "%d", "%m.listMap" });
        BLOCK_PARAMS.put("containListInt",    new String[]{ "%m.listInt", "%d" });
        BLOCK_PARAMS.put("containListStr",    new String[]{ "%m.listStr", "%s" });
        BLOCK_PARAMS.put("containListMap",    new String[]{ "%m.listMap", "%s" });
        BLOCK_PARAMS.put("listMapToStr",      new String[]{ "%m.listMap" });
        BLOCK_PARAMS.put("strToListMap",      new String[]{ "%s", "%m.listMap" });
        BLOCK_PARAMS.put("strToMap",          new String[]{ "%s", "%m.varMap" });
        BLOCK_PARAMS.put("listRefresh",       new String[]{ "%m.listview" });
        BLOCK_PARAMS.put("listSetData",       new String[]{ "%m.listview", "%m.listMap" });
        BLOCK_PARAMS.put("listSetCustomViewData", new String[]{ "%m.listview", "%m.listMap" });
        BLOCK_PARAMS.put("listGetCheckedCount",   new String[]{ "%m.listview" });
        BLOCK_PARAMS.put("listGetCheckedPosition",new String[]{ "%m.listview" });
        BLOCK_PARAMS.put("listGetCheckedPositions",new String[]{ "%m.listview", "%m.listInt" });
        BLOCK_PARAMS.put("listSetItemChecked",    new String[]{ "%m.listview", "%d", "%b" });
        BLOCK_PARAMS.put("listSmoothScrollTo",    new String[]{ "%m.listview", "%d" });
        BLOCK_PARAMS.put("listSmoothScrollto",    new String[]{ "%m.listview", "%d" });
        // Map
        BLOCK_PARAMS.put("mapPut",            new String[]{ "%m.varMap", "%s", "%s" });
        BLOCK_PARAMS.put("mapGet",            new String[]{ "%m.varMap", "%s" });
        BLOCK_PARAMS.put("mapRemoveKey",      new String[]{ "%m.varMap", "%s" });
        BLOCK_PARAMS.put("mapContainKey",     new String[]{ "%m.varMap", "%s" });
        BLOCK_PARAMS.put("mapGetAllKeys",     new String[]{ "%m.varMap", "%m.listStr" });
        BLOCK_PARAMS.put("mapClear",          new String[]{ "%m.varMap" });
        BLOCK_PARAMS.put("mapIsEmpty",        new String[]{ "%m.varMap" });
        BLOCK_PARAMS.put("mapSize",           new String[]{ "%m.varMap" });
        BLOCK_PARAMS.put("mapToStr",          new String[]{ "%m.varMap" });
        BLOCK_PARAMS.put("mapCreateNew",      new String[]{ "%m.varMap" });
        BLOCK_PARAMS.put("setListMap",        new String[]{ "%m.listMap", "%m.varMap" });
        // View
        BLOCK_PARAMS.put("setText",           new String[]{ "%m.textview", "%s" });
        BLOCK_PARAMS.put("getText",           new String[]{ "%m.textview" });
        BLOCK_PARAMS.put("setHint",           new String[]{ "%m.edittext", "%s" });
        BLOCK_PARAMS.put("setHintTextColor",  new String[]{ "%m.edittext", "%m.color" });
        BLOCK_PARAMS.put("setTextColor",      new String[]{ "%m.textview", "%m.color" });
        BLOCK_PARAMS.put("setImage",          new String[]{ "%m.imageview", "%m.resource" });
        BLOCK_PARAMS.put("setImageFilePath",  new String[]{ "%m.imageview", "%s" });
        BLOCK_PARAMS.put("setImageUrl",       new String[]{ "%m.imageview", "%s" });
        BLOCK_PARAMS.put("setEnable",         new String[]{ "%m.view", "%b" });
        BLOCK_PARAMS.put("getEnable",         new String[]{ "%m.view" });
        BLOCK_PARAMS.put("setVisible",        new String[]{ "%m.view", "%m.visible" });
        BLOCK_PARAMS.put("setClickable",      new String[]{ "%m.view", "%b" });
        BLOCK_PARAMS.put("setAlpha",          new String[]{ "%m.view", "%d" });
        BLOCK_PARAMS.put("getAlpha",          new String[]{ "%m.view" });
        BLOCK_PARAMS.put("setRotate",         new String[]{ "%m.view", "%d" });
        BLOCK_PARAMS.put("getRotate",         new String[]{ "%m.view" });
        BLOCK_PARAMS.put("setScaleX",         new String[]{ "%m.view", "%d" });
        BLOCK_PARAMS.put("getScaleX",         new String[]{ "%m.view" });
        BLOCK_PARAMS.put("setScaleY",         new String[]{ "%m.view", "%d" });
        BLOCK_PARAMS.put("getScaleY",         new String[]{ "%m.view" });
        BLOCK_PARAMS.put("setTranslationX",   new String[]{ "%m.view", "%d" });
        BLOCK_PARAMS.put("getTranslationX",   new String[]{ "%m.view" });
        BLOCK_PARAMS.put("setTranslationY",   new String[]{ "%m.view", "%d" });
        BLOCK_PARAMS.put("getTranslationY",   new String[]{ "%m.view" });
        BLOCK_PARAMS.put("getLocationX",      new String[]{ "%m.view" });
        BLOCK_PARAMS.put("getLocationY",      new String[]{ "%m.view" });
        BLOCK_PARAMS.put("requestFocus",      new String[]{ "%m.view" });
        BLOCK_PARAMS.put("setTypeface",       new String[]{ "%m.textview", "%m.font", "%m.method" });
        BLOCK_PARAMS.put("setBgColor",        new String[]{ "%m.view", "%m.color" });
        BLOCK_PARAMS.put("setBgResource",     new String[]{ "%m.view", "%m.resource_bg" });
        BLOCK_PARAMS.put("setColorFilter",    new String[]{ "%m.imageview", "%m.color" });
        BLOCK_PARAMS.put("viewOnClick",       new String[]{ "%m.view" });
        BLOCK_PARAMS.put("setChecked",        new String[]{ "%m.checkbox", "%b" });
        BLOCK_PARAMS.put("getChecked",        new String[]{ "%m.checkbox" });
        BLOCK_PARAMS.put("setTitle",          new String[]{ "%s" });
        // SeekBar
        BLOCK_PARAMS.put("seekBarSetProgress",new String[]{ "%m.seekbar", "%d" });
        BLOCK_PARAMS.put("seekBarGetProgress",new String[]{ "%m.seekbar" });
        BLOCK_PARAMS.put("seekBarSetMax",     new String[]{ "%m.seekbar", "%d" });
        BLOCK_PARAMS.put("seekBarGetMax",     new String[]{ "%m.seekbar" });
        BLOCK_PARAMS.put("setThumbResource",  new String[]{ "%m.seekbar", "%m.resource" });
        BLOCK_PARAMS.put("setTrackResource",  new String[]{ "%m.seekbar", "%m.resource" });
        // Spinner
        BLOCK_PARAMS.put("spnSetData",        new String[]{ "%m.spinner", "%m.listStr" });
        BLOCK_PARAMS.put("spnGetSelection",   new String[]{ "%m.spinner" });
        BLOCK_PARAMS.put("spnRefresh",        new String[]{ "%m.spinner" });
        BLOCK_PARAMS.put("spnSetSelection",   new String[]{ "%m.spinner", "%d" });
        // Progressbar
        BLOCK_PARAMS.put("progressBarSetIndeterminate", new String[]{ "%m.progressbar", "%b" });
        // Drawer
        BLOCK_PARAMS.put("openDrawer",        new String[]{});
        BLOCK_PARAMS.put("closeDrawer",       new String[]{});
        BLOCK_PARAMS.put("isDrawerOpen",      new String[]{});
        // Intent / Activity
        BLOCK_PARAMS.put("intentSetScreen",   new String[]{ "%m.intent", "%m.activity" });
        BLOCK_PARAMS.put("intentPutExtra",    new String[]{ "%m.intent", "%s", "%s" });
        BLOCK_PARAMS.put("intentGetString",   new String[]{ "%s" });
        BLOCK_PARAMS.put("intentSetAction",   new String[]{ "%m.intent", "%m.intentAction" });
        BLOCK_PARAMS.put("intentSetData",     new String[]{ "%m.intent", "%s" });
        BLOCK_PARAMS.put("intentSetFlags",    new String[]{ "%m.intent", "%m.intentFlags" });
        BLOCK_PARAMS.put("startActivity",     new String[]{ "%m.intent" });
        BLOCK_PARAMS.put("finishActivity",    new String[]{});
        // Toast / Clipboard
        BLOCK_PARAMS.put("doToast",           new String[]{ "%s" });
        BLOCK_PARAMS.put("copyToClipboard",   new String[]{ "%s" });
        // String ops
        BLOCK_PARAMS.put("stringLength",      new String[]{ "%s" });
        BLOCK_PARAMS.put("stringContains",    new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("stringEquals",      new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("stringReplace",     new String[]{ "%s", "%s", "%s" });
        BLOCK_PARAMS.put("stringReplaceAll",  new String[]{ "%s", "%s", "%s" });
        BLOCK_PARAMS.put("stringReplaceFirst",new String[]{ "%s", "%s", "%s" });
        BLOCK_PARAMS.put("stringIndex",       new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("stringLastIndex",   new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("stringSub",         new String[]{ "%s", "%d", "%d" });
        BLOCK_PARAMS.put("stringJoin",        new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("toUpperCase",       new String[]{ "%s" });
        BLOCK_PARAMS.put("toLowerCase",       new String[]{ "%s" });
        BLOCK_PARAMS.put("trim",              new String[]{ "%s" });
        BLOCK_PARAMS.put("toString",          new String[]{ "%d" });
        BLOCK_PARAMS.put("toStringWithDecimal", new String[]{ "%d" });
        BLOCK_PARAMS.put("toStringFormat",    new String[]{ "%d", "%s" });
        BLOCK_PARAMS.put("toNumber",          new String[]{ "%s" });
        // Math / Operators
        BLOCK_PARAMS.put("plus",              new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("minus",             new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("times",             new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("divide",            new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("rest",              new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("bigger",            new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("smaller",           new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("equal",             new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("and",               new String[]{ "%b", "%b" });
        BLOCK_PARAMS.put("or",                new String[]{ "%b", "%b" });
        BLOCK_PARAMS.put("not",               new String[]{ "%b" });
        BLOCK_PARAMS.put("true",              new String[]{});
        BLOCK_PARAMS.put("false",             new String[]{});
        BLOCK_PARAMS.put("mathAbs",           new String[]{ "%d" });
        BLOCK_PARAMS.put("mathSqrt",          new String[]{ "%d" });
        BLOCK_PARAMS.put("mathSin",           new String[]{ "%d" });
        BLOCK_PARAMS.put("mathCos",           new String[]{ "%d" });
        BLOCK_PARAMS.put("mathTan",           new String[]{ "%d" });
        BLOCK_PARAMS.put("mathAsin",          new String[]{ "%d" });
        BLOCK_PARAMS.put("mathAcos",          new String[]{ "%d" });
        BLOCK_PARAMS.put("mathAtan",          new String[]{ "%d" });
        BLOCK_PARAMS.put("mathExp",           new String[]{ "%d" });
        BLOCK_PARAMS.put("mathLog",           new String[]{ "%d" });
        BLOCK_PARAMS.put("mathLog10",         new String[]{ "%d" });
        BLOCK_PARAMS.put("mathCeil",          new String[]{ "%d" });
        BLOCK_PARAMS.put("mathFloor",         new String[]{ "%d" });
        BLOCK_PARAMS.put("mathRound",         new String[]{ "%d" });
        BLOCK_PARAMS.put("mathPow",           new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("mathMax",           new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("mathMin",           new String[]{ "%d", "%d" });
        BLOCK_PARAMS.put("mathPi",            new String[]{});
        BLOCK_PARAMS.put("mathE",             new String[]{});
        BLOCK_PARAMS.put("mathToDegree",      new String[]{ "%d" });
        BLOCK_PARAMS.put("mathToRadian",      new String[]{ "%d" });
        BLOCK_PARAMS.put("mathGetDip",        new String[]{ "%d" });
        BLOCK_PARAMS.put("mathGetDisplayWidth",  new String[]{});
        BLOCK_PARAMS.put("mathGetDisplayHeight", new String[]{});
        BLOCK_PARAMS.put("random",            new String[]{ "%d", "%d" });
        // Control
        BLOCK_PARAMS.put("if",                new String[]{ "%b" });
        BLOCK_PARAMS.put("ifElse",            new String[]{ "%b" });
        BLOCK_PARAMS.put("forever",           new String[]{});
        BLOCK_PARAMS.put("repeat",            new String[]{ "%d" });
        BLOCK_PARAMS.put("break",             new String[]{});
        BLOCK_PARAMS.put("else",              new String[]{});
        // Variables
        BLOCK_PARAMS.put("setVarInt",         new String[]{ "%m.varInt", "%d" });
        BLOCK_PARAMS.put("setVarStr",         new String[]{ "%m.varStr", "%s" });
        BLOCK_PARAMS.put("setVarBoolean",     new String[]{ "%m.varBool", "%b" });
        BLOCK_PARAMS.put("setVarString",      new String[]{ "%m.varStr", "%s" });  // xB alias
        BLOCK_PARAMS.put("increaseInt",       new String[]{ "%m.varInt" });
        BLOCK_PARAMS.put("decreaseInt",       new String[]{ "%m.varInt" });
        // File (SharedPrefs)
        BLOCK_PARAMS.put("fileSetData",       new String[]{ "%m.file", "%s", "%s" });
        BLOCK_PARAMS.put("fileGetData",       new String[]{ "%m.file", "%s" });
        BLOCK_PARAMS.put("fileRemoveData",    new String[]{ "%m.file", "%s" });
        // FileUtil (camelCase — Java source usage)
        BLOCK_PARAMS.put("fileutilRead",      new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilWrite",     new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("fileutilCopy",      new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("fileutilMove",      new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("fileutilDelete",    new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilMakeDir",   new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilIsExist",   new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilIsFile",    new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilIsDir",     new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilLength",    new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilListDir",   new String[]{ "%s", "%m.listStr" });
        BLOCK_PARAMS.put("fileutilStartsWith",new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("fileutilEndsWith",  new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("fileutilGetLastSegmentPath", new String[]{ "%s" });
        // FileUtil (lowercase — xB.class usage)
        BLOCK_PARAMS.put("fileutilcopy",      new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("fileutildelete",    new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilisdir",     new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilisexist",   new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilisfile",    new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutillength",    new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutillistdir",   new String[]{ "%s", "%m.listStr" });
        BLOCK_PARAMS.put("fileutilmakedir",   new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilmove",      new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("fileutilread",      new String[]{ "%s" });
        BLOCK_PARAMS.put("fileutilwrite",     new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("fileutilStartsWith",new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("fileutilEndsWith",  new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("filepickerStartPickFiles",  new String[]{ "%m.filepicker" });
        BLOCK_PARAMS.put("filepickerstartpickfiles",  new String[]{ "%m.filepicker" });
        // Dialog
        BLOCK_PARAMS.put("dialogSetTitle",    new String[]{ "%m.dialog", "%s" });
        BLOCK_PARAMS.put("dialogSetMessage",  new String[]{ "%m.dialog", "%s" });
        BLOCK_PARAMS.put("dialogOkButton",    new String[]{ "%m.dialog", "%s" });
        BLOCK_PARAMS.put("dialogCancelButton",new String[]{ "%m.dialog", "%s" });
        BLOCK_PARAMS.put("dialogNeutralButton",new String[]{ "%m.dialog", "%s" });
        BLOCK_PARAMS.put("dialogShow",        new String[]{ "%m.dialog" });
        BLOCK_PARAMS.put("dialogDismiss",     new String[]{ "%m.dialog" });
        // Timer
        BLOCK_PARAMS.put("timerAfter",        new String[]{ "%m.timer", "%d" });
        BLOCK_PARAMS.put("timerEvery",        new String[]{ "%m.timer", "%d", "%d" });
        BLOCK_PARAMS.put("timerCancel",       new String[]{ "%m.timer" });
        // Calendar
        BLOCK_PARAMS.put("calendarGetNow",    new String[]{ "%m.calendar" });
        BLOCK_PARAMS.put("calendarGetTime",   new String[]{ "%m.calendar" });
        BLOCK_PARAMS.put("calendarSetTime",   new String[]{ "%m.calendar", "%d" });
        BLOCK_PARAMS.put("calendarSet",       new String[]{ "%m.calendar", "%m.calendarField", "%d" });
        BLOCK_PARAMS.put("calendarAdd",       new String[]{ "%m.calendar", "%m.calendarField", "%d" });
        BLOCK_PARAMS.put("calendarFormat",    new String[]{ "%m.calendar", "%s" });
        BLOCK_PARAMS.put("calendarDiff",      new String[]{ "%m.calendar", "%m.calendar" });
        BLOCK_PARAMS.put("calendarViewSetDate",     new String[]{ "%m.calendarview", "%d" });
        BLOCK_PARAMS.put("calendarViewSetMinDate",  new String[]{ "%m.calendarview", "%d" });
        BLOCK_PARAMS.put("calnedarViewSetMaxDate",  new String[]{ "%m.calendarview", "%d" });
        BLOCK_PARAMS.put("calendarViewGetDate",     new String[]{ "%m.calendarview" });
        // Media
        BLOCK_PARAMS.put("mediaplayerCreate", new String[]{ "%m.mediaplayer", "%m.sound" });
        BLOCK_PARAMS.put("mediaplayerStart",  new String[]{ "%m.mediaplayer" });
        BLOCK_PARAMS.put("mediaplayerPause",  new String[]{ "%m.mediaplayer" });
        BLOCK_PARAMS.put("mediaplayerReset",  new String[]{ "%m.mediaplayer" });
        BLOCK_PARAMS.put("mediaplayerRelease",new String[]{ "%m.mediaplayer" });
        BLOCK_PARAMS.put("mediaplayerSeek",   new String[]{ "%m.mediaplayer", "%d" });
        BLOCK_PARAMS.put("mediaplayerSetLooping", new String[]{ "%m.mediaplayer", "%b" });
        BLOCK_PARAMS.put("mediaplayerIsPlaying",  new String[]{ "%m.mediaplayer" });
        BLOCK_PARAMS.put("mediaplayerIsLooping",  new String[]{ "%m.mediaplayer" });
        BLOCK_PARAMS.put("mediaplayerGetCurrent", new String[]{ "%m.mediaplayer" });
        BLOCK_PARAMS.put("mediaplayerGetDuration",new String[]{ "%m.mediaplayer" });
        BLOCK_PARAMS.put("soundpoolCreate",   new String[]{ "%m.soundpool", "%d" });
        BLOCK_PARAMS.put("soundpoolLoad",     new String[]{ "%m.sound", "%m.soundpool" });
        BLOCK_PARAMS.put("soundpoolStreamPlay",new String[]{ "%m.soundpool", "%m.sound", "%d" });
        BLOCK_PARAMS.put("soundpoolStreamStop",new String[]{ "%m.soundpool", "%d" });
        // Animator
        BLOCK_PARAMS.put("objectanimatorSetTarget",      new String[]{ "%m.objectanimator", "%m.view" });
        BLOCK_PARAMS.put("objectanimatorSetProperty",    new String[]{ "%m.objectanimator", "%m.animatorproperty" });
        BLOCK_PARAMS.put("objectanimatorSetDuration",    new String[]{ "%m.objectanimator", "%d" });
        BLOCK_PARAMS.put("objectanimatorSetValue",       new String[]{ "%m.objectanimator", "%d" });
        BLOCK_PARAMS.put("objectanimatorSetFromTo",      new String[]{ "%m.objectanimator", "%d", "%d" });
        BLOCK_PARAMS.put("objectanimatorSetRepeatCount", new String[]{ "%m.objectanimator", "%d" });
        BLOCK_PARAMS.put("objectanimatorSetRepeatMode",  new String[]{ "%m.objectanimator", "%m.aniRepeatMode" });
        BLOCK_PARAMS.put("objectanimatorSetInterpolator",new String[]{ "%m.objectanimator", "%m.aniInterpolator" });
        BLOCK_PARAMS.put("objectanimatorStart",          new String[]{ "%m.objectanimator" });
        BLOCK_PARAMS.put("objectanimatorCancel",         new String[]{ "%m.objectanimator" });
        BLOCK_PARAMS.put("objectanimatorIsRunning",      new String[]{ "%m.objectanimator" });
        // Firebase
        BLOCK_PARAMS.put("firebaseStartListen", new String[]{ "%m.firebase" });
        BLOCK_PARAMS.put("firebaseStopListen",  new String[]{ "%m.firebase" });
        BLOCK_PARAMS.put("firebaseAdd",         new String[]{ "%m.firebase", "%s", "%s" });
        BLOCK_PARAMS.put("firebaseDelete",      new String[]{ "%m.firebase", "%s" });
        BLOCK_PARAMS.put("firebasePush",        new String[]{ "%m.firebase", "%s" });
        BLOCK_PARAMS.put("firebaseGetPushKey",  new String[]{ "%m.firebase" });
        BLOCK_PARAMS.put("firebaseGetChildren", new String[]{ "%m.firebase", "%m.listMap" });
        BLOCK_PARAMS.put("firebaseauthCreateUser",        new String[]{ "%m.firebaseauth", "%s", "%s" });
        BLOCK_PARAMS.put("firebaseauthSignInUser",        new String[]{ "%m.firebaseauth", "%s", "%s" });
        BLOCK_PARAMS.put("firebaseauthSignInAnonymously", new String[]{ "%m.firebaseauth" });
        BLOCK_PARAMS.put("firebaseauthResetPassword",     new String[]{ "%m.firebaseauth", "%s" });
        BLOCK_PARAMS.put("firebaseauthSignOutUser",       new String[]{ "%m.firebaseauth" });
        BLOCK_PARAMS.put("firebaseauthGetCurrentUser",    new String[]{ "%m.firebaseauth" });
        BLOCK_PARAMS.put("firebaseauthGetUid",            new String[]{ "%m.firebaseauth" });
        BLOCK_PARAMS.put("firebaseauthIsLoggedIn",        new String[]{ "%m.firebaseauth" });
        BLOCK_PARAMS.put("firebasestorageUploadFile",     new String[]{ "%m.firebasestorage", "%s", "%s" });
        BLOCK_PARAMS.put("firebasestorageDownloadFile",   new String[]{ "%m.firebasestorage", "%s", "%s" });
        BLOCK_PARAMS.put("firebasestorageDelete",         new String[]{ "%m.firebasestorage", "%s" });
        // Network
        BLOCK_PARAMS.put("requestnetworkStartRequestNetwork", new String[]{ "%m.requestnetwork", "%m.requestType", "%s", "%s" });
        BLOCK_PARAMS.put("requestnetworkSetParams",           new String[]{ "%m.requestnetwork", "%m.varMap", "%m.requestType" });
        BLOCK_PARAMS.put("requestnetworkSetHeaders",          new String[]{ "%m.requestnetwork", "%m.varMap" });
        // TTS / STT
        BLOCK_PARAMS.put("textToSpeechSpeak",          new String[]{ "%m.texttospeech", "%s" });
        BLOCK_PARAMS.put("textToSpeechStop",           new String[]{ "%m.texttospeech" });
        BLOCK_PARAMS.put("textToSpeechShutdown",       new String[]{ "%m.texttospeech" });
        BLOCK_PARAMS.put("textToSpeechSetPitch",       new String[]{ "%m.texttospeech", "%d" });
        BLOCK_PARAMS.put("textToSpeechSetSpeechRate",  new String[]{ "%m.texttospeech", "%d" });
        BLOCK_PARAMS.put("textToSpeechIsSpeaking",     new String[]{ "%m.texttospeech" });
        BLOCK_PARAMS.put("speechToTextStartListening", new String[]{ "%m.speechtotext" });
        BLOCK_PARAMS.put("speechToTextStopListening",  new String[]{ "%m.speechtotext" });
        BLOCK_PARAMS.put("speechToTextShutdown",       new String[]{ "%m.speechtotext" });
        // Ads
        BLOCK_PARAMS.put("interstitialadCreate",       new String[]{ "%m.interstitialad" });
        BLOCK_PARAMS.put("interstitialadLoadAd",       new String[]{ "%m.interstitialad" });
        BLOCK_PARAMS.put("interstitialadShow",         new String[]{ "%m.interstitialad" });
        BLOCK_PARAMS.put("adViewLoadAd",               new String[]{ "%m.adview" });
        // Camera
        BLOCK_PARAMS.put("camerastarttakepicture",     new String[]{ "%m.camera" });
        // Gyroscope
        BLOCK_PARAMS.put("gyroscopeStartListen",       new String[]{ "%m.gyroscope" });
        BLOCK_PARAMS.put("gyroscopeStopListen",        new String[]{ "%m.gyroscope" });
        // Location
        BLOCK_PARAMS.put("locationManagerRequestLocationUpdates", new String[]{ "%m.locationmanager", "%m.providerType", "%d", "%d" });
        BLOCK_PARAMS.put("locationManagerRemoveUpdates",          new String[]{ "%m.locationmanager" });
        // WebView
        BLOCK_PARAMS.put("webViewLoadUrl",     new String[]{ "%m.webview", "%s" });
        BLOCK_PARAMS.put("webViewGetUrl",      new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewGoBack",      new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewGoForward",   new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewCanGoBack",   new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewCanGoForward",new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewClearCache",  new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewClearHistory",new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewStopLoading", new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewZoomIn",      new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewZoomOut",     new String[]{ "%m.webview" });
        BLOCK_PARAMS.put("webViewSetCacheMode",new String[]{ "%m.webview", "%m.cacheMode" });
        // MapView
        BLOCK_PARAMS.put("mapViewAddMarker",          new String[]{ "%m.mapview", "%s", "%d", "%d" });
        BLOCK_PARAMS.put("mapViewMoveCamera",         new String[]{ "%m.mapview", "%d", "%d" });
        BLOCK_PARAMS.put("mapViewZoomIn",             new String[]{ "%m.mapview" });
        BLOCK_PARAMS.put("mapViewZoomOut",            new String[]{ "%m.mapview" });
        BLOCK_PARAMS.put("mapViewZoomTo",             new String[]{ "%m.mapview", "%d" });
        BLOCK_PARAMS.put("mapViewSetMapType",         new String[]{ "%m.mapview", "%m.mapType" });
        BLOCK_PARAMS.put("mapViewSetMarkerPosition",  new String[]{ "%m.mapview", "%s", "%d", "%d" });
        BLOCK_PARAMS.put("mapViewSetMarkerColor",     new String[]{ "%m.mapview", "%s", "%m.markerColor", "%d" });
        BLOCK_PARAMS.put("mapViewSetMarkerIcon",      new String[]{ "%m.mapview", "%s", "%m.resource" });
        BLOCK_PARAMS.put("mapViewSetMarkerInfo",      new String[]{ "%m.mapview", "%s", "%s", "%s" });
        BLOCK_PARAMS.put("mapViewSetMarkerVisible",   new String[]{ "%m.mapview", "%s", "%b" });
        // Vibrator
        BLOCK_PARAMS.put("vibratorAction",            new String[]{ "%m.vibrator", "%d" });
        // Bitmap
        BLOCK_PARAMS.put("rotateBitmapFile",          new String[]{ "%s", "%s", "%d" });
        BLOCK_PARAMS.put("scaleBitmapFile",           new String[]{ "%s", "%s", "%d", "%d" });
        BLOCK_PARAMS.put("skewBitmapFile",            new String[]{ "%s", "%s", "%d", "%d" });
        BLOCK_PARAMS.put("cropBitmapFileFromCenter",  new String[]{ "%s", "%s", "%d", "%d" });
        BLOCK_PARAMS.put("resizeBitmapFileRetainRatio",new String[]{ "%s", "%s", "%d" });
        BLOCK_PARAMS.put("resizeBitmapFileToSquare",  new String[]{ "%s", "%s", "%d" });
        BLOCK_PARAMS.put("resizeBitmapFileToCircle",  new String[]{ "%s", "%s" });
        BLOCK_PARAMS.put("resizeBitmapFileWithRoundedBorder", new String[]{ "%s", "%s", "%d" });
        BLOCK_PARAMS.put("setBitmapFileBrightness",   new String[]{ "%s", "%s", "%d" });
        BLOCK_PARAMS.put("setBitmapFileContrast",     new String[]{ "%s", "%s", "%d" });
        BLOCK_PARAMS.put("setBitmapFileColorFilter",  new String[]{ "%s", "%s", "%m.color" });
        BLOCK_PARAMS.put("getJpegRotate",             new String[]{ "%s" });
        // Source
        BLOCK_PARAMS.put("addSourceDirectly",         new String[]{ "%s.inputOnly" });
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void initEventParams() {
        EVENT_PARAMS.put("initializeLogic",          new String[]{});
        EVENT_PARAMS.put("onBackPressed",            new String[]{});
        EVENT_PARAMS.put("onPostCreate",             new String[]{});
        EVENT_PARAMS.put("onStart",                  new String[]{});
        EVENT_PARAMS.put("onStop",                   new String[]{});
        EVENT_PARAMS.put("onDestroy",                new String[]{});
        EVENT_PARAMS.put("onResume",                 new String[]{});
        EVENT_PARAMS.put("onPause",                  new String[]{});
        EVENT_PARAMS.put("moreBlock",                new String[]{});
        EVENT_PARAMS.put("onClick",                  new String[]{});
        EVENT_PARAMS.put("onCheckedChange",          new String[]{ "%b.isChecked" });
        EVENT_PARAMS.put("onItemSelected",           new String[]{});
        EVENT_PARAMS.put("onItemClicked",            new String[]{ "%d.position" });
        EVENT_PARAMS.put("onItemLongClicked",        new String[]{ "%d.position" });
        EVENT_PARAMS.put("onTextChanged",            new String[]{ "%s.charSeq" });
        EVENT_PARAMS.put("onPageStarted",            new String[]{ "%s.url" });
        EVENT_PARAMS.put("onPageFinished",           new String[]{ "%s.url" });
        EVENT_PARAMS.put("onProgressChanged",        new String[]{ "%d.progressValue" });
        EVENT_PARAMS.put("onStartTrackingTouch",     new String[]{});
        EVENT_PARAMS.put("onStopTrackingTouch",      new String[]{});
        EVENT_PARAMS.put("onAnimationStart",         new String[]{});
        EVENT_PARAMS.put("onAnimationEnd",           new String[]{});
        EVENT_PARAMS.put("onAnimationCancel",        new String[]{});
        EVENT_PARAMS.put("onBindCustomView",         new String[]{ "%m.view", "%d.position" });
        EVENT_PARAMS.put("onDateChange",             new String[]{ "%d.year", "%d.month", "%d.day" });
        EVENT_PARAMS.put("onChildAdded",             new String[]{ "%s.childKey", "%m.varMap.childValue" });
        EVENT_PARAMS.put("onChildChanged",           new String[]{ "%s.childKey", "%m.varMap.childValue" });
        EVENT_PARAMS.put("onChildRemoved",           new String[]{ "%s.childKey" });
        EVENT_PARAMS.put("onCancelled",              new String[]{});
        EVENT_PARAMS.put("onCreateUserComplete",     new String[]{ "%b.success", "%s.errorMessage" });
        EVENT_PARAMS.put("onSignInUserComplete",     new String[]{ "%b.success", "%s.errorMessage" });
        EVENT_PARAMS.put("onResetPasswordEmailSent", new String[]{ "%b.success", "%s.errorMessage" });
        EVENT_PARAMS.put("onUploadProgress",         new String[]{ "%d.totalByteCount", "%d.totalByteCount" });
        EVENT_PARAMS.put("onDownloadProgress",       new String[]{ "%d.totalByteCount", "%d.totalByteCount" });
        EVENT_PARAMS.put("onUploadSuccess",          new String[]{ "%s.downloadUrl" });
        EVENT_PARAMS.put("onDownloadSuccess",        new String[]{ "%s.filePath" });
        EVENT_PARAMS.put("onDeleteSuccess",          new String[]{});
        EVENT_PARAMS.put("onFailure",                new String[]{ "%s.errorMessage" });
        EVENT_PARAMS.put("onPictureTaken",           new String[]{ "%s.filePath" });
        EVENT_PARAMS.put("onPictureTakenCancel",     new String[]{});
        EVENT_PARAMS.put("onFilesPicked",            new String[]{ "%m.listStr.filePath" });
        EVENT_PARAMS.put("onFilesPickedCancel",      new String[]{});
        EVENT_PARAMS.put("onAdLoaded",               new String[]{});
        EVENT_PARAMS.put("onAdFailedToLoad",         new String[]{ "%d.errorCode" });
        EVENT_PARAMS.put("onAdOpened",               new String[]{});
        EVENT_PARAMS.put("onAdClosed",               new String[]{});
        EVENT_PARAMS.put("onResponse",               new String[]{ "%s.tag", "%s.response", "%m.varMap.responseHeaders" });
        EVENT_PARAMS.put("onErrorResponse",          new String[]{ "%s.tag", "%s.errorMessage" });
        EVENT_PARAMS.put("onSpeechResult",           new String[]{ "%s.result" });
        EVENT_PARAMS.put("onSpeechError",            new String[]{ "%d.errorCode" });
        EVENT_PARAMS.put("onConnected",              new String[]{ "%s.tag" });
        EVENT_PARAMS.put("onDataReceived",           new String[]{ "%s.tag", "%s.data" });
        EVENT_PARAMS.put("onDataSent",               new String[]{ "%s.tag" });
        EVENT_PARAMS.put("onConnectionError",        new String[]{ "%s.tag", "%s.errorMessage" });
        EVENT_PARAMS.put("onConnectionStopped",      new String[]{ "%s.tag" });
        EVENT_PARAMS.put("onMapReady",               new String[]{});
        EVENT_PARAMS.put("onMarkerClicked",          new String[]{ "%s.id" });
        EVENT_PARAMS.put("onSensorChanged",          new String[]{ "%d.x", "%d.y", "%d.z", "%d.acc" });
        EVENT_PARAMS.put("onLocationChanged",        new String[]{ "%d.lat", "%d.lng", "%d.acc", "%s.connectionState" });
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void initBlockSnake() {
        // ── math / operators ──────────────────────────────────────────────────
        BLOCK_SNAKE.put("plus",                   "plus");
        BLOCK_SNAKE.put("minus",                  "minus");
        BLOCK_SNAKE.put("times",                  "times");
        BLOCK_SNAKE.put("divide",                 "divide");
        BLOCK_SNAKE.put("rest",                   "rest");
        BLOCK_SNAKE.put("bigger",                 "bigger");
        BLOCK_SNAKE.put("smaller",                "smaller");
        BLOCK_SNAKE.put("equal",                  "equal");
        BLOCK_SNAKE.put("and",                    "and");
        BLOCK_SNAKE.put("or",                     "or");
        BLOCK_SNAKE.put("not",                    "not");
        BLOCK_SNAKE.put("true",                   "true");
        BLOCK_SNAKE.put("false",                  "false");
        BLOCK_SNAKE.put("if",                     "if");
        BLOCK_SNAKE.put("ifElse",                 "if_else");
        BLOCK_SNAKE.put("forever",                "forever");
        BLOCK_SNAKE.put("repeat",                 "repeat");
        BLOCK_SNAKE.put("break",                  "break");
        BLOCK_SNAKE.put("else",                   "else");
        BLOCK_SNAKE.put("random",                 "random");
        // ── math functions ────────────────────────────────────────────────────
        BLOCK_SNAKE.put("mathAbs",                "math_abs");
        BLOCK_SNAKE.put("mathSqrt",               "math_sqrt");
        BLOCK_SNAKE.put("mathSin",                "math_sin");
        BLOCK_SNAKE.put("mathCos",                "math_cos");
        BLOCK_SNAKE.put("mathTan",                "math_tan");
        BLOCK_SNAKE.put("mathAsin",               "math_asin");
        BLOCK_SNAKE.put("mathAcos",               "math_acos");
        BLOCK_SNAKE.put("mathAtan",               "math_atan");
        BLOCK_SNAKE.put("mathExp",                "math_exp");
        BLOCK_SNAKE.put("mathLog",                "math_log");
        BLOCK_SNAKE.put("mathLog10",              "math_log10");
        BLOCK_SNAKE.put("mathCeil",               "math_ceil");
        BLOCK_SNAKE.put("mathFloor",              "math_floor");
        BLOCK_SNAKE.put("mathRound",              "math_round");
        BLOCK_SNAKE.put("mathPow",                "math_pow");
        BLOCK_SNAKE.put("mathMax",                "math_max");
        BLOCK_SNAKE.put("mathMin",                "math_min");
        BLOCK_SNAKE.put("mathPi",                 "math_pi");
        BLOCK_SNAKE.put("mathE",                  "math_e");
        BLOCK_SNAKE.put("mathToDegree",           "math_to_degree");
        BLOCK_SNAKE.put("mathToRadian",           "math_to_radian");
        BLOCK_SNAKE.put("mathGetDip",             "math_get_dip");
        BLOCK_SNAKE.put("mathGetDisplayWidth",    "math_get_display_width");
        BLOCK_SNAKE.put("mathGetDisplayHeight",   "math_get_display_height");
        // ── strings ───────────────────────────────────────────────────────────
        BLOCK_SNAKE.put("stringLength",           "string_length");
        BLOCK_SNAKE.put("stringContains",         "string_contains");
        BLOCK_SNAKE.put("stringEquals",           "string_equals");
        BLOCK_SNAKE.put("stringReplace",          "string_replace");
        BLOCK_SNAKE.put("stringReplaceAll",       "string_replace_all");
        BLOCK_SNAKE.put("stringReplaceFirst",     "string_replace_first");
        BLOCK_SNAKE.put("stringIndex",            "string_index");
        BLOCK_SNAKE.put("stringLastIndex",        "string_last_index");
        BLOCK_SNAKE.put("stringSub",              "string_sub");
        BLOCK_SNAKE.put("stringJoin",             "string_join");
        BLOCK_SNAKE.put("toUpperCase",            "to_upper_case");
        BLOCK_SNAKE.put("toLowerCase",            "to_lower_case");
        BLOCK_SNAKE.put("trim",                   "trim");
        BLOCK_SNAKE.put("toString",               "to_string");
        BLOCK_SNAKE.put("toStringWithDecimal",    "to_string_with_decimal");
        BLOCK_SNAKE.put("toStringFormat",         "to_string_format");
        BLOCK_SNAKE.put("toNumber",               "to_number");
        // ── variables ─────────────────────────────────────────────────────────
        BLOCK_SNAKE.put("setVarInt",              "set_var_int");
        BLOCK_SNAKE.put("setVarStr",              "set_var_str");
        BLOCK_SNAKE.put("setVarBoolean",          "set_var_bool");
        BLOCK_SNAKE.put("setVarString",           "set_var_str");   // xB alias
        BLOCK_SNAKE.put("increaseInt",            "increase_int");
        BLOCK_SNAKE.put("decreaseInt",            "decrease_int");
        // ── lists ─────────────────────────────────────────────────────────────
        BLOCK_SNAKE.put("addListInt",             "add_list_int");
        BLOCK_SNAKE.put("addListStr",             "add_list_str");
        BLOCK_SNAKE.put("addListMap",             "add_list_map");
        BLOCK_SNAKE.put("addMapToList",           "add_map_to_list");
        BLOCK_SNAKE.put("clearList",              "clear_list");
        BLOCK_SNAKE.put("deleteList",             "delete_list");
        BLOCK_SNAKE.put("lengthList",             "length_list");
        BLOCK_SNAKE.put("getAtListInt",           "get_at_list_int");
        BLOCK_SNAKE.put("getAtListStr",           "get_at_list_str");
        BLOCK_SNAKE.put("getAtListMap",           "get_at_list_map");
        BLOCK_SNAKE.put("getMapInList",           "get_map_in_list");
        BLOCK_SNAKE.put("setAtListMap",           "set_at_list_map");
        BLOCK_SNAKE.put("indexofListInt",         "indexof_list_int");
        BLOCK_SNAKE.put("indexofListStr",         "indexof_list_str");
        BLOCK_SNAKE.put("indexListInt",           "indexof_list_int");
        BLOCK_SNAKE.put("indexListStr",           "indexof_list_str");
        BLOCK_SNAKE.put("insertListInt",          "insert_list_int");
        BLOCK_SNAKE.put("insertListStr",          "insert_list_str");
        BLOCK_SNAKE.put("insertListMap",          "insert_list_map");
        BLOCK_SNAKE.put("insertMapToList",        "insert_map_to_list");
        BLOCK_SNAKE.put("containListInt",         "contain_list_int");
        BLOCK_SNAKE.put("containListStr",         "contain_list_str");
        BLOCK_SNAKE.put("containListMap",         "contain_list_map");
        BLOCK_SNAKE.put("listMapToStr",           "list_map_to_str");
        BLOCK_SNAKE.put("strToListMap",           "str_to_list_map");
        BLOCK_SNAKE.put("strToMap",               "str_to_map");
        BLOCK_SNAKE.put("listRefresh",            "list_refresh");
        BLOCK_SNAKE.put("listSetData",            "list_set_data");
        BLOCK_SNAKE.put("listSetCustomViewData",  "list_set_custom_view_data");
        BLOCK_SNAKE.put("listGetCheckedCount",    "list_get_checked_count");
        BLOCK_SNAKE.put("listGetCheckedPosition", "list_get_checked_position");
        BLOCK_SNAKE.put("listGetCheckedPositions","list_get_checked_positions");
        BLOCK_SNAKE.put("listSetItemChecked",     "list_set_item_checked");
        BLOCK_SNAKE.put("listSmoothScrollTo",     "list_smooth_scrollto");
        BLOCK_SNAKE.put("listSmoothScrollto",     "list_smooth_scrollto");
        BLOCK_SNAKE.put("setListMap",             "set_at_list_map");
        // ── map ───────────────────────────────────────────────────────────────
        BLOCK_SNAKE.put("mapPut",                 "map_put");
        BLOCK_SNAKE.put("mapGet",                 "map_get");
        BLOCK_SNAKE.put("mapRemoveKey",           "map_remove_key");
        BLOCK_SNAKE.put("mapContainKey",          "map_contain_key");
        BLOCK_SNAKE.put("mapGetAllKeys",          "map_get_all_keys");
        BLOCK_SNAKE.put("mapClear",               "map_clear");
        BLOCK_SNAKE.put("mapIsEmpty",             "map_is_empty");
        BLOCK_SNAKE.put("mapSize",                "map_size");
        BLOCK_SNAKE.put("mapToStr",               "map_to_str");
        BLOCK_SNAKE.put("mapCreateNew",           "map_create_new");
        // ── view ──────────────────────────────────────────────────────────────
        BLOCK_SNAKE.put("setText",                "set_text");
        BLOCK_SNAKE.put("getText",                "get_text");
        BLOCK_SNAKE.put("setHint",                "set_hint");
        BLOCK_SNAKE.put("setHintTextColor",       "set_hint_text_color");
        BLOCK_SNAKE.put("setTextColor",           "set_text_color");
        BLOCK_SNAKE.put("setImage",               "set_image");
        BLOCK_SNAKE.put("setImageFilePath",       "set_image_file_path");
        BLOCK_SNAKE.put("setImageUrl",            "set_image_url");
        BLOCK_SNAKE.put("setEnable",              "set_enable");
        BLOCK_SNAKE.put("getEnable",              "get_enable");
        BLOCK_SNAKE.put("setVisible",             "set_visible");
        BLOCK_SNAKE.put("setClickable",           "set_clickable");
        BLOCK_SNAKE.put("setAlpha",               "set_alpha");
        BLOCK_SNAKE.put("getAlpha",               "get_alpha");
        BLOCK_SNAKE.put("setRotate",              "set_rotate");
        BLOCK_SNAKE.put("getRotate",              "get_rotate");
        BLOCK_SNAKE.put("setScaleX",              "set_scale_x");
        BLOCK_SNAKE.put("getScaleX",              "get_scale_x");
        BLOCK_SNAKE.put("setScaleY",              "set_scale_y");
        BLOCK_SNAKE.put("getScaleY",              "get_scale_y");
        BLOCK_SNAKE.put("setTranslationX",        "set_translation_x");
        BLOCK_SNAKE.put("getTranslationX",        "get_translation_x");
        BLOCK_SNAKE.put("setTranslationY",        "set_translation_y");
        BLOCK_SNAKE.put("getTranslationY",        "get_translation_y");
        BLOCK_SNAKE.put("getLocationX",           "get_location_x");
        BLOCK_SNAKE.put("getLocationY",           "get_location_y");
        BLOCK_SNAKE.put("requestFocus",           "request_focus");
        BLOCK_SNAKE.put("setTypeface",            "set_typeface");
        BLOCK_SNAKE.put("setBgColor",             "set_bg_color");
        BLOCK_SNAKE.put("setBgResource",          "set_bg_resource");
        BLOCK_SNAKE.put("setColorFilter",         "set_color_filter");
        BLOCK_SNAKE.put("viewOnClick",            "view_on_click");
        BLOCK_SNAKE.put("setChecked",             "set_checked");
        BLOCK_SNAKE.put("getChecked",             "get_checked");
        BLOCK_SNAKE.put("setTitle",               "set_title");
        BLOCK_SNAKE.put("seekBarSetProgress",     "seekbar_set_progress");
        BLOCK_SNAKE.put("seekBarGetProgress",     "seekbar_get_progress");
        BLOCK_SNAKE.put("seekBarSetMax",          "seekbar_set_max");
        BLOCK_SNAKE.put("seekBarGetMax",          "seekbar_get_max");
        BLOCK_SNAKE.put("setThumbResource",       "set_thumb_resource");
        BLOCK_SNAKE.put("setTrackResource",       "set_track_resource");
        BLOCK_SNAKE.put("spnSetData",             "spn_set_data");
        BLOCK_SNAKE.put("spnGetSelection",        "spn_get_selection");
        BLOCK_SNAKE.put("spnRefresh",             "spn_refresh");
        BLOCK_SNAKE.put("spnSetSelection",        "spn_set_selection");
        BLOCK_SNAKE.put("progressBarSetIndeterminate", "progressbar_set_indeterminate");
        BLOCK_SNAKE.put("openDrawer",             "open_drawer");
        BLOCK_SNAKE.put("closeDrawer",            "close_drawer");
        BLOCK_SNAKE.put("isDrawerOpen",           "is_drawer_open");
        // ── intent / activity ─────────────────────────────────────────────────
        BLOCK_SNAKE.put("intentSetScreen",        "intent_set_screen");
        BLOCK_SNAKE.put("intentPutExtra",         "intent_put_extra");
        BLOCK_SNAKE.put("intentGetString",        "intent_get_string");
        BLOCK_SNAKE.put("intentSetAction",        "intent_set_action");
        BLOCK_SNAKE.put("intentSetData",          "intent_set_data");
        BLOCK_SNAKE.put("intentSetFlags",         "intent_set_flags");
        BLOCK_SNAKE.put("startActivity",          "start_activity");
        BLOCK_SNAKE.put("finishActivity",         "finish_activity");
        BLOCK_SNAKE.put("doToast",                "do_toast");
        BLOCK_SNAKE.put("copyToClipboard",        "copy_to_clipboard");
        // ── file ──────────────────────────────────────────────────────────────
        BLOCK_SNAKE.put("fileSetData",            "file_set_data");
        BLOCK_SNAKE.put("fileGetData",            "file_get_data");
        BLOCK_SNAKE.put("fileRemoveData",         "file_remove_data");
        BLOCK_SNAKE.put("fileutilRead",           "fileutil_read");
        BLOCK_SNAKE.put("fileutilWrite",          "fileutil_write");
        BLOCK_SNAKE.put("fileutilCopy",           "fileutil_copy");
        BLOCK_SNAKE.put("fileutilMove",           "fileutil_move");
        BLOCK_SNAKE.put("fileutilDelete",         "fileutil_delete");
        BLOCK_SNAKE.put("fileutilMakeDir",        "fileutil_make_dir");
        BLOCK_SNAKE.put("fileutilIsExist",        "fileutil_is_exist");
        BLOCK_SNAKE.put("fileutilIsFile",         "fileutil_is_file");
        BLOCK_SNAKE.put("fileutilIsDir",          "fileutil_is_dir");
        BLOCK_SNAKE.put("fileutilLength",         "fileutil_length");
        BLOCK_SNAKE.put("fileutilListDir",        "fileutil_list_dir");
        BLOCK_SNAKE.put("fileutilStartsWith",     "fileutil_starts_with");
        BLOCK_SNAKE.put("fileutilEndsWith",       "fileutil_ends_with");
        BLOCK_SNAKE.put("fileutilGetLastSegmentPath", "fileutil_get_last_segment_path");
        BLOCK_SNAKE.put("fileutilcopy",           "fileutil_copy");
        BLOCK_SNAKE.put("fileutildelete",         "fileutil_delete");
        BLOCK_SNAKE.put("fileutilisdir",          "fileutil_is_dir");
        BLOCK_SNAKE.put("fileutilisexist",        "fileutil_is_exist");
        BLOCK_SNAKE.put("fileutilisfile",         "fileutil_is_file");
        BLOCK_SNAKE.put("fileutillength",         "fileutil_length");
        BLOCK_SNAKE.put("fileutillistdir",        "fileutil_list_dir");
        BLOCK_SNAKE.put("fileutilmakedir",        "fileutil_make_dir");
        BLOCK_SNAKE.put("fileutilmove",           "fileutil_move");
        BLOCK_SNAKE.put("fileutilread",           "fileutil_read");
        BLOCK_SNAKE.put("fileutilwrite",          "fileutil_write");
        BLOCK_SNAKE.put("filepickerStartPickFiles",   "file_picker_start_pick_files");
        BLOCK_SNAKE.put("filepickerstartpickfiles",   "file_picker_start_pick_files");
        // ── dialog / timer / calendar ─────────────────────────────────────────
        BLOCK_SNAKE.put("dialogSetTitle",         "dialog_set_title");
        BLOCK_SNAKE.put("dialogSetMessage",       "dialog_set_message");
        BLOCK_SNAKE.put("dialogOkButton",         "dialog_ok_button");
        BLOCK_SNAKE.put("dialogCancelButton",     "dialog_cancel_button");
        BLOCK_SNAKE.put("dialogNeutralButton",    "dialog_neutral_button");
        BLOCK_SNAKE.put("dialogShow",             "dialog_show");
        BLOCK_SNAKE.put("dialogDismiss",          "dialog_dismiss");
        BLOCK_SNAKE.put("timerAfter",             "timer_after");
        BLOCK_SNAKE.put("timerEvery",             "timer_every");
        BLOCK_SNAKE.put("timerCancel",            "timer_cancel");
        BLOCK_SNAKE.put("calendarGetNow",         "calendar_get_now");
        BLOCK_SNAKE.put("calendarGetTime",        "calendar_get_time");
        BLOCK_SNAKE.put("calendarSetTime",        "calendar_set_time");
        BLOCK_SNAKE.put("calendarSet",            "calendar_set");
        BLOCK_SNAKE.put("calendarAdd",            "calendar_add");
        BLOCK_SNAKE.put("calendarFormat",         "calendar_format");
        BLOCK_SNAKE.put("calendarDiff",           "calendar_diff");
        BLOCK_SNAKE.put("calendarViewSetDate",    "calendarview_set_date");
        BLOCK_SNAKE.put("calendarViewSetMinDate", "calendarview_set_min_date");
        BLOCK_SNAKE.put("calnedarViewSetMaxDate", "calendarview_set_max_date");
        BLOCK_SNAKE.put("calendarViewGetDate",    "calendarview_get_date");
        // ── media / firebase / network / sensors ──────────────────────────────
        BLOCK_SNAKE.put("mediaplayerCreate",      "mediaplayer_create");
        BLOCK_SNAKE.put("mediaplayerStart",       "mediaplayer_start");
        BLOCK_SNAKE.put("mediaplayerPause",       "mediaplayer_pause");
        BLOCK_SNAKE.put("mediaplayerReset",       "mediaplayer_reset");
        BLOCK_SNAKE.put("mediaplayerRelease",     "mediaplayer_release");
        BLOCK_SNAKE.put("mediaplayerSeek",        "mediaplayer_seek");
        BLOCK_SNAKE.put("mediaplayerSetLooping",  "mediaplayer_set_looping");
        BLOCK_SNAKE.put("mediaplayerIsPlaying",   "mediaplayer_is_playing");
        BLOCK_SNAKE.put("mediaplayerIsLooping",   "mediaplayer_is_looping");
        BLOCK_SNAKE.put("mediaplayerGetCurrent",  "mediaplayer_get_current");
        BLOCK_SNAKE.put("mediaplayerGetDuration", "mediaplayer_get_duration");
        BLOCK_SNAKE.put("soundpoolCreate",        "soundpool_create");
        BLOCK_SNAKE.put("soundpoolLoad",          "soundpool_load");
        BLOCK_SNAKE.put("soundpoolStreamPlay",    "soundpool_stream_play");
        BLOCK_SNAKE.put("soundpoolStreamStop",    "soundpool_stream_stop");
        BLOCK_SNAKE.put("objectanimatorSetTarget",      "objectanimator_set_target");
        BLOCK_SNAKE.put("objectanimatorSetProperty",    "objectanimator_set_property");
        BLOCK_SNAKE.put("objectanimatorSetDuration",    "objectanimator_set_duration");
        BLOCK_SNAKE.put("objectanimatorSetValue",       "objectanimator_set_value");
        BLOCK_SNAKE.put("objectanimatorSetFromTo",      "objectanimator_set_from_to");
        BLOCK_SNAKE.put("objectanimatorSetRepeatCount", "objectanimator_set_repeat_count");
        BLOCK_SNAKE.put("objectanimatorSetRepeatMode",  "objectanimator_set_repeat_mode");
        BLOCK_SNAKE.put("objectanimatorSetInterpolator","objectanimator_set_interpolator");
        BLOCK_SNAKE.put("objectanimatorStart",          "objectanimator_start");
        BLOCK_SNAKE.put("objectanimatorCancel",         "objectanimator_cancel");
        BLOCK_SNAKE.put("objectanimatorIsRunning",      "objectanimator_is_running");
        BLOCK_SNAKE.put("firebaseStartListen",    "firebase_start_listen");
        BLOCK_SNAKE.put("firebaseStopListen",     "firebase_stop_listen");
        BLOCK_SNAKE.put("firebaseAdd",            "firebase_add");
        BLOCK_SNAKE.put("firebaseDelete",         "firebase_delete");
        BLOCK_SNAKE.put("firebasePush",           "firebase_push");
        BLOCK_SNAKE.put("firebaseGetPushKey",     "firebase_get_key");
        BLOCK_SNAKE.put("firebaseGetChildren",    "firebase_get_children");
        BLOCK_SNAKE.put("firebaseauthCreateUser",         "firebaseauth_create_user");
        BLOCK_SNAKE.put("firebaseauthSignInUser",          "firebaseauth_signin_user");
        BLOCK_SNAKE.put("firebaseauthSignInAnonymously",   "firebaseauth_signin_anonymously");
        BLOCK_SNAKE.put("firebaseauthResetPassword",       "firebaseauth_reset_password");
        BLOCK_SNAKE.put("firebaseauthSignOutUser",         "firebaseauth_signout");
        BLOCK_SNAKE.put("firebaseauthGetCurrentUser",      "firebaseauth_get_email");
        BLOCK_SNAKE.put("firebaseauthGetUid",              "firebaseauth_get_uid");
        BLOCK_SNAKE.put("firebaseauthIsLoggedIn",          "firebaseauth_is_logged_in");
        BLOCK_SNAKE.put("firebasestorageUploadFile",   "firebasestorage_upload_file");
        BLOCK_SNAKE.put("firebasestorageDownloadFile", "firebasestorage_download_file");
        BLOCK_SNAKE.put("firebasestorageDelete",       "firebasestorage_delete");
        BLOCK_SNAKE.put("requestnetworkStartRequestNetwork", "requestnetwork_start_request_network");
        BLOCK_SNAKE.put("requestnetworkSetParams",           "requestnetwork_set_params");
        BLOCK_SNAKE.put("requestnetworkSetHeaders",          "requestnetwork_set_headers");
        BLOCK_SNAKE.put("textToSpeechSpeak",       "texttospeech_speak");
        BLOCK_SNAKE.put("textToSpeechStop",        "texttospeech_stop");
        BLOCK_SNAKE.put("textToSpeechShutdown",    "texttospeech_shutdown");
        BLOCK_SNAKE.put("textToSpeechSetPitch",    "texttospeech_set_pitch");
        BLOCK_SNAKE.put("textToSpeechSetSpeechRate","texttospeech_set_speech_rate");
        BLOCK_SNAKE.put("textToSpeechIsSpeaking",  "texttospeech_is_speaking");
        BLOCK_SNAKE.put("speechToTextStartListening","speechtotext_start_listening");
        BLOCK_SNAKE.put("speechToTextStopListening", "speechtotext_stop_listening");
        BLOCK_SNAKE.put("speechToTextShutdown",      "speechtotext_shutdown");
        BLOCK_SNAKE.put("interstitialadCreate",    "interstitialad_create");
        BLOCK_SNAKE.put("interstitialadLoadAd",    "interstitialad_load_ad");
        BLOCK_SNAKE.put("interstitialadShow",      "interstitialad_show");
        BLOCK_SNAKE.put("adViewLoadAd",            "adview_load_ad");
        BLOCK_SNAKE.put("camerastarttakepicture",  "camera_start_take_picture");
        BLOCK_SNAKE.put("gyroscopeStartListen",    "gyroscope_start_listen");
        BLOCK_SNAKE.put("gyroscopeStopListen",     "gyroscope_stop_listen");
        BLOCK_SNAKE.put("locationManagerRequestLocationUpdates", "locationmanager_request_location_updates");
        BLOCK_SNAKE.put("locationManagerRemoveUpdates",          "locationmanager_remove_updates");
        BLOCK_SNAKE.put("webViewLoadUrl",          "webview_load_url");
        BLOCK_SNAKE.put("webViewGetUrl",           "webview_get_url");
        BLOCK_SNAKE.put("webViewGoBack",           "webview_go_back");
        BLOCK_SNAKE.put("webViewGoForward",        "webview_go_forward");
        BLOCK_SNAKE.put("webViewCanGoBack",        "webview_can_go_back");
        BLOCK_SNAKE.put("webViewCanGoForward",     "webview_can_go_forward");
        BLOCK_SNAKE.put("webViewClearCache",       "webview_clear_cache");
        BLOCK_SNAKE.put("webViewClearHistory",     "webview_clear_history");
        BLOCK_SNAKE.put("webViewStopLoading",      "webview_stop_loading");
        BLOCK_SNAKE.put("webViewZoomIn",           "webview_zoom_in");
        BLOCK_SNAKE.put("webViewZoomOut",          "webview_zoom_out");
        BLOCK_SNAKE.put("webViewSetCacheMode",     "webview_set_cache_mode");
        BLOCK_SNAKE.put("mapViewAddMarker",          "mapview_add_marker");
        BLOCK_SNAKE.put("mapViewMoveCamera",         "mapview_move_camera");
        BLOCK_SNAKE.put("mapViewZoomIn",             "mapview_zoom_in");
        BLOCK_SNAKE.put("mapViewZoomOut",            "mapview_zoom_out");
        BLOCK_SNAKE.put("mapViewZoomTo",             "mapview_zoom_to");
        BLOCK_SNAKE.put("mapViewSetMapType",         "mapview_set_map_type");
        BLOCK_SNAKE.put("mapViewSetMarkerPosition",  "mapview_set_marker_position");
        BLOCK_SNAKE.put("mapViewSetMarkerColor",     "mapview_set_marker_color");
        BLOCK_SNAKE.put("mapViewSetMarkerIcon",      "mapview_set_marker_icon");
        BLOCK_SNAKE.put("mapViewSetMarkerInfo",      "mapview_set_marker_info");
        BLOCK_SNAKE.put("mapViewSetMarkerVisible",   "mapview_set_marker_visible");
        BLOCK_SNAKE.put("vibratorAction",            "vibrator_action");
        BLOCK_SNAKE.put("rotateBitmapFile",          "rotate_bitmap");
        BLOCK_SNAKE.put("scaleBitmapFile",           "scale_bitmap");
        BLOCK_SNAKE.put("skewBitmapFile",            "skew_bitmap");
        BLOCK_SNAKE.put("cropBitmapFileFromCenter",  "crop_bitmap_center");
        BLOCK_SNAKE.put("resizeBitmapFileRetainRatio","resize_bitmap_ratio");
        BLOCK_SNAKE.put("resizeBitmapFileToSquare",  "resize_bitmap_square");
        BLOCK_SNAKE.put("resizeBitmapFileToCircle",  "resize_bitmap_circle");
        BLOCK_SNAKE.put("resizeBitmapFileWithRoundedBorder", "resize_bitmap_rounded");
        BLOCK_SNAKE.put("setBitmapFileBrightness",   "set_bitmap_brightness");
        BLOCK_SNAKE.put("setBitmapFileContrast",     "set_bitmap_contrast");
        BLOCK_SNAKE.put("setBitmapFileColorFilter",  "set_bitmap_color_filter");
        BLOCK_SNAKE.put("getJpegRotate",             "get_jpeg_rotate");
        BLOCK_SNAKE.put("addSourceDirectly",         "add_source_directly");
    }

    // ─────────────────────────────────────────────────────────────────────────
    private static void initEventSnake() {
        EVENT_SNAKE.put("initializeLogic",          "initialize");
        EVENT_SNAKE.put("onBackPressed",            "on_back_pressed");
        EVENT_SNAKE.put("onPostCreate",             "on_post_created");
        EVENT_SNAKE.put("onStart",                  "on_start");
        EVENT_SNAKE.put("onStop",                   "on_stop");
        EVENT_SNAKE.put("onDestroy",                "on_destroy");
        EVENT_SNAKE.put("onResume",                 "on_resume");
        EVENT_SNAKE.put("onPause",                  "on_pause");
        EVENT_SNAKE.put("moreBlock",                "on_post_created");
        EVENT_SNAKE.put("onClick",                  "on_clicked");
        EVENT_SNAKE.put("onCheckedChange",          "on_check_changed");
        EVENT_SNAKE.put("onItemSelected",           "on_item_selected");
        EVENT_SNAKE.put("onItemClicked",            "on_item_clicked");
        EVENT_SNAKE.put("onItemLongClicked",        "on_item_long_clicked");
        EVENT_SNAKE.put("onTextChanged",            "on_text_changed");
        EVENT_SNAKE.put("onPageStarted",            "on_page_started");
        EVENT_SNAKE.put("onPageFinished",           "on_page_finished");
        EVENT_SNAKE.put("onProgressChanged",        "on_progress_changed");
        EVENT_SNAKE.put("onStartTrackingTouch",     "on_start_tracking_touch");
        EVENT_SNAKE.put("onStopTrackingTouch",      "on_stop_tracking_touch");
        EVENT_SNAKE.put("onAnimationStart",         "on_animation_start");
        EVENT_SNAKE.put("onAnimationEnd",           "on_animation_end");
        EVENT_SNAKE.put("onAnimationCancel",        "on_animation_cancel");
        EVENT_SNAKE.put("onBindCustomView",         "on_bind_custom_view");
        EVENT_SNAKE.put("onDateChange",             "on_date_change");
        EVENT_SNAKE.put("onChildAdded",             "on_child_added");
        EVENT_SNAKE.put("onChildChanged",           "on_child_changed");
        EVENT_SNAKE.put("onChildRemoved",           "on_child_removed");
        EVENT_SNAKE.put("onCancelled",              "on_cancelled");
        EVENT_SNAKE.put("onCreateUserComplete",     "on_create_user_complete");
        EVENT_SNAKE.put("onSignInUserComplete",     "on_sign_in_user_complete");
        EVENT_SNAKE.put("onResetPasswordEmailSent", "on_reset_password_email_sent");
        EVENT_SNAKE.put("onUploadProgress",         "on_upload_progress");
        EVENT_SNAKE.put("onDownloadProgress",       "on_download_progress");
        EVENT_SNAKE.put("onUploadSuccess",          "on_upload_success");
        EVENT_SNAKE.put("onDownloadSuccess",        "on_download_success");
        EVENT_SNAKE.put("onDeleteSuccess",          "on_delete_success");
        EVENT_SNAKE.put("onFailure",                "on_failure");
        EVENT_SNAKE.put("onPictureTaken",           "on_picture_taken");
        EVENT_SNAKE.put("onPictureTakenCancel",     "on_picture_taken_cancel");
        EVENT_SNAKE.put("onFilesPicked",            "on_files_picked");
        EVENT_SNAKE.put("onFilesPickedCancel",      "on_files_picked_cancel");
        EVENT_SNAKE.put("onAdLoaded",               "on_ad_loaded");
        EVENT_SNAKE.put("onAdFailedToLoad",         "on_ad_failed_to_load");
        EVENT_SNAKE.put("onAdOpened",               "on_ad_opened");
        EVENT_SNAKE.put("onAdClosed",               "on_ad_closed");
        EVENT_SNAKE.put("onResponse",               "on_response");
        EVENT_SNAKE.put("onErrorResponse",          "on_error_response");
        EVENT_SNAKE.put("onSpeechResult",           "on_speech_result");
        EVENT_SNAKE.put("onSpeechError",            "on_speech_error");
        EVENT_SNAKE.put("onConnected",              "on_connected");
        EVENT_SNAKE.put("onDataReceived",           "on_data_received");
        EVENT_SNAKE.put("onDataSent",               "on_data_sent");
        EVENT_SNAKE.put("onConnectionError",        "on_connection_error");
        EVENT_SNAKE.put("onConnectionStopped",      "on_connection_stopped");
        EVENT_SNAKE.put("onMapReady",               "on_map_ready");
        EVENT_SNAKE.put("onMarkerClicked",          "on_marker_clicked");
        EVENT_SNAKE.put("onSensorChanged",          "on_sensor_changed");
        EVENT_SNAKE.put("onLocationChanged",        "on_location_changed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns parameter type specs for a block opCode. */
    public static ArrayList<String> a(String opCode) {
        String[] p = BLOCK_PARAMS.get(opCode);
        if (p != null) return new ArrayList<>(Arrays.asList(p));
        return new ArrayList<>();
    }

    /** Returns parameter type specs for an event (root spec). */
    public static ArrayList<String> b(String eventName) {
        String[] p = EVENT_PARAMS.get(eventName);
        if (p != null) return new ArrayList<>(Arrays.asList(p));
        return new ArrayList<>();
    }

    /** Converts event name to snake_case for root_spec_* resource lookup. */
    public static String c(String eventName) {
        String s = EVENT_SNAKE.get(eventName);
        return s != null ? s : toSnakeCase(eventName);
    }

    /** Converts block opCode to snake_case for block_* resource lookup. */
    public static String d(String opCode) {
        String s = BLOCK_SNAKE.get(opCode);
        return s != null ? s : toSnakeCase(opCode);
    }

    // ─────────────────────────────────────────────────────────────────────────
    /** Fallback: runtime camelCase → snake_case for unknown opCodes. */
    private static String toSnakeCase(String camel) {
        if (camel == null || camel.isEmpty()) return camel;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char ch = camel.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(ch));
        }
        return sb.toString();
    }
}
