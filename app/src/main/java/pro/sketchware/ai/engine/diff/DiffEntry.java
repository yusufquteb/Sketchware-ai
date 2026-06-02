package pro.sketchware.ai.engine.diff;

/**
 * A single line in a diff result.
 */
public final class DiffEntry {

    public enum Type { ADDED, REMOVED, UNCHANGED }

    public final Type   type;
    public final String line;
    public final int    oldLine;  // 1-based line number in old file, -1 if added
    public final int    newLine;  // 1-based line number in new file, -1 if removed

    public DiffEntry(Type type, String line, int oldLine, int newLine) {
        this.type    = type;
        this.line    = line;
        this.oldLine = oldLine;
        this.newLine = newLine;
    }
}
