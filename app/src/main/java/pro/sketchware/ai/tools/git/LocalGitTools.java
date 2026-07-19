package pro.sketchware.ai.tools.git;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.io.File;
import java.util.Set;

import pro.sketchware.ai.engine.risk.RiskLevel;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.ai.tools.AgentTool;
import pro.sketchware.ai.tools.ToolContext;

/**
 * LocalGitTools — Phase 3, item 2 ("a real local Git tool").
 *
 * WHAT THE PROMPT ASKED TO VERIFY, AND WHAT WAS ACTUALLY FOUND (contradicts
 * the prompt's assumed premise): the prompt said to check whether the project
 * uses any Git library (org.eclipse.jgit or otherwise) and, if not, to STOP
 * and ask before adding one as a new dependency. It is already a dependency —
 * {@code app/build.gradle} already declares
 * {@code org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r} — and there
 * is already a whole {@code pro.sketchware.git} package using it
 * (GitRepositoryCore, GitPatchApplier, GitQuickLook) plus a non-AI
 * GitWorkflowActivity screen. So the "no dependency exists" branch of the
 * prompt's rule 2 does not apply; no new dependency was added.
 *
 * HOWEVER, everything in {@code pro.sketchware.git} is built around REMOTE
 * workflows — {@link pro.sketchware.git.GitConfig} requires a remoteUrl and
 * (usually) a token, and every operation (clone/push/pull) either clones from
 * or syncs against that remote. There is no existing wrapper for plain LOCAL
 * git operations (init a repo with no remote, check status, stage files,
 * commit) — which is what this phase actually asked for ("Git محلية حقيقية").
 * This class is that missing local-only wrapper. It calls JGit's {@code Git}
 * API directly rather than going through {@code GitRepositoryCore}, since
 * every method there either requires a {@code GitConfig} with a remote URL
 * or performs a network operation this phase's tools should not need.
 *
 * Explicitly NOT implemented here (out of scope per this phase's own text,
 * which asked only for init/status/add/commit): push, pull, clone, branch
 * switching, merge, or any remote interaction — those already exist in
 * {@code pro.sketchware.git} for the cases that need a remote.
 */
public final class LocalGitTools {

    private LocalGitTools() {}

    private static ToolResult ok(String output) { return ToolResult.success(null, output); }
    private static ToolResult err(String msg) { return ToolResult.failure(null, msg); }

    private static void addProp(JsonObject props, String key, String type, String desc) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", desc);
        props.add(key, p);
    }

    private static JsonObject scIdOnlySchema(String scIdDesc) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        addProp(props, "sc_id", "string", scIdDesc);
        schema.add("properties", props);
        JsonArray req = new JsonArray();
        req.add("sc_id");
        schema.add("required", req);
        return schema;
    }

    // ── git_init ─────────────────────────────────────────────────────────────

    public static class GitInitTool implements AgentTool {
        @Override public String getName() { return "git_init"; }
        @Override public RiskLevel getRiskLevel() { return RiskLevel.LOW; }
        @Override public boolean requiresProject() { return true; }
        @Override public String getDescription() {
            return "Initialises a local Git repository in a project's working directory (no remote). "
                 + "Safe to call on an already-initialised project — reports 'already a git repo' "
                 + "instead of failing.";
        }
        @Override public JsonObject getParametersSchema() {
            return scIdOnlySchema("Project ID whose working directory should become a git repo");
        }
        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = requireScId(args, ctx);
            if (scId == null) return err("sc_id is required and must be an accessible project");
            File dir = ctx.getProjectDataDir(scId);
            if (new File(dir, ".git").exists()) {
                return ok("Project " + scId + " is already a git repository.");
            }
            try {
                ctx.reportProgress("Running git init…", -1, true);
                try (Git git = Git.init().setDirectory(dir).call()) {
                    return ok("Initialised empty git repository in " + dir.getAbsolutePath());
                }
            } catch (Exception e) {
                return err("git init failed: " + e.getMessage());
            }
        }
    }

    // ── git_status ───────────────────────────────────────────────────────────

    public static class GitStatusTool implements AgentTool {
        @Override public String getName() { return "git_status"; }
        @Override public RiskLevel getRiskLevel() { return RiskLevel.LOW; }
        @Override public boolean requiresProject() { return true; }
        @Override public String getDescription() {
            return "Shows the working-tree status (staged, modified, untracked files) for a project's "
                 + "local git repository. Requires git_init to have been run first.";
        }
        @Override public JsonObject getParametersSchema() {
            return scIdOnlySchema("Project ID to check git status for");
        }
        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = requireScId(args, ctx);
            if (scId == null) return err("sc_id is required and must be an accessible project");
            File dir = ctx.getProjectDataDir(scId);
            if (!new File(dir, ".git").exists()) {
                return err("Project " + scId + " is not a git repository yet. Call git_init first.");
            }
            try (Git git = Git.open(dir)) {
                ctx.reportProgress("Running git status…", -1, true);
                Status status = git.status().call();
                StringBuilder sb = new StringBuilder();
                sb.append("branch: ").append(safeBranch(git)).append("\n");
                appendSet(sb, "staged (added)", status.getAdded());
                appendSet(sb, "staged (changed)", status.getChanged());
                appendSet(sb, "staged (removed)", status.getRemoved());
                appendSet(sb, "modified (unstaged)", status.getModified());
                appendSet(sb, "untracked", status.getUntracked());
                appendSet(sb, "missing", status.getMissing());
                if (status.isClean()) sb.append("working tree clean\n");
                return ok(sb.toString());
            } catch (Exception e) {
                return err("git status failed: " + e.getMessage());
            }
        }

        private static String safeBranch(Git git) {
            try { return git.getRepository().getBranch(); }
            catch (Exception e) { return "?"; }
        }

        private static void appendSet(StringBuilder sb, String label, Set<String> items) {
            if (items == null || items.isEmpty()) return;
            sb.append(label).append(" (").append(items.size()).append("):\n");
            for (String item : items) sb.append("  ").append(item).append("\n");
        }
    }

    // ── git_add ──────────────────────────────────────────────────────────────

    public static class GitAddTool implements AgentTool {
        @Override public String getName() { return "git_add"; }
        @Override public RiskLevel getRiskLevel() { return RiskLevel.LOW; }
        @Override public boolean requiresProject() { return true; }
        @Override public String getDescription() {
            return "Stages files for commit in a project's local git repository. "
                 + "Optional 'pattern' (default '.' — stage everything); pass a specific "
                 + "relative path to stage just that file or directory.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject schema = scIdOnlySchema("Project ID");
            JsonObject props = schema.getAsJsonObject("properties");
            addProp(props, "pattern", "string", "File pattern to stage, relative to the project root. Default '.' stages everything.");
            return schema;
        }
        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = requireScId(args, ctx);
            if (scId == null) return err("sc_id is required and must be an accessible project");
            File dir = ctx.getProjectDataDir(scId);
            if (!new File(dir, ".git").exists()) {
                return err("Project " + scId + " is not a git repository yet. Call git_init first.");
            }
            String pattern = args.has("pattern") && !args.get("pattern").isJsonNull()
                    ? args.get("pattern").getAsString().trim() : ".";
            if (pattern.isEmpty()) pattern = ".";
            try (Git git = Git.open(dir)) {
                ctx.reportProgress("Staging " + pattern + "…", -1, true);
                git.add().addFilepattern(pattern).call();
                return ok("Staged '" + pattern + "' in project " + scId + ".");
            } catch (Exception e) {
                return err("git add failed: " + e.getMessage());
            }
        }
    }

    // ── git_commit ───────────────────────────────────────────────────────────

    public static class GitCommitTool implements AgentTool {
        @Override public String getName() { return "git_commit"; }
        @Override public RiskLevel getRiskLevel() { return RiskLevel.MEDIUM; }
        @Override public boolean requiresProject() { return true; }
        @Override public String getDescription() {
            return "Commits currently staged changes in a project's local git repository. "
                 + "Required: sc_id, message. Fails clearly if nothing is staged rather than "
                 + "creating an empty commit.";
        }
        @Override public JsonObject getParametersSchema() {
            JsonObject schema = scIdOnlySchema("Project ID");
            JsonObject props = schema.getAsJsonObject("properties");
            addProp(props, "message", "string", "Commit message");
            JsonArray req = schema.getAsJsonArray("required");
            req.add("message");
            return schema;
        }
        @Override
        public ToolResult execute(JsonObject args, ToolContext ctx) {
            String scId = requireScId(args, ctx);
            if (scId == null) return err("sc_id is required and must be an accessible project");
            if (!args.has("message") || args.get("message").isJsonNull()
                    || args.get("message").getAsString().trim().isEmpty()) {
                return err("message is required and must not be empty");
            }
            String message = args.get("message").getAsString().trim();
            File dir = ctx.getProjectDataDir(scId);
            if (!new File(dir, ".git").exists()) {
                return err("Project " + scId + " is not a git repository yet. Call git_init first.");
            }
            try (Git git = Git.open(dir)) {
                Status status = git.status().call();
                boolean hasStaged = !status.getAdded().isEmpty() || !status.getChanged().isEmpty()
                        || !status.getRemoved().isEmpty();
                if (!hasStaged) {
                    return err("Nothing staged to commit. Call git_add first.");
                }
                ctx.reportProgress("Committing…", -1, true);
                org.eclipse.jgit.revwalk.RevCommit commit = git.commit().setMessage(message).call();
                String shortHash = commit.getName().substring(0, Math.min(8, commit.getName().length()));
                return ok("Committed " + shortHash + ": " + message);
            } catch (Exception e) {
                return err("git commit failed: " + e.getMessage());
            }
        }
    }

    private static String requireScId(JsonObject args, ToolContext ctx) {
        if (!args.has("sc_id") || args.get("sc_id").isJsonNull()) return null;
        String scId = args.get("sc_id").getAsString().trim();
        if (scId.isEmpty() || !ctx.isProjectAllowed(scId)) return null;
        return scId;
    }
}
