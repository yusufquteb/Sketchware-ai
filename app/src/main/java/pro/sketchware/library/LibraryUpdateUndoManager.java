package pro.sketchware.library;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

import pro.sketchware.utility.io.SafeFileOps;

public final class LibraryUpdateUndoManager {
    private final Deque<Entry> history = new ArrayDeque<>();

    public void snapshot(File file, File backupDir) throws Exception {
        SafeFileOps.ensureDirectory(backupDir);
        File backup = new File(backupDir, file.getName() + ".bak." + System.currentTimeMillis());
        SafeFileOps.copyTree(file, backup);
        history.push(new Entry(file, backup));
    }

    public boolean undoLast() throws Exception {
        Entry entry = history.poll();
        if (entry == null) return false;
        if (entry.target.exists()) SafeFileOps.deleteRecursively(entry.target);
        SafeFileOps.copyTree(entry.backup, entry.target);
        return true;
    }

    private static final class Entry { final File target; final File backup; Entry(File target, File backup) { this.target = target; this.backup = backup; } }
}
