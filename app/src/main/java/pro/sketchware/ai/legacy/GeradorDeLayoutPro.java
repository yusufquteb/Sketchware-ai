package pro.sketchware.ai.legacy;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import pro.sketchware.ai.api.AiApiClient;
import pro.sketchware.ai.api.AiClientFactory;
import pro.sketchware.ai.api.StreamingResponseHandler;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.storage.AiPreferences;
import pro.sketchware.network.MorphClientPro;

/**
 * GeradorDeLayoutPro — AI layout generator for Sketchware Pro.
 *
 * <p><b>Package corrected from the original {@code pro.sketchware.ia} (typo) to
 * {@code pro.sketchware.ai.legacy}.</b> Still actively used by
 * {@code AiProjectBottomSheet} (inline layout generator) and
 * {@code DesignXmlEditorTool} (the {@code generate_layout} / {@code GenerateLayoutTool}
 * agent tool) — this is NOT dead code, despite {@code AiUiGeneratorService}'s header
 * comment describing a separate, newer pipeline that replaced a different
 * (unrelated) UI-generator flow. Placed under {@code legacy} because it predates
 * the current {@code AIEngine} pipeline and uses a simpler blocking-call design;
 * keep until both call sites are migrated to {@code AiUiGeneratorService}.
 *
 * Ported from Sketchware-IA's GeradorDeLayout:
 * - Uses Pro's AiApiClient/AiPreferences (any configured provider)
 * - Same high-quality Sketchware-aware prompt that IA uses → proper layouts
 * - Optionally refines output with MorphClientPro (MORF)
 * - Blocking API call via CountDownLatch (runs on background thread)
 *
 * This replaces the JSON-format approach that produced bad layouts
 * (just a TextView + Button instead of a real calculator, etc.)
 */
public final class GeradorDeLayoutPro {

    private static final String TAG = "GeradorDeLayoutPro";
    /** Timeout for a single AI request (seconds). */
    private static final long REQUEST_TIMEOUT_SECONDS = 120L;

    private final Context context;
    private final String userPrompt;
    /** Optional: pass the current layout XML to get a refinement instead of a new layout. */
    private final String currentLayout;

    public GeradorDeLayoutPro(Context context, String userPrompt) {
        this(context, userPrompt, null);
    }

    public GeradorDeLayoutPro(Context context, String userPrompt, String currentLayout) {
        this.context = context.getApplicationContext();
        this.userPrompt = userPrompt;
        this.currentLayout = currentLayout;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Generates a Sketchware-compatible Android XML layout.
     * BLOCKING — must be called on a background thread.
     *
     * @return Clean Android XML layout string (no markdown fences, no <?xml?> header)
     * @throws IOException if AI call fails, API key missing, or response is not XML
     */
    public String generateLayout() throws IOException {
        String fullPrompt = buildPrompt();
        String rawResponse = sendBlockingRequest(fullPrompt);
        String cleanXml = cleanXmlLayout(rawResponse);

        if (!looksLikeXml(cleanXml)) {
            throw new IOException("AI response did not return usable XML. Try again or rephrase your request.");
        }

        // ── Optional Morph (MORF) refinement ─────────────────────────────────
        AiPreferences prefs = AiPreferences.getInstance(context);
        if (prefs.isMorphForLayoutEnabled()) {
            cleanXml = applyMorphRefinement(cleanXml, prefs);
        }

        return cleanXml;
    }

    // ── Prompt builder (mirrors IA's GeradorDeLayout.montarPromptBase) ─────────

    private String buildPrompt() {
        StringJoiner p = new StringJoiner("\n");

        p.add("You generate Android XML layouts that must be compatible with Sketchware.");
        p.add("Return only XML. No markdown, no explanations, no comments.");
        p.add("Prefer returning only the children that belong inside the screen root.");
        p.add("If you decide to return a root layout, return exactly one root ViewGroup.");
        p.add("");
        p.add("== RULES ==");
        p.add("1. Use only Sketchware-supported components and attributes.");
        p.add("2. Every interactive view MUST have android:id in @+id/name format.");
        p.add("3. Keep the hierarchy simple, readable and mobile friendly.");
        p.add("4. Use valid Android XML attribute names and values.");
        p.add("5. Prefer 8dp or 16dp spacing and text sizes in sp.");
        p.add("6. Do not use Compose, data binding, or unsupported custom XML.");
        p.add("7. Keep the root layout clean unless explicitly requested.");
        p.add("8. Use layout_weight or match_parent for responsiveness.");
        p.add("");
        p.add("== SUPPORTED COMPONENTS ==");
        Map<String, List<String>> types = getSupportedTypes();
        p.add("Layouts: " + String.join(", ", types.get("layouts")));
        p.add("Widgets: " + String.join(", ", types.get("widgets")));
        p.add("");
        p.add("== DESIGN STANDARDS ==");
        p.add("1. Buttons MUST have centered text: android:gravity=\"center\".");
        p.add("2. Use consistent 8dp or 16dp padding/margins.");
        p.add("3. All interactive elements: minimum 48dp touch target.");
        p.add("4. Avoid unnecessary nesting.");
        p.add("5. For calculators: use a GridView-style LinearLayout grid of Buttons.");
        p.add("6. For forms: use proper EditText with hints and InputType.");
        p.add("7. For lists: use ListView or RecyclerView.");
        p.add("");

        if (currentLayout != null && !currentLayout.trim().isEmpty()) {
            p.add("== CURRENT LAYOUT (read this before making changes) ==");
            p.add(currentLayout.trim());
            p.add("");
            p.add("== MODIFICATION REQUEST ==");
            p.add(userPrompt);
            p.add("");
            p.add("Return ONLY the complete updated XML. Preserve existing views unless explicitly asked to remove them.");
        } else {
            p.add("== GENERATION REQUEST ==");
            p.add(userPrompt);
            p.add("");
            p.add("Return ONLY compact Android XML. No markdown. No explanations. Start with the root element.");
        }

        return p.toString();
    }

    private static Map<String, List<String>> getSupportedTypes() {
        Map<String, List<String>> t = new HashMap<>();
        t.put("layouts", Arrays.asList(
                "LinearLayout", "RelativeLayout", "HorizontalScrollView", "ScrollView",
                "TabLayout", "BottomNavigationView", "CardView", "CollapsingToolbarLayout",
                "TextInputLayout", "SwipeRefreshLayout", "RadioGroup", "NestedScrollView"));
        t.put("widgets", Arrays.asList(
                "Button", "TextView", "EditText", "ImageView", "WebView", "ProgressBar",
                "ListView", "Spinner", "CheckBox", "Switch", "SeekBar", "CalendarView",
                "RadioButton", "RatingBar", "VideoView", "SearchView", "RecyclerView",
                "MaterialButton", "TextInputEditText", "ImageButton", "MaterialSwitch"));
        return t;
    }

    // ── Blocking HTTP call using Pro's AiApiClient ─────────────────────────────

    private String sendBlockingRequest(String prompt) throws IOException {
        AiPreferences prefs = AiPreferences.getInstance(context);

        // Use layout-specific provider if configured, otherwise use selected provider
        AiProvider provider = getLayoutProvider(prefs);
        String apiKey = prefs.getApiKey(provider);
        String modelId = prefs.getSelectedModel(provider);

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IOException(
                    "No API key configured for " + provider.getDisplayName()
                    + ". Go to AI Settings to configure.");
        }

        AiApiClient client = AiClientFactory.createClient(context, provider, apiKey);
        if (client == null) {
            throw new IOException("Cannot create AI client for: " + provider.getDisplayName());
        }

        // System prompt: enforce XML-only response
        String systemPrompt = "You are an Android XML layout generator for Sketchware Pro. "
                + "RULES: Return ONLY valid Android XML. "
                + "NEVER use markdown fences (```xml or ```). "
                + "NEVER add explanations or comments. "
                + "NEVER include <?xml?> declaration. "
                + "Start immediately with the root ViewGroup element (e.g. <LinearLayout). "
                + "Keep XML compact — avoid unnecessary nesting. "
                + "Every interactive view MUST have android:id in @+id/name format.";

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(null, prompt)); // user message

        // Block until streaming completes
        CountDownLatch latch = new CountDownLatch(1);
        String[] result = {null};
        String[] error  = {null};
        StringBuilder streamBuffer = new StringBuilder();

        client.sendChatRequest(messages, modelId, systemPrompt,
                new StreamingResponseHandler() {
                    @Override public void onChunk(String delta)         { streamBuffer.append(delta); }
                    @Override public void onToolCall(ToolCall toolCall) { /* layout gen has no tools */ }

                    @Override
                    public void onComplete(String fullResponse) {
                        result[0] = fullResponse.isEmpty()
                                ? streamBuffer.toString() : fullResponse;
                        latch.countDown();
                    }

                    @Override
                    public void onError(String errMsg) {
                        error[0] = errMsg;
                        latch.countDown();
                    }
                });

        try {
            boolean finished = latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                throw new IOException("AI request timed out after "
                        + REQUEST_TIMEOUT_SECONDS + "s. Try a simpler layout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Layout generation interrupted.");
        }

        if (error[0] != null) throw new IOException(error[0]);
        if (result[0] == null || result[0].trim().isEmpty()) {
            throw new IOException("AI returned an empty response. Check provider status.");
        }

        return result[0];
    }

    // ── Morph refinement ───────────────────────────────────────────────────────

    private String applyMorphRefinement(String xml, AiPreferences prefs) {
        Log.d(TAG, "Applying Morph refinement...");
        try {
            MorphClientPro morph = new MorphClientPro(context);
            String instructions =
                    "Refine this Android XML for Sketchware. "
                    + "Keep it valid, compact, and well-indented. "
                    + "Remove any markdown fences or XML comments. "
                    + "Ensure every interactive view has android:id. "
                    + "Preserve all widget IDs and the complete UI structure.";
            String refined = morph.applyCodeEdit(xml, xml, instructions);
            if (looksLikeXml(refined)) {
                Log.d(TAG, "Morph refinement applied.");
                return refined;
            }
        } catch (Exception e) {
            Log.w(TAG, "Morph refinement skipped (using original): " + e.getMessage());
        }
        return xml;
    }

    // ── Provider selection: Groq is default for layout (fast + good XML output) ─

    private AiProvider getLayoutProvider(AiPreferences prefs) {
        // Use the layout-specific provider if set, otherwise use the selected provider
        String layoutProviderName = prefs.prefs().getString(
                AiPreferences.KEY_LAYOUT_AI_PROVIDER, "");
        if (!layoutProviderName.isEmpty()) {
            AiProvider p = AiProvider.fromName(layoutProviderName);
            if (p != null && prefs.getApiKey(p) != null && !prefs.getApiKey(p).isEmpty()) {
                return p;
            }
        }
        // Fallback to the currently selected provider
        return prefs.getSelectedProvider();
    }

    // ── XML helpers ────────────────────────────────────────────────────────────

    private boolean looksLikeXml(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        String t = s.trim();
        return t.contains("<") && t.contains(">");
    }

    public static String cleanXmlLayout(String layout) {
        if (layout == null) return "";
        String c = layout.replace("```xml", "").replace("```", "").trim();
        c = c.replaceFirst("^<\\?xml[^>]*>\\s*", "");
        int first = c.indexOf('<');
        int last  = c.lastIndexOf('>');
        if (first >= 0 && last > first) c = c.substring(first, last + 1);
        return c.trim();
    }
}
