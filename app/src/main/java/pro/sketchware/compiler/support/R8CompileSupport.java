package pro.sketchware.compiler.support;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class R8CompileSupport {
    private R8CompileSupport() {}

    public static List<String> command(File inputJar, File outputZip, File androidJar, File rules) {
        List<String> args = new ArrayList<>();
        args.add("--release");
        args.add("--lib"); args.add(androidJar.getAbsolutePath());
        args.add("--output"); args.add(outputZip.getAbsolutePath());
        if (rules != null && rules.isFile()) { args.add("--pg-conf"); args.add(rules.getAbsolutePath()); }
        args.add(inputJar.getAbsolutePath());
        return args;
    }
}
