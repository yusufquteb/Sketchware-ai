package pro.sketchware.tools;

import java.io.File;

import pro.sketchware.utility.io.SafeFileOps;

public final class ClassCloneTool {
    private ClassCloneTool() {}

    public static void cloneJavaClass(File source, File target, String oldClassName, String newClassName) throws Exception {
        String code = SafeFileOps.readUtf8(source);
        if (oldClassName != null && newClassName != null && !oldClassName.isEmpty() && !newClassName.isEmpty()) code = code.replace(oldClassName, newClassName);
        SafeFileOps.writeUtf8Atomic(target, code);
    }
}
