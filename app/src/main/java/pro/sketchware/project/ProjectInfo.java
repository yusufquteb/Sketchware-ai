package pro.sketchware.project;

import java.io.File;

public final class ProjectInfo {
    public final File root;
    public final boolean hasGradle;
    public final boolean hasAndroidManifest;
    public final boolean hasSource;
    public final long lastModified;

    public ProjectInfo(File root) {
        this.root = root;
        this.hasGradle = new File(root, "build.gradle").isFile() || new File(root, "app/build.gradle").isFile();
        this.hasAndroidManifest = new File(root, "app/src/main/AndroidManifest.xml").isFile() || new File(root, "AndroidManifest.xml").isFile();
        this.hasSource = new File(root, "app/src/main/java").isDirectory() || new File(root, "src/main/java").isDirectory();
        this.lastModified = root == null ? 0 : root.lastModified();
    }
}
