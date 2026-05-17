package pro.sketchware.ai.utils;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import pro.sketchware.ai.models.AiProvider;

/**
 * Checks whether an AI provider is reachable with a lightweight probe request.
 *
 * <p>The check is fire-and-forget: it is only useful as a UI hint (e.g. a status
 * dot on the provider selector). It MUST NOT be called for every message or on every
 * model list entry — that would waste rate-limit quota.
 *
 * <p>Usage:
 * <pre>
 *   ModelStatusChecker.checkStatusAsync(AiProvider.OPENAI, apiKey,
 *       status -> runOnUiThread(() -> updateStatusDot(status)));
 * </pre>
 */
public class ModelStatusChecker {

    public enum Status { ONLINE, OFFLINE, RATE_LIMITED, CHECKING }

    /** Callback invoked on the main thread when the status is resolved. */
    public interface StatusCallback {
        void onStatusResolved(Status status);
    }

    private static final int PROBE_TIMEOUT_SECONDS = 8;

    // Shared lightweight client for probes (separate from the streaming client)
    private static final OkHttpClient PROBE_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * Returns true if the provider is ready to receive requests (API key present if required).
     * This is a synchronous, offline check — no network access.
     */
    public static boolean isProviderReady(AiProvider provider, String apiKey) {
        if (!provider.requiresApiKey()) return true;
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Probes the provider with a HEAD request to its models endpoint and
     * reports the result via {@code callback} on the main thread.
     *
     * <p>Only call this when displaying provider selection UI — do NOT call
     * it per-message or per-model-list-item.
     *
     * @param provider the provider to probe
     * @param apiKey   the API key (may be null for no-auth providers)
     * @param callback receives the resolved Status on the main thread
     */
    public static void checkStatusAsync(AiProvider provider, String apiKey, StatusCallback callback) {
        if (!isProviderReady(provider, apiKey)) {
            post(callback, Status.OFFLINE);
            return;
        }

        String url = provider.getBaseUrl() + provider.getModelsEndpoint();

        // Some providers use non-HTTPS localhost — keep as-is
        Request.Builder builder = new Request.Builder().url(url).head();

        if (provider.requiresApiKey() && apiKey != null && !apiKey.isEmpty()) {
            if (provider == AiProvider.ANTHROPIC) {
                builder.header("x-api-key", apiKey)
                       .header("anthropic-version", "2023-06-01");
            } else {
                builder.header("Authorization", "Bearer " + apiKey);
            }
        }

        PROBE_CLIENT.newCall(builder.build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                post(callback, Status.OFFLINE);
            }
            @Override public void onResponse(Call call, Response response) {
                response.close();
                int code = response.code();
                Status status;
                if (code == 429) {
                    status = Status.RATE_LIMITED;
                } else if (code >= 200 && code < 500) {
                    // 2xx = OK, 401/403 = reachable but wrong key — still "online" from network perspective
                    status = Status.ONLINE;
                } else {
                    status = Status.OFFLINE;
                }
                post(callback, status);
            }
        });
    }

    /**
     * Synchronous version — DO NOT call from the main thread.
     *
     * @deprecated Prefer {@link #checkStatusAsync} to avoid blocking the calling thread.
     */
    @Deprecated
    public static Status checkStatus(AiProvider provider, String apiKey) {
        if (!isProviderReady(provider, apiKey)) return Status.OFFLINE;
        String url = provider.getBaseUrl() + provider.getModelsEndpoint();
        Request.Builder builder = new Request.Builder().url(url).head();
        if (provider.requiresApiKey() && apiKey != null) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try (Response r = PROBE_CLIENT.newCall(builder.build()).execute()) {
            int code = r.code();
            if (code == 429)            return Status.RATE_LIMITED;
            if (code >= 200 && code < 500) return Status.ONLINE;
            return Status.OFFLINE;
        } catch (IOException e) {
            return Status.OFFLINE;
        }
    }

    private static void post(StatusCallback callback, Status status) {
        MAIN_HANDLER.post(() -> callback.onStatusResolved(status));
    }
}
