package pro.sketchware.ai.tools.code;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.JsonObject;

import java.util.ArrayList;

import a.a.a.jq;
import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;
import pro.sketchware.logic.LogicSyntaxChecker;

/**
 * AI tool: check_logic_syntax
 *
 * Generates Java code from Sketchware block logic and validates it with JavaParser
 * before a full compile cycle. Gives the AI instant feedback on syntax errors
 * without the cost of a slow compilation.
 *
 * Usage by AI agent:
 *   check_logic_syntax(activityName="MainActivity", javaCode="int x = ;")
 *
 * Two modes:
 *  - javaCode provided → raw code check (no block generation needed)
 *  - javaCode omitted  → placeholder; caller should supply javaCode for raw checks
 */
public class LogicSyntaxCheckerTool implements AgentTool {

    @Override
    public String getName() {
        return "check_logic_syntax";
    }

    @Override
    public String getDescription() {
        return "Validates Java code generated from Sketchware block logic using JavaParser. "
                + "Pass the raw Java snippet in 'java_code'. Returns isValid, any error "
                + "message, and the code that was checked. Use this before calling "
                + "build_project to catch syntax errors early.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject javaCode = new JsonObject();
        javaCode.addProperty("type", "string");
        javaCode.addProperty("description",
                "The Java code snippet to validate (method body or full class).");
        properties.add("java_code", javaCode);

        schema.add("properties", properties);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("java_code");
        schema.add("required", required);

        return schema;
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW; // read-only validation, no file writes
    }

    @Override
    public Category getCategory() {
        return Category.ANALYSIS;
    }

    @Override
    public boolean requiresProject() {
        return false;
    }

    @Override
    public ToolResult execute(JsonObject arguments, ToolContext context) {
        String toolCallId = "check_logic_syntax_" + System.currentTimeMillis();

        String javaCode = arguments.has("java_code")
                ? arguments.get("java_code").getAsString() : null;

        if (javaCode == null || javaCode.trim().isEmpty()) {
            return ToolResult.failure(toolCallId, "java_code parameter is required and must not be empty.");
        }

        LogicSyntaxChecker.SyntaxResult result = LogicSyntaxChecker.checkRawCode(javaCode);

        JsonObject output = new JsonObject();
        output.addProperty("isValid", result.isValid);
        output.addProperty("errorMessage", result.errorMessage != null ? result.errorMessage : "");
        output.addProperty("checkedCode", result.generatedCode != null ? result.generatedCode : javaCode);

        return ToolResult.success(toolCallId, output.toString());
    }
}
