package pro.sketchware.resources;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.utility.io.SafeFileOps;

public final class ResourceManager {
    private final File resDir;
    private final File assetsDir;
    private final File jniLibsDir;

    public ResourceManager(File srcMainDir) {
        this.resDir = new File(srcMainDir, "res");
        this.assetsDir = new File(srcMainDir, "assets");
        this.jniLibsDir = new File(srcMainDir, "jniLibs");
    }

    public List<File> assets() throws Exception { return list(assetsDir); }
    public List<File> nativeLibraries() throws Exception { return list(jniLibsDir); }
    public List<File> rawResources() throws Exception { return list(resDir); }
    public void importAsset(File source, String name) throws Exception { copy(source, new File(assetsDir, name)); }
    public void importNativeLibrary(File source, String abi) throws Exception { copy(source, new File(new File(jniLibsDir, abi), source.getName())); }

    private List<File> list(File dir) throws Exception { return dir.exists() ? SafeFileOps.listFilesRecursively(dir) : new ArrayList<>(); }
    private void copy(File source, File target) throws Exception { SafeFileOps.ensureParent(target); java.nio.file.Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
}
