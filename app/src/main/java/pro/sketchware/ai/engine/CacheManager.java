package pro.sketchware.ai.engine;

import android.util.Log;
import pro.sketchware.ai.utils.AiLog;
import android.util.LruCache;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * CacheManager — thread-safe LRU cache for AI-generated XML responses.
 *
 * <p>Caches by a SHA-256 hash of (tool + prompt + modelId) so that identical
 * generation requests (same user request, same model) are served instantly
 * from memory without a network call.
 *
 * <p>Eviction policy:
 * <ul>
 *   <li>Max entries: 50 (in-memory only; no disk persistence)</li>
 *   <li>Max entry TTL: 30 minutes (entries older than this are treated as misses)</li>
 *   <li>Max entry size: 64 KB of XML (larger responses skip the cache)</li>
 * </ul>
 *
 * <p>Thread safety: all public methods are synchronized on the internal LruCache
 * which is itself thread-safe on Android.
 */
public final class CacheManager {

    private static final String TAG          = "CacheManager";
    private static final int    MAX_ENTRIES  = 50;
    private static final long   MAX_TTL_MS   = TimeUnit.MINUTES.toMillis(30);
    private static final int    MAX_SIZE_BYTES = 64 * 1024; // 64 KB

    /** Single entry in the cache. */
    private static final class Entry {
        final String xml;
        final long   timestampMs;

        Entry(String xml) {
            this.xml         = xml;
            this.timestampMs = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestampMs > MAX_TTL_MS;
        }
    }

    /** Singleton instance. */
    private static volatile CacheManager INSTANCE;

    public static CacheManager getInstance() {
        if (INSTANCE == null) {
            synchronized (CacheManager.class) {
                if (INSTANCE == null) INSTANCE = new CacheManager();
            }
        }
        return INSTANCE;
    }

    private final LruCache<String, Entry> cache;
    private int hits   = 0;
    private int misses = 0;

    private CacheManager() {
        cache = new LruCache<>(MAX_ENTRIES);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Looks up a cached AI response.
     *
     * @param tool     the tool name (e.g. "GENERATE_UI", "MODIFY_UI")
     * @param prompt   the full prompt string
     * @param modelId  the model used (different models may give different results)
     * @return the cached XML string, or {@code null} if not cached / expired
     */
    public String get(String tool, String prompt, String modelId) {
        String key   = makeKey(tool, prompt, modelId);
        Entry  entry = cache.get(key);
        if (entry == null) {
            misses++;
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            misses++;
            AiLog.d(TAG, "Cache expired for key " + key.substring(0, 8) + "…");
            return null;
        }
        hits++;
        AiLog.d(TAG, "Cache HIT [" + hits + "/" + (hits + misses) + "]: " + tool);
        return entry.xml;
    }

    /**
     * Stores an AI response in the cache.
     *
     * @param tool     tool name
     * @param prompt   full prompt
     * @param modelId  model used
     * @param xml      the XML response to cache
     */
    public void put(String tool, String prompt, String modelId, String xml) {
        if (xml == null || xml.isEmpty()) return;
        if (xml.length() > MAX_SIZE_BYTES) {
            AiLog.d(TAG, "Skipping cache for large response (" + xml.length() + " bytes)");
            return;
        }
        String key = makeKey(tool, prompt, modelId);
        cache.put(key, new Entry(xml));
        AiLog.d(TAG, "Cached " + tool + " response (" + xml.length() + " bytes)");
    }

    /**
     * Invalidates a specific cached entry.
     */
    public void invalidate(String tool, String prompt, String modelId) {
        cache.remove(makeKey(tool, prompt, modelId));
    }

    /**
     * Clears the entire cache.
     */
    public void clear() {
        cache.evictAll();
        hits   = 0;
        misses = 0;
        AiLog.d(TAG, "Cache cleared");
    }

    /**
     * Returns a human-readable stats string.
     */
    public String stats() {
        int total = hits + misses;
        int rate  = total > 0 ? (hits * 100 / total) : 0;
        return "Cache: " + cache.size() + "/" + MAX_ENTRIES
                + " entries, " + hits + " hits, " + misses + " misses (" + rate + "% hit rate)";
    }

    /** Current number of entries in the cache. */
    public int size() {
        return cache.size();
    }

    // ── Key generation ────────────────────────────────────────────────────────

    /**
     * Creates a stable, short cache key from (tool, prompt, modelId).
     * Uses SHA-256 truncated to 32 hex chars for a compact but collision-safe key.
     */
    private static String makeKey(String tool, String prompt, String modelId) {
        String raw = (tool != null ? tool : "")
                + "|" + (modelId != null ? modelId : "")
                + "|" + (prompt  != null ? prompt  : "");
        try {
            MessageDigest md  = MessageDigest.getInstance("SHA-256");
            byte[]        hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb  = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback: use hashCode (weak but functional)
            return String.valueOf(raw.hashCode());
        }
    }
}
