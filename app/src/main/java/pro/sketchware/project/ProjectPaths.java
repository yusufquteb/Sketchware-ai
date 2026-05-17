package pro.sketchware.project;

import java.io.File;

public final class ProjectPaths {
    public final File root;
    public final File app;
    public final File srcMain;
    public final File javaDir;
    public final File resDir;
    public final File assetsDir;

    public ProjectPaths(File root) {
        this.root = root;
        this.app = new File(root, "app");
        this.srcMain = new File(app, "src/main");
        this.javaDir = new File(srcMain, "java");
        this.resDir = new File(srcMain, "res");
        this.assetsDir = new File(srcMain, "assets");
    }
}
