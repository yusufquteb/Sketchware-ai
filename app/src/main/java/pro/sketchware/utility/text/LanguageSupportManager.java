package pro.sketchware.utility.text;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

public final class LanguageSupportManager {
    private final Map<String, LanguageDefinition> byExtension = new HashMap<>();

    public LanguageSupportManager() {
        register(new LanguageDefinition("java", set("java"), set("abstract","boolean","break","case","catch","class","const","continue","default","do","double","else","enum","extends","final","finally","float","for","if","implements","import","instanceof","int","interface","long","new","package","private","protected","public","return","static","super","switch","this","throw","throws","try","void","while"), "//"));
        register(new LanguageDefinition("kotlin", set("kt","kts"), set("class","object","fun","val","var","when","if","else","for","while","return","package","import","interface","sealed","data","inline","suspend"), "//"));
        register(new LanguageDefinition("xml", set("xml"), Collections.emptySet(), ""));
        register(new LanguageDefinition("json", set("json"), set("true","false","null"), ""));
    }

    public void register(LanguageDefinition definition) {
        for (String ext : definition.extensions) byExtension.put(ext.toLowerCase(Locale.US), definition);
    }

    public LanguageDefinition forFileName(String fileName) {
        if (fileName == null) return null;
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) return null;
        return byExtension.get(fileName.substring(idx + 1).toLowerCase(Locale.US));
    }

    private static HashSet<String> set(String... values) { return new HashSet<>(Arrays.asList(values)); }
}
