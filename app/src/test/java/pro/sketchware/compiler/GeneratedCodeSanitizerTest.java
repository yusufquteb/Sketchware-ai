package pro.sketchware.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeneratedCodeSanitizerTest {

    @Test
    public void sanitizeActivityCodeQualifiesOnlyUnscopedCalls() {
        String code = """
                package test;

                import android.content.Context;
                import android.content.Intent;
                import android.view.View;

                public class MainActivity {
                    void bind(View _view, Context _context) {
                        findViewById(1);
                        startActivity(new Intent(this, MainActivity.class));
                        _view.findViewById(2);
                        _context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    }
                }
                """;

        String sanitized = GeneratedCodeSanitizer.sanitize(code, "MainActivity", false);

        assertTrue(sanitized.contains("MainActivity.this.findViewById(1);"));
        assertTrue(sanitized.contains("new Intent(MainActivity.this, MainActivity.class)"));
        assertTrue(sanitized.contains("_view.findViewById(2);"));
        assertTrue(sanitized.contains("_context.getSystemService(Context.INPUT_METHOD_SERVICE);"));
        assertFalse(sanitized.contains("_view.MainActivity.this.findViewById"));
        assertFalse(sanitized.contains("_context.MainActivity.this.getSystemService"));
    }

    @Test
    public void sanitizeFragmentCodeUsesFragmentContextAccessors() {
        String code = """
                package test;

                import android.content.Context;
                import android.content.Intent;

                public class SampleFragment {
                    void bind(Intent intent) {
                        findViewById(1);
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                        getBaseContext();
                        getApplicationContext();
                        startActivity(intent);
                        new Intent(this, SampleFragment.class);
                    }
                }
                """;

        String sanitized = GeneratedCodeSanitizer.sanitize(code, "SampleFragment", true);

        assertTrue(sanitized.contains("getActivity().findViewById(1);"));
        assertTrue(sanitized.contains("getContext().getSystemService(Context.INPUT_METHOD_SERVICE);"));
        assertTrue(sanitized.contains("getActivity().getBaseContext();"));
        assertTrue(sanitized.contains("getContext().getApplicationContext();"));
        assertTrue(sanitized.contains("getActivity().startActivity(intent);"));
        assertTrue(sanitized.contains("new Intent(getActivity(), SampleFragment.class)"));
    }

    @Test
    public void sanitizeIsIdempotentForAlreadyScopedCalls() {
        String code = """
                package test;

                public class MainActivity {
                    void bind() {
                        MainActivity.this.findViewById(1);
                    }
                }
                """;

        String sanitized = GeneratedCodeSanitizer.sanitize(code, "MainActivity", false);
        String sanitizedAgain = GeneratedCodeSanitizer.sanitize(sanitized, "MainActivity", false);

        assertEquals(sanitized, sanitizedAgain);
        assertFalse(sanitizedAgain.contains("MainActivity.this.MainActivity.this"));
    }

    @Test
    public void sanitizeRepairsSupportedArgumentHoles() {
        String code = """
                package test;

                public class MainActivity {
                    void bind(Checkable target) {
                        String.valueOf();
                        target.setChecked();
                        target.setProgress();
                    }

                    interface Checkable {
                        void setChecked(boolean checked);
                        void setProgress(int value);
                    }
                }
                """;

        String sanitized = GeneratedCodeSanitizer.sanitize(code, "MainActivity", false);

        assertTrue(sanitized.contains("String.valueOf(\"\")"));
        assertTrue(sanitized.contains("target.setChecked(false);"));
        assertTrue(sanitized.contains("target.setProgress(0);"));
    }

    @Test
    public void syntaxFixerUsesConsistentSafeDefaultsAcrossGeneratorStages() {
        String malformed = """
                if () {
                } else if () {
                }
                while () {
                }
                switch (((int))) {
                    case ((int)):
                        break;
                }
                progress.setProgress 7;
                someBoolean = ;
                """;

        String fixed = GeneratedCodeSyntaxFixer.fix(malformed);

        assertTrue(fixed.contains("if (false)"));
        assertTrue(fixed.contains("else if (false)"));
        assertTrue(fixed.contains("while (false)"));
        assertTrue(fixed.contains("switch(0)"));
        assertTrue(fixed.contains("case 0:"));
        assertTrue(fixed.contains("progress.setProgress(7);"));
        assertTrue(fixed.contains("someBoolean = false;"));
    }

    @Test
    public void normalizerDoesNotApplyActivitySanitizerToHelperClasses() {
        String code = """
                package test;

                import android.content.Context;
                import android.view.View;

                public class SketchwareUtil {
                    void bind(View _view, Context _context) {
                        _view.findViewById(1);
                        _context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    }
                }
                """;

        String normalized = LegacyJavaSourceNormalizer.normalizeJavaFile(code);

        assertEquals(code, normalized);
        assertFalse(normalized.contains("SketchwareUtil.this"));
    }
}
