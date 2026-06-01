package pro.sketchware.editor.importer;

import java.util.ArrayList;
import java.util.List;

public final class JavaToBlocksPreprocessor {
    private JavaToBlocksPreprocessor() {}

    /**
     * Splits a method body into top-level statements.
     *
     * Rules:
     *  - Split on ';' when brace/paren depth == 0
     *  - A brace block that closes at depth 0 (if/else/for/while/try body)
     *    is emitted as one statement including any trailing else/catch/finally
     *  - String and char literals are skipped to avoid false delimiters
     *  - Line and block comments are skipped
     */
    public static List<String> statements(String methodBody) {
        List<String> out = new ArrayList<>();
        if (methodBody == null) return out;

        int len = methodBody.length();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        int parenDepth = 0;
        boolean inLineComment  = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar   = false;

        int i = 0;
        while (i < len) {
            char c = methodBody.charAt(i);
            char next = (i + 1 < len) ? methodBody.charAt(i + 1) : 0;

            // ── comment detection ──────────────────────────────────────
            if (!inString && !inChar) {
                if (inLineComment) {
                    current.append(c);
                    if (c == '\n') inLineComment = false;
                    i++;
                    continue;
                }
                if (inBlockComment) {
                    current.append(c);
                    if (c == '*' && next == '/') {
                        current.append(next);
                        inBlockComment = false;
                        i += 2;
                    } else {
                        i++;
                    }
                    continue;
                }
                if (c == '/' && next == '/') { inLineComment = true;  current.append(c); i++; continue; }
                if (c == '/' && next == '*') { inBlockComment = true; current.append(c); i++; continue; }
            }

            // ── string / char literals ─────────────────────────────────
            if (!inLineComment && !inBlockComment) {
                if (inString) {
                    current.append(c);
                    if (c == '\\') { i++; if (i < len) { current.append(methodBody.charAt(i)); } }
                    else if (c == '"') inString = false;
                    i++;
                    continue;
                }
                if (inChar) {
                    current.append(c);
                    if (c == '\\') { i++; if (i < len) { current.append(methodBody.charAt(i)); } }
                    else if (c == '\'') inChar = false;
                    i++;
                    continue;
                }
                if (c == '"')  { inString = true;  current.append(c); i++; continue; }
                if (c == '\'') { inChar   = true;  current.append(c); i++; continue; }
            }

            // ── normal character ───────────────────────────────────────
            current.append(c);

            if      (c == '{') { braceDepth++; }
            else if (c == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
                if (braceDepth == 0 && parenDepth == 0) {
                    // A brace block just closed — peek ahead for else/catch/finally
                    // which must stay attached to this statement.
                    int j = i + 1;
                    while (j < len && Character.isWhitespace(methodBody.charAt(j))) j++;
                    String tail = methodBody.substring(j);
                    if (tail.startsWith("else") || tail.startsWith("catch") || tail.startsWith("finally")) {
                        // Don't emit yet; let the loop continue to consume the tail
                        i++;
                        continue;
                    }
                    // Emit the block statement
                    String stmt = current.toString().trim();
                    if (!stmt.isEmpty()) out.add(stmt);
                    current.setLength(0);
                }
            }
            else if (c == '(') { parenDepth++; }
            else if (c == ')') { parenDepth = Math.max(0, parenDepth - 1); }
            else if (c == ';' && braceDepth == 0 && parenDepth == 0) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) out.add(stmt);
                current.setLength(0);
                i++;
                continue;
            }

            i++;
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) out.add(remaining);
        return out;
    }
}
