package pro.sketchware.compiler.support;

import java.io.File;
import java.util.Locale;

public final class ViewBindingSupport {
    private ViewBindingSupport() {}

    public static String bindingClassName(File layoutFile) {
        String name = layoutFile.getName().replaceFirst("\\.xml$", "");
        StringBuilder out = new StringBuilder();
        for (String part : name.split("_")) if (!part.isEmpty()) out.append(part.substring(0, 1).toUpperCase(Locale.US)).append(part.substring(1));
        out.append("Binding");
        return out.toString();
    }
}
