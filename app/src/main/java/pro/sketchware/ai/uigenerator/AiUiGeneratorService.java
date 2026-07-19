package pro.sketchware.ai.uigenerator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.engine.AIEngine;
import pro.sketchware.ai.engine.CacheManager;

/**
 * AiUiGeneratorService — REFACTORED to use the full AIEngine pipeline.
 *
 * <p>Replaces the previous GeradorDeLayoutPro approach with the production-grade:
 * <pre>
 *   ModelManager (fallback) → PromptBuilder → AI → XMLValidator → CacheManager
 * </pre>
 *
 * <p>Improvements over the previous version:
 * <ul>
 *   <li><b>Model fallback</b> — tries all ACTIVE_MODELS in order, not just one</li>
 *   <li><b>Caching</b> — identical prompts skip the AI call entirely</li>
 *   <li><b>Validation</b> — output is validated and auto-fixed before delivery</li>
 *   <li><b>Streaming</b> — live token display during generation</li>
 *   <li><b>Guardrails</b> — strict prompt rules prevent hallucinated attributes</li>
 * </ul>
 *
 * <p>All callbacks are delivered on the <b>main thread</b>.
 */
public class AiUiGeneratorService {

    private static final String TAG = "AiUiGeneratorService";

    /** Callback interface for generation results. Always called on the main thread. */
    public interface GenerationCallback {
        /** Called periodically with a human-readable status update. */
        void onProgress(String statusMessage);
        /**
         * Called on success.
         * @param layoutXml    validated, auto-fixed Android XML
         * @param components   views with android:id parsed from the XML
         * @param fromCache    true if this result was served from cache (no AI call)
         * @param wasAutoFixed true if XMLValidator applied automatic fixes
         */
        void onSuccess(String layoutXml, List<UiComponent> components,
                       boolean fromCache, boolean wasAutoFixed);
        /** Called on failure. */
        void onError(String errorMessage);
        /**
         * Called with streaming tokens during generation (optional — may not be set).
         * Update a preview text field with this for real-time feedback.
         */
        default void onStreamingChunk(String chunk) {}
    }

    /** A single UI component identified by android:id in the generated layout. */
    public static class UiComponent {
        public final String id;
        public final String type;
        public UiComponent(String id, String type) { this.id = id; this.type = type; }
    }

    private final Context    context;
    private final AIEngine   engine;
    private final Handler    mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isGenerating = false;

    public AiUiGeneratorService(Context context) {
        this.context = context.getApplicationContext();
        this.engine  = new AIEngine(context);
    }

    public boolean isGenerating() { return isGenerating; }

    // ── Generation ────────────────────────────────────────────────────────────

    /**
     * Generates a layout from a natural-language prompt.
     *
     * @param userPrompt   free-text description of the desired screen
     * @param activityName name of the activity/screen (used in the prompt for context)
     * @param projectPkg   app package name (used in the prompt for context)
     * @param callback     result callback (always on main thread)
     */
    public void generate(String userPrompt, String activityName,
                          String projectPkg, GenerationCallback callback) {
        if (isGenerating) {
            mainHandler.post(() -> callback.onError(
                    "A layout is already being generated. Please wait."));
            return;
        }
        isGenerating = true;
        engine.reset();

        // Attach streaming callback for live preview
        engine.setStreamCallback(chunk -> mainHandler.post(() -> callback.onStreamingChunk(chunk)));

        engine.generateUi(userPrompt, activityName, projectPkg, new AIEngine.EngineCallback() {
            @Override public void onProgress(String msg) { callback.onProgress(msg); }

            @Override public void onSuccess(String xml, boolean fromCache, boolean wasAutoFixed) {
                isGenerating = false;
                List<UiComponent> components = parseComponentsFromXml(xml);
                callback.onSuccess(xml, components, fromCache, wasAutoFixed);
                Log.i(TAG, "Generation complete — " + components.size() + " component(s), "
                        + (fromCache ? "from cache" : "live AI")
                        + (wasAutoFixed ? ", auto-fixed" : ""));
            }

            @Override public void onError(String errorMessage) {
                isGenerating = false;
                callback.onError(errorMessage);
                Log.e(TAG, "Generation failed: " + errorMessage);
            }
        });
    }

    /**
     * Simplified overload — uses empty activityName and projectPkg.
     * Kept for backward compatibility with existing callers.
     */
    public void generate(String userPrompt, GenerationCallback callback) {
        generate(userPrompt, "", "", callback);
    }

    // ── Modify ────────────────────────────────────────────────────────────────

    /**
     * Modifies an EXISTING layout according to user instructions.
     *
     * @param userPrompt   what to change
     * @param existingXml  the current layout XML
     * @param activityName the screen being modified
     * @param callback     result callback
     */
    public void modify(String userPrompt, String existingXml,
                        String activityName, GenerationCallback callback) {
        if (isGenerating) {
            mainHandler.post(() -> callback.onError(
                    "A layout operation is already in progress. Please wait."));
            return;
        }
        isGenerating = true;
        engine.reset();
        engine.setStreamCallback(chunk -> mainHandler.post(() -> callback.onStreamingChunk(chunk)));

        engine.modifyUi(userPrompt, existingXml, activityName, new AIEngine.EngineCallback() {
            @Override public void onProgress(String msg) { callback.onProgress(msg); }

            @Override public void onSuccess(String xml, boolean fromCache, boolean wasAutoFixed) {
                isGenerating = false;
                callback.onSuccess(xml, parseComponentsFromXml(xml), fromCache, wasAutoFixed);
            }

            @Override public void onError(String err) {
                isGenerating = false;
                callback.onError(err);
            }
        });
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    /** Cancels any in-flight generation. */
    public void cancel() {
        engine.cancel();
        isGenerating = false;
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    /** Returns cache statistics string (for debug display). */
    public String getCacheStats() {
        return CacheManager.getInstance().stats();
    }

    /** Clears the response cache. */
    public void clearCache() {
        CacheManager.getInstance().clear();
    }

    // ── Component extraction ───────────────────────────────────────────────────

    /**
     * Parses the generated XML and extracts all views that have an android:id attribute.
     */
    private List<UiComponent> parseComponentsFromXml(String xml) {
        List<UiComponent> components = new ArrayList<>();
        if (xml == null || xml.trim().isEmpty()) return components;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    String rawId   = parser.getAttributeValue(null, "android:id");
                    if (rawId != null && !rawId.isEmpty()) {
                        String id = rawId.replace("@+id/", "").replace("@id/", "");
                        components.add(new UiComponent(id, simplifyTagName(tagName)));
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.w(TAG, "Component parse warning: " + e.getMessage());
        }
        return components;
    }

    private static String simplifyTagName(String tag) {
        if (tag == null) return "View";
        int dot = tag.lastIndexOf('.');
        return dot >= 0 ? tag.substring(dot + 1) : tag;
    }
}
