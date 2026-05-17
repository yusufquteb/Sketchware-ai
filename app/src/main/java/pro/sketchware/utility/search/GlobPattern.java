package pro.sketchware.utility.search;

import java.util.regex.Pattern;

public final class GlobPattern {
    private final Pattern pattern;

    private GlobPattern(Pattern pattern) { this.pattern = pattern; }

    public static GlobPattern compile(String glob) {
        if (glob == null || glob.isEmpty()) glob = "**";
        StringBuilder regex = new StringBuilder();
        char[] chars = glob.replace('\\', '/').toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '*') {
                boolean doubleStar = i + 1 < chars.length && chars[i + 1] == '*';
                regex.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) i++;
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (".()[]{}+$^|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return new GlobPattern(Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE));
    }

    public boolean matches(String relativePath) {
        return pattern.matcher(relativePath == null ? "" : relativePath.replace('\\', '/')).matches();
    }
}
