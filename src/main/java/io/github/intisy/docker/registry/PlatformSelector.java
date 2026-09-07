package io.github.intisy.docker.registry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks one platform's manifest out of a multi-architecture index.
 *
 * @author Finn Birich
 */
public final class PlatformSelector {
    private static final Gson GSON = new Gson();

    public static boolean isIndex(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        String bare = mediaType.split(";")[0].trim();
        return RegistryClient.OCI_INDEX.equals(bare) || RegistryClient.DOCKER_MANIFEST_LIST.equals(bare);
    }

    /**
     * @throws IOException when no entry matches, naming every platform the index does carry, so
     * the operator sees why rather than only that.
     */
    public static String digestFor(String indexJson, String os, String architecture) throws IOException {
        JsonObject index = GSON.fromJson(indexJson, JsonObject.class);
        JsonArray manifests = index.getAsJsonArray("manifests");
        List<String> available = new ArrayList<String>();
        if (manifests != null) {
            for (JsonElement element : manifests) {
                JsonObject entry = element.getAsJsonObject();
                JsonObject platform = entry.getAsJsonObject("platform");
                if (platform == null) {
                    continue;
                }
                String entryOs = asString(platform, "os");
                String entryArchitecture = asString(platform, "architecture");
                available.add(entryOs + "/" + entryArchitecture);
                if (os.equals(entryOs) && architecture.equals(entryArchitecture)) {
                    return asString(entry, "digest");
                }
            }
        }
        throw new IOException("index carries no " + os + "/" + architecture
                + " manifest; it offers " + available);
    }

    private static String asString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private PlatformSelector() {}
}
