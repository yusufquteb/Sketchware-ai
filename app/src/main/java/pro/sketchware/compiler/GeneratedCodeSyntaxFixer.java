package pro.sketchware.compiler;

/**
 * Repairs syntax-invalid generator output before it reaches later formatting or AST-based sanitizers.
 *
 * <p>This fixer is intentionally limited to malformed constructs that cannot be parsed at all. Valid-but-wrong
 * method calls are handled by higher-level sanitizers where we can reason about AST structure safely.</p>
 */
public final class GeneratedCodeSyntaxFixer {

    private GeneratedCodeSyntaxFixer() {
    }

    public static String fix(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        String sanitized = code;
        sanitized = sanitized.replaceAll("\\(\\((?:int|long|short|byte)\\)\\)", "0");
        sanitized = sanitized.replaceAll("\\(\\((?:float|double)\\)\\)", "0");
        sanitized = sanitized.replaceAll("\\bif\\s*\\(\\s*\\)", "if (false)");
        sanitized = sanitized.replaceAll("\\belse\\s+if\\s*\\(\\s*\\)", "else if (false)");
        sanitized = sanitized.replaceAll("\\bwhile\\s*\\(\\s*\\)", "while (false)");
        sanitized = sanitized.replaceAll("\\bswitch\\s*\\(\\s*\\(\\(int\\)\\)\\s*\\)", "switch(0)");
        sanitized = sanitized.replaceAll("\\bcase\\s*\\(\\(int\\)\\)\\s*:", "case 0:");
        sanitized = sanitized.replaceAll("(\\.setProgress)\\s+([0-9]+)\\s*;", "$1($2);");
        sanitized = sanitized.replaceAll("([A-Za-z_][A-Za-z0-9_]*Boolean)\\s*=\\s*;", "$1 = false;");
        return sanitized;
    }
}
