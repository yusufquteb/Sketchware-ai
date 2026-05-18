package pro.sketchware.utility.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class SafeFileOps {
    private static final int BUFFER_SIZE = 64 * 1024;

    private SafeFileOps() {}

    public static void ensureDirectory(File directory) throws IOException {
        if (directory == null) throw new IOException("Directory is null");
        if (directory.exists()) {
            if (!directory.isDirectory()) throw new IOException("Path is not a directory: " + directory);
            return;
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create directory: " + directory);
        }
    }

    public static void ensureParent(File file) throws IOException {
        File parent = file == null ? null : file.getParentFile();
        if (parent != null) ensureDirectory(parent);
    }

    public static String readUtf8(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        return new String(data, StandardCharsets.UTF_8);
    }

    public static void writeUtf8Atomic(File file, String content) throws IOException {
        Objects.requireNonNull(file, "file");
        ensureParent(file);
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp." + System.nanoTime());
        Files.write(tmp.toPath(), (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void copyTree(File source, File target) throws IOException {
        if (source == null || !source.exists()) throw new IOException("Source does not exist: " + source);
        final Path src = source.toPath();
        final Path dst = target.toPath();
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path out = dst.resolve(src.relativize(file));
                Files.createDirectories(out.getParent());
                Files.copy(file, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static int deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) return 0;
        final int[] count = {0};
        Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(f); count[0]++; return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.deleteIfExists(dir); count[0]++; return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    public static List<File> listFilesRecursively(File root) throws IOException {
        if (root == null || !root.exists()) return Collections.emptyList();
        List<File> out = new ArrayList<>();
        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                out.add(file.toFile()); return FileVisitResult.CONTINUE;
            }
        });
        out.sort(Comparator.comparing(File::getAbsolutePath));
        return out;
    }

    public static void zipDirectory(File root, File zipFile) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(zipFile, "zipFile");
        ensureParent(zipFile);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            Path base = root.toPath();
            for (File file : listFilesRecursively(root)) {
                Path rel = base.relativize(file.toPath());
                String entryName = rel.toString().replace(File.separatorChar, '/');
                zos.putNextEntry(new ZipEntry(entryName));
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buf)) >= 0) zos.write(buf, 0, read);
                }
                zos.closeEntry();
            }
        }
    }

    public static void unzip(File zipFile, File destination) throws IOException {
        ensureDirectory(destination);
        String canonicalDest = destination.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buf = new byte[BUFFER_SIZE];
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destination, entry.getName());
                String canonicalOut = out.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalDest)) {
                    throw new IOException("Unsafe zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    ensureDirectory(out);
                } else {
                    ensureParent(out);
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
                        int read;
                        while ((read = zis.read(buf)) >= 0) bos.write(buf, 0, read);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public static String sha256(File file) throws IOException {
        return digest(file, "SHA-256");
    }

    public static String digest(File file, String algorithm) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buf = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buf)) >= 0) md.update(buf, 0, read);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Digest failed for " + file, e);
        }
    }
}
