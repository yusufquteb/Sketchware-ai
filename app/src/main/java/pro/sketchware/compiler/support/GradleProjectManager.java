package pro.sketchware.compiler.support;

import java.io.File;
import java.util.regex.Pattern;

import pro.sketchware.utility.io.SafeFileOps;

public final class GradleProjectManager {
    private GradleProjectManager() {}

    public static void upsertDependency(File buildGradle, String dependencyLine) throws Exception {
        String content = buildGradle.exists() ? SafeFileOps.readUtf8(buildGradle) : "plugins { id 'com.android.application' }\n\ndependencies {\n}\n";
        if (content.contains(dependencyLine)) return;
        int idx = content.lastIndexOf("dependencies");
        if (idx < 0) content += "\ndependencies {\n    " + dependencyLine + "\n}\n";
        else {
            int brace = content.indexOf('{', idx);
            content = content.substring(0, brace + 1) + "\n    " + dependencyLine + content.substring(brace + 1);
        }
        SafeFileOps.writeUtf8Atomic(buildGradle, content);
    }

    public static void setApplicationId(File buildGradle, String applicationId) throws Exception {
        String content = SafeFileOps.readUtf8(buildGradle);
        String replacement = "applicationId = \"" + applicationId + "\"";
        content = Pattern.compile("applicationId\\s*=\\s*\"[^\"]*\"").matcher(content).replaceAll(replacement);
        SafeFileOps.writeUtf8Atomic(buildGradle, content);
    }
}
