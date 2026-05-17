package pro.sketchware.utility.search;

import java.io.File;

public final class FileSearchResult {
    public final File file;
    public final int lineNumber;
    public final String line;
    public final int score;

    public FileSearchResult(File file, int lineNumber, String line, int score) {
        this.file = file;
        this.lineNumber = lineNumber;
        this.line = line == null ? "" : line;
        this.score = score;
    }
}
