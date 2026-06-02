package pro.sketchware.ai.engine.pipeline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite persistence layer for Pipelines and PipelineSteps.
 *
 * Tables:
 *   pipelines      — one row per pipeline
 *   pipeline_steps — one row per step
 */
public final class PipelineDatabase extends SQLiteOpenHelper {

    private static final String TAG     = "PipelineDB";
    private static final String DB_NAME = "ai_pipelines.db";
    private static final int    VERSION = 1;

    // ── Pipelines table ──────────────────────────────────────────────────────
    private static final String T_PIPELINES  = "pipelines";
    private static final String C_PIPE_ID    = "pipeline_id";
    private static final String C_PIPE_NAME  = "name";
    private static final String C_PIPE_SCID  = "sc_id";
    private static final String C_PIPE_WSID  = "workspace_id";
    private static final String C_PIPE_STATUS= "status";
    private static final String C_PIPE_STEP  = "current_step_index";
    private static final String C_PIPE_ERR   = "error_message";
    private static final String C_CREATED    = "created_at";
    private static final String C_STARTED    = "started_at";
    private static final String C_FINISHED   = "finished_at";

    // ── Steps table ──────────────────────────────────────────────────────────
    private static final String T_STEPS      = "pipeline_steps";
    private static final String C_STEP_PIPEID= "pipeline_id";
    private static final String C_STEP_IDX   = "step_index";
    private static final String C_STEP_TOOL  = "tool_name";
    private static final String C_STEP_DESC  = "description";
    private static final String C_STEP_STATUS= "status";
    private static final String C_STEP_OUT   = "output";
    private static final String C_STEP_ERR   = "error";
    private static final String C_STEP_START = "started_at";
    private static final String C_STEP_FIN   = "finished_at";
    private static final String C_STEP_SNAP  = "snapshot_id";

    private static volatile PipelineDatabase instance;

    public static PipelineDatabase getInstance(Context ctx) {
        if (instance == null) {
            synchronized (PipelineDatabase.class) {
                if (instance == null) {
                    instance = new PipelineDatabase(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private PipelineDatabase(Context ctx) {
        super(ctx, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_PIPELINES + " ("
                + C_PIPE_ID     + " TEXT PRIMARY KEY, "
                + C_PIPE_NAME   + " TEXT, "
                + C_PIPE_SCID   + " TEXT, "
                + C_PIPE_WSID   + " TEXT, "
                + C_PIPE_STATUS + " TEXT NOT NULL DEFAULT 'CREATED', "
                + C_PIPE_STEP   + " INTEGER DEFAULT 0, "
                + C_PIPE_ERR    + " TEXT, "
                + C_CREATED     + " INTEGER DEFAULT 0, "
                + C_STARTED     + " INTEGER DEFAULT 0, "
                + C_FINISHED    + " INTEGER DEFAULT 0"
                + ")");

        db.execSQL("CREATE TABLE " + T_STEPS + " ("
                + C_STEP_PIPEID + " TEXT NOT NULL, "
                + C_STEP_IDX    + " INTEGER NOT NULL, "
                + C_STEP_TOOL   + " TEXT, "
                + C_STEP_DESC   + " TEXT, "
                + C_STEP_STATUS + " TEXT NOT NULL DEFAULT 'PENDING', "
                + C_STEP_OUT    + " TEXT, "
                + C_STEP_ERR    + " TEXT, "
                + C_STEP_START  + " INTEGER DEFAULT 0, "
                + C_STEP_FIN    + " INTEGER DEFAULT 0, "
                + C_STEP_SNAP   + " TEXT, "
                + "PRIMARY KEY (" + C_STEP_PIPEID + ", " + C_STEP_IDX + "), "
                + "FOREIGN KEY (" + C_STEP_PIPEID + ") REFERENCES "
                + T_PIPELINES + "(" + C_PIPE_ID + ") ON DELETE CASCADE"
                + ")");

        db.execSQL("CREATE INDEX idx_steps_pipeline ON " + T_STEPS + "(" + C_STEP_PIPEID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + T_STEPS);
        db.execSQL("DROP TABLE IF EXISTS " + T_PIPELINES);
        onCreate(db);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ── Pipeline CRUD ────────────────────────────────────────────────────────

    public void insertPipeline(Pipeline p) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(C_PIPE_ID, p.pipelineId);
        v.put(C_PIPE_NAME, p.name);
        v.put(C_PIPE_SCID, p.scId);
        v.put(C_PIPE_WSID, p.workspaceId);
        v.put(C_PIPE_STATUS, p.status.name());
        v.put(C_PIPE_STEP, p.currentStepIndex);
        v.put(C_CREATED, p.createdAt);
        v.put(C_STARTED, p.startedAt);
        v.put(C_FINISHED, p.finishedAt);
        db.insertOrThrow(T_PIPELINES, null, v);
    }

    public void updatePipelineStatus(String pipelineId, PipelineStatus status,
                                     int currentStepIndex, String errorMessage) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(C_PIPE_STATUS, status.name());
        v.put(C_PIPE_STEP, currentStepIndex);
        v.put(C_PIPE_ERR, errorMessage);
        if (status == PipelineStatus.RUNNING && currentStepIndex == 0) {
            v.put(C_STARTED, System.currentTimeMillis());
        }
        if (status == PipelineStatus.COMPLETED || status == PipelineStatus.FAILED) {
            v.put(C_FINISHED, System.currentTimeMillis());
        }
        db.update(T_PIPELINES, v, C_PIPE_ID + "=?", new String[]{pipelineId});
    }

    public Pipeline getPipeline(String pipelineId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(T_PIPELINES, null,
                C_PIPE_ID + "=?", new String[]{pipelineId},
                null, null, null)) {
            if (!c.moveToFirst()) return null;
            return cursorToPipeline(c);
        } catch (Exception e) {
            Log.e(TAG, "getPipeline error: " + e.getMessage());
            return null;
        }
    }

    public List<Pipeline> getActivePipelines(String workspaceId) {
        SQLiteDatabase db = getReadableDatabase();
        List<Pipeline> result = new ArrayList<>();
        String sel = C_PIPE_WSID + "=? AND " + C_PIPE_STATUS + " NOT IN ('COMPLETED','FAILED')";
        try (Cursor c = db.query(T_PIPELINES, null, sel, new String[]{workspaceId},
                null, null, C_CREATED + " DESC")) {
            while (c.moveToNext()) result.add(cursorToPipeline(c));
        } catch (Exception e) {
            Log.e(TAG, "getActivePipelines error: " + e.getMessage());
        }
        return result;
    }

    // ── Step CRUD ────────────────────────────────────────────────────────────

    public void insertStep(PipelineStep step) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(C_STEP_PIPEID, step.pipelineId);
        v.put(C_STEP_IDX, step.stepIndex);
        v.put(C_STEP_TOOL, step.toolName);
        v.put(C_STEP_DESC, step.description);
        v.put(C_STEP_STATUS, step.status.name());
        db.insertOrThrow(T_STEPS, null, v);
    }

    public void updateStep(PipelineStep step) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(C_STEP_STATUS, step.status.name());
        v.put(C_STEP_OUT, step.output);
        v.put(C_STEP_ERR, step.error);
        v.put(C_STEP_START, step.startedAt);
        v.put(C_STEP_FIN, step.finishedAt);
        v.put(C_STEP_SNAP, step.snapshotId);
        db.update(T_STEPS, v,
                C_STEP_PIPEID + "=? AND " + C_STEP_IDX + "=?",
                new String[]{step.pipelineId, String.valueOf(step.stepIndex)});
    }

    public List<PipelineStep> getSteps(String pipelineId) {
        SQLiteDatabase db = getReadableDatabase();
        List<PipelineStep> result = new ArrayList<>();
        try (Cursor c = db.query(T_STEPS, null,
                C_STEP_PIPEID + "=?", new String[]{pipelineId},
                null, null, C_STEP_IDX + " ASC")) {
            while (c.moveToNext()) {
                PipelineStep s = new PipelineStep(
                        pipelineId,
                        c.getInt(c.getColumnIndexOrThrow(C_STEP_IDX)),
                        c.getString(c.getColumnIndexOrThrow(C_STEP_TOOL)),
                        c.getString(c.getColumnIndexOrThrow(C_STEP_DESC)));
                s.status     = StepStatus.valueOf(c.getString(c.getColumnIndexOrThrow(C_STEP_STATUS)));
                s.output     = c.getString(c.getColumnIndexOrThrow(C_STEP_OUT));
                s.error      = c.getString(c.getColumnIndexOrThrow(C_STEP_ERR));
                s.startedAt  = c.getLong(c.getColumnIndexOrThrow(C_STEP_START));
                s.finishedAt = c.getLong(c.getColumnIndexOrThrow(C_STEP_FIN));
                s.snapshotId = c.getString(c.getColumnIndexOrThrow(C_STEP_SNAP));
                result.add(s);
            }
        } catch (Exception e) {
            Log.e(TAG, "getSteps error: " + e.getMessage());
        }
        return result;
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Pipeline cursorToPipeline(Cursor c) {
        Pipeline p = new Pipeline(
                c.getString(c.getColumnIndexOrThrow(C_PIPE_ID)),
                c.getString(c.getColumnIndexOrThrow(C_PIPE_NAME)),
                c.getString(c.getColumnIndexOrThrow(C_PIPE_SCID)),
                c.getString(c.getColumnIndexOrThrow(C_PIPE_WSID)));
        p.status           = PipelineStatus.valueOf(c.getString(c.getColumnIndexOrThrow(C_PIPE_STATUS)));
        p.currentStepIndex = c.getInt(c.getColumnIndexOrThrow(C_PIPE_STEP));
        p.errorMessage     = c.getString(c.getColumnIndexOrThrow(C_PIPE_ERR));
        p.createdAt        = c.getLong(c.getColumnIndexOrThrow(C_CREATED));
        p.startedAt        = c.getLong(c.getColumnIndexOrThrow(C_STARTED));
        p.finishedAt       = c.getLong(c.getColumnIndexOrThrow(C_FINISHED));
        return p;
    }
}
