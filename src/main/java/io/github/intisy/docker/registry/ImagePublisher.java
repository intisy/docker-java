package io.github.intisy.docker.registry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Publishes an image that is a base image plus one layer, moving only the blobs the target is
 * missing. Never contacts a Docker daemon.
 *
 * @author Finn Birich
 */
public final class ImagePublisher {
    private static final Gson GSON = new Gson();

    public static String publish(RegistryClient source, ImageReference base,
                                 RegistryClient target, ImageReference targetReference,
                                 Layer layer, ImageConfigOverrides overrides) throws IOException {
        RegistryClient.Manifest baseManifest = resolveToPlatformManifest(source, base);
        JsonObject manifestJson = GSON.fromJson(baseManifest.text(), JsonObject.class);

        String baseConfigDigest = manifestJson.getAsJsonObject("config").get("digest").getAsString();
        String baseConfigJson = new String(
                source.getBlob(base.repository(), baseConfigDigest).bytes(), StandardCharsets.UTF_8);

        ImageAssembler.Assembled assembled =
                ImageAssembler.append(baseConfigJson, baseManifest.text(), layer, overrides);

        copyBaseLayers(source, base, target, targetReference, manifestJson);
        uploadNewLayer(target, targetReference, layer);
        uploadConfig(target, targetReference, assembled);

        target.putManifest(targetReference, assembled.manifestMediaType(),
                assembled.manifestJson().getBytes(StandardCharsets.UTF_8));
        return Digests.sha256(assembled.manifestJson().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A base image is normally published as a multi-architecture index, so the first read yields the index
     * and the platform manifest needs a second read by digest. A base that is already a single manifest skips
     * straight through.
     */
    private static RegistryClient.Manifest resolveToPlatformManifest(RegistryClient source, ImageReference base)
            throws IOException {
        RegistryClient.Manifest first = source.getManifest(base);
        if (!PlatformSelector.isIndex(first.mediaType())) {
            return first;
        }
        String digest = PlatformSelector.digestFor(first.text(), "linux", "amd64");
        return source.getManifest(base.withReference(digest, true));
    }

    private static void copyBaseLayers(RegistryClient source, ImageReference base,
                                       RegistryClient target, ImageReference targetReference,
                                       JsonObject manifestJson) throws IOException {
        JsonArray layers = manifestJson.getAsJsonArray("layers");
        for (int i = 0; i < layers.size(); i++) {
            String digest = layers.get(i).getAsJsonObject().get("digest").getAsString();
            copyBlobIfMissing(source, base, target, targetReference, digest);
        }
    }

    private static void copyBlobIfMissing(RegistryClient source, ImageReference base,
                                          RegistryClient target, ImageReference targetReference,
                                          String digest) throws IOException {
        if (target.blobExists(targetReference.repository(), digest)) {
            return;
        }
        byte[] blob = source.getBlob(base.repository(), digest).bytes();
        target.putBlob(targetReference.repository(), digest, RegistryBody.ofBytes(blob));
    }

    private static void uploadNewLayer(RegistryClient target, ImageReference targetReference, Layer layer)
            throws IOException {
        if (target.blobExists(targetReference.repository(), layer.digest())) {
            return;
        }
        InputStream content = Files.newInputStream(layer.file());
        try {
            target.putBlob(targetReference.repository(), layer.digest(),
                    RegistryBody.ofStream(content, layer.size()));
        } finally {
            content.close();
        }
    }

    private static void uploadConfig(RegistryClient target, ImageReference targetReference,
                                     ImageAssembler.Assembled assembled) throws IOException {
        if (target.blobExists(targetReference.repository(), assembled.configDigest())) {
            return;
        }
        target.putBlob(targetReference.repository(), assembled.configDigest(),
                RegistryBody.ofBytes(assembled.configJson().getBytes(StandardCharsets.UTF_8)));
    }

    private ImagePublisher() {}
}
