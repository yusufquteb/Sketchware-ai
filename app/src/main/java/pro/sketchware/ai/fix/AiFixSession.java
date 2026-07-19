package pro.sketchware.ai.fix;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an AI compile-error fix session.
 *
 * When a build fails, AiFixSupport creates a session from the raw compile log,
 * resolves the target Java file and block event, and stores all context needed
 * for the AI agent to propose a fix.
 */
public class AiFixSession {

    public String sessionId;
    public String scId;
    public String rawLog;
    public String errorFileName;
    public int    errorLine  = -1;
    public String errorMessage;
    public String targetJavaName;
    public String targetJavaSource;
    public String targetId;
    public String targetEventName;
    public String targetEventText;
    public long   createdAt;

    /** Returns true if the session has enough context to attempt an AI fix. */
    public boolean hasResolvedTarget() {
        return notBlank(targetJavaName)
                && notBlank(targetId)
                && notBlank(targetEventName);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("sessionId",       sessionId);
        obj.put("scId",            scId);
        obj.put("rawLog",          rawLog);
        obj.put("errorFileName",   errorFileName);
        obj.put("errorLine",       errorLine);
        obj.put("errorMessage",    errorMessage);
        obj.put("targetJavaName",  targetJavaName);
        obj.put("targetJavaSource",targetJavaSource);
        obj.put("targetId",        targetId);
        obj.put("targetEventName", targetEventName);
        obj.put("targetEventText", targetEventText);
        obj.put("createdAt",       createdAt);
        return obj;
    }

    public static AiFixSession fromJson(JSONObject obj) {
        AiFixSession s = new AiFixSession();
        s.sessionId        = obj.optString("sessionId",       "");
        s.scId             = obj.optString("scId",            "");
        s.rawLog           = obj.optString("rawLog",          "");
        s.errorFileName    = obj.optString("errorFileName",   "");
        s.errorLine        = obj.optInt   ("errorLine",       -1);
        s.errorMessage     = obj.optString("errorMessage",    "");
        s.targetJavaName   = obj.optString("targetJavaName",  "");
        s.targetJavaSource = obj.optString("targetJavaSource","");
        s.targetId         = obj.optString("targetId",        "");
        s.targetEventName  = obj.optString("targetEventName", "");
        s.targetEventText  = obj.optString("targetEventText", "");
        s.createdAt        = obj.optLong  ("createdAt",        0L);
        return s;
    }

    private static boolean notBlank(String v) {
        return v != null && !v.trim().isEmpty();
    }
}
