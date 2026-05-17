package pro.sketchware.compiler.support;

public final class CodeGenerationSupport {
    private CodeGenerationSupport() {}

    public static String javaString(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    public static String setter(String target, String method, String argument) { return target + "." + method + "(" + argument + ");"; }
}
