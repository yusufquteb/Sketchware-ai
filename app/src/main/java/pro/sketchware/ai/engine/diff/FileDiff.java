package pro.sketchware.ai.engine.diff;

import java.util.List;

/**
 * Complete diff result for a single file.
 */
public final class FileDiff {

    public final String         filename;
    public final List<DiffHunk> hunks;
    public final int            linesAdded;
    public final int            linesRemoved;

    public FileDiff(String filename, List<DiffHunk> hunks, int linesAdded, int linesRemoved) {
        this.filename     = filename;
        this.hunks        = hunks;
        this.linesAdded   = linesAdded;
        this.linesRemoved = linesRemoved;
    }

    public boolean hasChanges() {
        return linesAdded > 0 || linesRemoved > 0;
    }

    public String getSummary() {
        return "+" + linesAdded + " -" + linesRemoved + " in " + filename;
    }
}
