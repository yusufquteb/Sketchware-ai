package pro.sketchware.ai.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pro.sketchware.ai.models.AiProvider;
import pro.sketchware.ai.models.AiProviderModels;
import pro.sketchware.ai.storage.AiPreferences;

/**
 * Shared (provider, model) failover queue builder.
 *
 * <p>Extracted verbatim from {@link AgentExecutor}'s previously-private
 * {@code buildProviderModelQueue()} so both {@link AgentExecutor} and
 * {@link pro.sketchware.ai.orchestrator.AgentOrchestrator} can retry across
 * providers/models identically instead of the orchestrator throwing on the
 * very first failure (see CHANGES-this-session.md, "Orchestrator activation —
 * point 4").
 *
 * <p>Behavior is unchanged from the original: first exhausts all models of the
 * initial provider, then iterates every other enabled provider in group order
 * (free-no-key → free-with-api → paid), skipping providers without a
 * required API key.
 */
public final class ProviderFailoverQueue {

    private ProviderFailoverQueue() {}

    /** A (provider, modelId) pair used in the dynamic failover queue. */
    public static final class ProviderModelPair {
        public final AiProvider provider;
        public final String modelId;
        public ProviderModelPair(AiProvider p, String m) {
            provider = p; modelId = m;
        }
    }

    public static List<ProviderModelPair> build(
            AiProvider initialProvider,
            String initialModelId,
            AiPreferences prefs) {

        List<ProviderModelPair> queue = new ArrayList<>();
        Set<String> addedProviders = new HashSet<>();

        // Initial provider: selected model first, then remaining static models
        queue.add(new ProviderModelPair(initialProvider, initialModelId));
        addedProviders.add(initialProvider.name());
        for (String m : AiProviderModels.getStaticModels(initialProvider)) {
            if (!m.equals(initialModelId)) {
                queue.add(new ProviderModelPair(initialProvider, m));
            }
        }

        // Remaining enabled providers in group order: free-no-key → free-with-api → paid
        AiProvider.ProviderGroup[] groupOrder = {
            AiProvider.ProviderGroup.FREE_NO_API,
            AiProvider.ProviderGroup.FREE_WITH_API,
            AiProvider.ProviderGroup.PAID
        };
        for (AiProvider.ProviderGroup group : groupOrder) {
            for (AiProvider p : AiProvider.values()) {
                if (p.getGroup() != group) continue;
                if (addedProviders.contains(p.name())) continue;
                if (!prefs.isProviderEnabled(p)) continue;
                if (p.requiresApiKey() && !prefs.hasApiKey(p)) continue;

                addedProviders.add(p.name());

                String selected = prefs.getSelectedModel(p);
                List<String> providerModels = new ArrayList<>();
                if (selected != null && !selected.isEmpty()) {
                    providerModels.add(selected);
                }
                for (String m : AiProviderModels.getStaticModels(p)) {
                    if (!m.equals(selected)) providerModels.add(m);
                }
                if (providerModels.isEmpty()) {
                    String def = AiProviderModels.getDefaultModel(p);
                    if (!def.isEmpty()) providerModels.add(def);
                }
                for (String m : providerModels) {
                    queue.add(new ProviderModelPair(p, m));
                }
            }
        }
        return queue;
    }
}
