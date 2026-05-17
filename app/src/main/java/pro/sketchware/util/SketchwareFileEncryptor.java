package pro.sketchware.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Saves files only inside the active project's editable scope.
 */
public class SketchwareFileEncryptor {
    private static final String ENCRYPTION_KEY = "sketchwaresecure";
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    public static boolean encryptAndSaveFile(String scId, String filePath, String content) {
        try {
            ProjectPathResolver.ResolvedPath resolvedPath = ProjectPathResolver.resolveForWrite(scId, filePath);
            if (resolvedPath == null) {
                return false;
            }

            File file = resolvedPath.getFile();
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            String contentToSave = content == null ? "" : content.trim();
            try {
                if (contentToSave.startsWith("{")) {
                    contentToSave = new org.json.JSONObject(contentToSave).toString();
                } else if (contentToSave.startsWith("[")) {
                    contentToSave = new org.json.JSONArray(contentToSave).toString();
                }
            } catch (Exception ignored) {
            }

            boolean needsEncryption = shouldEncrypt(file, resolvedPath.getRelativePath());
            if (!needsEncryption) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    raf.setLength(0);
                    raf.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
                }
                return true;
            }

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] key = ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));

            byte[] encrypted = cipher.doFinal(contentToSave.getBytes(StandardCharsets.UTF_8));
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.setLength(0);
                raf.write(encrypted);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean fileExists(String scId, String filePath) {
        ProjectPathResolver.ResolvedPath resolvedPath = ProjectPathResolver.resolveForWrite(scId, filePath);
        return resolvedPath != null && resolvedPath.getFile().exists();
    }

    private static boolean shouldEncrypt(File file, String relativePath) {
        String fileName = file.getName();
        if (fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            if (ext.equals("xml")
                    || ext.equals("json")
                    || ext.equals("txt")
                    || ext.equals("java")
                    || ext.equals("kt")
                    || ext.equals("gradle")
                    || ext.equals("properties")
                    || ext.equals("pro")
                    || ext.equals("html")
                    || ext.equals("md")) {
                return false;
            }
        }

        if (file.exists()) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                if (bytes.length == 0) {
                    return isProtectedSketchwareFile(relativePath, fileName);
                }

                String possibleText = new String(bytes, StandardCharsets.UTF_8).trim();
                boolean looksPlainText = (possibleText.startsWith("{") && possibleText.endsWith("}"))
                        || (possibleText.startsWith("[") && possibleText.endsWith("]"))
                        || possibleText.startsWith("<?xml")
                        || possibleText.startsWith("<");
                return !looksPlainText;
            } catch (Exception ignored) {
                return true;
            }
        }

        return isProtectedSketchwareFile(relativePath, fileName);
    }

    private static boolean isProtectedSketchwareFile(String relativePath, String fileName) {
        return !fileName.contains(".")
                && (relativePath.startsWith("data/") || relativePath.startsWith("mysc/list/"));
    }
}
