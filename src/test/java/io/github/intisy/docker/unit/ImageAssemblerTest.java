package io.github.intisy.docker.unit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.intisy.docker.registry.Digests;
import io.github.intisy.docker.registry.ImageAssembler;
import io.github.intisy.docker.registry.ImageConfigOverrides;
import io.github.intisy.docker.registry.Layer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
public class ImageAssemblerTest {
    private static final Gson GSON = new Gson();
    private static final String NEW_DIFF_ID =
            "sha256:1234123412341234123412341234123412341234123412341234123412341234";
    private static final String NEW_DIGEST =
            "sha256:5678567856785678567856785678567856785678567856785678567856785678";

    private static Layer layer() {
        return new Layer(Paths.get("layer.tar.gz"), NEW_DIFF_ID, NEW_DIGEST, 4096L);
    }

    private static ImageConfigOverrides overrides() {
        return new ImageConfigOverrides()
                .withEntrypoint(Arrays.asList("/opt/spisor/launcher/bin/launcher",
                        "--registry=/var/lib/spisor/registry"))
                .withCmd(null)
                .withWorkingDir("/var/lib/spisor");
    }

    private static ImageAssembler.Assembled assemble() throws IOException {
        return ImageAssembler.append(fixture("base-config.json"), fixture("base-manifest.json"),
                layer(), overrides());
    }

    @Test
    public void theNewDiffIdIsAppendedLast() throws IOException {
        JsonObject config = GSON.fromJson(assemble().configJson(), JsonObject.class);
        com.google.gson.JsonArray diffIds = config.getAsJsonObject("rootfs").getAsJsonArray("diff_ids");

        assertEquals(3, diffIds.size());
        assertEquals(NEW_DIFF_ID, diffIds.get(2).getAsString());
    }

    @Test
    public void theNewLayerDescriptorIsAppendedLastWithItsCompressedDigestAndSize() throws IOException {
        JsonObject manifest = GSON.fromJson(assemble().manifestJson(), JsonObject.class);
        com.google.gson.JsonArray layers = manifest.getAsJsonArray("layers");

        assertEquals(3, layers.size());
        JsonObject added = layers.get(2).getAsJsonObject();
        assertEquals(NEW_DIGEST, added.get("digest").getAsString());
        assertEquals(4096L, added.get("size").getAsLong());
    }

    @Test
    public void entrypointAndWorkingDirAreStampedAndCmdIsCleared() throws IOException {
        JsonObject config = GSON.fromJson(assemble().configJson(), JsonObject.class)
                .getAsJsonObject("config");

        assertEquals("/opt/spisor/launcher/bin/launcher",
                config.getAsJsonArray("Entrypoint").get(0).getAsString());
        assertEquals("--registry=/var/lib/spisor/registry",
                config.getAsJsonArray("Entrypoint").get(1).getAsString());
        assertEquals("/var/lib/spisor", config.get("WorkingDir").getAsString());
        assertTrue(config.get("Cmd") == null || config.get("Cmd").isJsonNull(),
                "a base Cmd left in place would run jshell instead of the launcher");
    }

    @Test
    public void notCallingWithCmdLeavesTheBaseCmdInPlace() throws IOException {
        ImageConfigOverrides overrides = new ImageConfigOverrides()
                .withEntrypoint(Arrays.asList("/opt/spisor/launcher/bin/launcher"))
                .withWorkingDir("/var/lib/spisor");

        JsonObject config = GSON.fromJson(
                ImageAssembler.append(fixture("base-config.json"), fixture("base-manifest.json"),
                        layer(), overrides).configJson(),
                JsonObject.class).getAsJsonObject("config");

        assertEquals("jshell", config.getAsJsonArray("Cmd").get(0).getAsString());
    }

    @Test
    public void theBaseEnvironmentSurvives() throws IOException {
        JsonObject config = GSON.fromJson(assemble().configJson(), JsonObject.class)
                .getAsJsonObject("config");

        assertTrue(config.getAsJsonArray("Env").toString().contains("JAVA_HOME=/opt/java/openjdk"),
                "dropping the base env would remove JAVA_HOME and PATH and the launcher would not start");
    }

    /**
     * The manifest's config descriptor must describe the config document we actually produced.
     * If the digest or the size disagrees by one byte the registry rejects the manifest, and the
     * message it gives is not obviously about this.
     */
    @Test
    public void theManifestConfigDescriptorMatchesTheProducedConfigExactly() throws IOException {
        ImageAssembler.Assembled assembled = assemble();
        JsonObject manifest = GSON.fromJson(assembled.manifestJson(), JsonObject.class);
        JsonObject descriptor = manifest.getAsJsonObject("config");

        byte[] configBytes = assembled.configJson().getBytes(StandardCharsets.UTF_8);
        assertEquals(Digests.sha256(configBytes), descriptor.get("digest").getAsString());
        assertEquals(configBytes.length, descriptor.get("size").getAsLong());
        assertEquals(assembled.configDigest(), descriptor.get("digest").getAsString());
        assertEquals(configBytes.length, assembled.configSize());
    }

    @Test
    public void aHistoryEntryIsAdded() throws IOException {
        JsonObject config = GSON.fromJson(assemble().configJson(), JsonObject.class);
        assertEquals(2, config.getAsJsonArray("history").size());
    }

    @Test
    public void anOciBaseProducesAnOciManifest() throws IOException {
        assertEquals("application/vnd.oci.image.manifest.v1+json", assemble().manifestMediaType());
    }

    /**
     * A docker-schema2 manifest and an OCI manifest use different layer media types, and a
     * manifest that mixes them is rejected. Taking the added layer's type from the layers already
     * present is what keeps an assembled manifest internally consistent whichever shape the base
     * image happened to use, and base images in the wild are both.
     */
    @Test
    public void aDockerSchema2BaseKeepsDockerMediaTypesThroughout() throws IOException {
        String dockerManifest = fixture("base-manifest.json")
                .replace("application/vnd.oci.image.manifest.v1+json",
                        "application/vnd.docker.distribution.manifest.v2+json")
                .replace("application/vnd.oci.image.config.v1+json",
                        "application/vnd.docker.container.image.v1+json")
                .replace("application/vnd.oci.image.layer.v1.tar+gzip",
                        "application/vnd.docker.image.rootfs.diff.tar.gzip");

        ImageAssembler.Assembled assembled =
                ImageAssembler.append(fixture("base-config.json"), dockerManifest, layer(), overrides());

        assertEquals("application/vnd.docker.distribution.manifest.v2+json", assembled.manifestMediaType());
        JsonObject manifest = GSON.fromJson(assembled.manifestJson(), JsonObject.class);
        assertEquals("application/vnd.docker.image.rootfs.diff.tar.gzip",
                manifest.getAsJsonArray("layers").get(2).getAsJsonObject().get("mediaType").getAsString());
    }

    @Test
    public void aBaseConfigWithNoHistoryArrayGetsOneCreated() throws IOException {
        String baseConfigWithoutHistory = "{\"architecture\":\"amd64\",\"os\":\"linux\","
                + "\"config\":{\"Env\":[],\"Cmd\":[\"jshell\"]},"
                + "\"rootfs\":{\"type\":\"layers\",\"diff_ids\":["
                + "\"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"]}}";

        JsonObject config = GSON.fromJson(
                ImageAssembler.append(baseConfigWithoutHistory, fixture("base-manifest.json"),
                        layer(), overrides()).configJson(),
                JsonObject.class);

        assertEquals(1, config.getAsJsonArray("history").size());
    }

    @Test
    public void aBaseConfigWithNoInnerConfigObjectGetsOneCreated() throws IOException {
        String baseConfigWithoutInnerConfig = "{\"architecture\":\"amd64\",\"os\":\"linux\","
                + "\"rootfs\":{\"type\":\"layers\",\"diff_ids\":["
                + "\"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"]}}";

        JsonObject config = GSON.fromJson(
                ImageAssembler.append(baseConfigWithoutInnerConfig, fixture("base-manifest.json"),
                        layer(), overrides()).configJson(),
                JsonObject.class).getAsJsonObject("config");

        assertEquals("/opt/spisor/launcher/bin/launcher", config.getAsJsonArray("Entrypoint").get(0).getAsString());
    }

    private static String fixture(String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = ImageAssemblerTest.class.getResourceAsStream("/registry/" + name)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
