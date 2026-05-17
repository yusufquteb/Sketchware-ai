package pro.sketchware.editor.importer;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CustomComponentRegistry {
    private final Set<String> classes = new LinkedHashSet<>();
    public void add(String className) { if (className != null && className.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) classes.add(className); }
    public boolean contains(String className) { return classes.contains(className); }
    public Set<String> all() { return Collections.unmodifiableSet(classes); }
}
