package pro.sketchware.ai.engine.risk;

/**
 * User-configurable approval strictness.
 *
 * CONSERVATIVE → ask for approval on every MEDIUM or CRITICAL operation.
 * BALANCED     → ask for MEDIUM and CRITICAL (default).
 * AUTONOMOUS   → ask only for CRITICAL; trusted session for MEDIUM (15-min window).
 */
public enum ApprovalMode {
    CONSERVATIVE,
    BALANCED,
    AUTONOMOUS;

    public static ApprovalMode fromString(String s) {
        if (s == null) return BALANCED;
        switch (s.toUpperCase(java.util.Locale.ROOT)) {
            case "CONSERVATIVE": return CONSERVATIVE;
            case "AUTONOMOUS":   return AUTONOMOUS;
            default:             return BALANCED;
        }
    }
}
