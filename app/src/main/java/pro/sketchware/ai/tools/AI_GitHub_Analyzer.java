package pro.sketchware.ai.tools;

import android.util.Log;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pro.sketchware.ai.models.ToolResult;

/**
 * AI GitHub Analyzer - Integrated as Official Agent Tools.
 * This class provides tools for the AI to search and compare GitHub repositories.
 */
public class AI_GitHub_Analyzer {
    private static final String TAG = "AI_GITHUB_TOOL";
    private static final OkHttpClient client = new OkHttpClient();

    // ─────────────────────────────────────────────────────────────────────────
    // TOOL 1: GitHub Compare Tool
    // ─────────────────────────────────────────────────────────────────────────
    public static class GitHubCompareTool implements AgentTool {
        @Override
        public String getName() {
            return "github_compare";
        }

        @Override
        public String getDescription() {
            return "Compares two versions/branches of a GitHub repository. Useful for analyzing differences between project versions.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            
            JsonObject properties = new JsonObject();
            
            JsonObject owner = new JsonObject();
            owner.addProperty("type", "string");
            owner.addProperty("description", "The GitHub username or organization owner of the repo");
            
            JsonObject repo = new JsonObject();
            repo.addProperty("type", "string");
            repo.addProperty("description", "The name of the repository");
            
            JsonObject base = new JsonObject();
            base.addProperty("type", "string");
            base.addProperty("description", "The base commit/branch (e.g., 'main' or 'v1.0')");
            
            JsonObject head = new JsonObject();
            head.addProperty("type", "string");
            head.addProperty("description", "The comparison commit/branch (e.g., 'dev' or 'v2.0')");
            
            properties.add("owner", owner);
            properties.add("repo", repo);
            properties.add("base", base);
            properties.add("head", head);
            
            schema.add("properties", properties);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String owner = arguments.get("owner").getAsString();
            String repo = arguments.get("repo").getAsString();
            String base = arguments.get("base").getAsString();
            String head = arguments.get("head").getAsString();
            
            String url = "https://api.github.com/repos/" + owner + "/" + repo + "/compare/" + base + "..." + head;
            
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Sketchware-AI-Analyzer")
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    return ToolResult.success(context.getCurrentToolCallId(), "Comparison data retrieved successfully: " + body);
                } else {
                    return ToolResult.failure(context.getCurrentToolCallId(), "GitHub API Error: " + response.code());
                }
            } catch (IOException e) {
                return ToolResult.failure(context.getCurrentToolCallId(), "Network Error: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOOL 2: GitHub Search Tool
    // ─────────────────────────────────────────────────────────────────────────
    public static class GitHubSearchTool implements AgentTool {
        @Override
        public String getName() {
            return "github_search";
        }

        @Override
        public String getDescription() {
            return "Searches GitHub for repositories based on a keyword or query.";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            
            JsonObject properties = new JsonObject();
            JsonObject query = new JsonObject();
            query.addProperty("type", "string");
            query.addProperty("description", "The search query (e.g., 'Sketchware Pro AI')");
            
            properties.add("query", query);
            schema.add("properties", properties);
            return schema;
        }

        @Override
        public ToolResult execute(JsonObject arguments, ToolContext context) {
            String query = arguments.get("query").getAsString();
            String url = "https://api.github.com/search/repositories?q=" + query;
            
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Sketchware-AI-Analyzer")
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    return ToolResult.success(context.getCurrentToolCallId(), "Search results retrieved: " + body);
                } else {
                    return ToolResult.failure(context.getCurrentToolCallId(), "GitHub API Error: " + response.code());
                }
            } catch (IOException e) {
                return ToolResult.failure(context.getCurrentToolCallId(), "Network Error: " + e.getMessage());
            }
        }
    }
}
