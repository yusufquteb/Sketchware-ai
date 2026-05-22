package pro.sketchware.ai.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates tool-call arguments against a tool's JSON Schema before execution.
 *
 * <p>Supports a practical subset of JSON Schema sufficient for AI tool contracts:
 * <ul>
 *   <li>Required field presence checks</li>
 *   <li>Basic type verification: string, number, integer, boolean, object, array</li>
 *   <li>Null/missing field detection</li>
 * </ul>
 *
 * <p>Returns a {@link ValidationResult} with a human-readable error so the model
 * can self-correct on the next attempt rather than producing a cryptic failure.
 */
public final class ToolCallValidator {

    private ToolCallValidator() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Validates {@code args} against {@code schema}.
     *
     * @param args   the JSON arguments the model produced for this tool call
     * @param schema the tool's parameter schema (from {@code AgentTool.getParametersSchema()})
     * @return a {@link ValidationResult} — check {@link ValidationResult#valid} first
     */
    @NonNull
    public static ValidationResult validate(@Nullable JsonObject args,
                                             @Nullable JsonObject schema) {
        if (schema == null) return ValidationResult.ok();     // no schema = no validation
        if (args   == null) args = new JsonObject();           // treat absent args as empty

        List<String> errors = new ArrayList<>();

        // ── Required fields ───────────────────────────────────────────────────
        if (schema.has("required") && schema.get("required").isJsonArray()) {
            for (JsonElement req : schema.getAsJsonArray("required")) {
                String field = req.getAsString();
                if (!args.has(field) || args.get(field).isJsonNull()) {
                    errors.add("Missing required field: \"" + field + "\"");
                }
            }
        }

        // ── Type checks on present fields ─────────────────────────────────────
        if (schema.has("properties") && schema.get("properties").isJsonObject()) {
            JsonObject props = schema.getAsJsonObject("properties");
            for (String field : props.keySet()) {
                if (!args.has(field) || args.get(field).isJsonNull()) continue;
                JsonElement propSchema = props.get(field);
                if (!propSchema.isJsonObject()) continue;
                String expectedType = propSchema.getAsJsonObject().has("type")
                        ? propSchema.getAsJsonObject().get("type").getAsString()
                        : null;
                if (expectedType == null) continue;

                JsonElement value = args.get(field);
                String typeError = checkType(field, value, expectedType);
                if (typeError != null) errors.add(typeError);
            }
        }

        return errors.isEmpty()
                ? ValidationResult.ok()
                : ValidationResult.fail(String.join("; ", errors));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @Nullable
    private static String checkType(String field, JsonElement value, String expectedType) {
        switch (expectedType) {
            case "string":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    return "Field \"" + field + "\" must be a string";
                }
                break;
            case "number":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    return "Field \"" + field + "\" must be a number";
                }
                break;
            case "integer":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    return "Field \"" + field + "\" must be an integer";
                }
                break;
            case "boolean":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                    return "Field \"" + field + "\" must be a boolean";
                }
                break;
            case "object":
                if (!value.isJsonObject()) {
                    return "Field \"" + field + "\" must be an object";
                }
                break;
            case "array":
                if (!value.isJsonArray()) {
                    return "Field \"" + field + "\" must be an array";
                }
                break;
            default:
                break; // unknown type — skip
        }
        return null;
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /** Immutable result of a schema validation pass. */
    public static final class ValidationResult {
        public final boolean valid;
        @Nullable public final String errorMessage;

        private ValidationResult(boolean valid, @Nullable String errorMessage) {
            this.valid        = valid;
            this.errorMessage = errorMessage;
        }

        static ValidationResult ok()              { return new ValidationResult(true, null); }
        static ValidationResult fail(String msg)  { return new ValidationResult(false, msg); }
    }
}
