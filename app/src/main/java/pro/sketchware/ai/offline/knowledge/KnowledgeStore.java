package pro.sketchware.ai.offline.knowledge;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Persistent, on-device knowledge base for the local (offline) model: project rules,
 * environment facts, and tool notes that must survive regardless of how long the chat
 * history has grown or been trimmed.
 *
 * <p><b>Why this exists:</b> {@link pro.sketchware.ai.offline.LocalModelProvider} hard-caps
 * the prompt at a small, fixed KV-cache size (4096 tokens as of this writing — see that
 * class's javadoc) and trims the oldest chat messages first once history no longer fits.
 * A rule stated early in a long conversation (e.g. "always use Kotlin, not Java") is exactly
 * what gets trimmed away first, so the model silently stops following it. Storing such rules
 * here — outside the chat history entirely — means they never depend on how much of the
 * conversation is still in the trimming window.
 *
 * <p><b>Design constraints (deliberately minimal):</b>
 * <ul>
 *   <li>No embeddings / vector search / extra native dependency — this targets a ~4096-token
 *       local model on a phone, where the retrieval step itself must be cheap. Android's
 *       built-in SQLite FTS4 virtual table gives keyword-relevance ranking for free.</li>
 *   <li>Every retrieval is token-budgeted by the caller ({@code buildKnowledgeBlock} in
 *       {@code LocalModelProvider}) using the same {@code TokenBudgetChecker} char-count
 *       heuristic already used for the system prompt and tool block, so this can never by
 *       itself blow the hard KV-cache ceiling — it competes for space the same way those do.</li>
 *   <li>{@link Priority#CRITICAL} entries (e.g. core project rules) are always included,
 *       independent of the user's current message — they do not need to "match" anything to
 *       be retrieved, which is what actually solves the forgetting problem. {@link
 *       Priority#NORMAL} entries are retrieved only when relevant to the current message,
 *       via FTS4 keyword match, and compete for the small remaining slice of the budget.</li>
 * </ul>
 */
public class KnowledgeStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "offline_knowledge.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_ENTRIES = "knowledge_entries";
    private static final String TABLE_FTS = "knowledge_fts";

    public enum Category {
        RULE,        // explicit user-stated project rules ("always use Kotlin")
        ENV,         // environment facts (JDK version, package name, min SDK, ...)
        TOOL,        // notes about which tools/APIs are available and how to use them
        PREFERENCE   // softer stylistic preferences ("prefer Material3 components")
    }

    /** CRITICAL entries are injected into every prompt, unconditionally. NORMAL entries are
     *  retrieved only when they match the current message via FTS4. */
    public enum Priority { CRITICAL, NORMAL }

    public static final class Entry {
        public final long id;
        public final Category category;
        public final String title;
        public final String content;
        public final String keywords;
        public final Priority priority;
        public final long updatedAt;

        Entry(long id, Category category, String title, String content, String keywords,
              Priority priority, long updatedAt) {
            this.id = id;
            this.category = category;
            this.title = title;
            this.content = content;
            this.keywords = keywords;
            this.priority = priority;
            this.updatedAt = updatedAt;
        }
    }

    public KnowledgeStore(@NonNull Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_ENTRIES + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "category TEXT NOT NULL, "
                + "title TEXT NOT NULL, "
                + "content TEXT NOT NULL, "
                + "keywords TEXT NOT NULL DEFAULT '', "
                + "priority TEXT NOT NULL DEFAULT 'NORMAL', "
                + "updated_at INTEGER NOT NULL)");

        // FTS4 ships with Android's built-in SQLite — no extra dependency needed. Content is
        // duplicated into this virtual table (not "content=" external-content mode) to keep
        // insert/delete logic simple; the dataset here is small (tens to low hundreds of rows)
        // so the duplication cost is negligible.
        db.execSQL("CREATE VIRTUAL TABLE " + TABLE_FTS + " USING fts4(title, content, keywords)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ENTRIES);
        onCreate(db);
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    /**
     * Inserts a new entry, or — if an entry with the same category+title already exists —
     * replaces its content (so re-saving "language preference" updates it in place instead
     * of accumulating duplicates that would each compete for retrieval budget).
     */
    public long upsert(@NonNull Category category, @NonNull String title, @NonNull String content,
                        @NonNull String keywords, @NonNull Priority priority) {
        SQLiteDatabase db = getWritableDatabase();
        long existingId = findIdByCategoryAndTitle(db, category, title);

        ContentValues values = new ContentValues();
        values.put("category", category.name());
        values.put("title", title);
        values.put("content", content);
        values.put("keywords", keywords);
        values.put("priority", priority.name());
        values.put("updated_at", System.currentTimeMillis());

        long id;
        if (existingId >= 0) {
            db.update(TABLE_ENTRIES, values, "id = ?", new String[]{String.valueOf(existingId)});
            id = existingId;
            db.delete(TABLE_FTS, "rowid = ?", new String[]{String.valueOf(existingId)});
        } else {
            id = db.insert(TABLE_ENTRIES, null, values);
        }

        ContentValues ftsValues = new ContentValues();
        ftsValues.put("rowid", id);
        ftsValues.put("title", title);
        ftsValues.put("content", content);
        ftsValues.put("keywords", keywords);
        db.insertWithOnConflict(TABLE_FTS, null, ftsValues, SQLiteDatabase.CONFLICT_REPLACE);

        return id;
    }

    private long findIdByCategoryAndTitle(SQLiteDatabase db, Category category, String title) {
        try (Cursor c = db.query(TABLE_ENTRIES, new String[]{"id"},
                "category = ? AND title = ?", new String[]{category.name(), title},
                null, null, null)) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return -1;
    }

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_ENTRIES, "id = ?", new String[]{String.valueOf(id)});
        db.delete(TABLE_FTS, "rowid = ?", new String[]{String.valueOf(id)});
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    /** Every CRITICAL entry — always injected regardless of the current message. Kept small
     *  by design (core rules only); the caller still token-budgets these like everything else. */
    @NonNull
    public List<Entry> getCriticalEntries() {
        List<Entry> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_ENTRIES,
                new String[]{"id", "category", "title", "content", "keywords", "priority", "updated_at"},
                "priority = ?", new String[]{Priority.CRITICAL.name()},
                null, null, "updated_at DESC")) {
            while (c.moveToNext()) result.add(readEntry(c));
        }
        return result;
    }

    /**
     * FTS4 keyword search over NORMAL-priority entries relevant to {@code userMessage},
     * ranked by match quality, capped at {@code limit} results. Returns an empty list (not
     * an error) if nothing matches, if the message has no usable keywords, or on any FTS
     * query failure — retrieval is always a best-effort enhancement, never a hard requirement
     * for the model to keep working.
     */
    @NonNull
    public List<Entry> searchRelevant(@Nullable String userMessage, int limit) {
        List<Entry> result = new ArrayList<>();
        String ftsQuery = buildFtsQuery(userMessage);
        if (ftsQuery.isEmpty()) return result;

        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT e.id, e.category, e.title, e.content, e.keywords, e.priority, e.updated_at "
                + "FROM " + TABLE_ENTRIES + " e "
                + "JOIN " + TABLE_FTS + " f ON f.rowid = e.id "
                + "WHERE " + TABLE_FTS + " MATCH ? AND e.priority = ? "
                + "LIMIT ?";
        try (Cursor c = db.rawQuery(sql,
                new String[]{ftsQuery, Priority.NORMAL.name(), String.valueOf(Math.max(limit, 0))})) {
            while (c.moveToNext()) result.add(readEntry(c));
        } catch (Exception e) {
            // Malformed FTS query syntax (e.g. a lone quote in the user's message) — treat as
            // "no relevant entries" rather than surfacing a DB error mid-chat.
            return new ArrayList<>();
        }
        return result;
    }

    @NonNull
    public List<Entry> getAll() {
        List<Entry> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_ENTRIES,
                new String[]{"id", "category", "title", "content", "keywords", "priority", "updated_at"},
                null, null, null, null, "priority ASC, updated_at DESC")) {
            while (c.moveToNext()) result.add(readEntry(c));
        }
        return result;
    }

    private Entry readEntry(Cursor c) {
        return new Entry(
                c.getLong(0),
                Category.valueOf(c.getString(1)),
                c.getString(2),
                c.getString(3),
                c.getString(4),
                Priority.valueOf(c.getString(5)),
                c.getLong(6));
    }

    /**
     * Turns free-form user text into a safe FTS4 MATCH query: lowercases, strips FTS special
     * characters, drops short stopwords, and OR-joins the remaining tokens (OR rather than the
     * implicit AND so a message only needs to touch on *one* relevant keyword — e.g. just the
     * word "database" — to surface a matching rule, rather than needing every word in the
     * entry's keyword set to appear in the same message).
     */
    @NonNull
    private String buildFtsQuery(@Nullable String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) return "";
        String[] rawTokens = userMessage.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9\\u0600-\\u06FF\\s]", " ") // keep Latin, digits, Arabic; drop FTS-special chars
                .split("\\s+");

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String tok : rawTokens) {
            if (tok.length() < 3) continue; // drop very short/stopword-like tokens
            if (count > 0) sb.append(" OR ");
            sb.append(tok).append('*'); // prefix match so partial words still hit
            count++;
            if (count >= 12) break; // cap query size — this is a heuristic filter, not exhaustive parsing
        }
        return sb.toString();
    }
}
