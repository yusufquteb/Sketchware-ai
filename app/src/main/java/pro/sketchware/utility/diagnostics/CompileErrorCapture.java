package pro.sketchware.utility.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompileErrorCapture {
    private static final Pattern JAVAC = Pattern.compile("^(.+?):(\\d+)(?::(\\d+))?:\\s*(error|warning):\\s*(.*)$", Pattern.CASE_INSENSITIVE);
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
            Matcher k = KOTLIN.matcher(line);
            if (k.find()) {
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, k.group(1), parseInt(k.group(2)), parseInt(k.group(3)), k.group(4), line));
            }
        }
        return diagnostics;
    }

    private static int parseInt(String value) {
        try { return value == null ? -1 : Integer.parseInt(value); } catch (Exception e) { return -1; }
    }
}
