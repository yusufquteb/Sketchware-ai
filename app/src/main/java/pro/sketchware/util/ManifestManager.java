package pro.sketchware.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ManifestManager provides a safe way to modify AndroidManifest.xml.
 * It allows adding permissions, activities, and other components dynamically.
 */
public class ManifestManager {

    public static boolean addPermission(File manifestFile, String permission) {
        if (permission == null || permission.isEmpty()) return false;
        
        try {
            List<String> lines = readFile(manifestFile);
            for (String line : lines) {
                if (line.contains("android:name=\"" + permission + "\"")) {
                    return true; // Permission already exists
                }
            }

            // Insert permission before closing </manifest> tag
            int insertIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("</manifest>")) {
                    insertIndex = i;
                    break;
                }
            }

            if (insertIndex != -1) {
                lines.add(insertIndex, "    <uses-permission android:name=\"" + permission + "\" />");
                writeFile(manifestFile, lines);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean addActivity(File manifestFile, String activityName, String label, String icon) {
        try {
            List<String> lines = readFile(manifestFile);
            for (String line : lines) {
                if (line.contains("android:name=\"" + activityName + "\"")) {
                    return true; // Activity already exists
                }
            }

            String activityTag = "    <activity\n" +
                    "        android:name=\"" + activityName + "\"\n" +
                    "        android:label=\"" + (label != null ? label : "") + "\"\n" +
                    "        android:icon=\"" + (icon != null ? icon : "") + "\"\n" +
                    "        android:exported=\"false\">\n" +
                    "    </activity>";

            int insertIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("</application>")) {
                    insertIndex = i;
                    break;
                }
            }

            if (insertIndex != -1) {
                lines.add(insertIndex, activityTag);
                writeFile(manifestFile, lines);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static List<String> readFile(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static void writeFile(File file, List<String> lines) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }
}
