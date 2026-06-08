package pro.sketchware.compiler;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotSame;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;

public class IncrementalCompileCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Verifies that createOrReuse() returns the exact same SourceFingerprint object
     * (no SHA re-computation) when lastModified and size are unchanged.
     */
    @Test
    public void lazyShaShouldReuseWhenMetadataUnchanged() throws Exception {
        File file = tmp.newFile("A.java");
        Files.writeString(file.toPath(), "class A {}");

        Object fingerprint1 = invokeCreateOrReuse(file, null);
        assertNotNull("first fingerprint must not be null", fingerprint1);

        // Re-use: metadata is identical, object should be the same reference
        Object fingerprint2 = invokeCreateOrReuse(file, fingerprint1);
        assertSame("fingerprint must be reused when metadata is unchanged", fingerprint1, fingerprint2);
    }

    /**
     * Verifies that createOrReuse() creates a new SourceFingerprint when the file changes.
     */
    @Test
    public void lazyShaShouldRecomputeWhenContentChanges() throws Exception {
        File file = tmp.newFile("B.java");
        Files.writeString(file.toPath(), "class B {}");

        Object fingerprint1 = invokeCreateOrReuse(file, null);
        assertNotNull(fingerprint1);

        // Write more bytes so the size changes
        Files.writeString(file.toPath(), "class B { int x; }");
        // Force a lastModified difference on filesystems with coarse timestamps
        file.setLastModified(file.lastModified() + 2000);

        Object fingerprint2 = invokeCreateOrReuse(file, fingerprint1);
        assertNotSame("fingerprint must be recomputed when file metadata changes", fingerprint1, fingerprint2);
    }

    // ---------------------------------------------------------------------------
    // Reflection helpers (SourceFingerprint is a private nested class)
    // ---------------------------------------------------------------------------

    private static Object invokeCreateOrReuse(File file, Object old) throws Exception {
        Class<?> fingerprintClass = Class.forName(
                "pro.sketchware.compiler.IncrementalCompileCache$SourceFingerprint");
        Method method = fingerprintClass.getDeclaredMethod("createOrReuse", File.class, fingerprintClass);
        method.setAccessible(true);
        return method.invoke(null, file, old);
    }
}
