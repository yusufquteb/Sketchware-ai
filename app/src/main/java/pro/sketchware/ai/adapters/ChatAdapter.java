package pro.sketchware.ai.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import android.util.Base64;
import io.noties.markwon.Markwon;
import pro.sketchware.R;
import pro.sketchware.ai.models.ChatMessage;
import pro.sketchware.ai.models.ToolCall;
import pro.sketchware.ai.models.ToolResult;
import pro.sketchware.databinding.ItemChatMessageAssistantBinding;
import pro.sketchware.databinding.ItemChatMessageUserBinding;
import pro.sketchware.databinding.ItemChatToolCallBinding;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnArtifactActionListener {
        void onInstallArtifact(@NonNull String artifactPath);
    }

    static final int TYPE_USER = 0;
    static final int TYPE_ASSISTANT = 1;

    private static final int MAX_TOOL_PREVIEW_LENGTH = 220;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private enum ToolPhase {
        RUNNING,
        SUCCESS,
        FAILED
    }

    private static class ToolVisualSpec {
        @NonNull final String title;
        @DrawableRes final int toolIconRes;
        @DrawableRes final int statusIconRes;
        @Nullable final String collapsedDetail;
        @Nullable final String details;

        ToolVisualSpec(@NonNull String title, @DrawableRes int toolIconRes,
                       @DrawableRes int statusIconRes, @Nullable String collapsedDetail,
                       @Nullable String details) {
            this.title = title;
            this.toolIconRes = toolIconRes;
            this.statusIconRes = statusIconRes;
            this.collapsedDetail = collapsedDetail;
            this.details = details;
        }
    }

    private static class ToolUiState {
        @NonNull final ToolCall toolCall;
        @Nullable ToolResult toolResult;
        @Nullable String status;
        int progress = -1;
        boolean indeterminate = true;
        boolean expanded = false;

        ToolUiState(@NonNull ToolCall toolCall) {
            this.toolCall = toolCall;
        }
    }

    public static class ChatItem {
        final int type;
        @Nullable ChatMessage message;
        @NonNull final List<ToolUiState> toolStates;
        /** Whether this message is expanded (show full text) or collapsed (max 8 lines) */
        boolean messageExpanded = false;

        private ChatItem(int type, @Nullable ChatMessage message) {
            this.type = type;
            this.message = message;
            this.toolStates = new ArrayList<>();
        }

        public static ChatItem userMessage(@NonNull ChatMessage msg) {
            return new ChatItem(TYPE_USER, msg);
        }

        public static ChatItem assistantMessage(@NonNull ChatMessage msg) {
            ChatItem item = new ChatItem(TYPE_ASSISTANT, msg);
            List<ToolCall> toolCalls = msg.getToolCalls();
            if (toolCalls != null) {
                for (ToolCall toolCall : toolCalls) {
                    item.toolStates.add(new ToolUiState(toolCall));
                }
            }
            return item;
        }
    }

    private final List<ChatItem> items = new ArrayList<>();
    @Nullable private OnArtifactActionListener artifactActionListener;
    private Markwon markwon;

    public void setArtifactActionListener(@Nullable OnArtifactActionListener artifactActionListener) {
        this.artifactActionListener = artifactActionListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (markwon == null) {
            markwon = Markwon.create(parent.getContext());
        }
        if (viewType == TYPE_USER) {
            return new UserViewHolder(ItemChatMessageUserBinding.inflate(inflater, parent, false));
        }
        return new AssistantViewHolder(ItemChatMessageAssistantBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = items.get(position);
        if (item.type == TYPE_USER) {
            ((UserViewHolder) holder).bind(item);
        } else {
            ((AssistantViewHolder) holder).bind(item, markwon, artifactActionListener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    public void addUserMessage(@NonNull ChatMessage msg) {
        items.add(ChatItem.userMessage(msg));
        notifyItemInserted(items.size() - 1);
    }

    public void addAssistantMessage(@NonNull ChatMessage msg) {
        ChatItem item = ChatItem.assistantMessage(msg);
        if (!shouldRenderAssistantMessage(item)) {
            return;
        }
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    public void updateLastAssistantMessage(@NonNull String chunk) {
        ChatItem item = ensureStreamingAssistantItem();
        if (item.message == null) {
            return;
        }
        item.message.appendContent(chunk);
        notifyItemChanged(items.indexOf(item));
    }

    public void replaceStreamingAssistantMessage(@NonNull ChatMessage finalMessage) {
        int streamingIndex = findStreamingAssistantIndex();
        ChatItem finalItem = ChatItem.assistantMessage(finalMessage);
        if (streamingIndex >= 0) {
            ChatItem existing = items.get(streamingIndex);
            mergeToolStates(existing, finalItem);
            if (shouldRenderAssistantMessage(finalItem)) {
                items.set(streamingIndex, finalItem);
                dedupeToolStates(finalItem);
                int updatedIndex = items.indexOf(finalItem);
                if (updatedIndex >= 0) {
                    notifyItemChanged(updatedIndex);
                }
            } else {
                items.remove(streamingIndex);
                notifyItemRemoved(streamingIndex);
            }
        } else if (shouldRenderAssistantMessage(finalItem)) {
            items.add(finalItem);
            dedupeToolStates(finalItem);
            int insertedIndex = items.indexOf(finalItem);
            if (insertedIndex >= 0) {
                notifyItemInserted(insertedIndex);
            }
        }
    }

    public void addToolCall(@NonNull ToolCall tc) {
        ChatItem item = ensureStreamingAssistantItem();
        if (findToolState(item, tc.getId()) == null) {
            item.toolStates.add(new ToolUiState(tc));
            dedupeToolStates(item);
            notifyItemChanged(items.indexOf(item));
        }
    }

    public void updateToolCallProgress(@NonNull String toolCallId, @Nullable String status,
                                       int progress, boolean indeterminate) {
        int index = findAssistantIndexForTool(toolCallId);
        if (index < 0) {
            return;
        }
        ToolUiState state = findToolState(items.get(index), toolCallId);
        if (state == null) {
            return;
        }
        state.status = status;
        state.progress = progress;
        state.indeterminate = indeterminate;
        notifyItemChanged(index);
    }

    public void updateToolCallResult(@NonNull String toolCallId, @NonNull ToolResult result) {
        int index = findAssistantIndexForTool(toolCallId);
        if (index < 0) {
            return;
        }
        ToolUiState state = findToolState(items.get(index), toolCallId);
        if (state == null) {
            return;
        }
        state.toolResult = result;
        state.progress = result.isSuccess() ? 100 : -1;
        state.indeterminate = false;
        notifyItemChanged(index);
    }

    public void setMessages(@NonNull List<ChatMessage> messages) {
        List<ChatItem> oldItems = new ArrayList<>(items);
        items.clear();
        Map<String, Integer> latestAssistantIndexByToolId = collectLatestAssistantIndexByToolId(messages);
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String role = msg.getRole();
            if ("user".equals(role)) {
                items.add(ChatItem.userMessage(msg));
            } else if ("assistant".equals(role)) {
                ChatItem assistantItem = ChatItem.assistantMessage(msg);
                pruneStaleToolStates(assistantItem, latestAssistantIndexByToolId, i);
                if (shouldRenderAssistantMessage(assistantItem)) {
                    items.add(assistantItem);
                }
            } else if ("tool".equals(role)) {
                applyPersistedToolResult(msg);
            }
        }
        List<ChatItem> newItems = new ArrayList<>(items);
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldItems.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                ChatMessage oldMsg = oldItems.get(oldPos).message;
                ChatMessage newMsg = newItems.get(newPos).message;
                if (oldMsg == null || newMsg == null) return oldMsg == newMsg;
                return Objects.equals(oldMsg.getId(), newMsg.getId());
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                ChatItem oldItem = oldItems.get(oldPos);
                ChatItem newItem = newItems.get(newPos);
                if (oldItem.message == null || newItem.message == null) return oldItem.message == newItem.message;
                return Objects.equals(oldItem.message.getContent(), newItem.message.getContent())
                        && oldItem.toolStates.size() == newItem.toolStates.size();
            }
        }).dispatchUpdatesTo(this);
    }

    private void applyPersistedToolResult(@NonNull ChatMessage msg) {
        String toolCallId = msg.getToolCallId();
        if (toolCallId == null) {
            return;
        }
        int assistantIndex = findAssistantIndexForTool(toolCallId);
        if (assistantIndex < 0) {
            return;
        }

        String content = msg.getContent() != null ? msg.getContent() : "";
        ToolResult result = content.startsWith("Error:")
                ? ToolResult.failure(toolCallId, content.substring("Error:".length()).trim())
                : ToolResult.success(toolCallId, content);
        ToolUiState state = findToolState(items.get(assistantIndex), toolCallId);
        if (state != null) {
            state.toolResult = result;
            state.progress = result.isSuccess() ? 100 : -1;
            state.indeterminate = false;
        }
    }

    private void mergeToolStates(@NonNull ChatItem existing, @NonNull ChatItem replacement) {
        Map<String, ToolUiState> previousById = new LinkedHashMap<>();
        for (ToolUiState state : existing.toolStates) {
            previousById.put(state.toolCall.getId(), state);
        }

        if (replacement.toolStates.isEmpty() && !existing.toolStates.isEmpty()) {
            replacement.toolStates.addAll(existing.toolStates);
            return;
        }

        for (ToolUiState replacementState : replacement.toolStates) {
            ToolUiState previousState = previousById.get(replacementState.toolCall.getId());
            if (previousState != null) {
                replacementState.toolResult = previousState.toolResult;
                replacementState.status = previousState.status;
                replacementState.progress = previousState.progress;
                replacementState.indeterminate = previousState.indeterminate;
                replacementState.expanded = previousState.expanded;
            }
        }
    }

    private boolean shouldRenderAssistantMessage(@Nullable ChatItem item) {
        if (item == null || item.message == null) {
            return false;
        }
        return item.message.hasVisibleAssistantContent()
                || item.message.isStreaming()
                || !item.toolStates.isEmpty();
    }

    @NonNull
    private ChatItem ensureStreamingAssistantItem() {
        int streamingIndex = findStreamingAssistantIndex();
        if (streamingIndex >= 0) {
            return items.get(streamingIndex);
        }
        ChatMessage placeholder = ChatMessage.assistantMessage("", null);
        placeholder.setStreaming(true);
        ChatItem item = ChatItem.assistantMessage(placeholder);
        items.add(item);
        notifyItemInserted(items.size() - 1);
        return item;
    }

    private void dedupeToolStates(@NonNull ChatItem owner) {
        Set<String> toolCallIds = new LinkedHashSet<>();
        for (ToolUiState state : owner.toolStates) {
            toolCallIds.add(state.toolCall.getId());
        }
        if (toolCallIds.isEmpty()) {
            return;
        }

        for (int i = items.size() - 1; i >= 0; i--) {
            ChatItem item = items.get(i);
            if (item == owner || item.type != TYPE_ASSISTANT) {
                continue;
            }
            boolean changed = item.toolStates.removeIf(state -> toolCallIds.contains(state.toolCall.getId()));
            if (!changed) {
                continue;
            }
            if (!shouldRenderAssistantMessage(item)) {
                items.remove(i);
                notifyItemRemoved(i);
            } else {
                notifyItemChanged(i);
            }
        }
    }

    private Map<String, Integer> collectLatestAssistantIndexByToolId(@NonNull List<ChatMessage> messages) {
        Map<String, Integer> latestIndexByToolId = new LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (!"assistant".equals(message.getRole()) || message.getToolCalls() == null) {
                continue;
            }
            for (ToolCall toolCall : message.getToolCalls()) {
                if (toolCall != null && !TextUtils.isEmpty(toolCall.getId())) {
                    latestIndexByToolId.put(toolCall.getId(), i);
                }
            }
        }
        return latestIndexByToolId;
    }

    private void pruneStaleToolStates(@NonNull ChatItem assistantItem,
                                      @NonNull Map<String, Integer> latestAssistantIndexByToolId,
                                      int assistantMessageIndex) {
        assistantItem.toolStates.removeIf(state -> {
            String toolCallId = state.toolCall.getId();
            Integer latestIndex = latestAssistantIndexByToolId.get(toolCallId);
            return latestIndex != null && latestIndex != assistantMessageIndex;
        });
    }

    private int findStreamingAssistantIndex() {
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatItem item = items.get(i);
            if (item.type == TYPE_ASSISTANT && item.message != null && item.message.isStreaming()) {
                return i;
            }
        }
        return -1;
    }

    private int findAssistantIndexForTool(@NonNull String toolCallId) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ChatItem item = items.get(i);
            if (item.type == TYPE_ASSISTANT && findToolState(item, toolCallId) != null) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private ToolUiState findToolState(@NonNull ChatItem item, @NonNull String toolCallId) {
        for (ToolUiState state : item.toolStates) {
            if (toolCallId.equals(state.toolCall.getId())) {
                return state;
            }
        }
        return null;
    }

    private static String formatTimestamp(long timestamp) {
        return TIME_FORMAT.format(new Date(timestamp));
    }

    private static String summarizeToolArguments(@Nullable String arguments) {
        return summarizeStructuredPayload(arguments, true);
    }

    private static String summarizeToolResult(@Nullable ToolResult result) {
        if (result == null) {
            return "";
        }
        return summarizeStructuredPayload(result.isSuccess() ? result.getOutput() : result.getError(), false);
    }

    private static String summarizeStructuredPayload(@Nullable String payload, boolean compact) {
        if (TextUtils.isEmpty(payload)) {
            return compact ? "No details" : "";
        }
        try {
            JsonElement element = JsonParser.parseString(payload.trim());
            if (element.isJsonObject()) {
                return summarizeObject(element.getAsJsonObject(), compact);
            }
            if (element.isJsonArray()) {
                return summarizeArray(element.getAsJsonArray(), compact);
            }
        } catch (Exception ignored) {
        }
        String normalized = payload.trim().replace("\n", " ").replaceAll("\\s+", " ");
        if (normalized.length() > MAX_TOOL_PREVIEW_LENGTH) {
            normalized = normalized.substring(0, MAX_TOOL_PREVIEW_LENGTH) + "...";
        }
        return normalized;
    }

    private static String summarizeObject(@NonNull JsonObject object, boolean compact) {
        List<String> lines = new ArrayList<>();
        addLineIfPresent(lines, object, "message");
        addLineIfPresent(lines, object, "status");
        addLineIfPresent(lines, object, "sc_id");
        addLineIfPresent(lines, object, "file_path");
        addLineIfPresent(lines, object, "artifact_path");
        addLineIfPresent(lines, object, "compile_log_path");
        addLineIfPresent(lines, object, "dependency");
        addLineIfPresent(lines, object, "library_name");
        addLineIfPresent(lines, object, "root");

        if (object.has("attached_libraries") && object.get("attached_libraries").isJsonArray()) {
            lines.add("libraries: " + summarizeArray(object.getAsJsonArray("attached_libraries"), true));
        }
        if (object.has("content") && object.get("content").isJsonPrimitive()) {
            String preview = object.get("content").getAsString().trim().replaceAll("\\s+", " ");
            if (preview.length() > MAX_TOOL_PREVIEW_LENGTH) {
                preview = preview.substring(0, MAX_TOOL_PREVIEW_LENGTH) + "...";
            }
            lines.add("preview: " + preview);
        }

        if (lines.isEmpty()) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (lines.size() >= (compact ? 4 : 6)) {
                    break;
                }
                if (entry.getValue().isJsonPrimitive()) {
                    lines.add(entry.getKey() + ": " + entry.getValue().getAsString());
                }
            }
        }
        return TextUtils.join("\n", lines);
    }

    private static void addLineIfPresent(@NonNull List<String> lines, @NonNull JsonObject object, @NonNull String key) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            lines.add(key.replace('_', ' ') + ": " + object.get(key).getAsString());
        }
    }

    private static String summarizeArray(@NonNull JsonArray array, boolean compact) {
        List<String> previews = new ArrayList<>();
        int limit = compact ? 3 : 5;
        for (int i = 0; i < array.size() && i < limit; i++) {
            JsonElement element = array.get(i);
            if (element.isJsonPrimitive()) {
                previews.add(element.getAsString());
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("name") && object.get("name").isJsonPrimitive()) {
                    previews.add(object.get("name").getAsString());
                } else {
                    previews.add("item " + (i + 1));
                }
            }
        }
        if (array.size() > limit) {
            previews.add("+" + (array.size() - limit) + " more");
        }
        return previews.isEmpty() ? "No items" : TextUtils.join(", ", previews);
    }

    @Nullable
    private static String extractInstallableArtifactPath(@Nullable ToolResult result) {
        if (result == null || !result.isSuccess() || TextUtils.isEmpty(result.getOutput())) {
            return null;
        }
        try {
            JsonObject object = JsonParser.parseString(result.getOutput()).getAsJsonObject();
            boolean installable = object.has("installable")
                    && object.get("installable").isJsonPrimitive()
                    && object.get("installable").getAsBoolean();
            if (!installable) {
                return null;
            }
            if (!object.has("artifact_path") || !object.get("artifact_path").isJsonPrimitive()) {
                return null;
            }
            String path = object.get("artifact_path").getAsString();
            return path.endsWith(".apk") ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static JsonObject parseJsonObject(@Nullable String payload) {
        if (TextUtils.isEmpty(payload)) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(payload.trim());
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ToolPhase resolveToolPhase(@NonNull ToolUiState state) {
        if (state.toolResult == null) {
            return ToolPhase.RUNNING;
        }
        return state.toolResult.isSuccess() ? ToolPhase.SUCCESS : ToolPhase.FAILED;
    }

    @NonNull
    private static ToolVisualSpec buildToolVisualSpec(@NonNull ToolUiState state) {
        ToolCall toolCall = state.toolCall;
        String toolName = toolCall.getName() != null ? toolCall.getName() : "tool";
        ToolPhase phase = resolveToolPhase(state);
        JsonObject arguments = parseJsonObject(toolCall.getArguments());
        JsonObject successPayload = state.toolResult != null && state.toolResult.isSuccess()
                ? parseJsonObject(state.toolResult.getOutput()) : null;
        JsonObject failurePayload = state.toolResult != null && !state.toolResult.isSuccess()
                ? parseJsonObject(state.toolResult.getError()) : null;
        String subject = resolveToolSubject(toolName, arguments, successPayload, failurePayload);
        String title = buildToolTitle(toolName, phase, subject);
        String details = buildToolDetails(state);
        String collapsedDetail = phase == ToolPhase.RUNNING ? trimToNull(state.status) : null;
        return new ToolVisualSpec(title, resolveToolIcon(toolName), resolveStatusIcon(phase), collapsedDetail, details);
    }

    @NonNull
    private static String buildToolDetails(@NonNull ToolUiState state) {
        List<String> sections = new ArrayList<>();
        String runningDetail = trimToNull(state.status);
        if (resolveToolPhase(state) == ToolPhase.RUNNING && runningDetail != null && !"Starting...".equals(runningDetail)) {
            sections.add("Current step\n" + runningDetail);
        }
        String argumentsSummary = trimToNull(summarizeToolArguments(state.toolCall.getArguments()));
        if (argumentsSummary != null && !"No details".equals(argumentsSummary)) {
            sections.add("Request\n" + argumentsSummary);
        }
        String resultSummary = trimToNull(summarizeToolResult(state.toolResult));
        if (resultSummary != null) {
            sections.add((state.toolResult != null && state.toolResult.isSuccess() ? "Result" : "Issue") + "\n" + resultSummary);
        }
        return sections.isEmpty() ? "No extra details yet." : TextUtils.join("\n\n", sections);
    }

    @NonNull
    private static String buildToolTitle(@NonNull String toolName, @NonNull ToolPhase phase,
                                         @Nullable String subject) {
        String title;
        switch (toolName) {
            case "create_project":
                title = phase == ToolPhase.SUCCESS ? "Created Project" : phase == ToolPhase.FAILED ? "Creating Project Failed" : "Creating Project";
                break;
            case "delete_project":
                title = phase == ToolPhase.SUCCESS ? "Deleted Project" : phase == ToolPhase.FAILED ? "Deleting Project Failed" : "Deleting Project";
                break;
            case "duplicate_project":
                title = phase == ToolPhase.SUCCESS ? "Duplicated Project" : phase == ToolPhase.FAILED ? "Duplicating Project Failed" : "Duplicating Project";
                break;
            case "get_project_info":
                title = phase == ToolPhase.SUCCESS ? "Loaded Project Info" : phase == ToolPhase.FAILED ? "Loading Project Info Failed" : "Loading Project Info";
                break;
            case "list_projects":
                title = phase == ToolPhase.SUCCESS ? "Listed Projects" : phase == ToolPhase.FAILED ? "Listing Projects Failed" : "Listing Projects";
                break;
            case "read_file":
                title = phase == ToolPhase.SUCCESS ? "Read File" : phase == ToolPhase.FAILED ? "Reading File Failed" : "Reading File";
                break;
            case "write_file":
                title = phase == ToolPhase.SUCCESS ? "Wrote File" : phase == ToolPhase.FAILED ? "Writing File Failed" : "Writing File";
                break;
            case "delete_file":
                title = phase == ToolPhase.SUCCESS ? "Deleted File" : phase == ToolPhase.FAILED ? "Deleting File Failed" : "Deleting File";
                break;
            case "list_files":
                title = phase == ToolPhase.SUCCESS ? "Listed Files" : phase == ToolPhase.FAILED ? "Listing Files Failed" : "Listing Files";
                break;
            case "copy_file":
                title = phase == ToolPhase.SUCCESS ? "Copied File" : phase == ToolPhase.FAILED ? "Copying File Failed" : "Copying File";
                break;
            case "move_file":
                title = phase == ToolPhase.SUCCESS ? "Moved File" : phase == ToolPhase.FAILED ? "Moving File Failed" : "Moving File";
                break;
            case "list_activities":
                title = phase == ToolPhase.SUCCESS ? "Listed Activities" : phase == ToolPhase.FAILED ? "Listing Activities Failed" : "Listing Activities";
                break;
            case "create_activity":
                title = phase == ToolPhase.SUCCESS ? "Created Activity" : phase == ToolPhase.FAILED ? "Creating Activity Failed" : "Creating Activity";
                break;
            case "delete_activity":
                title = phase == ToolPhase.SUCCESS ? "Deleted Activity" : phase == ToolPhase.FAILED ? "Deleting Activity Failed" : "Deleting Activity";
                break;
            case "get_layout":
                title = phase == ToolPhase.SUCCESS ? "Loaded Layout" : phase == ToolPhase.FAILED ? "Loading Layout Failed" : "Loading Layout";
                break;
            case "edit_layout":
                title = phase == ToolPhase.SUCCESS ? "Updated Layout" : phase == ToolPhase.FAILED ? "Updating Layout Failed" : "Updating Layout";
                break;
            case "add_string_resource":
                title = phase == ToolPhase.SUCCESS ? "Added String Resource" : phase == ToolPhase.FAILED ? "Adding String Resource Failed" : "Adding String Resource";
                break;
            case "add_color_resource":
                title = phase == ToolPhase.SUCCESS ? "Added Color Resource" : phase == ToolPhase.FAILED ? "Adding Color Resource Failed" : "Adding Color Resource";
                break;
            case "list_resources":
                title = phase == ToolPhase.SUCCESS ? "Listed Resources" : phase == ToolPhase.FAILED ? "Listing Resources Failed" : "Listing Resources";
                break;
            case "get_compile_logs":
                title = phase == ToolPhase.SUCCESS ? "Loaded Compile Logs" : phase == ToolPhase.FAILED ? "Loading Compile Logs Failed" : "Loading Compile Logs";
                break;
            case "get_project_structure":
                title = phase == ToolPhase.SUCCESS ? "Loaded Project Structure" : phase == ToolPhase.FAILED ? "Loading Project Structure Failed" : "Loading Project Structure";
                break;
            case "build_project":
                title = phase == ToolPhase.SUCCESS ? "Built Project" : phase == ToolPhase.FAILED ? "Building Project Failed" : "Building Project";
                break;
            case "list_libraries":
                title = phase == ToolPhase.SUCCESS ? "Listed Libraries" : phase == ToolPhase.FAILED ? "Listing Libraries Failed" : "Listing Libraries";
                break;
            case "validate_libraries":
                title = phase == ToolPhase.SUCCESS ? "Validated Libraries" : phase == ToolPhase.FAILED ? "Validating Libraries Failed" : "Validating Libraries";
                break;
            case "add_library":
                title = phase == ToolPhase.SUCCESS ? "Added Library" : phase == ToolPhase.FAILED ? "Adding Library Failed" : "Adding Library";
                break;
            case "remove_library":
                title = phase == ToolPhase.SUCCESS ? "Removed Library" : phase == ToolPhase.FAILED ? "Removing Library Failed" : "Removing Library";
                break;
            case "attach_local_library":
                title = phase == ToolPhase.SUCCESS ? "Attached Library" : phase == ToolPhase.FAILED ? "Attaching Library Failed" : "Attaching Library";
                break;
            case "detach_local_library":
                title = phase == ToolPhase.SUCCESS ? "Detached Library" : phase == ToolPhase.FAILED ? "Detaching Library Failed" : "Detaching Library";
                break;
            case "download_dependency":
                title = phase == ToolPhase.SUCCESS ? "Downloaded Dependency" : phase == ToolPhase.FAILED ? "Downloading Dependency Failed" : "Downloading Dependency";
                break;
            case "set_build_compiler":
                title = phase == ToolPhase.SUCCESS ? "Build Compiler Configured" : phase == ToolPhase.FAILED ? "Configuring Build Compiler Failed" : "Configuring Build Compiler";
                break;
            case "build_with_r8":
                title = phase == ToolPhase.SUCCESS ? "R8 Build Completed" : phase == ToolPhase.FAILED ? "R8 Build Failed" : "Building with R8";
                break;
            case "run_shell_command":
                title = phase == ToolPhase.SUCCESS ? "Shell Command Executed" : phase == ToolPhase.FAILED ? "Shell Command Failed" : "Running Shell Command";
                break;
            case "decrypt_project_file":
                title = phase == ToolPhase.SUCCESS ? "File Decrypted" : phase == ToolPhase.FAILED ? "Decryption Failed" : "Decrypting File";
                break;
            case "encrypt_project_file":
                title = phase == ToolPhase.SUCCESS ? "File Encrypted" : phase == ToolPhase.FAILED ? "Encryption Failed" : "Encrypting File";
                break;
            case "generate_layout_from_description":
                title = phase == ToolPhase.SUCCESS ? "Layout Generated" : phase == ToolPhase.FAILED ? "Layout Generation Failed" : "Generating Layout";
                break;
            case "describe_layout_live":
                title = phase == ToolPhase.SUCCESS ? "Layout Described" : phase == ToolPhase.FAILED ? "Layout Read Failed" : "Reading Layout";
                break;
            case "add_view_live":
                title = phase == ToolPhase.SUCCESS ? "View Added (Live)" : phase == ToolPhase.FAILED ? "Adding View Failed" : "Adding View (Live)";
                break;
            case "modify_view_live":
                title = phase == ToolPhase.SUCCESS ? "View Updated (Live)" : phase == ToolPhase.FAILED ? "Updating View Failed" : "Updating View (Live)";
                break;
            case "remove_view_live":
                title = phase == ToolPhase.SUCCESS ? "View Removed (Live)" : phase == ToolPhase.FAILED ? "Removing View Failed" : "Removing View (Live)";
                break;
            case "github_search":
                title = phase == ToolPhase.SUCCESS ? "GitHub Search Completed" : phase == ToolPhase.FAILED ? "GitHub Search Failed" : "Searching GitHub";
                break;
            case "github_compare":
                title = phase == ToolPhase.SUCCESS ? "GitHub Compare Completed" : phase == ToolPhase.FAILED ? "GitHub Compare Failed" : "Comparing on GitHub";
                break;
            default:
                String fallback = humanizeSnakeCase(toolName);
                title = phase == ToolPhase.SUCCESS ? fallback : phase == ToolPhase.FAILED ? fallback + " Failed" : fallback;
                break;
        }
        return subject == null ? title : title + " \"" + subject + "\"";
    }

    @Nullable
    private static String resolveToolSubject(@NonNull String toolName, @Nullable JsonObject arguments,
                                             @Nullable JsonObject successPayload,
                                             @Nullable JsonObject failurePayload) {
        switch (toolName) {
            case "create_project":
                return firstNonBlank(jsonString(successPayload, "app_name"), jsonString(successPayload, "workspace_name"),
                        jsonString(arguments, "app_name"), jsonString(arguments, "project_name"));
            case "duplicate_project":
                return firstNonBlank(jsonString(successPayload, "app_name"), jsonString(arguments, "new_app_name"),
                        prefixedScId(jsonString(successPayload, "sc_id")));
            case "delete_project":
            case "build_project":
            case "get_project_info":
            case "get_project_structure":
            case "get_compile_logs":
            case "validate_libraries":
            case "list_libraries":
            case "list_resources":
            case "list_files":
            case "list_activities":
            case "list_projects":
                return null;
            case "create_activity":
            case "delete_activity":
            case "get_layout":
            case "edit_layout":
                return firstNonBlank(jsonString(successPayload, "activity_name"), jsonString(arguments, "activity_name"));
            case "read_file":
            case "write_file":
            case "delete_file":
                return leafName(firstNonBlank(jsonString(successPayload, "file_path"), jsonString(arguments, "file_path")));
            case "copy_file":
            case "move_file":
                return leafName(firstNonBlank(jsonString(successPayload, "target"), jsonString(arguments, "target_path")));
            case "add_string_resource":
            case "add_color_resource":
                return firstNonBlank(jsonString(successPayload, "key"), jsonString(arguments, "key"));
            case "add_library":
            case "remove_library":
            case "attach_local_library":
            case "detach_local_library":
                return firstNonBlank(jsonString(successPayload, "library_name"), jsonString(arguments, "library_name"));
            case "download_dependency":
                return firstNonBlank(jsonString(successPayload, "dependency"), jsonString(arguments, "dependency"));
            default:
                return firstNonBlank(jsonString(successPayload, "name"), jsonString(failurePayload, "name"));
        }
    }

    @DrawableRes
    private static int resolveToolIcon(@NonNull String toolName) {
        switch (toolName) {
            case "create_project":
            case "delete_project":
            case "duplicate_project":
            case "list_projects":
            case "get_project_info":
                return R.drawable.ic_mtrl_folder;
            case "read_file":
            case "list_files":
                return R.drawable.ic_mtrl_file;
            case "write_file":
                return R.drawable.ic_mtrl_save;
            case "delete_file":
                return R.drawable.ic_mtrl_delete;
            case "copy_file":
            case "move_file":
                return R.drawable.ic_mtrl_database_moved;
            case "list_activities":
            case "create_activity":
            case "delete_activity":
                return R.drawable.ic_mtrl_screen;
            case "get_layout":
            case "edit_layout":
                return R.drawable.ic_mtrl_design;
            case "add_string_resource":
                return R.drawable.ic_mtrl_text_change;
            case "add_color_resource":
                return R.drawable.ic_mtrl_pick_color;
            case "list_resources":
                return R.drawable.ic_mtrl_palette;
            case "get_compile_logs":
                return R.drawable.ic_mtrl_bug_report;
            case "get_project_structure":
                return R.drawable.ic_mtrl_folder_code;
            case "build_project":
                return R.drawable.ic_mtrl_deployed_code;
            case "list_libraries":
            case "add_library":
            case "remove_library":
            case "attach_local_library":
            case "detach_local_library":
                return R.drawable.ic_mtrl_package;
            case "download_dependency":
                return R.drawable.ic_mtrl_download;
            case "validate_libraries":
                return R.drawable.ic_mtrl_verified_user;
            case "set_build_compiler":
            case "build_with_r8":
                return R.drawable.ic_mtrl_deployed_code;
            case "run_shell_command":
            case "execute_shell":
                return R.drawable.ic_mtrl_terminal;
            case "decrypt_project_file":
            case "encrypt_project_file":
                return R.drawable.ic_mtrl_shield_lock;
            case "generate_layout_from_description":
            case "describe_layout_live":
            case "add_view_live":
            case "modify_view_live":
            case "remove_view_live":
                return R.drawable.ic_mtrl_design;
            case "github_search":
            case "github_compare":
                return R.drawable.ic_mtrl_code;
            default:
                return R.drawable.ic_tool_call;
        }
    }

    @DrawableRes
    private static int resolveStatusIcon(@NonNull ToolPhase phase) {
        switch (phase) {
            case SUCCESS:
                return R.drawable.ic_mtrl_check;
            case FAILED:
                return R.drawable.ic_mtrl_warning;
            case RUNNING:
            default:
                return R.drawable.ic_mtrl_sync;
        }
    }

    @NonNull
    private static String humanizeSnakeCase(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return "Tool";
        }
        String[] parts = value.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? "Tool" : builder.toString();
    }

    @Nullable
    private static String jsonString(@Nullable JsonObject object, @NonNull String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        String value = object.get(key).getAsString();
        return trimToNull(value);
    }

    @Nullable
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    @Nullable
    private static String prefixedScId(@Nullable String scId) {
        String value = trimToNull(scId);
        return value == null ? null : "#" + value;
    }

    @Nullable
    private static String leafName(@Nullable String path) {
        String value = trimToNull(path);
        if (value == null) {
            return null;
        }
        int slashIndex = value.lastIndexOf('/');
        int colonIndex = value.lastIndexOf(':');
        int separatorIndex = Math.max(slashIndex, colonIndex);
        return separatorIndex >= 0 ? value.substring(separatorIndex + 1) : value;
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Decodes Base64-encoded text that sometimes arrives from AI API responses.
     * Returns the original text unchanged if it is not valid Base64.
     */
    private static String decodeIfBase64(@Nullable String text) {
        if (text == null || text.length() < 8) return text != null ? text : "";
        String trimmed = text.trim();
        boolean looksBase64 = trimmed.length() > 20
                && trimmed.matches("[A-Za-z0-9+/=]+")
                && !trimmed.contains(" ")
                && !trimmed.contains("\n");
        if (!looksBase64) return text;
        try {
            byte[] decoded = Base64.decode(trimmed, Base64.DEFAULT);
            String decodedStr = new String(decoded, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (decodedStr.length() > 2 && isPrintable(decodedStr)) {
                return decodedStr;
            }
        } catch (Exception ignored) {}
        return text;
    }

    private static boolean isPrintable(String s) {
        int printable = 0;
        for (int i = 0; i < Math.min(s.length(), 100); i++) {
            char c = s.charAt(i);
            if (c >= 32 || c == '\n' || c == '\r' || c == '\t') printable++;
        }
        return (double) printable / Math.min(s.length(), 100) > 0.85;
    }

    // ── Message action helpers ────────────────────────────────────────────

    private static void copyToClipboard(@NonNull Context context, @NonNull String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("AI Message", text));
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private static void shareText(@NonNull Context context, @NonNull String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(intent, "Share message"));
    }

    private static void showMessageOptions(@NonNull Context context, @NonNull String text) {
        new MaterialAlertDialogBuilder(context)
                .setItems(new String[]{"Copy", "Share"}, (d, i) -> {
                    if (i == 0) copyToClipboard(context, text);
                    else shareText(context, text);
                })
                .show();
    }

    // ── UserViewHolder ────────────────────────────────────────────────────

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageUserBinding binding;

        UserViewHolder(@NonNull ItemChatMessageUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ChatItem item) {
            String content = item.message != null ? item.message.getContent() : "";
            String text = content != null ? content : "";
            binding.messageContent.setText(text);
            binding.messageMeta.setText(item.message != null ? formatTimestamp(item.message.getTimestamp()) : "");

            binding.btnCopyMessage.setOnClickListener(v -> copyToClipboard(v.getContext(), text));

            binding.messageContent.setOnLongClickListener(v -> {
                showMessageOptions(v.getContext(), text);
                return true;
            });
        }
    }

    // ── AssistantViewHolder ───────────────────────────────────────────────

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageAssistantBinding binding;

        AssistantViewHolder(@NonNull ItemChatMessageAssistantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ChatItem item, @NonNull Markwon markwon,
                  @Nullable OnArtifactActionListener artifactActionListener) {
            ChatMessage message = item.message;
            String content = message != null ? message.getContent() : "";
            binding.messageMeta.setText(message != null ? formatTimestamp(message.getTimestamp()) : "");
            binding.streamingBadge.setVisibility(message != null && message.isStreaming() ? View.VISIBLE : View.GONE);

            // Reasoning / thinking card
            String thoughtSummary = extractThoughtSummary(item);
            boolean hasThoughtSummary = !TextUtils.isEmpty(thoughtSummary);
            binding.reasoningCard.setVisibility(hasThoughtSummary ? View.VISIBLE : View.GONE);
            if (hasThoughtSummary) {
                binding.reasoningSummary.setText(thoughtSummary);
                binding.reasoningDetail.setText(thoughtSummary);
            }

            boolean hasMessageContent = !TextUtils.isEmpty(content) && item.toolStates.isEmpty();
            binding.messageContent.setVisibility(hasMessageContent ? View.VISIBLE : View.GONE);
            binding.messageContent.setTextIsSelectable(true);

            // ── Expand / collapse logic (now driven by btn_expand_message in actions row) ──
            // The floating btn_expand_message inside the bubble is REMOVED from the layout.
            // expand is wired below via the actions row btn_expand_message.
            if (hasMessageContent) {
                boolean isCurrentlyStreaming = message != null && message.isStreaming();
                String displayContent = decodeIfBase64(content);

                if (isCurrentlyStreaming) {
                    binding.messageContent.setMaxLines(Integer.MAX_VALUE);
                    binding.messageContent.setEllipsize(null);
                    binding.messageContent.setText(displayContent);
                } else {
                    markwon.setMarkdown(binding.messageContent, displayContent);
                    applyExpandState(item);
                }
            }

            // Tool cards
            binding.toolsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(binding.getRoot().getContext());
            int cardSpacing = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 10,
                    binding.getRoot().getResources().getDisplayMetrics());
            for (int i = 0; i < item.toolStates.size(); i++) {
                ToolUiState state = item.toolStates.get(i);
                ItemChatToolCallBinding toolBinding = ItemChatToolCallBinding.inflate(inflater, binding.toolsContainer, false);
                View toolView = toolBinding.getRoot();
                ViewGroup.LayoutParams lp = toolView.getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).topMargin = i == 0 ? 0 : cardSpacing;
                    toolView.setLayoutParams(lp);
                }
                bindToolCard(toolBinding, state, artifactActionListener);
                binding.toolsContainer.addView(toolView);
            }
            binding.toolsContainer.setVisibility(item.toolStates.isEmpty() ? View.GONE : View.VISIBLE);

            // Actions row
            boolean showActions = hasMessageContent && (message == null || !message.isStreaming());
            binding.messageActionsRow.setVisibility(showActions ? View.VISIBLE : View.GONE);

            if (showActions) {
                String finalContent = decodeIfBase64(content);

                // Copy
                binding.btnCopyMessage.setOnClickListener(v ->
                        copyToClipboard(v.getContext(), finalContent));

                // Share
                binding.btnShareMessage.setOnClickListener(v ->
                        shareText(v.getContext(), finalContent));

                // Expand — wired to btn_expand_message in the actions row
                // Shows after layout pass to know line count
                binding.messageContent.post(() -> {
                    android.text.Layout layout = binding.messageContent.getLayout();
                    boolean needsExpand = layout != null && layout.getLineCount() >= 8;
                    binding.btnExpandMessage.setVisibility(needsExpand || item.messageExpanded ? View.VISIBLE : View.GONE);
                    binding.labelExpand.setVisibility(needsExpand || item.messageExpanded ? View.VISIBLE : View.GONE);
                    updateExpandIcon(item);
                });

                binding.btnExpandMessage.setOnClickListener(v -> {
                    item.messageExpanded = !item.messageExpanded;
                    applyExpandState(item);
                    updateExpandIcon(item);
                    // Animate icon rotation: collapsed=0°, expanded=180°
                    float rotation = item.messageExpanded ? 180f : 0f;
                    binding.btnExpandMessage.animate().rotation(rotation).setDuration(200).start();
                });

                // Long-press for options dialog
                binding.messageContent.setOnLongClickListener(v -> {
                    showMessageOptions(v.getContext(), finalContent);
                    return true;
                });
            }
        }

        /** Apply max-lines and ellipsize based on item.messageExpanded */
        private void applyExpandState(@NonNull ChatItem item) {
            if (item.messageExpanded) {
                binding.messageContent.setMaxLines(Integer.MAX_VALUE);
                binding.messageContent.setEllipsize(null);
            } else {
                binding.messageContent.setMaxLines(8);
                binding.messageContent.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
        }

        /** Sync the expand icon drawable direction */
        private void updateExpandIcon(@NonNull ChatItem item) {
            binding.btnExpandMessage.setImageResource(
                    item.messageExpanded
                            ? R.drawable.ic_mtrl_arrow_up
                            : R.drawable.ic_mtrl_expand);
        }

        @Nullable
        private String extractThoughtSummary(@NonNull ChatItem item) {
            for (ToolUiState state : item.toolStates) {
                ToolCall toolCall = state.toolCall;
                if (toolCall != null && !TextUtils.isEmpty(toolCall.getThoughtSignature())) {
                    return toolCall.getThoughtSignature();
                }
            }
            if (item.message != null && item.message.getToolCalls() != null) {
                for (ToolCall toolCall : item.message.getToolCalls()) {
                    if (toolCall != null && !TextUtils.isEmpty(toolCall.getThoughtSignature())) {
                        return toolCall.getThoughtSignature();
                    }
                }
            }
            return null;
        }

        private void bindToolCard(@NonNull ItemChatToolCallBinding toolBinding,
                                  @NonNull ToolUiState state,
                                  @Nullable OnArtifactActionListener artifactActionListener) {
            ToolVisualSpec visualSpec = buildToolVisualSpec(state);
            toolBinding.toolName.setText(visualSpec.title);
            toolBinding.toolIcon.setImageResource(visualSpec.toolIconRes);
            toolBinding.toolStatusIcon.setImageResource(visualSpec.statusIconRes);
            toolBinding.toolPreview.setText("");
            toolBinding.toolPreview.setVisibility(View.GONE);
            toolBinding.toolDetails.setText(visualSpec.details);
            toolBinding.toolDetails.setVisibility(state.expanded ? View.VISIBLE : View.GONE);
            toolBinding.expandIcon.setRotation(state.expanded ? 180f : 0f);

            ToolResult result = state.toolResult;
            LinearProgressIndicator progress = toolBinding.toolProgress;
            if (result == null || state.indeterminate || (state.progress >= 0 && state.progress < 100)) {
                progress.setVisibility(View.VISIBLE);
                progress.setIndeterminate(result == null || state.indeterminate || state.progress < 0);
                if (!progress.isIndeterminate() && state.progress >= 0) {
                    progress.setProgress(state.progress);
                }
            } else {
                progress.setVisibility(View.GONE);
            }

            MaterialButton installButton = toolBinding.btnInstallArtifact;
            String artifactPath = extractInstallableArtifactPath(result);
            if (!TextUtils.isEmpty(artifactPath) && artifactActionListener != null && state.expanded) {
                installButton.setVisibility(View.VISIBLE);
                installButton.setOnClickListener(v -> artifactActionListener.onInstallArtifact(artifactPath));
            } else {
                installButton.setVisibility(View.GONE);
                installButton.setOnClickListener(null);
            }

            toolBinding.getRoot().setOnClickListener(v -> {
                state.expanded = !state.expanded;
                bindToolCard(toolBinding, state, artifactActionListener);
            });
        }
    }
}
