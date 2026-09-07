package io.github.intisy.docker.unit;

import io.github.intisy.docker.registry.PlatformSelector;
import io.github.intisy.docker.registry.RegistryClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("unit")
public class PlatformSelectorTest {

    /**
     * The arm64 entry is deliberately first in the fixture. Taking the first entry is the bug this
     * test exists to prevent: it yields an image that pulls perfectly and then fails to exec.
     */
    @Test
    public void picksAmd64EvenWhenArm64ComesFirst() throws IOException {
        assertEquals("sha256:2222222222222222222222222222222222222222222222222222222222222222",
                PlatformSelector.digestFor(fixture(), "linux", "amd64"));
    }

    @Test
    public void missingPlatformFailsAndNamesWhatWasAvailable() throws IOException {
        try {
            PlatformSelector.digestFor(fixture(), "linux", "s390x");
            fail("expected an IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("linux/s390x"), expected.getMessage());
            assertTrue(expected.getMessage().contains("linux/amd64"), expected.getMessage());
        }
    }

    @Test
    public void indexMediaTypesAreRecognisedAndManifestsAreNot() {
        assertTrue(PlatformSelector.isIndex(RegistryClient.OCI_INDEX));
        assertTrue(PlatformSelector.isIndex(RegistryClient.DOCKER_MANIFEST_LIST));
        assertFalse(PlatformSelector.isIndex(RegistryClient.OCI_MANIFEST));
        assertFalse(PlatformSelector.isIndex(null));
    }

    /**
     * Registries append parameters to Content-Type. Matching the header exactly would classify a
     * real index as a plain manifest and then try to read layers out of it.
     */
    @Test
    public void indexMediaTypeWithParametersStillCounts() {
        assertTrue(PlatformSelector.isIndex(RegistryClient.OCI_INDEX + "; charset=utf-8"));
    }

    private static String fixture() throws IOException {
        InputStream in = PlatformSelectorTest.class.getResourceAsStream("/registry/temurin-index.json");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
