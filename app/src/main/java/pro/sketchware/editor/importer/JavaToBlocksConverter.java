package pro.sketchware.editor.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Java method code to Sketchware block JSON objects.
 *
 * Precedence (first match wins):
 *  1. Control flow: if/ifElse, repeat/for, forever/while(true), break
 *  2. View operations: setText, setVisibility, setEnabled, setAlpha, etc.
 *  3. Toast, finish, startActivity
 *  4. Variable assignment / increment / decrement
 *  5. String / Math operations
 *  6. Known method calls: dialog, webview, timer, mediaplayer, etc.
 *  7. Fallback: addSourceDirectly
 */
public final class JavaToBlocksConverter {

    private JavaToBlocksConverter() {}

    // ── Known Sketchware event names ─────────────────────────────────────────
    private static final Set<String> LIFECYCLE_EVENTS = new HashSet<>(Arrays.asList(
        "initializeLogic", "onCreate", "onStart", "onStop", "onDestroy",
        "onResume", "onPause", "onBackPressed", "onPostCreate"
    ));

    private static final Set<String> KNOWN_EVENTS = new HashSet<>(Arrays.asList(
        "onClick", "onLongClick", "onCheckedChange", "onItemSelected",
        "onItemClicked", "onItemLongClicked", "onTextChanged", "onPageStarted",
        "onPageFinished", "onProgressChanged", "onStartTrackingTouch",
        "onStopTrackingTouch", "onAnimationStart", "onAnimationEnd",
        "onAnimationCancel", "onBindCustomView", "onDateChange",
        "onChildAdded", "onChildChanged", "onChildRemoved", "onCancelled",
        "onCreateUserComplete", "onSignInUserComplete", "onResetPasswordEmailSent",
        "onUploadProgress", "onDownloadProgress", "onUploadSuccess",
        "onDownloadSuccess", "onDeleteSuccess", "onFailure", "onPictureTaken",
        "onPictureTakenCancel", "onFilesPicked", "onFilesPickedCancel",
        "onAdLoaded", "onAdFailedToLoad", "onAdOpened", "onAdClosed",
        "onResponse", "onErrorResponse", "onSpeechResult", "onSpeechError",
        "onConnected", "onDataReceived"
    ));

    // ── Spec strings for each opCode ─────────────────────────────────────────
    // These must match the strings stored in logic files
    private static String spec(String opCode) {
        switch (opCode) {
            case "setText":             return "set %m.textview text %s";
            case "getText":             return "%m.textview getText";
            case "setHint":             return "set %m.edittext hint %s";
            case "setVisible":          return "set %m.view visible %m.visible";
            case "setEnable":           return "set %m.view enable %b";
            case "setAlpha":            return "set %m.view alpha %d";
            case "setRotate":           return "set %m.view rotate %d";
            case "setScaleX":           return "set %m.view scaleX %d";
            case "setScaleY":           return "set %m.view scaleY %d";
            case "setClickable":        return "set %m.view clickable %b";
            case "setBgColor":          return "set %m.view background color %m.color";
            case "setTextColor":        return "set %m.textview color %m.color";
            case "requestFocus":        return "%m.view requestFocus";
            case "setImage":            return "set %m.imageview image %m.resource";
            case "setImageFilePath":    return "set %m.imageview image from file %s";
            case "setChecked":          return "set %m.checkbox checked %b";
            case "doToast":             return "toast %s";
            case "if":                  return "if %b then";
            case "ifElse":              return "if %b then else";
            case "forever":             return "forever";
            case "repeat":              return "repeat %d times";
            case "break":               return "break";
            case "setVarInt":           return "set int %m.varInt to %d";
            case "setVarStr":           return "set str %m.varStr to %s";
            case "setVarBoolean":       return "set bool %m.varBool to %b";
            case "increaseInt":         return "int %m.varInt ++";
            case "decreaseInt":         return "int %m.varInt --";
            case "startActivity":       return "start %m.intent";
            case "finishActivity":      return "finish";
            case "intentSetScreen":     return "set %m.intent to %m.activity";
            case "intentPutExtra":      return "put extra %m.intent key %s value %s";
            case "intentGetString":     return "getExtra %s";
            case "stringLength":        return "length of %s";
            case "stringContains":      return "%s contains %s";
            case "stringEquals":        return "%s equals %s";
            case "stringJoin":          return "join %s and %s";
            case "stringReplace":       return "%s replace %s to %s";
            case "toUpperCase":         return "toUpperCase %s";
            case "toLowerCase":         return "toLowerCase %s";
            case "trim":                return "trim %s";
            case "toString":            return "toString %d";
            case "toNumber":            return "toNumber %s";
            case "plus":                return "%d + %d";
            case "minus":               return "%d - %d";
            case "times":               return "%d * %d";
            case "divide":              return "%d / %d";
            case "rest":                return "%d mod %d";
            case "mathAbs":             return "Math.abs %d";
            case "mathSqrt":            return "Math.sqrt %d";
            case "mathMax":             return "Math.max %d and %d";
            case "mathMin":             return "Math.min %d and %d";
            case "mathRound":           return "Math.round %d";
            case "mathFloor":           return "Math.floor %d";
            case "mathCeil":            return "Math.ceil %d";
            case "random":              return "random %d to %d";
            case "addListInt":          return "add %m.listInt number %d";
            case "addListStr":          return "add %m.listStr string %s";
            case "clearList":           return "clear %m.list";
            case "lengthList":          return "length of %m.list";
            case "mapPut":              return "%m.varMap put key %s value %s";
            case "mapGet":              return "%m.varMap get key %s";
            case "mapClear":            return "%m.varMap clear";
            case "dialogSetTitle":      return "set %m.dialog title %s";
            case "dialogSetMessage":    return "set %m.dialog message %s";
            case "dialogShow":          return "%m.dialog show";
            case "dialogDismiss":       return "%m.dialog dismiss";
            case "webViewLoadUrl":      return "%m.webview load url %s";
            case "webViewGoBack":       return "%m.webview go back";
            case "webViewGoForward":    return "%m.webview go forward";
            case "mediaplayerStart":    return "%m.mediaplayer start";
            case "mediaplayerPause":    return "%m.mediaplayer pause";
            case "mediaplayerReset":    return "%m.mediaplayer reset";
            case "mediaplayerRelease":  return "%m.mediaplayer release";
            case "timerCancel":         return "%m.timer cancel";
            case "fileSetData":         return "%m.file set %s to %s";
            case "fileGetData":         return "%m.file get %s";
            case "fileRemoveData":      return "%m.file remove key %s";
            case "addSourceDirectly":   return "add source directly %s.inputOnly";
            default:                    return opCode;
        }
    }

    // ── Block builder ─────────────────────────────────────────────────────────
    public static JsonObject block(String opCode, String... params) {
        JsonObject b = new JsonObject();
        b.addProperty("opCode",    opCode);
        b.addProperty("spec",      spec(opCode));
        b.addProperty("type",      isControlBlock(opCode) ? "c" : " ");
        b.addProperty("typeName",  "");
        b.addProperty("target",    "");
        b.addProperty("nextBlock", -1);
        b.addProperty("subStack1", -1);
        b.addProperty("subStack2", -1);
        JsonArray p = new JsonArray();
        for (String param : params) p.add(param);
        b.add("parameters", p);
        return b;
    }

    private static boolean isControlBlock(String opCode) {
        return opCode.equals("if") || opCode.equals("ifElse")
            || opCode.equals("forever") || opCode.equals("repeat");
    }

    // ── Statement converter ───────────────────────────────────────────────────
    /**
     * Converts a single Java statement to the best-matching Sketchware block.
     * Returns a block JSON object (never null — falls back to addSourceDirectly).
     */
    public static JsonObject convertStatement(String stmt) {
        String s = stmt.trim();

        // ── break ──────────────────────────────────────────────────────────
        if (s.equals("break;") || s.equals("break")) {
            return block("break");
        }

        // ── finish() ──────────────────────────────────────────────────────
        if (s.equals("finish();") || s.equals("finish()")) {
            return block("finishActivity");
        }

        // ── Toast ─────────────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("Toast\\.makeText\\([^,]+,\\s*(.+),\\s*Toast\\..*\\)\\.show\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("doToast", m.group(1).trim());
            Matcher m2 = Pattern.compile("SketchwareUtil\\.toast\\((.+)\\)\\s*;?").matcher(s);
            if (m2.matches()) return block("doToast", m2.group(1).trim());
        }

        // ── setText ────────────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.setText\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("setText", m.group(1), m.group(2).trim());
        }

        // ── setHint ────────────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.setHint\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("setHint", m.group(1), m.group(2).trim());
        }

        // ── setVisibility ─────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.setVisibility\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) {
                String vis = m.group(2).trim();
                // Normalize visibility value
                if (vis.contains("VISIBLE"))   vis = "0";
                if (vis.contains("INVISIBLE")) vis = "4";
                if (vis.contains("GONE"))      vis = "8";
                return block("setVisible", m.group(1), vis);
            }
        }

        // ── setEnabled ────────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.setEnabled\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("setEnable", m.group(1), m.group(2).trim());
        }

        // ── setAlpha ──────────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.setAlpha\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("setAlpha", m.group(1), m.group(2).trim());
        }

        // ── setClickable ──────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.setClickable\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("setClickable", m.group(1), m.group(2).trim());
        }

        // ── setRotation ───────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.setRotation\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("setRotate", m.group(1), m.group(2).trim());
        }

        // ── requestFocus ──────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.requestFocus\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("requestFocus", m.group(1));
        }

        // ── webview ───────────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.loadUrl\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("webViewLoadUrl", m.group(1), m.group(2).trim());
        }
        {
            Matcher m = Pattern.compile("(\\w+)\\.goBack\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("webViewGoBack", m.group(1));
        }
        {
            Matcher m = Pattern.compile("(\\w+)\\.goForward\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("webViewGoForward", m.group(1));
        }

        // ── mediaplayer ───────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.start\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("mediaplayerStart", m.group(1));
        }
        {
            Matcher m = Pattern.compile("(\\w+)\\.pause\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("mediaplayerPause", m.group(1));
        }
        {
            Matcher m = Pattern.compile("(\\w+)\\.reset\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("mediaplayerReset", m.group(1));
        }
        {
            Matcher m = Pattern.compile("(\\w+)\\.release\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("mediaplayerRelease", m.group(1));
        }

        // ── dialog ────────────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\.setTitle\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("dialogSetTitle", m.group(1), m.group(2).trim());
        }
        {
            Matcher m = Pattern.compile("(\\w+)\\.setMessage\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("dialogSetMessage", m.group(1), m.group(2).trim());
        }
        {
            Matcher m = Pattern.compile("(\\w+)\\.show\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("dialogShow", m.group(1));
        }
        {
            Matcher m = Pattern.compile("(\\w+)\\.dismiss\\(\\)\\s*;?").matcher(s);
            if (m.matches()) return block("dialogDismiss", m.group(1));
        }

        // ── startActivity ────────────────────────────────────────────────
        {
            Matcher m = Pattern.compile("startActivity\\((.+)\\)\\s*;?").matcher(s);
            if (m.matches()) return block("startActivity", m.group(1).trim());
        }

        // ── increment / decrement ─────────────────────────────────────────
        {
            Matcher m = Pattern.compile("(\\w+)\\+\\+\\s*;?").matcher(s);
            if (m.matches()) return block("increaseInt", m.group(1));
        }
        {
            Matcher m = Pattern.compile("\\+\\+(\\w+)\\s*;?").matcher(s);
            if (m.matches()) return block("increaseInt", m.group(1));
        }
        {
            Matcher m = Pattern.compile("(\\w+)--\\s*;?").matcher(s);
            if (m.matches()) return block("decreaseInt", m.group(1));
        }

        // ── variable assignment ───────────────────────────────────────────
        // Only match simple literal / arithmetic assignments — NOT method calls,
        // object instantiations, or field accesses (those go to addSourceDirectly).
        {
            Matcher m = Pattern.compile("String\\s+(\\w+)\\s*=\\s*(.+?);?").matcher(s);
            if (m.matches()) {
                String value = m.group(2).trim();
                // Only convert if value is a string literal or simple variable
                if (value.startsWith("\"") || (!value.contains("(") && !value.contains("new "))) {
                    return block("setVarStr", m.group(1).trim(), value);
                }
            }
        }
        {
            Matcher m = Pattern.compile("boolean\\s+(\\w+)\\s*=\\s*(.+?);?").matcher(s);
            if (m.matches()) {
                String value = m.group(2).trim();
                if (value.equals("true") || value.equals("false")) {
                    return block("setVarBoolean", m.group(1).trim(), value);
                }
            }
        }
        {
            // int varName = literal;  or  varName = literal;
            // Guard: skip if value contains a method call or object creation
            Matcher m = Pattern.compile("(?:int\\s+)?(\\w+)\\s*=\\s*(.+?);?").matcher(s);
            if (m.matches()) {
                String varName = m.group(1).trim();
                String value = m.group(2).trim();
                // Skip complex right-hand sides (method calls, new, field access chains)
                if (value.contains("(") || value.contains("new ") || value.contains(".")) {
                    // fall through to addSourceDirectly
                } else if (value.matches("-?\\d+(\\.\\d+)?") || value.matches("[\\w+\\-*/\\s]+")) {
                    // Pure numeric literal or simple arithmetic between variables/numbers
                    if (value.startsWith("\"")) {
                        return block("setVarStr", varName, value);
                    } else if (value.equals("true") || value.equals("false")) {
                        return block("setVarBoolean", varName, value);
                    } else {
                        return block("setVarInt", varName, value);
                    }
                }
            }
        }

        // ── if/ifElse — detect but return as addSourceDirectly with note ──
        // Control blocks with subStacks require recursive conversion (complex)
        // For now, wrap in addSourceDirectly and flag it
        if (s.startsWith("if (") || s.startsWith("if(")) {
            return block("addSourceDirectly", s);
        }
        if (s.startsWith("for (") || s.startsWith("for(") || s.startsWith("while (") || s.startsWith("while(")) {
            return block("addSourceDirectly", s);
        }

        // ── fallback: addSourceDirectly ───────────────────────────────────
        return block("addSourceDirectly", s);
    }

    // ── Method parser ─────────────────────────────────────────────────────────
    public static class ParsedMethod {
        public final String name;
        public final String signature;   // full signature line
        public final String body;        // content between { }
        public final boolean isPrivate;
        public final boolean isPublic;
        public final boolean isOverride;
        public final String params;      // raw param string

        public ParsedMethod(String name, String sig, String body,
                            boolean isPrivate, boolean isPublic,
                            boolean isOverride, String params) {
            this.name = name; this.signature = sig; this.body = body;
            this.isPrivate = isPrivate; this.isPublic = isPublic;
            this.isOverride = isOverride; this.params = params;
        }
    }

    public enum MethodKind { LIFECYCLE_EVENT, KNOWN_EVENT, MORE_BLOCK }

    public static MethodKind classify(ParsedMethod m) {
        if (LIFECYCLE_EVENTS.contains(m.name)) return MethodKind.LIFECYCLE_EVENT;
        if (KNOWN_EVENTS.contains(m.name))     return MethodKind.KNOWN_EVENT;
        return MethodKind.MORE_BLOCK;
    }

    /**
     * Parses a Java source snippet to extract method declarations.
     * Handles both full class bodies and multiple method definitions.
     * Skips methods inside anonymous inner classes / lambdas (depth > 1).
     */
    public static List<ParsedMethod> parseMethods(String source) {
        List<ParsedMethod> methods = new ArrayList<>();
        if (source == null || source.trim().isEmpty()) return methods;

        // Pre-compute brace depth at every character (ignoring string/comment content)
        // so we can reject matches that are inside anonymous inner classes.
        int[] braceDepth = computeBraceDepths(source);

        // Match method declarations: optional @Override, access modifier, void/type, name, params, body
        Pattern methodPat = Pattern.compile(
            "(?:@Override\\s*)?((?:public|private|protected)\\s+(?:static\\s+)?(?:void|\\w+)\\s+(\\w+)\\s*\\(([^)]*)\\))\\s*\\{",
            Pattern.MULTILINE
        );
        Matcher m = methodPat.matcher(source);

        // Determine whether the source contains a top-level class declaration.
        // If it does, valid methods sit at brace depth 1 (inside the class body).
        // If it's a bare snippet with no class wrapper, depth 0 is acceptable.
        boolean hasClassWrapper = source.contains(" class ") || source.startsWith("class ");

        while (m.find()) {
            int depthAtMatch = braceDepth[m.start()];
            // Skip methods that are inside anonymous/inner classes
            if (hasClassWrapper && depthAtMatch > 1) continue;
            if (!hasClassWrapper && depthAtMatch > 0) continue;

            String sig     = m.group(1);
            String name    = m.group(2);
            String params  = m.group(3).trim();
            boolean isOverride = m.start() > 0 && source.substring(Math.max(0, m.start() - 20), m.start()).contains("@Override");
            boolean isPrivate  = sig.contains("private");
            boolean isPublic   = sig.contains("public");

            // Extract body between matching { }
            int bodyStart = m.end();
            int depth = 1;
            int i = bodyStart;
            while (i < source.length() && depth > 0) {
                char c = source.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                i++;
            }
            String body = source.substring(bodyStart, i - 1).trim();
            methods.add(new ParsedMethod(name, sig, body, isPrivate, isPublic, isOverride, params));
        }
        return methods;
    }

    /**
     * Computes the brace-depth at each character position in {@code source},
     * ignoring content inside string literals and comments so that braces
     * inside strings/comments don't affect the count.
     */
    private static int[] computeBraceDepths(String source) {
        int[] depths = new int[source.length()];
        int depth = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = (i + 1 < source.length()) ? source.charAt(i + 1) : 0;
            if (inLineComment) {
                if (c == '\n') inLineComment = false;
            } else if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
            } else if (inString) {
                if (c == '\\') { i++; } // skip escaped char
                else if (c == '"') inString = false;
            } else if (inChar) {
                if (c == '\\') { i++; }
                else if (c == '\'') inChar = false;
            } else {
                if (c == '/' && next == '/') { inLineComment = true; i++; }
                else if (c == '/' && next == '*') { inBlockComment = true; i++; }
                else if (c == '"') inString = true;
                else if (c == '\'') inChar = true;
                else if (c == '{') depth++;
                else if (c == '}') depth = Math.max(0, depth - 1);
            }
            depths[i] = depth;
        }
        return depths;
    }

    /**
     * Convert a method body to a list of block JSON objects.
     * Each statement becomes one or more blocks.
     */
    public static List<JsonObject> convertMethodBody(String body) {
        List<JsonObject> blocks = new ArrayList<>();
        List<String> stmts = JavaToBlocksPreprocessor.statements(body);
        for (String stmt : stmts) {
            if (!stmt.trim().isEmpty()) {
                blocks.add(convertStatement(stmt));
            }
        }
        return blocks;
    }
}
