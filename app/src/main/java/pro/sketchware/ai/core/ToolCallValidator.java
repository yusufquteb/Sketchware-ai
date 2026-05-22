package pro.sketchware.ai.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates tool-call arguments against a tool's JSON Schema before execution.
 *
 * <p>Covers a practical superset of JSON Schema sufficient for AI tool contracts:
 * <ul>
 *   <li>Required field presence</li>
 *   <li>Type verification: string, number, integer, boolean, object, array</li>
 *   <li>Enum constraints: {@code "enum": ["a","b","c"]}</li>
 *   <li>String length: {@code "minLength"} / {@code "maxLength"}</li>
 *   <li>Recursive nested object validation via {@code "properties"}</li>
 *   <li>Array item type via {@code "items": {"type": "…"}}</li>
 * </ul>
 *
 * <p>Returns a {@link ValidationResult} with a model-actionable error message so
 * the AI can self-correct on retry rather than producing a cryptic failure.
 */
public final class ToolCallValidator {

    private ToolCallValidator() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Validates {@code args} against {@code schema}.
     *
     * @param args   JSON arguments produced by the model
     * @param schema the tool's parameter schema (from {@link pro.sketchware.ai.tools.AgentTool#getParametersSchema()})
     * @return non-null {@link ValidationResult}
     */
    @NonNull
    public static ValidationResult validate(@Nullable JsonObject args,
                                             @Nullable JsonObject schema) {
        if (schema == null) return ValidationResult.ok();
        if (args   == null) args = new JsonObject();

        List<String> errors = new ArrayList<>();
        validateObject(args, schema, "", errors);

        return errors.isEmpty()
                ? ValidationResult.ok()
                : ValidationResult.fail(String.join("; ", errors));
    }

    // ── Recursive core ────────────────────────────────────────────────────────

    private static void validateObject(@NonNull JsonObject obj,
                                        @NonNull JsonObject schema,
                                        @NonNull String path,
                                        @NonNull List<String> errors) {
        // ── Required fields ───────────────────────────────────────────────────
        if (schema.has("required") && schema.get("required").isJsonArray()) {
            for (JsonElement req : schema.getAsJsonArray("required")) {
                String field = req.getAsString();
                String fpath = path.isEmpty() ? field : path + "." + field;
                if (!obj.has(field) || obj.get(field).isJsonNull()) {
                    errors.add("Missing required field: \"" + fpath + "\"");
                }
            }
        }

        if (!schema.has("properties") || !schema.get("properties").isJsonObject()) return;
        JsonObject props = schema.getAsJsonObject("properties");

        for (String field : props.keySet()) {
            if (!obj.has(field) || obj.get(field).isJsonNull()) continue;

            JsonElement propSchema = props.get(field);
            if (!propSchema.isJsonObject()) continue;

            String fpath = path.isEmpty() ? field : path + "." + field;
            JsonElement value = obj.get(field);
            validateField(value, propSchema.getAsJsonObject(), fpath, errors);
        }
    }

    private static void validateField(@NonNull JsonElement value,
                                       @NonNull JsonObject schema,
                                       @NonNull String path,
                                       @NonNull List<String> errors) {
        // ── Type check ────────────────────────────────────────────────────────
        String type = schema.has("type") ? schema.get("type").getAsString() : null;
        if (type != null) {
            String typeErr = checkType(path, value, type);
            if (typeErr != null) {
                errors.add(typeErr);
                return; // skip further checks if type is wrong
            }
        }

        // ── Enum ──────────────────────────────────────────────────────────────
        if (schema.has("enum") && schema.get("enum").isJsonArray()) {
            JsonArray allowed = schema.getAsJsonArray("enum");
            boolean found = false;
            for (JsonElement e : allowed) {
                if (e.equals(value)) { found = true; break; }
            }
            if (!found) {
                List<String> vals = new ArrayList<>();
                for (JsonElement e : allowed) vals.add(e.toString());
                errors.add("Field \"" + path + "\" must be one of: " + String.join(", ", vals));
            }
        }

        // ── String constraints ────────────────────────────────────────────────
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String str = value.getAsString();
            if (schema.has("minLength")) {
                int min = schema.get("minLength").getAsInt();
                if (str.length() < min) {
                    errors.add("Field \"" + path + "\" must be at least " + min + " character(s)");
                }
            }
            if (schema.has("maxLength")) {
                int max = schema.get("maxLength").getAsInt();
                if (str.length() > max) {
                    errors.add("Field \"" + path + "\" must be at most " + max + " character(s)");
                }
            }
        }

        // ── Recursive object validation ───────────────────────────────────────
        if (value.isJsonObject() && (schema.has("properties") || schema.has("required"))) {
            validateObject(value.getAsJsonObject(), schema, path, errors);
        }

        // ── Array item validation ─────────────────────────────────────────────
        if (value.isJsonArray() && schema.has("items") && schema.get("items").isJsonObject()) {
            JsonObject itemSchema = schema.getAsJsonObject("items");
            String itemType = itemSchema.has("type") ? itemSchema.get("type").getAsString() : null;
            if (itemType != null) {
                int idx = 0;
                for (JsonElement item : value.getAsJsonArray()) {
                    String itemPath = path + "[" + idx + "]";
                    String err = checkType(itemPath, item, itemType);
                    if (err != null) errors.add(err);
                    idx++;
                }
            }
        }
    }

    @Nullable
    private static String checkType(String path, JsonElement value, String type) {
        switch (type) {
            case "string":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                    return "Field \"" + path + "\" must be a string";
                break;
            case "number":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                    return "Field \"" + path + "\" must be a number";
                break;
            case "integer":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                    return "Field \"" + path + "\" must be an integer";
                break;
            case "boolean":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                    return "Field \"" + path + "\" must be a boolean";
                break;
            case "object":
                if (!value.isJsonObject())
                    return "Field \"" + path + "\" must be an object";
                break;
            case "array":
                if (!value.isJsonArray())
                    return "Field \"" + path + "\" must be an array";
                break;
            default:
                break;
        }
        return null;
    }

    // ── Result ────────────────────────────────────────────────────────────────

    /** Immutable result of a schema validation pass. */
    public static final class ValidationResult {
        public final boolean valid;
        @Nullable public final String errorMessage;

        private ValidationResult(boolean valid, @Nullable String errorMessage) {
            this.valid        = valid;
            this.errorMessage = errorMessage;
        }

        static ValidationResult ok()              { return new ValidationResult(true,  null); }
        static ValidationResult fail(String msg)  { return new ValidationResult(false, msg);  }
    }
}
