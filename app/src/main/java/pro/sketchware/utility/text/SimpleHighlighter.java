package pro.sketchware.utility.text;

import java.util.List;

public final class SimpleHighlighter {
    private final LanguageSupportManager languages = new LanguageSupportManager();
    private final SyntaxHighlighter highlighter = new SyntaxHighlighter();

    public List<SyntaxHighlighter.Span> highlight(String fileName, String source) {
        return highlighter.highlight(source, languages.forFileName(fileName));
    }
}
