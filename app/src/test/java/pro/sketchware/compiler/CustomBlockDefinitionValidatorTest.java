package pro.sketchware.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CustomBlockDefinitionValidatorTest {

    @Test
    public void validateAcceptsRegularCustomBlockDefinition() {
        String validationError = CustomBlockDefinitionValidator.validate(
                "showToast",
                "show %m.activity %s",
                "SketchwareUtil.showMessage(%1$s, %2$s);",
                " "
        );

        assertNull(validationError);
    }

    @Test
    public void validateRejectsMissingRequiredStackPlaceholderForControlBlock() {
        String validationError = CustomBlockDefinitionValidator.validate(
                "onlyIf",
                "if %b",
                "if (%1$s) { }",
                "c"
        );

        assertEquals("Control blocks must use their stack placeholder(s) in code", validationError);
    }

    @Test
    public void validateRejectsLegacyOverlayPermissionShortcut() {
        String validationError = CustomBlockDefinitionValidator.validate(
                "overlayPermission",
                "request overlay",
                "helper.requestOverlayDisplayPermission(%1$s);",
                "c"
        );

        assertEquals("Overlay permission blocks must declare an activity/context parameter instead of using the stack placeholder as the first argument",
                validationError);
    }

    @Test
    public void normalizeTypeTreatsRegularAliasesAsRegularBlockType() {
        assertEquals(" ", CustomBlockDefinitionValidator.normalizeType(""));
        assertEquals(" ", CustomBlockDefinitionValidator.normalizeType("regular"));
        assertEquals("e", CustomBlockDefinitionValidator.normalizeType("e"));
    }
}
