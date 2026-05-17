package pro.sketchware.ai.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Centralized, user-friendly AI error message helper.
 * All provider clients call getFriendlyMessage() instead of raw HTTP codes.
 */
public final class AiErrorHelper {

    private AiErrorHelper() {}

    public static String getFriendlyMessage(int code, String rawError) {
        switch (code) {
            case 400:
                return "Bad Request \u26a0\ufe0f\nThe AI provider rejected the request. "
                        + "This is usually a model configuration issue. Try switching models.";
            case 401:
                return "Invalid API Key \u26a0\ufe0f\nYour API key is wrong or expired. "
                        + "Go to AI Settings to update it.";
            case 402:
                return "Insufficient Balance \uD83D\uDCB8\nYour account credit is empty. "
                        + "Top up at the provider\u2019s website, or switch to a free provider: "
                        + "Groq \u221e, Cerebras, or Google AI Studio.";
            case 403:
                return "Access Denied \uD83D\uDEAB\nYour API key doesn\u2019t have permission for this model, "
                        + "or the free tier is exhausted. Try Groq \u221e or Cerebras (both free).";
            case 404:
                return "Model Not Found \uD83D\uDD0D\nThe selected model is no longer available. "
                        + "Go to AI Settings \u2192 tap \uD83D\uDD04 to refresh the model list, then choose a different model.";
            case 408:
                return "Request Timed Out \u23F1\ufe0f\nThe AI took too long to respond. "
                        + "Try a shorter message or switch to a faster provider like Groq \u221e.";
            case 413:
                return "Message Too Long \uD83D\uDCCF\nYour message or conversation history is too large. "
                        + "Start a new conversation or shorten your message.";
            case 422:
                if (rawError != null && rawError.toLowerCase().contains("model")) {
                    return "Model Error \uD83D\uDD0D\nThe selected model name is invalid or no longer supported. "
                            + "Go to AI Settings \u2192 tap \uD83D\uDD04 Refresh, then select a valid model.";
                }
                return "Invalid Request \u26a0\ufe0f\nThe AI provider couldn\u2019t process this request. "
                        + "This usually means the model name is wrong. "
                        + "Refresh models in AI Settings and try again.";
            case 429:
                return "Rate Limit Reached \u23f3\nToo many requests sent — the provider needs a short break. "
                        + "Wait 30-60 seconds and retry, or switch to Groq \u221e (truly unlimited) "
                        + "or Cerebras (free, high limits). Both work without an API key.";
            case 500:
                return "AI Server Error \uD83D\uDEA8\nSomething went wrong on the provider\u2019s side. "
                        + "Please try again in a few minutes.";
            case 502:
            case 503:
            case 504:
                return "Service Temporarily Unavailable \uD83D\uDCA4\n"
                        + "The AI provider is overloaded or down for maintenance. "
                        + "Please try again shortly, or switch to another provider.";
            default:
                String desc = code >= 500
                        ? "Server Error \uD83D\uDEA8 (the AI provider has a problem)"
                        : code >= 400
                        ? "Request Error \u26a0\ufe0f (the request could not be processed)"
                        : "Unexpected Error \u26a0\ufe0f";
                if (rawError != null && !rawError.isEmpty() && rawError.length() < 200) {
                    return desc + "\n" + rawError;
                }
                return desc + "\nSomething went wrong. Please try again or switch providers.";
        }
    }

    public static String readBodySafely(Response response) {
        try {
            ResponseBody body = response.body();
            if (body == null) return "(no response body)";
            String content = body.string();
            try {
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("error")) {
                    if (json.get("error").isJsonObject()) {
                        JsonObject err = json.getAsJsonObject("error");
                        if (err.has("message")) return err.get("message").getAsString();
                    } else if (json.get("error").isJsonPrimitive()) {
                        return json.get("error").getAsString();
                    }
                }
                if (json.has("message")) return json.get("message").getAsString();
            } catch (Exception ignored) {}
            return content.length() > 500 ? content.substring(0, 500) + "\u2026" : content;
        } catch (Exception e) {
            return "(failed to read response: " + e.getMessage() + ")";
        }
    }
}
