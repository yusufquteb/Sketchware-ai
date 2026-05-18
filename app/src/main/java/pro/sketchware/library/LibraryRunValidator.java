package pro.sketchware.library;

import dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity;

import android.content.Context;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-run library validation for DayDream projects.
 *
 * <p>Call {@link #validateAndRun(Context, List, Runnable)} before starting
 * a project build/run.  It:
 * <ol>
 *   <li>Checks every required library against the local_libs folder</li>
 *   <li>For exact matches → OK, proceed</li>
 *   <li>For fuzzy (base-name) matches → use the found library, no dialog</li>
 *   <li>For missing libraries → show download prompt, run only after resolution</li>
 *   <li>For completely unknown libraries with no download URL → warn user</li>
 * </ol>
 *
 * <p>Integration example (in ProjectBuilder or RunManager):
 * <pre>
 *   List&lt;String&gt; required = project.getLibraryNames();  // from project data
 *   LibraryRunValidator.validateAndRun(activity, required, () -&gt; {
 *       // all libraries are present — start compilation
 *       startBuild();
 *   });
 * </pre>
 */
public class LibraryRunValidator {

    private static final String TAG = "LibRunValidator";

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates that all required libraries are available, then executes {@code onAllReady}.
     *
     * @param host         the Activity hosting the dialogs
     * @param requiredLibs list of library names the project references
     * @param onAllReady   called on the main thread when all libraries are ready
     */
    public static void validateAndRun(AppCompatActivity host,
                                      List<String> requiredLibs,
                                      Runnable onAllReady) {
        if (requiredLibs == null || requiredLibs.isEmpty()) {
            if (onAllReady != null) onAllReady.run();
            return;
        }

        ValidationState state = new ValidationState(requiredLibs, onAllReady);
        processNext(host, state);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal — sequential resolution
    // ─────────────────────────────────────────────────────────────────────────

    private static class ValidationState {
        final List<String> remaining;
        final Runnable onAllReady;
        /** Libraries that resolved to a different name (for logging) */
        final List<String> remappedLibs = new ArrayList<>();

        ValidationState(List<String> libs, Runnable onAllReady) {
            this.remaining  = new ArrayList<>(libs);
            this.onAllReady = onAllReady;
        }
    }

    /** Processes libraries one by one; shows at most one dialog at a time */
    private static void processNext(AppCompatActivity host, ValidationState state) {
        if (state.remaining.isEmpty()) {
            // All done
            if (!state.remappedLibs.isEmpty()) {
                Log.i(TAG, "Libraries resolved via base-name: " + state.remappedLibs);
            }
            if (state.onAllReady != null) state.onAllReady.run();
            return;
        }

        String libName = state.remaining.remove(0);
        BuiltInLibraryHelper.ResolveResult result =
                BuiltInLibraryHelper.resolveLibraryForRun(libName);

        switch (result.status) {

            case FOUND:
                // Perfect match — continue
                processNext(host, state);
                break;

            case FOUND_BY_BASENAME:
                // Updated/renamed library found by base name — use it silently
                state.remappedLibs.add(libName + " → " + result.item.name);
                processNext(host, state);
                break;

            case JAR_MISSING:
                // Library metadata exists but JAR is absent — offer to download
                String version = result.item != null ? result.item.version : null;
                dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity.promptMissingLibraryDownload(
                        host, libName, version,
                        () -> processNext(host, state)  // retry after download
                );
                break;

            case NOT_FOUND:
                // Completely absent — offer to download if we know it, else warn
                BuiltInLibraryHelper.BuiltInLibDef def =
                        BuiltInLibraryHelper.getDefinition(
                                libName.replaceAll("-\\d+(\\.\\d+)*$", ""));

                String ver = (def != null) ? def.version : null;
                dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity.promptMissingLibraryDownload(
                        host, libName, ver,
                        () -> processNext(host, state)
                );
                break;

            default:
                // Unknown status — skip and continue to avoid blocking the build
                Log.w(TAG, "Unknown resolve status for " + libName);
                processNext(host, state);
                break;
        }
    }
}
