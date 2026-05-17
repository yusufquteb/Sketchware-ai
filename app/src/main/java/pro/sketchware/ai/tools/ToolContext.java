package pro.sketchware.ai.tools;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pro.sketchware.utility.FilePathUtil;

/**
 * Context passed to tools during execution. Contains the application context,
 * the workspace identifier, and the list of project IDs the agent is allowed to access.
 */
public class ToolContext {

    public interface ToolProgressListener {
        void onToolProgress(String toolCallId, String status, int progress, boolean indeterminate);
    }

    public interface CancellationChecker {
        boolean isCancelled();
    }

    private final Context appContext;
    private final List<String> allowedProjectIds;
    private final String workspaceId;
    private final FilePathUtil filePathUtil = new FilePathUtil();

    private ToolProgressListener progressListener;
    private CancellationChecker cancellationChecker;
    private String currentToolCallId;

    public ToolContext(Context appContext, List<String> allowedProjectIds, String workspaceId) {
        this.appContext = appContext;
        this.allowedProjectIds = allowedProjectIds != null
                ? new ArrayList<>(allowedProjectIds)
                : new ArrayList<>();
        this.workspaceId = workspaceId;
    }

    public Context getAppContext() {
        return appContext;
    }

    public List<String> getAllowedProjectIds() {
        return Collections.unmodifiableList(allowedProjectIds);
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public synchronized boolean isProjectAllowed(String scId) {
        if (scId == null || scId.isEmpty()) {
            return false;
        }
        if (allowedProjectIds.contains(scId)) {
            return true;
        }
        if (workspaceId != null && !workspaceId.isEmpty()) {
            try {
                pro.sketchware.ai.storage.WorkspaceManager manager =
                        new pro.sketchware.ai.storage.WorkspaceManager(appContext);
                pro.sketchware.ai.models.Workspace workspace = manager.getWorkspace(workspaceId);
                if (workspace != null && workspace.hasProject(scId)) {
                    allowedProjectIds.add(scId);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public synchronized void addAllowedProjectId(String scId) {
        if (scId == null || scId.isEmpty()) {
            return;
        }
        if (!allowedProjectIds.contains(scId)) {
            allowedProjectIds.add(scId);
        }
    }

    public synchronized void removeAllowedProjectId(String scId) {
        if (scId == null || scId.isEmpty()) {
            return;
        }
        allowedProjectIds.remove(scId);
    }

    public void setToolProgressListener(ToolProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void setCancellationChecker(CancellationChecker cancellationChecker) {
        this.cancellationChecker = cancellationChecker;
    }

    public void beginToolCall(String toolCallId) {
        currentToolCallId = toolCallId;
    }

    public String getCurrentToolCallId() {
        return currentToolCallId;
    }

    public void endToolCall() {
        currentToolCallId = null;
    }

    public boolean isCancelled() {
        return cancellationChecker != null && cancellationChecker.isCancelled();
    }

    public void reportProgress(String status) {
        reportProgress(status, -1, true);
    }

    public void reportProgress(String status, int progress) {
        reportProgress(status, progress, false);
    }

    public void reportProgress(String status, int progress, boolean indeterminate) {
        if (progressListener != null && currentToolCallId != null) {
            progressListener.onToolProgress(currentToolCallId, status, progress, indeterminate);
        }
    }

    public File getSketchwareDir() {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware");
    }

    public File getProjectDataDir(String scId) {
        return new File(getSketchwareDir(), "data" + File.separator + scId);
    }

    public File getProjectMyscDir(String scId) {
        return new File(getSketchwareDir(), "mysc" + File.separator + scId);
    }

    public File getProjectMyscListDir(String scId) {
        return new File(getSketchwareDir(), "mysc" + File.separator + "list" + File.separator + scId);
    }

    public File getProjectBackupDir(String scId) {
        return new File(getSketchwareDir(), "bak" + File.separator + scId);
    }

    public File getProjectJavaDir(String scId) {
        return new File(filePathUtil.getPathJava(scId));
    }

    public File getProjectResourceDir(String scId) {
        return new File(filePathUtil.getPathResource(scId));
    }

    public File getProjectAssetsDir(String scId) {
        return new File(filePathUtil.getPathAssets(scId));
    }

    public File getProjectLocalLibraryFile(String scId) {
        return new File(filePathUtil.getPathLocalLibrary(scId));
    }

    public File getProjectCompileLogFile(String scId) {
        return new File(FilePathUtil.getLastCompileLogPath(scId));
    }
}
