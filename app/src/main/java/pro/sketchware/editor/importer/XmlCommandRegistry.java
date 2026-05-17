package pro.sketchware.editor.importer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class XmlCommandRegistry {
    private final Map<String, String> commands = new LinkedHashMap<>();
    public void register(String name, String template) { if (name != null && !name.isEmpty()) commands.put(name, template == null ? "" : template); }
    public String template(String name) { return commands.getOrDefault(name, ""); }
    public Map<String, String> all() { return Collections.unmodifiableMap(commands); }
}
