package pro.sketchware.library;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans a local library folder and extracts the real Maven coordinate
 * by reading embedded metadata from classes.jar / classes.aar.
 *
 * Priority order:
 *   1. META-INF/maven/{g}/{a}/pom.properties  ← most reliable, present in 90%+ of Maven libs
 *   2. META-INF/MANIFEST.MF (Bundle-SymbolicName + Implementation-Version)
 *   3. AndroidManifest.xml (package + versionName)
 *   4. Fallback to LibraryVersionChecker.resolveCoordinate(folderName)
 */
public class LibraryFolderScanner {

    private static final String TAG = "LibFolderScanner";

    public static class ScanResult {
        public final String groupId;
        public final String artifactId;
        public final String version;
        public final String source; // where the info came from

        ScanResult(String g, String a, String v, String src) {
            groupId = g; artifactId = a; version = v; source = src;
        }

        /** Returns the full Maven coordinate "groupId:artifactId:version" */
        public String toCoordinate() {
            return groupId + ":" + artifactId + ":" + version;
        }

        @Override public String toString() {
            return toCoordinate() + "  [" + source + "]";
        }
    }

    /**
     * Scans the library folder at the given path and returns the best
     * Maven coordinate found, or null if nothing could be determined.
     */
    @Nullable
    public static ScanResult scan(String libFolderPath) {
        File folder = new File(libFolderPath);
        if (!folder.isDirectory()) return null;

        // ── 1. Try classes.jar (most common) ─────────────────────────────────
        ScanResult r = scanJar(new File(folder, "classes.jar"));
        if (r != null) return r;

        // ── 2. Try classes.aar (some libs ship as AAR) ────────────────────────
        r = scanJar(new File(folder, "classes.aar"));
        if (r != null) return r;

        // ── 3. Scan any other .jar files in the folder ───────────────────────
        File[] files = folder.listFiles(f -> f.getName().endsWith(".jar"));
        if (files != null) {
            for (File f : files) {
                r = scanJar(f);
                if (r != null) return r;
            }
        }

        // ── 4. Fallback: AndroidManifest.xml package + version ───────────────
        r = scanManifest(new File(folder, "AndroidManifest.xml"),
                         folder.getName());
        if (r != null) return r;

        // ── 5. Last resort: name-based lookup table ───────────────────────────
        String coord = LibraryVersionChecker.resolveCoordinate(folder.getName());
        if (coord != null) {
            String[] p = coord.split(":");
            if (p.length >= 3)
                return new ScanResult(p[0], p[1], p[2], "name-lookup");
        }

        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JAR scanning
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    private static ScanResult scanJar(File jarFile) {
        if (!jarFile.exists()) return null;
        try (ZipFile zip = new ZipFile(jarFile)) {

            // ── Pass 1: pom.properties ────────────────────────────────────────
            ScanResult r = findPomProperties(zip);
            if (r != null) return r;

            // ── Pass 2: MANIFEST.MF ───────────────────────────────────────────
            r = findManifest(zip);
            if (r != null) return r;

        } catch (Exception e) {
            Log.w(TAG, "Failed to scan " + jarFile.getName() + ": " + e.getMessage());
        }
        return null;
    }

    /** Looks for META-INF/maven/{groupId}/{artifactId}/pom.properties */
    @Nullable
    private static ScanResult findPomProperties(ZipFile zip) {
        try {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("META-INF/maven/") || !name.endsWith("/pom.properties"))
                    continue;

                // Path: META-INF/maven/{groupId}/{artifactId}/pom.properties
                // Extract groupId & artifactId from the path itself (more reliable than file content)
                String[] parts = name.split("/");
                // parts[0]=META-INF, parts[1]=maven, parts[2]=groupId, parts[3]=artifactId, parts[4]=pom.properties
                // But groupId can contain dots-as-slashes, so rejoin parts[2..n-2]
                // Actually Maven layout uses dots-as-directories for groupId
                String pathGroupId   = parts.length > 3 ? parts[2] : null;
                String pathArtifact  = parts.length > 4 ? parts[3] : null;

                // Also read the file itself for the authoritative values
                try (InputStream is = zip.getInputStream(entry)) {
                    Properties props = new Properties();
                    props.load(is);
                    String g = props.getProperty("groupId",   pathGroupId);
                    String a = props.getProperty("artifactId", pathArtifact);
                    String v = props.getProperty("version");
                    if (g != null && a != null && v != null
                            && !g.isEmpty() && !a.isEmpty() && !v.isEmpty()) {
                        return new ScanResult(g, a, v, "pom.properties");
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "pom.properties scan failed: " + e.getMessage());
        }
        return null;
    }

    /** Looks for META-INF/MANIFEST.MF with Bundle-SymbolicName / Implementation-* */
    @Nullable
    private static ScanResult findManifest(ZipFile zip) {
        try {
            ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
            if (entry == null) return null;
            try (InputStream is = zip.getInputStream(entry)) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is));
                String line;
                String bundleSymbolic = null, bundleVersion = null;
                String implTitle = null, implVersion = null;
                String groupId = null;

                while ((line = br.readLine()) != null) {
                    if (line.startsWith("Bundle-SymbolicName:")) {
                        // e.g. "com.squareup.okhttp3" or "com.squareup.okhttp3; singleton:=true"
                        bundleSymbolic = line.substring(20).trim().split(";")[0].trim();
                    } else if (line.startsWith("Bundle-Version:")) {
                        bundleVersion = line.substring(15).trim().split("[. ]")[0]
                            + extractDotVersion(line.substring(15).trim());
                        bundleVersion = line.substring(15).trim().split(";")[0].trim();
                    } else if (line.startsWith("Implementation-Title:")) {
                        implTitle = line.substring(21).trim();
                    } else if (line.startsWith("Implementation-Version:")) {
                        implVersion = line.substring(23).trim();
                    } else if (line.startsWith("Implementation-Vendor-Id:")) {
                        groupId = line.substring(25).trim();
                    } else if (line.startsWith("Automatic-Module-Name:")) {
                        // e.g. "com.squareup.okhttp3" → useful as groupId
                        if (groupId == null) groupId = line.substring(22).trim();
                    }
                }

                // Prefer Bundle-SymbolicName as groupId.artifactId
                if (bundleSymbolic != null && bundleVersion != null) {
                    String[] bParts = splitGroupArtifact(bundleSymbolic);
                    if (bParts != null)
                        return new ScanResult(bParts[0], bParts[1], bundleVersion, "MANIFEST.MF");
                }
                // Fallback: Implementation-* headers
                if (groupId != null && implTitle != null && implVersion != null) {
                    return new ScanResult(groupId, implTitle, implVersion, "MANIFEST.MF");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MANIFEST.MF scan failed: " + e.getMessage());
        }
        return null;
    }

    /** Tries to read package + versionName from AndroidManifest.xml (binary XML = not parseable here) */
    @Nullable
    private static ScanResult scanManifest(File manifestFile, String folderName) {
        // Binary XML can't be easily parsed without Android framework,
        // but the package name is often readable as a UTF-8 string in the binary
        if (!manifestFile.exists()) return null;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(manifestFile.toPath());
            String raw = new String(bytes, "UTF-8");
            // Look for package name pattern (com.xxx.xxx) in the binary blob
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*){2,})")
                    .matcher(raw);
            while (m.find()) {
                String pkg = m.group(1);
                // Skip Android SDK packages and common noise
                if (pkg.startsWith("android.") || pkg.startsWith("java.")
                    || pkg.startsWith("dalvik.") || pkg.length() < 10) continue;
                // Use folder name for version
                String version = LibraryVersionChecker.extractVersionFromName(folderName);
                if (version != null)
                    return new ScanResult(pkg, inferArtifactId(pkg, folderName), version, "AndroidManifest");
                break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Manifest scan failed: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Splits "com.squareup.okhttp3" into ["com.squareup.okhttp3", "okhttp3"].
     * For "com.example.mylibrary" → ["com.example", "mylibrary"].
     */
    @Nullable
    private static String[] splitGroupArtifact(String bundleSymbolicName) {
        int lastDot = bundleSymbolicName.lastIndexOf('.');
        if (lastDot < 0) return null;
        String group    = bundleSymbolicName.substring(0, lastDot);
        String artifact = bundleSymbolicName.substring(lastDot + 1);
        if (group.isEmpty() || artifact.isEmpty()) return null;
        return new String[]{group, artifact};
    }

    private static String inferArtifactId(String pkg, String folderName) {
        // Use the last segment of package or the folder base name
        String folderBase = folderName.replaceAll("[_-][Vv]?\\d.*$", "").toLowerCase();
        if (!folderBase.isEmpty()) return folderBase;
        return pkg.substring(pkg.lastIndexOf('.') + 1);
    }

    private static String extractDotVersion(String raw) {
        // Normalize "1.2.3.RELEASE" → "1.2.3"
        return raw.replaceAll("[^0-9.].*$", "").replaceAll("\\.$", "");
    }

    /**
     * Scans all subdirectories of the given root directory for library artifacts,
     * returning metadata for each found library.
     *
     * @param root Directory containing library sub-folders (e.g. local_libs root).
     * @return List of LocalLibraryMetadata; empty if root doesn't exist or has no libs.
     */
    public static List<LocalLibraryMetadata> scan(File root) {
        List<LocalLibraryMetadata> result = new ArrayList<>();
        if (root == null || !root.isDirectory()) return result;
        File[] entries = root.listFiles();
        if (entries == null) return result;
        for (File entry : entries) {
            if (!entry.isDirectory()) continue;
            LocalLibraryMetadata meta = new LocalLibraryMetadata();
            meta.name = entry.getName();
            meta.root = entry;
            // Try classes.jar then classes.aar as the primary artifact
            File jar = new File(entry, "classes.jar");
            File aar = new File(entry, "classes.aar");
            if (jar.isFile())      meta.artifact = jar;
            else if (aar.isFile()) meta.artifact = aar;
            // Try to extract version from embedded Maven metadata
            ScanResult sr = scan(entry.getAbsolutePath());
            if (sr != null) {
                meta.version = sr.version;
                meta.id      = sr.groupId + ":" + sr.artifactId;
            } else {
                meta.version = extractVersionFromName(entry.getName());
                meta.id      = extractBaseFromName(entry.getName());
            }
            result.add(meta);
        }
        return result;
    }

    private static String extractVersionFromName(String name) {
        if (name == null) return "";
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)+)$").matcher(name);
        return m.find() ? m.group(1) : "";
    }

    private static String extractBaseFromName(String name) {
        if (name == null) return "";
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)+)$").matcher(name);
        if (!m.find()) return name;
        return name.substring(0, name.length() - m.group(1).length()).replaceAll("[-_]$", "");
    }
}
