package pro.sketchware.ai.fix;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Persists and retrieves AiFixSession objects via SharedPreferences.
 *
 * Sessions survive across activity restarts so the fix workflow can continue
 * after the user navigates away and returns.
 */
public class AiFixSessionStore {

    private static final String PREFS_NAME = "ai_fix_sessions";

    private final SharedPreferences prefs;

    public AiFixSessionStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Saves a session and returns the session ID. */
    public String save(AiFixSession session) {
        if (session.sessionId == null || session.sessionId.isEmpty()) {
            session.sessionId = UUID.randomUUID().toString();
        }
        session.createdAt = System.currentTimeMillis();
        try {
            prefs.edit()
                 .putString(session.sessionId, session.toJson().toString())
                 .apply();
        } catch (Exception e) {
            android.util.Log.e("AiFixSessionStore", "Failed to save session", e);
        }
        return session.sessionId;
    }

    /** Retrieves a session by ID, or null if not found. */
    public AiFixSession get(String sessionId) {
        String json = prefs.getString(sessionId, null);
        if (json == null) return null;
        try {
            return AiFixSession.fromJson(new JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns the most recently saved session for a project, or null. */
    public AiFixSession getLatestForProject(String scId) {
        AiFixSession latest = null;
        for (String key : prefs.getAll().keySet()) {
            String json = prefs.getString(key, null);
            if (json == null) continue;
            try {
                AiFixSession s = AiFixSession.fromJson(new JSONObject(json));
                if (scId.equals(s.scId)) {
                    if (latest == null || s.createdAt > latest.createdAt) {
                        latest = s;
                    }
                }
            } catch (Exception ignored) {}
        }
        return latest;
    }

    /** Deletes a session by ID. */
    public void delete(String sessionId) {
        prefs.edit().remove(sessionId).apply();
    }

    /** Clears all sessions older than 24 hours. */
    public void pruneOld() {
        long cutoff = System.currentTimeMillis() - 24L * 3600 * 1000;
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            String json = prefs.getString(key, null);
            if (json == null) continue;
            try {
                AiFixSession s = AiFixSession.fromJson(new JSONObject(json));
                if (s.createdAt < cutoff) {
                    editor.remove(key);
                }
            } catch (Exception ignored) {}
        }
        editor.apply();
    }
}
