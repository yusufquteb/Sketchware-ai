package pro.sketchware.git;

import java.io.File;

public final class GitProjectWorkflow {
    private final File repositoryRoot;
    private final GitConfig config;

    public GitProjectWorkflow(File repositoryRoot, GitConfig config) {
        this.repositoryRoot = repositoryRoot;
        this.config = config;
    }

    public GitResult cloneOrRefresh() { return GitRepositoryCore.cloneRepository(repositoryRoot, config); }
    public GitResult pull() { return GitRepositoryCore.pull(repositoryRoot, config); }
    public GitResult push(String title, String description) { return GitRepositoryCore.push(repositoryRoot, config, title, description); }
    public GitResult switchBranch(String branch) { return GitRepositoryCore.switchBranch(repositoryRoot, config, branch); }
    public boolean needsPull() { return GitRepositoryCore.hasRemoteChanges(repositoryRoot, config); }
}
