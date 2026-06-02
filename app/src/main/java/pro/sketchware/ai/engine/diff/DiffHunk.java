package pro.sketchware.ai.engine.diff;

import java.util.List;

/**
 * A contiguous group of changed lines with surrounding context.
 */
public final class DiffHunk {
    public final String          header;   // e.g. "@@ -12,7 +12,9 @@"
    public final List<DiffEntry> entries;

    public DiffHunk(String header, List<DiffEntry> entries) {
        this.header  = header;
        this.entries = entries;
    }
}
