package pro.sketchware.library;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BuiltInLibraryRegistry {
    private final Map<String, String> coordinates = new LinkedHashMap<>();

    public void register(String id, String coordinate) {
        if (id != null && !id.trim().isEmpty()) coordinates.put(id.trim(), coordinate == null ? "" : coordinate.trim());
    }

    public String coordinateOf(String id) { return coordinates.getOrDefault(id, ""); }
    public Map<String, String> all() { return Collections.unmodifiableMap(coordinates); }
}
