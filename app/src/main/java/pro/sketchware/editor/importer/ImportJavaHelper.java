package pro.sketchware.editor.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImportJavaHelper {
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+([^;]+);", Pattern.MULTILINE);
    private ImportJavaHelper() {}

    public static List<String> importsOf(String source) {
        List<String> out = new ArrayList<>();
        Matcher m = IMPORT.matcher(source == null ? "" : source);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    public static String stripPackage(String source) { return source == null ? "" : source.replaceFirst("(?s)^\\s*package\\s+[^;]+;\\s*", ""); }
}
