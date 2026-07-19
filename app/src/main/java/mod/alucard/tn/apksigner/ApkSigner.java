package mod.alucard.tn.apksigner;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.apksigner.ApkSignerTool;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import mod.jbk.build.BuiltInLibraries;

public class ApkSigner {

    private static final File EXTRACTED_TESTKEY_FILES_DIRECTORY = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "testkey");

    public boolean signWithTestKey(@NonNull String inputPath, @NonNull String outputPath, @Nullable LogCallback callback) {
        List<String> args = Arrays.asList(
                "sign",
                "--in", inputPath,
                "--out", outputPath,
                "--key", new File(EXTRACTED_TESTKEY_FILES_DIRECTORY, "testkey.pk8").getAbsolutePath(),
                "--cert", new File(EXTRACTED_TESTKEY_FILES_DIRECTORY, "testkey.x509.pem").getAbsolutePath(),
                "--v1-signing-enabled", "true",
                "--v2-signing-enabled", "true",
                "--v3-signing-enabled", "true",
                "--v4-signing-enabled", "false"
        );
        return runSigningTask(args, outputPath, callback);
    }

    public boolean signWithKeyStore(@NonNull String inputFilePath, @NonNull String outputFilePath,
                                    @NonNull String keyStorePath, @NonNull String keyStorePassword,
                                    @NonNull String keyStoreKeyAlias, @NonNull String keyPassword,
                                    @Nullable LogCallback callback) {
        return signWithKeyStore(inputFilePath, outputFilePath, keyStorePath, keyStorePassword,
                keyStoreKeyAlias, keyPassword, true, true, true, false, callback);
    }

    public boolean signWithKeyStore(@NonNull String inputFilePath, @NonNull String outputFilePath,
                                    @NonNull String keyStorePath, @NonNull String keyStorePassword,
                                    @NonNull String keyStoreKeyAlias, @NonNull String keyPassword,
                                    boolean enableV1, boolean enableV2, boolean enableV3, boolean enableV4,
                                    @Nullable LogCallback callback) {
        List<String> args = new ArrayList<>(Arrays.asList(
                "sign",
                "--in", inputFilePath,
                "--out", outputFilePath,
                "--ks", keyStorePath,
                "--ks-pass", "pass:" + keyStorePassword,
                "--ks-key-alias", keyStoreKeyAlias,
                "--key-pass", "pass:" + keyPassword,
                "--v1-signing-enabled", String.valueOf(enableV1),
                "--v2-signing-enabled", String.valueOf(enableV2),
                "--v3-signing-enabled", String.valueOf(enableV3),
                "--v4-signing-enabled", String.valueOf(enableV4)
        ));
        return runSigningTask(args, outputFilePath, callback);
    }

    private boolean runSigningTask(@NonNull List<String> args, @NonNull String outputPath, @Nullable LogCallback callback) {
        LogCallback.errorCount.set(0);
        File outputFile = new File(outputPath);
        File parentFile = outputFile.getParentFile();
        if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
            Log.e("ApkSigner", "Failed to create output directory: " + parentFile.getAbsolutePath());
            return false;
        }
        if (outputFile.exists() && !outputFile.delete()) {
            Log.e("ApkSigner", "Failed to replace existing signed output: " + outputFile.getAbsolutePath());
            return false;
        }
        try (LogWriter logger = new LogWriter(callback)) {
            long savedTimeMillis = System.currentTimeMillis();
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;

            logger.write("Signing with arguments: " + sanitizeArguments(args) + "\n");
            PrintStream stream = null;
            if (callback != null) {
                stream = new PrintStream(logger);
                System.setOut(stream);
                System.setErr(stream);
            }

            try {
                ApkSignerTool.main(args.toArray(new String[0]));
                verify(outputPath, logger);
            } catch (Exception e) {
                LogCallback.errorCount.incrementAndGet();
                logger.write("Failed to sign APK: " + Log.getStackTraceString(e) + "\n");
            } finally {
                if (callback != null) {
                    System.setOut(oldOut);
                    System.setErr(oldErr);
                }
                if (stream != null) {
                    stream.close();
                }
            }

            logger.write("Signing finished in " + (System.currentTimeMillis() - savedTimeMillis) + " ms\n");
        } catch (IOException e) {
            Log.e("ApkSigner", "Failed to initialize signer logger", e);
            return false;
        }
        return LogCallback.errorCount.get() == 0;
    }

    private void verify(@NonNull String outputPath, @NonNull LogWriter logger) {
        List<String> verifyArgs = Arrays.asList(
                "verify",
                "--verbose",
                outputPath
        );
        try {
            ApkSignerTool.main(verifyArgs.toArray(new String[0]));
        } catch (Exception e) {
            LogCallback.errorCount.incrementAndGet();
            logger.write("APK verification failed: " + Log.getStackTraceString(e) + "\n");
        }
    }

    private List<String> sanitizeArguments(List<String> args) {
        ArrayList<String> sanitized = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            String value = args.get(i);
            if ("--ks-pass".equals(value) || "--key-pass".equals(value)) {
                sanitized.add(value);
                sanitized.add("pass:******");
                i++;
                continue;
            }
            sanitized.add(value);
        }
        return sanitized;
    }

    public interface LogCallback {
        AtomicInteger errorCount = new AtomicInteger(0);

        void onNewLineLogged(String line);
    }

    private static class LogWriter extends OutputStream {

        private final LogCallback callback;
        private final StringBuilder cache = new StringBuilder();

        private LogWriter(LogCallback callback) {
            this.callback = callback;
        }

        @Override
        public void write(int b) {
            if (callback == null) {
                return;
            }
            cache.append((char) b);
            if (((char) b) == '\n') {
                callback.onNewLineLogged(cache.toString());
                cache.setLength(0);
            }
        }

        private void write(String message) {
            if (callback == null) {
                return;
            }
            for (byte b : message.getBytes()) {
                write(b);
            }
        }

        @Override
        public void close() throws IOException {
            if (callback != null && cache.length() > 0) {
                callback.onNewLineLogged(cache.toString());
                cache.setLength(0);
            }
            super.close();
        }
    }
}
