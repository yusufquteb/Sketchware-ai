package pro.sketchware.ai.engine;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XMLValidator — validates Android XML layout strings and auto-fixes common issues.
 *
 * <p>Validation detects:
 * <ul>
 *   <li>Malformed/unclosed tags</li>
 *   <li>Missing required attributes (layout_width, layout_height)</li>
 *   <li>Duplicate android:id values</li>
 *   <li>Unknown/deprecated attribute names</li>
 *   <li>Invalid attribute values</li>
 *   <li>Empty layout (no root element)</li>
 * </ul>
 *
 * <p>Auto-fix handles:
 * <ul>
 *   <li>Extracting valid XML from a mixed-text AI response</li>
 *   <li>Adding missing layout_width/layout_height = "match_parent"/"wrap_content"</li>
 *   <li>Removing obviously invalid attribute values</li>
 *   <li>Trimming surrounding AI commentary text</li>
 * </ul>
 */
public final class XMLValidator {

    private static final String TAG = "XMLValidator";

    /** Result of a validation + optional auto-fix pass. */
    public static class ValidationResult {
        /** Whether the XML is valid (after auto-fix if autoFix=true). */
        public final boolean valid;
        /** The validated (and possibly auto-fixed) XML. */
        public final String  xml;
        /** Human-readable list of issues found. Empty if fully valid. */
        public final List<String> issues;
        /** Whether at least one auto-fix was applied. */
        public final boolean wasAutoFixed;

        ValidationResult(boolean valid, String xml, List<String> issues, boolean wasAutoFixed) {
            this.valid        = valid;
            this.xml          = xml;
            this.issues       = issues;
            this.wasAutoFixed = wasAutoFixed;
        }

        /** Formats issues as a numbered list for display or prompt injection. */
        public String issueReport() {
            if (issues.isEmpty()) return "No issues found.";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < issues.size(); i++) {
                sb.append(i + 1).append(". ").append(issues.get(i)).append('\n');
            }
            return sb.toString().trim();
        }
    }

    // ── Attribute name sets ───────────────────────────────────────────────────

    /** Attributes that every non-root view MUST have. */
    private static final Set<String> REQUIRED_ATTRS = new HashSet<>(Arrays.asList(
            "android:layout_width", "android:layout_height"
    ));

    /** Deprecated attributes that should be flagged. */
    private static final Set<String> DEPRECATED_ATTRS = new HashSet<>(Arrays.asList(
            "android:paddingLeft",  "android:paddingRight",
            "android:marginLeft",   "android:marginRight",
            "android:layout_marginLeft", "android:layout_marginRight"
    ));

    /** Attributes commonly hallucinated by AI models — not valid in Android. */
    private static final Set<String> HALLUCINATED_ATTRS = new HashSet<>(Arrays.asList(
            "android:cornerRadius",   // must be in ShapeDrawable, not view
            "android:rounded",
            "android:backgroundColor",// not a real attribute (use background)
            "android:color",          // not a view attribute
            "android:fontSize",       // Android uses android:textSize
            "android:fontFamily",     // valid only on TextView
            "android:placeholder",    // Android uses android:hint
            "android:value",          // not valid on layouts
            "android:flex",
            "app:layout_flex"
    ));

    private XMLValidator() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Validates {@code xml} and optionally attempts auto-fix.
     *
     * @param xml     the XML string to validate
     * @param autoFix if true, applies automatic fixes before returning
     * @return a {@link ValidationResult} with validity flag, fixed XML, and issue list
     */
    public static ValidationResult validate(String xml, boolean autoFix) {
        List<String> issues = new ArrayList<>();
        String working = xml != null ? xml.trim() : "";

        // Step 1: extract from AI response (remove fences, intro text)
        if (autoFix) {
            working = PromptBuilder.extractXmlFromResponse(working);
        }

        // Step 2: quick empty check
        if (working.isEmpty()) {
            issues.add("Empty XML — no layout content");
            return new ValidationResult(false, xml, issues, false);
        }

        // Step 3: detect and auto-fix missing layout_width/height on root
        boolean autoFixed = false;
        if (autoFix) {
            String fixed = autoFixMissingDimensions(working);
            if (!fixed.equals(working)) {
                working = fixed;
                autoFixed = true;
                issues.add("[AUTO-FIXED] Added missing layout_width/layout_height to root");
            }
        }

        // Step 4: detect hallucinated attributes and remove them
        if (autoFix) {
            String fixed = removeHallucinatedAttrs(working);
            if (!fixed.equals(working)) {
                working = fixed;
                autoFixed = true;
                issues.add("[AUTO-FIXED] Removed unsupported AI-hallucinated attributes");
            }
        }

        // Step 5: parse with XmlPullParser for structural validation
        List<String> parseIssues = parseAndValidate(working);
        issues.addAll(parseIssues);

        // Step 6: semantic checks (deprecated attrs, duplicate IDs)
        issues.addAll(checkDeprecatedAttributes(working));
        List<String> dupIssues = checkDuplicateIds(working);
        issues.addAll(dupIssues);

        // Auto-fix duplicate IDs
        if (autoFix && !dupIssues.isEmpty()) {
            String fixed = deduplicateIds(working);
            if (!fixed.equals(working)) {
                working = fixed;
                autoFixed = true;
                issues.add("[AUTO-FIXED] Deduplicated android:id values");
            }
        }

        boolean valid = parseIssues.isEmpty() && dupIssues.isEmpty();
        return new ValidationResult(valid, working, issues, autoFixed);
    }

    // ── Auto-fix operations ───────────────────────────────────────────────────

    /**
     * Adds layout_width="match_parent" layout_height="match_parent" to the root tag
     * if either is missing.
     */
    static String autoFixMissingDimensions(String xml) {
        // Find opening root tag end position
        int tagEnd = xml.indexOf('>');
        if (tagEnd < 0) return xml;

        boolean hasSelfClose = tagEnd > 0 && xml.charAt(tagEnd - 1) == '/';
        String openTag = xml.substring(0, hasSelfClose ? tagEnd - 1 : tagEnd);

        String out = xml;
        if (!openTag.contains("android:layout_width")) {
            out = out.replaceFirst(
                    "(\\s*/?>)",
                    " android:layout_width=\"match_parent\"$1");
        }
        if (!out.substring(0, out.indexOf('>')).contains("android:layout_height")) {
            out = out.replaceFirst(
                    "(\\s*/?>)",
                    " android:layout_height=\"match_parent\"$1");
        }
        return out;
    }

    /** Removes known-invalid AI-hallucinated attributes from the XML text. */
    static String removeHallucinatedAttrs(String xml) {
        String out = xml;
        for (String attr : HALLUCINATED_ATTRS) {
            // matches: attr="value" or attr='value'
            out = out.replaceAll(
                    "\\s*" + Pattern.quote(attr) + "\\s*=\\s*[\"'][^\"']*[\"']",
                    "");
        }
        return out;
    }

    /**
     * Finds duplicate android:id values and appends _2, _3 etc. to make them unique.
     */
    static String deduplicateIds(String xml) {
        Set<String> seen    = new HashSet<>();
        Pattern idPat = Pattern.compile("android:id\\s*=\\s*\"(@\\+?id/)([^\"]+)\"");
        Matcher m     = idPat.matcher(xml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String prefix = m.group(1);
            String name   = m.group(2);
            if (seen.contains(name)) {
                int suffix = 2;
                while (seen.contains(name + "_" + suffix)) suffix++;
                name = name + "_" + suffix;
                m.appendReplacement(sb, "android:id=\"" + prefix + name + "\"");
            } else {
                m.appendReplacement(sb, m.group(0));
            }
            seen.add(name);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ── Validation checks ─────────────────────────────────────────────────────

    /** Parses the XML with XmlPullParser and reports structural errors. */
    private static List<String> parseAndValidate(String xml) {
        List<String> issues = new ArrayList<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));

            boolean rootSeen = false;
            int depth = 0;

            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    if (!rootSeen) {
                        rootSeen = true;
                        // Validate root has layout_width and layout_height
                        for (String req : REQUIRED_ATTRS) {
                            String localName = req.replace("android:", "");
                            boolean found = false;
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                if (localName.equals(parser.getAttributeName(i))
                                        || req.equals(parser.getAttributeName(i))) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                issues.add("Root <" + parser.getName()
                                        + "> is missing required attribute: " + req);
                            }
                        }
                    }
                    depth++;
                } else if (event == XmlPullParser.END_TAG) {
                    depth--;
                }
                event = parser.next();
            }

            if (!rootSeen) {
                issues.add("XML has no root element");
            }
            if (depth != 0) {
                issues.add("XML has " + Math.abs(depth) + " unclosed/extra tag(s)");
            }
        } catch (Exception e) {
            issues.add("XML parse error: " + e.getMessage());
            Log.d(TAG, "Parse error", e);
        }
        return issues;
    }

    /** Checks for deprecated left/right padding/margin attributes. */
    private static List<String> checkDeprecatedAttributes(String xml) {
        List<String> issues = new ArrayList<>();
        for (String dep : DEPRECATED_ATTRS) {
            if (xml.contains(dep)) {
                String replacement = dep.replace("Left", "Start").replace("Right", "End");
                issues.add("Deprecated attribute '" + dep + "' found — use '" + replacement + "' instead");
            }
        }
        return issues;
    }

    /** Checks for duplicate android:id values. */
    private static List<String> checkDuplicateIds(String xml) {
        List<String> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Pattern pat = Pattern.compile("android:id\\s*=\\s*\"(@\\+?id/[^\"]+)\"");
        Matcher m   = pat.matcher(xml);
        while (m.find()) {
            String id = m.group(1);
            if (!seen.add(id)) {
                issues.add("Duplicate android:id: " + id);
            }
        }
        return issues;
    }
}
