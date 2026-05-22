package pro.sketchware.ai.adapters;

import android.content.Context;
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
import pro.sketchware.ai.core.CircuitBreaker;
import pro.sketchware.ai.models.AiProvider;

/**
 * RecyclerView adapter for the AI Providers list.
 * Supports two item types: GROUP_HEADER and PROVIDER_CARD.
 * Providers are organized into three groups: Free (no API), Free (with API), Paid.
 */
public class AiProviderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER   = 0;
    private static final int TYPE_PROVIDER = 1;

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface ProviderCallback {
        void onToggle(AiProvider provider, boolean enabled);
        void onKeyChanged(AiProvider provider, String key);
        void onGetKey(AiProvider provider);
        void onRefresh(AiProvider provider);
    }

    // ── Item types ────────────────────────────────────────────────────────────

    public abstract static class ListItem {
        public abstract int getType();
    }

    public static class GroupHeader extends ListItem {
        public final String title;
        public final String subtitle;
        public final int    iconRes;
        public final boolean showDivider;

        public GroupHeader(String title, String subtitle, int iconRes, boolean showDivider) {
            this.title       = title;
            this.subtitle    = subtitle;
            this.iconRes     = iconRes;
            this.showDivider = showDivider;
        }

        @Override public int getType() { return TYPE_HEADER; }
    }

    public static class ProviderItem extends ListItem {
        public final ProviderState state;
        public ProviderItem(ProviderState state) { this.state = state; }
        @Override public int getType() { return TYPE_PROVIDER; }
    }

    /** Per-provider UI state */
    public static class ProviderState {
        public final AiProvider provider;
        public boolean enabled;
        public String  apiKey;
        public String  modelsCountText;
        public boolean keyVisible;

        public ProviderState(AiProvider provider, boolean enabled,
                             String apiKey, String modelsCountText) {
            this.provider        = provider;
            this.enabled         = enabled;
            this.apiKey          = apiKey != null ? apiKey : "";
            this.modelsCountText = modelsCountText != null ? modelsCountText : "";
            this.keyVisible      = false;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final List<ListItem>     items    = new ArrayList<>();
    private final ProviderCallback   callback;

    public AiProviderAdapter(ProviderCallback callback) {
        this.callback = callback;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    public void setStates(List<ProviderState> states) {
        items.clear();

        List<ProviderState> freeNoApi  = new ArrayList<>();
        List<ProviderState> freeApi    = new ArrayList<>();
        List<ProviderState> paid       = new ArrayList<>();

        for (ProviderState s : states) {
            switch (s.provider.getGroup()) {
                case FREE_NO_API:  freeNoApi.add(s);  break;
                case FREE_WITH_API: freeApi.add(s);   break;
                default:           paid.add(s);       break;
            }
        }

        if (!freeNoApi.isEmpty()) {
            items.add(new GroupHeader(
                    "Free — No API Key",
                    "Works immediately, zero setup required",
                    R.drawable.ic_mtrl_check,
                    false));
            for (ProviderState s : freeNoApi) items.add(new ProviderItem(s));
        }

        if (!freeApi.isEmpty()) {
            items.add(new GroupHeader(
                    "Free — API Key Required",
                    "Generous free tiers — get a key in minutes",
                    R.drawable.ic_mtrl_key,
                    !freeNoApi.isEmpty()));
            for (ProviderState s : freeApi) items.add(new ProviderItem(s));
        }

        if (!paid.isEmpty()) {
            items.add(new GroupHeader(
                    "Paid Providers",
                    "Premium models — pay-as-you-go pricing",
                    R.drawable.ic_mtrl_payment,
                    !freeNoApi.isEmpty() || !freeApi.isEmpty()));
            for (ProviderState s : paid) items.add(new ProviderItem(s));
        }

        notifyDataSetChanged();
    }

    public void setEnabled(AiProvider provider, boolean enabled) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof ProviderItem) {
                ProviderState s = ((ProviderItem) items.get(i)).state;
                if (s.provider == provider) {
                    s.enabled = enabled;
                    notifyItemChanged(i);
                    return;
                }
            }
        }
    }

    public void setModelCount(AiProvider provider, String text) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof ProviderItem) {
                ProviderState s = ((ProviderItem) items.get(i)).state;
                if (s.provider == provider) {
                    s.modelsCountText = text;
                    notifyItemChanged(i);
                    return;
                }
            }
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    @Override public int getItemCount() { return items.size(); }

    @Override public int getItemViewType(int pos) { return items.get(pos).getType(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inf.inflate(R.layout.item_provider_group_header, parent, false);
            return new HeaderViewHolder(v);
        }
        View v = inf.inflate(R.layout.item_ai_provider, parent, false);
        return new ProviderViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        if (holder instanceof HeaderViewHolder) {
            bindHeader((HeaderViewHolder) holder, (GroupHeader) items.get(pos));
        } else {
            bindProvider((ProviderViewHolder) holder, ((ProviderItem) items.get(pos)).state);
        }
    }

    // ── Header binding ────────────────────────────────────────────────────────

    private void bindHeader(HeaderViewHolder h, GroupHeader header) {
        h.title.setText(header.title);
        h.subtitle.setText(header.subtitle);
        h.icon.setImageResource(header.iconRes);
        h.divider.setVisibility(header.showDivider ? View.VISIBLE : View.GONE);

        // Count providers in this group
        int count = 0;
        boolean counting = false;
        for (ListItem item : items) {
            if (item == header) { counting = true; continue; }
            if (counting) {
                if (item instanceof GroupHeader) break;
                count++;
            }
        }
        h.count.setText(count + " providers");
    }

    // ── Provider binding ──────────────────────────────────────────────────────

    private void bindProvider(ProviderViewHolder h, ProviderState state) {
        AiProvider provider = state.provider;
        Context ctx = h.itemView.getContext();

        h.icon.setImageResource(getIconFor(provider));
        h.name.setText(provider.getDisplayName());

        // Badge
        if (provider.isUnlimited()) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText("∞ Unlimited");
        } else if (!provider.requiresApiKey()) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText("FREE");
        } else {
            h.badge.setVisibility(View.GONE);
        }

        // Status + Circuit Breaker health badge
        applyProviderStatus(h, ctx, state, provider);

        h.modelsCount.setText(state.modelsCountText);
        h.modelsCount.setVisibility(state.modelsCountText.isEmpty() ? View.GONE : View.VISIBLE);

        // Switch
        h.toggle.setOnCheckedChangeListener(null);
        h.toggle.setChecked(state.enabled);
        h.toggle.setOnCheckedChangeListener((btn, checked) -> {
            state.enabled = checked;
            applyProviderStatus(h, ctx, state, provider);
            h.layoutApiKey.setVisibility(checked ? View.VISIBLE : View.GONE);
            callback.onToggle(provider, checked);
        });

        // Expandable API key section
        h.layoutApiKey.setVisibility(state.enabled ? View.VISIBLE : View.GONE);

        if (!state.apiKey.isEmpty()) {
            h.inputApiKey.setText(state.apiKey);
        } else {
            h.inputApiKey.setText("");
        }
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

        h.btnCopyKey.setOnClickListener(v -> {
            String key = h.inputApiKey.getText().toString().trim();
            if (!key.isEmpty()) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("api_key", key));
                    com.google.android.material.snackbar.Snackbar
                            .make(h.itemView, "Key copied",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                            .show();
                }
            }
        });

        h.inputApiKey.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String key = h.inputApiKey.getText().toString().trim();
                state.apiKey = key;
                callback.onKeyChanged(provider, key);
            }
        });

        // Free providers — disable key input
        if (!provider.requiresApiKey()) {
            h.inputApiKey.setEnabled(false);
            h.inputApiKey.setHint("No API key required — free access");
            h.inputApiKey.setText("");
            h.btnShowKey.setVisibility(View.GONE);
            h.btnCopyKey.setVisibility(View.GONE);
            h.btnGetKey.setVisibility(View.GONE);
        } else {
            h.inputApiKey.setEnabled(true);
            h.inputApiKey.setHint("Enter API key");
            h.btnShowKey.setVisibility(View.VISIBLE);
            h.btnCopyKey.setVisibility(View.VISIBLE);
            h.getKeyLabel.setText("Get your API key from " + getKeySourceLabel(provider));
            h.btnGetKey.setVisibility(View.VISIBLE);
            h.btnGetKey.setOnClickListener(v -> callback.onGetKey(provider));
        }

        h.btnRefresh.setOnClickListener(v -> {
            String key = h.inputApiKey.getText().toString().trim();
            state.apiKey = key;
            callback.onKeyChanged(provider, key);
            callback.onRefresh(provider);
        });
    }

    // ── Status + Circuit Breaker badge ───────────────────────────────────────

    private static void applyProviderStatus(ProviderViewHolder h, Context ctx,
                                             ProviderState state, AiProvider provider) {
        if (!state.enabled) {
            h.status.setText("Disabled");
            h.status.setTextColor(
                    ctx.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary})
                       .getColor(0, 0xFF888888));
            return;
        }

        long cooldownMs = CircuitBreaker.getInstance().getRemainingCooldownMs(provider.name());
        if (cooldownMs > 0) {
            long secs = (cooldownMs + 999) / 1000; // round up
            h.status.setText("Circuit open — cooldown " + secs + "s");
            h.status.setTextColor(0xFFE53935); // red
        } else {
            int fails = CircuitBreaker.getInstance().getFailureCount(provider.name());
            if (fails > 0) {
                h.status.setText("Enabled · " + fails + " recent failure" + (fails == 1 ? "" : "s"));
                h.status.setTextColor(0xFFFF8F00); // amber
            } else {
                h.status.setText("Enabled");
                h.status.setTextColor(0xFF4CAF50); // green
            }
        }
    }

    // ── Icon & label mappings ─────────────────────────────────────────────────

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
            default:               return R.drawable.ic_mtrl_code;
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
            case MORPH:            return "Morph LLM → morphllm.com/dashboard/api-keys";
            default:               return "Provider website";
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final View     divider;
        final ImageView icon;
        final TextView  title;
        final TextView  count;
        final TextView  subtitle;

        HeaderViewHolder(@NonNull View v) {
            super(v);
            divider  = v.findViewById(R.id.group_divider);
            icon     = v.findViewById(R.id.group_icon);
            title    = v.findViewById(R.id.group_title);
            count    = v.findViewById(R.id.group_count);
            subtitle = v.findViewById(R.id.group_subtitle);
        }
    }

    static class ProviderViewHolder extends RecyclerView.ViewHolder {
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

        ProviderViewHolder(@NonNull View v) {
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
