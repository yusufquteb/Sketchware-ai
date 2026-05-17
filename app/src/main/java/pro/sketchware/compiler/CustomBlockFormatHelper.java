package pro.sketchware.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared validation/parsing rules for custom-block String.format templates.
 */
public final class CustomBlockFormatHelper {

    private CustomBlockFormatHelper() {
    }

    public static ValidationResult validateDefinition(String code, int parameterCount, int stackCount) {
        PlaceholderAnalysis analysis = analyze(code);
        if (analysis.errorMessage != null) {
            return new ValidationResult(analysis, analysis.errorMessage);
        }

        int maxSupportedPlaceholder = parameterCount + stackCount;
        if (analysis.highestPlaceholderIndex > maxSupportedPlaceholder) {
            return new ValidationResult(analysis,
                    "Code references placeholder %" + analysis.highestPlaceholderIndex
                            + "$s but this block only provides " + maxSupportedPlaceholder + " value(s)");
        }

        return new ValidationResult(analysis, null);
    }

    public static PlaceholderAnalysis analyze(String code) {
        if (code == null || code.isEmpty()) {
            return new PlaceholderAnalysis(List.of(), 0, null);
        }

        ArrayList<PlaceholderReference> references = new ArrayList<>();
        int highestIndex = 0;
        int nextSequentialIndex = 1;

        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) != '%') {
                continue;
            }

            if (i + 1 >= code.length()) {
                continue;
            }

            int tokenStart = i;
            char next = code.charAt(i + 1);
            if (next == '%') {
                i++;
                continue;
            }

            if (next == '<') {
                return new PlaceholderAnalysis(List.of(), highestIndex,
                        "Relative String.format placeholders like %<s are not supported in custom block code");
            }

            int cursor = i + 1;
            Integer explicitIndex = null;
            if (Character.isDigit(next)) {
                int digitStart = cursor;
                while (cursor < code.length() && Character.isDigit(code.charAt(cursor))) {
                    cursor++;
                }

                if (cursor < code.length() && code.charAt(cursor) == '$') {
                    explicitIndex = Integer.parseInt(code.substring(digitStart, cursor));
                    if (explicitIndex <= 0) {
                        return new PlaceholderAnalysis(List.of(), highestIndex,
                                "Placeholder indices must start at 1");
                    }
                    cursor++;
                } else if (cursor < code.length() && Character.isLetter(code.charAt(cursor))) {
                    return new PlaceholderAnalysis(List.of(), highestIndex,
                            "Use String.format placeholders like %1$s, not "
                                    + code.substring(tokenStart, cursor + 1));
                } else {
                    continue;
                }
            }

            if (cursor >= code.length()) {
                continue;
            }

            char conversion = code.charAt(cursor);
            if (conversion == 's') {
                int resolvedIndex = explicitIndex != null ? explicitIndex : nextSequentialIndex++;
                references.add(new PlaceholderReference(resolvedIndex));
                highestIndex = Math.max(highestIndex, resolvedIndex);
                i = cursor;
                continue;
            }

            if (Character.isLetter(conversion)) {
                return new PlaceholderAnalysis(List.of(), highestIndex,
                        "Only String.format %s placeholders are supported in custom block code, found "
                                + code.substring(tokenStart, cursor + 1));
            }
        }

        return new PlaceholderAnalysis(references, highestIndex, null);
    }

    public static final class ValidationResult {
        private final PlaceholderAnalysis analysis;
        private final String errorMessage;

        private ValidationResult(PlaceholderAnalysis analysis, String errorMessage) {
            this.analysis = analysis;
            this.errorMessage = errorMessage;
        }

        public PlaceholderAnalysis analysis() {
            return analysis;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public boolean isValid() {
            return errorMessage == null;
        }
    }

    public static final class PlaceholderAnalysis {
        private final List<PlaceholderReference> references;
        private final int highestPlaceholderIndex;
        private final String errorMessage;

        private PlaceholderAnalysis(List<PlaceholderReference> references, int highestPlaceholderIndex,
                                    String errorMessage) {
            this.references = references;
            this.highestPlaceholderIndex = highestPlaceholderIndex;
            this.errorMessage = errorMessage;
        }

        public int highestPlaceholderIndex() {
            return highestPlaceholderIndex;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public boolean referencesRange(int startIndex, int endIndex) {
            if (startIndex > endIndex) {
                return false;
            }

            for (PlaceholderReference reference : references) {
                if (reference.index >= startIndex && reference.index <= endIndex) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class PlaceholderReference {
        private final int index;

        private PlaceholderReference(int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }
    }
}
