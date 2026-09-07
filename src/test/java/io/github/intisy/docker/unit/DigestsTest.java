package io.github.intisy.docker.unit;

import io.github.intisy.docker.registry.Digests;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
public class DigestsTest {
    private static final String EMPTY_SHA256 =
            "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String ABC_SHA256 =
            "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    public void emptyInputHasTheKnownEmptyDigest() {
        assertEquals(EMPTY_SHA256, Digests.sha256(new byte[0]));
    }

    @Test
    public void knownVectorMatches() {
        assertEquals(ABC_SHA256, Digests.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void streamAndBytesAgree() throws IOException {
        byte[] payload = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        assertEquals(Digests.sha256(payload), Digests.sha256(new ByteArrayInputStream(payload)));
    }
}
