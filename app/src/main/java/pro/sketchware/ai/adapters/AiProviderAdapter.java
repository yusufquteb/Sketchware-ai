// nikit overhaul — Task 2 — 2026-05
package pro.sketchware.ai.adapters;

import android.content.Context;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.models.AiProvider;

/**
 * RecyclerView adapter for the AI Providers list in AiSettingsActivity.
 * Each provider gets a card with icon, name, status, enable switch,
 * API key field, and Get Key / Refresh Models buttons.
 */
public class AiProviderAdapter extends RecyclerView.Adapter<AiProviderAdapter.ViewHolder> {

    public interface ProviderCallback {
        /** Called when user toggles the switch for a provider */
        void onToggle(AiProvider provider, boolean enabled);
        /** Called when user types and loses focus from the API key field */
        void onKeyChanged(AiProvider provider, String key);
        /** Called when user taps "Get Key" */
        void onGetKey(AiProvider provider);
        /** Called when user taps "Refresh Models" */
        void onRefresh(AiProvider provider);
    }

    /** Per-provider state (enabled, key, models count text) */
    public static class ProviderState {
        public final AiProvider provider;
        public boolean enabled;
        public String apiKey;         // masked — only for display/restore
        public String modelsCountText;
        public boolean keyVisible;

        public ProviderState(AiProvider provider, boolean enabled,
                             String apiKey, String modelsCountText) {
            this.provider       = provider;
            this.enabled        = enabled;
            this.apiKey         = apiKey != null ? apiKey : "";
            this.modelsCountText = modelsCountText != null ? modelsCountText : "";
            this.keyVisible     = false;
        }
    }

    private final List<ProviderState> states = new ArrayList<>();
    private final ProviderCallback callback;

    public AiProviderAdapter(ProviderCallback callback) {
        this.callback = callback;
    }

    public void setStates(List<ProviderState> newStates) {
        states.clear();
        // Task 2: LOCAL_LLM is managed via its own dedicated section — exclude from main list
        for (ProviderState s : newStates) {
            if (s.provider != AiProvider.LOCAL_LLM) states.add(s);
        }
        notifyDataSetChanged();
    }

    /** Programmatically toggle a provider (e.g. Manus revert) */
    public void setEnabled(AiProvider provider, boolean enabled) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).provider == provider) {
                states.get(i).enabled = enabled;
                notifyItemChanged(i);
                return;
            }
        }
    }

    /** Updates the models count text for one provider without full rebind */
    public void setModelCount(AiProvider provider, String text) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).provider == provider) {
                states.get(i).modelsCountText = text;
                notifyItemChanged(i);
                return;
            }
        }
    }

    @Override public int getItemCount() { return states.size(); }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ai_provider, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        ProviderState state = states.get(pos);
        AiProvider provider = state.provider;
        Context ctx = h.itemView.getContext();

        // ── Icon ──────────────────────────────────────────────────────────────
        h.icon.setImageResource(getIconFor(provider));

        // ── Name & badge ──────────────────────────────────────────────────────
        h.name.setText(provider.getDisplayName());

        if (provider.isUnlimited()) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText("\u221e Unlimited");
        } else if (!provider.requiresApiKey()) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText("FREE");
        } else {
            h.badge.setVisibility(View.GONE);
        }

        // ── Status & models count ─────────────────────────────────────────────
        h.status.setText(state.enabled ? "Enabled" : "Disabled");
        h.status.setTextColor(state.enabled
                ? 0xFF4CAF50   // green
                : ctx.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary})
                      .getColor(0, 0xFF888888));
        h.modelsCount.setText(state.modelsCountText);
        h.modelsCount.setVisibility(
                state.modelsCountText.isEmpty() ? View.GONE : View.VISIBLE);

        // ── Switch ────────────────────────────────────────────────────────────
        h.toggle.setOnCheckedChangeListener(null);  // prevent spurious triggers
        h.toggle.setChecked(state.enabled);
        h.toggle.setOnCheckedChangeListener((btn, checked) -> {
            state.enabled = checked;
            h.status.setText(checked ? "Enabled" : "Disabled");
            h.status.setTextColor(checked ? 0xFF4CAF50
                    : ctx.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary})
                          .getColor(0, 0xFF888888));
            // Show expandable section when enabled for any provider (key required or free)
            h.layoutApiKey.setVisibility(checked ? View.VISIBLE : View.GONE);
            callback.onToggle(provider, checked);
        });

        // ── API Key section ───────────────────────────────────────────────────
        // Always show the expandable section when enabled (both key-required and free)
        h.layoutApiKey.setVisibility(state.enabled ? View.VISIBLE : View.GONE);

        h.inputApiKey.removeTextChangedListener(null);
        if (!state.apiKey.isEmpty()) {
            h.inputApiKey.setText(state.apiKey);
        } else {
            h.inputApiKey.setText("");
        }
        // Show/hide eye toggle
        state.keyVisible = false;
        h.inputApiKey.setTransformationMethod(PasswordTransformationMethod.getInstance());
        h.btnShowKey.setOnClickListener(v -> {
            state.keyVisible = !state.keyVisible;
            if (state.keyVisible) {
                h.inputApiKey.setTransformationMethod(SingleLineTransformationMethod.getInstance());
                h.btnShowKey.setImageResource(R.drawable.ic_mtrl_preview);
            } else {
                h.inputApiKey.setTransformationMethod(PasswordTransformationMethod.getInstance());
                h.btnShowKey.setImageResource(R.drawable.ic_mtrl_preview_off);
            }
            h.inputApiKey.setSelection(h.inputApiKey.getText().length());
        });

        // Copy key
        h.btnCopyKey.setOnClickListener(v -> {
            String key = h.inputApiKey.getText().toString().trim();
            if (!key.isEmpty()) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("api_key", key));
                    com.google.android.material.snackbar.Snackbar
                            .make(h.itemView, "Key copied", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        // Save key on focus loss
        h.inputApiKey.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String key = h.inputApiKey.getText().toString().trim();
                state.apiKey = key;
                callback.onKeyChanged(provider, key);
            }
        });

        // Free providers — show a "Visit Website" button; disable key input
        if (!provider.requiresApiKey()) {
            h.inputApiKey.setEnabled(false);
            h.inputApiKey.setHint("No API key required — free access");
            h.inputApiKey.setText("");
            h.btnShowKey.setVisibility(View.GONE);
            h.btnCopyKey.setVisibility(View.GONE);
            // Repurpose Get Key button as "Visit Website"
            h.btnGetKey.setVisibility(View.VISIBLE);
            h.getKeyLabel.setText("Free provider — visit " + getFreeProviderWebsite(provider));
            h.btnGetKey.setOnClickListener(v -> callback.onGetKey(provider));
        } else {
            h.inputApiKey.setEnabled(true);
            h.inputApiKey.setHint("Enter API key");
            h.btnShowKey.setVisibility(View.VISIBLE);
            h.btnCopyKey.setVisibility(View.VISIBLE);
            // Get Key button label and action
            h.getKeyLabel.setText("Get your API key from " + getKeySourceLabel(provider));
            h.btnGetKey.setVisibility(View.VISIBLE);
            h.btnGetKey.setOnClickListener(v -> callback.onGetKey(provider));
        }

        // Refresh button
        h.btnRefresh.setOnClickListener(v -> {
            String key = h.inputApiKey.getText().toString().trim();
            state.apiKey = key;
            callback.onKeyChanged(provider, key);
            callback.onRefresh(provider);
        });
    }

    // ── Icon mapping ──────────────────────────────────────────────────────────

    @DrawableRes
    private static int getIconFor(AiProvider p) {
        switch (p) {
            case GEMINI:           return R.drawable.ic_provider_gemini;
            case OPENAI:           return R.drawable.ic_provider_openai;
            case ANTHROPIC:        return R.drawable.ic_provider_anthropic;
            case DEEPSEEK:         return R.drawable.ic_provider_deepseek;
            case XAI_GROK:         return R.drawable.ic_provider_xai_grok;
            case GROQ:             return R.drawable.ic_provider_groq;
            case NVIDIA:           return R.drawable.ic_provider_nvidia;
            case OPENROUTER:       return R.drawable.ic_provider_openrouter;
            case DEEPINFRA:        return R.drawable.ic_provider_deepinfra;
            case TOGETHER:         return R.drawable.ic_provider_together;
            case HUGGINGFACE:      return R.drawable.ic_provider_huggingface;
            case CEREBRAS:         return R.drawable.ic_provider_cerebras;
            case GOOGLE_AI_STUDIO: return R.drawable.ic_provider_google_ai_studio;
            case SAMBANOVA:        return R.drawable.ic_provider_sambanova;
            // providers without dedicated icons — use generic stack icon
            default:               return R.drawable.ic_provider_local_llm;
        }
    }

    private static String getFreeProviderWebsite(AiProvider p) {
        switch (p) {
            case CHUTES:           return "api.airforce";
            case GOOGLE_AI_STUDIO: return "aistudio.google.com";
            case SAMBANOVA:        return "cloud.sambanova.ai";
            case HUGGINGFACE:      return "huggingface.co";
            default:               return p.getBaseUrl().replace("https://", "");
        }
    }

    private static String getKeySourceLabel(AiProvider p) {
        switch (p) {
            case GEMINI:           return "Google AI Studio → aistudio.google.com";
            case OPENAI:           return "OpenAI Platform → platform.openai.com";
            case ANTHROPIC:        return "Anthropic Console → console.anthropic.com";
            case DEEPSEEK:         return "DeepSeek Platform → platform.deepseek.com";
            case XAI_GROK:         return "xAI Console → console.x.ai";
            case GROQ:             return "Groq Console → console.groq.com";
            case NVIDIA:           return "NVIDIA Build → build.nvidia.com";
            case OPENROUTER:       return "OpenRouter → openrouter.ai/keys";
            case DEEPINFRA:        return "DeepInfra → deepinfra.com/dash";
            case TOGETHER:         return "Together AI → api.together.ai";
            case HUGGINGFACE:      return "HuggingFace → huggingface.co/settings/tokens";
            case CEREBRAS:         return "Cerebras Cloud → cloud.cerebras.ai";
            case GOOGLE_AI_STUDIO: return "Google AI Studio → aistudio.google.com";
            case SAMBANOVA:        return "SambaNova Cloud → cloud.sambanova.ai";
            case MISTRAL:          return "Mistral Console → console.mistral.ai";
            case COHERE:           return "Cohere Dashboard → dashboard.cohere.com";
            case HYPERBOLIC:       return "Hyperbolic → app.hyperbolic.ai";
            case KLUSTER:          return "Kluster AI → platform.kluster.ai";
            case OVH:              return "OVH Cloud → horizon.cloud.ovh.net";
            case CLOUDFLARE:       return "Cloudflare → dash.cloudflare.com";
            case GITHUB_MODELS:    return "GitHub → github.com/settings/tokens";
            case LAMBDA:           return "Lambda Labs → cloud.lambdalabs.com";
            case SCALEWAY:         return "Scaleway → console.scaleway.com";
            case FIREWORKS:        return "Fireworks AI → fireworks.ai/account/api-keys";
            case NOVITA:           return "Novita AI → novita.ai/settings";
            default:               return "Provider website";
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView  icon;
        final TextView   name;
        final TextView   badge;
        final TextView   status;
        final TextView   modelsCount;
        final MaterialSwitch toggle;
        final View       layoutApiKey;
        final EditText   inputApiKey;
        final ImageView  btnShowKey;
        final ImageView  btnCopyKey;
        final View       btnGetKey;
        final TextView   getKeyLabel;
        final com.google.android.material.button.MaterialButton btnRefresh;

        ViewHolder(@NonNull View v) {
            super(v);
            icon         = v.findViewById(R.id.provider_icon);
            name         = v.findViewById(R.id.provider_name);
            badge        = v.findViewById(R.id.provider_badge);
            status       = v.findViewById(R.id.provider_status);
            modelsCount  = v.findViewById(R.id.provider_models_count);
            toggle       = v.findViewById(R.id.provider_switch);
            layoutApiKey = v.findViewById(R.id.layout_api_key);
            inputApiKey  = v.findViewById(R.id.input_api_key);
            btnShowKey   = v.findViewById(R.id.btn_show_key);
            btnCopyKey   = v.findViewById(R.id.btn_copy_key);
            btnGetKey    = v.findViewById(R.id.btn_get_key);
            getKeyLabel  = v.findViewById(R.id.get_key_label);
            btnRefresh   = v.findViewById(R.id.btn_refresh);
        }
    }
}
