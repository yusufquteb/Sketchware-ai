package pro.sketchware.ai.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration object that makes AiAssistantBottomSheet page-aware.
 *
 * Each page (Library, LogicEditor, BuildPage, ErrorLog…) creates one of these
 * and passes it to AiAssistantBottomSheet.
 *
 * Tool types:
 *   CATEGORY   → section header (no action)
 *   AI_ASSISTED → fills input, user edits & sends, AI responds
 *   DIRECT      → fills input, user sends, system executes (offline-safe)
 *                 Result shown in chat so user sees what happened.
 */
public class AiPageConfig {

    // ── Tool model ────────────────────────────────────────────────────────────

    public enum ToolType { CATEGORY, AI_ASSISTED, DIRECT }

    public static class Tool {
        public final String   label;
        public final int      iconRes;
        public final ToolType type;
        public final String   inputTemplate;  // fills input field; user edits before send
        public final String   actionKey;       // DIRECT tools: unique key for handler

        /** Category header. */
        public Tool(String label, int iconRes) {
            this(label, iconRes, ToolType.CATEGORY, null, null);
        }

        /** AI-assisted tool. */
        public static Tool ai(String label, int iconRes, String template) {
            return new Tool(label, iconRes, ToolType.AI_ASSISTED, template, null);
        }

        /** Direct tool — system executes, result shown in chat. */
        public static Tool direct(String label, int iconRes, String template, String actionKey) {
            return new Tool(label, iconRes, ToolType.DIRECT, template, actionKey);
        }

        private Tool(String label, int iconRes, ToolType type, String template, String key) {
            this.label         = label;
            this.iconRes       = iconRes;
            this.type          = type;
            this.inputTemplate = template;
            this.actionKey     = key;
        }
    }

    /** Called when user sends a DIRECT tool message. */
    public interface DirectActionHandler {
        /**
         * @param actionKey   the Tool.actionKey
         * @param userInput   the (possibly edited) input the user sent
         * @return human-readable result for the chat; null = show generic "Done"
         */
        String execute(String actionKey, String userInput);
    }

    // ── Config fields ─────────────────────────────────────────────────────────

    public final String               pageTitle;
    public final String               scopeLabel;
    public final String               inputHint;
    public final String               systemPrompt;
    public final List<Tool>           tools;
    public final DirectActionHandler  directActionHandler;

    private AiPageConfig(Builder b) {
        this.pageTitle           = b.pageTitle;
        this.scopeLabel          = b.scopeLabel;
        this.inputHint           = b.inputHint;
        this.systemPrompt        = b.systemPrompt;
        this.tools               = b.tools;
        this.directActionHandler = b.directActionHandler;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static class Builder {
        String pageTitle = "AI Assistant";
        String scopeLabel = "";
        String inputHint = "Ask anything…";
        String systemPrompt = "";
        List<Tool> tools = new ArrayList<>();
        DirectActionHandler directActionHandler = null;

        public Builder pageTitle(String t)              { pageTitle = t;           return this; }
        public Builder scopeLabel(String s)             { scopeLabel = s;          return this; }
        public Builder inputHint(String h)              { inputHint = h;           return this; }
        public Builder systemPrompt(String sp)          { systemPrompt = sp;       return this; }
        public Builder tools(List<Tool> t)              { tools = t;               return this; }
        public Builder directActions(DirectActionHandler h) { directActionHandler = h; return this; }
        public AiPageConfig build()                     { return new AiPageConfig(this); }
    }
}
