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
 * Supported: GEMINI, OPENAI, ANTHROPIC, DEEPSEEK, XAI_GROK, GROQ, NVIDIA,
 *            OPENROUTER, DEEPINFRA, TOGETHER, HUGGINGFACE, CEREBRAS,
 *            GOOGLE_AI_STUDIO, SAMBANOVA, MISTRAL, COHERE, HYPERBOLIC,
 *            KLUSTER, OVH, CLOUDFLARE, GITHUB_MODELS, LAMBDA, SCALEWAY,
 *            FIREWORKS, NOVITA, CHUTES (AirForce AI), MORPH.
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
        switch (provider) {
            case GEMINI:           return new GeminiApiClient(apiKey);
            case OPENAI:           return new OpenAiApiClient(apiKey);
            case ANTHROPIC:        return new AnthropicApiClient(apiKey);
            case DEEPSEEK:         return new DeepSeekApiClient(apiKey);
            case XAI_GROK:         return new XaiGrokApiClient(apiKey);
            case NVIDIA:           return new NvidiaApiClient(apiKey);
            case OPENROUTER:       return new OpenRouterApiClient(apiKey);
            case DEEPINFRA:        return new DeepInfraApiClient(apiKey);
            case GROQ:             return new GroqApiClient(apiKey);
            case TOGETHER:         return new TogetherApiClient(apiKey);
            case HUGGINGFACE:      return new HuggingFaceApiClient(apiKey);
            case CEREBRAS:         return new CerebrasApiClient(apiKey);
            case GOOGLE_AI_STUDIO: return new GoogleAiStudioApiClient(apiKey);
            case SAMBANOVA:        return new SambaNovaApiClient(apiKey);
            case MISTRAL:          return new MistralApiClient(apiKey);
            case COHERE:           return new CohereApiClient(apiKey);
            case HYPERBOLIC:       return new HyperbolicApiClient(apiKey);
            case KLUSTER:          return new KlusterApiClient(apiKey);
            case OVH:              return new OvhApiClient(apiKey);
            case CLOUDFLARE:       return new CloudflareApiClient(apiKey);
            case GITHUB_MODELS:    return new GitHubModelsApiClient(apiKey);
            case LAMBDA:           return new LambdaApiClient(apiKey);
            case SCALEWAY:         return new ScalewayApiClient(apiKey);
            case FIREWORKS:        return new FireworksApiClient(apiKey);
            case NOVITA:           return new NovitaApiClient(apiKey);
            case CHUTES:           return new ChutesApiClient(apiKey);
            case MORPH:            return new MorphApiClient(apiKey);
            default:
                Log.w(TAG, "No client implementation for provider: " + provider
                        + " — returning null");
                return null;
        }
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
            case COHERE:           return "\u2705 Stable \u2014 20 req/min, 1,000 req/month on free tier";
            case HYPERBOLIC:       return "\u2705 Stable \u2014 $1 free credit, fast Llama & DeepSeek inference";
            case KLUSTER:          return "\u2705 Stable \u2014 affordable batch & realtime inference";
            case OVH:              return "\u26A0\uFE0F 12 req/min limit \u2014 European provider, good privacy";
            case CLOUDFLARE:       return "\u2705 Stable \u2014 10,000 neurons/day free, Llama 3.3 & Mistral";
            case GITHUB_MODELS:    return "\u26A0\uFE0F Rate limits vary \u2014 use GitHub personal token";
            case LAMBDA:           return "\u2705 Stable \u2014 Llama 3.3 70B, Hermes, Qwen and more";
            case SCALEWAY:         return "\u2705 Stable \u2014 1M free tokens, European cloud";
            case FIREWORKS:        return "\u2705 Stable \u2014 $1 free credit, fast Llama 4 & DeepSeek";
            case NOVITA:           return "\u2705 Stable \u2014 $0.50 free for 1 year, Llama 4 & DeepSeek R1";
            case CHUTES:           return "\u2705 Free \u2014 AirForce AI, no API key needed. GPT-4o, Claude, Gemini, Llama 4, DeepSeek & more";
            case MORPH:            return "\u2705 Stable \u2014 morph-v3-fast for precise code & XML layout editing";
            case NVIDIA:           return "\u26A0\uFE0F May have rate limits \u2014 use meta/llama-3.3-70b-instruct";
            case OPENROUTER:       return "\u26A0\uFE0F Quality varies by sub-model \u2014 prefix model with provider/";
            case DEEPINFRA:        return "\u26A0\uFE0F Supports Gemma 2/3 \u2014 check model ID matches exactly";
            case HUGGINGFACE:      return "\u26A0\uFE0F Free tier has rate limits \u2014 set model to specific HF model ID";
            default:               return "Unknown provider";
        }
    }

    public static boolean requiresApiKey(AiProvider provider) {
        return provider != AiProvider.CHUTES;
    }
}
