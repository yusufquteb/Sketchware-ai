package pro.sketchware.compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FileUtil;

/**
 * Produces Java-7/8-safe temporary source trees from Sketchware-generated sources.
 *
 * Transformations applied (in order):
 *  1. Arrow-switch expressions  → classic switch statements        (Java 8 compat)
 *  2. Typed-param lambda listeners → anonymous inner classes       (Java 7 compat)
 *     e.g.  foo(View _v -> { ... })  →  foo(new View.OnClickListener(){ onClick(View _v){...} })
 *
 * The lambda conversion uses a character-level brace counter so it handles
 * multi-line bodies with nested if/else/try blocks correctly.
 */
public final class LegacyJavaSourceNormalizer {

    private LegacyJavaSourceNormalizer() {
    }

    public static String normalizeDirectoryToTemp(String sourceDirPath, String tempRootPath) {
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists()) {
            return sourceDirPath;
        }
        File tempDir = new File(tempRootPath);
        if (tempDir.exists()) {
            FileUtil.deleteFile(tempDir.getAbsolutePath());
        }
        copyRecursive(sourceDir, tempDir);
        normalizeTree(tempDir);
        return tempDir.getAbsolutePath();
    }

    private static void normalizeTree(File root) {
        List<File> files = new ArrayList<>();
        collectJavaFiles(root, files);
        for (File file : files) {
            String code = FileUtil.readFileIfExist(file.getAbsolutePath());
            String normalized = normalizeJavaFile(code);
            if (!code.equals(normalized)) {
                FileUtil.writeFile(file.getAbsolutePath(), normalized);
            }
        }
    }

    public static String normalizeJavaFile(String code) {
        return normalizeJava(code);
    }

    public static String normalizeJava(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        code = normalizeArrowSwitchReturnExpression(code);
        code = normalizeArrowSwitchStatement(code);
        code = normalizeLambdaListeners(code);
        return code;
    }

    // ── Lambda → anonymous-class conversion ──────────────────────────────────

    /**
     * Converts Sketchware-style typed-parameter lambda listeners to anonymous
     * inner classes that compile under Java 1.7.
     *
     * Handles the two patterns emitted by older Sketchware code generators:
     *
     *   Pattern A (typed param):
     *     someMethod(View _v -> {
     *         ...body (may contain nested braces)...
     *     });
     *   →
     *     someMethod(new android.view.View.OnClickListener() {
     *         @Override public void onClick(android.view.View _v) {
     *             ...body...
     *         }
     *     });
     *
     * Uses a character-level brace-depth counter so nested if/for/try blocks
     * inside the body are handled correctly regardless of line count.
     */
    private static String normalizeLambdaListeners(String code) {
        // Matches: ( SomeType paramName -> {
        // Group 1 = type (e.g. "View"), group 2 = param name
        Pattern openPattern = Pattern.compile(
                "\\(\\s*((?:[A-Za-z_$][\\w$.]*\\.)*[A-Za-z_$][\\w$]*)\\s+([A-Za-z_$][\\w$]*)\\s*->\\s*\\{");

        StringBuilder result = new StringBuilder();
        int pos = 0;

        Matcher m = openPattern.matcher(code);
        while (m.find(pos)) {
            // Check this isn't inside a string literal or comment (simple heuristic)
            // by verifying the preceding context on the same line doesn't look like
            // it's inside quotes. We rely on the fact that generated code doesn't
            // put these patterns inside string literals.

            String type  = m.group(1);   // e.g. "View"
            String param = m.group(2);   // e.g. "_v"

            // Skip obvious non-listener patterns: method signatures, casts, etc.
            // A listener lambda always follows "(" immediately or after whitespace.
            // The match starts with "(" so we already know that.

            // Append everything up to and including the "(" before the type
            int lambdaStart = m.start();   // position of "("
            result.append(code, pos, lambdaStart);

            // Now find the matching closing "})" using brace depth counting.
            // m.end() points just after "{"
            int bodyStart = m.end();       // index of char after "{"
            int depth = 1;
            int i = bodyStart;
            while (i < code.length() && depth > 0) {
                char c = code.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                i++;
            }
            // i now points to char after the closing "}"
            // The pattern ends with "})" or "});" — consume optional whitespace + ")"
            int bodyEnd = i - 1; // index of the closing "}"
            String body = code.substring(bodyStart, bodyEnd);

            // Consume the ")" after "}" (and optional ";")
            // Find the ")" that closes the method call
            int afterClose = i; // skip the "}"
            // skip whitespace
            while (afterClose < code.length() && code.charAt(afterClose) == ' ') afterClose++;
            // expect ")"
            if (afterClose < code.length() && code.charAt(afterClose) == ')') {
                afterClose++; // skip ")"
            }

            // Build the replacement anonymous class
            String fqType = qualifyViewType(type);
            result.append("(new ").append(fqType).append(".OnClickListener() {\n")
                  .append("    @Override public void onClick(").append(fqType).append(" ").append(param).append(") {")
                  .append(body)
                  .append("    }\n")
                  .append("})");

            pos = afterClose;
        }

        result.append(code, pos, code.length());
        return result.toString();
    }

    /**
     * Maps short View type names to their fully-qualified equivalents.
     * Falls back to {@code android.view.View} for unknown types.
     */
    private static String qualifyViewType(String type) {
        switch (type) {
            case "View":            return "android.view.View";
            case "ViewGroup":       return "android.view.ViewGroup";
            case "AdapterView":     return "android.widget.AdapterView";
            case "CompoundButton":  return "android.widget.CompoundButton";
            case "SeekBar":         return "android.widget.SeekBar";
            default:
                return type.contains(".") ? type : "android.view." + type;
        }
    }

    // ── Switch-expression normalisation (unchanged from original) ─────────────

    private static String normalizeArrowSwitchReturnExpression(String code) {
        Pattern pattern = Pattern.compile("return\\s+switch\\s*\\(([^)]*)\\)\\s*\\{([\\s\\S]*?)\\};", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(code);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            String body = matcher.group(2);
            String replacement = "switch (" + expr + ") {\n" + convertArrowCases(body, true) + "}\n";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String normalizeArrowSwitchStatement(String code) {
        Pattern pattern = Pattern.compile("switch\\s*\\(([^)]*)\\)\\s*\\{([\\s\\S]*?)\\}", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(code);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String full = matcher.group(0);
            String body = matcher.group(2);
            if (!body.contains("->")) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(full));
                continue;
            }
            String expr = matcher.group(1).trim();
            String replacement = "switch (" + expr + ") {\n" + convertArrowCases(body, false) + "}";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String convertArrowCases(String body, boolean returnStatements) {
        StringBuilder sb = new StringBuilder();
        Pattern casePattern = Pattern.compile("(case\\s+[^\\n:]+|default)\\s*->\\s*([^;{}]+);", Pattern.MULTILINE);
        Matcher matcher = casePattern.matcher(body);
        while (matcher.find()) {
            sb.append(matcher.group(1)).append(":\n    ");
            if (returnStatements) {
                sb.append("return ").append(matcher.group(2).trim()).append(";\n");
            } else {
                sb.append(matcher.group(2).trim()).append(";\n    break;\n");
            }
        }
        return sb.toString();
    }

    // ── File utilities ────────────────────────────────────────────────────────

    private static void collectJavaFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectJavaFiles(child, out);
            } else if (child.getName().endsWith(".java")) {
                out.add(child);
            }
        }
    }

    private static void copyRecursive(File source, File target) {
        if (source.isDirectory()) {
            FileUtil.makeDir(target.getAbsolutePath());
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) {
                copyRecursive(child, new File(target, child.getName()));
            }
        } else {
            FileUtil.copyFile(source.getAbsolutePath(), target.getAbsolutePath());
        }
    }
}
