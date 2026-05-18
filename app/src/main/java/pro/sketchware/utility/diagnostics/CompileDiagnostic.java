package pro.sketchware.utility.diagnostics;

public final class CompileDiagnostic {
    public enum Severity { ERROR, WARNING, NOTE }

    public final Severity severity;
    public final String file;
    public final int line;
    public final int column;
    public final String message;
    public final String raw;

    public CompileDiagnostic(Severity severity, String file, int line, int column, String message, String raw) {
        this.severity = severity == null ? Severity.ERROR : severity;
        this.file = file == null ? "" : file;
        this.line = Math.max(line, -1);
        this.column = Math.max(column, -1);
        this.message = message == null ? "" : message;
        this.raw = raw == null ? "" : raw;
    }

    public boolean hasLocation() { return !file.isEmpty() && line > 0; }

    @Override public String toString() {
        String loc = hasLocation() ? file + ":" + line + (column > 0 ? ":" + column : "") + ": " : "";
        return loc + severity.name().toLowerCase() + ": " + message;
    }
}
