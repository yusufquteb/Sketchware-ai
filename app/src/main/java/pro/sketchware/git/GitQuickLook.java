package pro.sketchware.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class GitQuickLook {
    private GitQuickLook() {}

    public static List<String> recentCommits(File repositoryRoot, int limit) throws Exception {
        List<String> out = new ArrayList<>();
        try (Git git = Git.open(repositoryRoot)) {
            Iterable<RevCommit> commits = git.log().setMaxCount(Math.max(1, limit)).call();
            for (RevCommit commit : commits) out.add(commit.getName() + " " + commit.getShortMessage());
        }
        return out;
    }
}
