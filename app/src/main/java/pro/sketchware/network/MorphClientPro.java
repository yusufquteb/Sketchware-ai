package pro.sketchware.network;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * MorphClientPro — Morph code-edit AI client for Sketchware Pro.
 *
 * Ported from Sketchware-IA's MorphClient and adapted to use Pro's
 * AiPreferences for settings storage (morph_api_key, morph_enabled,
 * morph_for_layout) instead of the IA-specific ia_settings prefs.
 *
 * Morph (morphllm.com) is a specialized model for applying code edits —
 * it refines AI-generated XML layouts to produce cleaner output.
 * Use {@link #applyCodeEdit(String, String, String)} to refine layout XML.
 */
public class MorphClientPro {

    private static final String TAG = "MorphClientPro";
    private static final String BASE_URL = "https://api.morphllm.com/v1/chat/completions";
    private static final String MODEL    = "morph-v3-fast";

    private final Context context;
    private final OkHttpClient client;

    public MorphClientPro(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Applies a code edit using Morph's specialized merging model.
     * Uses Pro's AiPreferences for morph_api_key.
     *
     * @param initialCode  The original code (or a close equivalent)
     * @param codeEdit     The edit to apply (may contain placeholder comments)
     * @param instructions What change to make
     * @return The merged/refined code, or the original if Morph fails
     * @throws IOException if the API call fails
     */
    public String applyCodeEdit(String initialCode, String codeEdit,
                                String instructions) throws IOException {
        AiPreferences prefs = AiPreferences.getInstance(context);

        if (!prefs.isMorphEnabled()) {
            throw new IOException("Morph is not enabled. Configure it in AI Settings → Layout Refinement.");
        }

        String apiKey = prefs.getMorphApiKey();
        if (apiKey.isEmpty()) {
            throw new IOException("Morph API key is not configured. Visit morphllm.com to get one.");
        }

        // Morph-specific message format: <instruction>…</instruction><code>…</code><update>…</update>
        String content = String.format(
                "<instruction>%s</instruction>\n<code>%s</code>\n<update>%s</update>",
                instructions, initialCode, codeEdit);

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", MODEL);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", content));
            jsonBody.put("messages", messages);
            jsonBody.put("temperature", 0.2); // Low temp for precise code edits
            jsonBody.put("max_tokens", 4096);
        } catch (Exception e) {
            throw new IOException("Error preparing Morph request", e);
        }

        Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody.toString(),
                        MediaType.parse("application/json")))
                .build();

        int maxRetries = 3;
        long backoffMs = 1000L;
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    int code = response.code();
                    if (attempt < maxRetries && (code == 429 || code >= 500)) {
                        Thread.sleep(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, 16000L);
                        continue;
                    }
                    String body = response.body() != null ? response.body().string() : "";
                    throw new IOException("Morph API error " + code + ": " + body);
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray choices = jsonResponse.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    throw new IOException("Morph API returned empty choices");
                }
                JSONObject firstChoice = choices.getJSONObject(0);
                String refined = null;
                if (firstChoice.has("message")) {
                    refined = firstChoice.getJSONObject("message").optString("content", null);
                }
                if (refined == null) {
                    refined = firstChoice.optString("text", null);
                }
                if (refined == null || refined.isEmpty()) {
                    throw new IOException("Morph API returned empty content");
                }
                return cleanXml(refined);

            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
                    backoffMs = Math.min(backoffMs * 2, 16000L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Morph request interrupted", e);
            } catch (Exception e) {
                throw new IOException("Error processing Morph response", e);
            }
        }

        throw lastException != null ? lastException
                : new IOException("Unknown error contacting Morph API");
    }

    /**
     * Cleans XML returned by Morph (removes markdown fences, trims whitespace).
     */
    private String cleanXml(String xml) {
        if (xml == null) return "";
        String cleaned = xml.replace("```xml", "").replace("```", "").trim();
        // Remove <?xml?> declaration (Sketchware doesn't need it in ViewBean XML)
        cleaned = cleaned.replaceFirst("^<\\?xml[^>]*>\\s*", "");
        int first = cleaned.indexOf('<');
        int last  = cleaned.lastIndexOf('>');
        if (first >= 0 && last > first) {
            cleaned = cleaned.substring(first, last + 1);
        }
        return cleaned.trim();
    }
}
