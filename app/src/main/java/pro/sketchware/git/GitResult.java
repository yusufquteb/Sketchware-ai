package pro.sketchware.git;

public final class GitResult {
    public final boolean success;
    public final String message;
    public final Throwable error;

    private GitResult(boolean success, String message, Throwable error) {
        this.success = success; this.message = message == null ? "" : message; this.error = error;
    }

    public static GitResult ok(String message) { return new GitResult(true, message, null); }
    public static GitResult fail(String message, Throwable error) { return new GitResult(false, message, error); }
}
