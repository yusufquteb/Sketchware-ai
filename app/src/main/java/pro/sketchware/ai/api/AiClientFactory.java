package pro.sketchware.ai.api;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import pro.sketchware.ai.core.ProviderCapabilities;
import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * Centralized factory for creating AI API clients.
 * Supported: POLLINATIONS, GEMINI, OPENAI, ANTHROPIC, DEEPSEEK, XAI_GROK, GROQ, NVIDIA,
 *            OPENROUTER, DEEPINFRA, TOGETHER, HUGGINGFACE, CEREBRAS,
 *            GOOGLE_AI_STUDIO, SAMBANOVA, MISTRAL, HYPERBOLIC,
 *            KLUSTER, LAMBDA, SCALEWAY,
 *            FIREWORKS, NOVITA, MORPH, LOCAL_LLM (on-device, no network).
 */
public final class AiClientFactory {

    private static final String TAG = "AiClientFactory";

    private AiClientFactory() {}

    /**
     * Creates an API client for the given provider.
     *
     * @return the client, or {@code null} only if the provider enum value has no
     *         corresponding client class (should never happen in production).
     */
    @Nullable
    public static AiApiClient createClient(Context context, AiProvider provider, String apiKey) {
        if (provider == null) {
            Log.e(TAG, "createClient called with null provider");
            return null;
        }
        AiPreferences preferences = AiPreferences.getInstance(context);
        AiApiClient createdClient;
        switch (provider) {
            case POLLINATIONS:     createdClient = new PollinationsApiClient(apiKey); break;
            case GEMINI:           createdClient = new GeminiApiClient(apiKey); break;
            case OPENAI:           createdClient = new OpenAiApiClient(apiKey); break;
            case ANTHROPIC:        createdClient = new AnthropicApiClient(apiKey); break;
            case DEEPSEEK:         createdClient = new DeepSeekApiClient(apiKey); break;
            case XAI_GROK:         createdClient = new XaiGrokApiClient(apiKey); break;
            case NVIDIA:           createdClient = new NvidiaApiClient(apiKey); break;
            case OPENROUTER:       createdClient = new OpenRouterApiClient(apiKey); break;
            case DEEPINFRA:        createdClient = new DeepInfraApiClient(apiKey); break;
            case GROQ:             createdClient = new GroqApiClient(apiKey); break;
            case TOGETHER:         createdClient = new TogetherApiClient(apiKey); break;
            case HUGGINGFACE:      createdClient = new HuggingFaceApiClient(apiKey); break;
            case CEREBRAS:         createdClient = new CerebrasApiClient(apiKey); break;
            case GOOGLE_AI_STUDIO: createdClient = new GoogleAiStudioApiClient(apiKey); break;
            case SAMBANOVA:        createdClient = new SambaNovaApiClient(apiKey); break;
            case MISTRAL:          createdClient = new MistralApiClient(apiKey); break;
            case HYPERBOLIC:       createdClient = new HyperbolicApiClient(apiKey); break;
            case KLUSTER:          createdClient = new KlusterApiClient(apiKey); break;
            case LAMBDA:           createdClient = new LambdaApiClient(apiKey); break;
            case SCALEWAY:         createdClient = new ScalewayApiClient(apiKey); break;
            case FIREWORKS:        createdClient = new FireworksApiClient(apiKey); break;
            case NOVITA:           createdClient = new NovitaApiClient(apiKey); break;
            case MORPH:            createdClient = new MorphApiClient(apiKey); break;
            case LOCAL_LLM:        createdClient = new pro.sketchware.ai.offline.LocalModelProvider(context); break;
            default:
                Log.w(TAG, "No client implementation for provider: " + provider
                        + " — returning null");
                return null;
        }
        // ✅ FIX (previously a no-op "fake settings" bug): the "AI Performance Profiles"
        // screen in AiSettingsActivity wrote ai_temperature/ai_max_tokens to SharedPreferences,
        // but nothing ever read them back. `preferences` was fetched above and left unused.
        // Every client now receives the user's configured values (0 = provider default).
        createdClient.setGenerationParams(preferences.getTemperature(), preferences.getMaxTokens());
        return createdClient;
    }

    /**
     * Returns the capability profile for the given provider.
     * Never returns null.
     */
    @NonNull
    public static ProviderCapabilities getCapabilities(@NonNull AiProvider provider) {
        return ProviderCapabilities.of(provider);
    }

    public static String getCompatibilityNote(AiProvider provider) {
        switch (provider) {
            case POLLINATIONS:     return "\u2705 Free \u2014 no API key needed. ~1 req/min anonymous. Use \u2018openai\u2019 or \u2018qwen-coder\u2019";
            case GEMINI:           return "\u2705 Stable \u2014 use gemini-2.0-flash or gemini-2.5-pro";
            case OPENAI:           return "\u2705 Stable \u2014 use gpt-4o-mini for best cost/performance";
            case ANTHROPIC:        return "\u2705 Stable \u2014 prompt caching enabled (saves ~90% tokens)";
            case GROQ:             return "\u2705 Stable \u221e \u2014 fastest inference, use llama-3.3-70b-versatile";
            case DEEPSEEK:         return "\u2705 Stable \u2014 deepseek-chat is very cost-effective";
            case XAI_GROK:         return "\u2705 Stable \u2014 use grok-3-mini for coding tasks";
            case CEREBRAS:         return "\u2705 Stable \u2014 extremely fast, use llama3.1-8b for quick tasks";
            case TOGETHER:         return "\u2705 Stable \u2014 supports Gemma 3 27B, Llama 3.3, DeepSeek R1";
            case GOOGLE_AI_STUDIO: return "\u2705 Stable \u2014 Gemma 3 models free via aistudio.google.com key";
            case SAMBANOVA:        return "\u2705 Stable \u2014 Gemma 3/2, Llama 4, DeepSeek R1 free at cloud.sambanova.ai";
            case MISTRAL:          return "\u2705 Stable \u2014 free Experiment plan, phone verification required";
            case HYPERBOLIC:       return "\u2705 Stable \u2014 $1 free credit, fast Llama & DeepSeek inference";
            case KLUSTER:          return "\u2705 Stable \u2014 affordable batch & realtime inference";
            case LAMBDA:           return "\u2705 Stable \u2014 Llama 3.3 70B, Hermes, Qwen and more";
            case SCALEWAY:         return "\u2705 Stable \u2014 1M free tokens, European cloud";
            case FIREWORKS:        return "\u2705 Stable \u2014 $1 free credit, fast Llama 4 & DeepSeek";
            case NOVITA:           return "\u2705 Stable \u2014 $0.50 free for 1 year, Llama 4 & DeepSeek R1";
            case MORPH:            return "\u2705 Stable \u2014 morph-v3-fast for precise code & XML layout editing";
            case LOCAL_LLM:        return "\ud83d\udcf4 Offline \u2014 requires downloading a model once in Settings before use";
            case NVIDIA:           return "\u26A0\uFE0F May have rate limits \u2014 use meta/llama-3.3-70b-instruct";
            case OPENROUTER:       return "\u26A0\uFE0F Quality varies by sub-model \u2014 prefix model with provider/";
            case DEEPINFRA:        return "\u26A0\uFE0F Supports Gemma 2/3 \u2014 check model ID matches exactly";
            case HUGGINGFACE:      return "\u26A0\uFE0F Free tier has rate limits \u2014 set model to specific HF model ID";
            default:               return "Unknown provider";
        }
    }

    public static boolean requiresApiKey(AiProvider provider) {
        return provider != null && provider.requiresApiKey();
    }
}
