package io.github.intisy.docker.registry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Rewrites a base image's config and manifest so that they carry one additional layer.
 *
 * @author Finn Birich
 */
public final class ImageAssembler {
    private static final Gson GSON = new Gson();

    public static final class Assembled {
        private final String configJson;
        private final String configDigest;
        private final long configSize;
        private final String manifestJson;
        private final String manifestMediaType;

        Assembled(String configJson, String configDigest, long configSize,
                  String manifestJson, String manifestMediaType) {
            this.configJson = configJson;
            this.configDigest = configDigest;
            this.configSize = configSize;
            this.manifestJson = manifestJson;
            this.manifestMediaType = manifestMediaType;
        }

        public String configJson() {
            return configJson;
        }

        public String configDigest() {
            return configDigest;
        }

        public long configSize() {
            return configSize;
        }

        public String manifestJson() {
            return manifestJson;
        }

        public String manifestMediaType() {
            return manifestMediaType;
        }
    }

    public static Assembled append(String baseConfigJson, String baseManifestJson,
                                   Layer layer, ImageConfigOverrides overrides) {
        JsonObject config = GSON.fromJson(baseConfigJson, JsonObject.class);
        config.getAsJsonObject("rootfs").getAsJsonArray("diff_ids").add(layer.diffId());
        addHistory(config);
        applyOverrides(config, overrides);

        String configJson = GSON.toJson(config);
        byte[] configBytes = configJson.getBytes(StandardCharsets.UTF_8);
        String configDigest = Digests.sha256(configBytes);

        JsonObject manifest = GSON.fromJson(baseManifestJson, JsonObject.class);
        JsonObject configDescriptor = manifest.getAsJsonObject("config");
        configDescriptor.addProperty("digest", configDigest);
        configDescriptor.addProperty("size", configBytes.length);

        JsonObject layerDescriptor = new JsonObject();
        layerDescriptor.addProperty("mediaType", layerMediaTypeMatching(manifest));
        layerDescriptor.addProperty("digest", layer.digest());
        layerDescriptor.addProperty("size", layer.size());
        manifest.getAsJsonArray("layers").add(layerDescriptor);

        String mediaType = manifest.has("mediaType")
                ? manifest.get("mediaType").getAsString()
                : RegistryClient.OCI_MANIFEST;
        return new Assembled(configJson, configDigest, configBytes.length, GSON.toJson(manifest), mediaType);
    }

    /**
     * A docker-schema2 manifest and an OCI manifest use different layer media types, and mixing them in one
     * manifest is rejected. Taking the type from the layers already there keeps the assembled manifest
     * internally consistent whichever shape the base image used. The {@code OCI_LAYER_GZIP} fallback only
     * applies when the base manifest has no layers at all; a real base image always has at least one, so this
     * never mixes a Docker manifest with an OCI layer type in practice.
     */
    private static String layerMediaTypeMatching(JsonObject manifest) {
        JsonArray layers = manifest.getAsJsonArray("layers");
        if (layers != null && layers.size() > 0) {
            JsonObject first = layers.get(0).getAsJsonObject();
            if (first.has("mediaType")) {
                return first.get("mediaType").getAsString();
            }
        }
        return RegistryClient.OCI_LAYER_GZIP;
    }

    /**
     * No {@code created} timestamp is written deliberately: a wall-clock value would change the config bytes,
     * and therefore the config digest and manifest, on every build, so two builds of identical inputs would
     * push different images. Same determinism property as why {@link LayerBuilder} pins mtime to zero.
     */
    private static void addHistory(JsonObject config) {
        JsonArray history = config.getAsJsonArray("history");
        if (history == null) {
            history = new JsonArray();
            config.add("history", history);
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("created_by", "docker-java registry assembler");
        history.add(entry);
    }

    private static void applyOverrides(JsonObject config, ImageConfigOverrides overrides) {
        JsonObject inner = config.getAsJsonObject("config");
        if (inner == null) {
            inner = new JsonObject();
            config.add("config", inner);
        }
        if (overrides.entrypoint() != null) {
            inner.add("Entrypoint", toArray(overrides.entrypoint()));
        }
        if (overrides.clearCmd()) {
            inner.remove("Cmd");
        } else if (overrides.cmd() != null) {
            inner.add("Cmd", toArray(overrides.cmd()));
        }
        if (overrides.env() != null) {
            inner.add("Env", toArray(overrides.env()));
        }
        if (overrides.workingDir() != null) {
            inner.addProperty("WorkingDir", overrides.workingDir());
        }
    }

    private static JsonArray toArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private ImageAssembler() {}
}
