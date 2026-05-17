package pro.sketchware.utility.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ErrorFixHelper {
    private ErrorFixHelper() {}

    public static List<String> suggestionsFor(CompileDiagnostic diagnostic) {
        List<String> out = new ArrayList<>();
        if (diagnostic == null) return out;
        String msg = diagnostic.message.toLowerCase(Locale.US);
        if (msg.contains("cannot find symbol")) {
            out.add("Check the class, method, or variable name and add the missing import or dependency.");
        }
        if (msg.contains("package") && msg.contains("does not exist")) {
            out.add("Add the library dependency that provides this package or remove the stale import.");
        }
        if (msg.contains("duplicate class")) {
            out.add("Remove duplicated jar/aar dependencies or exclude one copy from the build classpath.");
        }
        if (msg.contains("uses or overrides a deprecated api")) {
            out.add("Rebuild with detailed warnings, then migrate the deprecated API usage.");
        }
        if (msg.contains("min sdk") || msg.contains("requires api level")) {
            out.add("Raise the project minimum SDK or guard this API call behind a runtime SDK check.");
        }
        if (out.isEmpty()) out.add("Open the reported file and inspect the statement around the diagnostic location.");
        return out;
    }
}
