package io.github.intisy.docker.unit;

import io.github.intisy.docker.registry.Digests;
import io.github.intisy.docker.registry.ImageReference;
import io.github.intisy.docker.registry.RegistryBody;
import io.github.intisy.docker.registry.RegistryClient;
import io.github.intisy.docker.registry.RegistryResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("unit")
public class RegistryClientTest {
    private static final String LOCAL = "registry.example.internal";
    private static final String MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";

    private RegistryClient plainHttpClient(FakeRegistryHttp http) {
        return new RegistryClient(http, LOCAL, true);
    }

    @Test
    public void getManifestUsesHttpForAPlainRegistryAndReturnsTheDigestHeader() throws Exception {
        FakeRegistryHttp http = new FakeRegistryHttp();
        http.answerText("GET http://" + LOCAL + "/v2/myorg/app/manifests/0.1.0", 200, "{\"schemaVersion\":2}",
                "Docker-Content-Digest", "sha256:aa", "Content-Type", MANIFEST_MEDIA_TYPE);

        RegistryClient.Manifest manifest =
                plainHttpClient(http).getManifest(ImageReference.parse(LOCAL + "/myorg/app:0.1.0"));

        assertEquals("sha256:aa", manifest.digest());
        assertEquals(MANIFEST_MEDIA_TYPE, manifest.mediaType());
        assertEquals("{\"schemaVersion\":2}", manifest.text());
    }

    /**
     * A registry that does not send Docker-Content-Digest is within its rights, and the digest is
     * then the sha256 of the body we received. Falling back to null here would produce a manifest
     * that cannot be referenced by digest later.
     */
    @Test
    public void manifestDigestIsComputedWhenTheHeaderIsAbsent() throws Exception {
        FakeRegistryHttp http = new FakeRegistryHttp();
        String body = "{\"schemaVersion\":2}";
        http.answerText("GET http://" + LOCAL + "/v2/myorg/app/manifests/0.1.0", 200, body,
                "Content-Type", MANIFEST_MEDIA_TYPE);

        RegistryClient.Manifest manifest =
                plainHttpClient(http).getManifest(ImageReference.parse(LOCAL + "/myorg/app:0.1.0"));

        assertEquals(Digests.sha256(body.getBytes(StandardCharsets.UTF_8)), manifest.digest());
    }

    @Test
    public void httpsIsUsedWhenPlainHttpIsNotRequested() throws Exception {
        FakeRegistryHttp http = new FakeRegistryHttp();
        http.answerText("GET https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/21-jre",
                200, "{}", "Content-Type", MANIFEST_MEDIA_TYPE);

        new RegistryClient(http, "registry-1.docker.io", false)
                .getManifest(ImageReference.parse("eclipse-temurin:21-jre"));

        assertTrue(http.calls().get(0).startsWith("GET https://"));
    }

    @Test
    public void blobExistsIsTrueOnTwoHundredAndFalseOnFourOhFour() throws Exception {
        FakeRegistryHttp http = new FakeRegistryHttp();
        http.answer("HEAD http://" + LOCAL + "/v2/myorg/app/blobs/sha256:aa",
                new RegistryResponse(200, new HashMap<String, String>(), new byte[0]));
        http.answer("HEAD http://" + LOCAL + "/v2/myorg/app/blobs/sha256:bb",
                new RegistryResponse(404, new HashMap<String, String>(), new byte[0]));

        RegistryClient client = plainHttpClient(http);
        assertTrue(client.blobExists("myorg/app", "sha256:aa"));
        assertFalse(client.blobExists("myorg/app", "sha256:bb"));
    }

    @Test
    public void putBlobPostsThenPutsToTheLocationWithTheDigestQuery() throws Exception {
        FakeRegistryHttp http = new FakeRegistryHttp();
        String uploadUrl = "http://" + LOCAL + "/v2/myorg/app/blobs/uploads/abc123";
        http.answer("POST http://" + LOCAL + "/v2/myorg/app/blobs/uploads/",
                new RegistryResponse(202, headers("Location", uploadUrl), new byte[0]));
        http.answer("PUT " + uploadUrl + "?digest=sha256%3Aaa",
                new RegistryResponse(201, new HashMap<String, String>(), new byte[0]));

        plainHttpClient(http).putBlob("myorg/app", "sha256:aa", RegistryBody.ofBytes(new byte[] {1, 2, 3}));

        assertEquals("POST http://" + LOCAL + "/v2/myorg/app/blobs/uploads/", http.calls().get(0));
        assertEquals("PUT " + uploadUrl + "?digest=sha256%3Aaa", http.calls().get(1));
    }

    /**
     * A registry may return a relative Location. Resolving it against the registry root rather
     * than treating it as absolute is the difference between a working upload and a
     * MalformedURLException on the second call.
     */
    @Test
    public void relativeUploadLocationIsResolvedAgainstTheRegistry() throws Exception {
        FakeRegistryHttp http = new FakeRegistryHttp();
        http.answer("POST http://" + LOCAL + "/v2/myorg/app/blobs/uploads/",
                new RegistryResponse(202, headers("Location", "/v2/myorg/app/blobs/uploads/rel1"), new byte[0]));
        http.answer("PUT http://" + LOCAL + "/v2/myorg/app/blobs/uploads/rel1?digest=sha256%3Aaa",
                new RegistryResponse(201, new HashMap<String, String>(), new byte[0]));

        plainHttpClient(http).putBlob("myorg/app", "sha256:aa", RegistryBody.ofBytes(new byte[] {1}));

        assertEquals("PUT http://" + LOCAL + "/v2/myorg/app/blobs/uploads/rel1?digest=sha256%3Aaa",
                http.calls().get(1));
    }

    @Test
    public void putManifestSendsTheMediaTypeAsContentType() throws Exception {
        FakeRegistryHttp http = new FakeRegistryHttp();
        http.answer("PUT http://" + LOCAL + "/v2/myorg/app/manifests/0.1.0",
                new RegistryResponse(201, new HashMap<String, String>(), new byte[0]));

        byte[] manifest = "{\"schemaVersion\":2}".getBytes(StandardCharsets.UTF_8);
        plainHttpClient(http).putManifest(
                ImageReference.parse(LOCAL + "/myorg/app:0.1.0"), MANIFEST_MEDIA_TYPE, manifest);

        assertEquals("PUT http://" + LOCAL + "/v2/myorg/app/manifests/0.1.0", http.calls().get(0));
        assertEquals("{\"schemaVersion\":2}",
                new String(http.bodySentTo("PUT http://" + LOCAL + "/v2/myorg/app/manifests/0.1.0"),
                        StandardCharsets.UTF_8));
    }

    /**
     * A failed push must name the status and the body. A silent failure here surfaces much later
     * as a pod that cannot pull, which is a far worse place to start debugging from.
     */
    @Test
    public void aFailedManifestPutThrowsWithStatusAndBody() {
        FakeRegistryHttp http = new FakeRegistryHttp();
        http.answerText("PUT http://" + LOCAL + "/v2/myorg/app/manifests/0.1.0", 400, "MANIFEST_INVALID");

        try {
            plainHttpClient(http).putManifest(ImageReference.parse(LOCAL + "/myorg/app:0.1.0"),
                    MANIFEST_MEDIA_TYPE, new byte[] {1});
            fail("expected an IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("400"), expected.getMessage());
            assertTrue(expected.getMessage().contains("MANIFEST_INVALID"), expected.getMessage());
        }
    }

    private static java.util.Map<String, String> headers(String key, String value) {
        java.util.Map<String, String> headers = new HashMap<String, String>();
        headers.put(key, value);
        return headers;
    }
}
