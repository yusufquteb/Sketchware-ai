package pro.sketchware.utility.text;

import java.util.ArrayList;
import java.util.List;

public final class SyntaxHighlighter {
    public enum Kind { TEXT, KEYWORD, STRING, COMMENT, NUMBER }

    public static final class Span {
        public final Kind kind; public final int start; public final int end;
        public Span(Kind kind, int start, int end) { this.kind = kind; this.start = start; this.end = end; }
    }

    public List<Span> highlight(String source, LanguageDefinition language) {
        List<Span> spans = new ArrayList<>();
        if (source == null || source.isEmpty() || language == null) return spans;
        String comment = language.lineComment;
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            if (!comment.isEmpty() && source.startsWith(comment, i)) {
                int end = source.indexOf('\n', i);
                spans.add(new Span(Kind.COMMENT, i, end < 0 ? source.length() : end));
                i = end < 0 ? source.length() : end;
            } else if (c == '"' || c == '\'') {
                int end = scanString(source, i, c);
                spans.add(new Span(Kind.STRING, i, end)); i = end;
            } else if (Character.isDigit(c)) {
                int end = i + 1; while (end < source.length() && (Character.isDigit(source.charAt(end)) || source.charAt(end) == '.')) end++;
                spans.add(new Span(Kind.NUMBER, i, end)); i = end;
            } else if (Character.isJavaIdentifierStart(c)) {
                int end = i + 1; while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) end++;
                if (language.keywords.contains(source.substring(i, end))) spans.add(new Span(Kind.KEYWORD, i, end));
                i = end;
            } else {
                i++;
            }
        }
        return spans;
    }

    private int scanString(String s, int start, char quote) {
        boolean escape = false;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == quote) return i + 1;
        }
        return s.length();
    }
}
