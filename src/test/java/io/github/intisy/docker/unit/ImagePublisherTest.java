package io.github.intisy.docker.unit;

import io.github.intisy.docker.registry.ImageConfigOverrides;
import io.github.intisy.docker.registry.ImagePublisher;
import io.github.intisy.docker.registry.ImageReference;
import io.github.intisy.docker.registry.Layer;
import io.github.intisy.docker.registry.RegistryClient;
import io.github.intisy.docker.registry.RegistryResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
public class ImagePublisherTest {
    private static final String INDEX_DIGEST =
            "sha256:2222222222222222222222222222222222222222222222222222222222222222";
    private static final String CONFIG_DIGEST =
            "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String BASE_LAYER_DIGEST =
            "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
    private static final String NEW_LAYER_DIGEST =
            "sha256:5678567856785678567856785678567856785678567856785678567856785678";

    private static final String INDEX = "{\"schemaVersion\":2,\"manifests\":[{\"digest\":\"" + INDEX_DIGEST
            + "\",\"platform\":{\"os\":\"linux\",\"architecture\":\"amd64\"}}]}";
    private static final String MANIFEST = "{\"schemaVersion\":2,"
            + "\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\","
            + "\"config\":{\"mediaType\":\"application/vnd.oci.image.config.v1+json\",\"digest\":\""
            + CONFIG_DIGEST + "\",\"size\":10},"
            + "\"layers\":[{\"mediaType\":\"application/vnd.oci.image.layer.v1.tar+gzip\",\"digest\":\""
            + BASE_LAYER_DIGEST + "\",\"size\":30}]}";
    private static final String CONFIG = "{\"architecture\":\"amd64\",\"os\":\"linux\","
            + "\"config\":{\"Env\":[\"PATH=/usr/bin\"]},"
            + "\"rootfs\":{\"type\":\"layers\",\"diff_ids\":[\"sha256:aaaa\"]},\"history\":[]}";

    private FakeRegistryHttp sourceHttp;
    private FakeRegistryHttp targetHttp;

    private RegistryClient source() {
        sourceHttp = new FakeRegistryHttp();
        sourceHttp.answerText("GET https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/21-jre",
                200, INDEX, "Content-Type", RegistryClient.OCI_INDEX);
        sourceHttp.answerText("GET https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/"
                + INDEX_DIGEST, 200, MANIFEST, "Content-Type", RegistryClient.OCI_MANIFEST);
        sourceHttp.answer("GET https://registry-1.docker.io/v2/library/eclipse-temurin/blobs/" + CONFIG_DIGEST,
                new RegistryResponse(200, new HashMap<String, String>(), CONFIG.getBytes(StandardCharsets.UTF_8)));
        sourceHttp.answer("GET https://registry-1.docker.io/v2/library/eclipse-temurin/blobs/" + BASE_LAYER_DIGEST,
                new RegistryResponse(200, new HashMap<String, String>(), new byte[] {9, 9, 9}));
        return new RegistryClient(sourceHttp, "registry-1.docker.io", false);
    }

    private RegistryClient target(boolean baseLayerAlreadyPresent) {
        targetHttp = new FakeRegistryHttp();
        String root = "http://registry.spisor.internal/v2/spisor/core";
        targetHttp.answer("HEAD " + root + "/blobs/" + BASE_LAYER_DIGEST, new RegistryResponse(
                baseLayerAlreadyPresent ? 200 : 404, new HashMap<String, String>(), new byte[0]));
        targetHttp.answer("HEAD " + root + "/blobs/" + NEW_LAYER_DIGEST,
                new RegistryResponse(404, new HashMap<String, String>(), new byte[0]));
        targetHttp.answer("POST " + root + "/blobs/uploads/", new RegistryResponse(202,
                location(root + "/blobs/uploads/u1"), new byte[0]));
        targetHttp.answer("PUT " + root + "/blobs/uploads/u1?digest=" + encoded(BASE_LAYER_DIGEST),
                new RegistryResponse(201, new HashMap<String, String>(), new byte[0]));
        targetHttp.answer("PUT " + root + "/blobs/uploads/u1?digest=" + encoded(NEW_LAYER_DIGEST),
                new RegistryResponse(201, new HashMap<String, String>(), new byte[0]));
        targetHttp.answer("PUT " + root + "/manifests/0.1.0",
                new RegistryResponse(201, new HashMap<String, String>(), new byte[0]));
        targetHttp.answerMissingHeadsAs404();
        targetHttp.acceptAnyBlobUpload();
        return new RegistryClient(targetHttp, "registry.spisor.internal", true);
    }

    private static java.util.Map<String, String> location(String value) {
        java.util.Map<String, String> headers = new HashMap<String, String>();
        headers.put("Location", value);
        return headers;
    }

    private static String encoded(String digest) {
        return digest.replace(":", "%3A");
    }

    private Layer layer(Path tmp) throws IOException {
        Path file = tmp.resolve("layer.tar.gz");
        Files.write(file, new byte[] {1, 2, 3, 4});
        return new Layer(file,
                "sha256:1234123412341234123412341234123412341234123412341234123412341234",
                NEW_LAYER_DIGEST, 4L);
    }

    private static ImageConfigOverrides overrides() {
        List<String> entrypoint = Arrays.asList("/opt/spisor/launcher/bin/launcher");
        return new ImageConfigOverrides().withEntrypoint(entrypoint).withCmd(null);
    }

    @Test
    public void publishesConfigLayerAndManifestAndReturnsTheManifestDigest(@TempDir Path tmp) throws IOException {
        String digest = ImagePublisher.publish(source(), ImageReference.parse("eclipse-temurin:21-jre"),
                target(false), ImageReference.parse("registry.spisor.internal/spisor/core:0.1.0"),
                layer(tmp), overrides());

        assertTrue(digest.startsWith("sha256:"), digest);
        List<String> calls = targetHttp.calls();
        assertTrue(calls.contains("PUT http://registry.spisor.internal/v2/spisor/core/manifests/0.1.0"),
                calls.toString());
    }

    /**
     * The manifest must be the LAST thing pushed. A registry rejects a manifest whose blobs are
     * not all present, so an ordering slip here produces a MANIFEST_BLOB_UNKNOWN that reads like a
     * corruption problem.
     */
    @Test
    public void theManifestIsPushedLast(@TempDir Path tmp) throws IOException {
        ImagePublisher.publish(source(), ImageReference.parse("eclipse-temurin:21-jre"),
                target(false), ImageReference.parse("registry.spisor.internal/spisor/core:0.1.0"),
                layer(tmp), overrides());

        List<String> calls = targetHttp.calls();
        assertEquals("PUT http://registry.spisor.internal/v2/spisor/core/manifests/0.1.0",
                calls.get(calls.size() - 1));
    }

    /**
     * The base is tens of megabytes. Re-sending it on every publish would make the day-to-day loop
     * unusable, and the HEAD that prevents it is cheap.
     */
    @Test
    public void aBaseLayerAlreadyInTheTargetIsNotReUploaded(@TempDir Path tmp) throws IOException {
        ImagePublisher.publish(source(), ImageReference.parse("eclipse-temurin:21-jre"),
                target(true), ImageReference.parse("registry.spisor.internal/spisor/core:0.1.0"),
                layer(tmp), overrides());

        assertFalse(sourceHttp.calls().contains("GET https://registry-1.docker.io/v2/library/eclipse-temurin/blobs/"
                        + BASE_LAYER_DIGEST),
                "an already-present layer must not even be fetched from the source");
    }

    @Test
    public void theIndexIsResolvedToTheAmd64ManifestBeforeAnythingIsRead(@TempDir Path tmp) throws IOException {
        ImagePublisher.publish(source(), ImageReference.parse("eclipse-temurin:21-jre"),
                target(false), ImageReference.parse("registry.spisor.internal/spisor/core:0.1.0"),
                layer(tmp), overrides());

        assertEquals("GET https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/21-jre",
                sourceHttp.calls().get(0));
        assertEquals("GET https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/" + INDEX_DIGEST,
                sourceHttp.calls().get(1));
    }
}
