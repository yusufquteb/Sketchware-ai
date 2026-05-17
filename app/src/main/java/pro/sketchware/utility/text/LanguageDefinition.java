package pro.sketchware.utility.text;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class LanguageDefinition {
    public final String id;
    public final Set<String> extensions;
    public final Set<String> keywords;
    public final String lineComment;

    public LanguageDefinition(String id, Set<String> extensions, Set<String> keywords, String lineComment) {
        this.id = id;
        this.extensions = Collections.unmodifiableSet(new HashSet<>(extensions));
        this.keywords = Collections.unmodifiableSet(new HashSet<>(keywords));
        this.lineComment = lineComment == null ? "" : lineComment;
    }
}
