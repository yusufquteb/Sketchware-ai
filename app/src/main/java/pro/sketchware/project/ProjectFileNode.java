package pro.sketchware.project;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProjectFileNode {
    public final File file;
    public final List<ProjectFileNode> children = new ArrayList<>();

    public ProjectFileNode(File file) { this.file = file; }
    public boolean isDirectory() { return file != null && file.isDirectory(); }
    public List<ProjectFileNode> children() { return Collections.unmodifiableList(children); }
}
