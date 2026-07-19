package pro.sketchware.utility.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompileErrorCapture {
    // Matches "file:line[:col]: error|warning: message" (standard javac diagnostics).
    private static final Pattern JAVAC = Pattern.compile("^(.+?):(\\d+)(?::(\\d+))?:\\s*(error|warning):\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    // Matches "file:line: Note: message" — javac's location-attached notes
    // (e.g. "Foo.java:12: Note: Foo.java uses unchecked or unsafe operations.").
    private static final Pattern JAVAC_NOTE = Pattern.compile("^(.+?):(\\d+)(?::(\\d+))?:\\s*Note:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    // Matches bare, non-location javac notes (e.g. "Note: Recompile with -Xlint:unchecked for details.").
    private static final Pattern BARE_NOTE = Pattern.compile("^Note:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern KOTLIN = Pattern.compile("^e:\\s+(.+?):\\s*\\((\\d+),\\s*(\\d+)\\):\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    private CompileErrorCapture() {}

    public static List<CompileDiagnostic> parse(String output) {
        if (output == null || output.trim().isEmpty()) return Collections.emptyList();
        String[] lines = output.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<CompileDiagnostic> diagnostics = new ArrayList<>();
        for (String line : lines) {
            Matcher j = JAVAC.matcher(line);
            if (j.find()) {
                CompileDiagnostic.Severity severity = "warning".equalsIgnoreCase(j.group(4)) ? CompileDiagnostic.Severity.WARNING : CompileDiagnostic.Severity.ERROR;
                diagnostics.add(new CompileDiagnostic(severity, j.group(1), parseInt(j.group(2)), parseInt(j.group(3)), j.group(5), line));
                continue;
            }
            Matcher jn = JAVAC_NOTE.matcher(line);
            if (jn.find()) {
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.NOTE, jn.group(1), parseInt(jn.group(2)), parseInt(jn.group(3)), jn.group(4), line));
                continue;
            }
            Matcher k = KOTLIN.matcher(line);
            if (k.find()) {
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, k.group(1), parseInt(k.group(2)), parseInt(k.group(3)), k.group(4), line));
                continue;
            }
            Matcher bn = BARE_NOTE.matcher(line);
            if (bn.find()) {
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.NOTE, null, -1, -1, bn.group(1), line));
            }
        }
        return diagnostics;
    }

    private static int parseInt(String value) {
        try { return value == null ? -1 : Integer.parseInt(value); } catch (Exception e) { return -1; }
    }
}
