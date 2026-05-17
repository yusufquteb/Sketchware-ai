package pro.sketchware.settings;

import java.util.LinkedHashSet;
import java.util.Set;

public final class PermissionSettingsManager {
    private final ProjectSettingsStore store;
    public PermissionSettingsManager(ProjectSettingsStore store) { this.store = store; }
    public void setPermissions(Set<String> permissions) {
        StringBuilder out = new StringBuilder();
        if (permissions != null) {
            for (String permission : permissions) {
                if (permission == null) continue;
                String trimmed = permission.trim();
                if (!trimmed.isEmpty()) out.append(trimmed).append('\n');
            }
        }
        store.putString("permissions", out.toString().trim());
    }
    public Set<String> getPermissions() {
        String raw = store.getString("permissions", "");
        Set<String> out = new LinkedHashSet<>();
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }
}
