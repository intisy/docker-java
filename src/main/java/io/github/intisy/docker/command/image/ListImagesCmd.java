package io.github.intisy.docker.command.image;

import com.google.gson.reflect.TypeToken;
import io.github.intisy.docker.exception.DockerException;
import io.github.intisy.docker.model.Image;
import io.github.intisy.docker.transport.DockerHttpClient;
import io.github.intisy.docker.transport.DockerResponse;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to list images.
 *
 * @author Finn Birich
 */
public class ListImagesCmd {
    private final DockerHttpClient client;
    private boolean showAll = false;
    private boolean digests = false;
    private Map<String, List<String>> filters;

    public ListImagesCmd(DockerHttpClient client) {
        this.client = client;
    }

    /**
     * Show all images (default hides intermediate images).
     */
    public ListImagesCmd withShowAll(boolean showAll) {
        this.showAll = showAll;
        return this;
    }

    /**
     * Show digest information.
     */
    public ListImagesCmd withDigests(boolean digests) {
        this.digests = digests;
        return this;
    }

    /**
     * Add a filter.
     */
    public ListImagesCmd withFilter(String key, String... values) {
        if (this.filters == null) {
            this.filters = new HashMap<>();
        }
        this.filters.put(key, java.util.Arrays.asList(values));
        return this;
    }

    /**
     * Filter by dangling status.
     */
    public ListImagesCmd withDanglingFilter(boolean dangling) {
        return withFilter("dangling", String.valueOf(dangling));
    }

    /**
     * Filter by label.
     */
    public ListImagesCmd withLabelFilter(String label) {
        return withFilter("label", label);
    }

    /**
     * Filter by reference (name[:tag]).
     */
    public ListImagesCmd withReferenceFilter(String reference) {
        return withFilter("reference", reference);
    }

    /**
     * Execute the command.
     */
    public List<Image> exec() {
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (showAll) {
                queryParams.put("all", "true");
            }
            if (digests) {
                queryParams.put("digests", "true");
            }
            if (filters != null && !filters.isEmpty()) {
                queryParams.put("filters", client.getGson().toJson(filters));
            }

            DockerResponse response = client.get("/images/json", queryParams);
            
            if (!response.isSuccessful()) {
                throw new DockerException("Failed to list images: " + response.getBody(), response.getStatusCode());
            }

            Type listType = new TypeToken<List<Image>>() {}.getType();
            return client.getGson().fromJson(response.getBody(), listType);
        } catch (IOException e) {
            throw new DockerException("Failed to list images", e);
        }
    }
}
