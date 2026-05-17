package pro.sketchware.git;

public final class GitConfig {
    public String projectId = "";
    public String remoteUrl = "";
    public String token = "";
    public String branch = "main";

    public GitConfig() {}
    public GitConfig(String projectId, String remoteUrl, String token, String branch) {
        this.projectId = projectId == null ? "" : projectId;
        this.remoteUrl = remoteUrl == null ? "" : remoteUrl;
        this.token = token == null ? "" : token;
        this.branch = (branch == null || branch.isEmpty()) ? "main" : branch;
    }
}
