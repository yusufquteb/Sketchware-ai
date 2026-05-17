package pro.sketchware.compiler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared validation rules for persisted custom-block definitions.
 */
public final class CustomBlockDefinitionValidator {

    private static final Pattern SPEC_PARAM_PATTERN = Pattern.compile("%\\w+(?:\\.\\w+)?|%\\w");

    private CustomBlockDefinitionValidator() {
    }

    public static String validate(String name, String spec, String code, String type) {
        String normalizedName = safeTrim(name);
        String normalizedSpec = safeTrim(spec);
        String normalizedCode = code == null ? "" : code;
        String normalizedType = normalizeType(type);

        if (normalizedName.isEmpty()) {
            return "Block name is required";
        }
        if (normalizedSpec.isEmpty()) {
            return "Block spec is required";
        }
        if (normalizedCode.trim().isEmpty()) {
            return "Block code is required";
        }

        int parameterCount = extractSpecParameterCount(normalizedSpec);
        int stackCount = switch (normalizedType) {
            case "c" -> 1;
            case "e" -> 2;
            default -> 0;
        };

        CustomBlockFormatHelper.ValidationResult validation =
                CustomBlockFormatHelper.validateDefinition(normalizedCode, parameterCount, stackCount);
        if (!validation.isValid()) {
            return validation.errorMessage();
        }

        if (stackCount > 0 && !validation.analysis().referencesRange(parameterCount + 1, parameterCount + stackCount)) {
            return "Control blocks must use their stack placeholder(s) in code";
        }

        if (normalizedType.equals("c") && parameterCount == 0
                && normalizedCode.contains("requestOverlayDisplayPermission(")
                && normalizedCode.contains("%1$s")) {
            return "Overlay permission blocks must declare an activity/context parameter instead of using the stack placeholder as the first argument";
        }

        return null;
    }

    public static int extractSpecParameterCount(String spec) {
        Matcher matcher = SPEC_PARAM_PATTERN.matcher(spec == null ? "" : spec);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static String normalizeType(String type) {
        String trimmed = safeTrim(type);
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("regular")) {
            return " ";
        }
        return trimmed;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
