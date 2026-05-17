package pro.sketchware.logic;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;

import a.a.a.Fx;
import a.a.a.jq;

/**
 * LogicSyntaxChecker — validates the Java code generated from Sketchware block logic.
 *
 * Uses the JavaParser library (already in the classpath) to parse the generated code
 * and detect syntax errors before a full compile. This gives the AI instant feedback
 * without needing a slow compilation cycle.
 *
 * Usage:
 *   SyntaxResult result = LogicSyntaxChecker.check(activityName, buildConfig, blocks, viewBinding);
 *   if (!result.isValid) { // show error to AI }
 */
public class LogicSyntaxChecker {

    public static class SyntaxResult {
        public final boolean isValid;
        public final String  errorMessage;
        public final String  generatedCode;

        public SyntaxResult(boolean isValid, String errorMessage, String generatedCode) {
            this.isValid       = isValid;
            this.errorMessage  = errorMessage;
            this.generatedCode = generatedCode;
        }
    }

    /**
     * Generates Java code from the given blocks and checks it for syntax errors.
     *
     * @param activityName       Name of the activity (e.g. "MainActivity")
     * @param buildConfig        Sketchware build configuration object
     * @param blocks             List of BlockBeans to validate
     * @param viewBindingEnabled Whether view binding is enabled for this project
     * @return SyntaxResult with validity flag, error message (if any), and generated code
     */
    public static SyntaxResult check(
            String activityName,
            jq buildConfig,
            ArrayList<BlockBean> blocks,
            boolean viewBindingEnabled) {

        if (blocks == null || blocks.isEmpty()) {
            return new SyntaxResult(true, null, "");
        }

        try {
            Fx generator = new Fx(activityName, buildConfig, blocks, viewBindingEnabled);
            String code = generator.a();

            if (code == null || code.trim().isEmpty()) {
                return new SyntaxResult(true, null, "");
            }

            // Wrap in a dummy class for the parser
            String wrapped = "class __SyntaxCheck__ { void __check__() {\n" + code + "\n} }";

            // Use reflection to invoke JavaParser — avoids hard dependency
            // if JavaParser isn't available the check is skipped gracefully
            try {
                Class<?> parserClass = Class.forName("com.github.javaparser.JavaParser");
                Object parser = parserClass.getDeclaredConstructor().newInstance();
                java.lang.reflect.Method parseMethod = parserClass.getMethod("parse", String.class);
                Object parseResult = parseMethod.invoke(parser, wrapped);

                Class<?> resultClass = parseResult.getClass();
                java.lang.reflect.Method isSuccessful = resultClass.getMethod("isSuccessful");
                boolean ok = (Boolean) isSuccessful.invoke(parseResult);

                if (ok) {
                    return new SyntaxResult(true, null, code);
                } else {
                    java.lang.reflect.Method getProblems = resultClass.getMethod("getProblems");
                    Object problems = getProblems.invoke(parseResult);
                    String errorMsg = problems.toString();
                    return new SyntaxResult(false, errorMsg, code);
                }

            } catch (ClassNotFoundException ignored) {
                // JavaParser not available — treat as valid
                return new SyntaxResult(true, null, code);
            }

        } catch (Exception e) {
            return new SyntaxResult(false, "Exception during syntax check: " + e.getMessage(), "");
        }
    }

    /**
     * Quick check variant that accepts raw Java code (already generated).
     *
     * @param javaCode The raw Java code string to check
     * @return SyntaxResult
     */
    public static SyntaxResult checkRawCode(String javaCode) {
        if (javaCode == null || javaCode.trim().isEmpty()) {
            return new SyntaxResult(true, null, "");
        }

        try {
            String wrapped = "class __SyntaxCheck__ { void __check__() {\n" + javaCode + "\n} }";

            Class<?> parserClass = Class.forName("com.github.javaparser.JavaParser");
            Object parser = parserClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method parseMethod = parserClass.getMethod("parse", String.class);
            Object parseResult = parseMethod.invoke(parser, wrapped);

            Class<?> resultClass = parseResult.getClass();
            java.lang.reflect.Method isSuccessful = resultClass.getMethod("isSuccessful");
            boolean ok = (Boolean) isSuccessful.invoke(parseResult);

            if (ok) {
                return new SyntaxResult(true, null, javaCode);
            } else {
                java.lang.reflect.Method getProblems = resultClass.getMethod("getProblems");
                Object problems = getProblems.invoke(parseResult);
                return new SyntaxResult(false, problems.toString(), javaCode);
            }

        } catch (ClassNotFoundException ignored) {
            return new SyntaxResult(true, null, javaCode);
        } catch (Exception e) {
            return new SyntaxResult(false, "Exception: " + e.getMessage(), javaCode);
        }
    }
}
