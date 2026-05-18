package pro.sketchware.editor.importer;

import java.util.ArrayList;
import java.util.List;

public final class JavaToBlocksPreprocessor {
    private JavaToBlocksPreprocessor() {}

    public static List<String> statements(String methodBody) {
        List<String> out = new ArrayList<>();
        if (methodBody == null) return out;
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < methodBody.length(); i++) {
            char c = methodBody.charAt(i);
            current.append(c);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (c == ';' && depth == 0) { out.add(current.toString().trim()); current.setLength(0); }
        }
        if (current.toString().trim().length() > 0) out.add(current.toString().trim());
        return out;
    }
}
