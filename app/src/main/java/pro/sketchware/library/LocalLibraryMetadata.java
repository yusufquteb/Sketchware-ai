package pro.sketchware.library;

import java.io.File;

public final class LocalLibraryMetadata {
    public String id = "";
    public String name = "";
    public String version = "";
    public File root;
    public File artifact;

    public boolean isValid() { return artifact != null && artifact.isFile() && (artifact.getName().endsWith(".jar") || artifact.getName().endsWith(".aar")); }
}
