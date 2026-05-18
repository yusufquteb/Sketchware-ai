package pro.sketchware.compiler.support;

import java.io.File;

import pro.sketchware.utility.io.SafeFileOps;

public final class LibDexCache {
    private final File cacheDir;

    public LibDexCache(File cacheDir) { this.cacheDir = cacheDir; }

    public File dexFileFor(File library) throws Exception {
        SafeFileOps.ensureDirectory(cacheDir);
        return new File(cacheDir, SafeFileOps.sha256(library) + ".dex");
    }

    public boolean isValid(File library) throws Exception {
        File dex = dexFileFor(library);
        return dex.isFile() && dex.length() > 0 && dex.lastModified() >= library.lastModified();
    }
}
