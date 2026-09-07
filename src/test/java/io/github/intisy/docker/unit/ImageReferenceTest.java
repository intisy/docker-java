package io.github.intisy.docker.unit;

import io.github.intisy.docker.registry.ImageReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
public class ImageReferenceTest {

    @Test
    public void bareNameGetsDockerHubLibraryAndLatest() {
        ImageReference reference = ImageReference.parse("nginx");
        assertEquals("docker.io", reference.registry());
        assertEquals("registry-1.docker.io", reference.host());
        assertEquals("library/nginx", reference.repository());
        assertEquals("latest", reference.reference());
        assertFalse(reference.isDigest());
    }

    @Test
    public void officialImageWithTag() {
        ImageReference reference = ImageReference.parse("eclipse-temurin:21-jre");
        assertEquals("library/eclipse-temurin", reference.repository());
        assertEquals("21-jre", reference.reference());
    }

    @Test
    public void namespacedImageKeepsItsNamespaceAndGetsNoLibraryPrefix() {
        ImageReference reference = ImageReference.parse("myorg/app:1.0");
        assertEquals("docker.io", reference.registry());
        assertEquals("myorg/app", reference.repository());
        assertEquals("1.0", reference.reference());
    }

    @Test
    public void explicitRegistryIsRecognisedByItsDot() {
        ImageReference reference = ImageReference.parse("registry.example.internal/myorg/app:0.1.0");
        assertEquals("registry.example.internal", reference.registry());
        assertEquals("registry.example.internal", reference.host());
        assertEquals("myorg/app", reference.repository());
        assertEquals("0.1.0", reference.reference());
    }

    /**
     * The colon in a registry port must not be mistaken for a tag separator. This is the parse
     * bug every hand-rolled reference parser ships with at least once.
     */
    @Test
    public void registryPortIsNotATag() {
        ImageReference reference = ImageReference.parse("localhost:5000/myorg/app:0.1.0");
        assertEquals("localhost:5000", reference.registry());
        assertEquals("myorg/app", reference.repository());
        assertEquals("0.1.0", reference.reference());
    }

    @Test
    public void registryPortWithNoTagStillParses() {
        ImageReference reference = ImageReference.parse("localhost:5000/myorg/app");
        assertEquals("localhost:5000", reference.registry());
        assertEquals("myorg/app", reference.repository());
        assertEquals("latest", reference.reference());
    }

    @Test
    public void digestReferenceIsRecognised() {
        String digest = "sha256:0000000000000000000000000000000000000000000000000000000000000001";
        ImageReference reference = ImageReference.parse("nginx@" + digest);
        assertTrue(reference.isDigest());
        assertEquals(digest, reference.reference());
        assertEquals("library/nginx", reference.repository());
    }

    @Test
    public void withRegistryRetargetsAndKeepsTheRest() {
        ImageReference retargeted = ImageReference.parse("eclipse-temurin:21-jre")
                .withRegistry("registry.example.internal");
        assertEquals("registry.example.internal", retargeted.registry());
        assertEquals("library/eclipse-temurin", retargeted.repository());
        assertEquals("21-jre", retargeted.reference());
    }

    @Test
    public void roundTripsThroughToString() {
        String text = "registry.example.internal/myorg/app:0.1.0";
        assertEquals(text, ImageReference.parse(text).toString());
    }

    @Test
    public void emptyReferenceIsRejected() {
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            public void execute() {
                ImageReference.parse("");
            }
        });
    }
}
