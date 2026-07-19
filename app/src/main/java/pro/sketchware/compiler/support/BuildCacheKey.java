package pro.sketchware.compiler.support;

import java.io.File;
import java.util.List;

import pro.sketchware.utility.io.SafeFileOps;
import pro.sketchware.utility.hash.HashUtil;

public final class BuildCacheKey {
    private BuildCacheKey() {}

    public static String forFiles(List<File> files) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (File file : files) if (file.isFile()) sb.append(file.getAbsolutePath()).append(':').append(file.lastModified()).append(':').append(file.length()).append(':').append(SafeFileOps.sha256(file)).append('\n');
        return HashUtil.sha256(sb.toString());
    }
}
