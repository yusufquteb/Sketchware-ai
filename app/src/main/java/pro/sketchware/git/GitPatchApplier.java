package pro.sketchware.git;

import org.eclipse.jgit.api.Git;

import java.io.File;

public final class GitPatchApplier {
    private GitPatchApplier() {}

    public static GitResult applyPatch(File repositoryRoot, File patchFile) {
        try (Git git = Git.open(repositoryRoot)) {
            git.apply().setPatch(patchFile.toURI().toURL().openStream()).call();
            return GitResult.ok("Patch applied");
        } catch (Exception e) {
            return GitResult.fail("Patch apply failed: " + e.getMessage(), e);
        }
    }
}
