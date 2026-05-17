package pro.sketchware.git;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import pro.sketchware.utility.io.SafeFileOps;

public final class GitRepositoryCore {
    public static String lastError = "";

    private GitRepositoryCore() {}

    public static GitResult cloneRepository(File localPath, GitConfig config) {
        if (config == null) return GitResult.fail("Missing Git configuration", null);
        String branch = config.branch == null || config.branch.isEmpty() ? "main" : config.branch;
        String normalizedUrl = GitUrlNormalizer.normalize(config.remoteUrl);
        try {
            return doClone(localPath, normalizedUrl, config.token, branch);
        } catch (Exception first) {
            lastError = first.getMessage();
            if (!"main".equals(branch)) {
                try { return doClone(localPath, normalizedUrl, config.token, "main"); } catch (Exception ignored) { lastError = ignored.getMessage(); }
            }
            try { return doClone(localPath, normalizedUrl, config.token, "master"); } catch (Exception second) {
                lastError = second.getMessage();
                return GitResult.fail("Clone failed: " + lastError, second);
            }
        }
    }

    private static GitResult doClone(File localPath, String url, String token, String branch) throws Exception {
        if (localPath.exists()) SafeFileOps.deleteRecursively(localPath);
        SafeFileOps.ensureDirectory(localPath);
        CloneCommand command = Git.cloneRepository().setURI(url).setDirectory(localPath).setCloneAllBranches(false).setBranch(branch);
        if (token != null && !token.isEmpty()) command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""));
        try (Git ignored = command.call()) { return GitResult.ok("Cloned " + branch); }
    }

    public static GitResult push(File projectPath, GitConfig config, String title, String description) {
        String message = (title == null || title.trim().isEmpty()) ? "Project update" : title.trim();
        if (description != null && !description.trim().isEmpty()) message += "\n\n" + description.trim();
        try (Git git = openOrInit(projectPath, config)) {
            git.add().addFilepattern(".").call();
            try { git.commit().setMessage(message).call(); } catch (EmptyCommitException ignored) { }
            git.push().setRemote("origin").setCredentialsProvider(credentials(config)).call();
            return GitResult.ok("Pushed");
        } catch (Exception e) {
            lastError = e.getMessage();
            return GitResult.fail("Push failed: " + lastError, e);
        }
    }

    public static GitResult pull(File projectPath, GitConfig config) {
        try (Git git = Git.open(projectPath)) {
            git.pull().setCredentialsProvider(credentials(config)).call();
            return GitResult.ok("Pulled");
        } catch (Exception e) {
            lastError = e.getMessage();
            return GitResult.fail("Pull failed: " + lastError, e);
        }
    }

    public static GitResult switchBranch(File projectPath, GitConfig config, String branchName) {
        String branch = branchName == null || branchName.isEmpty() ? "main" : branchName;
        try (Git git = Git.open(projectPath)) {
            git.fetch().setRemote("origin").setCredentialsProvider(credentials(config)).call();
            git.checkout().setCreateBranch(true).setName(branch).setStartPoint("origin/" + branch).call();
            return GitResult.ok("Switched to " + branch);
        } catch (Exception checkoutFailure) {
            GitConfig cloneConfig = new GitConfig(config.projectId, config.remoteUrl, config.token, branch);
            return cloneRepository(projectPath, cloneConfig);
        }
    }

    public static boolean hasRemoteChanges(File projectPath, GitConfig config) {
        try (Git git = Git.open(projectPath)) {
            if (!git.status().call().isClean()) return true;
            Repository repository = git.getRepository();
            String branch = config.branch == null || config.branch.isEmpty() ? "main" : config.branch;
            ObjectId localHead = repository.resolve("refs/heads/" + branch);
            if (localHead == null) return true;
            String remoteSha = githubBranchSha(config.remoteUrl, config.token, branch);
            return remoteSha.isEmpty() || !localHead.name().equals(remoteSha);
        } catch (Exception e) {
            lastError = e.getMessage();
            return true;
        }
    }

    private static Git openOrInit(File projectPath, GitConfig config) throws Exception {
        if (new File(projectPath, ".git").exists()) return Git.open(projectPath);
        SafeFileOps.ensureDirectory(projectPath);
        Git git = Git.init().setDirectory(projectPath).call();
        git.remoteAdd().setName("origin").setUri(new URIish(GitUrlNormalizer.normalize(config.remoteUrl))).call();
        return git;
    }

    private static UsernamePasswordCredentialsProvider credentials(GitConfig config) {
        String token = config == null || config.token == null ? "" : config.token;
        return new UsernamePasswordCredentialsProvider(token, "");
    }

    private static String githubBranchSha(String remote, String token, String branch) throws Exception {
        String normalized = GitUrlNormalizer.normalize(remote).replace(".git", "");
        String[] parts = normalized.split("/");
        if (parts.length < 2 || !normalized.contains("github.com")) return "";
        String api = "https://api.github.com/repos/" + parts[parts.length - 2] + "/" + parts[parts.length - 1] + "/branches/" + branch;
        HttpURLConnection c = (HttpURLConnection) new URL(api).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/vnd.github.v3+json");
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "token " + token);
        if (c.getResponseCode() != 200) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
        reader.close();
        return new JSONObject(body.toString()).getJSONObject("commit").getString("sha");
    }
}
