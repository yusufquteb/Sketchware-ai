package a.a.a;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * String/format utilities for block spec parsing, clipboard, and number formatting.
 *
 * Key method: FB.c(String spec) → tokenizes a block spec string into parts.
 * Example: "setText %m.textview %s" → ["setText ", "%m.textview", " ", "%s"]
 *
 * This is the enhanced Java version of the original FB.class.
 * The c() method is critical: BlockBean uses it to count parameters.
 */
public class FB {

    // ── Spec token pattern: matches %X or %X.name or plain text ──────────────
    private static final Pattern SPEC_TOKEN =
            Pattern.compile("%[smdb](?:\\.[\\w.]+)?|%[smdb]|[^%]+");

    // ── Param-only pattern (used by BlockBean.buildClassInfo) ─────────────────
    private static final Pattern PARAM_ONLY =
            Pattern.compile("%[smdb](?:\\.[\\w.]+)?|%[smdb]");

    /**
     * Tokenize a block spec string.
     * Returns a list of tokens: text segments and %X placeholders.
     *
     * Example: "add %m.listInt %d"
     *   → ["add ", "%m.listInt", " ", "%d"]
     */
    public static ArrayList<String> c(String spec) {
        ArrayList<String> tokens = new ArrayList<>();
        if (spec == null || spec.isEmpty()) return tokens;
        Matcher m = SPEC_TOKEN.matcher(spec);
        while (m.find()) {
            String token = m.group();
            if (!token.isEmpty()) tokens.add(token);
        }
        return tokens;
    }

    /**
     * Extract only the parameter tokens (%m.X, %s, %d, %b) from a spec.
     * Used to count parameters without text noise.
     */
    public static ArrayList<String> extractParams(String spec) {
        ArrayList<String> params = new ArrayList<>();
        if (spec == null || spec.isEmpty()) return params;
        Matcher m = PARAM_ONLY.matcher(spec);
        while (m.find()) params.add(m.group());
        return params;
    }

    /**
     * Returns the number of parameters in a spec string.
     */
    public static int paramCount(String spec) {
        return extractParams(spec).size();
    }

    /**
     * Check if a spec string contains any parameter placeholder.
     */
    public static boolean b(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.contains("%s") || s.contains("%d") || s.contains("%b") || s.contains("%m");
    }

    // ── Legacy int overload (char precedence) ────────────────────────────────
    public static int a(char c) {
        switch (c) {
            case '%': return 1;
            case '(': return 2;
            case ')': return 3;
            default:  return 0;
        }
    }

    /** Returns empty string (legacy stub). */
    public static String a() { return ""; }

    /** Format int with compact notation: K, M, G. */
    public static String a(int n) {
        if (n < 1000)             return String.valueOf(n);
        if (n < 1_000_000)        return new DecimalFormat("#.#K").format(n / 1000.0);
        if (n < 1_000_000_000)    return new DecimalFormat("#.#M").format(n / 1_000_000.0);
        return new DecimalFormat("#.#G").format(n / 1_000_000_000.0);
    }

    /** Base64-decode a string to byte array. */
    public static byte[] a(String s) {
        try { return Base64.decode(s, Base64.DEFAULT); }
        catch (Exception e) { return new byte[0]; }
    }

    /** Copy text to system clipboard. */
    public static void a(Context ctx, String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager)
                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        } catch (Exception e) {
            Log.w("FB", "clipboard: " + e.getMessage());
        }
    }

    /** Format int as comma-separated thousands. */
    public static String b(int n) {
        return new DecimalFormat("#,###").format(n);
    }

    /** Format int as compact file-size string (B/KB/MB/GB). */
    public static String c(int bytes) {
        if (bytes < 1024)           return bytes + " B";
        if (bytes < 1024 * 1024)    return new DecimalFormat("#.#KB").format(bytes / 1024.0);
        if (bytes < 1024*1024*1024) return new DecimalFormat("#.#MB").format(bytes / (1024.0 * 1024));
        return new DecimalFormat("#.#GB").format(bytes / (1024.0 * 1024 * 1024));
    }

    /** Base64-encode a string. */
    public static String d(String input) {
    if (input == null || input.isEmpty()) return "";
    return input
            .replace("\\n", "\n")
            .replace("\\'", "'")
            .replace("\\\"", "\"");
}

    /**
     * Build the spec display string by joining token parts.
     * Used when reconstructing a spec from head/body/tail strings.
     *
     * @param head   text before first param (may be empty)
     * @param bodies list of between-param text strings
     * @param tail   text after last param (may be empty)
     * @param params list of param type tokens (%m.view etc.)
     * @return       full spec string
     */
    public static String buildSpec(String head, ArrayList<String> bodies,
                                    String tail, ArrayList<String> params) {
        StringBuilder sb = new StringBuilder();
        if (head != null && !head.isEmpty()) sb.append(head);
        for (int i = 0; i < params.size(); i++) {
            sb.append(' ').append(params.get(i));
            if (i < bodies.size() && bodies.get(i) != null && !bodies.get(i).isEmpty()) {
                sb.append(' ').append(bodies.get(i));
            }
        }
        if (tail != null && !tail.isEmpty()) sb.append(' ').append(tail);
        return sb.toString().trim();
    }
}
