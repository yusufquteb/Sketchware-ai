package pro.sketchware.editor.palette;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialComponentRegistry {
    private final Map<String, String> components = new LinkedHashMap<>();

    public MaterialComponentRegistry() {
        register("Chip", "com.google.android.material.chip.Chip");
        register("ExtendedFloatingActionButton", "com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton");
        register("MaterialDivider", "com.google.android.material.divider.MaterialDivider");
        register("NavigationRailView", "com.google.android.material.navigationrail.NavigationRailView");
        register("SearchBar", "com.google.android.material.search.SearchBar");
        register("Slider", "com.google.android.material.slider.Slider");
        register("RangeSlider", "com.google.android.material.slider.RangeSlider");
    }

    public void register(String displayName, String className) { components.put(displayName, className); }
    public Map<String, String> all() { return Collections.unmodifiableMap(components); }
}
