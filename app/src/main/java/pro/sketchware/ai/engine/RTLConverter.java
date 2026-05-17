package pro.sketchware.ai.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RTLConverter — converts Android XML layouts to RTL (right-to-left) compatible
 * form using PURE LOGIC. No AI call is made.
 *
 * <p>Transformations applied:
 * <ol>
 *   <li>Replace attribute names: {@code paddingLeft→paddingStart}, {@code marginRight→marginEnd}, etc.</li>
 *   <li>Replace gravity values: {@code left→start}, {@code right→end}</li>
 *   <li>Add {@code android:layoutDirection="locale"} to the root element</li>
 *   <li>Add {@code android:textDirection="anyRtl"} to all TextView/EditText elements</li>
 *   <li>Mirror absolute gravity in RelativeLayout rules</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   RTLConverter.ConversionResult result = RTLConverter.convert(xml);
 *   if (result.changesApplied > 0) {
 *       applyToLayout(result.xml);
 *   }
 * </pre>
 */
public final class RTLConverter {

    private RTLConverter() {}

    /** Result of a conversion pass. */
    public static class ConversionResult {
        /** The converted XML. Equals input if no changes were needed. */
        public final String xml;
        /** How many attribute replacements were applied. */
        public final int changesApplied;
        /** Human-readable summary of what was changed. */
        public final List<String> changeLog;

        ConversionResult(String xml, int changes, List<String> log) {
            this.xml           = xml;
            this.changesApplied = changes;
            this.changeLog     = log;
        }
    }

    // ── Attribute name mapping: old → new ────────────────────────────────────

    private static final Map<String, String> ATTR_RENAMES = new HashMap<>();
    static {
        // Padding
        ATTR_RENAMES.put("android:paddingLeft",  "android:paddingStart");
        ATTR_RENAMES.put("android:paddingRight", "android:paddingEnd");
        // Margin
        ATTR_RENAMES.put("android:layout_marginLeft",  "android:layout_marginStart");
        ATTR_RENAMES.put("android:layout_marginRight", "android:layout_marginEnd");
        // Drawable compounds
        ATTR_RENAMES.put("android:drawableLeft",  "android:drawableStart");
        ATTR_RENAMES.put("android:drawableRight", "android:drawableEnd");
        // Relative layout anchor rules
        ATTR_RENAMES.put("android:layout_toLeftOf",    "android:layout_toStartOf");
        ATTR_RENAMES.put("android:layout_toRightOf",   "android:layout_toEndOf");
        ATTR_RENAMES.put("android:layout_alignLeft",   "android:layout_alignStart");
        ATTR_RENAMES.put("android:layout_alignRight",  "android:layout_alignEnd");
        ATTR_RENAMES.put("android:layout_alignParentLeft",  "android:layout_alignParentStart");
        ATTR_RENAMES.put("android:layout_alignParentRight", "android:layout_alignParentEnd");
    }

    // ── Gravity value replacements ────────────────────────────────────────────

    /** Simple word-boundary gravity token replacements. */
    private static final Map<String, String> GRAVITY_REPLACEMENTS = new HashMap<>();
    static {
        GRAVITY_REPLACEMENTS.put("left",  "start");
        GRAVITY_REPLACEMENTS.put("right", "end");
        // Compound tokens — Android allows gravity="left|center_vertical"
        GRAVITY_REPLACEMENTS.put("left|", "start|");
        GRAVITY_REPLACEMENTS.put("|left", "|start");
        GRAVITY_REPLACEMENTS.put("right|", "end|");
        GRAVITY_REPLACEMENTS.put("|right", "|end");
    }

    // ── Gravity attribute names to patch ──────────────────────────────────────

    private static final List<String> GRAVITY_ATTRS = Arrays.asList(
            "android:gravity",
            "android:layout_gravity"
    );

    // ── Text views that need textDirection ────────────────────────────────────

    private static final List<String> TEXT_VIEWS = Arrays.asList(
            "TextView", "EditText", "AutoCompleteTextView",
            "MultiAutoCompleteTextView", "CheckBox", "RadioButton",
            "Button", "ToggleButton", "Switch"
    );

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Converts an Android XML layout to RTL-compatible form.
     *
     * @param xml the input layout XML
     * @return a {@link ConversionResult} with the converted XML and change summary
     */
    public static ConversionResult convert(String xml) {
        if (xml == null || xml.isEmpty()) {
            return new ConversionResult(xml, 0, new ArrayList<>());
        }

        List<String> log    = new ArrayList<>();
        String       result = xml;
        int          total  = 0;

        // 1. Rename attribute names
        for (Map.Entry<String, String> entry : ATTR_RENAMES.entrySet()) {
            String old = entry.getKey();
            String neu = entry.getValue();
            if (result.contains(old)) {
                result = result.replace(old, neu);
                int count = countOccurrences(xml, old);
                total += count;
                log.add("Renamed " + count + "× \"" + old + "\" → \"" + neu + "\"");
            }
        }

        // 2. Patch gravity values
        int gravityChanges = 0;
        for (String gravityAttr : GRAVITY_ATTRS) {
            StringBuffer sb  = new StringBuffer();
            Pattern pat = Pattern.compile(
                    Pattern.quote(gravityAttr) + "\\s*=\\s*\"([^\"]*)\"");
            Matcher m = pat.matcher(result);
            while (m.find()) {
                String value    = m.group(1);
                String newValue = replaceGravityValue(value);
                if (!newValue.equals(value)) {
                    gravityChanges++;
                    m.appendReplacement(sb,
                            Matcher.quoteReplacement(gravityAttr + "=\"" + newValue + "\""));
                } else {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                }
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        if (gravityChanges > 0) {
            total += gravityChanges;
            log.add("Patched " + gravityChanges + " gravity value(s): left→start, right→end");
        }

        // 3. Add layoutDirection="locale" to root if missing
        if (!result.contains("android:layoutDirection")) {
            result = injectAttributeIntoRoot(result, "android:layoutDirection=\"locale\"");
            if (result.contains("android:layoutDirection=\"locale\"")) {
                total++;
                log.add("Added android:layoutDirection=\"locale\" to root");
            }
        }

        // 4. Add textDirection="anyRtl" to text views that lack it
        int textDirAdded = 0;
        for (String tag : TEXT_VIEWS) {
            textDirAdded += injectTextDirectionToTag(tag, result);
        }
        if (textDirAdded > 0) {
            // Need to rebuild result with textDirection injected
            result = injectTextDirectionAll(result);
            total += textDirAdded;
            log.add("Added android:textDirection=\"anyRtl\" to " + textDirAdded + " text view(s)");
        }

        return new ConversionResult(result, total, log);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Replaces gravity token values: "left" → "start", "right" → "end".
     * Handles compound values like "right|center_vertical".
     */
    private static String replaceGravityValue(String value) {
        if (value == null) return "";
        String result = value;
        // Word-boundary token matching
        result = result.replaceAll("(?<![a-zA-Z_])left(?![a-zA-Z_])",  "start");
        result = result.replaceAll("(?<![a-zA-Z_])right(?![a-zA-Z_])", "end");
        return result;
    }

    /**
     * Injects an attribute into the root element's opening tag.
     * Handles both self-closing and regular tags.
     */
    private static String injectAttributeIntoRoot(String xml, String attribute) {
        // Find first START_TAG (skip XML declaration)
        Pattern tagPat = Pattern.compile("<([A-Za-z][A-Za-z0-9._:]*)");
        Matcher m      = tagPat.matcher(xml);
        if (!m.find()) return xml;
        int tagStart = m.start();

        // Find the end of this tag (either > or />)
        int pos = tagStart;
        boolean inString = false;
        char stringChar = 0;
        while (pos < xml.length()) {
            char c = xml.charAt(pos);
            if (inString) {
                if (c == stringChar) inString = false;
            } else {
                if (c == '"' || c == '\'') { inString = true; stringChar = c; }
                else if (c == '>' || (c == '/' && pos + 1 < xml.length() && xml.charAt(pos + 1) == '>')) {
                    break;
                }
            }
            pos++;
        }
        if (pos >= xml.length()) return xml;

        return xml.substring(0, pos) + " " + attribute + xml.substring(pos);
    }

    /**
     * Counts occurrences of {@code needle} in {@code haystack}.
     */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx   = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /**
     * Counts how many text-view tags lack android:textDirection.
     * (Used for the change log — actual injection done by injectTextDirectionAll.)
     */
    private static int injectTextDirectionToTag(String tag, String xml) {
        int count = 0;
        Pattern pat = Pattern.compile("<" + Pattern.quote(tag) + "[\\s/>]");
        Matcher m = pat.matcher(xml);
        while (m.find()) {
            // Check if textDirection is already in this tag
            int tagEnd = findTagEnd(xml, m.start());
            if (tagEnd >= 0) {
                String tagText = xml.substring(m.start(), tagEnd);
                if (!tagText.contains("android:textDirection")) count++;
            }
        }
        return count;
    }

    /** Injects android:textDirection="anyRtl" into all text-view tags that lack it. */
    private static String injectTextDirectionAll(String xml) {
        String result = xml;
        for (String tag : TEXT_VIEWS) {
            Pattern pat = Pattern.compile("<" + Pattern.quote(tag) + "([\\s>])");
            Matcher m   = Pattern.compile("<" + Pattern.quote(tag) + "[\\s/>]").matcher(result);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                int tagStart = m.start();
                int tagEnd   = findTagEnd(result, tagStart);
                if (tagEnd < 0) { m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0))); continue; }
                String tagText = result.substring(tagStart, tagEnd);
                if (tagText.contains("android:textDirection")) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                } else {
                    // Inject before the closing > or />
                    String replaced = m.group(0).replaceFirst(
                            "([\\s/>])$",
                            " android:textDirection=\"anyRtl\"$1");
                    m.appendReplacement(sb, Matcher.quoteReplacement(replaced));
                }
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    /** Finds the index just after the closing '>' of the tag starting at {@code tagStart}. */
    private static int findTagEnd(String xml, int tagStart) {
        boolean inString = false;
        char stringChar  = 0;
        for (int i = tagStart; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (inString) {
                if (c == stringChar) inString = false;
            } else {
                if (c == '"' || c == '\'') { inString = true; stringChar = c; }
                else if (c == '>') return i + 1;
            }
        }
        return -1;
    }
}
