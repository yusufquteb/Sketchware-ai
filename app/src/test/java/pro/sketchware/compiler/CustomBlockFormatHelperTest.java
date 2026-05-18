package pro.sketchware.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CustomBlockFormatHelperTest {

    @Test
    public void validateDefinitionAcceptsSupportedStringFormatPlaceholders() {
        CustomBlockFormatHelper.ValidationResult validation =
                CustomBlockFormatHelper.validateDefinition("%1$s + %s + %%", 2, 0);

        assertTrue(validation.isValid());
        assertNull(validation.errorMessage());
        assertEquals(2, validation.analysis().highestPlaceholderIndex());
    }

    @Test
    public void validateDefinitionRejectsOldStylePlaceholderSyntax() {
        CustomBlockFormatHelper.ValidationResult validation =
                CustomBlockFormatHelper.validateDefinition("return %1s;", 1, 0);

        assertFalse(validation.isValid());
        assertEquals("Use String.format placeholders like %1$s, not %1s", validation.errorMessage());
    }

    @Test
    public void validateDefinitionRejectsUnsupportedConversionTypes() {
        CustomBlockFormatHelper.ValidationResult validation =
                CustomBlockFormatHelper.validateDefinition("return %1$d;", 1, 0);

        assertFalse(validation.isValid());
        assertEquals("Only String.format %s placeholders are supported in custom block code, found %1$d",
                validation.errorMessage());
    }

    @Test
    public void validateDefinitionRejectsZeroBasedIndices() {
        CustomBlockFormatHelper.ValidationResult validation =
                CustomBlockFormatHelper.validateDefinition("return %0$s;", 1, 0);

        assertFalse(validation.isValid());
        assertEquals("Placeholder indices must start at 1", validation.errorMessage());
    }

    @Test
    public void validateDefinitionRejectsPlaceholderReferencesPastSupportedArguments() {
        CustomBlockFormatHelper.ValidationResult validation =
                CustomBlockFormatHelper.validateDefinition("return %3$s;", 2, 0);

        assertFalse(validation.isValid());
        assertEquals("Code references placeholder %3$s but this block only provides 2 value(s)",
                validation.errorMessage());
    }

    @Test
    public void analysisTracksStackPlaceholderRangesAcrossMixedIndexing() {
        CustomBlockFormatHelper.PlaceholderAnalysis analysis =
                CustomBlockFormatHelper.analyze("%2$s + %s + %4$s");

        assertNull(analysis.errorMessage());
        assertEquals(4, analysis.highestPlaceholderIndex());
        assertTrue(analysis.referencesRange(3, 4));
        assertFalse(analysis.referencesRange(5, 6));
    }
}
