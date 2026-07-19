package pro.sketchware.util;

import pro.sketchware.activities.projecttools.ProjectToolPaths;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class GradleInjectionManager {

    private static final String FILE_APP = "app.gradle.injection";
    private static final String FILE_PROJECT = "project.gradle.injection";
    private static final String FILE_PROPERTIES = "properties.gradle.injection";

    public static String readAppGradleInject(String scId) {
        return readFile(getInjectionFile(scId, FILE_APP));
    }

    public static String readProjectGradleInject(String scId) {
        return readFile(getInjectionFile(scId, FILE_PROJECT));
    }

    public static String readPropertiesInject(String scId) {
        return readFile(getInjectionFile(scId, FILE_PROPERTIES));
    }

    public static void writeAppGradleInject(String scId, String content) {
        writeFile(getInjectionFile(scId, FILE_APP), content);
    }

    public static void writeProjectGradleInject(String scId, String content) {
        writeFile(getInjectionFile(scId, FILE_PROJECT), content);
    }

    public static void writePropertiesInject(String scId, String content) {
        writeFile(getInjectionFile(scId, FILE_PROPERTIES), content);
    }

    private static File getInjectionFile(String scId, String fileName) {
        File dir = ProjectToolPaths.getProjectGradleInjectionDir(scId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, fileName);
    }

    private static String readFile(File file) {
        if (!file.exists() || !file.isFile()) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(file.toPath())).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeFile(File file, String content) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content != null ? content : "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String appendIfPresent(String original, String injection) {
        if (injection == null || injection.trim().isEmpty()) {
            return original;
        }
        return original + "\n" + injection;
    }
}
