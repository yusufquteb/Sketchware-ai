package pro.sketchware.utility;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * Persists the last-opened Logic Editor position to SharedPreferences so users
 * can resume editing after a process kill or app crash.
 *
 * Usage in LogicEditorActivity:
 *   EditorStateManager.save(this, scId, javaName, eventKey);   // in onCreate / onResume
 *   EditorStateManager.clear(this);                             // in finish()
 */
public final class EditorStateManager {

    private static final String PREFS_NAME    = "editor_state";
    private static final String KEY_SC_ID     = "sc_id";
    private static final String KEY_JAVA_NAME = "java_name";
    private static final String KEY_EVENT_KEY = "event_key";
    private static final String KEY_TIMESTAMP = "timestamp";

    private static final long DEBOUNCE_MS = 1500;

    private static final Handler debouncer = new Handler(Looper.getMainLooper());
    private static Runnable pendingSave;

    private EditorStateManager() {}

    /** Immediately persist the current editor position. */
    public static void save(Context ctx, String scId, String javaName, String eventKey) {
        SharedPreferences.Editor ed = prefs(ctx).edit();
        ed.putString(KEY_SC_ID, scId);
        ed.putString(KEY_JAVA_NAME, javaName);
        ed.putString(KEY_EVENT_KEY, eventKey);
        ed.putLong(KEY_TIMESTAMP, System.currentTimeMillis());
        ed.apply();
    }

    /**
     * Schedule a save that fires {@value #DEBOUNCE_MS} ms after the last call.
     * Use inside fast-changing callbacks (e.g. block drag) to avoid redundant writes.
     */
    public static void scheduleSave(Context ctx, String scId, String javaName, String eventKey) {
        debouncer.removeCallbacks(pendingSave);
        pendingSave = () -> save(ctx, scId, javaName, eventKey);
        debouncer.postDelayed(pendingSave, DEBOUNCE_MS);
    }

    /** Returns the last saved state, or null if none. */
    public static EditorState restore(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String scId = p.getString(KEY_SC_ID, null);
        if (scId == null) return null;
        return new EditorState(
                scId,
                p.getString(KEY_JAVA_NAME, null),
                p.getString(KEY_EVENT_KEY, null),
                p.getLong(KEY_TIMESTAMP, 0)
        );
    }

    /** Returns the last saved state only if it belongs to the given project. */
    public static EditorState restoreForProject(Context ctx, String scId) {
        EditorState state = restore(ctx);
        return (state != null && scId.equals(state.scId)) ? state : null;
    }

    /** Clear saved state (call when user explicitly closes the editor). */
    public static void clear(Context ctx) {
        debouncer.removeCallbacks(pendingSave);
        prefs(ctx).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static final class EditorState {
        public final String scId;
        public final String javaName;   // activity java name, e.g. "MainActivity"
        public final String eventKey;   // e.g. "onClick_btn1"
        public final long   timestamp;

        EditorState(String scId, String javaName, String eventKey, long timestamp) {
            this.scId      = scId;
            this.javaName  = javaName;
            this.eventKey  = eventKey;
            this.timestamp = timestamp;
        }
    }
}
